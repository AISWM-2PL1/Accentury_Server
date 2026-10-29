# 구축, 재구축, 철거

환경 스택(bootstrap, staging, prod)을 처음 짓고, 다시 짓고, 철거하는 절차와 staging을 비용 때문에 잠시 끄고 켜는 절차다.

관련 코드:

- `../../infra/bootstrap/` (state 버킷, ECR, OIDC 공급자, 서비스 연결 역할, 음성 버킷 `voice.tf`, CloudTrail과 GuardDuty `audit.tf`)
- `../../infra/envs/staging/`, `../../infra/envs/prod/` (`main.tf`, `terraform.tfvars`, `backend.tf`, `outputs.tf`)
- `../../infra/modules/` (network, data, edge, fargate, ai-host, config, deploy, monitoring, slack-alerts, waf, ai-ami)
- `../../.github/workflows/deploy.yml` (Image Deploy, `DEPLOY_PAUSED`, verified 태그)
- `../../scripts/push-images.sh`, `../../scripts/publish-share-assets.sh`, `../../scripts/publish-well-known.sh`, `../../scripts/publish-privacy.sh`

실증 기록은 [docs/wiki/infra-history.md](../wiki/infra-history.md)의 KAN-140, KAN-165, KAN-36, KAN-201, staging 수동 끄기 항목에 있다.

## 사전 요건

- Terraform >= 1.11. S3 네이티브 잠금 `use_lockfile`은 1.10부터, write-only 인자 `value_wo`는 1.11부터다. envs는
  `required_version = ">= 1.11.0"`, bootstrap은 `">= 1.10.0"`이다. DynamoDB 잠금 테이블은 필요 없다.
- AWS CLI 프로파일(accentury-cli, ap-northeast-2). Terraform 실행용이다. 배포 파이프라인은 이 프로파일이 아니라 GitHub
  OIDC 역할을 쓴다 (KAN-127, [github.md](github.md)).
- 선행 완료 상태: Route 53 호스팅 영역과 ACM 인증서 2장 (KAN-119). us-east-1 인증서는 CloudFront 뷰어 구간, 서울 인증서는
  ALB 오리진 구간(KAN-125)에 쓴다. 셋 다 Terraform이 소유하지 않고 data 소스로 조회만 한다. destroy에도 지워지지 않는다.
- 인증서 2장에는 태그 `accentury-role = wildcard`가 붙어 있어야 한다. 조회가 도메인 + 이 태그로 고정돼 있어서(apex 전용
  인증서가 추가돼도 잘못 잡히지 않게), 태그가 없으면 plan이 "empty result"로 실패한다. 인증서를 재발급하면 다시 붙인다.

  ```
  aws acm add-tags-to-certificate --region us-east-1      --certificate-arn <arn> --tags Key=accentury-role,Value=wildcard
  aws acm add-tags-to-certificate --region ap-northeast-2 --certificate-arn <arn> --tags Key=accentury-role,Value=wildcard
  ```

- ECR 이미지 (KAN-120): backend Fargate 태스크(runtime_platform X86_64)와 ai 호스트(c7i.xlarge)가 모두 x86_64이므로 이미지는
  linux/amd64여야 한다. `scripts/push-images.sh`의 기본 PLATFORM이 그 값이다 (KAN-124에서 고정). arm64 이미지를 배포하면
  태스크가 exec format error로 바로 죽고 회로 차단기가 되돌린다 ([deploy.md "이미지 배포 파이프라인과 롤백"](deploy.md#이미지-배포-파이프라인과-롤백-kan-128)).
  ECR 리포지토리 자체는 bootstrap 스택이 소유한다. 실모델 베이스 이미지(`accentury/ai-model`)도 같은 이유로 linux/amd64여야
  한다. 이 리포지토리만 CI가 아니라 모델 담당이 직접 push한다 ([deploy.md "실모델 베이스 이미지 push"](deploy.md#실모델-베이스-이미지-push-kan-173), KAN-173).
- 첫 apply 전에 SSM `/accentury/{env}/IMAGE_TAG`가 있어야 한다 (KAN-165). fargate 모듈이 이 값을 태스크 정의 image에 읽으므로
  없으면 plan이 ParameterNotFound로 멈춘다. 이 파라미터는 Terraform 소유가 아니라 destroy 뒤에도 남는다. 새로 짓는
  환경(prod 최초 구축 KAN-171 같은 경우)은 staging에서 verified된 SHA를 먼저 넣는다.

  ```
  aws ssm put-parameter --name /accentury/prod/IMAGE_TAG --type String --value <verified SHA>
  ```

## Terraform 작업 규칙

- 콘솔로 먼저 짓고 나중에 코드를 맞추는 순서는 금지다 (KAN-140). 리소스는 Terraform으로만 만든다.
- 모듈 `depends_on`은 쓰지 않는다. 모듈 안 data 소스까지 apply 시점으로 미뤄 user_data나 태스크 정의가 unknown이 되고,
  값 하나만 바뀌어도 인스턴스가 교체된다. 순서가 필요하면 출력값(이름 목록 등)을 참조해 의존을 만든다.
- 살아 있는 스택에서 보안 그룹을 갈아 끼울 때는 규칙의 참조를 새 그룹으로 먼저 apply한다. 옛 그룹을 참조하는 규칙이
  남아 있는 동안은 옛 그룹을 지울 수 없어 DependencyViolation으로 재시도만 반복한다(2026-09-02 ec2-sg 제거 때 실측, 그때는
  `aws ec2 modify-security-group-rules`로 참조를 먼저 바꿔 풀었다). 좁은 egress 규칙을 넣는 경우는
  [`waf-edge.md`](waf-edge.md)의 적용 순서를 따른다.
- 살아 있는 스택에서 보안 그룹 `description`을 바꾸지 않는다. 교체 강제 속성이고 이름이 고정이라 SG 교체가 RDS와 ASG 부착
  때문에 실패한다. alb, rds, ai SG description에 남은 "ec2-sg" 문구는 스택을 처음부터 지을 때 고친다.

## 구축 절차

### 0. bootstrap (계정에 한 번만)

state 버킷은 Terraform state를 담을 곳이라 처음에는 자기 자신을 원격 state로 만들 수 없다 (닭과 달걀). 지금은 그 버킷의
`bootstrap/terraform.tfstate`에 state가 있다 (`backend.tf`, KAN-245). envs와 똑같이 `terraform init`만 하면 원격 state를
읽는다. 버킷부터 다시 만들어야 하는 계정에서는 `backend.tf`를 잠시 빼고 로컬로 apply한 뒤 `terraform init -migrate-state`로
되돌린다.

```
cd infra/bootstrap
terraform init
terraform plan
terraform apply
```

생성물:

| 리소스 | 비고 |
| --- | --- |
| `accentury-tfstate-<account_id>` 버킷 | 버전 관리, 암호화, 퍼블릭 차단, 이전 버전 30일 만료, TLS 강제 정책 (KAN-242). `prevent_destroy` |
| 음성 전용 버킷 `accentury-voice-<account_id>` | KAN-269. 버전 관리, 암호화, 퍼블릭 차단, 만료 규칙 없음, TLS 강제와 객체 읽기 주체 제한 정책. `prevent_destroy`, `force_destroy` 없음. envs/*가 이 버킷에 쓰므로 bootstrap apply가 먼저다 ([secrets-ssm.md "음성 전용 S3"](secrets-ssm.md#음성-전용-s3-kan-269)) |
| ECR `accentury/backend`, `accentury/ai`, `accentury/ai-model` | IMMUTABLE. 라이프사이클: 태그 없는 이미지 1일, 최근 50개 유지. 롤백이 이전 SHA를 다시 당기므로 최근 이미지가 남아 있어야 한다 |
| GitHub Actions OIDC 공급자 `token.actions.githubusercontent.com` | KAN-127. 계정에 1개뿐이라 여기서 만든다. envs/*의 deploy 모듈이 data 소스로 조회하므로 bootstrap apply가 먼저다 |
| ECS 서비스 연결 역할 `AWSServiceRoleForECS` | KAN-165. 계정에 1개뿐이다. 없으면 envs apply의 클러스터 생성이 실패한다 |
| `AWSServiceRoleForApplicationAutoScaling_ECSService` | KAN-168. 같은 이유다. 없으면 envs apply의 scalable target 등록이 실패한다 |
| CloudTrail trail `accentury-account-audit`, 로그 버킷 `accentury-cloudtrail-<account_id>`, GuardDuty detector | KAN-245 ([waf-edge.md "전송 구간과 엣지 보안"](waf-edge.md#전송-구간과-엣지-보안-kan-245)) |

배포용 리포지토리 `accentury/backend`와 `accentury/ai`는 KAN-120이 콘솔에서 먼저 만들었다. `ecr.tf`의 import 블록이 첫 plan에서
그 둘을 state로 흡수하므로 별도 import 명령이 없다. plan에 `scan_on_push`와 라이프사이클 정책이 갱신/생성으로 나오는 것은
정상이다. `accentury/ai-model`은 KAN-173에서 코드로 처음 만들므로 import 대상이 아니다 (import 블록의 for_each는 배포용 2개만
돈다). 흡수가 이미 끝난 계정에서 KAN-173을 apply하면 plan은 `accentury/ai-model` 리포지토리와 라이프사이클 정책 생성 2건만
보여야 한다.

버킷이 이미 있는데 state를 잃었다면:

```
terraform import aws_s3_bucket.tfstate accentury-tfstate-<account_id>
```

### 1. staging 먼저

```
cd infra/envs/staging
terraform init
terraform plan
terraform apply
```

staging을 검증한 뒤에 같은 모듈로 prod를 짓는다. 순서를 강제하는 이유는 staging에서 모듈 결함을 다 털어내야 prod apply가
한 번에 서기 때문이다.

첫 apply 후 확인:

- `terraform plan` 재실행이 "No changes"인가 (KAN-140 AC).
- 잠금 동작: 터미널 2개에서 동시에 `terraform apply`를 걸면 뒤쪽이 state lock 오류로 거부되는가 (KAN-140 AC).
- backend 서비스 (KAN-165): apply 자체가 서비스 안정(태스크 RUNNING + healthy)까지 기다린다 (`wait_for_steady_state`). 그래도
  확인한다.

  ```
  aws ecs describe-services --cluster "$(terraform output -raw ecs_cluster_name)" --services backend \
    --query 'services[0].[runningCount,deployments[0].rolloutState,taskDefinition]' --output text
  aws elbv2 describe-target-health --target-group-arn <대상 그룹 arn>   # 태스크 IP 하나가 healthy
  ```

- backend 오토스케일링 (KAN-168): scalable target이 min 1 max 3으로 등록됐고 목표 추적 경보 2개가 생겼는지. 부하가 없으면
  AlarmLow(스케일인)는 ALARM, AlarmHigh는 OK가 정상이다. min 1이라 더 줄지 않을 뿐이다 ([backend.md "오토스케일링"](backend.md#오토스케일링-kan-168)).

  ```
  aws application-autoscaling describe-scalable-targets --service-namespace ecs \
    --resource-ids "$(terraform output -raw backend_autoscaling_resource_id)" \
    --query 'ScalableTargets[0].[MinCapacity,MaxCapacity]' --output text
  aws cloudwatch describe-alarms --alarm-names $(terraform output -json backend_autoscaling_alarm_names | jq -r '.[]') \
    --query 'MetricAlarms[].[AlarmName,StateValue]' --output table
  ```

- ai 호스트 (KAN-36, ALB는 KAN-201): ASG에 인스턴스 1대가 InService이고 AI 대상 그룹에서 healthy이며, 프라이빗 영역의
  `ai.accentury.internal` A 레코드가 내부 ALB의 alias인지. 새 인스턴스가 healthy가 되기까지 약 3분 20초 걸린다 (docker 설치 +
  이미지 pull + 모델 적재, 실측은 history의 KAN-201 항목, ([ai-host.md "내부 ALB와 오토스케일링"](ai-host.md#내부-alb와-오토스케일링-kan-201))).

  ```
  aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names "$(terraform output -raw ai_asg_name)" \
    --query 'AutoScalingGroups[0].Instances[].[InstanceId,LifecycleState,HealthStatus]' --output table
  aws elbv2 describe-target-health --target-group-arn "$(aws elbv2 describe-target-groups \
    --names "$(terraform output -raw ai_target_group_name)" --query 'TargetGroups[0].TargetGroupArn' --output text)" \
    --query 'TargetHealthDescriptions[].[Target.Id,TargetHealth.State]' --output table
  aws route53 list-resource-record-sets --hosted-zone-id "$(terraform output -raw private_zone_id)" \
    --query "ResourceRecordSets[?Type=='A'].[Name,AliasTarget.DNSName]" --output table
  ```

- 음성 전용 S3 (KAN-201, KAN-269, 두 환경): 버킷은 bootstrap이 만든다. **bootstrap apply가 이 apply보다 먼저다.** 여기서는
  버킷이 있고 퍼블릭 액세스가 차단됐는지, 두 파라미터가 생겼는지 본다. **이 apply가 backend 코드 배포보다 먼저다.** 태스크
  정의 secrets에 `ACCENTURY_TRAINING_BUCKET`과 `ACCENTURY_TRAINING_KEYPREFIX`가 늘어 apply가 태스크 정의를 새 리비전으로 갈고,
  그 파라미터가 없으면 파이프라인의 리비전 등록이 실패한다 (KAN-132 교훈, [secrets-ssm.md "음성 전용 S3"](secrets-ssm.md#음성-전용-s3-kan-269)).

  ```
  aws s3api get-public-access-block --bucket "$(terraform output -raw training_bucket)"
  aws s3api get-bucket-versioning --bucket "$(terraform output -raw training_bucket)"      # Status = Enabled
  aws ssm get-parameters-by-path --path /accentury/staging --query "Parameters[?contains(Name, 'TRAINING')].[Name,Value]" --output table
  ```

### 2. prod

```
aws ssm put-parameter --name /accentury/prod/IMAGE_TAG --type String --value <verified SHA>   # 사전 요건
cd infra/envs/prod
terraform init
terraform plan   # staging과 diff가 tfvars 차이뿐인지 눈으로 확인
terraform apply
```

### 3. apply 뒤 남는 수동 단계

새로 짓거나 다시 지은 환경마다 아래를 한다. Terraform이 만들지 않는 것들이라 apply만으로는 생기지 않는다.

1. **자리 표시 시크릿을 실제 값으로 넣는다.** config 모듈은 사람만 아는 외부 시크릿 5종(카카오 Admin 키, 후기 슬랙 웹훅,
   네이버 Client Secret, 애플 p8 개인 키, Gemini API 키)을 자리 표시 값으로 만든다. 그 값인 동안 해당 기능만 꺼지거나 401,
   503이다. 넣는 명령과 재기동 순서는 [secrets-ssm.md "재구축 뒤 넣는 시크릿 5종"](secrets-ssm.md#재구축-뒤-넣는-시크릿-5종)에 있다.
2. 이미지 태그 반영: backend 태스크는 apply 시점의 SSM `IMAGE_TAG`로 이미 떠 있다. ai 호스트는 같은 태그를 읽어 첫 부팅에
   기동한다. 다른 SHA를 반영하려면 Actions "Image Deploy"를 해당 환경으로 수동 실행한다. SSM 갱신, ai 호스트 reload, backend
   리비전 배포를 한 번에 한다 ([deploy.md "이미지 배포 파이프라인과 롤백"](deploy.md#이미지-배포-파이프라인과-롤백-kan-128)).
3. GitHub environment 변수 4개(`AWS_DEPLOY_ROLE_ARN`, `WEB_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, `APP_DOMAIN`)를 apply 출력으로
   채운다 ([github.md](github.md)). 그 뒤 웹 번들은 Accentury_App 레포의 Web Deploy 워크플로가 올린다 (KAN-127).
4. **등급 공유 이미지 5장을 올린다** (KAN-132). `scripts/publish-share-assets.sh <env>`를 환경마다 한 번 돌린다. backend는 SSM
   `ACCENTURY_RESULT_ASSETBASEURL`(= `https://<도메인>/share`)에 등급 code를 붙여 `/result`의 `share.imageUrl`을 만들므로, 버킷에
   `share/<code>.png`가 없으면 결과 화면과 카카오 카드의 이미지가 깨진다. 캐릭터가 바뀌면 다시 돌린다. 서버 배포는 없다
   ([deploy.md "등급 공유 이미지"](deploy.md#등급-공유-이미지-kan-132)).
5. **개인정보처리방침을 게시한다** (KAN-133). `scripts/publish-privacy.sh <env>`를 환경마다 한 번 돌린다. 웹 버킷의
   `privacy.html` 객체 하나라 apply로도, 웹 배포로도 생기지 않는다. 확장자 없는 `/privacy`는 SPA 재작성 함수가 앱 화면으로
   돌리므로 주소는 `/privacy.html`이다. 본문이 바뀌면 다시 돌린다 (자세한 것은 `infra/privacy/README.md`).
6. **SNS 이메일 구독을 확인한다** (KAN-134). apply 직후 수신함에 AWS 확인 메일이 오고, 링크를 누르기 전에는 경보가 울려도
   메일이 나가지 않는다. 확인 명령은 [alarms.md "수신 주소와 구독 확인"](alarms.md#수신-주소와-구독-확인)에 있다.
7. **App Link 검증 파일을 올린다** (KAN-32). `scripts/publish-well-known.sh <env>`를 환경마다 한 번 돌린다. 버킷 안의 객체
   2개라 apply만으로는 생기지 않는다. 서명 키나 애플 팀 ID가 바뀌면 다시 돌린다 (`infra/well-known/README.md`).
8. staging을 다시 지었으면 **prod에서 슬랙 `#infra-alerts` 채널 설정을 다시 만든다** (KAN-273, [alarms.md "staging을 다시 지은 뒤"](alarms.md#staging을-다시-지은-뒤)). staging 토픽이 새로 생겨
   Chatbot 구독이 끊기는데, 토픽 ARN은 이름이 같아 prod plan이 "No changes"로 나와 그냥 apply해서는 복구되지 않는다.

   ```
   terraform -chdir=infra/envs/prod apply -replace='module.slack_alerts.aws_chatbot_slack_channel_configuration.infra[0]'
   aws sns list-subscriptions-by-topic --topic-arn <staging 토픽 ARN>   # global.sns-api.chatbot.amazonaws.com 구독이 있어야 한다
   ```

9. 철거 때 `DEPLOY_PAUSED`를 걸어 뒀으면 두 레포에서 지운다 (아래 철거 절).

## staging 끄기와 켜기

2026-10-14부터 staging은 쓰지 않는 동안 **destroy가 아니라 수동 정지**로 비용을 줄인다. destroy는 RDS를 스냅샷 없이
지우고(staging tfvars `db_skip_final_snapshot = true`), 손으로 넣은 SSM 시크릿도 자리 표시 값으로 되돌리기 때문이다. 정지는
Terraform 밖에서 AWS CLI로 하고, 예약 자동화는 두지 않는다. 결정 경위는 docs/wiki/infra-decisions.md에 있다.

원래 값(켜진 상태):

| 대상 | 이름 | 값 |
| --- | --- | --- |
| ECS backend 확장 대상 | `service/accentury-staging/backend` (클러스터 `accentury-staging`, 서비스 `backend`) | min 1 / max 3, desired 1 |
| AI ASG | `accentury-staging-ai` | min 1 / max 3 / desired 1 |
| RDS | `accentury-staging` | available |
| 경보 | 이름이 `accentury-staging-`로 시작하는 18개 (monitoring 모듈 16개 + ai-host `scale-out`, `scale-in` 2개) | 액션 켜짐 |

backend 목표 추적 경보 2개(`TargetTracking-...`)는 이 prefix에 걸리지 않는다. 확장 대상의 min을 0으로 내리면 함께 잠잠해진다.

### 끄기

먼저 다른 세션이 staging에서 실증 중이 아닌지, Image Deploy가 돌고 있지 않은지 확인한다.

```
REGION=ap-northeast-2

# 1. 경보 액션을 끈다 (zsh는 변수 단어 분리를 하지 않으므로 xargs로 넘긴다)
aws cloudwatch describe-alarms --region "$REGION" --alarm-name-prefix accentury-staging- \
  --query 'MetricAlarms[].AlarmName' --output text | tr '\t' '\n' \
  | xargs aws cloudwatch disable-alarm-actions --region "$REGION" --alarm-names

# 2. backend를 0으로 (확장 대상 min을 먼저 0으로 내려야 오토스케일링이 1로 되돌리지 않는다)
aws application-autoscaling register-scalable-target --region "$REGION" --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount --resource-id service/accentury-staging/backend \
  --min-capacity 0 --max-capacity 3
aws ecs update-service --region "$REGION" --cluster accentury-staging --service backend --desired-count 0

# 3. AI 호스트를 0대로
aws autoscaling update-auto-scaling-group --region "$REGION" --auto-scaling-group-name accentury-staging-ai \
  --min-size 0 --desired-capacity 0

# 4. RDS 정지
aws rds stop-db-instance --region "$REGION" --db-instance-identifier accentury-staging
```

확인: 경보 18개의 `ActionsEnabled`가 전부 false, ECS running 0 / desired 0, ASG 인스턴스 0대, RDS `stopped`. RDS 정지가 시간의
대부분을 차지해 전체 약 8분 30초 걸린다.

```
aws cloudwatch describe-alarms --region "$REGION" --alarm-name-prefix accentury-staging- \
  --query 'MetricAlarms[].[AlarmName,ActionsEnabled]' --output table
aws ecs describe-services --region "$REGION" --cluster accentury-staging --services backend \
  --query 'services[0].[runningCount,desiredCount]' --output text
aws autoscaling describe-auto-scaling-groups --region "$REGION" --auto-scaling-group-names accentury-staging-ai \
  --query 'AutoScalingGroups[0].[MinSize,DesiredCapacity,length(Instances)]' --output text
aws rds describe-db-instances --region "$REGION" --db-instance-identifier accentury-staging \
  --query 'DBInstances[0].DBInstanceStatus' --output text
```

### 켜기

순서는 **RDS -> AI -> ECS -> 경보**다. AI는 DB를 쓰지 않으므로 RDS 시작 명령 직후 함께 켜도 된다. 경보는 backend가 대상
그룹에서 healthy가 된 뒤에 켠다. 먼저 켜면 기동 중의 no-healthy-target이 알림을 보낸다.

```
REGION=ap-northeast-2

# 1. RDS 시작
aws rds start-db-instance --region "$REGION" --db-instance-identifier accentury-staging

# 2. AI 호스트 1대
aws autoscaling update-auto-scaling-group --region "$REGION" --auto-scaling-group-name accentury-staging-ai \
  --min-size 1 --desired-capacity 1

# 3. RDS가 available이 된 뒤 backend 1개
aws rds wait db-instance-available --region "$REGION" --db-instance-identifier accentury-staging
aws application-autoscaling register-scalable-target --region "$REGION" --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount --resource-id service/accentury-staging/backend \
  --min-capacity 1 --max-capacity 3
aws ecs update-service --region "$REGION" --cluster accentury-staging --service backend --desired-count 1
aws ecs wait services-stable --region "$REGION" --cluster accentury-staging --services backend

# 4. backend 대상이 healthy인지 본다. services-stable 뒤에도 Spring 기동으로 1분 30초쯤 unhealthy다
aws elbv2 describe-target-health --region "$REGION" --target-group-arn "$(aws elbv2 describe-target-groups \
  --region "$REGION" --names accentury-staging-backend --query 'TargetGroups[0].TargetGroupArn' --output text)" \
  --query 'TargetHealthDescriptions[].TargetHealth.State' --output text

# 5. 경보 액션을 켠다
aws cloudwatch describe-alarms --region "$REGION" --alarm-name-prefix accentury-staging- \
  --query 'MetricAlarms[].AlarmName' --output text | tr '\t' '\n' \
  | xargs aws cloudwatch enable-alarm-actions --region "$REGION" --alarm-names
```

대기 루프를 쓸 때는 상태 문자열을 정확히 비교한다. `grep -q healthy`는 `unhealthy`에도 걸린다. 조회 키는
`TargetHealthDescriptions[].TargetHealth.State`다 (`TargetHealth[]`로 쓰면 항상 None이다). AI 대상 그룹은
`accentury-staging-ai-tg`다. 전체 약 7~8분 걸린다 (RDS 약 5분 + backend 정상까지 약 3분). no-healthy-target 경보는 healthy 뒤
3분쯤 늦게 OK로 돌아와 OK 알림 1건이 간다.

### 꺼 둔 동안의 성질

- **Dev 병합은 prod로 못 나간다.** 꺼진 동안 Dev 병합의 Image Deploy는 staging 반영에서 실패해 그 SHA에 `verified-<sha>` 태그가
  붙지 않는다. Release 승격은 verified 이미지를 찾으므로 그 커밋은 prod로 나가지 못한다. 켠 뒤 최신 Dev SHA로 Image Deploy를
  다시 실행해 verified를 받는다. 끄는 데 `DEPLOY_PAUSED`를 쓰지 않는다. 그것은 배포를 건너뛰게 할 뿐 verified를 주지 않는다.
- Accentury_App의 Web Deploy는 정지의 영향을 받지 않는다 (CloudFront와 웹 버킷은 그대로다).
- RDS는 정지 7일째 AWS가 자동으로 시작한다. 오래 꺼 둘 때는 다시 정지한다.
- 꺼 둔 동안 staging `terraform apply`를 하면 AI ASG의 min과 ECS 확장 대상 min이 코드 값(1)으로 돌아온다. apply 뒤 다시 끄거나,
  켜는 김에 apply한다.
- RDS 운영자 접속 통로([rds-flyway.md](rds-flyway.md))는 AI 호스트를 경유하므로 staging이 켜져 있어야 쓸 수 있다.

## 철거 (teardown)

환경 스택은 `terraform destroy`로 제거한다. 호스팅 영역과 인증서는 data 참조라 삭제 대상에 포함되지 않는다 (KAN-140 AC).
staging을 비용 때문에 잠시 멈추는 것이 목적이면 destroy가 아니라 위 "staging 끄기"를 쓴다.

```
cd infra/envs/staging   # 또는 prod
terraform destroy
```

destroy 전에:

- **prod는 번역 기록 버킷 때문에 그대로는 destroy가 실패한다.** `aws_s3_bucket.translation_records`(`accentury-translator-prompt-<account_id>`,
  KAN-266)는 `translation_records_enabled = true`(prod만)일 때 생기고 `prevent_destroy`라 plan 단계에서 막힌다. 기록은 다시 만들
  수 없으므로 버킷을 지우지 않는다. 그래도 환경을 철거해야 하면 사람이 판단해 그 버킷 관련 리소스(`aws_s3_bucket.translation_records`,
  `_versioning`, `_server_side_encryption_configuration`, `_public_access_block`, `aws_s3_bucket_policy.translation_records`)를
  `terraform state rm`으로 state에서 먼저 뺀 뒤 destroy한다. 버킷과 객체는 그대로 남는다.
- prod는 RDS 삭제 보호가 켜져 있다. 먼저 `terraform.tfvars`에서 `db_deletion_protection = false`로 바꿔 apply한 뒤 destroy한다.
  최종 스냅샷(`accentury-prod-final-<suffix>`)이 남는다. 접미사는 스택마다 다른 고정 난수라 재구축 후 다시 destroy해도 이름이
  충돌하지 않고, 남은 스냅샷은 필요 없어지면 콘솔에서 직접 지운다. staging은 `db_skip_final_snapshot = true`라 스냅샷 없이
  지워진다.
- prod를 철거하면 prod가 소유한 슬랙 Chatbot 채널 설정(`#infra-alerts`, `#daily-user`)도 함께 지워져 staging 토픽의 슬랙
  구독까지 끊긴다 (채널당 계정에 설정이 하나뿐이라 prod가 두 환경 몫을 갖는다).
- **destroy 직후 그 환경의 GitHub environment 변수 `DEPLOY_PAUSED`를 `true`로 둔다. 두 레포 모두다.** 환경이 없는데 병합이
  일어나면 배포 워크플로가 역할 부재로 `AssumeRoleWithWebIdentity` 거부라는 헷갈리는 메시지로 실패한다 (2026-08-27 실제 발생).
  이 레포(Accentury_Server)에서는 Image Deploy(`deploy.yml`)가, Accentury_App에서는 Web Deploy가 이 변수를 본다. 변수가
  `true`면 워크플로는 배포를 건너뛰고 요약에 "환경 철거 중"을 남긴다. 재구축과 변수 4개 갱신 뒤 지운다.

  ```
  # destroy 직후
  gh variable set DEPLOY_PAUSED -e staging --body true -R AISWM-2PL1/Accentury_Server
  gh variable set DEPLOY_PAUSED -e staging --body true -R AISWM-2PL1/Accentury_App
  # apply와 변수 4개 갱신 뒤
  gh variable delete DEPLOY_PAUSED -e staging -R AISWM-2PL1/Accentury_Server
  gh variable delete DEPLOY_PAUSED -e staging -R AISWM-2PL1/Accentury_App
  ```

destroy가 지우는 것과 남기는 것:

| 대상 | destroy 뒤 |
| --- | --- |
| web S3 버킷 | `force_destroy = true`라 객체째 지워진다. 번들은 파이프라인이 다시 만들 수 있는 산출물이다 |
| ai 부팅 자산 버킷 `accentury-{env}-ai-boot-<account_id>` | `force_destroy = true`라 객체째 지워진다. 재구축 apply가 다시 올린다 |
| CloudFront 배포 | 비활성화 전파 때문에 삭제에 수 분 걸린다 |
| ECS 서비스 | 태스크를 먼저 내린다 (등록 해제 30초 + stopTimeout 최대 120초) |
| 로그 그룹 `/accentury/{env}/backend`, `/accentury/{env}/ai`(KAN-203) | Terraform 소유라 로그째 지워진다. ai 스트림은 인스턴스마다 쌓이지만 그룹 하나에 딸려 있다 |
| 태스크 정의 | Terraform은 자기가 아는 리비전(최신 ACTIVE)만 deregister한다. 파이프라인이 만든 옛 리비전은 INACTIVE로 남는다. 요금이 없고 잔존 확인 대상이 아니다. 치우려면 `aws ecs delete-task-definitions` |
| 프라이빗 영역과 `ai.accentury.internal` A 레코드 | KAN-201부터 레코드는 Terraform 소유(ALB alias)라 함께 지워진다. KAN-36 시절 인스턴스가 만든 레코드가 남아 있어도 영역이 `force_destroy = true`라 레코드째 지운다 |
| AI ASG | 인스턴스를 먼저 종료한 뒤 삭제된다 |
| SNS 토픽과 이메일 구독 | 미확인 구독은 AWS가 지워 주지 않아 state에서만 빠지지만, 토픽이 삭제되면 딸린 구독도 함께 사라져 잔존물이 남지 않는다 (KAN-134). 재구축 때는 토픽이 새로 생기므로 확인 메일을 다시 눌러야 한다 |
| SSM `/accentury/{env}/IMAGE_TAG` | Terraform이 만들지 않아 남는다. 다음 재구축 때 backend 태스크와 ai 호스트가 그 SHA로 뜬다. 재구축 plan이 이 값을 요구하므로(사전 요건) 지우지 않는 편이 맞다 |
| Terraform 소유 SSM 시크릿 | 지워진다. 재구축하면 자리 표시 값으로 다시 생기므로 위 "apply 뒤 남는 수동 단계" 1번을 다시 한다 |
| 음성 전용 버킷 `accentury-voice-<account_id>` (KAN-269) | bootstrap 소유라 환경 destroy로 지워지지 않는다. bootstrap에서도 `prevent_destroy`이고 `force_destroy`가 없다. 음성은 Terraform으로 지우지 않는다 |
| 옛 학습 버킷 `accentury-staging-training-<account_id>` (KAN-201) | `removed` 블록으로 Terraform 관리에서 빠져 그대로 남는다. 사람만 정리한다 |
| prod 번역 기록 버킷 `accentury-translator-prompt-<account_id>` | 지우지 않는다 (위 destroy 전에 참고) |
| bootstrap state 버킷 | `prevent_destroy`로 보호된다. 두 환경 state가 전부 필요 없어진 것이 확실할 때만 코드에서 보호를 풀고 지운다 |

잔존 확인은 `terraform state list`에 더해 아래로 본다 (SCP가 `tag:GetResources`를 막는다).

```
aws ecs list-clusters
aws ecs list-task-definitions --status ACTIVE
aws logs describe-log-groups --log-group-name-prefix /accentury
```
