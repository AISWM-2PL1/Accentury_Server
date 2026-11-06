# 인프라 실증과 측정 기록

KAN-297(2026-10-29)에서 README 본문에 섞여 있던 실증 결과, 측정치, "언제 무엇을 했다" 기록을 모은 파일이다.
날짜순이고 각 항목 제목에 티켓 번호가 있다. 당시 값 그대로라 지금 값과 다를 수 있다. 지금 절차와 값은
[`docs/ops/`](../ops)를 본다. "(날짜 추정)"은 원문에 날짜가 없어 맥락과 git log로 추정한 항목이다.

### 2026-08-24 KAN-140 Terraform 버전 확인

S3 네이티브 잠금 `use_lockfile`(1.10부터)과 write-only 인자 `value_wo`(1.11부터)를 쓰려고 Terraform >= 1.11을 요건으로 잡았다. 2026-08-24
기준 로컬 설치 1.15.8에서 확인했고, DynamoDB 잠금 테이블은 필요 없다.

### 2026-08-26 KAN-129 /accentury/* 파라미터 없음 확인

2026-08-26 기준 `/accentury/*` 아래에는 파라미터가 없다 (CLI 확인). 그래서 "이미 있는 SSM 파라미터" 절의 import 절차는 손으로 만든 값이 있거나 destroy 없이 이 코드를 처음 적용하는 스택에만 해당했다. 당시 import 대상은 같은 이름 9개(`spring_profiles_active`, `ai_base_url`, `datasource_url`, `trusted_proxies`, `web_test_url`, `asset_base_url`, `admin_token`, `ai_token_backend`, `ai_token_ai`)였고 `admin_token`과 토큰 2개는 `random_password`의 새 값으로 덮인다고 적혀 있었다. 이 설계 이전 이름(`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 등)은 EC2 시절 기동 스크립트가 backend.env에 그대로 실었으므로 지우라고 했다. backend.env와 그 기동 스크립트는 KAN-165(Fargate 전환)로 없어졌고, 토큰은 KAN-242부터 ephemeral + `value_wo`다.

### 2026-08-27 KAN-140 철거 뒤 웹 배포 실패와 DEPLOY_PAUSED (날짜는 원문)

환경을 destroy한 상태에서 `web/**` 변경이 병합되자 Web Deploy가 역할 부재로 `AssumeRoleWithWebIdentity` 거부라는 헷갈리는 메시지로
실패했다 (2026-08-27 실제 발생). 그 뒤 GitHub environment 변수 `DEPLOY_PAUSED`를 두어, 변수가 있으면 워크플로가 빌드만 하고 배포를
건너뛴 채 요약에 "환경 철거 중"을 남기게 했다. 당시는 모노레포라 Web Deploy가 같은 레포에 있었고, 레포 분리(KAN-221) 뒤에는
Accentury_App의 Web Deploy와 이 레포의 Image Deploy(`deploy.yml`)가 각자 이 변수를 본다.

### 2026-08-28 KAN-149 WAF 비용 산정

처음에는 규칙 3개라 환경당 월 약 8달러(웹 ACL 5 + 규칙 3개 x 1) + 요청 100만 건당 0.60달러였다(KAN-149 코멘트, 2026-08-28. 8달러는 원문의 "KAN-244에서 규칙 3개 추가"에서 거꾸로 계산한 값). KAN-244에서 규칙 3개가 더해져 환경당 월 약 11달러, 두 환경 합산 월 22달러 안팎이 됐다. 로그는 매치된 요청만 7일 보존이라 1달러 미만이다.

### 2026-08-28 KAN-134 경보 실증 (EC2 대상)

2026-08-28 staging 실측값이다 (EC2 대상, KAN-134 당시).

| 구간 | 걸린 시간 |
| --- | --- |
| 컨테이너 정지부터 `unhealthy-hosts` ALARM까지 | 5분 47초 |
| 트래픽 시작부터 `alb-5xx` ALARM까지 | 3분 38초 |

같은 날 `alb-5xx`가 ALARM인 상태에서 8분간 요청이 0인 동안 ALARM이 유지되고, 정상 요청이 들어온 직후 OK가 됐다. 20건을 한 번에 몰아치면 "2회 연속"이 성립하지 않는다는 점은 Codex 지적(2026-08-28)으로 실증 절차에 반영했다.

EC2 시절 `unhealthy-hosts`가 더 걸린 이유는 앞에 ALB 헬스체크 판정이 한 겹 더 있었기 때문이다
(컨테이너가 멈춰도 대상이 unhealthy로 바뀌는 데 30초 x 5회). Fargate에서는 등록 해제가 즉시라
`no-healthy-target`이 그보다 빠르다. 티켓 AC의 "수 분 내"는 이 값 기준이다. 복구 뒤에는 같은
간격으로 OK 메일이 온다.

`ec2-cpu-surplus` 시절 `CPUCreditBalance` 하한으로 경보를 걸면 새로 뜬 인스턴스가 몇 시간 동안 무조건 울었다(2026-08-28 staging 실측).

### 2026-08-30 KAN-22 전달본 score() 첫 실측
전달본의 `Track1Scorer.score()`는 동기이고 1건 14~30초였다 (08-30 실측). 이 값이 한동안 ai/README의 "지연 예산 재확정 (P95)" 남은 일과 계약 스위트 상한 설명의 근거였다. 지연 예산은 KAN-57의 c7i.xlarge 실측(bf16 + MFA align_one, 1건 P95 11.1초)과 KAN-172 재조정으로 75초에 확정됐다 (`ai/app/config.py` 주석).

### 2026-08-31 KAN-173 실모델 베이스 이미지 push 경로 결정 (날짜 추정)

모델 담당 IAM 사용자 `jaeyoung`(AdministratorAccess)이 ECR `accentury/ai-model`에 직접 push하는 경로를 정했다. 리포지토리 한정 정책은 만들지 않기로 했다(2026-08-31 결정). 당시 문서는 베이스 `accentury-track1`(MFA + Whisper large-v3 + Praat)을 약 5GB, 이 베이스 위의 `accentury/ai` 이미지를 약 8.5GB(베이스 약 5GB + 파이썬 의존성)로 적었다. 이 값은 추정이었고 2026-09-10 실측으로 대체됐다(아래).

### 2026-09-01 KAN-36 AI 경보 2종 실증

AI 경보 2종의 2026-09-01 staging 실측값이다 (KAN-36). ai 컨테이너를 내린 뒤 스모크 업로드
3건이 202를 받고 그 재전송 실패로 회로가 열려 4건째부터 503이었다. 당시 AI 호스트는 내부 ALB 이전이라 A 레코드 갱신 방식이었고, `ai-unhealthy`는 Minimum 통계였다.

| 구간 | 걸린 시간 |
| --- | --- |
| ai 정지부터 회로 열림(업로드 503)까지 | 약 20초 |
| ai 정지부터 `ai-circuit-open` ALARM까지 | 2분 34초 |
| ai 정지부터 `ai-unhealthy` ALARM까지 | 3분 57초 |
| `systemctl reload accentury`부터 스모크 통과(회로 닫힘)까지 | 45초 |
| reload부터 `ai-unhealthy` OK까지 | 1분 54초 |
| reload부터 `ai-circuit-open` OK까지 | 2분 34초 |
| ai 인스턴스 강제 종료부터 새 인스턴스 InService까지 | 1분 34초 |
| 강제 종료부터 A 레코드 갱신(새 사설 IP)까지 | 2분 20초 |
| 강제 종료부터 backend(당시 EC2 호스트)에서 health 200까지 | 2분 39초 |

교체 중 업로드가 없으면 회로는 닫힌 채고, 결측 2분은 `ai-unhealthy`의 3회 조건에 못 미쳐 경보가 서지 않았다.

### 2026-09-02 KAN-165 backend Fargate 첫 태스크 기동 실측

staging 첫 apply의 첫 태스크, 0.5 vCPU / 2 GB, 이미지 약 180MB, Flyway 마이그레이션 포함. 값은 `describe-tasks`의 시각, 서비스 이벤트, 컨테이너 로그에서 읽었다.

| 구간 | 걸린 시간 |
| --- | --- |
| 태스크 생성(PROVISIONING)부터 RUNNING까지 (ENI 5초, 이미지 pull 6초 포함) | 45초 |
| 컨테이너 시작부터 Spring `Started BackendApplication`까지 (JVM 기동) | 71초 (t3.small 시절 로컬 실측 2.8초의 25배 - 0.5 vCPU는 버스트가 없다) |
| RUNNING부터 ALB 대상 healthy(배포 completed)까지 | 1분 16초 (기동 71초 + 헬스체크 10초 x 3회) |
| 태스크 생성부터 서비스 steady state까지 | 2분 1초 |
| `terraform apply`의 서비스 생성(`wait_for_steady_state`) | 2분 41초 |

`health_check_grace_period_seconds` 150초와 컨테이너 healthCheck `startPeriod` 120초는 이 실측에 여유를 더해 정했다.

### 2026-09-02 KAN-165 이미지 배포 파이프라인 staging 실측

`deploy.yml`의 apply.sh를 로컬 CLI로 그대로 실행했다.

| 시나리오 | 결과 | 걸린 시간 |
| --- | --- | --- |
| 정방향 배포 (ai reload + backend 리비전 2 등록 + update-service) | 성공, 무중단 (옛 태스크가 새 태스크 healthy 뒤 등록 해제) | 전체 4분 16초. ai reload 25초, backend 롤링 배포 3분 43초 |
| 같은 태그 재실행 | 리비전을 쌓지 않고 `--force-new-deployment` | (KAN-165 실증 코멘트) |
| 배포 실패 1: pull 불가 이미지 (arm64 `f5c2123`, `CannotPullContainerError`) | 태스크 배치 실패 4회 뒤 회로 차단기 FAILED, ECS가 리비전 2 배포로 롤백. 파이프라인은 PRIMARY 교체를 감지해 실패 반환, 역순 롤백이 리비전 3 deregister | FAILED까지 15분 17초, 역순 롤백 41초, 그 뒤 plan No changes |
| 배포 실패 2: 헬스체크만 실패하는 리비전 (`healthCheck` = `exit 1`) | 태스크 3개가 각각 startPeriod 120초 + retries 60초 뒤 `Task failed container health checks`로 교체, 3회째에 FAILED, 롤백. 그동안 옛 태스크가 계속 서비스 (min healthy 100%) | FAILED까지 15분 33초, 역순 롤백 29초, plan No changes |

당시 ai는 단일 EC2였고 ai reload 25초는 그 시절 값이다(KAN-201 이후 ASG 순차 reload로 바뀌었다).

### 2026-09-02 KAN-168 backend 오토스케일링 staging 실측

9차 구축, 85개 리소스. 부하 = CloudFront 경유 `GET /v0/tests/gn-2026.08.1`(당시 활성 버전, KAN-220 뒤에는 gn-2026.09.4만 남았다) 초당 25건(태스크당 분당 1500건, 목표값의 1.5배). 시각은 KST, 출처는 경보 이력, 스케일링 활동, `describe-tasks`, 서비스 이벤트, `HealthyHostCount` 분 단위 지표, 컨테이너 로그.

| 구간 | 시각 | 걸린 시간 |
| --- | --- | --- |
| 부하 시작 | 23:35:25 | |
| AlarmHigh OK -> ALARM (분당 1500건 데이터포인트 3개: 23:37, 23:38, 23:39) | 23:41:05 | 부하 시작 + 5분 40초 |
| 스케일링 활동 "desired 2" | 23:41:05 | 경보와 같은 초 |
| 태스크 생성 / RUNNING(started) / 대상 그룹 등록 | 23:41:12 / 23:41:47 / 23:41:40 | |
| `HealthyHostCount` 2 (서비스 steady state 23:43:06) | 23:43 | **ALARM + 2분 1초**, 부하 시작 + 7분 41초 |
| AlarmLow OK -> ALARM (15개 데이터포인트 23:42~23:56 전부 900 미만) | 23:57:11 | |
| 스케일링 활동 "desired 1" (첫 태스크가 종료 대상) | 23:57:11 | 경보와 같은 초 |
| 대상 등록 해제 / SIGTERM(backend 로그 "종료 1/4") / STOPPED | 23:58:17 / 23:59:03 / 23:59:41 | KAN-166 4단계 종료가 1초 안에 끝남 (진행 중 분석 0건) |
| 종료 구간의 요청 | 부하 계속 중 | 5xx 0건, 200이 14999건 (드레인 30초 + graceful shutdown) |
| 남은 태스크 | desired 1 / running 1, healthy | min 1 복귀 |

같은 날 확인한 것 둘. `desired_count`를 콘솔 경로(`update-service --desired-count 2`)로 바꾼 뒤 `terraform plan`이 No changes다(`ignore_changes` 동작). 그리고 **스케일인은 알람 창 전체를 본다**. 부하가 중간에 14분 끊겼다가(로컬 부하 생성기가 돌던 Mac의 절전) 태스크당 750건으로 다시 들어오는 중이었는데도 AlarmLow가 서자 바로 1로 줄였다. 15개 데이터포인트 중 13개가 0이라 창 평균이 목표에 한참 못 미쳤기 때문으로 보인다. 줄인 뒤 태스크 1개가 분당 1500건을 받으면 3분 뒤 다시 늘어난다. 부하가 짧게 끊겼다 돌아오는 패턴에서는 이 왕복이 한 번 생길 수 있고, 그 사이 요청은 드레인과 graceful shutdown이 받아 5xx는 없었다.

### 2026-09-02 KAN-165 경보 재실증 (Fargate 대상)

2026-09-02 staging 실측값이다 (Fargate 대상, KAN-165 재실증. 값은 KAN-165 실증 코멘트).

| 구간 | 걸린 시간 |
| --- | --- |
| desired 0부터 첫 503까지 (등록 해제 지연 30초 뒤 옛 태스크 종료) | 22초 |
| 트래픽 시작부터 `alb-5xx` ALARM까지 | 3분 52초 |
| desired 0부터 `no-healthy-target` ALARM까지 | 9분 9초 |
| desired 1 복구부터 `no-healthy-target` OK까지 | 5분 7초 (태스크 healthy 약 2분 + 1분 x 2회 + 평가 지연) |
| 트래픽 종료부터 `alb-5xx` OK까지 | 8분 22초 (결측이 notBreaching이라 트래픽 없이도 내려왔다 - 08-28 실측과 다른 점) |

`no-healthy-target`이 9분 걸린 이유는 대상이 0개일 때 `HealthyHostCount`가 0으로 나오지 않고 결측이
되고, CloudWatch가 결측을 breaching으로 세기 전에 지표가 끊겼는지 판단하는 유예를 두기 때문이다
(08-28의 EC2 대상은 unhealthy 값 1이 매 분 나와 5분 47초였다). 태스크가 죽어 ECS가 2분에서 3분 안에
교체하는 정상 경로는 이 경보에 안 잡히는 것이 맞고, 잡아야 하는 것은 "몇 분째 healthy 대상이 없다"라
9분도 티켓 AC "수 분 내" 안이다. 더 빠르게 하려면 결측 없이 계속 나오는 지표(Container Insights의
`RunningTaskCount`, 이름당 요금)가 필요해 KAN-38에서 판단한다.

### 2026-09-03 KAN-132 등급 이미지 기준 URL 파라미터 apply 선행

필수 SSM 파라미터 `ACCENTURY_RESULT_ASSETBASEURL`을 더하면서 staging은 2026-09-03에 apply를 먼저 하고 그 코드를 실은 이미지를 배포했다. KAN-36의 내부 호출 토큰도 같은 순서였다. prod는 최초 apply(KAN-171)에 이 파라미터가 들어 있었다.

### 2026-09-03 KAN-169 종료 내결함성 실증

KAN-165(Fargate), KAN-166(graceful shutdown), KAN-167(다중 인스턴스 상태), KAN-168(오토스케일링)이 함께 동작하는지를 staging 10차 구축(85개, 2026-09-03 13:27부터 18분)에서 한 번에 봤다. 부하는 `scripts/load_resilience.py`로 걸었다. CloudFront 경유 정의 조회 GET을 초당 N건 보내면서 응시자 스레드가 세션 생성, 음성 5문항 업로드, 폴링, 실패 문항 재업로드(재녹음), 어휘 제출, 완료 확정을 반복한다. 세션은 합성 트래픽 표시(KAN-138)를 붙인다. 시각은 KST, 출처는 ECS 서비스 이벤트, `describe-tasks`, 스케일링 활동, 경보 이력, 컨테이너 로그, 부하 생성기의 이벤트 파일이다.

| 시나리오 | 실측 | 판정 |
| --- | --- | --- |
| 롤링 배포 (부하 중, Image Deploy 워크플로) | 3회(6c1244a, kan36-09011729, 6c1244a 복귀) 모두 반영 시작부터 배포 완료까지 4분 18초 안팎. 새 태스크 등록 후 옛 태스크 등록 해제, 드레인 30초, SIGTERM, 4단계 종료 1초 안. 클라이언트 5xx와 연결 실패 0건 | 무중단 |
| 자동 롤백 (pull 불가 이미지 `kan169-broken`, 부하 중) | update-service 14:06:20, CannotPullContainerError 3회(14:08:23, 14:10:36, 14:12:52), 4번째 시도 뒤 회로 차단기 "deployment failed" 14:18:21, 롤백 배포 완료 14:19:37. 파이프라인이 회로 차단기 롤백을 감지해 실패 리비전 deregister와 ai 되돌림까지 마치고 실패 종료(dispatch부터 15분 1초). 옛 태스크가 내내 서비스해 5xx 0건 | 자동 롤백 1회 확인 |
| 스케일아웃 (GET 초당 25건 + 응시자 4 = 태스크당 분당 약 1650건) | 부하 시작 14:27:38, AlarmHigh ALARM 14:33:03(+5분 25초), 같은 초에 desired 2, 태스크 시작 14:33:06, 대상 등록 14:33:34, healthy 2 도달 14:35:00(**ALARM + 1분 57초**). 60초 쿨다운 뒤 14:35:03 desired 3(경보 창에 늘리기 전 데이터포인트가 남아 한 단계 더) | KAN-168 실측(2분 1초)과 같은 수준 |
| 강제 종료 (`stop-task`, 2회) | 1회차 14:35:42 호출, 등록 해제 14:35:50, SIGTERM 14:36:34, STOPPED 14:37:14. 2회차(등록 해제 지연을 5초로 낮춘 상태) 14:39:01 호출, 등록 해제 14:39:23, SIGTERM 14:39:49, STOPPED 14:40:30. 두 번 다 "종료 1/4 진행 중 0건"으로 4단계가 1초 안에 끝났고 클라이언트 5xx 0건, 세션 완주 실패 0건 | 5xx 0건 |
| 스케일인 (경부하 GET 초당 2건 + 응시자 2로 전환) | 전환 14:43:05, AlarmLow ALARM 14:55:52(+12분 47초), desired 2 14:59:52, 300초 쿨다운 뒤 desired 1 15:05:52. 줄어드는 태스크마다 등록 해제, 드레인, SIGTERM, 4단계 종료 로그. 경부하 요청 5xx 0건, min 1 복귀 | min 1 유지 |
| WAF Block 전환 | `waf_enforce=true` apply 15:09:59(변경 1개, in-place). 정상 흐름 스모크(`e2e_smoke.py`) 통과. 같은 IP에서 `POST /v0/sessions` 700건을 92초에 보내자 backend 429(retryAfterMs 46741)를 거쳐 WAF 429(`Retry-After: 300`, retryAfterMs 300000, correlationId 없는 봉투)로 바뀌었다. WAF 로그에 `action=BLOCK`, `terminatingRuleId=rate-limit-costly-posts` 246건(15:12:12부터 15:13:07, 버스트 시작 61초 뒤부터)이 남았고 15:18:31에 201로 풀렸다. Count 모드로 돌던 13:45부터 15:09까지 정상 부하(46811건)에서 세 규칙의 COUNT 로그는 0건 | 오탐 0건, prod 값 true |

서버 쪽 대조: ALB `RequestCount` 46811, `HTTPCode_ELB_5XX_Count` 0, `HTTPCode_Target_5XX_Count` 0, CloudFront 5xxErrorRate 0(13:45부터 15:15). 클라이언트 쪽은 세 phase 합계 GET 37389, 세션 439, 업로드 2190, 폴링 2950, 완료 429에 5xx 0, 연결 실패 0이다.

진행 중 분석과 SIGTERM: stop-task든 스케일인이든 롤링 배포든 ECS는 먼저 ALB 대상을 등록 해제하고 드레인(기본 30초)을 끝낸 뒤에야 SIGTERM을 보낸다. 실측으로 호출부터 SIGTERM까지 52초와 48초였고, 등록 해제 지연을 5초로 낮춰도 26초였다. 그 사이 새 요청은 오지 않고 이미 받은 분석은 `ai-timeout`(실측 당시 10초, 실모델은 85초 - KAN-172. 정상 추론 P95 11초) 안에 끝나므로, 4단계 종료가 시작될 때 실행 중이거나 대기 중인 작업은 언제나 0건이다. 즉 KAN-166의 워커 배수(예산 90초)는 AI 응답이 ECS 드레인 창보다 길어질 때만 쓰이는 두 번째 방어선이고, 티켓이 요구한 "종료 시점에 PROCESSING인 AnalysisJob"은 이 구조에서 만들 수 없다. 대신 실패 격리와 재녹음은 롤링 배포의 ai 컨테이너 reload 창(약 25초)에서 확인됐다. 그 창에 접수된 문항 11건이 `RETRYABLE_FAILED(ANALYSIS_UNAVAILABLE)`로 격리됐고 재업로드 11건이 전부 COMPLETED로 끝났다.

목표값 1000 확정: 태스크당 분당 약 1650건(목표의 1.65배)에서 태스크 1개의 GET p95는 181ms, 업로드 p95는 281ms로 포화 징후가 없었고, 늘어나는 7분 23초(경보 5분 25초 + 기동 1분 57초) 동안 오류가 없었다. 1000은 "늘어나는 동안 태스크 1개가 버티는 값"으로 충분하고 낮추면 폴링 스파이크에 불필요하게 늘어나므로 그대로 둔다. 관찰 하나. 스케일아웃 쿨다운 60초는 경보 창(3분)에 늘리기 전의 데이터포인트가 남아 있는 동안 한 단계를 더 늘린다(2 -> 3). 큰 스파이크에는 의도한 동작이고 15분 뒤 스케일인이 되돌리므로 값은 유지한다.

실증 뒤 남긴 것: `scripts/load_resilience.py`(재사용), tfvars `waf_enforce = true`(두 환경, 2026-10-29에도 그대로다). 실증 중 임시로 만들었다 지운 것: SSM `/accentury/staging/ai/ACCENTURY_AI_STUB_DELAY_MS`(스텁 지연 6초, 종료 시점에 진행 중 분석을 만들려던 시도), 대상 그룹 등록 해제 지연 5초(2회차 강제 종료 뒤 30초 복원), ECR 태그 `kan169-broken`(backend는 arm64 757c580, ai는 6c1244a를 가리키게 해 pull 실패를 유도. 두 리포지토리에 같은 태그가 있어야 파이프라인이 받아들인다). Flyway로 옛 이미지를 죽이려던 첫 시도(`kan36-09011729`, V4 없음)는 Flyway가 미래 버전 이력을 기본으로 무시해 정상 기동했다. 옛 이미지 롤백은 스키마가 앞서 있어도 막히지 않는다는 뜻이라 이것도 기록한다.

### 2026-09-03 KAN-169 staging WAF Block 전환

2026-09-03 KAN-169에서 staging을 Block으로 전환했다 (`waf_enforce = true`, 두 환경 tfvars).
Count 관찰은 2026-08-28부터의 staging 사이클과 당일 부하 시험(정상 형태 요청 46811건)에서 세
규칙 매치 0건이었고, Block 전환 뒤 `e2e_smoke.py` 정상 흐름이 통과했으며, 같은 IP의
`POST /v0/sessions` 700건(92초)이 backend 429를 거쳐 WAF 429(`Retry-After: 300`)로 바뀌고 5분 뒤
풀리는 것을 확인했다. prod는 처음부터 이 값으로 짓는다 (KAN-171). 당시 규칙은 세 개(10, 20, 30)였다.

### 2026-09-04 KAN-171 prod 최초 구축의 IMAGE_TAG 선행 (날짜 추정)

staging은 파이프라인이 남긴 `/accentury/staging/IMAGE_TAG`가 destroy 뒤에도 남아 있어 재구축 plan이 그대로 섰다. prod 최초 구축(KAN-171)은
그 파라미터가 없어서 staging에서 verified된 SHA를 먼저 `put-parameter`로 넣고 apply했다.

### 2026-09-05 KAN-171 관리자 토큰을 GitHub 저장소 시크릿에서 뺌 (KAN-22 스모크 정리 포함)

예전에는 `e2e-smoke.yml`이 저장소 시크릿 `ACCENTURY_ADMIN_TOKEN`을 읽었다 (KAN-171 리뷰 P3로 변경). 이 저장소는 2026-09-05 확인 시점에 이 시크릿이 없었으므로 지울 것이 없었다 (KAN-171 Codex 리뷰 P1이 "읽지 않게 된 것과 남아 있는 것은 다르다"를 지적). 같은 날 억양 점수를 예측하던 스모크 옵션 `expect-stub-scores`는 스텁과 함께 없앴다 (KAN-22).

### 2026-09-05 KAN-38 부팅 자산을 S3로 옮김 (날짜 추정)

파일을 user_data에 `base64gzip`으로 박으면 16KB 상한의 75%를 먹었고 KAN-38이 health 스크립트를 늘리며 상한을 넘겼다. 옮긴 뒤 user_data는 약 5.4KB(상한의 약 33%)였다. 2026-10-29 기준 템플릿 원문만 5,890바이트(약 36%)다. 파일을 박아 넣던 시절에는 내용이 곧 user_data라 "파일이 바뀌면 인스턴스가 교체된다"가 공짜로 성립했고, 옮기면서 해시로 명시적으로 되살렸다.

### 2026-09-05 KAN-38 관측성 층 최초 구성과 비용 추정

KAN-38이 대시보드 `accentury-{env}-ops` 1개, 경보 3종(`ai-temp-residue`, `analysis-backlog-high`, `analysis-timeouts-high`), ai 호스트
지표 경로를 더했다 (커밋 540c2cb). 당시 README의 비용 추정: 커스텀 지표 29개(이름 x 차원 조합)로 환경당 월 약 8.7달러, 경보 3종에
0.40달러(`analysis-timeouts-high`는 지표 2개를 세는 math 경보라 0.20달러), 대시보드는 계정당 3개까지 무료, 두 환경 합산 월 18달러
안팎 증가. 뒤에 KAN-272가 지표 3개(`accentury.analysis.judged`의 사유 셋)와 경보 1종(`analysis-unscorable-high`)을 더해 환경당 월
약 1달러가 늘었다. 이 추정에는 그 뒤의 `accentury.result.wait`(KAN-284), 번역 지표(KAN-266), `translation-quota-exhausted`와
`result-wait-high` 경보가 들어 있지 않았다 (2026-10-29 대조에서 docs/ops/observability.md "비용"을 코드 기준으로 다시 셈).

당시 `analysis-backlog-high` 임계는 "1분 최대 >= 60이 5회 연속"이었다.

### 2026-09-05 KAN-22 스텁 엔진 제거와 fake 엔진 도입
스텁 엔진은 2026-09-05에 제거했다 (KAN-22). 실모델은 개발 기계(arm64)와 CI 러너에서 돌릴 수 없어 로컬 풀스택과 E2E가 함께 사라졌는데, 그러자 앱과 웹의 완주 확인이 돌릴 스택을 통째로 잃었다 (PR #87 리뷰). 그래서 `fake` 엔진(`ai/app/fake.py`)을 두었다 (루트 docker-compose.yml 주석은 2026-09-06 반영으로 적는다).

### 2026-09-05 KAN-22 실모델 어댑터 연결과 계약 스위트 정리
실모델 어댑터(`ai/app/track1.py`)를 붙이면서 라우트(`ai/app/analyze.py`)는 한 줄도 고치지 않았다. KAN-135가 그은 엔진 경계가 지켜졌다는 증거로 ai README에 적어 두었던 사실이다. 같은 때 스텁이 사라지면서 계약 적합성 스위트의 엔진 프로파일 표(`ENGINE_PROFILES`)와 `--contract-engine` 옵션을 접었다. 고를 엔진이 하나면 표는 목적을 잃기 때문이다 (KAN-297에서 ai README에서 옮김).

### 2026-09-05 KAN-36 옛 infra README 구성도 아래 문단 (KAN-297에서 옮김)

에픽 KAN-118(프로토타입 스텁 배포)이 정한 구조에 AI 전용 호스트 분리(KAN-36 A단계)와
backend의 Fargate 전환(KAN-165)을 더한 것이 구성도의 출발점이다.

backend는 KAN-165부터 ECS Fargate다 (온디맨드만, Spot 없음 - 2026-09-01 결정). 태스크 1개 고정은 backend의
인메모리 상태(요청 제한, 회로 차단기, 혼잡 판정, 디스패처 큐) 때문이었고 KAN-167(다중 인스턴스 상태 정리)과
KAN-168(오토스케일링)이 풀었다. AI는 KAN-36 A단계로 전용 EC2에 분리됐고, 2026-09-05에 실모델로 바뀌었다 (KAN-22 -
`ai/Dockerfile`의 FROM이 모델 전달본 `accentury/ai-model:<모델 해시>`다). 메모리 상한과 GPU 여부는 KAN-57 실측
뒤에 정한다고 적혀 있었고, 이후 c7i.xlarge 유지와 `mem_limit` 7g(KAN-36 B단계, 2026-09-10)로 정해졌다.

### 2026-09-07 KAN-176 개인정보처리방침 확정 본문
KAN-176이 `infra/privacy/privacy.html`에 14개 절의 확정 본문을 넣고 검색 노출을 막던 `<meta name="robots" content="noindex">`를 지웠다. KAN-133(2026-09-04)이 마련한 게시 경로는 그대로 두고 그 경로로 올라가는 내용만 바꿨다 (KAN-297에서 infra/privacy README에서 옮김).

### 2026-09-08 KAN-172 분석 시간 예산 확정

KAN-22가 임시로 올렸던 값(85s / 300s / 1 / 75)을 KAN-57의 c7i.xlarge 실측(bf16 + MFA `align_one`, 1건 P50 10.1초, P95 11.1초, 콜드 스타트 31초, 동시 처리 상한 1건)으로 다시 봤고 같은 값을 정식값으로 확정했다. 같은 티켓에서 backend의 읽기 타임아웃은 재전송하지 않게 바꿨다 (`ANALYSIS_TIMEOUT` 즉시 종결, 연결 실패와 5xx만 2회 재전송). 폴링 혼잡 임계치는 30에서 6으로, 디스패처 큐 용량은 200에서 30으로, AI compose `start_period`는 300초에서 90초로 내렸고 `analysis-backlog-high` 경보 임계치는 60에서 12가 됐다. 당시 `dispatch-concurrency`는 1이었다 (2026-10-06 KAN-272에서 3).

### 2026-09-08 KAN-172 analysis-backlog-high 임계 60 -> 12

실모델 기준 타임아웃과 폴링 파라미터 재조정(커밋 a71da3c)에서 `analysis_backlog_threshold` 기본값을 12로 내렸다. 근거는 폴링 혼잡
임계치(`congestion-threshold` 6)의 두 배이고 AI 1건 10초 기준 대기열 2분이다. README 관측성 표는 2026-10-29 대조 때까지 60으로 남아
있었다.

같은 티켓의 staging 실측: 문항당 처리 시간 TTS 발화 9.9~11.1초, 워밍업 뒤 첫 호출 22.9초. KAN-204가 이 시간이 어느 단계에 쓰였는지를
지표로 올리게 된 출발점이다.

### 2026-09-08 KAN-172 staging 첫 호출 콜드 실측 (날짜 추정)
가중치는 워밍업에서 이미 올라와 있어도 전달본 안쪽의 지연 초기화가 첫 채점에서 한 번 일어난다. staging 첫 호출 22.9초와 그 뒤 10초대의 차이가 그것이다 (KAN-172 실측).

### 2026-09-09 KAN-203 ai 컨테이너 로그 이중 로깅 실측

awslogs 드라이버로 바꾼 뒤에도 `docker logs`가 읽히는지 staging에서 확인했다 (docker 25.0.16). 원격 드라이버를 쓰면 엔진이 `local`
드라이버로 사본을 하나 더 남기고(`/var/lib/docker/containers/<id>/container-cached.log`) `docker logs`가 그것을 읽는다. 로깅 드라이버가
`json-file`이던 동안에는 ai 컨테이너 로그가 호스트 디스크에만 있어서, ASG가 인스턴스를 교체하면(`ai-unhealthy` 경보, 시작 템플릿 갱신에
따른 instance refresh) 추론 실패 원인이 인스턴스와 함께 사라졌다. 호스트 사본 상한 50MB는 옛 json-file 설정과 같은 값으로 맞춘 것이다.

### 2026-09-09 KAN-203 로그 전송 차단 실증 (staging)

"로그가 안 나가면 추론도 멈추는 것 아닌가"를 실측으로 닫아 둔 기록이다. 차단 방법은 docs/ops/observability.md "로그 전송 차단 재현
절차"와 같다 (`/etc/hosts` 무효화로는 dockerd가 이미 맺은 연결을 계속 써서 막히지 않았고, 호스트 `iptables OUTPUT`에서 logs 엔드포인트
IP의 443을 끊었다).

실측 결과 (인스턴스 `i-0474d5e02dfabab6a`, logs 엔드포인트 IP 6개를 끊은 상태):

| 항목 | 결과 |
| --- | --- |
| 컨테이너 재생성 | `docker compose up -d --force-recreate` 종료 코드 0. 기동이 막히지 않는다 |
| 준비 상태 | health UP까지 12초 (평시와 같다 - 가중치는 페이지 캐시에 있다) |
| 분석 요청 | 2.69초, 2.52초, 2.60초. 차단 전 2.51초, 복구 뒤 2.70초와 차이가 없다 |
| 401 경로 | 1.3~1.7ms. 로그 한 줄을 만드는 요청도 밀리지 않는다 |
| 차단 중 남긴 줄 | **버려지지 않았다.** 복구 뒤 25~75초 늦게 전부 도착했다 (드라이버가 버퍼를 들고 재시도한다) |
| 차단 중 컨테이너를 다시 만들면 | 그 컨테이너 버퍼에 남아 있던 줄은 사라진다 (직전 한 줄이 유실됐다) |
| 차단 중 `docker logs` | 그 구간의 줄은 보이지 않는다 - 이중 로깅은 원격 쓰기가 실패하면 사본도 남기지 않는다 |

읽는 법은 둘이다. **짧은 CloudWatch 장애로는 로그를 잃지 않는다** - 버퍼(4MB) 안이면 늦게라도 온다. 그리고 **로그가 못 나가는 것이
추론을 멈추지 않는다** - 이것이 `mode: non-blocking`의 목적이고, 위 숫자가 그 증거다.

덤으로 확인된 것 하나. 이 차단은 호스트 `OUTPUT` 체인에서만 걸렸다. 컨테이너 egress 가드(`DOCKER-USER`, KAN-36)는 실증 내내 그대로
있었고 로그 전송에 아무 영향이 없었다 - **로그 드라이버 트래픽이 컨테이너가 아니라 호스트 dockerd에서 나간다**는 전제가 실측으로
확인된 것이다.

### 2026-09-10 KAN-36 B단계 루트 볼륨 40GiB와 mem_limit 7g

루트 볼륨을 20에서 40GiB로(이미지 7GB x 2 공존 + pull 임시 공간), 컨테이너 상한을 `mem_limit: 7g`로 정했다. 호스트 7.6GiB에서 OS와 에이전트 몫 0.6GiB를 뺀 값이고, 1.3배 규칙(8.05GB)은 8GB 호스트에서 성립하지 않아 대신 "호스트 프로세스는 살린다"를 기준으로 잡았다. staging 실측 최대 4.55GiB (compose 주석).

### 2026-09-10 실모델 ai 이미지 크기 실측

staging에서 실모델 `accentury/ai` 이미지가 7.02GB(모델 베이스 4.12GB 위)로 측정됐다(`infra/envs/*/terraform.tfvars` 루트 볼륨 주석, KAN-36 B단계). KAN-173 시절 문서의 "베이스 약 5GB, ai 이미지 약 8.5GB" 추정을 대체한다.

### 2026-09-10 KAN-36 B단계 AI 호스트 자원 경보 실증

AI 호스트 자원 경보 2종(KAN-36 B단계)의 2026-09-10 staging 실증이다. 티켓이 허용한 두 방법 중
**임계 일시 하향**으로 했다. `put-metric-alarm`으로 임계만 낮춰(디스크 80 -> 20, 메모리 90 -> 50)
ALARM을 보고, `terraform apply`가 그 드리프트를 되돌리는 것으로 OK를 봤다. 디스크를 실제로
채우는 방법(`fallocate`)은 지표 경로가 같으므로 결과가 다르지 않다. 그 지표 경로 자체는 같은 날
instance refresh에서 확인됐다. 새 인스턴스의 pull 중 `RootDiskUsedPercent`가 23%에서 36%로 올랐다
23%로 내려왔고(이미지 7GB pull의 임시 공간), `MemoryUsedPercent`는 컨테이너 기동 전 9%에서 워밍업 뒤
56%로 올랐다. 지표는 health와 무관하게 호스트가 뜬 직후부터 나간다.

| 구간 | 걸린 시간 |
| --- | --- |
| `ai-disk-high` 임계 하향(11:03:59 UTC)부터 ALARM까지 | 1분 28초 (이미 쌓인 3개 데이터포인트를 새 임계로 재평가) |
| `ai-mem-high` 임계 하향(11:05:55)부터 ALARM까지 | 39초 |
| `terraform apply` 임계 복원(11:07:48)부터 `ai-mem-high` OK까지 | 1분 5초 |
| 임계 복원부터 `ai-disk-high` OK까지 | 1분 11초 |
| 시작 템플릿 갱신(루트 볼륨 40GiB)의 instance refresh: 새 인스턴스 시작부터 컨테이너 healthy까지 | 3분 39초 (10:49:23 -> 10:53:02, 이미지 7GB pull 포함) |

실증 뒤 `terraform plan`은 No changes이고 경보 12종 전부 OK였다(당시 경보 수). 실제 임계(80, 90)로 ALARM이 서는
데는 여기에 조건 길이(디스크 3분, 메모리 2분)가 더해진다.

### 2026-09-10 KAN-204 문항당 추론 시간 실측 (날짜 추정)
문항 하나가 10초대(TTS 발화 실측 9.9~11.1초)인데 그 시간이 어느 단계에 쓰였는지가 로그에도 지표에도 없었다. 이것이 단계별 소요 시간 수첩(`ai/app/stages.py`)을 만든 계기다.

### 2026-09-11 KAN-201 내부 ALB와 오토스케일링 staging 실증

apply 뒤 instance refresh로 뜬 새 인스턴스가 3분 20초 만에 대상 그룹 healthy가 됐고, 실발화 5문항이 ALB를 거쳐 완주했다(AI 컨테이너 로그의 출처 IP가 ALB ENI). 실발화 25건을 한꺼번에 올리자 PROCESSING이 약 2분간 6 이상이었고, 시작 2분 44초 뒤 경보 `ai-scale-out`이 ALARM으로 바뀌며 desired 1 -> 2가 됐다. desired 3 + backend 태스크 3에서 20건을 올리자 인스턴스별 7 / 7 / 6으로 갈렸고(로그 스트림 `ai/<인스턴스 ID>`), 부하를 거둔 뒤 16분에 3 -> 2, 19분에 2 -> 1로 돌아왔다. 순차 reload는 Jira KAN-201 코멘트에 있다. 당시 확대 판정은 2분 연속, 워밍업은 600초였다.

KAN-36 시절에는 `ai.accentury.internal`이 인스턴스가 부팅마다 자기 IP로 UPSERT하던 A 레코드였다 (`accentury-up.sh`의 3번 앞 단계, IMDSv2로 읽은 사설 IP). KAN-201에서 그 이름을 내부 ALB의 alias로 바꾸고 ASG `max_size`를 `ai_max_size`(두 환경 3)로 열었다. 당시 backend 쪽은 무변경이었다 (`dispatch-concurrency` 1 그대로, "동시성은 backend 태스크 수에서 온다"는 전제). 같은 KAN-201에서 음성 저장은 staging에만 환경별 버킷(`accentury-staging-training-<계정 ID>`)을 두었다.

### 2026-09-11 KAN-201 AI 새 인스턴스 기동 시간 실측

staging에서 새 AI 인스턴스가 대상 그룹 healthy가 되기까지 약 3분 20초였다 (시작 06:51:19, healthy 06:54:39. docker 설치 + 이미지 7GB
pull + 모델 적재).

### 2026-09-11 KAN-196 개인정보처리방침 prod 게시 게이트: 광고 사업자 확정
1차 배포에 맞춤형 광고를 넣기로 한 2026-09-07 팀 결정(KAN-196 앱, KAN-197 웹) 뒤 사업자가 Google LLC의 Google AdMob으로 정해져, 2항 제3자 제공 표, 4항 국외 이전, 10항 광고의 「확정 후 기재」 자리표시자를 전부 채웠다 (KAN-196 1단계). prod 게시 게이트 첫 항목이 이때 닫혔다.

### 2026-09-17 KAN-217 prod E2E 스모크 자동화 (날짜 추정)

prod 반영 직후 같은 job에서 `scripts/e2e_smoke.py`를 부르게 됐다. 그 전에는 prod 승격 뒤 사람이 `e2e-smoke.yml`을 따로 돌렸다.

### 2026-09-21~22 KAN-220 Flyway 재베이스라인 실행

옛 V1~V12를 새 `V1__baseline.sql` 하나로 합쳤다 (2026-09-21 결정, 경위는 그 파일 머리 주석과 `application.yml`의 flyway 주석). 당시
남긴 정의는 `gn-2026.09.4`였다. 두 환경 모두 2026-09-22에 전환을 마쳤고 `baseline-on-migrate`는 그 뒤 제거했다. 이력 표를 지우지 않고
이름을 바꾸는 방식은 PR #121 리뷰 P1을 반영한 것이고, 미만료 세션 0건을 기다리는 2단계는 Codex 리뷰 P2(완주 세션이 24시간 살아 있는
동안 정의를 지우면 결과 화면이 404)를 반영한 것이다. 제약과 인덱스 이름 바꾸기는 2026-09-22 로컬 리허설에서 "relation
flyway_schema_history_pk already exists"로 기동이 막히는 것을 보고 더했다.

실제로 넣은 전환 SQL:

```
begin;
do $$ begin
  if exists (select 1 from test_session where test_version <> 'gn-2026.09.4' and expires_at > now()) then
    raise exception '구 버전을 참조하는 미만료 세션이 남아 있다 - 2단계로 돌아간다';
  end if;
end $$;
select test_version from test_definition order by published_at;
select version, type, checksum from flyway_schema_history order by installed_rank;
select test_version, previous_test_version from active_test_version;   -- gn-2026.09.4 여야 한다
update active_test_version set previous_test_version = null where id = 'CURRENT';
create table test_definition_kan220_backup as
  select * from test_definition where test_version <> 'gn-2026.09.4';
delete from test_definition where test_version <> 'gn-2026.09.4';
alter table flyway_schema_history rename to flyway_schema_history_kan220_backup;
-- 표 이름을 바꿔도 제약과 인덱스 이름은 그대로 남는다. 두지 않으면 새 이미지의 Flyway가 새 표를 만들 때
-- "relation flyway_schema_history_pk already exists"로 기동이 막힌다 (2026-09-22 로컬 리허설에서 확인).
alter table flyway_schema_history_kan220_backup rename constraint flyway_schema_history_pk to flyway_schema_history_kan220_backup_pk;
alter index flyway_schema_history_s_idx rename to flyway_schema_history_kan220_backup_s_idx;
commit;
```

prod 스냅샷 명령 (당시 형태):

```
DB_ID=$(aws rds describe-db-instances --query "DBInstances[?Endpoint.Address=='$RDS_HOST'].DBInstanceIdentifier | [0]" --output text)
aws rds create-db-snapshot --db-instance-identifier "$DB_ID" --db-snapshot-identifier "kan220-before-rebaseline-$(date +%Y%m%d%H%M)"
aws rds wait db-snapshot-available --db-snapshot-identifier "kan220-before-rebaseline-<위 시각>"
```

복구 SQL과 정리 SQL은 `kan220` 접미로 docs/ops/rds-flyway.md의 일반형과 같다
(`drop table flyway_schema_history_kan220_backup, test_definition_kan220_backup;`).

리허설: 전환과 복구 두 경로 모두 2026-09-22 로컬 postgres:16에서 해 봤다. 옛 V1~V12를 적용한 DB에서 전환 SQL 뒤 새 jar가 baseline
1행으로 기동하고, 복구 SQL 뒤 옛 파일 셋으로 `flyway validate`가 12개 마이그레이션을 통과했다. 같은 날 session-manager-plugin을 sudo
없이 홈에 풀어 설치해 통로를 실제로 썼다.

### 2026-09-21 KAN-220 Flyway 재베이스라인
옛 V1~V12를 `V1__baseline.sql` 하나로 합쳤다. 정의 발행 마이그레이션 네 벌(각 640KB)이 쌓여 있었는데 쓰는 정의는 gn-2026.09.4 하나라서다. 경위와 합친 출처 표는 그 파일 머리 주석에 있고, 다음 마이그레이션은 `V2__<설명>.sql`부터 다시 번호를 매겼다. 빈 DB에서는 이력에 `1 / SQL` 한 행이 남는다.

### 2026-09-22 KAN-220 staging, prod 재베이스라인 전환 완료
운영자가 이력 표의 이름을 바꾸고 구 정의 행을 지운 뒤 배포했고, 당시 켜 둔 `baseline-on-migrate`가 V1을 실행하지 않고 baseline 행만 기록했다 (이력에 `1 / BASELINE` 한 행). 설정은 전환 뒤 제거했다. 절차와 복구 경로는 [`docs/ops/rds-flyway.md`](../ops/rds-flyway.md).

### 2026-09-22 KAN-221 모노레포 분리
모노레포 `Accentury`를 Accentury_Server와 Accentury_App 둘로 나눴다. 옛 레포는 Accentury_Prototype(읽기 전용)으로 남았다. 브랜치 모델(`Dev` 병합이 staging, `Release` 병합이 prod)은 바꾸지 않았다. 서로 읽던 파일은 복사본으로 풀었다. 분리 전에는 한 레포라 `scripts/publish-share-assets.sh`가 `assets/share`를 직접 읽었고, 분리하면서 `infra/share-assets/`에 복사본을 두었다. 매니페스트 `android:path`의 복사본 `infra/modules/edge/app-link-paths.json`도 이때 생겼다 (KAN-297에서 루트, infra/share-assets, infra/well-known README에서 옮김).

### 2026-10-01 KAN-242 write-only 시크릿 전환 staging 실측

staging 전환 뒤 state pull로 `admin_token`, `ai_token_*`, `jwt_secret`의 `value`가 `""`이고 `has_value_wo`가 `true`임을 확인했다. 같은 날 aws provider 6.61.0 바이너리에서 `aws_elasticache_replication_group`에 write-only 인자가 없음을 확인했다. state 버킷을 읽을 수 있는 주체가 전부 SSM도 직접 읽는 관리자임을 확인했다 (KAN-242 노출 범위 표). 전환 전에는 `random_password` + `value`라 값이 state(버전 관리 버킷)에 평문으로 남아, 버킷을 읽을 수 있는 주체는 누구나 임의 사용자의 Access JWT를 만들 수 있었다.

### 2026-10-03 KAN-245 bootstrap state를 원격으로 이전

bootstrap은 state 버킷을 만드는 스택이라 처음에는 로컬 state로 만들었고, 2026-10-03(KAN-245)에 그 버킷의 `bootstrap/terraform.tfstate`로
옮겼다 (`backend.tf`).

### 2026-10-04 KAN-269 음성 전용 버킷 적용 순서 (기록)

적용 순서는 bootstrap, staging, prod였다.

1. `infra/bootstrap` apply - 음성 버킷과 버킷 정책이 생기고 CloudTrail 데이터 이벤트 대상에 음성 버킷이 더해졌다. 옛 staging 버킷은 대상에 남았다.
2. `infra/envs/staging` apply - plan에 옛 버킷 리소스 6개(버킷, 퍼블릭 차단, 암호화, 버킷 정책, 읽기 역할과 그 정책)가 "will no longer be managed by Terraform, but will not be destroyed"로 나와야 했다. destroy로 나오는 것은 옛 버킷의 수명주기 규칙과 테스터 ID, 가명 키 파라미터 셋이었다. 이 apply는 2026-12-31 전에 끝나야 했다 - 옛 버킷에는 KAN-239가 건 만료 규칙(2026-12-31에 객체 삭제)이 실물로 살아 있고 버전 관리가 꺼져 있어, 그날을 넘기면 남은 음성이 복구할 수 없이 지워진다. 이 apply가 그 규칙을 없앤다. (KB 기록상 staging 실증 2026-10-05, prod apply 2026-10-07. 이 문서 작업에서 실물은 대조하지 않았다.)
3. `infra/envs/prod` apply - 이 티켓의 몫은 두 파라미터와 태스크 역할 문장, 태스크 정의 교체뿐이었다. prod에는 아직 적용하지 않은 앞선 변경(KAN-242 write-only 시크릿, KAN-244 WAF, KAN-245 엣지와 backend 아웃바운드)이 쌓여 있어 plan에 함께 나왔다 (2026-10-04 plan: 11 add, 11 change, 6 destroy). 그 변경들의 순서 제약(이미지가 먼저, backend 아웃바운드는 좁은 규칙을 `-target`으로 먼저)이 그대로 적용됐다.

같은 변경이 방침 버전을 `2026-10-04`로 올렸다. 저장 대상은 KAN-274에서 익명 세션만, KAN-276부터 계정 세션도 메타 JSON을 남기게 됐다.

### 2026-10-04 KAN-269 방침 버전 2026-10-04
KAN-269(음성 저장 선택 동의) 개정으로 방침 버전과 시행일이 `2026-10-04`가 됐다.

### 2026-10-04 KAN-269 개인정보처리방침 staging 전용 블록 비움
음성 저장이 두 환경 공통의 선택 동의가 되면서 `privacy.html`의 `<!-- staging-only:begin -->`와 `<!-- staging-only:end -->` 사이에 있던 staging 전용 학습 수집 고지(KAN-239가 넣은 것)를 지웠다. 그 뒤로 두 환경의 게시 본문은 같고, 자르는 경로는 다음 환경별 문구를 위해 남겼다. `privacy.test.mjs`가 음성 저장 선택 동의 절을 붙들기 시작한 것도 이 개정부터다 (KAN-297에서 infra/privacy README에서 옮김).

### 2026-10-05 KAN-246 AI 호스트 AMI 교체 staging 실증

`al2023-ami-2023.12.20260831.0`에서 `20260930.0`으로 바꿨다(커널 계열은 6.18로 같다). plan은 변경 2건, apply는 10초 만에 끝나고 교체는 그 뒤에 ASG가 진행했다.

| 시각 (KST) | 일 |
| --- | --- |
| 20:02:55 | instance refresh 시작, 2초 뒤 새 인스턴스 시작 (대상 그룹에 옛 1대 healthy + 새 1대 initial) |
| 20:06:34 | 새 인스턴스가 대상 그룹 healthy로 보인 첫 표본 (직전 표본 20:06:13은 아직 아님. 시작 약 3분 30초 뒤, 이미지 7GB pull과 모델 적재 포함). healthy 2대 |
| 20:07:17 | 옛 인스턴스 등록 해제 시작 (draining) |
| 20:09:30 | 옛 인스턴스 종료 완료 |
| 20:09:40 | refresh Successful - 시작부터 6분 45초 |

20초 간격으로 본 대상 그룹의 healthy 대상은 교체 내내 1대 아래로 내려가지 않았다 (1대 17회, 2대 3회). 그동안 합성 응시자 2명(`scripts/load_resilience.py --get-rps 0 --examinees 2`)이 쉬지 않고 돌았는데, 교체 구간의 분석 180건을 포함해 전체 330건이 전부 AI에 닿아 판정을 받았고(합성 사인파라 판정은 모두 `ANALYSIS_MISREAD`다) 5xx와 연결 실패, AI 미도달과 시간 초과는 0건이었다. 교체 뒤 plan은 No changes, 스모크는 통과했다.

### 2026-10-06 KAN-272 prod에서 AI 2대째가 일을 받지 못함

prod는 backend 태스크가 1개로 유지되므로(backend 오토스케일링은 요청 수 기준이라 분석이 밀려도 늘지 않는다) 동시 호출이 늘 1건이었고, AI가 2대로 늘어도 2번째는 일을 받지 못했다. 응시가 몰린 14:52부터 진행 중 분석이 19건까지 쌓이는 동안 AI의 lock 대기는 0초였고 분당 완료는 7건을 넘지 못했다. 같은 날 2번째가 이미 일하는데 워밍업 600초가 3번째를 약 7분 막아 물결 최고치 14건을 2대로 받았다 (KAN-281의 근거).

### 2026-10-06 KAN-272 판정 코드 ANALYSIS_UNSCORABLE 추가
AI의 §2.4 판정 코드 허용 목록에 `ANALYSIS_UNSCORABLE`을 더했다(채점 불가 NaN 음성). 배포가 AI 먼저라 이 코드를 모르는 옛 backend가 부르는 구간이 있어, 실모델 어댑터는 요청 meta의 `acceptsVerdicts`에 이 코드가 있을 때만 낸다. AI 요청 종료 로그에 `quality=<품질 코드>`가 찍히기 시작한 것도 이 티켓부터다. 전달본 단계 이름(`transcribe` 등)이 같은 줄에 찍히기 시작한 것은 KAN-293(2026-10-24)부터다 (KAN-297에서 ai README에서 옮김).

### 2026-10-08 KAN-266 번역 키 넣기 (날짜 추정)

환경마다 Google AI Studio 프로젝트를 따로 만들었고(team2pl1 계정, 결제 수단 연결 안 함) 각 프로젝트에서 API 키를 받아 넣었다. 당시 문서는 "AI Studio의 rate limit 화면에서 실제 무료 한도(RPM, RPD)를 확인해 KAN-266에 남긴다"였다.

### 2026-10-13 KAN-273 Chatbot 채널 설정 제약 실측

슬랙 채널 하나에는 계정당 Chatbot 채널 설정을 하나만 만들 수 있다(2026-10-13 실측, 두 번째는 `has already been configured
for AWS account`로 거부). 그래서 prod가 두 채널 설정을 모두 갖는 구조가 됐다.

### 2026-10-14 KAN-285 사전 적재 AMI 실측 (미채택)

첫 부팅 3분 22초 중 docker 설치 40초와 이미지 pull 1분 53초가 매번 같은 일이라, EC2 Image Builder로 그 둘을 미리 넣은 AMI(`modules/ai-ami`, prod 스택이 소유, 수동 실행)를 만들어 두 환경에 적용해 봤다. 결과는 더 느리고 불규칙해서 tfvars를 맨 AMI로 되돌렸다. AMI에서 만든 루트 볼륨은 블록을 처음 읽을 때 S3 스냅샷에서 가져오는데(EBS 지연 로딩), 그 대역폭이 볼륨마다 초당 약 15~50MB로 달라 모델 가중치 3~4GB를 읽는 적재 구간이 15초에서 1~4분으로 늘었다. 맨 AMI는 pull이 디스크에 새로 쓰므로 적재가 항상 15초고, 전체도 3분 20초 안팎으로 일정하다.

| 표본 | OS 부팅 완료 | 컨테이너까지 | 모델 적재 | 시작 -> ALB healthy |
| --- | --- | --- | --- | --- |
| 맨 AMI (prod 00:17) | 9초 | 2분 52초 (docker 40초 + pull 1분 53초) | 15초 | 약 3분 22초 |
| 맨 AMI, 새 user_data (staging 01:02) | - | pull 1분 52초 | - | 약 3분 20초 |
| 사전 적재 (staging 01:36, 태그 다름 - pull 2초) | 36초 | 1분 11초 | 1분 7초 | 약 2분 50초 |
| 사전 적재 + 선읽기 (staging 01:47) | 37초 | 1분 20초 | 3분 41초 (선읽기와 경쟁) | 약 5분 28초 |
| 사전 적재 (staging 01:58) | - | 1분 28초 | 3분 7초 | 약 4분 40초 |
| 사전 적재 (prod 02:00, 태그 같음 - pull 없음) | 33초 | 1분 6초 | 3분 58초 | 약 5분 34초 |

선읽기(user_data 첫머리에서 docker 레이어의 큰 파일을 백그라운드로 `cat`)는 역효과였다 - 같은 지연 로딩 파이프를 나눠 써 적재가 선읽기가 끝나는 순간까지 밀렸다.

### 2026-10-14 staging 수동 정지 운영 시작

2026-10-14부터 staging을 destroy 대신 수동 정지로 운영했다. 첫 실측: 10-14 끄기 8분 30초(RDS 정지가 대부분), 10-15 켜기 RDS 5분 20초 +
backend 정상까지 약 3분 = 약 8분. backend는 `services-stable` 뒤에도 Spring 기동으로 1분 30초쯤 unhealthy였고, no-healthy-target 경보는
healthy 뒤 3분쯤 늦게 OK로 돌아와 OK 알림 1건이 갔다. 10-15 KAN-284의 `result-wait-high`가 더해져 끄고 켜는 경보가 18개가 됐다. 그 뒤
10-20~10-28 사이 실증(KAN-288, KAN-296 등)마다 켜고 껐고, 켜기는 약 7~8분이었다.

### 2026-10-15 KAN-266 Gemini 무료 한도 확인과 계정당 하루 5건

AI Studio에서 무료 등급 한도 RPM 15, RPD 500(TPM 250K)을 확인했다 (커밋 b6ff384). RPD 500이 프로젝트 전체 몫이라 계정당 하루 5건 한도(Redis `trq:{userId}:{yyyyMMdd}`, KST 자정 초기화, 6번째부터 429 `TRANSLATION_DAILY_LIMIT_EXCEEDED`)를 더했다.

### 2026-10-20 KAN-286 방침 버전 2026-10-20 prod 게시
출석 기록 개정본(KAN-286)의 방침 버전을 처음 `2026-10-15`로 적었다가 prod 게시일에 맞춰 `2026-10-20`으로 올렸다. 이 판이 prod에 게시돼 있다 (2026-10-29 `https://accentury.app/privacy.html`의 `accentury-policy-version` 메타가 `2026-10-20`인 것을 확인). 옛 게이트의 "앱 로그인(KAN-224)을 prod에 내기 전에 계정 PII 본문이 prod에 게시돼 있어야 한다" 조건도 이로써 충족됐다.

### 2026-10-22 KAN-289 IdP 값 두 환경 반영

2026-10-22 기준 두 환경 모두 IdP String 값 넷이 다 들어가 있다 - 애플과 네이버는 KAN-241, KAN-243에서, 구글과 카카오는 로그인을 켠 빌드 10(App PR #43)에 맞춰 KAN-289에서 넣었다.

### 2026-10-24 KAN-293 추론 안쪽 단계 지표 수신과 콜드 기준 변경

KAN-204 시점(README 원문)에는 안쪽 다섯 단계(`transcribe`~`scoring`)를 워커 결과 JSON의 `stageMs`로 받기로 합의만 해 두었고, 전달본이 그
값을 싣기 전까지는 어댑터가 재는 바깥 구간(`lockWait`, `workerLoad`, `model`, `total`)만 올라와 대시보드의 나머지 줄이 비어 있었다. 당시
`warm` 차원의 `cold` 정의는 "그 워커의 첫 채점"이었다.

KAN-293(2026-10-24)에서 전달본이 이미 봉투의 `detail.stageMs`(`stt`, `mfa`, `score`)로 싣고 있었는데 어댑터가 최상위만 봐서 prod에 안쪽
계열이 0개였음을 확인하고, 어댑터가 `detail.stageMs`를 읽어 이름을 옮기게 했다 (모델 태그 `f77011c`부터는 다섯 이름으로 직접 싣는다).
같은 티켓에서 콜드 기준을 "그 워커에서 MFA가 처음 실제로 돈 요청까지"로 바꿨다 (커밋 2475b1b, 게이트 탈락이 콜드를 소비하지 않게).
실측: MFA 첫 실행 `align` 17.5초, 그 뒤 4~6초.

### 2026-10-24 KAN-293 전달본 단계 시간을 어댑터가 버리던 문제 발견
`score()` 안쪽은 어댑터에서 잴 수 없어 워커 결과 JSON의 `stageMs`(단계 이름 -> ms)로 받기로 했는데(KAN-204), 2026-10-24 ECR `ai-model:26ac67a`의 `serve.py`를 꺼내 보니 전달본은 이미 `detail.stageMs = {"stt", "mfa", "score"}`를 싣고 있었고 어댑터는 최상위 `stageMs`만 보고 있어 그 값을 전부 버리고 있었다. 그래서 워커(`_stage_ms`)가 최상위가 없으면 `detail.stageMs`를 읽고 옛 이름을 옮기도록 고쳤다.

### 2026-10-24 KAN-293 MFA 첫 실행 실측
콜드의 실체는 MFA 첫 실행이었다 (KAN-293 실측 `align` 17.5초, 그 뒤 4~6초). 그래서 콜드 기준을 "align이 처음 있는 요청까지"로 바꿨다.
