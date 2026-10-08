# prod 환경 값. 환경 간 차이는 이 파일의 diff로 전부 열거된다 (KAN-140 AC).
env    = "prod"
region = "ap-northeast-2"

domain                 = "accentury.app"
hosted_zone_name       = "accentury.app"
acm_certificate_domain = "accentury.app"

vpc_cidr             = "10.0.0.0/16"
azs                  = ["ap-northeast-2a", "ap-northeast-2c"]
public_subnet_cidrs  = ["10.0.0.0/24", "10.0.1.0/24"]
private_subnet_cidrs = ["10.0.10.0/24", "10.0.11.0/24"]

# backend는 EC2가 아니라 Fargate 서비스다 (KAN-165). 태스크 크기(0.5 vCPU / 2 GB)는 두 환경이 같아 모듈 기본값이다.
db_instance_class = "db.t4g.micro"

# AI 추론 호스트 (KAN-36). A단계 스텁 모드부터 c7i.xlarge (2026-09-01 결정). B단계(실모델, 2026-09-10)에서도
# 유지한다 - KAN-57 채택안(Whisper bf16 + MFA align_one)의 1건 P95가 11.1초로 판정 게이트의 6초를 넘지만,
# GPU(g4dn.xlarge)가 줄이는 것은 Whisper 6초뿐이고 MFA 3.9초는 CPU 작업이라 어느 인스턴스로도 3초(NFR-PF-01)에
# 못 닿는다. 상향(c7i.2xlarge 또는 GPU)은 NFR 완화 논의와 KAN-204 stageMs(Whisper 대 MFA 비율)를 본 뒤 정한다.
# RSS 최대 6.19GB는 8GB의 77%로 KAN-57 메모리 축(75%)을 살짝 넘지만 동시 처리가 1건 고정이라 그대로 둔다.
ai_instance_type = "c7i.xlarge"
# AI 호스트 AMI (KAN-246). AL2023 x86_64이고, 아래 값은 al2023-ami-2023.12.20260930.0-kernel-6.18-x86_64다.
# 월 1회 staging, prod 순으로 최신 값으로 바꾼다 - 바꾸면 인스턴스가 무중단으로 교체된다 (README "AI 호스트 AMI 갱신").
ai_ami_id = "ami-0870825cefcaafcc8"
# 루트 볼륨 40GiB (B단계). 실모델 ai 이미지는 7.02GB(2026-09-10 staging 실측, 모델 베이스 4.12GB 위)이고 reload 중
# 옛 SHA와 새 SHA가 공존하는 데다 pull이 압축 레이어를 임시로 한 벌 더 풀어 순간 최대치가 약 2.5(OS와 docker) +
# 7 x 2 + 4 = 21GB다. 20GB에서는 pull이 디스크 부족으로 실패할 수 있어(실측 여유 11.8GB) 두 배로 올린다.
ai_root_volume_size = 40
# AI 호스트 오토스케일링 상한 (KAN-201, 2026-09-11 결정). 평시 1대, 진행 중 분석 6건 이상인 분이 1번 나오면 +1(KAN-272에서 2분 연속에서 줄였다), 1건 이하
# 15분 연속이면 -1. 내부 ALB(least_outstanding_requests)가 backend 태스크 3개의 동시 호출을 빈 인스턴스로 나눈다.
ai_max_size = 3

# 음성 저장 (KAN-201, KAN-269). 음성 저장에 동의한 세션의 음성 WAV와 AI 원점수를 모델 재학습용으로 음성 전용 버킷
# (accentury-voice-<계정 ID>, bootstrap이 만든다)의 prod/ 접두사 아래에 남긴다. 두 환경 모두 true다
# (2026-10-04 결정). 끄면 파라미터와 권한만 사라지고 버킷과 모인 음성은 그대로다.
training_bucket_enabled = true

# 사투리 텍스트 번역 기록 (KAN-266, 2026-10-08 결정). 번역 요청마다 입력, 출력, 결과 종류, 처리 시간을 번역 기록 버킷
# (accentury-translator-prompt-<계정 ID>)에 남긴다. prod만 true다.
translation_records_enabled = true

ssm_prefix = "/accentury/prod"

# prod 역할은 ECR push 불가 (KAN-128 승격 모델). staging이 검증한 SHA만 반영한다.
ci_image_push = false

# prod는 실수로 지워지면 안 된다. destroy하려면 먼저 이 값을 false로 apply한다.
db_deletion_protection = true
db_skip_final_snapshot = false

# WAF (KAN-149). staging의 Count 관찰 결과(오탐 0건)와 Block 전환 실증이 KAN-169에 기록됐으므로
# prod도 처음부터 Block이다. 두 환경 같은 값이다 - staging에서 본 결과가 prod에 그대로 적용되어야
# 관찰의 의미가 있다 (2026-08-28 확정).
waf_enforce    = true
waf_rate_limit = 300

# 로그인과 refresh, 관리자 경로 rate 규칙 (KAN-244, 2026-10-02 확정). waf_enforce를 따른다. 근거는 README "WAF 웹 ACL".
waf_auth_rate_limit  = 100
waf_admin_rate_limit = 50

# IP 평판 관리형 규칙 (KAN-244). 두 환경 Count(false)로 시작해 staging에서 일주일 관찰한 뒤 staging부터 true,
# prod가 뒤따른다 (2026-10-02 결정).
waf_ip_reputation_enforce = false

# 네이버 로그인 Client ID (KAN-243). 시크릿이 아니고 앱의 NAVER_CLIENT_ID와 같은 값이다. 두 환경이 같은 네이버
# 앱을 쓴다. 짝인 Client Secret은 시크릿이라 여기 두지 않고 apply 뒤 put-parameter로 넣는다 (README "소셜 로그인" 절).
auth_naver_client_id = "2nlTdPK4RqeqQcLtb6xH"

# 애플 (KAN-241). 셋 다 시크릿이 아니다. 번들 ID는 identityToken의 aud 검증(애플 로그인)과 탈퇴 revoke의 client_id,
# 팀 ID와 키 ID는 탈퇴 revoke의 client_secret JWT(iss, kid)다. 팀 ID는 apple-app-site-association의 appID 앞부분과 같다.
# 짝인 Sign in with Apple 키(.p8)는 시크릿이라 여기 두지 않고 apply 뒤 put-parameter로 넣는다 (README "소셜 로그인" 절).
auth_apple_bundle_id = "com.accentury.app"
auth_apple_team_id   = "559P9SYY57"
auth_apple_key_id    = "PR2QSQK4JW"
