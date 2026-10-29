# 배포

웹 버킷에 손으로 올리는 정적 자산(App Link 검증 파일, 등급 공유 이미지)과, BE와 AI 이미지를 staging에서 prod로 승격하는 이미지 배포 파이프라인, 실모델 베이스 이미지 push를 다룬다.

관련 코드:
- [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) (Image Deploy)
- [scripts/push-images.sh](../../scripts/push-images.sh), [scripts/e2e_smoke.py](../../scripts/e2e_smoke.py)
- [scripts/publish-well-known.sh](../../scripts/publish-well-known.sh), [scripts/publish-share-assets.sh](../../scripts/publish-share-assets.sh)
- [infra/well-known](../../infra/well-known), [infra/share-assets](../../infra/share-assets)
- [infra/modules/deploy](../../infra/modules/deploy) (`image-deploy` 정책)
- [infra/bootstrap/ecr.tf](../../infra/bootstrap/ecr.tf)
- [ai/Dockerfile](../../ai/Dockerfile) (`ARG MODEL_TAG`)

GitHub environment와 변수는 [github.md](github.md), backend 서비스 쪽 동작은 [backend.md](backend.md)에 있다. 실증 기록은 docs/wiki/infra-history.md의 KAN-132, KAN-165, KAN-173 항목이다.

## 정적 자산 게시

두 자산 모두 Terraform이 만드는 것이 아니라 웹 S3 버킷 안의 객체다. apply로는 생기지 않고, apply를 다시 해도 지워지지 않는다. 웹 배포(Accentury_App 레포의 `web-deploy.yml`)의 `aws s3 sync`에 `--delete`가 없고 배포 역할에 `s3:DeleteObject`가 아예 없어서(`modules/deploy`), 스크립트로 올린 객체는 이후 웹 배포에 살아남는다. 그래서 자산 교체가 웹 재배포를 요구하지 않고, 웹 배포도 자산을 다시 올리지 않는다.

두 스크립트가 쓰는 권한은 대상 버킷의 `s3:PutObject`와 배포의 `cloudfront:CreateInvalidation`, `cloudfront:GetInvalidation`이다(무효화 완료를 기다리기 때문이다). 배포 역할이 셋 다 갖고 있다.

### App Link 검증 파일 (KAN-32)

공유 링크 `https://accentury.app/t?c=...`를 브라우저 대신 앱이 받게 하려면 도메인이 앱을 인정한다고 공개 선언해야 한다. 그 선언 파일 두 개(`apple-app-site-association`, `assetlinks.json`)가 웹 버킷의 `/.well-known/` 아래 산다.

```
scripts/publish-well-known.sh staging      # 또는 prod
```

정본은 `infra/well-known/<env>/.well-known/`이고, 값의 근거, 게시 뒤 확인 명령, Play App Signing 함정은 전부 [infra/well-known/README.md](../../infra/well-known/README.md)에 있다. 여기서는 위 공통 사실에 더해 하나만 적는다.

- CloudFront의 SPA 재작성 Function이 `/.well-known/`을 예외로 빼 준다는 전제 위에 선다(docs/wiki/infra-decisions.md의 SPA 재작성 Function 항목). 예외가 빠지면 확장자 없는 `apple-app-site-association`이 index.html로 치환돼 iOS 검증만 조용히 죽는다.

### 등급 공유 이미지 (KAN-132)

`/result`의 `share.imageUrl`(§3.7)은 등급마다 정적 PNG 한 장이다. 결과 화면과 카카오 공유 카드가 같은 파일을 쓴다(KAN-162 자산). 원본은 Accentury_App 레포의 `assets/share/<code>.png`이고, 이 레포의 `infra/share-assets/<code>.png` 5장이 게시용 복사본이다.

값 5개를 설정에 적지 않고 **기준 URL 하나**에 등급 code를 붙인다. SSM `ACCENTURY_RESULT_ASSETBASEURL` = `https://<도메인>/share`(config 모듈이 도메인으로 조립)이고, backend `TierAssets`가 `<기준>/<code 소문자>.png`를 만든다. 환경마다 다른 것은 도메인뿐이고, 파일명은 자산 정본이 등급 code로 고정해 두었기 때문이다.

이미지는 웹 S3 버킷의 `share/` 아래에 산다. CloudFront 기본 동작(S3 오리진, 캐싱 최적화)이 서빙하고, 파일명에 확장자가 있어 SPA 재작성 Function에는 걸리지 않는다.

```
scripts/publish-share-assets.sh staging     # 또는 prod
```

5장을 `image/png`, `Cache-Control: public, max-age=86400`으로 올리고 `/share/*`를 무효화한 뒤 도메인으로 200과 내용 일치를 확인한다. 몇 번을 돌려도 같은 결과다(멱등). 캐릭터가 바뀌면 Accentury_App 레포의 `assets/characters/build.py`로 다시 만들어 `infra/share-assets/`의 복사본을 바꾸고 이 스크립트만 돌린다. **서버와 앱 배포는 없다**(AC: 이미지 교체는 S3 업로드만으로 반영). 대상 버킷과 배포 ID는 terraform output에서 읽고, 없으면 `WEB_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID` 환경 변수로 준다. E2E 스모크(KAN-138)가 완주 뒤 `share.imageUrl`에 HEAD를 보내 200 `image/png`를 확인하므로, 이미지를 안 올린 환경은 스모크에서 드러난다.

카카오 카드가 실제로 그려지려면 이미지 도메인이 **카카오 개발자 콘솔의 플랫폼 도메인에 등록**돼 있어야 한다(Accentury_App 레포 `assets/share/README.md`). AWS 밖 설정이라 Terraform도 스크립트도 확인하지 않는다.

### 필수 SSM 파라미터를 더할 때의 순서

**apply가 먼저, 그 파라미터를 읽는 코드를 실은 이미지 배포는 그다음이다.** 이미지 배포 파이프라인은 현재 태스크 정의를 복사해 image만 바꾸므로, apply로 태스크 정의에 새 secret이 들어가기 전에 그 코드가 배포되면 `DeploymentConfigGuard`가 누락으로 기동을 세우고 파이프라인이 자동 롤백한다(KAN-132 Codex 리뷰 P1, `ACCENTURY_RESULT_ASSETBASEURL`이 첫 사례). 필수 SSM 파라미터를 더하는 변경은 전부 같은 순서다.

## 이미지 배포 파이프라인과 롤백 (KAN-128)

`.github/workflows/deploy.yml`(Actions 이름 "Image Deploy")이 한다. 승격 모델이라 이미지는 Dev 푸시에서 한 번만 만들고, prod는 그 SHA를 고르기만 한다.

| 트리거 | 하는 일 |
| --- | --- |
| Dev 푸시 (`backend/**`, `ai/**`, `scripts/push-images.sh`, `scripts/e2e_smoke.py`, `.github/workflows/deploy.yml`) | `scripts/push-images.sh`로 두 이미지를 commit SHA 7자리 태그로 ECR에 push(이미 있으면 건너뜀) → staging 반영 → E2E 스모크 → `verified-<sha>` 표시 |
| Release 푸시 (같은 paths) | 빌드 없음. merge commit의 2번째 부모(Dev 끝)부터 거슬러 **처음 만나는 빌드된 SHA**가 후보다. 그 SHA에 `verified-<sha>` 태그(staging 반영과 스모크 통과 표시)가 없으면 더 오래된 것으로 건너뛰지 않고 실패한다(서버 변경이 조용히 빠지는 것을 막는다). environment `prod`의 required reviewers 승인 대기 → prod 반영 → E2E 스모크(KAN-217) |
| 수동 실행 | 환경과 `image_tag` 선택. 비우면 staging은 현재 커밋 빌드, prod는 위 승격 규칙. **롤백 = 이전 SHA를 `image_tag`에 넣는 것**(같은 절차, 재빌드 없음) |

### 반영 순서

반영 한 번은 SSM `/accentury/{env}/IMAGE_TAG`를 새 SHA로 바꾸고(직전 값과 backend 서비스의 현재 태스크 정의를 기억), 두 갈래로 간다(KAN-165). **ai를 먼저, backend를 다음에 올린다.** backend가 새 AI 계약을 전제로 할 수 있어서다(새 AI는 옛 backend 호출을 받아야 한다).

1. ai 호스트: ASG `accentury-{env}-ai`의 InService 인스턴스 ID를 조회해 한 대씩 순차로 reload한다(KAN-201, [ai-host.md "내부 ALB와 오토스케일링"](ai-host.md#내부-alb와-오토스케일링-kan-201)). 시작 전에 ASG 프로세스 `HealthCheck`, `ReplaceUnhealthy`, `AlarmNotification`을 멈추고 끝나면 어느 경로로든 되살린다. 대당 순서는 아래와 같다.
   - SSM 에이전트 등록 대기(최대 130초). 새 인스턴스는 등록에 실측 1.5분에서 2분이 걸린다.
   - SSM Run Command(`AWS-RunShellScript`, 인스턴스 ID 대상, 원격 실행 상한 1500초)로 `systemctl reload accentury`(첫 기동이면 `start`)를 부르고, 원격에서 compose.env의 IMAGE_TAG를 대조한 뒤 ai 컨테이너의 docker healthcheck가 healthy가 될 때까지 최대 600초 기다린다. 파이프라인은 그 명령의 완료를 최대 1560초 기다린다.
   - 그 인스턴스가 ALB 대상 그룹에서 healthy가 될 때까지 최대 600초.
   - 다음 인스턴스.

   InService 인스턴스가 없으면(ASG 교체 중) 실패로 끝나므로 교체가 끝난 뒤 다시 실행한다. job이 취소나 timeout으로 reload 도중 죽으면 정리 스텝(`always()`)이 멈춘 ASG 프로세스를 되살린다(KAN-212). 러너 자체가 사라져 정리 스텝도 못 돌면 [ai-host.md "배포 시 순차 reload"](ai-host.md#배포-시-순차-reload)의 수동 resume 절차를 따른다.
2. backend 서비스: 서비스가 도는 태스크 정의를 `describe-task-definition`으로 읽어 image 태그만 바꾼 리비전을 `register-task-definition`하고 `update-service`로 롤링 배포한다(새 태스크가 healthy가 된 뒤 옛 태스크를 뺀다, 무중단). 완료 판정은 docker healthcheck가 아니라 배포의 `rolloutState`가 COMPLETED인 것이고(ECS가 컨테이너 healthcheck와 ALB 대상 health를 본다) 최대 20분 기다린다. 같은 태그 재실행이면 리비전을 쌓지 않고 `--force-new-deployment`만 한다. cpu, secrets, stopTimeout 같은 형태는 Terraform(`modules/fargate`)이 정하므로 파이프라인은 image 외에 아무것도 바꾸지 않는다. 등록 전에, 서비스가 도는 리비전보다 새 ACTIVE 리비전 중 이 배포 역할이 등록한 것(지난 실행이 실패 리비전을 못 지운 채 죽은 잔재)을 찾아 deregister한다. Terraform이 등록한 리비전은 건드리지 않는다.

컨테이너가 healthy가 된 뒤에는 공개 경로(`https://도메인/v0/...`)가 게이트웨이 오류(502, 503, 504) 대신 백엔드 응답을 돌려줄 때까지 최대 6분 더 기다린다. ALB 대상 그룹 헬스체크가 healthy로 바뀌는 데 수십 초가 걸리고, 그동안 CloudFront 경유 요청은 502다.

### 실패 시 롤백

어느 쪽이든 실패하면 **역순(backend, ai)으로 되돌린다.** 같은 순서로 되돌리면 "옛 AI + 새 backend" 창이 생기는데 그 조합은 아무도 보장하지 않는다.

- backend는 ECS 회로 차단기(연속 실패 3회)가 이미 직전 배포로 되돌렸으면 그 배포의 완료만 확인하고, 시간 초과처럼 ECS가 되돌리지 않은 경우에는 직전 태스크 정의로 `update-service`한다.
- 실패한 리비전은 `deregister-task-definition`한다. Terraform이 패밀리의 최신 ACTIVE 리비전을 읽으므로(`track_latest`, [backend.md](backend.md)) 남기면 `terraform plan`에 drift로 나온다.
- 그다음 IMAGE_TAG를 직전 값으로 되돌려 ai 호스트를 reload한다.

그래서 "반영 실패 시 기존 이미지 유지"가 성립한다. 직전 값이 없는 첫 배포가 실패하면 backend는 ECS가 직전 배포를 유지하고 ai 호스트만 실패 상태로 남으므로, 유효한 태그를 수동 실행으로 넣는다. 원격 명령이 1560초 안에 끝나지 않아 원격 상태를 모르면 자동 롤백하지 않고 실패로 끝난다.

### 어디에 어떤 SHA가 떠 있는지

세 곳에 남는다. 실행 요약 표(환경, 반영 태그, 출처, 직전 태그, backend 태스크 정의 리비전), GitHub environment의 deployments 목록, 그리고 SSM `IMAGE_TAG` 자체다. backend는 `aws ecs describe-services --cluster accentury-{env} --services backend --query 'services[0].taskDefinition'`의 리비전이 가리키는 image, ai 호스트는 `grep IMAGE_TAG /run/accentury/compose.env`로 확인한다.

### verified 표시와 스모크

staging 반영과 스모크가 모두 통과하면 같은 이미지에 ECR 태그 `verified-<sha>`를 하나 더 붙인다(IMMUTABLE은 기존 태그 덮어쓰기만 막는다). prod는 수동 입력이든 자동 승격이든 이 표시가 있는 SHA만 받으므로, 빌드는 됐지만 staging에서 실패한 이미지는 prod에 갈 수 없다. 표시는 prod 역할의 ECR 조회 권한만으로 확인되므로 환경 간 SSM 교차 읽기가 없다.

테스트는 파이프라인에서 다시 돌리지 않는다. PR의 `backend-test`, `ai-test` required check가 게이트다. E2E 스모크는 두 환경 모두 반영 직후 같은 job에서 `scripts/e2e_smoke.py`를 직접 부르고, 관리자 토큰은 그 환경 SSM에서 읽어 합성 트래픽으로 표시한다. `e2e-smoke.yml`의 `workflow_call`을 쓰지 않는 이유는 토큰을 job output으로 넘기면 GitHub이 마스킹된 값이라며 output을 버리기 때문이다. `e2e-smoke.yml`의 `workflow_dispatch`는 승격과 무관한 수동 재실행용으로 남아 있다.

**스모크 실패는 실행을 실패로 만들지만 반영을 되돌리지는 않는다**(두 환경 같음). 스모크가 보는 것은 이미지가 아니라 전 구간이라, 원인이 이미지가 아닐 수 있다. 되돌리려면 수동 실행에 직전 SHA를 넣는다. staging에서는 스모크까지 통과해야 `verified-<sha>`가 붙고, prod에서는 붙일 표시가 없어 그 스텝이 skipped다.

### 권한

`infra/modules/deploy`의 `image-deploy` 정책이다.

- staging 역할만 ECR push를 갖고(`ci_image_push`, 환경 간 tfvars 차이) prod 역할은 조회뿐이다.
- Run Command는 자기 환경 이름이 붙은 ai 호스트(`accentury-{env}-ai`)에만 보낼 수 있다. ASG 프로세스 정지와 재개도 이 환경 ai ASG 하나로 좁혀져 있다.
- ECS는 이 환경의 backend 서비스 ARN에만 `UpdateService`, `DescribeServices`를 할 수 있고, `ListTasks`, `DescribeTasks`는 이 환경 클러스터 조건으로 좁힌다.
- `RegisterTaskDefinition`은 이 환경 태스크 정의 패밀리 ARN(`accentury-{env}-backend`와 그 리비전)으로 좁힌다. `DescribeTaskDefinition`, `DeregisterTaskDefinition`, `ListTaskDefinitions`는 AWS가 리소스 수준 권한을 지원하지 않아 `*`다. 그래서 staging 역할이 prod 패밀리의 리비전을 deregister할 수 있는 표면이 남는데(등록은 못 한다), 삭제가 아니라 INACTIVE 전환이고 도는 서비스에는 영향이 없어 수용한다.
- 리비전 등록에 필요한 `iam:PassRole`은 이 환경의 실행 역할과 태스크 역할 둘에 `iam:PassedToService = ecs-tasks.amazonaws.com` 조건으로 한정한다.

## 실모델 베이스 이미지 push (KAN-173)

`ai/` 컨테이너는 모델 담당이 만든 이미지 `accentury-track1`(KAN-159, MFA + Whisper large-v3 + Praat)을 `ai/Dockerfile`의 `FROM` 베이스로 쓴다(KAN-22). 그 이미지는 ECR `accentury/ai-model`에 두고, CI가 아니라 **모델 담당이 직접 push한다**. 배포용 리포지토리 2개(`accentury/backend`, `accentury/ai`)는 `scripts/push-images.sh`가 commit SHA 태그로 자동 push하는 곳이라 **손으로 올린 이미지를 섞지 않는다.**

- 권한: IAM 사용자 `jaeyoung`(AdministratorAccess). 콘솔에서 발급하고 Terraform 밖에 둔다(다른 팀원 IAM 사용자와 같다). 리포지토리 한정 정책은 만들지 않는다(2026-08-31 결정, KAN-173).
- 태그: 모델 해시(예: `3af5ff6`). 날짜 태그를 쓰지 않는다. IMMUTABLE이라 같은 태그는 다시 올릴 수 없고, 모델이 바뀌면 새 해시 태그로 올린다.
- 플랫폼: linux/amd64. 맥에서 빌드하면 `--platform linux/amd64`를 붙인다. arm64로 올린 태그는 IMMUTABLE이라 되돌릴 수 없다.

모델 담당 PC에서(`aws configure`로 `jaeyoung` 액세스 키를 넣은 뒤) 아래처럼 올린다.

```
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.ap-northeast-2.amazonaws.com"
LOCAL_IMAGE=accentury-track1:latest   # 로컬에서 빌드한 이미지 이름:태그
MODEL_HASH=3af5ff6                    # 모델 해시 = ECR 태그

aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin "$REGISTRY"
docker tag "$LOCAL_IMAGE" "${REGISTRY}/accentury/ai-model:${MODEL_HASH}"
docker push "${REGISTRY}/accentury/ai-model:${MODEL_HASH}"
```

push한 태그를 KAN-173 코멘트로 남기면 인프라 담당이 받아서 확인한다.

```
docker pull "${REGISTRY}/accentury/ai-model:${MODEL_HASH}"
docker image inspect "${REGISTRY}/accentury/ai-model:${MODEL_HASH}" \
  --format '{{.Os}}/{{.Architecture}} {{.Size}}'
```

`linux/amd64`가 아니면 그 태그는 쓰지 않고 새 해시로 다시 올린다.

### 모델 교체 (KAN-22)

배포에 쓰는 태그는 **`ai/Dockerfile`의 `ARG MODEL_TAG` 한 곳**에 있다. 모델이 바뀌면 그 줄을 새 해시로 바꾸는 것이 곧 모델 교체이고, 그 커밋의 SHA로 빌드된 `accentury/ai`가 새 모델을 품는다. 어느 배포가 어느 모델을 돌렸는지가 두 태그의 짝으로 남는다. 레지스트리 주소만 `scripts/push-images.sh`가 계정에서 유도해 `--build-arg MODEL_REGISTRY`로 넘긴다.

빌드 머신은 ECR에 로그인해 있어야 한다. 베이스가 사설 레지스트리에 있으므로 로그인 없이는 push가 아니라 빌드가 먼저 실패한다. 스크립트는 `DRY_RUN=1`에서도 로그인한다.

### 이미지 크기와 ECR 상한

실모델 `accentury/ai` 이미지는 약 7GB다(2026-09-10 staging 실측 7.02GB, 모델 베이스 4.12GB 위. `infra/envs/*/terraform.tfvars`의 루트 볼륨 주석). 같은 리포지토리 안에서 베이스 레이어는 한 번만 저장되므로 SHA 태그마다 늘어나는 것은 앱 레이어뿐이지만, 라이프사이클 상한 50개는 수백 MB짜리 backend 이미지 기준으로 정한 값이다. 태그가 몇 개 쌓인 뒤 ECR 콘솔의 리포지토리 크기로 실측하고, 필요하면 `infra/bootstrap/ecr.tf`에서 `accentury/ai`만 낮춘다(KAN-173 후속).
