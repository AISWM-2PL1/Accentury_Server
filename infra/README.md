# infra - Terraform으로 staging, prod 환경 구축 (KAN-140)

AWS 스택 2벌(staging, prod)을 같은 모듈에서 tfvars만 바꿔 짓는다.
콘솔로 먼저 짓고 나중에 코드를 맞추는 순서는 금지다 (KAN-140).

이 파일은 무엇이 있고 어디를 보면 되는지만 담는다. 운영 절차는 [`docs/ops/`](../docs/ops), 실증 기록과 설계
결정은 [`docs/wiki/`](../docs/wiki)에 있다 (KAN-297에서 나눴다).

## 문서 지도

| 하려는 일 | 문서 |
| --- | --- |
| Terraform 작업 규칙, 처음 구축, 재구축, 철거, staging 끄기와 켜기 | [`docs/ops/teardown.md`](../docs/ops/teardown.md) |
| GitHub 환경, 변수, 브랜치 보호, Dependabot | [`docs/ops/github.md`](../docs/ops/github.md) |
| 이미지 배포, 롤백, Release 승격, 정적 자산 게시(App Link, 등급 이미지), 모델 베이스 이미지 push | [`docs/ops/deploy.md`](../docs/ops/deploy.md) |
| backend Fargate 서비스와 오토스케일링 | [`docs/ops/backend.md`](../docs/ops/backend.md) |
| ai 호스트(ASG, 내부 ALB, 부팅, AMI 갱신, 교체) | [`docs/ops/ai-host.md`](../docs/ops/ai-host.md) |
| SSM 파라미터, 시크릿 회전, 재구축 뒤 자리 표시 값 주입 | [`docs/ops/secrets-ssm.md`](../docs/ops/secrets-ssm.md) |
| WAF, 전송 구간 TLS, GuardDuty와 CloudTrail | [`docs/ops/waf-edge.md`](../docs/ops/waf-edge.md) |
| 경보, 슬랙 알림 | [`docs/ops/alarms.md`](../docs/ops/alarms.md) |
| 지표와 대시보드 | [`docs/ops/observability.md`](../docs/ops/observability.md) |
| RDS 운영자 접속, Flyway 이력 복구 | [`docs/ops/rds-flyway.md`](../docs/ops/rds-flyway.md) |
| 실증 결과와 측정치 | [`docs/wiki/infra-history.md`](../docs/wiki/infra-history.md) |
| 설계 결정과 그 이유 | [`docs/wiki/infra-decisions.md`](../docs/wiki/infra-decisions.md) |
| 개인정보처리방침, App Link 검증 파일, 등급 이미지 원본 | [`privacy/`](privacy/README.md), [`well-known/`](well-known/README.md), [`share-assets/`](share-assets/README.md) |

## 구조

```
infra/
  bootstrap/          계정 공유 리소스. 여기만 로컬 state.
                      state 백엔드(S3), ECR 리포지토리 3개, GitHub OIDC 공급자, ECS 서비스 연결 역할,
                      음성 전용 버킷 accentury-voice-<계정>(voice.tf, KAN-269), CloudTrail과 GuardDuty(audit.tf, KAN-245)
  modules/
    network/          VPC, 서브넷, 보안 그룹 6종(alb, backend, rds, redis, ai_alb, ai), 내부 호출용 프라이빗 DNS 영역 (KAN-121, KAN-36, KAN-165, KAN-201, KAN-223)
    data/             RDS PostgreSQL 16 (KAN-122), ElastiCache Redis 7.1 복제 그룹 - Refresh 토큰과 번역 일일 한도 (KAN-223, KAN-266)
    fargate/          backend ECS Fargate 서비스 - 클러스터, 태스크 정의, 서비스, 실행 역할과 태스크 역할, 로그 그룹 (KAN-165), 목표 추적 오토스케일링 min 1 max 3 (KAN-168)
    ai-host/          ai 전용 EC2 - ASG(min 1 max 3), 내부 ALB, 부팅 자산 S3 버킷, 운영 compose, 기동 스크립트, systemd 유닛, egress 가드와 health 타이머 (KAN-124, KAN-36, KAN-201)
    ai-ami/           ai 호스트 사전 적재 AMI용 EC2 Image Builder 파이프라인. 만들었지만 쓰지 않는다 (KAN-285, prod만 켬)
    config/           backend, ai 환경 변수의 정본인 SSM 파라미터와 시크릿 (write-only 시크릿은 state에 값이 없다, KAN-242) (KAN-129, KAN-36)
    edge/             internal ALB(대상 그룹 ip), VPC 오리진, CloudFront, web S3 버킷 (KAN-125, KAN-126)
    waf/              CloudFront 앞단 웹 ACL. us-east-1 프로바이더로 호출한다 (KAN-149, KAN-244)
    monitoring/       SNS 이메일과 CloudWatch 경보 16개, 운영 대시보드 1개 (KAN-134, KAN-165, KAN-36, KAN-38, KAN-201, KAN-266, KAN-284)
    slack-alerts/     슬랙 Chatbot 채널 설정, 스케일링 알림 Lambda(scale_notify), 음성 수집 리포트 Lambda와 Scheduler (KAN-273)
    deploy/           GitHub Actions가 OIDC로 맡는 환경별 배포 역할 (KAN-127)
  envs/
    staging/          main.tf + terraform.tfvars
    prod/             main.tf + terraform.tfvars (번역 기록 버킷은 prod tfvars만 켠다, KAN-266)
  privacy/            개인정보처리방침 원본과 게시 테스트 (KAN-133)
  well-known/         App Link 검증 파일 원본 (KAN-32)
  share-assets/       등급 공유 이미지 원본 (KAN-132)
```

workspace가 아니라 디렉토리 분리를 쓴다. workspace는 현재 선택된 환경이 눈에
보이지 않아 prod에 실수로 apply할 위험이 있다. 디렉토리가 갈려 있으면 어느
환경을 만지는지 경로로 드러난다.

`envs/staging`과 `envs/prod`는 `terraform.tfvars`와 `backend.tf`(state key)만
다르고 나머지 파일은 동일해야 한다. 확인:

```
diff -r infra/envs/staging infra/envs/prod
```

## 구성도

요청은 위에서 아래로 한 줄로만 내려가고, 각 단계는 바로 앞 단계의 보안 그룹만 허용한다 (참조 사슬, KAN-121).
인터넷에서 ALB, 8080, 8000, 5432, 6379에 직접 닿을 길이 없고, ai는 ai 내부 ALB만 8000으로 부를 수 있으며
backend는 요청마다 공유 시크릿 헤더를 싣는다.

```
사용자 (Android, iOS 앱 / 스탠드얼론 웹)
  │  https://accentury.app  (staging: 추측 불가 서브도메인, KAN-206. 이름은 envs/staging/terraform.tfvars의 domain)
  ▼
Route 53 호스팅 영역 ── Porkbun에서 NS 위임 ── ACM 인증서 2장 (us-east-1, 서울)   KAN-119
  │
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ CloudFront 배포 (단일 출처, PriceClass_200)                 KAN-126 │
│   [WAF 웹 ACL us-east-1: 관리형 3종 + rate 3종]     KAN-149, KAN-244 │
│   기본 /*            → S3 web 버킷 (SPA 재작성 Function)          │
│   /v0/*  /admin/v0/* → VPC 오리진 (캐싱 끔)                       │
└──────┬─────────────────────────────────┬─────────────────────────┘
       │                                 │ VPC 오리진 ENI
       ▼                                 │ (CloudFront-VPCOrigins-Service-SG)
  S3 web 버킷                            │
  (웹 번들, 등급 이미지 KAN-132,         │
   개인정보처리방침 KAN-133,             │
   App Link 검증 파일 KAN-32)            │
                                         │
═══════ VPC (staging 10.1.0.0/16 | prod 10.0.0.0/16, 피어링/NAT/VPC 엔드포인트 없음) ═══════ KAN-121
                                         │
  사설 서브넷 x2 (AZ a, c)               ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ internal ALB  alb-sg: VPC 오리진 SG → 443만 (HTTPS)     KAN-125 │
  │   대상 그룹 ip:8080, 헬스체크 /actuator/health 10초 x 3  KAN-131 │
  │   등록 해제 지연 30초                                    KAN-166 │
  └──────────────────────────┬───────────────────────────────────┘
                             │ 8080 (backend-sg: alb-sg만)
  퍼블릭 서브넷 x2           ▼
  ┌──────────────────────────────────────────────────────────────┐
  │ ECS 클러스터 accentury-{env}  용량 공급자 FARGATE만       KAN-165 │
  │   서비스 backend  태스크 1~3개(요청 수 목표 추적, KAN-168), 롤링 배포, 회로 차단기 + 자동 롤백 │
  │   태스크 정의 accentury-{env}-backend  0.5 vCPU / 2 GB, x86_64   │
  │   ┌──────────────┐  awsvpc ENI + 퍼블릭 IP                      │
  │   │ backend :8080│ Spring Boot, secrets = SSM (staging 28개, prod 29개), 로그 → CloudWatch │
  │   └──┬───┬───┬───┘  stopTimeout 120초 (KAN-166), 회로 상태 지표    │
  └──────┼───┼───┼────────────────────────────────────────────────┘
         │   │   │ http://ai.accentury.internal:8000 (= ai 내부 ALB alias, KAN-201)
         │   │   │ + X-Accentury-Internal-Token (ai-alb-sg: backend-sg만 8000)
         │   │   ▼
         │   │  내부 ALB accentury-{env}-ai-alb ── 대상 그룹 8000 (least_outstanding_requests)   KAN-201
         │   │   │ (ai-sg: ai-alb-sg만 8000)
         │   │   ▼
         │   │  ┌──────────────────────────────────────────────────────┐
         │   │  │ ai 호스트 EC2 c7i.xlarge x86, ASG min 1 max 3     KAN-36 │
         │   │  │   PROCESSING >= 6 이면 +1, <= 1 이면 -1 (KAN-201)      │
         │   │  │   루트 40GiB, ai 컨테이너 mem_limit 7g                 │
         │   │  │   iptables DOCKER-USER: 컨테이너 → VPC 밖 egress DROP  │
         │   │  │   IMDSv2 hop limit 1, health 타이머 → CloudWatch       │
         │   │  │   docker compose (docker-compose.ai.yml)               │
         │   │  │   ┌────────────────┐                                   │
         │   │  │   │ ai :8000        │ FastAPI 워커 1 + 실모델 워커 프로세스 1 │
         │   │  │   └────────────────┘                                   │
         │   │  └──────────────────────────────────────────────────────┘
         │   │ 6379 TLS (redis-sg: backend-sg만)
         │   ▼
         │  ┌──────────────────────────────────────────────────────┐
  사설   │  │ ElastiCache Redis 7.1 cache.t4g.micro, 노드 1개  KAN-223 │
  서브넷 │  │   Refresh 토큰, 번역 일일 한도 (KAN-266)               │
         │  └──────────────────────────────────────────────────────┘
         │ 5432 (rds-sg: backend-sg만)
         ▼
  ┌──────────────────────────────────────────────────────────┐
  │ RDS PostgreSQL 16  db.t4g.micro, 단일 AZ, 백업 7일  KAN-122 │
  │   마스터 비밀번호 = Secrets Manager 관리형                 │
  │   스키마 = Flyway 마이그레이션                      KAN-123 │
  └──────────────────────────────────────────────────────────┘

밖으로 거는 연결 (퍼블릭 서브넷 + 퍼블릭 IP라 NAT도 VPC 엔드포인트도 없다)
  ├─ backend 실행 역할: ECR pull, CloudWatch Logs, SSM /accentury/{env}/* → secrets            KAN-165
  │    (태스크가 시작할 때 ECS 에이전트가 읽어 컨테이너 env로 준다. 정본은 config 모듈 KAN-129)
  ├─ backend 태스크 역할: Secrets Manager RDS 마스터 시크릿(연결 시점, 7일 회전 추종)            KAN-129
  │    CloudWatch PutMetricData accentury/backend (Micrometer)                                 KAN-36, KAN-38
  │    S3 음성 버킷 accentury-voice-<계정>/<env>/* PutObject (동의한 세션만)                     KAN-269
  │    S3 번역 기록 버킷 accentury-translator-prompt-<계정>/translations/* PutObject (prod만)    KAN-266
  ├─ backend → Gemini API (번역, HTTPS 443)                                                    KAN-266
  ├─ ai 호스트: SSM Session Manager, SSM /accentury/{env}/ai/*와 IMAGE_TAG → ai.env, ECR pull,
  │    부팅 자산 S3 버킷, CloudWatch accentury/ai (health 타이머)                                KAN-36, KAN-38
  ├─ SSM IMAGE_TAG (파이프라인 KAN-128이 쓴다): ai 호스트가 compose 보간에, Terraform이 backend
  │    태스크 정의 image에 읽는다 - 두 서비스가 같은 SHA다                                     KAN-165
  ├─ ECR accentury/backend, accentury/ai (commit SHA 태그)                                      KAN-120
  └─ ECR accentury/ai-model (실모델 베이스 이미지, 모델 해시 태그, 사람이 push)                  KAN-173

배포 파이프라인 (GitHub Actions, OIDC, 환경별 역할 modules/deploy)
  이미지 deploy.yml                                                KAN-128, KAN-165
    Dev 병합     → 이미지 빌드 → ECR push(SHA) → SSM IMAGE_TAG 갱신
                   → ai 호스트 Run Command reload → healthy
                   → backend 태스크 정의 리비전 등록 → ecs update-service → rolloutState COMPLETED
                   → 실패 시 역순 롤백(backend 직전 태스크 정의, ai 직전 SHA) → E2E 스모크 KAN-138 → verified 태그
    Release 병합 → 재빌드 없이 Dev 이력의 최신 빌드 SHA(verified 필수) → (environment 승인) → prod 반영 → E2E 스모크 (KAN-217)
    롤백         → 수동 실행에 이전 SHA 입력, 같은 절차
  웹 번들은 Accentury_App의 web-deploy.yml이 S3 업로드 + CloudFront 무효화를 한다    KAN-127

운영
  ├─ CloudWatch 경보 16개 → SNS 이메일 (일부는 슬랙)                 KAN-134, KAN-36, KAN-38
  ├─ ai 호스트 스케일링 경보 2개 (ASG 정책용)                        KAN-201
  ├─ 슬랙 Chatbot: 인프라 알림 채널, 스케일링 알림(EventBridge → Lambda scale_notify),
  │    음성 수집 리포트(Scheduler → Lambda voice_report, prod만)     KAN-273
  ├─ 감사: CloudTrail(관리 이벤트), GuardDuty(S3 보호, RDS 로그인)  KAN-245
  └─ Terraform: bootstrap + envs/{staging,prod}                       KAN-140
     같은 모듈 x 2환경, 차이는 tfvars뿐. staging은 쓰지 않을 때 수동으로 끈다(destroy 아님, docs/ops/teardown.md)
```

## 환경별 값 차이 (KAN-129)

Terraform 입력의 차이는 `diff -r infra/envs/staging infra/envs/prod`가 전부다
(`terraform.tfvars`와 `backend.tf`의 state key). 그 차이가 어디로 흘러가는지의 표다. 여기 없는 설정은 두 환경이 같다.

| 항목 | staging | prod | 흘러가는 곳 |
| --- | --- | --- | --- |
| 도메인 | `<랜덤>.accentury.app` (KAN-206, 이름은 tfvars에만) | `accentury.app` | CloudFront 대체 도메인, Route 53, `ACCENTURY_RESULT_WEBTESTURL`, `ACCENTURY_RESULT_ASSETBASEURL` |
| VPC CIDR | `10.1.0.0/16` | `10.0.0.0/16` | 서브넷 4개, `ACCENTURY_TRUSTEDPROXIES` |
| RDS 엔드포인트 | `accentury-staging.<id>.ap-northeast-2.rds.amazonaws.com` | `accentury-prod.<id>...` | `SPRING_DATASOURCE_URL` (apply 후 output `rds_endpoint`) |
| RDS 마스터 시크릿 | `rds!db-<staging uuid>` | `rds!db-<prod uuid>` | `SPRING_DATASOURCE_URL`의 `secretsManagerSecretId`, backend 태스크 역할 정책 |
| SSM 경로 | `/accentury/staging/*` | `/accentury/prod/*` | backend 실행 역할 정책과 태스크 정의 secrets, ai 호스트 역할 정책과 기동 스크립트, backend 태스크 정의의 `IMAGE_TAG` 조회 |
| backend secrets 개수 | 28개 | 29개 | prod만 `ACCENTURY_TRANSLATION_RECORDBUCKET`이 더 있다 (`modules/config/outputs.tf`의 `parameter_names`) |
| RDS 삭제 보호, 최종 스냅샷 | 없음, 생략 | 켬, 남김 | `db_deletion_protection`, `db_skip_final_snapshot` |
| 배포 역할 ECR push | 허용 | 불가 | `ci_image_push` - `modules/deploy` image-deploy 정책 (KAN-128 승격 모델) |
| 번역 기록 S3 (KAN-266) | 끔 | 켬 | `translation_records_enabled` - 버킷 `accentury-translator-prompt-<계정>`(prevent_destroy), 태스크 역할 PutObject, SSM `ACCENTURY_TRANSLATION_RECORDBUCKET` |
| 음성 수집 리포트 (KAN-273) | 끔 | 켬 | `voice_report_enabled` - slack-alerts 모듈의 리포트 Lambda와 Scheduler |
| 슬랙 채널 설정 소유 (KAN-273) | 소유 안 함 | 소유, staging 경보도 구독 | `slack_channel_configs_enabled`, `slack_infra_alerts_peer_envs = ["staging"]`. 채널당 Chatbot 설정이 1개라 prod가 갖는다 |
| AI AMI 파이프라인 (KAN-285) | 끔 | 켬(수동 실행, 쓰지 않음) | `ai_ami_pipeline_enabled` - `modules/ai-ami` |

두 환경이 같은 값이라 표에서 뺀 것: backend 태스크 0.5 vCPU / 2 GB와 오토스케일링 1~3(`modules/fargate` 기본값), AI 호스트
c7i.xlarge와 루트 40GiB(`ai_instance_type`, `ai_root_volume_size`), AI ASG max 3(`ai_max_size`), 음성 저장(`training_bucket_enabled`,
접두사는 env 이름), 관리자 토큰과 내부 호출 토큰(환경별 난수, write-only).

staging 설정으로 prod에 닿을 수 없는 이유: VPC가 분리돼 있고(피어링 없음), backend
실행 역할과 태스크 역할, ai 호스트 역할이 자기 환경의 SSM 경로와 시크릿 ARN만 허용하며,
시크릿 ARN 자체가 환경별로 다르다. 프라이빗 영역 `accentury.internal`은 이름이 같지만 각 환경 VPC에만 연결돼
있어 staging backend가 prod ai를 풀 수 없다. ai 호스트 역할은 인라인 정책으로는 ai 경로만 읽지만,
같은 역할에 붙은 `AmazonSSMManagedInstanceCore`가 `ssm:GetParameter(s)`를 `*`로 연다 (자세한 것은
[`docs/ops/secrets-ssm.md`](../docs/ops/secrets-ssm.md)).

## 옛 절 이름 색인

KAN-297 전에는 이 파일 한 곳에 모든 절이 있었다. 코드 주석이나 옛 PR, Jira가 가리키는 절 이름을 아래에서 찾는다.

| 옛 절 이름 | 지금 위치 |
| --- | --- |
| 사전 요건, 실행 절차, teardown 절차 | [`docs/ops/teardown.md`](../docs/ops/teardown.md) |
| GitHub 설정 (KAN-127) | [`docs/ops/github.md`](../docs/ops/github.md) |
| App Link 검증 파일 (KAN-32), 등급 공유 이미지 (KAN-132), 이미지 배포 파이프라인과 롤백 (KAN-128), 실모델 베이스 이미지 push (KAN-173) | [`docs/ops/deploy.md`](../docs/ops/deploy.md) |
| backend Fargate 서비스 (KAN-165), backend 오토스케일링 (KAN-168) | [`docs/ops/backend.md`](../docs/ops/backend.md) |
| 종료 내결함성 실증 (KAN-169) | [`docs/wiki/infra-history.md`](../docs/wiki/infra-history.md) |
| ai 호스트 컨테이너 기동 (KAN-124, KAN-36, KAN-201) | [`docs/ops/ai-host.md`](../docs/ops/ai-host.md), SSM과 시크릿 부분은 [`docs/ops/secrets-ssm.md`](../docs/ops/secrets-ssm.md) |
| WAF 웹 ACL (KAN-149), 전송 구간과 엣지 보안 (KAN-245) | [`docs/ops/waf-edge.md`](../docs/ops/waf-edge.md) |
| 경보와 알림 (KAN-134) | [`docs/ops/alarms.md`](../docs/ops/alarms.md) |
| 관측성 지표와 대시보드 (KAN-38) | [`docs/ops/observability.md`](../docs/ops/observability.md) |
| RDS 운영자 접속과 Flyway 재베이스라인 (KAN-220) | [`docs/ops/rds-flyway.md`](../docs/ops/rds-flyway.md) |
| 환경별 값 차이 (KAN-129), 구조, 구성도 | 이 파일 |
| 설계 결정 기록 | [`docs/wiki/infra-decisions.md`](../docs/wiki/infra-decisions.md) |
