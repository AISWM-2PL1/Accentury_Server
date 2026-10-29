# AI 호스트 운영

ai 컨테이너가 도는 전용 EC2(내부 ALB 뒤 ASG)의 구성, 부팅, 배포 reload, AMI 갱신, 분석 시간 예산을 다룬다. SSM 파라미터와 시크릿은 [secrets-ssm.md](secrets-ssm.md)에 있다.

관련 코드:

- [`infra/modules/ai-host/`](../../infra/modules/ai-host) - 시작 템플릿, ASG, 내부 ALB, 부팅 자산 버킷, IAM, 스케일링 경보
  - [`user_data.sh.tftpl`](../../infra/modules/ai-host/user_data.sh.tftpl), [`accentury-up.sh`](../../infra/modules/ai-host/accentury-up.sh), [`ai-egress-guard.sh`](../../infra/modules/ai-host/ai-egress-guard.sh), [`ai-health-metric.sh`](../../infra/modules/ai-host/ai-health-metric.sh), [`docker-compose.ai.yml`](../../infra/modules/ai-host/docker-compose.ai.yml)
- [`infra/modules/ai-ami/`](../../infra/modules/ai-ami) - 사전 적재 AMI 파이프라인 (미채택, prod 스택이 소유)
- [`infra/modules/config/main.tf`](../../infra/modules/config/main.tf) - 분석 시간 예산 파라미터
- [`infra/modules/monitoring/`](../../infra/modules/monitoring) - `ai-alb-no-healthy-host`, `ai-unhealthy`, `ai-disk-high`, `ai-mem-high`
- [`infra/envs/staging/terraform.tfvars`](../../infra/envs/staging/terraform.tfvars), [`infra/envs/prod/terraform.tfvars`](../../infra/envs/prod/terraform.tfvars) - `ai_instance_type`, `ai_ami_id`, `ai_root_volume_size`, `ai_max_size`, `ai_ami_pipeline_enabled`
- [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) - `reload_host`, "ASG 프로세스 정리" 스텝
- `backend/src/main/java/.../RestAiAnalysisClient.java`, `AnalysisDispatchConfig.java`, [`ai/app/track1.py`](../../ai/app/track1.py), [`ai/app/config.py`](../../ai/app/config.py)

실증 기록은 [docs/wiki/infra-history.md](../wiki/infra-history.md)의 KAN-201, KAN-246, KAN-285, KAN-272 항목, 결정 경위는 [docs/wiki/infra-decisions.md](../wiki/infra-decisions.md)에 있다.

## 개요

ai 호스트는 무상태이고 내부 ALB 뒤의 ASG(min 1, max 3)다. `modules/ai-host`의 첫 부팅 user_data가 docker와 compose
플러그인을 깔고(AMI에 이미 있으면 건너뛴다), 운영 compose 파일(`modules/ai-host/docker-compose.ai.yml`, 호스트에서는
`/opt/accentury/docker-compose.yml`)과 스크립트 3개(`accentury-up.sh`, `ai-egress-guard.sh`, `ai-health-metric.sh`)를
**부팅 자산 버킷에서 내려받은 뒤**(아래 "부팅 자산 버킷") systemd 유닛 `accentury.service`를 놓는다. 두 환경의 호스트
구성은 완전히 같고, 환경별 값은 전부 SSM Parameter Store에서 온다.

compose 파일이나 스크립트를 고치면 user_data가 바뀌어 **인스턴스가 교체된다** (시작 템플릿 새 버전 + ASG instance
refresh). 그래도 되는 이유가 무상태다. 교체는 새 인스턴스를 먼저 띄우고 옛 인스턴스를 나중에 종료하는 순서라 1대일
때도 분석이 끊기지 않는다 (instance refresh `min_healthy_percentage` 100, `max_healthy_percentage` 200, 아래 "AMI 갱신과
무중단 교체"). user_data는 raw 16KB 상한이 있어 ai-host 모듈의 precondition이 plan에서 크기를 검사한다.

backend는 이 호스트에 없다. KAN-165 전의 backend 호스트(`accentury-{env}`, t3.small, `aws_instance`)는 Fargate 서비스로
대체됐다.

## 호스트 사양

| | ai 호스트 `accentury-{env}-ai` |
| --- | --- |
| 인스턴스 | ASG min 1 max 3 (tfvars `ai_max_size`), `c7i.xlarge` 두 환경 동일 (tfvars `ai_instance_type`). 8GB 메모리를 이 컨테이너 혼자 쓴다 (실모델 RSS 최대 6.2GB) |
| 루트 볼륨 | 40GiB gp3 암호화 (tfvars `ai_root_volume_size`). 이미지 7GB x 2 공존 + pull 임시 공간 |
| 컨테이너 메모리 상한 | `mem_limit: 7g`. 호스트 7.6GiB에서 OS와 에이전트 몫 0.6GiB를 뺀 값이다. 근거는 `docker-compose.ai.yml` 주석 |
| 보안 그룹 | ai-sg (ai-alb-sg만 8000. ALB는 backend-sg만 8000) |
| 컨테이너 | ai (호스트 8000, 내부 ALB만 닿는다) |
| SSM 읽기 | 인라인 정책은 `/accentury/{env}/ai/*`와 `IMAGE_TAG`만 허용한다. 다만 같은 역할에 붙은 `AmazonSSMManagedInstanceCore`가 `ssm:GetParameter`, `ssm:GetParameters`를 `*`에 허용하므로, 이름을 아는 다른 파라미터도 단건으로는 읽힌다 (`modules/ai-host/main.tf`의 `ssm_core`). 기동 스크립트가 읽는 것은 위 둘뿐이다. 이 틈은 별도 티켓 몫이다 |
| IMDSv2 hop limit | 1 (컨테이너는 IMDS를 못 본다) |
| 추가 | iptables egress 가드, health 타이머 |

## 내부 ALB와 오토스케일링 (KAN-201)

backend가 부르는 이름 `ai.accentury.internal`은 내부 ALB(`accentury-{env}-ai-alb`)의 alias다. backend 쪽 설정은
`ACCENTURY_ANALYSIS_AIBASEURL`(`http://ai.accentury.internal:8000`)과 `dispatch-concurrency` 3이다. 태스크 하나가 동시에
3건까지 보내고 ALB가 그 호출을 빈 인스턴스로 나눈다 (아래 "분석 시간 예산").

| 항목 | 값 | 근거 |
| --- | --- | --- |
| ASG | min 1, max `ai_max_size`(3), 초기 desired 1 | 평시 1대. 계정 vCPU 쿼터(256)로는 64대까지 되므로 이 값은 비용 상한이다. |
| 대상 그룹 | HTTP 8000, instance, `least_outstanding_requests` | AI는 추론을 한 번에 하나만 돌린다(단일 lock). 라운드 로빈이면 한 대에 2건이 겹쳐 뒤 건이 lock을 기다린다. |
| 상태 검사 | `GET /internal/v0/health` 200, 15초 x 정상 2회 / 비정상 3회 | 워밍업 전(503 STARTING)에는 트래픽이 가지 않는다. 토큰 불필요. |
| ALB idle timeout | 90초 | backend 읽기 타임아웃(ai-timeout 85초)보다 길어야 한다. 기본 60초면 긴 추론이 504로 끊긴다. |
| 등록 해제 지연 | 90초 | 빠지는 인스턴스의 진행 중 추론(AI 상한 75초)이 끝날 시간이다. |
| ASG 상태 검사 | ELB, 유예 900초 | 첫 부팅(docker 설치 + 이미지 7GB pull + 모델 적재)이 실측 약 3분 22초다. 파이프라인의 healthy 대기(600초)에 pull을 더한 여유다. |
| 확대 | `accentury.analysis.processing.value` Maximum >= 6, 1분 x 1회 -> +1, 워밍업 300초 | backend의 폴링 혼잡 임계치와 같은 지점이다. CPU는 추론 1건이 10초라 밀림보다 늦다. 한 사람이 문항을 연달아 올려도 6에 닿지 않는다. 워밍업은 prod 실측 부팅(시작에서 healthy까지) 약 3분 + 경보 해제 지연 1~2분이다. |
| 축소 | 같은 지표 <= 1, 1분 x 15회 -> -1, 그 뒤 ALARM이 유지되는 동안 약 3분마다 다시 -1 (min까지) | 늘어난 인스턴스는 늘어난 동안만 과금이라 확대보다 훨씬 느리게 접는다. 실측 3에서 2가 16분, 2에서 1이 19분이다. |
| SG | backend-sg -> ai-alb-sg -> ai-sg (8000) | backend 태스크가 호스트에 직접 닿는 길은 없다. |

**MAX를 바꾸려면** 두 환경 tfvars의 `ai_max_size`를 고치고 apply한다. plan에 ASG 속성 변경 1건만 보여야 한다.
`min_size`는 모듈 기본값 1이고 0은 validation이 막는다 (AI가 아예 없으면 backend 회로가 열린 채 머문다).
`dispatch-concurrency`는 AI 최대 대수와 같은 값으로 두므로 MAX를 바꾸면 그 값과 아래 "분석 시간 예산"의 관계도 함께 본다.

지금 대수와 스케일링 이력:

```
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$(terraform output -raw ai_asg_name)" \
  --query 'AutoScalingGroups[0].[MinSize,MaxSize,DesiredCapacity]' --output text
aws autoscaling describe-scaling-activities --auto-scaling-group-name "$(terraform output -raw ai_asg_name)" \
  --max-items 5 --query 'Activities[].[StartTime,StatusCode,Cause]' --output table
aws cloudwatch describe-alarms --alarm-names "$(terraform output -raw ai_asg_name)-scale-out" "$(terraform output -raw ai_asg_name)-scale-in" \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output table
```

### ALB 뒤에서의 backend 판정

ALB가 스스로 만든 응답은 `Server: awselb/2.0`으로 온다. 대상이 없거나 연결을 거부한 502/503은 미도달(UNREACHED)로
접어 시도 예산(§2.5)에서 빼고, 대상이 idle timeout 안에 답하지 못한 504는 읽기 타임아웃과 같은 TIMED_OUT(재전송
없음, KAN-172)이다 (`RestAiAnalysisClient`). AI가 직접 낸 503(분석 시간 초과)은 uvicorn 헤더로 오므로 도달한 장애다.
`ai-timeout`을 SSM으로 ALB idle timeout(90초) 이상으로 올리면 504 경로가 실제로 열린다.

### 배포 시 순차 reload

파이프라인(`deploy.yml`의 `reload_host`)은 ASG의 InService 인스턴스를 한 대씩 돈다. SSM 에이전트 등록 대기, Run
Command로 `accentury-up.sh` 재실행(docker healthy까지), 그 대상이 ALB 대상 그룹에서 healthy가 될 때까지 대기, 다음
인스턴스 순이다. 여러 대에 한꺼번에 보내면 전부 같은 순간 대상 그룹에서 빠진다. 3대면 배포가 그만큼 길어진다(대당
약 2분에서 4분).

reload 동안에는 ASG 프로세스 `HealthCheck`, `ReplaceUnhealthy`, `AlarmNotification`을 멈춘다. ELB 상태 검사를 보는
ASG는 컨테이너가 내려간 인스턴스를 유예 기간이 지난 뒤 unhealthy로 교체해 버려, 정상 배포가 "인스턴스 사라짐"으로
실패하고 롤백하기 때문이다. 어느 경로로 끝나든 스크립트가 resume하고, job이 중간에 죽어도(수동 취소,
`timeout-minutes` 초과) 그 뒤의 "ASG 프로세스 정리" 스텝(`always()`)이 마커 파일 `$RUNNER_TEMP/asg-suspended`에 적힌
ASG를 되살린다 (KAN-212). 마커는 파이프라인의 suspend가 성공했을 때만 쓰이고 resume이 성공하면 지워지므로, 반영 전에
실패한 실행이나 정상 완료에서는 "재개할 ASG 없음"으로 끝나고 누가 손으로 멈춘 프로세스는 건드리지 않는다. 정지 뒤
1시간이 지나 역할 세션이 만료된 경우(timeout)를 위해 재개할 것이 있을 때만 세션을 다시 받는다.

러너 자체가 사라지면 어떤 스텝도 돌지 않으므로 그때만 아래 명령으로 손으로 되돌린다. 멈춘 채 두면 죽은 인스턴스가
교체되지 않고 스케일링도 서지 않으며, `terraform apply`의 instance refresh도 시작되지 않거나 취소된다 (refresh가
Cancelled나 Failed면 먼저 SuspendedProcesses를 본다). **같은 이유로 배포 파이프라인이 도는 동안에는 apply를 걸지 않는다.**

```
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$(terraform output -raw ai_asg_name)" \
  --query 'AutoScalingGroups[0].SuspendedProcesses[].ProcessName' --output text
aws autoscaling resume-processes --auto-scaling-group-name "$(terraform output -raw ai_asg_name)" \
  --scaling-processes HealthCheck ReplaceUnhealthy AlarmNotification
```

배포 역할은 그 조회와 정지/재개 권한(`autoscaling:DescribeAutoScalingGroups`, `autoscaling:SuspendProcesses`와
`ResumeProcesses`는 이 환경 ai ASG로 한정, `ssm:DescribeInstanceInformation`, `elasticloadbalancing:DescribeTarget*`)을
갖는다 (deploy 모듈).

### 경보와 비용

**경보.** `ai-alb-no-healthy-host`(대상 그룹 HealthyHostCount < 1, 3분)가 정본이고, 호스트 타이머 지표의
`ai-unhealthy`는 통계 Maximum으로 이중 안전망이다 (Minimum이면 새로 뜬 인스턴스의 워밍업(0)이 확대할 때마다 운다).
`ai-disk-high`, `ai-mem-high`도 Maximum이다. 경보 전체 표는 관측성 문서에 있다.

**비용.** 내부 ALB는 환경당 시간 요금 약 0.0225달러 + LCU(호출이 적어 최소)로 월 약 20달러다. 추가 인스턴스는
늘어난 동안만 과금된다(c7i.xlarge 온디맨드, Spot 제외).

## 기동 순서 (`accentury-up.sh`)

부팅마다 그리고 reload마다 돈다.

1. IMDSv2로 자기 인스턴스 ID를 읽는다. 컨테이너 로그 스트림 이름(`ai/<인스턴스 ID>`)에 쓴다 (KAN-203).
2. SSM 읽기. `get-parameters-by-path --path /accentury/{env}/ai`와 `get-parameter --name /accentury/{env}/IMAGE_TAG`.
3. 파라미터 이름의 마지막 조각을 환경 변수 이름으로 삼아 `/run/accentury/`(tmpfs, root 전용)에 env 파일 2개(compose.env,
   ai.env)를 쓴다. `IMAGE_TAG`는 compose.env(compose 보간 전용, 컨테이너에 안 들어간다), 그 외는 ai.env다. ai.env에
   `ACCENTURY_ENV`(env.conf)를 더한다 - 지표 차원이다. compose.env에는 로깅 드라이버가 보간하는 `AWS_REGION`,
   `AI_LOG_GROUP`, `INSTANCE_ID`도 적는다.
4. 인스턴스 프로파일로 ECR 로그인.
5. `docker compose --env-file /run/accentury/compose.env up -d --remove-orphans`
6. 어느 컨테이너도 쓰지 않는 이미지 정리 (`docker image prune -af`). 이전 SHA가 루트 볼륨에 쌓이지 않게 한다. 롤백은
   ECR에서 다시 당긴다. 정리가 실패해도 기동에는 영향이 없다.

1, 2, 4, 5는 일시 장애(IAM 전파 지연, 네트워크)에 대비해 최대 8회 백오프 재시도한다 (합계 약 4분). `IMAGE_TAG`
부재는 "배포 전" 상태라 재시도 없이 바로 실패한다. 스크립트는 자기 주소를 어디에도 알리지 않는다. ALB 대상 등록은
ASG가 한다.

ai 호스트의 env 파일은 tmpfs라 재부팅 시 사라졌다가 다시 만들어진다 (낡은 사본이 쌓이지 않는다). docker 자체는 컨테이너
환경 변수를 `/var/lib/docker/containers/*/config.v2.json`(암호화된 루트 볼륨, root 전용)에 기록하므로 호스트 디스크에
평문이 전혀 없는 것은 아니다. 값 형식 제약은 [secrets-ssm.md](secrets-ssm.md#값-형식-제약)에 있다.

## egress 가드와 health 타이머 (KAN-36)

`accentury-egress-guard.service`는 docker 뒤, `accentury.service` 앞에 iptables `DOCKER-USER` 체인에 "컨테이너
브리지에서 시작되는 연결 중 목적지가 VPC 대역 밖이면 DROP"과 IMDS(169.254.169.254) DROP을 넣는다. 실패하면 compose도
뜨지 않는다. `accentury-ai-health.timer`는 1분마다 `ai-health-metric.sh`로 `127.0.0.1:8000/internal/v0/health`를 찔러
CloudWatch `accentury/ai` `Healthy` 0|1(차원 env)을 올린다. 같은 스크립트가 루트 디스크와 메모리 사용률도 올린다
(`ai-disk-high`, `ai-mem-high`의 원천).

확인 (ai 호스트, SSM Session Manager):

```
iptables -S DOCKER-USER                         # DROP 4줄 (docker0, br+ 각각 VPC 밖 NEW, IMDS)
systemctl list-timers accentury-ai-health.timer  # 다음 실행 시각
journalctl -u accentury-ai-health -n 3           # health=200 -> Healthy=1 줄
docker logs --tail 20 $(docker compose ps -q ai)  # 이중 로깅으로 그대로 읽힌다 (KAN-203). 교체 뒤 로그는 CloudWatch
docker compose exec ai python -c "import urllib.request; urllib.request.urlopen('http://169.254.169.254/latest/meta-data/', timeout=2)"  # 실패해야 정상
```

## 부팅 자산 버킷 (KAN-38)

compose 파일과 스크립트 3개, 네 파일은 `accentury-{env}-ai-boot-{계정}` 버킷의 `ai-host/` 접두사 아래에 있고 Terraform이
`aws_s3_object`로 올린다. 퍼블릭 접근은 전면 차단이고, EC2 역할은 그 접두사에 `s3:GetObject`만 가진다 - 호스트가 뚫려도
자기 부팅 파일을 바꿔치기하지 못한다. 호스트가 퍼블릭 서브넷에 있어 ECR과 SSM을 쓰던 경로를 그대로 쓴다.

| 항목 | 값 |
| --- | --- |
| user_data 크기 | 템플릿 원문(`user_data.sh.tftpl`)이 5,890바이트라 렌더링 결과는 약 6KB, 16KB 상한의 약 36%다 (추정). 상한을 넘으면 precondition이 plan을 멈춘다. 그때는 템플릿 본문의 주석을 옮기거나 systemd 유닛 정의도 부팅 자산으로 내린다 |
| 변경 감지 | 네 파일의 `filemd5`를 묶은 해시가 user_data에 주석으로 들어간다. 파일이 바뀌면 해시가 바뀌고 시작 템플릿 새 버전과 instance refresh가 돈다 |
| 첫 부팅 실패 대비 | `fetch`가 5회까지 늘려 가며 재시도한다 (최대 약 75초). 다 실패하면 `set -e`가 부팅을 세우고 ASG가 교체한다 |
| 순서 | 시작 템플릿에 `depends_on = [aws_s3_object.boot]`. 오브젝트가 인스턴스보다 먼저 올라간다 |

해시가 핵심이다. 이 값이 없으면 스크립트를 고쳐도 user_data가 그대로라 S3만 갱신되고 도는 인스턴스는 옛 파일을 계속
쓴다. **해시를 user_data에서 빼지 않는다.**

## AMI 갱신과 무중단 교체 (KAN-246)

AI 호스트의 OS(AL2023)는 tfvars `ai_ami_id`에 적힌 AMI 그대로다. 인스턴스 안에서 `dnf update`를 돌리지 않으므로 커널,
glibc, OpenSSL의 보안 업데이트는 **AMI를 바꿔 인스턴스를 교체해야** 들어온다. docker는 user_data가 첫 부팅에 그 AMI의
저장소 버전으로 깔기 때문에 함께 올라간다. **월 1회, staging을 먼저 하고 확인한 뒤 prod를 한다.** Dependabot의 월 1회
갱신 PR(`.github/dependabot.yml`)과 같은 주기다. 넣는 값은 **맨 AL2023 AMI**다 (사전 적재 AMI는 쓰지 않는다, 아래).

```
# 1. 최신 AMI ID와 이름을 읽는다 (이름의 날짜가 지금 값보다 뒤여야 한다)
ami=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query Parameter.Value --output text)
aws ec2 describe-images --image-ids "$ami" --query 'Images[0].[ImageId,Name]' --output text

# 2. envs/staging/terraform.tfvars의 ai_ami_id와 그 위 주석의 이름을 바꾸고 plan을 본다.
#    변경은 2건이어야 한다 - 시작 템플릿의 image_id, 그리고 그 새 버전 번호를 따라가는 ASG의 launch_template.version
cd infra/envs/staging && terraform plan

# 3. apply. 교체가 끝날 때까지 기다린다 (약 7분)
terraform apply

# 4. 교체 확인 - refresh가 Successful이고 인스턴스가 새 AMI로 1대다
aws autoscaling describe-instance-refreshes --auto-scaling-group-name "$(terraform output -raw ai_asg_name)" \
  --max-records 1 --query 'InstanceRefreshes[0].[Status,PercentageComplete,StartTime,EndTime]' --output text
aws ec2 describe-instances --filters "Name=tag:Name,Values=$(terraform output -raw ai_asg_name)" \
  "Name=instance-state-name,Values=running" --query 'Reservations[].Instances[].[InstanceId,ImageId,LaunchTime]' --output text

# 5. 스모크 한 바퀴(secrets-ssm.md의 "원격 스모크 수동 실행")를 돌린 뒤 prod에서 1~4를 되풀이한다
```

**교체는 무중단이다.** instance refresh가 `min_healthy_percentage = 100`, `max_healthy_percentage = 200`이라 새
인스턴스를 먼저 띄운다. 새 인스턴스가 대상 그룹 healthy(모델 적재 완료)가 되고 워밍업 120초가 지난 뒤에야 옛 인스턴스가
대상 그룹에서 빠지고, 빠진 뒤에도 등록 해제 지연 90초 동안 진행 중이던 추론을 마친다. 그동안 ALB는 두 대에 나눠
보낸다. 교체하는 몇 분 동안 인스턴스가 평소의 두 배(1대면 2대)라 그만큼 과금된다.

| 주의 | 이유 |
| --- | --- |
| 배포 파이프라인이 도는 동안에는 하지 않는다 | reload가 ASG 프로세스를 멈추므로 refresh가 시작되지 않거나 취소된다 (위 "배포 시 순차 reload"). |
| AL2023 x86_64 AMI만 넣는다 | user_data가 dnf와 루트 디바이스 `/dev/xvda`를 전제한다. 파라미터 이름의 `kernel-default`가 가리키는 커널 계열이 바뀌면(AMI 이름의 `kernel-6.x`) staging에서 스모크를 반드시 본다. |
| 되돌리려면 `ai_ami_id`를 옛 값으로 바꿔 다시 apply한다 | 옛 AMI는 지원 종료(DeprecationTime) 뒤에도 ID로는 시작할 수 있다. 같은 무중단 교체가 한 번 더 돈다. |
| 새 인스턴스가 healthy가 되지 못하면 refresh가 실패하고 옛 인스턴스가 남는다 | 먼저 띄우는 순서라 실패해도 서비스는 옛 인스턴스로 이어진다. 원인은 새 인스턴스의 `/var/log/cloud-init-output.log`와 ai 컨테이너 로그를 본다. |

### 사전 적재 AMI는 쓰지 않는다 (KAN-285)

EC2 Image Builder로 docker와 ai 이미지를 미리 넣은 AMI(`modules/ai-ami`)는 EBS 지연 로딩 때문에 부팅이 더 느리고
불규칙해 **두 환경 tfvars에 넣지 않는다.** 실측은 history의 KAN-285 항목, 판단 근거는 decisions에 있다. 지연 로딩을
넘는 길은 Fast Snapshot Restore뿐이고 쓰지 않기로 했다.

남은 것은 `modules/ai-ami`(파이프라인, 레시피, 빌드 역할과 SG - prod 스택, tfvars `ai_ami_pipeline_enabled`, prod만
true)와 user_data의 "있으면 건너뛰기"(docker, compose 버전 검사)다. 파이프라인은 FSR을 켜 보거나 가중치를 이미지 밖으로
빼는 날 다시 쓸 수 있다. 비용은 구운 AMI의 스냅샷(20GiB, 월 1달러 미만)뿐이다 - 되돌림용으로 하나 두고 그 전 것은 등록
해제한다. 파이프라인을 치우려면 prod tfvars의 `ai_ami_pipeline_enabled`를 false로 apply한다 (AMI는 남는다).

| 항목 | 값 |
| --- | --- |
| 실행 | `aws imagebuilder start-image-pipeline-execution --image-pipeline-arn "$(terraform -chdir=infra/envs/prod output -raw ai_ami_pipeline_arn)"` (약 20분). 결과는 `list-image-pipeline-images`, 실패 로그는 출력 `ai_ami_build_log_group` |
| 부모 이미지 | SSM 퍼블릭 파라미터 참조 `ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64` - 빌드 때마다 그때의 최신 AL2023. Image Builder 관리형 AL2023 이미지는 이 리전에 DOCKER 형식뿐이라 못 쓴다 |
| 컴포넌트 | `modules/ai-ami/component.yml` - docker 설치, compose 플러그인, SSM `/accentury/prod/IMAGE_TAG`를 읽어 ECR pull, docker 정지. user_data.sh.tftpl의 같은 단계를 옮긴 것이라 두 파일이 같은 결과를 만들어야 한다. 로그인 토큰은 AMI에 남기지 않는다 |
| 빌드 인스턴스 | c7i.xlarge, prod 퍼블릭 서브넷, 전용 SG(인바운드 없음, 아웃바운드 443만), IMDSv2, 실패 시 종료. `resource_tags`에 `Name`은 예약 키라 못 넣는다 |
| 빌드 역할 | SSM 코어 + Image Builder 관리형 + ECR 읽기 + `IMAGE_TAG` 1개, 그 밖의 파라미터 읽기는 명시 Deny. `AmazonSSMManagedInstanceCore`가 `ssm:GetParameter`를 `*`에 허용하므로 Allow를 좁히는 것만으로는 경계가 서지 않는다. ai 호스트 역할에는 이 Deny가 없다 (위 "호스트 사양") |
| 루트 볼륨 | 20GiB gp3 암호화 - 호스트 시작 템플릿(40GiB)보다 작아야 띄울 수 있다 |
| 이미지 테스트 | 끔 (staging instance refresh가 대신한다) |
| 버전 | `recipe_version`(1.0.0). component.yml이나 레시피 인자를 바꾸면 올린다 - 같은 이름과 버전의 레시피는 다시 못 만든다 |

## 분석 시간 예산 (KAN-172 확정)

`modules/config`가 환경마다 넣는 값이다. 코드 기본값(`application.yml`, `ai/app/config.py`)과 같은 값이고, SSM은 실측이
바뀌었을 때 재빌드 없이 조정하는 자리다. 근거 실측은 KAN-57의 c7i.xlarge 값(bf16 + MFA `align_one`, 1건 P50 10.1초,
P95 11.1초, 콜드 스타트 31초, 호스트당 동시 처리 1건)이다.

| 파라미터 | 값 | 뜻 |
| --- | --- | --- |
| `ACCENTURY_ANALYSIS_AITIMEOUT` | `85s` | backend가 AI 호출에 거는 연결과 읽기 타임아웃. AI 상한 75초보다 10초 길다. |
| `ACCENTURY_ANALYSIS_PROCESSINGTIMEOUT` | `300s` | 실행 잔류 한도. `ai-timeout x 3 + 백오프`(255.9초)보다 길어야 backend가 뜬다. |
| `ACCENTURY_ANALYSIS_DISPATCHCONCURRENCY` | `3` | 태스크당 전달 워커 수. AI 최대 대수와 같은 값이다. AI는 호스트마다 추론을 한 번에 하나만 돌리므로(8GB에서 2건이면 OOM) AI가 1대면 뒤의 2건은 AI 안에서 차례를 기다리고, 2대 이상이면 내부 ALB가 나눈다. |
| `ai/ACCENTURY_AI_ANALYSIS_TIMEOUT_SECONDS` | `75` | AI 자신의 상한 (lock 대기와 워커 재적재 대기 포함). backend보다 짧아야 AI가 먼저 끊고 503을 돌려준다. 한 호스트에 겹친 호출 6건(태스크당 전달 워커 3개 x 롤링 배포 중 태스크 2개)의 6 x P95 11초 = 67초와 워커 재적재 31초 + 추론 11초 = 42초를 각각 덮는 값이다. 짧으면(25초, 40초) 추론 중인 요청을 끊어 멀쩡한 워커를 죽이고 재전송이 새 워커를 또 죽이는 연쇄가 된다. 두 시나리오의 합(97.6초)은 덮지 않는다 - 그때는 backend 회로 차단기가 연속 5회 실패에서 열려 호출을 멈추므로 연쇄가 자기수렴한다 (KAN-28). |

같은 티켓의 관련 값: backend의 읽기 타임아웃은 재전송하지 않는다 (`ANALYSIS_TIMEOUT` 즉시 종결, 연결 실패와 5xx만 2회
재전송). 폴링 혼잡 임계치는 6(AI 1분 처리량), 디스패처 큐 용량은 30(`queued-timeout` 5분 / 10초), AI compose
`start_period`는 90초, `analysis-backlog-high` 경보 임계치는 혼잡 임계치의 두 배인 12다. **값을 바꿀 때는 위 관계를 함께
본다** - backend는 기동 시점에 이 관계를 검사하고 어긋나면 뜨지 않는다 (`AnalysisDispatchConfig`).

**`dispatch-concurrency`는 전역 상한이 아니다.** 태스크 하나가 보내는 동시 호출만 묶으므로, backend 오토스케일링(최대
3)이나 롤링 배포로 태스크가 둘 이상 뜨면 그만큼 AI에 동시에 들어간다. 한 호스트에 겹치는 호출 수는 "태스크 수 x 3 / AI
대수"다 (값의 근거는 `modules/config/main.tf`).

| backend 태스크 | AI 1대에 겹치는 호출 | 맨 뒤 요청의 소요 | AI 상한 75초 |
| --- | --- | --- | --- |
| 1 (평시) | 3 | 약 33초 | 안 |
| 2 (롤링 배포 중) | 6 | 약 67초 | 안 |
| 3 (backend 스케일아웃) | 9 | 약 100초 | 밖. AI가 2대면 약 50초로 안이다 |

상한 밖 구간에서 일곱 번째 요청을 그대로 추론에 넣으면 66.6초에 시작해 **추론 도중** 75초 상한에 걸리고, 그 취소가
멀쩡한 워커를 죽여 재적재 31초가 뒤 요청까지 민다. 그래서 AI는 lock을 잡은 시점에 상한까지 남은 시간이 여유분(기본
15초, `ACCENTURY_AI_INFERENCE_RESERVE_SECONDS`)보다 적으면 추론을 시작하지 않고 429로 돌려준다 (KAN-272,
`ai/app/track1.py`의 `_require_inference_budget`). 추론 전 거절이라 backend는 시도 예산을 깎지 않고 재전송 예산(2회)
안에서 다시 보내고, 다 쓰면 `ANALYSIS_UNAVAILABLE`로 종결해 재업로드를 연다. AI 로그의 `과부하로 추론 전 거절`이 이
경우다 - 보이면 한 호스트에 호출이 너무 많이 겹친 것이다.

기다리는 요청이 **도는 추론을 방해하지는 않는다** - 취소는 자기 차례를 기다리는 지점에서 끊기고 워커를 죽이지 않는다
(`ai/app/track1.py`). 그래서 증상은 "느려지고 일부가 재전송된다"이지 "전부 죽는다"가 아니다. 전역 한 건으로 묶는 일은
다중 인스턴스 상태를 다루는 KAN-167의 몫이다.

## 운영 확인

ai 호스트는 SSM Session Manager로 접속한 뒤 본다.

```
systemctl status accentury          # 마지막 기동 결과
journalctl -u accentury -n 50       # 기동 스크립트 로그 (어느 태그를 읽었는지)
docker compose -f /opt/accentury/docker-compose.yml --env-file /run/accentury/compose.env ps
```

backend는 호스트가 없다 - [`backend.md`](backend.md)의 `describe-services`와 `logs tail`로 본다. ai 컨테이너는
호스트 8000(ai-sg가 ai-alb-sg만 허용)을 발행하고 `restart: unless-stopped`라 프로세스가 죽으면 docker가 다시 띄우며,
호스트가 재부팅되면 systemd 유닛이 SSM 값을 새로 읽어 `up -d`를 다시 건다. ai 호스트 자체가 죽거나 대상 그룹 상태
검사에 떨어지면 ASG가 교체하고 새 인스턴스는 ASG가 대상 그룹에 넣는다 (KAN-201 - 이름은 ALB alias라 바뀌지 않는다).
backend 태스크가 죽으면 ECS 서비스가 desired 수만큼 새 태스크를 띄운다.
