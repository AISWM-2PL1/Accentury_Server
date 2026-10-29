# backend 서비스

backend를 띄우는 ECS Fargate 서비스의 구성, 이미지 태그 정합, 태스크 재기동, 오토스케일링, 종료 동작을 다룬다.

관련 코드:
- [infra/modules/fargate](../../infra/modules/fargate) (클러스터, 태스크 정의, 서비스, 역할 2개, 오토스케일링)
- [infra/modules/config/outputs.tf](../../infra/modules/config/outputs.tf) (`parameter_names` = 태스크 정의 secrets 목록)
- [infra/bootstrap/ecs.tf](../../infra/bootstrap/ecs.tf) (Application Auto Scaling 서비스 연결 역할)
- [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) (이미지 반영)
- [scripts/load_resilience.py](../../scripts/load_resilience.py) (종료 내결함성 부하 생성기)
- `backend/src/main/java/app/accentury/backend/common/DeploymentConfigGuard.java` (기동 시 필수 설정 검사)

이미지 반영 절차는 [deploy.md](deploy.md)에 있다. 실증 기록은 docs/wiki/infra-history.md의 KAN-165, KAN-168, KAN-169 항목이다.

## 구성 (KAN-165)

backend는 `modules/fargate`가 만드는 ECS Fargate 서비스다. EC2 위 docker compose(KAN-124)를 대체했고, ai는 그대로 전용 EC2(ASG)다.

| 구성 | 값 | 비고 |
| --- | --- | --- |
| 클러스터 | `accentury-{env}`, 용량 공급자 `FARGATE`만 | `FARGATE_SPOT`은 연결하지 않는다(2026-09-01 결정). Container Insights 끔 |
| 태스크 정의 | 패밀리 `accentury-{env}-backend`, 0.5 vCPU / 2 GB, `X86_64`, 컨테이너 `backend` 1개 | image = ECR `accentury/backend:<SSM IMAGE_TAG>`, secrets = config 모듈의 SSM 파라미터(staging 28개, prod 29개, 아래), `stopTimeout` 120초, awslogs `/accentury/{env}/backend`(14일), 컨테이너 healthCheck = compose와 같은 bash `/dev/tcp` 검사(interval 10초, retries 6, startPeriod 120초) |
| 서비스 | `backend`, 처음 desired 1, 용량 공급자 전략 `FARGATE` weight 1 | 롤링 배포(min 100% / max 200%), 회로 차단기 + 자동 롤백, `health_check_grace_period_seconds` 150초, 퍼블릭 서브넷 + 퍼블릭 IP, `backend-sg`, 대상 그룹 ip:8080. 태스크 수는 오토스케일링이 1~3에서 조절하고 Terraform은 `desired_count`를 다시 보지 않는다(아래 "오토스케일링") |
| 실행 역할 | `accentury-{env}-backend-execution` | `AmazonECSTaskExecutionRolePolicy`(ECR pull, 로그) + 이 환경 config 파라미터(secrets 목록과 같다)의 `ssm:GetParameters`. ECS 에이전트 몫이라 컨테이너 안에서는 보이지 않는다 |
| 태스크 역할 | `accentury-{env}-backend-task` | 아래 표. 애플리케이션이 SDK 기본 체인으로 받는다. IMDS hop limit 조정이 없다 |

secrets 목록은 `modules/config/outputs.tf`의 `parameter_names`다. 이 중 기동 때 비어 있으면 안 되는 이름과 프로퍼티의 대응은 backend의 `DeploymentConfigGuard.SSM_NAMES`가 정본이다 ([`secrets-ssm.md`](secrets-ssm.md)). 공통 26개에 음성 저장 파라미터 2개(`training_bucket_enabled`, 두 환경 true)가 붙어 staging 28개, 번역 기록 버킷 파라미터 1개(`translation_records_enabled`, prod만 true)가 더 붙어 prod 29개다. 저장을 끈 환경의 목록에는 그 이름이 없다.

태스크 역할의 권한은 애플리케이션이 실제로 부르는 것뿐이다.

| 권한 | 대상 | 환경 |
| --- | --- | --- |
| `secretsmanager:GetSecretValue` | RDS 마스터 시크릿(KAN-129, 7일 회전이라 값을 복사하지 않는다) | 두 환경 |
| `cloudwatch:PutMetricData` | 네임스페이스 `accentury/backend` 조건(KAN-36) | 두 환경 |
| `s3:PutObject` | 음성 전용 버킷의 `<env>/*`(KAN-201, KAN-269). Get, List, Delete는 없다 | 두 환경(`training_bucket_enabled`) |
| `s3:PutObject` | 번역 기록 버킷의 `translations/*`(KAN-266). Get, List, Delete는 없다 | prod만(`translation_records_enabled`) |

ECS Exec(대화형 접속)은 일부러 켜지 않았다(2026-09-02 결정). 태스크 안에서 재현해야 하는 상황이 오면 서비스에 `enable_execute_command = true`를 주고 태스크 역할에 ssmmessages 4종(CreateControlChannel, CreateDataChannel, OpenControlChannel, OpenDataChannel)을 더한 뒤 새 배포를 강제한다.

## 이미지 태그 정합

**이미지 태그의 정본은 SSM `IMAGE_TAG` 하나다.** Terraform은 그 값을 data 소스로 읽어 태스크 정의 image에 넣고, 파이프라인은 서비스가 도는 리비전을 복제해 image만 바꾼 리비전을 등록한다. 태스크 정의가 `track_latest = true`라 Terraform은 자기가 만든 리비전이 아니라 패밀리의 최신 ACTIVE 리비전을 state로 읽는다. 그래서 파이프라인 배포 뒤에도 "SSM 태그 = 배포된 태그 = 최신 리비전의 image"가 성립해 `terraform plan`이 No changes다.

반대로 `modules/fargate`의 태스크 정의(cpu, secrets, stopTimeout 등)를 고쳐 apply하면 Terraform이 새 리비전을 만들고 서비스를 갱신해 굴린다. apply가 서비스 안정까지 기다린다. 두 주체가 같은 패밀리에 리비전을 쌓되 바꾸는 것이 다르고, **파이프라인이 실패한 리비전을 deregister하는 것이 이 정합의 조건이다.** 파이프라인이 만든 리비전에는 프로바이더 default_tags가 없어 태스크 정의의 태그는 `ignore_changes`다(비용 태그는 서비스가 태스크에 전파한다).

첫 구축에서는 SSM `IMAGE_TAG`가 먼저 있어야 plan이 선다.

**인프라 apply와 이미지 배포는 겹치지 않게 한다.** IMAGE_TAG를 plan 시점에 읽으므로, plan과 apply 사이(또는 `-out`으로 저장한 plan을 나중에 쓰는 사이)에 파이프라인이 배포하면 apply가 서비스를 옛 이미지로 되돌리고도 성공으로 끝난다. state 잠금은 apply끼리만 막고 파이프라인과는 안 겹친다. **apply 전에 Actions "Image Deploy"가 도는 중인지 확인하고, 저장한 plan은 재사용하지 않는다.**

**필수 SSM 파라미터를 더하는 변경은 apply가 먼저, 그 코드를 실은 이미지 배포가 다음이다.** 파이프라인은 태스크 정의의 secrets를 바꾸지 않으므로, 순서가 뒤집히면 `DeploymentConfigGuard`가 기동을 세우고 자동 롤백된다([deploy.md "필수 SSM 파라미터를 더할 때의 순서"](deploy.md#필수-ssm-파라미터를-더할-때의-순서)).

## 설정 값을 바꾼 뒤 재기동

secrets는 태스크가 시작할 때 한 번 읽힌다. SSM 값이 바뀌어도(관리자 토큰 재발급 등) 도는 태스크에는 반영되지 않으므로 태스크를 새로 띄운다.

```
aws ecs update-service --cluster accentury-staging --service backend --force-new-deployment
```

## 운영 확인

```
cd infra/envs/staging
cluster=$(terraform output -raw ecs_cluster_name)
aws ecs describe-services --cluster "$cluster" --services backend \
  --query 'services[0].[status,runningCount,taskDefinition,deployments[].[id,status,rolloutState,rolloutStateReason]]' --output json
aws ecs list-tasks --cluster "$cluster" --service-name backend                       # 도는 태스크
aws logs tail "$(terraform output -raw backend_log_group)" --since 10m --follow       # 컨테이너 로그
aws ecs describe-services --cluster "$cluster" --services backend --query 'services[0].events[:10]'  # 서비스 이벤트 (배포, 교체 사유)
```

## 헬스체크 유예와 회로 차단기

`health_check_grace_period_seconds`(150초)와 컨테이너 healthCheck `startPeriod`(120초)는 2026-09-02 staging 실측의 기동 시간(0.5 vCPU에서 Spring 기동 71초, 컨테이너 시작부터 ALB healthy까지 약 76초)에 여유를 더한 값이다. 이보다 짧으면 기동 중인 태스크를 ECS가 unhealthy로 판정해 교체를 반복하고, 회로 차단기가 3회에 배포를 접는다. 길면 죽은 이미지를 배포했을 때 회로 차단기 판정(실패 3회, 회당 grace period + 헬스체크 + 교체)이 그만큼 늦어진다.

회로 차단기가 실패 3회를 채우는 데 15분 안팎이 걸린다. 회당 태스크 시작 45초 + (pull 재시도 약 1분 30초 또는 grace 150초 + 헬스체크 판정) + 교체 대기이고, ECS가 실패 횟수를 평가하는 주기가 더해진다. 파이프라인의 rolloutState 대기 상한 20분은 이 값 기준이다. 그보다 빨리 되돌리려면 `thresholdConfiguration`(COUNT)이 필요한데 프로바이더 6.61에 아직 없다.

## 오토스케일링 (KAN-168)

`modules/fargate`가 backend 서비스에 Application Auto Scaling 목표 추적 정책을 붙인다. Fargate가 해 주는 것은 "늘리라고 결정된 뒤의 실행"뿐이라 이 정책이 없으면 태스크 수는 고정이다.

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 지표 | `ALBRequestCountPerTarget`(대상 그룹의 태스크당 분당 요청 수, 1분 Sum) | 이 서비스의 부하는 세션 생성과 폴링이라는 가벼운 요청의 개수라, 태스크 하나가 포화된 뒤에야 오르는 CPU 평균보다 요청 수가 스파이크에 먼저 반응한다. |
| 정책 | 목표 추적(`accentury-{env}-backend-requests-per-task`) | 목표값 하나로 늘리기와 줄이기가 같이 정해진다. |
| 목표값 | 태스크당 분당 1000건 | 2026-09-03 KAN-169 부하 시험으로 확정. 산정은 docs/wiki/infra-decisions.md |
| min / max | 1 / 3 | 2026-09-02 확정. 근거는 docs/wiki/infra-decisions.md |
| 스케일아웃 쿨다운 | 60초 | 태스크 기동(실측 2분 안팎)보다 짧게 두어 큰 스파이크에는 연달아 늘린다. |
| 스케일인 쿨다운 | 300초 | 줄이기는 천천히. 스케일인 경보 자체가 15분 조건이라 이 값은 연속 축소의 간격만 정한다. |

값은 두 환경이 같아야 하므로 tfvars가 아니라 `modules/fargate/variables.tf`의 기본값이다(`autoscaling_min_capacity`, `autoscaling_max_capacity`, `autoscaling_target_requests_per_minute`, 쿨다운 2개). 서비스의 `desired_count`는 첫 생성 값(1)일 뿐이고 그 뒤는 `ignore_changes`다. 안 그러면 스케일아웃된 상태에서 apply할 때마다 1로 되돌려 태스크를 죽인다. `desired_count`는 min과 max 사이여야 한다(variables 검증). 파이프라인(`deploy.yml`)의 `update-service`는 태스크 정의만 바꾸고 desired를 건드리지 않으므로 배포와 스케일링이 겹쳐도 된다. 서비스 연결 역할 `AWSServiceRoleForApplicationAutoScaling_ECSService`는 bootstrap이 만든다(`infra/bootstrap/ecs.tf`).

**목표 추적이 경보 2개를 스스로 만들고 조건 길이가 다르다.** 이름은 `TargetTracking-service/accentury-{env}/backend-AlarmHigh-<id>`와 `...-AlarmLow-<id>`이고(출력 `backend_autoscaling_alarm_names`), AlarmHigh(스케일아웃)는 1분 x 3회 연속, AlarmLow(스케일인)는 1분 x 15회 연속이다. 부하를 끊어도 15분은 줄지 않는다. 고장이 아니다. 부하가 없을 때 AlarmLow가 ALARM에 머무는 것도 정상이다(min 1이라 더 줄지 않을 뿐). **이 둘은 SNS에 붙이지 않는다.** 늘고 주는 것은 정상 동작이고, 상한에 닿아 못 늘리는 상태는 `backend-cpu-high`가 대신 드러낸다. monitoring 모듈의 `alarm_names`에도 없다.

동작 특성 둘(KAN-168, KAN-169 실측).
- 스케일아웃 쿨다운 60초는 경보 창(3분)에 늘리기 전의 데이터포인트가 남아 있는 동안 한 단계를 더 늘린다(1 -> 2 뒤 바로 3). 큰 스파이크에는 의도한 동작이고 15분 뒤 스케일인이 되돌린다.
- 스케일인은 알람 창 전체를 본다. 부하가 중간에 길게 끊겼다가 다시 들어오는 중이어도 15개 데이터포인트의 대부분이 낮으면 줄인다. 줄인 뒤 태스크 1개가 목표를 넘는 요청을 받으면 3분 뒤 다시 늘어나므로, 부하가 끊겼다 돌아오는 패턴에서는 이 왕복이 한 번 생길 수 있다. 그 사이 요청은 드레인과 graceful shutdown이 받는다.

함정 둘.
- 태스크가 1개일 때 `ALBRequestCountPerTarget`은 전체 요청 수와 같으므로 **목표값은 반드시 태스크당 기준으로 읽는다.**
- 폴링이 POST라 요청 수가 실제 사용자 수보다 훨씬 크게 잡히므로 **목표값을 바꿀 때는 폴링 주기(`poll-after-ms`, `congested-poll-after-ms`)를 같이 본다.**

운영 확인:

```
cd infra/envs/staging
rid=$(terraform output -raw backend_autoscaling_resource_id)
aws application-autoscaling describe-scalable-targets --service-namespace ecs --resource-ids "$rid" \
  --query 'ScalableTargets[0].[MinCapacity,MaxCapacity]' --output text
aws application-autoscaling describe-scaling-activities --service-namespace ecs --resource-id "$rid" \
  --query 'ScalingActivities[:5].[StartTime,StatusCode,Description]' --output table      # 늘고 준 이력
aws cloudwatch describe-alarms --alarm-names $(terraform output -json backend_autoscaling_alarm_names | jq -r '.[]') \
  --query 'MetricAlarms[].[AlarmName,StateValue,EvaluationPeriods,Threshold]' --output table
aws cloudwatch get-metric-statistics --namespace AWS/ApplicationELB --metric-name RequestCountPerTarget \
  --dimensions Name=TargetGroup,Value=<대상 그룹 arn_suffix> --statistics Sum --period 60 \
  --start-time "$(date -u -v-15M +%FT%TZ)" --end-time "$(date -u +%FT%TZ)" --output table   # 분당 태스크당 요청 수
```

## 종료 동작 (KAN-166, KAN-169)

**진행 중 분석은 SIGTERM에 잡히지 않는다.** stop-task든 스케일인이든 롤링 배포든 ECS는 먼저 ALB 대상을 등록 해제하고 드레인(대상 그룹 `deregistration_delay` 30초)을 끝낸 뒤에야 SIGTERM을 보낸다. 실측으로 호출부터 SIGTERM까지 약 50초였다. 그 사이 새 요청은 오지 않고 이미 받은 분석은 `ai-timeout`(실모델 85초, KAN-172) 안에 끝나므로, KAN-166 4단계 종료가 시작될 때 실행 중이거나 대기 중인 작업은 정상 경로에서 0건이다. KAN-166의 워커 배수(예산 90초)는 AI 응답이 ECS 드레인 창보다 길어질 때만 쓰이는 두 번째 방어선이다. `stopTimeout` 120초가 그 종료 예산이다.

실패 격리와 재녹음이 실제로 쓰이는 곳은 롤링 배포의 ai 컨테이너 reload 창(약 25초)이다. 그 창에 접수된 문항은 `RETRYABLE_FAILED(ANALYSIS_UNAVAILABLE)`로 격리되고 재업로드로 COMPLETED가 된다.

Flyway는 미래 버전 이력을 기본으로 무시하므로, 스키마가 앞선 DB에 옛 backend 이미지를 띄워도 기동은 막히지 않는다(KAN-169에서 확인). 옛 이미지로 되돌려도 되는지는 Flyway가 아니라 마이그레이션 내용으로 판단한다.

부하를 걸어 다시 확인하려면 `scripts/load_resilience.py`를 쓴다. CloudFront 경유 정의 조회 GET을 초당 N건 보내면서 응시자 스레드가 세션 생성, 음성 업로드, 폴링, 실패 문항 재업로드, 어휘 제출, 완료 확정을 반복하고, 세션에는 합성 트래픽 표시(KAN-138)를 붙인다.
