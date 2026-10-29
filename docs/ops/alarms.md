# 경보와 알림

CloudWatch 경보 전체 목록, 이메일(SNS)과 슬랙(AWS Chatbot) 알림 경로, 구독 확인과 경보 실증 절차다 (KAN-134, KAN-165, KAN-36, KAN-201, KAN-273).

관련 코드:

- [`infra/modules/monitoring/main.tf`](../../infra/modules/monitoring/main.tf) - SNS 토픽 `accentury-{env}-alerts`, 이메일 구독, 경보 16개
- [`infra/modules/monitoring/outputs.tf`](../../infra/modules/monitoring/outputs.tf) - `alarm_names`, `alerts_topic_arn`
- [`infra/modules/ai-host/main.tf`](../../infra/modules/ai-host/main.tf) - AI ASG 확대, 축소 경보 2개
- [`infra/modules/fargate/main.tf`](../../infra/modules/fargate/main.tf) - backend 목표 추적 오토스케일링(경보 2개는 AWS가 만든다)
- [`infra/modules/ai-host/ai-health-metric.sh`](../../infra/modules/ai-host/ai-health-metric.sh) - `accentury/ai` 지표를 올리는 호스트 타이머
- [`infra/modules/slack-alerts`](../../infra/modules/slack-alerts) - 슬랙 토픽, Chatbot 채널 설정, EventBridge 규칙, Lambda `scale_notify`와 `voice_report`, Scheduler
- `infra/envs/*/variables.tf` - `alert_email`, `slack_team_id`, `slack_infra_alerts_channel_id`, `slack_daily_user_channel_id` 기본값
- `infra/envs/*/terraform.tfvars` - `voice_report_enabled`, `slack_channel_configs_enabled`, `slack_infra_alerts_peer_envs`

실증 기록과 측정치는 [docs/wiki/infra-history.md](../wiki/infra-history.md)의 KAN-134, KAN-165, KAN-36, KAN-273 항목에, 경보 설계 이유는 [docs/wiki/infra-decisions.md](../wiki/infra-decisions.md)에 있다. 관측성 지표와 그 위의 경보 상세는 [observability.md](./observability.md)다.

## 경보 전체 목록

prod는 무인으로 돈다. 서버가 죽은 것을 사용자보다 먼저 알아야 하므로 SNS 토픽
하나(`accentury-{env}-alerts`)에 이메일 구독을 걸고 CloudWatch 경보를 붙인다. 두 환경 각 1벌이다.
경보 이름은 전부 `accentury-{env}-` 접두사가 붙는다. 아래 표는 2026-10-29 코드 기준이다.

### monitoring 모듈 (16개, 알림 경보)

전부 같은 토픽으로 ALARM과 OK 양쪽을 알린다. 해제 알림이 없으면 아직 죽어 있는지를 콘솔에서 확인해야 하기 때문이다. "슬랙"은 `#infra-alerts`에도 가는 경보다.

| 경보 | 지표 | 조건 | 결측 처리 | 슬랙 | 출처 |
| --- | --- | --- | --- | --- | --- |
| `no-healthy-target` | `AWS/ApplicationELB` `HealthyHostCount` (backend 대상 그룹) | 1분 최소 < 1이 2회 연속 | `breaching` | | KAN-165 |
| `alb-5xx` | `HTTPCode_ELB_5XX_Count` + `HTTPCode_Target_5XX_Count` 합 (지표 math) | 1분 합계 > 5가 2회 연속 | `notBreaching` | | KAN-134 |
| `rds-free-storage` | `FreeStorageSpace` | 5분 최소 < 2GiB | `missing` | | KAN-134 |
| `backend-cpu-high` | `AWS/ECS` `CPUUtilization` (ClusterName + ServiceName) | 1분 평균 >= 80%가 5회 연속 | `notBreaching` | | KAN-165 |
| `backend-mem-high` | `AWS/ECS` `MemoryUtilization` (ClusterName + ServiceName) | 1분 평균 >= 85%가 3회 연속 | `notBreaching` | | KAN-165 |
| `ai-alb-no-healthy-host` | `AWS/ApplicationELB` `HealthyHostCount` (AI 내부 ALB 대상 그룹) | 1분 최소 < 1이 3회 연속 | `breaching` | | KAN-201 |
| `ai-unhealthy` | `accentury/ai` `Healthy` (차원 env, ai 호스트 타이머) | 1분 최대 < 1이 3회 연속 | `breaching` | | KAN-36, 통계는 KAN-201 |
| `ai-circuit-open` | `accentury/backend` `accentury.ai.circuit.state.value` (차원 env, backend Micrometer) | 1분 최대 >= 2(열림)가 2회 연속 | `notBreaching` | | KAN-36 |
| `ai-disk-high` | `accentury/ai` `RootDiskUsedPercent` (차원 env, ai 호스트 타이머) | 1분 최대 >= 80%가 3회 연속 | `notBreaching` | 예 | KAN-36 B단계 |
| `ai-mem-high` | `accentury/ai` `MemoryUsedPercent` (차원 env, 호스트 대비, ai 호스트 타이머) | 1분 최대 >= 90%가 2회 연속 | `notBreaching` | | KAN-36 B단계 |
| `ai-temp-residue` | `accentury/ai` `TempFiles` | 5분 최대 >= 20이 2회 연속 | `notBreaching` | | KAN-38 |
| `analysis-backlog-high` | `accentury.analysis.processing.value` | 1분 최대 >= 12가 5회 연속 | `notBreaching` | | KAN-38, 임계 KAN-172 |
| `analysis-timeouts-high` | `accentury.analysis.timeouts.count` (stuck + lost 합) | 5분 합 > 5 | `notBreaching` | | KAN-38 |
| `analysis-unscorable-high` | `accentury.analysis.judged.count` (reason unscorable) | 5분 합 > 5 | `notBreaching` | | KAN-272 |
| `translation-quota-exhausted` | `accentury.translation.llm.calls.count` (outcome rate_limited) | 5분 합 > 0 | `notBreaching` | | KAN-266 |
| `result-wait-high` | `accentury.result.wait.percentile.value` (phi 0.95) | 1분 최대 >= 30초가 3회 연속 | `notBreaching` | 예 | KAN-284 |

앞의 열 개는 "서버가 죽었다"를 알리는 층이고, 뒤의 여섯 개는 "서버는 살아 있는데 파이프라인이 고장 났다"를 보는 관측성 층이다. 관측성 경보의 임계 근거와 대시보드는 [observability.md](./observability.md)에 있다.

AI 호스트가 내부 ALB 뒤에 있으므로(KAN-201) "받아 줄 healthy AI가 없다"의 정본은 `ai-alb-no-healthy-host`다. 대상 그룹 상태 검사가 준비 상태 게이트(`/internal/v0/health`, 워밍업 전 503)를 보므로 모델이 적재된 대상만 센다. `ai-unhealthy`는 ALB나 대상 그룹 자체가 사라진 경우까지 덮는 이중 안전망이다. 통계가 Maximum인 이유는 인스턴스가 여럿이면 같은 차원(env)으로 섞여 올라오기 때문이고, `Maximum < 1`은 "어느 인스턴스도 healthy가 아니다"라는 뜻이다.

`ai-unhealthy`, `ai-disk-high`, `ai-mem-high`, `ai-temp-residue`의 지표는 ai 호스트의 systemd 타이머(`ai-health-metric.sh`)가 1분마다 `aws cloudwatch put-metric-data`로 올린다(호스트 역할의 PutMetricData는 네임스페이스 `accentury/ai`로만 허용, CloudWatch agent 없음). 지표는 health와 무관하게 호스트가 뜬 직후부터 나간다. backend 지표(`accentury/backend`)는 Micrometer CloudWatch 레지스트리(`CloudWatchMetricsConfig`, 배포 프로파일에서만)가 `accentury.*`만 1분마다 올리고, 게이지는 이름에 `.value`가 붙는다. 회로 상태 값은 0 닫힘, 1 반열림, 2 열림이다.

### ai-host 모듈 (2개, 스케일링 경보)

알림이 아니라 ASG 스텝 스케일링 정책을 부른다. 메일은 가지 않고, 스케일 결과는 슬랙 `#infra-alerts`에 Lambda `scale_notify`가 알린다(아래 슬랙 절).

| 경보 | 지표 | 조건 | 동작 |
| --- | --- | --- | --- |
| `ai-scale-out` | `accentury.analysis.processing.value` (Maximum) | 1분 최대 >= 6이 1회 | +1 (워밍업 300초 동안 재확대 안 함) |
| `ai-scale-in` | `accentury.analysis.processing.value` (Maximum) | 1분 최대 <= 1이 15회 연속 | -1 (min까지) |

### backend 오토스케일링 경보 (2개, AWS 생성)

`modules/fargate`의 목표 추적 정책(`ALBRequestCountPerTarget`)이 AlarmHigh(1분 x 3회), AlarmLow(1분 x 15회) 두 경보를 스스로 만든다. Terraform 리소스가 아니고 `alarm_names`에도 없다. 이름은 output `backend_autoscaling_alarm_names`로 본다.

### 결측 처리와 해제의 특성

- `no-healthy-target`, `ai-alb-no-healthy-host`, `ai-unhealthy`는 결측을 장애로 센다. 그래서 스택을 새로 apply한 직후와 ai 인스턴스 교체 직후 몇 분은 한 번 울고, 대상이 뜨면 OK 알림이 따라온다.
- 나머지는 결측을 정상으로 센다. 트래픽이 0인 시간대에는 지표가 나오지 않기 때문이다.
- `alb-5xx`가 이미 ALARM인 상태에서 트래픽이 완전히 끊기면 해제되지 않고 ALARM에 머물 수 있다. 경보를 다시 평가하려면 데이터포인트가 최소 하나는 필요하다. 정상 요청이 들어오면 OK로 돌아온다.
- `rds-free-storage`의 `missing`은 staging RDS를 중지한 동안 상태를 바꾸지 않는다는 뜻이다.
- 토픽에 SSE를 켜지 않는다. 켜면 경보는 ALARM인데 메일만 조용히 안 오는 상태가 된다(이유는 decisions).

## 수신 주소와 구독 확인

수신 주소는 `envs/{env}/variables.tf`의 `alert_email` 기본값이다.
두 환경이 같은 값이라 `github_repositories`와 같은 이유로 tfvars가 아니라 기본값에 둔다.
환경별로 나누려면 해당 tfvars에 `alert_email = "..."` 한 줄을 넣으면 된다.

SNS 이메일 구독은 Terraform이 확인까지 해 줄 수 없다. apply 직후 상태는
`PendingConfirmation`이고 수신자가 AWS 메일의 링크를 눌러야 활성화된다. **누르기
전에는 경보가 울려도 메일이 나가지 않는다.** 환경을 새로 지을 때마다 확인한다.

```
aws sns list-subscriptions-by-topic \
  --topic-arn "$(terraform output -raw alerts_topic_arn)" \
  --query 'Subscriptions[].[Protocol,Endpoint,SubscriptionArn]' --output table
```

`SubscriptionArn`이 `PendingConfirmation`이면 아직 안 누른 것이고, `arn:aws:sns:...`
형태면 활성이다.

## 경보 상태 확인

```
aws cloudwatch describe-alarms \
  --alarm-names $(terraform output -json alarm_names | jq -r '.[]') \
  --query 'MetricAlarms[].[AlarmName,StateValue,StateReason]' --output table
```

output `alarm_names`는 15개만 준다. `translation-quota-exhausted`가 빠져 있다. 전부 보려면 접두사로 조회한다. 이때는 ai 호스트 스케일링 경보 2개도 함께 잡혀 18개다. backend 목표 추적 경보 2개는 이름이 `TargetTracking-`로 시작해 접두사 조회에 잡히지 않는다 (output `backend_autoscaling_alarm_names`).

```
aws cloudwatch describe-alarms --alarm-name-prefix accentury-staging- \
  --query 'MetricAlarms[].[AlarmName,StateValue]' --output table
```

## 경보 실증 절차

측정 결과는 [docs/wiki/infra-history.md](../wiki/infra-history.md)에 있다.

### backend 경보 (`no-healthy-target`, `alb-5xx`)

서비스의 태스크 수를 0으로 내려서 한다. 태스크 하나를
`stop-task`로 죽이면 ECS가 2분에서 3분 안에 교체해 버려 `no-healthy-target`의 2회 연속과
`alb-5xx`의 2분이 채 안 찬다. desired 0이면 대상
그룹이 비어 `HealthyHostCount` 0과 ALB 503이 트래픽 있는 동안 함께 나온다. Terraform이
desired_count 1을 아는 상태라 **실증 뒤 반드시 1로 되돌리고** `terraform plan`이 No changes인지 본다.

```
aws ecs update-service --cluster accentury-staging --service backend --desired-count 0
```

여기까지로 `no-healthy-target`이 선다. 대상이 0개가 되면 지표가 결측이 되고 결측이 breaching이라 2회 연속 조건이 충족된다(결측 유예 때문에 수 분 더 걸린다).

`alb-5xx`는 **2분 이상 요청을 계속 보내야** 선다. 20건을 한 번에 몰아치면 그것이
전부 한 분에 들어가고 다음 분은 결측이 되는데, 결측이 `notBreaching`이라 "2회 연속"이
성립하지 않는다. 3분 동안 흘려보낸다.

```
end=$(( $(date +%s) + 180 ))
while [ "$(date +%s)" -lt "$end" ]; do
  for i in $(seq 1 6); do
    curl -s -o /dev/null -w '%{http_code} ' \
      "https://<staging 도메인>/v0/tests/<활성 버전>"
  done
  echo " @ $(date +%H:%M:%S)"
  sleep 10
done
```

분당 약 36건이라 임계치 5를 세 분 연속 넘긴다. 복구는 태스크 수를 되돌리면 된다.
새 태스크가 healthy가 되면 두 경보가 OK로 돌아온다.

```
aws ecs update-service --cluster accentury-staging --service backend --desired-count 1
```

### AI 경보 (`ai-alb-no-healthy-host`, `ai-unhealthy`, `ai-circuit-open`)

ai 호스트의 컨테이너를 내려서 실증한다. 회로는 backend가 AI 호출에
연속 5회 실패하면 열리므로(`circuit-failure-threshold: 5`, KAN-28) 컨테이너를 내린 채 녹음 업로드를 5건 이상 보낸다 (E2E
스모크의 업로드 단계, 또는 앱). `ai-alb-no-healthy-host`와 `ai-unhealthy`는 트래픽 없이 3분 안팎에 선다.

```
aws ssm send-command --document-name AWS-RunShellScript \
  --targets Key=tag:Name,Values=accentury-staging-ai \
  --parameters 'commands=["cd /opt/accentury && docker compose --env-file /run/accentury/compose.env stop ai"]'
# 복구
aws ssm send-command --document-name AWS-RunShellScript \
  --targets Key=tag:Name,Values=accentury-staging-ai \
  --parameters 'commands=["systemctl reload accentury"]'
```

ASG 교체는 인스턴스를 강제 종료해 본다. 새 인스턴스가 뜨고 AI 대상 그룹에 등록돼 healthy가 되고
backend 회로가 닫힐 때까지의 시간을 티켓에 기록한다. 인스턴스가 여러 대면 `Instances[0]`만 종료된다.

```
id=$(aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names accentury-staging-ai \
  --query 'AutoScalingGroups[0].Instances[0].InstanceId' --output text)
aws ec2 terminate-instances --instance-ids "$id"
```

교체 중 업로드가 없으면 회로는 닫힌 채다.

### AI 호스트 자원 경보 (`ai-disk-high`, `ai-mem-high`)

임계를 잠깐 낮춰 실증한다. `put-metric-alarm`으로 임계만 낮춰(예: 디스크 80 -> 20, 메모리 90 -> 50)
ALARM을 보고, `terraform apply`가 그 드리프트를 되돌리는 것으로 OK를 본다. **실증 뒤 반드시 apply로 임계를 되돌리고** `terraform plan`이 No changes인지 본다. 디스크를 실제로
채우는 방법(`fallocate`)도 지표 경로가 같아 결과가 다르지 않다. 실제 임계(80, 90)로 ALARM이 서는
데는 조건 길이(디스크 3분, 메모리 2분)가 더해진다.

## 슬랙 알림 (KAN-273)

이메일과 별도로 AWS Chatbot(콘솔 이름 "Amazon Q Developer in chat applications")이 SNS 토픽을 슬랙 채널로 옮긴다
(`infra/modules/slack-alerts`). 이메일 토픽(`accentury-{env}-alerts`)에는 Chatbot을 붙이지 않는다. 붙이면 모든
경보가 슬랙으로 간다.

| 채널 | 환경 | 내용 | 경로 |
| --- | --- | --- | --- |
| `#infra-alerts` | 두 환경 | AI ASG 오토스케일링 확대와 축소, 시작 실패 | ASG 이벤트 -> EventBridge 규칙 3개 -> Lambda `accentury-{env}-scale-notify`(희망 용량 조회) -> `accentury-{env}-slack-infra-alerts` -> Chatbot |
| `#infra-alerts` | 두 환경 | `ai-disk-high`와 `result-wait-high`(KAN-284)의 ALARM과 OK (이메일도 그대로 간다) | 경보 -> `accentury-{env}-slack-infra-alerts` -> Chatbot |
| `#daily-user` | prod만 (`voice_report_enabled`) | 응시 현황 리포트, KST 08시부터 24시까지 짝수 정각 | Scheduler -> Lambda `accentury-prod-voice-report` -> `accentury-prod-slack-daily-user` -> Chatbot |

- 배포의 instance refresh(이미지 배포, AMI 교체)가 내는 시작과 종료는 알리지 않는다. 규칙이 이벤트의 `Cause`에
  `instance refresh`가 들어 있으면 거른다. 시작 실패는 원인과 무관하게 알린다.
- 메시지 머리의 `[staging]`/`[prod]`로 환경을 구분한다. 두 환경이 `#infra-alerts` 하나를 함께 쓴다.
- 확대와 축소 알림의 제목은 `[prod] AI 호스트 1대 늘었습니다 (현재: 3대)`처럼 변경 뒤 희망 용량을 붙인다. 슬랙 배너에는
  제목만 보인다. 이벤트에는 대수 필드가 없어(Cause 문장 끝 `from 2 to 3`에만 있다) Lambda `scale_notify`가 ASG의
  `DesiredCapacity`를 조회해 붙이고, 조회가 실패하면 Cause의 숫자로, 그것도 없으면 대수 없이 보낸다. 시작 실패 제목에는
  붙이지 않는다 (희망 용량은 늘었지만 실제로 늘지 않았다). 문구는 `scale_notify/scale_notify.py`의 `TITLES`다.
- Chatbot 채널 설정은 서울에 API가 없어 `aws.chatbot` provider(us-east-2)로 만든다. 토픽과 규칙과 Lambda는 서울이다.
- 슬랙 채널 하나에는 계정당 Chatbot 채널 설정을 하나만 만들 수 있다. 그래서 prod(`slack_channel_configs_enabled = true`)가 두 채널 설정을 모두 갖고, `#infra-alerts`
  설정에 staging 토픽(`slack_infra_alerts_peer_envs = ["staging"]`)도 붙인다. staging은 토픽과 규칙, 경보만 만든다.
  처음 적용도 staging(토픽 생성)이 먼저다.
- 채널 역할에는 정책이 없고 가드레일은 `CloudWatchReadOnlyAccess`다. 슬랙에서 AWS 명령을 실행하지 않는다.

### staging을 다시 지은 뒤

**staging을 부수고 다시 지으면 토픽이 새로 생겨 구독이 끊긴다.** 토픽 ARN은 이름이 같아 그대로이므로 prod plan은
"No changes"로 나오고 그냥 apply해서는 복구되지 않는다. prod에서 채널 설정을 다시 만든다.

```
terraform -chdir=infra/envs/prod apply -replace='module.slack_alerts.aws_chatbot_slack_channel_configuration.infra[0]'
```

확인은 `aws sns list-subscriptions-by-topic --topic-arn <staging 토픽 ARN>`에 `global.sns-api.chatbot.amazonaws.com` 구독이 있는지다.

### 처음 한 번 사람이 하는 일

Terraform이 못 한다.

1. 슬랙에 `#infra-alerts`와 `#daily-user` 채널을 만든다.
2. https://us-east-2.console.aws.amazon.com/chatbot/home#/chat-clients 에서 **Configure new client** > Slack을
   골라 워크스페이스를 승인한다. 콘솔 검색의 "Amazon Q Developer"는 Kiro(코딩 도구) 화면이라 다른 곳이다.
3. 승인 화면의 워크스페이스 ID와 두 채널 ID(채널 이름 클릭 > 맨 아래)를 `envs/*/variables.tf`의
   `slack_team_id`, `slack_infra_alerts_channel_id`, `slack_daily_user_channel_id` 기본값에 넣는다. 시크릿이 아니다.
4. 두 채널에서 `/invite`로 승인한 앱(슬랙에 "Amazon Q" 계열 이름으로 보인다)을 초대한다. 초대 전에는 apply가 성공해도 메시지가 오지 않는다.

### 응시 현황 리포트의 숫자 정의

`voice_report/voice_report.py`가 정의한다. 음성 버킷의 키와 `LastModified`만 본다. Lambda 역할에는
`prod/` 접두의 `s3:ListBucket`만 있고 `GetObject`가 없다. 음성 버킷 정책은 List를 거부하지 않으므로 bootstrap은 바꾸지 않는다.

| 값 | 정의 |
| --- | --- |
| 동의 세션 | WAV가 하나라도 있다 |
| 미동의 세션 | `_no-audio` 아래 JSON만 있다 (`_no-audio`에는 미동의 세션의 음성 라벨과 모든 세션의 단어 정오가 함께 쌓인다) |
| 완주 | 서로 다른 문항(itemId) 수가 그 버전의 기록 문항 수 이상. 기본 7, `gn-2026.10.1`은 3(단어 정오 기록 전), `gn-2026.09.4`는 5 |
| 진행 중 / 이탈 | 완주 전이고 마지막 객체가 30분(세션 TTL) 안이면 진행 중, 아니면 이탈 |
| 완주율 | 완주 / (완주 + 이탈) |
| 구간 소속 | 세션의 첫 객체 시각. 08시 리포트는 00~08시, 00시(24시) 리포트의 "오늘"은 막 끝난 날 |

`_no-audio`가 아닌 다른 밑줄 조각(`_learning` 등, 학습 기능의 음성)은 레벨테스트 세션이 아니라 세지 않는다. 학습 녹음은 이 리포트에 나오지 않는다.

새 레벨테스트 버전이 7문항이 아니면 `voice_report.py`의 `DEFAULT_ITEMS_PER_VERSION`에 더한다. 세션 시작만 하고
문항을 하나도 내지 않은 방문자는 S3에 흔적이 없어 세지 않는다.

### 확인과 실증

```
# 리포트를 지금 한 번 보내기 (prod)
aws lambda invoke --function-name accentury-prod-voice-report /dev/stdout

# 디스크 경보를 슬랙에 띄웠다 되돌리기 (이메일도 함께 간다)
aws cloudwatch set-alarm-state --alarm-name accentury-staging-ai-disk-high --state-value ALARM --state-reason "KAN-273 실증"
aws cloudwatch set-alarm-state --alarm-name accentury-staging-ai-disk-high --state-value OK --state-reason "KAN-273 실증"

# 스케일 알림 (staging, 수 분 뒤 축소 경보가 다시 1대로 되돌린다)
aws autoscaling set-desired-capacity --auto-scaling-group-name accentury-staging-ai --desired-capacity 2
```

### 끄기

끄려면 채널 설정을 지운다(`aws_chatbot_slack_channel_configuration`). 리포트만 끄려면 prod tfvars의
`voice_report_enabled = false`다.

## 비용

CloudWatch 표준 경보는 개당 월 0.10달러, 지표 math 경보는 지표 수만큼 센다(`alb-5xx`는 2개라 0.20달러). 아래는 2026-09-10 시점(KAN-134, KAN-165, KAN-36의 9종) 산정이다. 경보 9종에 환경당 월 약 1.00달러, 커스텀 지표(`Healthy`, `RootDiskUsedPercent`,
`MemoryUsedPercent` 3개 + backend `accentury.*` 약 5개)가 이름당 월 0.30달러로 약 2.40달러다. SNS 이메일 알림은 월 1000건까지 무료다. 두 환경
합산 월 5달러 안팎이라 상시 켜 둔다. 그 뒤 더해진 경보(`ai-alb-no-healthy-host`와 관측성 6종)와 지표의 몫은 [observability.md](./observability.md)의 비용을 본다.

슬랙 알림의 EventBridge 규칙과 Scheduler, Lambda(리포트 하루 9회 + 스케일 이벤트마다 1회, 수 초), SNS는 모두 무료 한도 안이다.
