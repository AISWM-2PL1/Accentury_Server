# 인프라 설계 결정 기록

`infra/README.md`의 "설계 결정 기록" 절을 2026-10-29(KAN-297)에 그대로 옮긴 파일이다. 결정을 내린 당시의 근거를
남기는 기록이라 옛 값이 들어 있다. 지금 값과 절차는 [`docs/ops/`](../ops)를 본다. 이후 바뀐 결정에는 항목 끝에
**지금 (2026-10-29)** 표기를 붙였다. 아래 "README 본문에서 옮긴 결정" 절은 README 각 절 본문에 섞여 있던 결정을
모은 것이다.

## 설계 결정 기록 (옛 infra README 절)

- **잠금**: Terraform 1.15.8이 S3 네이티브 잠금(`use_lockfile`)을 지원해
  DynamoDB 테이블을 만들지 않는다 (티켓의 버전 확인 항목 해소, 2026-08-24).
- **인증서 조회 domain**: us-east-1 인증서의 주 도메인은 `accentury.app`
  (SAN `*.accentury.app`)임을 CLI로 확인했다 (2026-08-24). 조회는
  `*.accentury.app`이 아니라 `accentury.app`으로 한다.
- **alb-sg 인바운드**: AWS 문서 기준, VPC 오리진 생성 시 CloudFront가 VPC에
  `CloudFront-VPCOrigins-Service-SG`를 만들어 오리진행 ENI에 붙인다. alb-sg는
  그 SG만 소스로 허용한다 (IP 대역 아닌 SG 참조, KAN-121 원칙).
- **SPA 재작성 Function**: 소스 파일(`modules/edge/spa-rewrite.js`) 하나를
  정본으로 두고 환경별 Function 2개를 만든다 (2026-08-24 확정). 환경별 state
  분리 구조에서 단일 Function을 공유 소유할 수 없기 때문이다. KAN-126의
  "두 배포가 공유" 문구는 이 결정으로 수정됐다.
  `/.well-known/`은 예외로 그냥 통과시킨다 (KAN-32) — App Link 검증 파일
  `apple-app-site-association`은 확장자가 없어 재작성 규칙에 걸리는데, 그러면 애플 CDN이
  JSON 대신 index.html을 받아 Universal Link 검증이 성립하지 않는다. 예외를 접두사 통째로
  잡은 이유는 RFC 8615가 그 아래를 기계 판독 메타데이터용으로 예약했기 때문이다.
  `infra/modules/edge/spa-rewrite.test.mjs`가 이 동작을 붙들고 있다.
- **RDS 마스터 비밀번호**: `manage_master_user_password`로 RDS가 생성해
  Secrets Manager에 보관한다. 코드, tfvars, state 어디에도 평문이 없다.
- **AMI는 tfvars에 명시 (ai 호스트, KAN-246)**: `ai_ami_id`에 AMI ID를 적는다. 처음에는 SSM의 "최신 AMI"
  파라미터를 읽고 `ignore_changes = [image_id]`로 묶었는데(KAN-36), 손대지 않은 plan에 교체가 끼어드는 것은
  막았지만 최초 생성 시점의 AMI에 영구히 고정돼 OS 보안 업데이트가 들어오지 않았다. 명시하면 plan은 여전히
  조용하고, 갱신은 값 한 줄을 바꾸는 일이 된다 ("AI 호스트 AMI 갱신").
- **AMI 사전 적재는 만들었지만 쓰지 않는다 (ai 호스트, KAN-285, 2026-10-14)**: 첫 부팅 3분 22초 중 docker 설치와
  이미지 pull 2분 33초가 매번 같은 일이라 EC2 Image Builder 파이프라인(`modules/ai-ami`, 수동 실행)으로 AMI에 넣어
  봤다. 두 환경 실측에서 EBS 지연 로딩이 그 이득을 넘어서는 비용(모델 적재 15초 -> 1~4분, 전체 2분 50초~5분 34초)을
  물려 맨 AMI로 되돌렸다 ("사전 적재 AMI는 쓰지 않는다"). 파이프라인과 user_data의 건너뛰기 로직은 남겨 두었다 -
  FSR을 켜거나 가중치를 이미지 밖으로 빼는 날 다시 쓴다. AMI ID를 Terraform이 자동으로 읽지 않는 이유는 위 "AMI는
  tfvars에 명시"와 같다.
- **PriceClass_200**: 한국이 포함되는 최소 티어.
- **지표 수집은 Micrometer CloudWatch push (2026-09-05, KAN-38)**: 티켓이 남긴 선택지는
  "레지스트리 push"와 "구조화 로그 기반(Logs Insights)"이었다. push를 택한 이유는 셋이다 -
  KAN-36이 회로 상태를 올리려고 이미 배선(네임스페이스, 차원, IAM 조건, 발행 주기 1분)을
  깔아 두었고, 경보와 대시보드가 지표를 직접 읽으며, 백분위를 낼 수 있다. 대가는 이름 x 차원
  조합마다 붙는 월 0.30달러라, 태그는 값이 다섯 이하로 닫힌 것만 쓴다. 자세한 근거는
  `docs/wiki/observability.md`.
- **actuator는 지표를 노출하지 않는다 (KAN-38)**: 수집 경로가 push라 `/actuator/metrics`를
  열 이유가 없다. 노출 폭은 여전히 health 하나이고 `ActuatorHealthApiTest`가 붙들고 있다.
- **backend 태스크 1개, ai 워커 1개 고정 (KAN-124, KAN-36, Fargate 전환 뒤에도 KAN-165)** -
  backend 쪽은 KAN-167과 KAN-168으로 풀렸다 (2026-09-02): 혼잡 판정과 활성 정의 포인터는 DB
  기준이 됐고 요청 제한, 회로 차단기, 워커 큐는 인스턴스별로 두기로 했으며(KAN-167), 그 위에
  오토스케일링 min 1 max 3이 붙었다 ("backend 오토스케일링" 절). 아래는 그 전의 근거다.
  backend가 다음 상태를 전부 프로세스 메모리에 둔다. 둘 이상이면 상태가 갈라져 요청
  제한이 배로 풀리고 회로 차단기가 서로 다른 판단을 한다.
  - 요청 제한 5축 (`RateLimits`: 세션 생성 IP, 업로드 IP, 업로드 세션, 어휘
    세션, 완료)
  - AI 회로 차단기 (`AiCircuitBreaker`: 닫힘/열림/반열림 상태, 연속 실패 카운터)
  - pollAfterMs 혼잡 판정 (진행 중 AI 전달 건수 임계치)
  - 분석 디스패처 풀 (`dispatch-concurrency` 3워커의 인메모리 큐)

  ai는 임시 디렉터리 하나를 프로세스 하나가 전용으로 쓴다 (KAN-27). 워커를
  늘리면 기동 시 잔여물 정리가 형제 워커의 처리 중 오디오를 지운다.
  `--workers 1`은 `ai/Dockerfile`이 고정한다. 같은 내용이 운영 compose 파일
  머리에도 있다.
- **backend는 ECS Fargate 온디맨드, Spot 없음 (2026-08-28 채택, 2026-09-01 Spot 제외, KAN-165)**:
  채택 근거는 비용이 아니라 학습이다 - 2026-08-19 멘토링이 Fargate 오토스케일링과 회수
  시 내결함성을 직접 경험하라고 했다. 순수 기술 논거로는 EC2 ASG가 이 규모에 맞고 Fargate가
  이기는 것은 스케일아웃 속도 하나다. 크기는 0.5 vCPU / 2 GB x86 (t3.small 18.98달러 대
  24.46달러/월). Spot을 빼는 근거 셋: base 1이 온디맨드라 프로토타입 트래픽에서 절감이 0에
  가깝다, 멘토 지시의 핵심(회수 시 제대로 꺼지고 켜지는지)은 KAN-169가 `stop-task`로 같은
  SIGTERM 후 stopTimeout 경로를 모사한다, Fargate Spot은 용량 부족 시 온디맨드로 대체하지
  않고 재시도만 한다. 다시 볼 조건은 스케일아웃 태스크가 상시 존재할 때이고 그때는 클러스터
  용량 공급자와 서비스 전략에 `FARGATE_SPOT`을 더한다. prod는 처음부터 이 구조로 세운다
  (2026-08-31, KAN-171).
- **이미지 태그의 정본은 SSM `IMAGE_TAG` 하나, Terraform 태스크 정의는 `track_latest` (KAN-165)**:
  Terraform이 태그를 data 소스로 읽어 태스크 정의에 넣고, 파이프라인은 리비전을 복제해
  image만 바꾼다. Terraform이 패밀리의 최신 ACTIVE 리비전을 state로 읽으므로 파이프라인
  배포 뒤에도 plan이 No changes이고, Terraform이 바꾼 형태(cpu, secrets)는 apply가 곧
  롤링 배포다. 검토한 대안 `ignore_changes = [task_definition]`은 Terraform 변경이 다음
  파이프라인 배포까지 서비스에 닿지 않는다. 조건은 파이프라인이 실패한 리비전을
  deregister하는 것이고, 파이프라인 리비전에 default_tags가 없어 태그는 `ignore_changes`다.
  첫 구축은 IMAGE_TAG가 먼저 있어야 한다 (ai 호스트와 같은 전제).
- **대상 그룹은 ip, 이름은 `-backend` (KAN-165)**: `target_type`은 교체 강제라 instance에서 ip로
  가며 그룹이 새로 만들어졌고, 리스너가 옛 그룹을 가리키는 동안 옛 그룹을 지울 수 없어
  `create_before_destroy`가 필요한데 같은 이름은 먼저 만들 수 없다. 등록 해제 지연은 30초
  (KAN-166 종료 예산 - HTTP 요청이 전부 1초 미만), 헬스체크는 10초 x 3회 (기본 30초 x 5회면
  롤링 배포와 회로 차단기 판정이 2분 30초씩 늦다).
- **실행 역할과 태스크 역할 분리 (KAN-165)**: EC2 시절 인스턴스 역할 하나가 ECR pull, SSM,
  Secrets Manager, CloudWatch를 다 가졌다. 실행 역할(ECS 에이전트 몫: ECR pull, awslogs,
  secrets 주입용 `ssm:GetParameters` 13개)은 컨테이너 안에서 보이지 않고, 태스크 역할
  (애플리케이션 몫: RDS 시크릿, PutMetricData)만 SDK 기본 체인으로 흘러간다. 신뢰 정책에
  `aws:SourceAccount`, `aws:SourceArn` 조건을 둔다 (AWS 문서의 혼동된 대리인 방지).
  SecureString은 AWS 관리 키라 kms 권한이 따로 없다.
  **지금 (2026-10-29)**: secrets는 staging 28개, prod 29개다(`modules/config/outputs.tf`의 `parameter_names`). 태스크 역할에는
  음성 버킷 PutObject(KAN-269)와 prod 번역 기록 PutObject(KAN-266)가 더 붙었다. 지금 목록은 [`docs/ops/secrets-ssm.md`](../ops/secrets-ssm.md)와 [`docs/ops/backend.md`](../ops/backend.md).
- **backend 태스크도 퍼블릭 서브넷 + 퍼블릭 IP, VPC 엔드포인트 없음 (KAN-165)**: ECR, SSM,
  Secrets Manager, CloudWatch에 닿으려면 사설 서브넷에서는 NAT(KAN-121이 뺌) 또는 VPC
  엔드포인트 5종(ecr.api, ecr.dkr, logs, ssm, secretsmanager - 개당 시간당 0.013달러, 월
  약 47달러)이 필요하다. 지금 EC2도 NAT 없이 퍼블릭 IP로 도는 구조와 같고, 인바운드는
  backend-sg가 alb-sg만 허용해 닫혀 있다. 대가는 태스크마다 붙는 퍼블릭 IPv4 3.65달러/월인데
  EC2도 같은 값을 냈다.
- **회로 차단기 + 파이프라인 시간 초과 롤백 (KAN-165)**: 서비스는 `deployment_circuit_breaker`
  enable + rollback이다. desired 1이면 임계가 AWS 최소값 3회라(2026-09 문서, 종전 10) 즉시
  죽는 이미지(arm64 exec format error 등)는 몇 분 안에 FAILED가 되고 ECS가 직전 배포로
  되돌린다. 헬스체크만 계속 실패하는 태스크는 grace period + 판정 + 교체가 3번이라 10분
  넘게 걸리므로 파이프라인이 20분을 상한으로 두고, 그 안에 COMPLETED가 아니면 직전 태스크
  정의로 직접 `update-service` 한다. 프로바이더 6.61은 `thresholdConfiguration`을 아직
  지원하지 않는다.
- **경보 `no-healthy-target`, `backend-cpu-high`, `backend-mem-high` (KAN-165, 2026-09-02 사용자
  결정)**: 근거는 위 "경보와 알림"의 의도된 선택. `ec2-cpu-surplus`는 옮길 대상(버스트
  인스턴스)이 없어 제거했다.
- **롤링 배포 중 태스크 2개 겹침은 수용 (KAN-165)**: 배포의 몇 분 동안 인메모리 상태(요청 제한,
  회로, 혼잡 판정)가 갈라지고 디스패처도 둘이 된다 - "태스크 1개 고정"의 의도된 예외다. KAN-166이
  롤링 배포의 겹침을 전제로 종료 순서를 만들었고, 상시 다중 태스크 방침은 KAN-167, 오토스케일링은
  KAN-168이 정한다. 겹침을 없애는 min 0 / max 100은 배포마다 수 분의 전면 중단이라 기각했다.
- **ECS Exec(대화형 접속)은 켜지 않는다 (KAN-165)**: EC2 시절의 SSM Session Manager 자리인데, 평시
  진단은 로그와 서비스 이벤트, 지표로 충분하다. 태스크 안에서 재현해야 하면 서비스에
  `enable_execute_command = true`와 태스크 역할의 ssmmessages 4종을 더해 새 배포를 강제한다
  (fargate/main.tf 주석).
  DB에 사람이 SQL을 넣어야 할 때는 ECS Exec 대신 AI 호스트 경유 SSM 포트 포워딩과 임시 SG 규칙을
  쓴다 (KAN-220, 2026-09-21 - "RDS 운영자 접속과 Flyway 재베이스라인" 절). 상시 통로(bastion,
  ECS Exec)는 여전히 두지 않는다.
- **배포 역할의 태스크 정의 권한 범위 (KAN-165)**: AWS 서비스 권한 레퍼런스 기준
  RegisterTaskDefinition만 리소스 수준 권한을 지원해 이 환경 패밀리로 좁혔다. Describe와 Deregister와
  List는 `*`뿐이라 staging 역할이 prod 리비전을 deregister할 수 있는 표면이 남는데, 삭제가 아니라
  INACTIVE 전환이고 도는 서비스에 영향이 없어 수용한다.
- **보안 그룹 description의 옛 문구 (KAN-165)**: alb, rds, ai SG의 description에 "ec2-sg"가
  남아 있다. description은 교체 강제 속성이고 이름이 고정이라(같은 이름을 먼저 만들 수
  없다) 살아 있는 스택에서 바꾸면 SG 교체가 RDS와 ASG 부착 때문에 실패한다. 규칙의 참조는
  전부 backend-sg로 옮겼고 문구만 남았다 - 다음에 스택을 처음부터 지을 때 고친다.
- **ec2-sg 제거 apply의 순서 함정 (2026-09-02 staging 실측, KAN-165)**: ai-sg 인바운드 규칙의
  참조를 ec2-sg에서 backend-sg로 바꾸는 in-place 갱신이 ec2-sg 삭제보다 뒤에 잡혀, 삭제가
  DependencyViolation으로 계속 재시도됐다 (규칙이 옛 그룹을 참조하는 동안은 못 지운다).
  `aws ec2 modify-security-group-rules`로 참조를 먼저 바꿔 풀었다. 처음부터 짓는 스택에는
  없는 문제이고, 살아 있는 스택에서 SG를 갈아 끼울 때는 규칙 참조를 먼저 apply한다.
- **AI는 전용 EC2, 스텁 모드부터 (2026-08-31, 2026-09-01 확정, KAN-36 A단계)**: backend와 같은 호스트에
  두면 Fargate 전환(KAN-165)과 오토스케일링(KAN-168)이 ai까지 복제해 의미가 없고, 실모델(RSS 7.1GB,
  지속 추론)은 backend와 메모리를 나눌 수 없다. 분리 시점을 "실모델이 나오면"에서 "지금, 스텁으로"로
  당겨 prod를 최종 구조로 한 번만 세운다 (KAN-171). 인스턴스는 처음부터 c7i.xlarge다 (2026-09-01) -
  t3.small로 시작하는 안은 단계 전환에 인스턴스 교체를 하나 더 만들고 staging 실증과 prod의 유형을
  다르게 했다. 대가는 스텁 기간의 월 147달러다. 루트 볼륨(40GiB)과 `mem_limit`(7g)은 B단계(2026-09-10)가
  tfvars와 compose로, 실모델 이미지는 KAN-22가 바꿨다. Fargate가 아니라 EC2인 이유는 모델 로드가 길고 이미지가 커서(7GB)
  스케일링 이점이 없고 GPU 선택지가 Fargate에 없기 때문이다. 1대 고정(ASG min 1 max 1)이라 교체 동안
  분석은 끊기고 회로가 열렸다 닫힌다 - 2대 이상은 내부 LB(월 약 20달러)가 필요해 미뤘다.
  **지금 (2026-10-29)**: KAN-201(2026-09-11)이 내부 ALB와 오토스케일링(min 1, max 3)을 두 환경에 넣었다. [`docs/ops/ai-host.md`](../ops/ai-host.md).
- **backend가 AI를 부르는 이름은 Route 53 프라이빗 영역 (KAN-36)**: ASG가 인스턴스를 교체하면 사설
  IP가 바뀌므로 주소를 SSM에 박을 수 없다. 인스턴스가 부팅 시 자기 IP로 `ai.accentury.internal` A
  레코드(TTL 10초)를 UPSERT하고 backend는 이름만 안다 - `ACCENTURY_ANALYSIS_AIBASEURL`의 정본은 여전히
  `modules/config`다. IAM은 그 영역, 그 이름, A 타입, UPSERT만 허용한다. 검토한 대안은 고정 ENI 사전
  생성(AZ에 묶여 ASG를 서브넷 1개로 제한, attach 실패 경로 추가)과 내부 NLB(월 16달러 이상, 2대
  이상일 때 쓰기로 한 것)였고, 프라이빗 영역은 월 0.5달러다. 영역 이름은 두 환경이 같다 - VPC별로
  풀리므로 충돌하지 않고 tfvars 차이도 늘지 않는다.
  **지금 (2026-10-29)**: 인스턴스가 A 레코드를 UPSERT하지 않는다. `ai.accentury.internal`은 Terraform이 소유한 내부 ALB의
  alias 레코드다(KAN-201, `modules/ai-host/main.tf`의 `aws_route53_record.ai`). 이름이 정본이라는 결정은 그대로다.
- **내부 호출은 보안 그룹 + 공유 시크릿 헤더 (KAN-36)**: 같은 compose 네트워크였을 때는 "backend만
  부른다"가 네트워크 구조로 보장됐다. 호스트가 갈라지면 ai-sg(ec2-sg만 8000)가 한 겹이고, 잘못 붙은
  SG나 뒤에 추가되는 VPC 안 호스트를 위해 `X-Accentury-Internal-Token`을 요청마다 대조한다
  (`ai/app/auth.py`, backend `RestAiAnalysisClient`). 난수 하나를 SSM 두 이름으로 싣고, ai 쪽은 하위
  경로 `/accentury/{env}/ai/`에 두어 ai 호스트 역할이 backend 시크릿(DB URL, 관리자 토큰)을 읽을 수
  없게 한다. health는 예외다 - compose healthcheck와 호스트 타이머가 토큰 없이 두드린다. 토큰이 어긋난
  배포는 backend가 429와 같은 추론 전 거절(UNREACHED)로 접어 사용자 시도 상한을 깎지 않고, 회로를
  열어 `ai-circuit-open` 경보로 드러낸다. ai 쪽은 운영 compose가 `ACCENTURY_AI_INTERNAL_TOKEN_REQUIRED=true`를
  주므로 토큰 없이는 기동하지 않는다 (fail-closed, backend 가드와 대칭).
  **지금 (2026-10-29)**: ai-sg는 ai-alb-sg만 8000으로 받는다(KAN-201, `modules/network/main.tf`의 `ai_from_ai_alb`). 토큰 대조는 그대로다.
- **ai 컨테이너의 인터넷과 IMDS 차단은 호스트 iptables (KAN-36)**: 같은 호스트 시절의 internal
  네트워크는 8000을 발행해야 하는 전용 호스트에서 쓸 수 없고, 보안 그룹 egress는 호스트와 컨테이너를
  못 가른다(호스트는 ECR, SSM, Route 53, CloudWatch에 나가야 한다). 그래서 `DOCKER-USER` 체인에
  "컨테이너 브리지에서 시작되는 연결 중 VPC 밖 목적지 DROP"을 넣고(docker가 만들고 비우지 않는
  사용자 체인), IMDS는 규칙 한 줄과 시작 템플릿의 hop limit 1로 겹쳐 막는다. 가드 유닛이 실패하면
  compose도 뜨지 않는다 (fail closed). 실모델이 런타임에 밖을 봐야 하면 그때 목적지를 열어 준다 -
  Whisper 가중치는 빌드 시 이미지에 넣는다 (KAN-22).
- **AI 경보는 커스텀 지표 2종 (KAN-36 사용자 결정)**: 대상 그룹 health가 없는 호스트라 타이머가
  `Healthy`를 올리고, 회로 열림은 backend의 Micrometer CloudWatch 레지스트리가 올린다. Boot 4는 이
  레지스트리를 자동 구성하지 않아 `CloudWatchMetricsConfig`가 직접 조립하고, `accentury.*` 지표만
  내보내 이름당 요금을 묶는다. 대안(회로 열림을 KAN-38로 미루기)은 "AI가 떠 있어도 추론만 죽은
  장애를 아무도 모른다"는 이유로 기각됐다.
  **지금 (2026-10-29)**: KAN-201부터 내부 ALB 대상 그룹이 있어 `ai-alb-no-healthy-host` 경보가 더해졌다. 지금 경보 목록은 [`docs/ops/alarms.md`](../ops/alarms.md).
- **파이프라인은 ai 호스트를 먼저 reload하고 롤백은 역순이다 (KAN-36)**: backend가 새 AI 계약을 전제로
  할 수 있어 AI가 먼저 올라와야 하고(새 AI는 옛 backend 호출을 받아야 한다), 롤백은 backend를 먼저
  되돌려 "옛 AI + 새 backend" 창을 만들지 않는다. Run Command 대상은 두 호스트의 Name 태그이고
  deploy 모듈이 그 둘만 허용한다.
- **ai 호스트의 compose 파일은 Terraform user_data로 배치** (2026-08-25 확정): 별도 전달
  채널(S3, Run Command) 없이 "재부팅 자동 기동"과 "두 환경 같은 파일" AC가
  성립한다. 대가는 compose 변경 시 인스턴스 교체인데, 호스트가 무상태라 감수한다.
  배포 파이프라인(KAN-128)은 SSM `IMAGE_TAG` 갱신과 `systemctl reload`만 한다.
  backend는 KAN-165부터 compose가 없다 - 태스크 정의가 그 자리다.
  compose 파일과 스크립트는 `base64gzip`으로 실어 왔다가 (2026-08-26, KAN-129 리뷰)
  **2026-09-05에 S3 부팅 자산 버킷으로 옮겼다** (KAN-38, 위 "부팅 자산 버킷").
  압축해도 16KB 상한의 75%였고 KAN-38의 health 스크립트 확장이 상한을 넘겨,
  precondition의 error_message가 제시하던 두 갈래 중 "주석을 줄인다"가 아니라
  "S3 배치로 옮긴다"를 골랐다 - 주석을 깎는 쪽은 결정 근거를 태우면서 KAN-36
  B단계(compose 확장)에 다시 걸린다. 2026-08-25 결정의 "별도 전달 채널 없이"는
  파일 배치 경로에 한해 깨졌지만, 성립시키려던 AC 둘(재부팅 자동 기동, 두 환경
  같은 파일)은 그대로다 - 파일의 정본은 여전히 Terraform이고 S3는 전달만 한다.
- **ssm_prefix는 env와 결합** (2026-08-26): envs 변수 validation이 `/accentury/{env}`와
  정확히 같은지 plan에서 세운다. prod tfvars에 staging 접두사를 잘못 적으면 prod EC2
  역할이 staging 경로를 읽게 되기 때문이다. compute의 인스턴스 precondition도 config가
  만든 이름이 같은 접두사 아래에 있는지 본다.
- **env 파일은 tmpfs(/run)**: 재부팅마다 SSM에서 다시 읽으므로 "마지막으로
  반영된 태그"의 정본은 SSM이고, 호스트에 낡은 env 사본이 쌓이지 않는다. docker의
  컨테이너 config에는 환경 변수가 남는다 (암호화 루트 볼륨). 파일 시크릿 마운트로
  그것까지 없애려면 앱이 파일을 읽어야 하므로 KAN-129 판단 사항으로 넘긴다
  (Codex P2, 2026-08-25).
- **DB 자격 증명은 Secrets Manager에서 연결 시점에** (2026-08-25 확정, KAN-129):
  RDS 관리형 마스터 시크릿의 7일 회전을 앱이 따라가야 한다. 검토한 대안은 셋이다.
  (1) Terraform `random_password`로 고정 비밀번호 - 회전이 없고 단순하지만 state와
  SSM에 비밀번호가 남고 KAN-122의 관리형 시크릿 결정을 되돌린다. (2) 기동 스크립트가
  Secrets Manager를 읽어 env로 주입 - 회전 시점마다 reload가 필요해 운영 부담이 남는다.
  (3) AWS Advanced JDBC Wrapper `awsSecretsManager` 플러그인 - AWS가 문서화한 경로이고,
  회전 뒤 인증 실패에 시크릿을 다시 받아 재접속하며, 비밀번호가 어디에도 복사되지
  않는다. (3)을 골랐다. 앱 전용 DB 사용자 분리(최소 권한)는 정식 개발 전환 시 검토한다.
- **trusted-proxies는 VPC CIDR 하나** (KAN-129): VPC 오리진 구조에서 CloudFront 발
  트래픽은 VPC 안 ENI 사설 IP로 들어오고 ALB도 VPC 안이라 XFF의 오른쪽 두 홉이
  같은 대역에 든다. CloudFront 오리진 페이싱 공인 대역은 매칭될 일이 없어 넣지 않는다.
  **정정 (KAN-244, 2026-10-02 staging 실측)**: XFF에는 그 공인 대역의 엣지 IP도 한 홉 들어 있어,
  VPC CIDR만 신뢰하면 backend가 엣지 IP(예: 54.182.245.160)를 접속자로 뽑았다. 전원이 엣지 서버
  단위로 IP 한도를 나눠 쓰고 있었다. 대역을 신뢰 목록에 넣는 대신 API 경로의 오리진 요청 정책이
  `CloudFront-Viewer-Address`를 넘기고, backend `ClientIps`가 신뢰 프록시 뒤에서 그 헤더를 먼저 읽는다
  (`modules/edge`의 `aws_cloudfront_origin_request_policy.api`). 대역 목록은 AWS가 바꾸면 조용히 같은
  문제로 돌아가지만, 헤더는 CloudFront가 직접 채우는 값이라 관리할 목록이 없다.
  **실제 원인 (2026-10-03)**: 헤더를 넘긴 뒤에도 엣지 IP가 찍혔다. Spring Boot 4.1이 ECS(`AWS_EXECUTION_ENV`)를
  감지해 `server.forward-headers-strategy`를 `NATIVE`로 켜고, Tomcat RemoteIpValve가 XFF로 `remoteAddr`를 엣지
  IP로 먼저 바꿔 놓아 `ClientIps`가 신뢰 프록시 뒤라는 것을 몰랐다. backend `application.yml`에서 `none`으로
  고정했다 (`ForwardHeadersBootTest`가 실제 Tomcat으로 재현한다).
- **Spring 프로파일 이름은 `deploy`** (KAN-129): staging과 prod가 같은 이름을 쓴다.
  환경 이름을 프로파일로 쓰면 `application-staging.yml` 같은 환경별 파일이 생길 여지가
  남아 "환경 간 차이는 tfvars와 SSM 값뿐"이 깨진다.
- **SSM 파라미터는 Terraform 소유, IMAGE_TAG만 예외** (KAN-129): 값이 모듈 출력에서
  오므로 코드가 정본이다. IMAGE_TAG는 배포마다 바뀌어 Terraform이 소유하면 매 배포가
  drift라 파이프라인(KAN-128)이 쓴다. destroy해도 IMAGE_TAG는 남으므로 재구축 시
  이전 태그로 바로 뜰 수 있다 - 그래서 인스턴스가 config 파라미터 뒤에 만들어지도록
  이름 목록을 참조한다. 모듈 `depends_on`은 쓰지 않는다 - compute의 data 소스까지
  apply 시점으로 미뤄 user_data가 unknown이 되고, config 값 하나만 바뀌어도 인스턴스가
  교체된다. Fargate에서는 같은 목록이 태스크 정의 secrets와 실행 역할 허용 목록이 된다.
- **ai 컨테이너는 internal 네트워크에만** (2026-08-26, KAN-129 리뷰): backend가 Secrets
  Manager를 읽으려면 IMDSv2 hop limit이 2여야 하는데, 그 값은 bridge 안 컨테이너 전부에
  인스턴스 자격 증명을 연다. 신뢰하지 않는 오디오를 받는 ai가 뚫리면 RDS 시크릿과 관리자
  토큰까지 읽는 자격 증명이 새므로, ai는 게이트웨이 없는 internal 네트워크에만 붙여 IMDS와
  인터넷 경로를 끊는다. backend는 default와 internal 둘 다에 붙는다.
  **지금 (2026-10-29)**: backend와 ai가 같은 EC2에 있던 시절의 결정이다. backend는 Fargate(KAN-165), ai는 전용 호스트(KAN-36)로
  갈라졌고, ai의 인터넷과 IMDS 차단은 위 "ai 컨테이너의 인터넷과 IMDS 차단은 호스트 iptables" 항목이 맡는다.
- **WAF 업로드 예외는 스코프 다운, override가 아님** (2026-08-28, KAN-149):
  `rule_action_override`로 본문 규칙 5개를 Count로 내리면 모든 경로에서 XSS/LFI/RFI 본문
  검사가 꺼진다. 스코프 다운은 업로드 경로만 CommonRuleSet에서 빼고 JSON 본문 경로의
  검사는 남긴다. 스코프 다운은 규칙 그룹 단위라 "업로드 경로에서 특정 규칙만 빼기"는
  불가능하고, 같은 관리형 그룹을 한 웹 ACL에 두 번 넣을 수도 없다.
  **지금 (2026-10-30)**: 라벨 매칭으로 바꿨다 (KAN-299, PR #55 리뷰). 본문 규칙 5개만 `rule_action_override`로 Count로 돌려 라벨을
  남기고, 뒤따르는 규칙 `aws-common-body`가 그 라벨이 붙었고 음성 업로드 경로가 아닌 요청만 차단한다. 위 전제("모든 경로에서 본문 검사가
  꺼진다")는 라벨 규칙을 두지 않을 때만 맞다. 업로드 경로도 그룹의 비본문 검사를 받게 됐고, 억양 학습 업로드 경로가 더해졌다.
  [`docs/ops/waf-edge.md`](../ops/waf-edge.md) "음성 업로드 경로 예외".
- **rate-based rule은 세션 생성과 업로드만 센다** (KAN-149): 웹이 `/complete`를 800ms
  마다 POST로 폴링해 `POST /v0/*` 전체를 세면 정상 사용자 1명이 5분에 375건이다. GPU
  비용이 되는 경로는 세션 생성과 업로드뿐이다.
  **지금 (2026-10-29)**: 이용 후기 `POST .../feedback`도 같은 규칙이 센다. KAN-244가 로그인과 refresh(100), 관리자 경로(50) rate 규칙을
  따로 더해 규칙은 6개다. [`docs/ops/waf-edge.md`](../ops/waf-edge.md).
  **지금 (2026-10-30)**: 억양 학습 업로드 `POST .../recordings`도 같은 규칙이 센다 (KAN-299). 본문 규칙 차단 `aws-common-body`가 더해져 규칙은 7개다.
- **WAF 차단 응답은 backend 봉투와 같은 429 JSON** (KAN-149): 앱은 봉투 없는 응답을
  `retryable = false`로 판정해 재시도 버튼을 지운다 (`UploadClient.toResult`,
  `UploadStatusBar`). 기본 403을 쓰면 사용자가 막다른 길에 선다.
- **WAF 로그는 매치만, Authorization 마스킹, 샘플 요청 끔** (KAN-149): 기본 Allow까지
  남기면 정적 자산이 대부분이라 비용만 든다. 로그 헤더의 세션 Bearer 토큰과
  X-Admin-Token은 `redacted_fields`로 가린다. 샘플 요청 저장은 마스킹 보장을 문서에서
  확인하지 못해 끈다.
- **WAF는 staging에도 붙인다** (2026-08-28 확정, KAN-149 코멘트): 실모델 전환이 임박해
  오탐과 임계값을 staging에서 먼저 봐야 하고, 두 환경 구조를 같게 유지한다. 월 8달러를
  감수한다.
  **지금 (2026-10-29)**: 규칙이 6개로 늘어 환경당 월 약 11달러다(KAN-244).
- **backend 태스크 0.5 vCPU / 2 GB (2026-08-28 확정, KAN-165)**: 힙은 2 GB의 75%(1.5 GB,
  `backend/Dockerfile` MaxRAMPercentage)이고 나머지가 네이티브 영역이다. EC2 시절의
  compose `mem_limit: 1g`(KAN-120 실측 권고 하한 - t3.small 2GB를 OS, docker와 나눴다)보다
  넉넉하다. Fargate 0.5 vCPU는 버스트가 없어 JVM 기동이 느리므로 grace period를 실측으로
  잡는다 (위 "backend Fargate 서비스").
- **ECR 라이프사이클 최근 50개**: 롤백(KAN-128)이 이전 SHA를 다시 당기는 방식이라
  최근 이미지가 남아 있어야 한다. prod가 50번 이상 전의 Dev 커밋을 돌리게 되면
  값을 올린다.
- **실모델 베이스 이미지는 별도 리포지토리 `accentury/ai-model`에 사람이 push**
  (2026-08-31 확정, KAN-173): 배포용 2개는 CI가 commit SHA 태그로 자동 push하는
  곳이라 손으로 올린 모델 이미지를 섞으면 "태그 = 커밋" 전제가 깨진다. 모델용은 태그가
  모델 해시다. 라이프사이클은 배포용과 같은 정책을 쓴다. 이미지 하나가 약 5GB라 50개가
  실제로 차면 저장 비용(GB당 월 0.10달러)이 드러나지만 모델 해시가 바뀔 때만 push하므로
  상한에 닿지 않는다. push 권한은 리포지토리 한정 정책 대신 모델 담당의 IAM 사용자
  `jaeyoung`에 AdministratorAccess를 주는 것으로 정했다 (3인 팀 규모의 사용자 결정).
  **지금 (2026-10-29)**: 실측 모델 베이스는 4.12GB, 그 위 ai 이미지는 7.02GB다(2026-09-10 staging, `envs/*/terraform.tfvars` 주석).
- **graceful shutdown 유예 (2026-08-31 확정, KAN-166)**: backend는 SIGTERM에 readiness를 내리고
  (집계 `/actuator/health` 503), 웹 요청을 15초 안에 마친 뒤, 실행 중인 분석만 90초 예산 안에서
  기다리고 대기 중(미시작) 분석은 즉시 실패로 정리한다 - 원본 음성을 저장하지 않아(FR-DP-01)
  끊긴 분석은 재녹음뿐이라, 예산을 큐 길이만큼 늘리다 SIGKILL을 맞는 것보다 즉시 재녹음 안내가
  낫다. 그래서 compose `stop_grace_period`는 110초(docker 기본 10초면 시작하자마자 죽는다),
  systemd `TimeoutStopSec`는 180초(ExecStop의 compose down이 그 유예를 기다린다)다. 티켓 표의
  웹 유예 90초는 워커 예산과 합쳐 최악 180초라 ECS stopTimeout 120초를 넘어 15초로 줄였다.
  Fargate(KAN-165)에서는 태스크 정의 `stopTimeout` 120초와 대상 그룹 `deregistration_delay`
  30초가 그 자리다 - ECS는 등록 해제 지연이 끝난 뒤 SIGTERM을 보내고, 120초 뒤 SIGKILL이다.
  compose 값은 ai 호스트에만 남는다.
- **push-images.sh 기본 PLATFORM=linux/amd64**: IMMUTABLE 태그라 arm64로 잘못
  올린 SHA는 다시 올릴 수 없다. 기본값을 운영 아키텍처로 고정해 그 사고를 막는다.
- **오리진 구간(CloudFront -> ALB) HTTPS** (2026-08-25 확정, KAN-125): ALB
  리스너는 443 하나뿐이고(HTTP 80 없음) 서울 리전 ACM 인증서(accentury.app +
  *.accentury.app)를 건다. CloudFront는 오리진 인증서 도메인이 Origin domain 값
  또는 오리진으로 전달되는 Host 헤더와 맞으면 받아들이는데(AWS 문서 "Require
  HTTPS for communication between CloudFront and your custom origin"), API 동작이
  viewer 헤더를 전부 넘기는 정책(KAN-244부터 Managed-AllViewer 대신 사용자 정의 `api` 정책)이라 Host가 ALB까지 가므로 ALB DNS 이름과 인증서가 달라도
  된다. VPC 오리진 정책은 https-only, alb-sg 인바운드는 443만 연다. 인증서
  2장(us-east-1은 뷰어 구간, 서울은 오리진 구간)이 각각 쓰인다.

## README 본문에서 옮긴 결정 (KAN-297, 2026-10-29)

위의 "설계 결정 기록" 절 밖, README 각 절 본문에 섞여 있던 결정 경위다. 당시 서술 그대로이고 지금 값은 docs/ops를 본다.

### GitHub, 배포, backend

#### 이미지는 한 번만 빌드하고 prod는 승격만 한다 (KAN-128)

이미지는 Dev 푸시에서 한 번만 만들고 prod는 staging에서 검증된 그 SHA를 고른다(2026-08-17 확정, deploy.yml 머리 주석). prod에서 재빌드하면 staging 검증이 무의미해진다. staging 검증 표시는 SSM 교차 읽기 대신 ECR 태그 `verified-<sha>`로 남긴다(Codex P1). prod 역할의 ECR 조회 권한만으로 확인되고, IMMUTABLE이 새 태그 추가는 허용하기 때문이다. Release의 후보 SHA에 표시가 없으면 더 오래된 SHA로 건너뛰지 않고 실패한다. 서버 변경이 조용히 빠지는 것을 막기 위해서다.

#### ai 먼저 올리고 역순으로 되돌린다 (KAN-128, KAN-165)

backend가 새 AI 계약을 전제로 할 수 있어 AI가 먼저 올라와야 한다(새 AI는 옛 backend 호출을 받아야 한다). 실패하면 backend, ai 순으로 되돌린다. 같은 순서로 되돌리면 "옛 AI + 새 backend" 창이 생기는데 그 조합은 아무도 보장하지 않는다.

#### 스모크 실패는 반영을 되돌리지 않는다 (KAN-128, KAN-217)

스모크가 보는 것은 이미지가 아니라 전 구간이라 원인이 이미지가 아닐 수 있다. 되돌림은 사람이 수동 실행에 직전 SHA를 넣어 한다. 파이프라인이 `e2e-smoke.yml`을 `workflow_call`로 부르지 않고 같은 job에서 스크립트를 직접 부르는 것은, 관리자 토큰을 job output으로 넘기면 GitHub이 마스킹된 값이라며 output을 버리기 때문이다.

#### Fargate Spot을 쓰지 않는다 (KAN-165, 2026-09-01)

클러스터에 `FARGATE`만 연결한다. base 1이 온디맨드라 절감이 0에 가깝고, 용량 부족 시 온디맨드로 대체하지 않는 위험만 더한다. 서비스는 launch_type이 아니라 용량 공급자 전략으로 만들어 나중에 Spot을 붙일 때 전략만 바꾸면 된다. 다시 볼 조건은 스케일아웃 태스크가 상시 존재하게 될 때다(modules/fargate/main.tf 주석).

#### ECS Exec를 켜지 않는다 (KAN-165, 2026-09-02)

평시 진단은 로그 그룹, 서비스 이벤트, 지표로 충분하다. 필요하면 `enable_execute_command`와 ssmmessages 4종을 더하고 새 배포를 강제한다(modules/fargate/main.tf 주석, 원본 README에는 없던 내용).

#### 오토스케일링 지표와 값 (KAN-168, KAN-169)

- 지표는 `ALBRequestCountPerTarget`이다. CPU 평균은 태스크 하나가 포화된 뒤에야 오르고, 이 서비스의 부하는 세션 생성과 폴링이라는 가벼운 요청의 개수라 요청 수가 스파이크에 먼저 반응한다.
- 목표값 1000의 산정(2026-09-02): 응시자 1명이 분석 대기 중 pollAfterMs 800ms로 분당 약 75건, 응시 전체 평균은 분당 약 20건이다. 1000이면 "동시 폴링 약 13명" 또는 "응시 중 약 50명"에서 늘어나 혼잡 판정 임계(PROCESSING 30건)와 같은 자릿수다. 0.5 vCPU의 처리 한계(수천 건/분)보다 훨씬 낮게 잡아 늘어나는 5분(경보 3분 + 기동 2분) 동안 태스크 1개가 포화되지 않게 한다. 2026-09-03 KAN-169 부하 시험에서 1650건/분에도 포화가 없어 1000으로 확정했다.
- min 1 / max 3(2026-09-02): min 2 예열은 월 약 24달러가 더 드는데 프로토타입 트래픽에 과하고 스파이크 초기는 혼잡 판정이 흡수한다. 상한 3은 당시 AI 서버가 EC2 1대라 backend를 늘려도 분석 처리량은 늘지 않기 때문이었다. backend 확장은 세션 생성과 폴링을 버티는 용도이고 분석 대기는 혼잡 판정(`pollAfterMs`)이 흡수한다. (2026-10-29 주: KAN-201부터 AI 호스트도 ASG max 3이라 "AI 1대"라는 근거는 지금 그대로 맞지 않는다. max 3 값은 다시 정하지 않았고 `modules/fargate/variables.tf`의 설명도 옛 근거 그대로다.)
- 쿨다운: 스케일아웃 60초는 태스크 기동 30초에서 2분(실측 2분 1초)보다 짧게 두어 큰 스파이크에는 연달아 늘린다. 스케일인 300초는 줄이기를 천천히 하기 위해서이고, 스케일인 경보 자체가 15분 조건이라 연속 축소의 간격만 정한다.
- 값은 두 환경이 같아야 하므로 tfvars가 아니라 모듈 기본값에 둔다.
- 목표 추적이 만드는 AlarmHigh, AlarmLow는 SNS에 붙이지 않는다. 늘고 주는 것은 정상 동작이고, 상한에 닿아 못 늘리는 상태는 `backend-cpu-high`가 대신 드러낸다.

#### Actions는 SHA로, 베이스 이미지는 digest로 고정하고 Dependabot이 월 1회 올린다 (KAN-246)

태그는 주인이 다른 커밋으로 옮길 수 있어서, 태그로 두면 남의 저장소가 뚫렸을 때 바뀐 코드가 우리 배포 역할(`id-token: write`)을 쥔 채 돈다(2025년 tj-actions 사례). 고정한 값은 저절로 움직이지 않으므로 Dependabot이 월 1회, 생태계마다 PR 하나로 갱신을 올린다(2026-10-05 결정, AI 호스트 AMI 갱신과 같은 주기).

#### 모델 베이스 push 권한은 기존 IAM 사용자로 둔다 (KAN-173, 2026-08-31)

모델 담당 IAM 사용자 `jaeyoung`(AdministratorAccess)을 콘솔에서 발급해 Terraform 밖에 두고, 리포지토리 한정 정책은 만들지 않는다. 다른 팀원 IAM 사용자와 같은 취급이다. 배포용 리포지토리 2개에는 손으로 올린 이미지를 섞지 않는다.

#### 등급 공유 이미지는 기준 URL 하나로 조립하고 스크립트로 게시한다 (KAN-132)

등급 5개 URL을 설정에 적지 않고 `ACCENTURY_RESULT_ASSETBASEURL` 하나에 등급 code를 붙인다. 환경마다 다른 것은 도메인뿐이고 파일명은 자산 정본이 등급 code로 고정해 두었기 때문이다. 이미지는 웹 번들과 수명이 달라(캐릭터가 바뀔 때만 바뀐다) 배포 파이프라인이 아니라 손으로 도는 스크립트로 올린다. 캐시는 1일이다. 1년 immutable이면 캐릭터 교체가 1년 걸리고, no-cache면 공유 카드마다 원본을 친다(scripts/publish-share-assets.sh 주석).

#### GA4는 환경마다 다른 스트림, AdSense는 prod에만 (KAN-33, KAN-197)

GA4 ID를 같이 쓰면 staging 확인 트래픽이 prod 집계에 섞인다. AdSense는 집계가 아니라 승인 대상이 prod 하나라서 prod에만 두고, 사이트 승인과 광고 단위 생성 뒤에 두 값을 함께 넣는다.

### ai 호스트, SSM과 시크릿

#### ai 호스트 앞단을 내부 ALB로, max 3 (KAN-201)

A 레코드 UPSERT는 1대 전제라 2대가 떠도 마지막에 뜬 1대만 호출을 받는다. 그래서 내부 ALB alias로 바꾸고 ASG를 열었다. 대상 그룹 알고리즘은 `least_outstanding_requests`다 - AI는 단일 lock이라 라운드 로빈이면 한 대에 2건이 겹친다. max 3은 비용 상한이다 (vCPU 쿼터로는 64대). 축소는 확대보다 훨씬 느리게(15분) 접는다 - 늘어난 동안만 과금이기 때문이다. backend 태스크가 호스트에 직접 닿는 길은 두지 않는다 (backend-sg -> ai-alb-sg -> ai-sg).

#### dispatch-concurrency 1 -> 3, 확대 판정 2분 -> 1분 (KAN-272, 2026-10-06)

"동시성은 backend 태스크 수에서 온다"는 KAN-201의 전제가 prod 실측에서 틀렸다 (history 2026-10-06). 태스크 하나가 동시에 3건을 보내도록 AI 최대 대수와 같은 3으로 올렸고, 확대 판정은 1분 x 1회로 줄였다 - 한 사람이 5문항을 연달아 올려도 5건이라 6에 닿지 않는다. 겹친 호출 9건 구간을 위해 AI는 lock을 잡을 때 남은 시간이 여유분(15초)보다 적으면 429로 추론 전 거절한다 (Codex astra 리뷰 P1).

#### 확대 워밍업 600 -> 300초 (KAN-281)

prod 실측 부팅(시작에서 healthy까지) 약 3분 + 경보 해제 지연 1~2분이다. 600초는 2026-10-06에 3번째를 약 7분 막았다.

#### 사전 적재 AMI 미채택, Fast Snapshot Restore도 쓰지 않음 (KAN-285, 2026-10-14)

오토스케일링 워밍업(300초)은 예측 가능한 쪽이 안전하다. 사전 적재 AMI는 EBS 지연 로딩으로 부팅이 2분 50초~5분 34초로 불규칙했다. FSR은 스냅샷당 AZ당 시간 요금이라 쓰지 않기로 했다. 파이프라인 모듈과 user_data의 "있으면 건너뛰기"는 남긴다 - FSR을 켜 보거나 가중치를 이미지 밖으로 빼는 날 다시 쓸 수 있다.

#### ai-ami 빌드 역할은 IMAGE_TAG 외 파라미터 읽기를 명시 Deny (KAN-285, Codex 리뷰)

`AmazonSSMManagedInstanceCore`가 `ssm:GetParameter`를 `*`에 허용하므로 Allow를 좁히는 것만으로는 경계가 서지 않는다. ai 호스트 역할도 같은 관리형 정책을 붙이고 있어 같은 틈이 있다 - 별도 티켓으로 남겼다.

#### reload 동안 ASG 프로세스 정지, 마커 파일로 재개 (KAN-201 Codex 리뷰 P1, KAN-212)

ELB 상태 검사를 보는 ASG는 컨테이너가 내려간 인스턴스를 유예 뒤 교체해 정상 배포를 "인스턴스 사라짐"으로 실패시킨다. job이 중간에 죽어도 `always()` 스텝이 마커에 적힌 ASG만 되살리고, 손으로 멈춘 프로세스는 건드리지 않는다.

#### ai-unhealthy 통계를 Maximum으로, 정본은 ai-alb-no-healthy-host (KAN-201)

Minimum이면 새로 뜬 인스턴스의 워밍업(0)이 확대할 때마다 운다. ALB 대상 그룹 HealthyHostCount가 정본이고 호스트 타이머 지표는 이중 안전망이다.

#### AMI 갱신은 월 1회, staging 먼저 (KAN-246)

인스턴스 안에서 `dnf update`를 돌리지 않으므로 OS 보안 업데이트는 AMI 교체로만 들어온다. Dependabot 월 1회 주기와 맞췄다. 교체는 새 인스턴스를 먼저 띄우는 순서(min_healthy 100, max_healthy 200)라 1대일 때도 무중단이다.

#### 부팅 자산을 S3로, 해시로 교체 고리 유지 (KAN-38)

user_data 16KB 상한 때문에 파일을 S3로 옮겼다. EC2 역할은 그 접두사 `GetObject`만 가져 호스트가 뚫려도 자기 부팅 파일을 바꿔치기하지 못한다. 해시를 user_data에 넣어 "파일이 바뀌면 인스턴스 교체"를 되살렸다.

#### mem_limit 7g, "호스트 프로세스는 살린다" (KAN-36 B단계, 2026-09-10)

상한이 없으면 호스트 OOM 킬러가 SSM 에이전트나 docker를 죽여 배포와 진단 경로까지 잃는다. 1.3배 규칙은 8GB 호스트에서 성립하지 않아 기준을 바꿨다. 근거는 compose 주석.

#### AI 상한 75초와 그 근거 (KAN-172, KAN-272)

한 호스트에 겹친 6건(6 x P95 11초 = 67초)과 워커 재적재(31초 + 11초 = 42초)를 각각 덮는다. 짧으면(25초, 40초) 추론 중인 요청을 끊어 멀쩡한 워커를 죽이고 재전송이 새 워커를 또 죽이는 연쇄가 된다 (Codex 리뷰 P1, 실제 어댑터로 재현). 합(97.6초)은 덮지 않는다 - 회로 차단기(KAN-28)가 연쇄를 자기수렴시킨다. 전역 한 건으로 묶는 일은 KAN-167 몫으로 두었다.

#### backend 환경 변수는 전부 Terraform config 모듈이 만든다 (KAN-129, KAN-165)

값이 다른 모듈의 출력이라 손으로 넣으면 재구축 때 어긋난다. 파라미터 목록이 곧 태스크 정의 secrets와 실행 역할 허용 목록이다 (KAN-132 때 코드 변경이 config 모듈 한 곳이었다).

#### DB 비밀번호를 SSM에 두지 않는다 (KAN-129)

RDS 관리형 시크릿은 7일마다 회전되므로 복사본은 첫 회전에서 죽는다. JDBC wrapper의 `awsSecretsManager` 플러그인이 직접 읽는다.

#### 관리자 토큰을 GitHub에 두지 않는다 (KAN-171, 2026-09-05)

저장소 시크릿은 하나라 두 환경 토큰을 못 담고, 아무 브랜치의 워크플로나 꺼낼 수 있다. 워크플로가 대상 환경 SSM에서 직접 읽는다.

#### 원격 스모크는 주소를 입력으로 받지 않는다 (KAN-171 Codex 리뷰 P1)

사람이 주소를 넣게 두면 SSM에서 읽은 관리자 토큰이 임의 호스트로 나간다. 주소를 정하려면 `workflow_call`로 토큰도 함께 넘겨야 한다.

#### 시크릿은 ephemeral + write-only, 회전은 버전 숫자 (KAN-242)

state(버전 관리 버킷)에 평문으로 남으면 버킷 읽기 주체가 JWT를 위조할 수 있었다. 카카오 Admin 키(KAN-164, Codex sol 리뷰 P2)가 먼저 쓴 방식이다 - `value` + `ignore_changes`는 refresh가 실제 값을 state에 읽어 온다.

#### Redis AUTH 토큰은 state에 남는 위험을 수용 (KAN-242, 2026-10-01)

provider에 write-only 인자가 없고, Redis는 사설 서브넷 + redis-sg(backend-sg의 6379만)라 토큰만으로는 쓸 수 없다.

#### state 버킷 읽기 주체를 좁히지 않는다 (KAN-242)

역할 목록이 틀리면 state가 잠기고, 읽을 수 있는 주체는 전부 SSM도 직접 읽는 관리자라 얻는 것이 없다. 대신 이전 버전 30일 만료와 TLS 강제를 둔다.

#### 후기 알림은 채널 하나, 환경 라벨로 구분 (KAN-211)

무료 플랜이라 수단이 Incoming Webhook 하나다. 가드 밖으로 두어 값이 없어도 후기 저장은 멈추지 않는다.

#### 번역 키는 환경별 AI Studio 프로젝트 (KAN-266, 2026-10-08), 계정당 하루 5건 (2026-10-15)

staging 실증이 prod 무료 한도를 쓰지 않게 프로젝트를 나눴다. RPD 500이 프로젝트 전체 몫이라 한 계정이 다 쓰지 못하게 5건 한도를 Redis에 뒀다. prod 기록 버킷은 `prevent_destroy`로 지키고 staging은 비용 때문에 기록하지 않는다.

#### Redis를 backend health에서 뺌, 스냅샷 없음 (KAN-223)

넣으면 Redis 장애 때 ALB가 태스크를 전부 뺀다. 노드 교체 시 손실은 Refresh뿐이라 한 번씩 다시 로그인하면 된다.

#### 음성 버킷을 두 환경 공용 하나로, bootstrap 소유 (KAN-269, 2026-10-04)

버전 관리가 버킷 단위라 음성 전용 새 버킷을 두고, 환경 destroy와 스위치 끄기가 음성을 건드리지 못하게 bootstrap에 뒀다. 접두사로 환경을 나누고 태스크 역할은 자기 접두에만 쓴다. 옛 staging 버킷은 `removed` + `destroy = false`로 관리에서만 뺐다.

### WAF, 전송 구간, 경보

#### WAF rate 규칙은 POST만 세고 경로를 정규화해 비교한다 (KAN-149)

GET, HEAD, OPTIONS는 backend가 405로 싸게 끝내는데 집계에 들어가면 같은 공유 IP의 정상 세션 생성과 업로드까지 밀어낸다. 경로는 `URL_DECODE` + `NORMALIZE_PATH` 뒤 정규식으로, 세그먼트 뒤 matrix 파라미터를 허용해 비교한다. Tomcat과 Spring이 `%73essions`, `./`, `;x=1`을 풀어 라우팅하므로 변환 없이는 변형 경로가 컨트롤러에는 닿으면서 집계만 비껴간다. 차단 대기 시간을 평가 창(5분)과 같게 둔 것은 한 번 넘긴 IP의 요청이 창에서 빠질 때까지 차단이 이어지기 때문이다.

#### rate limit 임계값 300 (KAN-149, 2026-08-28)

**지금 (2026-10-30)**: 400이다 (KAN-299). 레벨테스트가 7문항(음성 3)이 되면서 1인 최대는 8건으로 줄었고, 억양 학습 업로드가 집계에 더해져
학습 몫 100을 올렸다. 별도 규칙 대신 임계값 상향을 고른 이유와 공유 Wi-Fi 산정은 [`docs/ops/waf-edge.md`](../ops/waf-edge.md)
"rate limit 임계값의 근거". 아래는 300 당시의 산정이다.

- 정상 사용자 1명의 5분 창: 세션 1 + 업로드 5문항(재녹음 포함 10건 이내) = 최대 11건.
- backend의 IP당 제한이 세션 생성, 업로드 각각 분당 30(5분 150)이라 보통의 초과는
  backend가 먼저 잡아 정식 429 봉투를 낸다. WAF는 그 2배인 300에서만 개입해 backend가
  multipart를 받느라 지치기 전에 홍수를 엣지에서 자른다.
- 공유 Wi-Fi(학교, 시연장)는 외부 IP가 하나다. 동시 응시 20명 x 11건 = 220 < 300.
  시연 인원이 그보다 많으면 당일 tfvars 값을 올려 apply한다.
- staging도 같은 값이다. staging에서 본 결과가 prod에 그대로 적용되어야 Count 관찰의
  의미가 있다.

#### 인증 경로 임계는 backend 태스크 한도보다 낮게 (KAN-244, 2026-10-02)

로그인은 요청마다 외부 IdP를 부른다. backend의 AUTH 제한(IP당 분당 30)은 태스크별 인메모리라 태스크 수만큼 풀려, IP를 바꿔 가며 무효 토큰을 보내면 IdP 호출 쿼터가 소진돼 모든 사용자의 소셜 로그인이 502가 된다. 그래서 10번 규칙과 달리 `waf_auth_rate_limit = 100`을 backend 태스크 하나의 한도(5분 150)보다 낮게 뒀다.

#### 관리자 경로는 IP 허용 목록 대신 rate 규칙 (KAN-244, 2026-10-02)

E2E 스모크가 GitHub 호스티드 러너에서 관리자 API를 부르는데 러너 IP가 고정되지 않아 IP 허용 목록을 쓸 수 없다. 대신 IP당 추측 속도를 `waf_admin_rate_limit = 50`으로 묶고, backend가 인증 실패 IP를 WARN으로 남긴다.

#### IP 평판 규칙은 앱 출시 뒤 prod Count 로그를 보고 전환 (KAN-244, 2026-10-29)

IP 평판 규칙은 다른 규칙이 이미 Block인 뒤에 들어와 혼자 Count 관찰을 거치도록 `waf_ip_reputation_enforce`를 따로 뒀다. 2026-10-02에는 두 환경 false로 시작해 staging에서 일주일 관찰한 뒤 staging부터 true로 하고 prod가 뒤따르기로 했다. 2026-10-29에 이를 바꿔, 두 환경 false를 유지하고 앱 출시 뒤 prod의 Count 로그를 보고 전환하기로 정했다.

#### JDBC verify-full 적용 뒤 KAN-245 이전 이미지로 롤백하지 않는다 (KAN-245)

CA 번들이 이미지 안에 있어 URL이 바뀐 뒤 옛 이미지 태스크는 DB에 붙지 못한다. 꼭 돌아가야 하면 URL에서 `sslmode`, `sslrootcert` 두 파라미터를 먼저 뺀다.

#### HSTS에 preload를 넣지 않고 CSP는 Report-Only (KAN-245)

preload는 브라우저 목록에서 빼기가 몇 달 걸려 되돌릴 수 없다(edge/main.tf 주석). CSP는 수집 지점이 없어 Report-Only로 두고, enforce 전환 후속은 수집 엔드포인트부터다.

#### 경보 5xx는 두 지표의 합이다 (KAN-134)

`ELB_5XX`만 보면 backend가 500을 쏟는 상황을 놓치고,
`Target_5XX`만 보면 backend가 아예 죽어 ALB가 502를 내는 상황을 놓친다. 합계
하나로 보면 경보 개수도 한 개로 유지되어 "이 티켓은 경보 2종만"이라는 KAN-38
경계가 그대로 선다. 앞의 둘(`no-healthy-target`의 전신, `alb-5xx`)이 KAN-134의 필수 2종이고 `rds-free-storage`는 "선택" 항목인데 두 환경 모두 넣기로 했다(2026-08-28).

#### backend 다운은 HealthyHostCount < 1로 본다 (KAN-165)

KAN-134의 `unhealthy-hosts`(`UnHealthyHostCount >= 1`)는 EC2 대상 전제였다. 컨테이너가 죽어도
인스턴스가 등록된 채 unhealthy로 남아 그 지표가 1이 됐다. Fargate 태스크는 죽는 순간
ECS가 대상 그룹에서 등록 해제해 `UnHealthyHostCount`가 0에 머물고, 헬스체크만 실패하는
태스크도 ECS가 곧 교체해 unhealthy 구간이 1분 안팎이라 "2회 연속"에 못 미친다. 사용자
관점의 장애는 "받아 줄 healthy 대상이 없다"이고 그것은 교체 중이든 등록 해제됐든
`HealthyHostCount` 0으로 나타난다. 오토스케일링(KAN-168) 뒤 "여럿 중 하나가 죽었다"는
부분 장애라 ECS 교체에 맡긴다.

#### 결측을 장애로 세는 경보와 정상으로 세는 경보 (KAN-134, KAN-36, KAN-201)

- `no-healthy-target`은 결측을 장애로 센다. 이 지표는 대상이 등록돼 있는 한 계속
  나오므로 끊겼다는 것은 등록된 대상이 하나도 없거나(서비스가 태스크를 못 띄움) ALB나
  대상 그룹이 사라졌다는 뜻이다. 대가로 스택을 새로 apply한 직후 첫 태스크가 뜨는 몇 분
  동안 한 번 울린다. 이때는 대상이 실제로 없어 경보가 맞고, 뜨고 나면 OK 알림이 따라온다.
  `ai-alb-no-healthy-host`(KAN-201)와 `ai-unhealthy`(KAN-36)도 같은 이유로 결측을 장애로 센다.
- 5xx는 결측을 정상으로 센다. 트래픽이 0인 시간대에는 지표가 나오지 않는다.
  서버가 죽은 것은 `no-healthy-target`이 트래픽과 무관하게 잡는다. 다만 이미 ALARM인
  상태에서 트래픽이 완전히 끊기면 해제되지 않고 ALARM에 머물 수 있다. 아무도 복구를 확인해 주지
  않은 상태에서 알아서 내려가지 않는 편이 안전하므로 그대로 둔다(2026-09-02 재실증에서는 트래픽 없이도 내려왔다, history 참고).

#### SNS 토픽에 SSE를 켜지 않는다 (KAN-134)

AWS 관리형 키 `alias/aws/sns`는 키 정책에
`cloudwatch.amazonaws.com`이 없어서, 켜면 경보 상태는 ALARM인데 메일만 조용히
안 오는 상태가 된다. 나르는 내용이 "어느 경보가 어느 상태로 바뀌었다"뿐이라
고객 관리형 키를 따로 만들 이유가 없다.

#### backend 서비스 경보 2종은 ec2-cpu-surplus 자리다 (KAN-165, 2026-09-02 사용자 결정)

그 경보는 t3.small의 초과 CPU 크레딧을 봤는데 backend EC2가 사라졌고,
ai EC2(c7i.xlarge)는 버스트 계열이 아니라 크레딧 지표 자체가 없어 옮길 곳이 없다.
대신 `AWS/ECS`의 서비스 단위 평균 둘을 본다 (Container Insights 없이 나온다).
CPU는 Fargate 0.5 vCPU에 버스트가 없다는 점 때문이다. t3.small은 순간 2 vCPU까지
끌어 썼지만 여기서는 항상 정확히 0.5라 평균이 지속적으로 높으면 요청이 느려지는
중이자 태스크 크기 상향 신호다 (오토스케일링 KAN-168은 CPU가 아니라 요청 수로
늘린다). 5분 연속을 요구해 기동 직후 JIT 스파이크와 스모크 한 바퀴로는 서지 않는다.
메모리는 힙이 태스크 2 GB의 75%라 그 위는 네이티브 영역까지 차오른 상태이고, 힙이 마르면
`ExitOnOutOfMemoryError`로 태스크가 죽어 `no-healthy-target`이 사후에 잡는다. 이 경보는
그 전에 "차오르고 있다"를 알린다. 둘 다 결측(태스크 0개)은 정상으로 센다. 그것은
`no-healthy-target` 몫이다.

#### ec2-cpu-surplus가 CPUSurplusCreditBalance를 봤던 이유 (KAN-134, KAN-165 전까지)

unlimited 인스턴스는 잔액 0으로 시작해 시간당
24개씩 쌓는다. 잔액 하한으로 경보를 걸면 새로 뜬 인스턴스가 임계값에 닿을 때까지 몇
시간 동안 무조건 운다 (2026-08-28 staging 실측). 초과 크레딧은 정상 부하에서 0 근처에
머물다가 기준선을 넘겨 쓴 만큼만 쌓여 기동 오탐이 없었다. 버스트 인스턴스를 다시 쓰게
되면 같은 선택이 유효하다.

#### AI 경보는 커스텀 지표다 (KAN-36, KAN-201에서 보강)

KAN-36 A단계 당시 AI 호스트는 ALB 뒤가 아니라 대상 그룹 health가 없었고, 회로 상태는 어떤 표준 지표에도 없어 둘 다 커스텀 지표로 만들었다. `Healthy`는 ai 호스트의 systemd 타이머가 1분마다 올리고 워밍업 중(503 STARTING)도 0이다. 회로 경보는 2(열림)에만 선다. 반열림은 "다음 업로드로 시험한다"는 대기라 트래픽이 없으면 밤새 1에 머물고, 1 이상으로 걸면 잠깐 죽었다 복구된 AI가 아침까지 ALARM으로 남는다. JVM이나 HTTP 지표는 올리지 않는다. 이름당 월 0.30달러다.

KAN-201에서 AI 호스트가 내부 ALB 뒤로 가면서 `ai-alb-no-healthy-host`(대상 그룹 `HealthyHostCount`)가 정본이 됐고 `ai-unhealthy`는 이중 안전망이 됐다. 같은 티켓에서 `ai-unhealthy` 통계를 Minimum에서 Maximum으로 바꿨다. 인스턴스가 여럿이면 같은 차원으로 섞여, Minimum이면 새로 뜬 인스턴스의 워밍업 0이 3분 연속 잡혀 확대할 때마다 울기 때문이다.

#### AI 호스트 자원 경보 2종 (KAN-36 B단계, 2026-09-10)

ai 이미지가 7GB라 reload마다 SHA 2개와 pull 임시 공간이 루트 볼륨에 함께 놓이고, 컨테이너 RSS 최대 6.2GB
(KAN-57 bf16)가 8GB 인스턴스에 올라간다. 디스크가 차면 pull이 실패해 배포가 롤백하고, 메모리가
차면 OOM으로 워커가 죽어 재적재 31초가 반복된다. 둘 다 health는 UP인 채라 `ai-unhealthy`가
못 잡는다. 지표는 health 타이머가 같은 경로로 올린다(CloudWatch agent 없음). 디스크 80%는
정상 reload의 순간 최대치(약 21GB = 40GiB의 51%)보다 위이고 pull 실패(100%)보다 아래다.
메모리 90%는 호스트 대비다. 컨테이너 상한 7GiB(호스트의 92%)보다 조금 아래라 컨테이너가
죽기 전에 먼저 운다. 값은 `(MemTotal - MemAvailable) / MemTotal`이라 page cache는 사용으로 세지
않는다. 결측은 둘 다 `notBreaching`이다. 호스트가 없는 것은 `ai-unhealthy`가 잡는다.

#### 수신 주소는 variables.tf 기본값 (KAN-134, 2026-08-28)

두 환경이 같은 값이라 `github_repositories`와 같은 이유로 tfvars가 아니라 기본값에 둔다.

#### 슬랙 알림 구조 (KAN-273)

이메일 토픽에는 Chatbot을 붙이지 않는다(모든 경보가 슬랙으로 간다). 슬랙으로는 `ai-disk-high`와 `result-wait-high`(KAN-284)만 보낸다. instance refresh가 내는 시작과 종료는 배포마다 두 줄씩 쌓여 알리지 않기로 했다(2026-10-13 결정). 확대와 축소 제목에 대수를 붙이려고 EventBridge 입력 변환기 대신 Lambda `scale_notify`를 쓴다. 이벤트에 변경 뒤 대수 필드가 없기 때문이다. Chatbot 채널 설정은 계정당 채널 하나에 하나라 prod가 두 설정을 모두 갖는다.

### 구축과 철거, 관측성, RDS와 Flyway

#### staging은 destroy 대신 수동 정지로 비용을 줄인다 (2026-10-14)

destroy는 RDS를 스냅샷 없이 지우고(staging `db_skip_final_snapshot = true`), 손으로 넣은 SSM 시크릿(카카오 Admin 키, 슬랙 웹훅, 애플 개인
키 등)도 재구축 때 자리 표시 값으로 돌아간다. 음성 버킷은 bootstrap 소유 + `prevent_destroy`라 어느 쪽이든 안전하다. 그래서 ECS와 ASG를
0으로, RDS를 정지, 경보 액션을 끄는 수동 정지를 택했고 예약 자동화는 두지 않기로 했다. 대가는 꺼진 동안의 Dev 병합이 verified를 받지 못해
prod로 못 나가는 것이다. 원본 README 구조도 122줄의 "staging은 미사용 시 destroy"는 이 결정 이전 서술이다.

#### staging을 먼저 짓고 prod를 짓는다 (KAN-140)

staging에서 모듈 결함을 다 털어내야 prod apply가 한 번에 선다. 두 환경은 같은 모듈이고 차이는 tfvars뿐이라 prod plan은 staging과의
diff가 tfvars 차이뿐인지 눈으로 확인한다.

#### IMAGE_TAG는 Terraform 밖에 두고 destroy 뒤에도 남긴다 (KAN-165)

이미지 태그의 정본은 파이프라인이 쓰는 SSM `/accentury/{env}/IMAGE_TAG`다. 재구축 plan이 이 값을 요구하므로 destroy가 지우지 않는 편이
맞고, 남겨 두면 다음 재구축 때 backend 태스크와 ai 호스트가 그 SHA로 뜬다.

#### 관측성 경보는 같은 SNS 토픽, 결측은 notBreaching (KAN-38)

심각도별 채널을 나누지 않는다. 3인 팀에 채널이 여럿이면 어느 쪽도 보지 않게 된다. 관측성 경보는 결측을 장애로 세지 않는다. backend가 죽어
지표가 끊기는 것은 `no-healthy-target`이, AI 호스트가 죽는 것은 `ai-unhealthy`가 이미 잡으므로 같은 사건에 메일 여러 통을 보내지 않는다.

#### 못 읽은 지표는 0으로 올리지 않는다 (KAN-38)

0은 "깨끗하다"라서 조회가 막힌 순간에 경보가 거꾸로 조용해진다. 호스트 타이머는 임시파일 지표 조회가 실패하면 그 회차를 건너뛰고,
`ai/app/tempstore.py`도 훑기 실패 시 잔존 값을 덮지 않는다.

#### 추론 단계 표본은 낱개로 올리고 재시도 큐를 두지 않는다 (KAN-204)

CloudWatch가 p50과 p95를 계산하려면 관측값이 낱개(`Values`)로 와야 한다. `/internal/v0/metrics/stages`는 읽으면 비워지고, 소비자는
호스트 타이머 하나다. `put-metric-data`가 실패하면 그 1분 표본은 버린다. 재시도 큐를 두면 CloudWatch가 오래 막혔을 때 대가를 추론
프로세스가 메모리로 치르기 때문이다. 앱은 계열당 150개에서 오래된 표본부터 버린다.

#### ai 컨테이너 로그는 awslogs non-blocking으로 보낸다 (KAN-203)

교체를 넘겨 원인을 남기려고 CloudWatch로 보낸다. `mode: non-blocking`은 버퍼(4MB)가 차면 새 줄을 버린다. 추론이 밀리는 것보다 로그 몇 줄을
잃는 것이 낫다는 판단이다. 그룹 이름은 backend와 접두사(`/accentury/{env}/`)를 맞춰 Logs Insights에서 한 번에 고르게 했다.

#### 지표 태그는 값이 다섯 이하로 닫힌 것만 쓴다 (KAN-38)

지표 수가 곧 요금이라 세션 ID나 IP를 태그로 쓰면 요금이 트래픽에 비례해 상한 없이 늘어난다. AI 단계 이름도 같은 이유로 닫힌 목록이다.

#### RDS 운영자 접속은 AI 호스트 경유 SSM 포트 포워딩 임시 통로로 한다 (KAN-220)

RDS SG는 backend-sg만 받고, ECS Exec은 켜지 않기로 했으며(KAN-165), bastion도 없다. 상시 통로를 두지 않고, 필요할 때만 RDS SG에 ai-sg 출처
5432를 손으로 열었다가 닫는다. AI 호스트는 SSM 관리 대상이고 egress 가드가 컨테이너에만 걸려 있어 호스트 자신은 RDS에 닿는다.

#### Flyway 재베이스라인은 이력 표를 지우지 않고 이름을 바꾼다 (KAN-220, 2026-09-21)

옛 V1~V12를 새 V1 하나로 합쳤다. 이력 표를 지우면 되돌릴 길이 스냅샷 복원(엔드포인트가 바뀌는 큰 작업)뿐이라, 이름을 바꿔 두고 실패하면
이름을 되돌리는 것을 1순위 복구 경로로 삼았다 (PR #121 리뷰 P1). 지우는 정의 행도 백업 표에 복사한다. `baseline-on-migrate`는 전환 중인
이미지에만 켜고 전환 뒤 제거한다.

### backend, ai, 개인정보처리방침, App Link

#### 스텁 대신 fake 엔진을 둔다 (KAN-22)
실모델은 개발 기계(arm64)와 CI 러너에서 돌릴 수 없다. 스텁을 지우자 앱과 웹의 완주 확인이 돌릴 스택을 잃었으므로(PR #87 리뷰) `fake` 엔진을 둔다. `ACCENTURY_AI_ANALYSIS_ENGINE=fake`로 명시해야 켜지고, 점수는 `correlationId`의 해시라 추론이 아니며(`modelVersion`이 `fake-0.1`), 배포 compose의 `ACCENTURY_AI_INTERNAL_TOKEN_REQUIRED=true`와 양립하지 않아 운영에서는 기동이 거부된다.

#### 방침 자리표시자를 사업자 확정 전에는 테스트로 막지 않았다 (KAN-196)
사업자 확정 전에는 자리표시자를 테스트로 막지 않았다. 막으면 확정 전 단계의 본문을 커밋할 수 없어 오히려 문서가 코드보다 뒤처진다. 확정 뒤로는 반대로 「확정 후 기재」가 되살아나는 쪽을 `privacy.test.mjs`가 막는다.

#### 단계 시간을 남기는 이유 (KAN-204)
GPU 전환(KAN-57)은 Whisper만 줄이고 MFA는 CPU 작업이라 그대로인데, 두 단계의 비율을 모르면 그 판단이 추정으로 남는다. 그래서 라우트가 수첩을 쥐고 어댑터가 채운다 (ai/README "추론 단계별 소요 시간"에 결론을 남겼다).

#### AI의 correlationId 멱등 캐시는 범위 밖 (2026-09-01 정정)
`correlationId` 기반 멱등 캐시는 AI의 범위 밖이다. BE가 일시 장애 뒤 같은 `correlationId`로 재전송하는 설계이고(KAN-24, KAN-28), AI는 무상태이며 오디오를 남기지 않으므로 자체 멱등 저장소를 두지 않는다 (KAN-297에서 ai README에서 옮김).

#### scriptKey가 없거나 서비스 문장이 아니면 비재전송 판정 실패 (KAN-182, 2026-09-05)
값이 없거나 서비스 문장이 아닌 `scriptKey`는 422 `ANALYSIS_MISREAD`, `retryable=false`로 끊는다. 무시하고 분석하면 대본 없이 채점한 점수가 정상값처럼 나가고, 재전송 가능으로 내면 정의가 바뀌지 않는 한 결과가 같은 요청을 BE가 예산이 마를 때까지 반복한다 (KAN-297에서 ai README에서 옮김).

#### V15는 gn-2026.10.2를 새 버전 없이 제자리 UPDATE (KAN-282, 2026-10-13)
테스트 정의는 발행 후 불변이라는 규칙(KAN-26, §5.4)의 유일한 예외다. V15는 세션, 채점, 세트 구성이 바뀌지 않는 화면용 필드(`emotion`) 하나만 더하는 일이라 새 testVersion 없이 gn-2026.10.2를 제자리에서 UPDATE하기로 했다. 활성 전환 없이 배포만으로 반영된다 (KAN-297에서 backend README에서 옮김).

#### 개인정보처리방침 고지는 동의 화면과 별개로 유지 (KAN-2, FR-DP-06)
KAN-2에서 "동의 화면은 범위 제외하되 이 고지는 별도 유지"로 남겨 둔 항목이 FR-DP-06 개인정보처리방침 URL이고, `infra/privacy/`가 그 문서가 사는 자리다 (KAN-297에서 infra/privacy README에서 옮김).

#### 개인정보처리방침은 `/privacy.html`, 확장자 없는 URL은 함수 예외로 (KAN-133, 2026-09-01)
확장자 없는 `/privacy`는 SPA 재작성 함수가 index.html로 돌리므로 `/privacy.html`을 쓴다. 확장자 없는 URL이 꼭 필요해지면 `/.well-known/`처럼 `spa-rewrite.js`에 예외 한 줄을 넣는 대안이 있다 (KAN-133 코멘트, 2026-09-01. KAN-297에서 infra/privacy README에서 옮김).
