# Accentury_Server

Accentury(경남 사투리 레벨 테스트)의 서버 쪽 레포다.

| 레포 | 내용 |
| --- | --- |
| Accentury_Server (여기) | `backend/` Spring Boot API, `ai/` FastAPI 채점 서버, `infra/` Terraform, `scripts/` 배포와 게시 스크립트, `tools/content/` 콘텐츠 발행 도구 |
| [Accentury_App](https://github.com/AISWM-2PL1/Accentury_App) | Android 앱, iOS 앱, 웹(Vite), 자산 |
| [Accentury_Prototype](https://github.com/AISWM-2PL1/Accentury_Prototype) | 분리 전 모노레포. 읽기 전용 아카이브. 커밋 메시지의 `#NNN`은 이 레포의 PR 번호다. |

브랜치 모델: `Dev` 병합이 staging 배포, `Release` 병합이 prod 배포다 (`.github/workflows/deploy.yml`).

## 시작

| 무엇 | 어디 |
| --- | --- |
| backend 로컬 실행과 테스트 | [`backend/README.md`](backend/README.md) |
| ai 서버 | [`ai/README.md`](ai/README.md) |
| 인프라와 배포, GitHub 설정 | [`infra/README.md`](infra/README.md) |
| 로컬 풀스택 (DB + Redis + 가짜 AI + BE, 가짜 IdP 켜짐) | 루트 `docker compose up -d --build`. 앱과 웹은 Accentury_App을 따로 받아 이 스택을 겨눈다 (Accentury_App `web/README.md` "스택 띄우기"). |

## 다른 레포와 맞물린 파일 (사람이 지키는 규칙)

레포를 나누면서 서로 읽던 파일은 복사본으로 풀었다. 원본을 바꾸면 복사본도 같이 고친다.

| 원본 | 복사본 | 언제 갱신하나 |
| --- | --- | --- |
| `backend/src/main/resources/db/migration/`의 정의 발행 마이그레이션. 새 정의는 새 V 파일로 발행한다 (예: `V5__gn_2026_10_1_seven_items.sql`, `V11__gn_2026_10_2_curated.sql`) | Accentury_App `fixtures/definitions/<버전>/<그 V 파일>` (Accentury_App `fixtures/README.md`) | 새 정의를 발행할 때. 그 V 파일을 복사하고 앱, iOS, 웹의 PublishedGuideF0 테스트 세 곳의 경로 상수를 새 버전으로 바꾼다. 같은 버전을 제자리에서 고친 경우(V15가 gn-2026.10.2를 UPDATE)에는 그 파일이 새 원본이다. |
| Accentury_App `app/src/main/AndroidManifest.xml`의 `android:path` | `infra/modules/edge/app-link-paths.json` | App Link 진입 경로를 바꿀 때 (`spa-rewrite.test.mjs`가 AASA와 대조한다) |
| Accentury_App `assets/share/<tier>.png` | `infra/share-assets/<tier>.png` | 등급 캐릭터 이미지를 다시 만들 때 (`scripts/publish-share-assets.sh`가 올린다) |

문서: [`docs/ops`](docs/ops)에는 지금 유효한 운영 절차가, [`docs/wiki`](docs/wiki)에는 서버 쪽 주제(관측성, 개인정보처리방침 근거, 이용 후기, 인프라 실증 기록과 설계 결정)가 있다. 나머지 위키는 Accentury_App에 있다.
