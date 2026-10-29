# GitHub 설정

배포 워크플로가 쓰는 GitHub environment, deployment branch policy, 승인 게이트, environment 변수, Actions SHA 고정과 Dependabot을 다룬다.

관련 코드:
- [.github/workflows/deploy.yml](../../.github/workflows/deploy.yml) (Image Deploy)
- [.github/workflows/e2e-smoke.yml](../../.github/workflows/e2e-smoke.yml)
- [.github/workflows/branch-name.yml](../../.github/workflows/branch-name.yml)
- [.github/dependabot.yml](../../.github/dependabot.yml)
- [infra/modules/deploy](../../infra/modules/deploy) (OIDC 배포 역할과 신뢰 정책)
- `infra/envs/*/variables.tf`의 `github_repositories`
- Accentury_App 레포의 `.github/workflows/web-deploy.yml` (웹 배포)

## environment와 배포 역할 (KAN-127)

배포 워크플로(이 레포의 `deploy.yml`, `e2e-smoke.yml`과 Accentury_App 레포의 `web-deploy.yml`)는 GitHub environment `staging`, `prod`를 지정해 실행되고, 그 environment의 변수에서 대상을 읽는다. 역할의 신뢰 정책이 `repo:AISWM-2PL1/<레포>:environment:{env}`로 묶여 있어 environment 이름은 Terraform의 `env`와 같아야 한다. 레포 분리(KAN-221) 뒤에는 배포 역할 하나를 Accentury_Server와 Accentury_App 두 레포가 함께 맡는다. 신뢰 정책의 sub 목록에 두 레포가 들어 있고(`envs/*/variables.tf`의 `github_repositories`), environment와 변수는 두 레포에 각각 있다.

역할의 신뢰 정책은 이름만 있는 구형식과 숫자 ID가 붙는 불변 형식(`repo:AISWM-2PL1@295795156/Accentury_Server@1380964825:environment:{env}`)을 레포마다 둘 다 허용한다. 두 레포는 불변 형식을 쓴다(2026-07-15 이후 생성 저장소 기본). 지금 ID는 Accentury_Server 1380964825, Accentury_App 1380964859다. 저장소를 옮기거나 다시 만들면 ID가 바뀌므로 `gh api repos/OWNER/REPO --jq '[.owner.id, .id]'`로 다시 받아 `envs/*/variables.tf`의 `github_repositories` 기본값을 맞춘다. 분리 전 레포 Accentury_Prototype(id 1308814203)은 아카이브라 목록에 없다.

## deployment branch policy

어느 브랜치가 어느 environment로 배포하는지는 deployment branch policy로 고정한다(staging = Dev, prod = Release). 이것이 없으면 아무 브랜치의 워크플로가 environment를 지정해 역할을 맡을 수 있다.

```
for repo in Accentury_Server Accentury_App; do
  for env in staging prod; do
    branch=$([ "$env" = prod ] && echo Release || echo Dev)
    gh api -X PUT "repos/AISWM-2PL1/$repo/environments/$env" --input - <<'JSON'
{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}
JSON
    gh api -X POST "repos/AISWM-2PL1/$repo/environments/$env/deployment-branch-policies" \
      -f name="$branch" -f type=branch
  done
done
```

## prod 승인 게이트 (KAN-128)

prod의 승인 게이트는 GitHub environment `prod`의 **required reviewers**다. Settings > Environments > prod > Deployment protection rules > Required reviewers에 팀원을 넣으면 Release 병합이 만든 실행이 승인 전까지 `deploy` job에서 멈춘다. environment 설정이라 레포마다 둔다(`gh api -X PUT repos/AISWM-2PL1/<repo>/environments/prod --input -`에 `reviewers` 배열). 이 레포에서는 Image Deploy가, Accentury_App에서는 웹 배포가 각자 레포의 prod environment 승인을 기다린다. 코드가 아니라 저장소 설정이므로 레포에는 남지 않는다. 새 저장소에서는 다시 켠다.

## environment 변수

이 레포의 워크플로가 읽는 변수는 아래 4개다. 앞의 셋은 환경 apply 출력에서 채운다(재구축으로 배포 ID나 역할 ARN이 바뀌면 다시 넣는다). `deploy.yml`은 반영 전에 `AWS_DEPLOY_ROLE_ARN`과 `APP_DOMAIN`이 비어 있으면 실패한다.

| 변수 | 출처 | 쓰는 곳 |
| --- | --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | `terraform output -raw github_deploy_role_arn` | `deploy.yml`, `e2e-smoke.yml`, App `web-deploy.yml` |
| `WEB_BUCKET` | `terraform output -raw web_bucket` | App `web-deploy.yml` |
| `CLOUDFRONT_DISTRIBUTION_ID` | `terraform output -raw cloudfront_distribution_id` | App `web-deploy.yml` |
| `APP_DOMAIN` | `terraform output -raw domain` | E2E 스모크 대상(KAN-128). `deploy.yml`, `e2e-smoke.yml` |

같은 값을 두 레포에 넣는다. `gh variable set`에 `-R AISWM-2PL1/Accentury_Server`와 `-R AISWM-2PL1/Accentury_App`을 각각 준다.

```
cd infra/envs/staging   # 또는 prod
env=$(basename "$PWD")   # envs/<env> 디렉터리 이름. 도메인으로 환경을 구분하지 않는다 (staging 이름은 랜덤, KAN-206)
gh variable set AWS_DEPLOY_ROLE_ARN        -e "$env" --body "$(terraform output -raw github_deploy_role_arn)"
gh variable set WEB_BUCKET                 -e "$env" --body "$(terraform output -raw web_bucket)"
gh variable set CLOUDFRONT_DISTRIBUTION_ID -e "$env" --body "$(terraform output -raw cloudfront_distribution_id)"
gh variable set APP_DOMAIN                 -e "$env" --body "$(terraform output -raw domain)"
```

`DEPLOY_PAUSED`는 두 레포 모두에 둔다. 환경을 철거해 둔 동안 `DEPLOY_PAUSED=true`를 넣으면 이 레포의 Image Deploy와 App의 웹 배포가 배포 단계를 건너뛰고, 재구축 뒤 지우면 다시 배포한다(teardown 절차).

웹 배포 전용 변수(`ADSENSE_*`, `GA4_*`, `STORE_LISTING_READY`)는 Accentury_App에만 있다. 아래 두 항목은 그 레포에 넣는 값이다.

### GA4 계측 변수 (KAN-33)

`GA4_MEASUREMENT_ID`는 apply 출력이 아니라 Firebase 콘솔의 웹 데이터 스트림에서 나온다(`G-`로 시작). environment마다 **다른 스트림**을 둔다. 같은 ID를 쓰면 staging 확인 트래픽이 prod 집계에 섞인다. 비워 두면 그 환경의 번들은 계측 없이 빌드되고 배포는 그대로 된다.

```
gh variable set GA4_MEASUREMENT_ID -R AISWM-2PL1/Accentury_App -e prod --body G-XXXXXXXXXX
```

### AdSense 광고 변수 (KAN-197)

`ADSENSE_CLIENT_ID`와 `ADSENSE_SLOT_ID`는 AdSense 콘솔에서 나온다. 앞쪽이 게시자 ID(`ca-pub-` + 숫자. 지금 발급되는 값은 16자리인데 App `web-deploy.yml`의 ads.txt 스텝은 자릿수를 고정하지 않고 `^ca-pub-[0-9]+$`로 숫자 여부만 본다, 리뷰 P1-4)이고, 뒤쪽이 광고 단위를 만들면 생기는 슬롯 ID다.

**prod에만 둔다.** GA4처럼 집계가 섞여서가 아니라 승인 대상이 prod 하나이기 때문이다. AdSense는 심사를 통과한 사이트에만 광고를 내려주므로 staging에 값을 넣어도 빈 슬롯만 선다. 등록 시점도 그래서 늦다. 사이트 승인이 나고 광고 단위를 만든 **뒤에** 둘을 함께 넣는다. 하나만 넣은 환경은 아예 안 넣은 환경과 똑같이 광고 없는 번들이 된다(App 레포 `web/src/ads/adsense.ts`의 `adSenseIdsFromEnv`).

```
gh variable set ADSENSE_CLIENT_ID -R AISWM-2PL1/Accentury_App -e prod --body ca-pub-XXXXXXXXXXXXXXXX
gh variable set ADSENSE_SLOT_ID   -R AISWM-2PL1/Accentury_App -e prod --body XXXXXXXXXX
```

`ads.txt`는 손으로 올리지 않는다. App `web-deploy.yml`이 배포할 때마다 `ADSENSE_CLIENT_ID`에서 `ca-`를 떼어 `dist/ads.txt`에 한 줄로 적고 버킷 루트에 올린다. 값이 없으면 파일을 만들지 않고, 모양이 `^ca-pub-[0-9]+$`가 아니면 그 단계에서 실패한다. 게시 확인은 `curl https://accentury.app/ads.txt`이고 절차 전체는 App 레포 `docs/wiki/ads-web-adsense.md` §6, §10에 있다.

## Actions SHA 고정, 베이스 이미지 digest 고정, Dependabot (KAN-246)

워크플로의 `uses:`는 전부 `<40자 commit SHA> # v버전` 형식이고, Dockerfile의 베이스 이미지는 `태그@sha256:<digest>` 형식이다. 태그는 주인이 다른 커밋으로 옮길 수 있어서, 태그로 두면 남의 저장소가 뚫렸을 때 바뀐 코드가 우리 배포 역할(`id-token: write`)을 쥔 채 돈다(2025년 tj-actions 사례). SHA와 digest는 옮길 수 없다.

| 대상 | 고정 방식 | 갱신 |
| --- | --- | --- |
| `.github/workflows/*.yml`의 `uses:` 7종 | commit SHA, 뒤 주석에 버전 | Dependabot `github-actions` |
| `backend/Dockerfile`(`eclipse-temurin:25-jdk`, `25-jre`), `ai/Dockerfile.fake`(`python:3.12-slim`) | 멀티 아키텍처 인덱스 digest. 로컬(arm64)과 CI(amd64)가 같은 줄로 빌드한다. | Dependabot `docker`(`/backend`, `/ai`) |
| `ai/Dockerfile`의 베이스(`accentury/ai-model`) | ECR 불변 태그(모델 해시) | 대상 아님. [deploy.md "모델 교체"](deploy.md#모델-교체-kan-22) |
| `ai/requirements.txt` | 버전 범위 | Dependabot `pip`(`/ai`, `increase-if-necessary`). 범위가 새 버전을 막게 됐을 때만 PR을 올린다. |
| `ai/requirements.lock` | 버전과 해시 | Dependabot이 알아보는 이름이 아니다. 사람이 다시 만든다(`ai/README.md` "의존성 잠금"). |

고정한 값은 저절로 움직이지 않으므로 Dependabot(`.github/dependabot.yml`)이 **월 1회, 생태계마다 PR 하나**로 갱신을 올린다(생태계 3종: `github-actions`, `docker`, `pip`). 그 PR의 브랜치명은 `dependabot/...`으로 고정이라, 브랜치 네이밍 검사(`branch-name.yml`)는 PR을 연 계정이 `dependabot[bot]`일 때만 그 접두사를 통과시킨다. 병합하면 여느 Dev 병합처럼 staging 파이프라인이 돈다.

워크플로에 새 action을 더하거나 손으로 올릴 때는 태그가 가리키는 commit SHA를 받아 적는다.

```
gh api repos/actions/checkout/commits/v4 --jq .sha            # uses: actions/checkout@<SHA> # v4.x.y
docker buildx imagetools inspect python:3.12-slim --format '{{json .Manifest.Digest}}'   # FROM python:3.12-slim@<digest>
```
