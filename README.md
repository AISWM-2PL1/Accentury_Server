# Accentury_Server

Accentury(경남 사투리 레벨 테스트)의 서버 쪽 레포다. 2026-09-22에 모노레포 `Accentury`를 둘로 나눴다 (KAN-221).

| 레포 | 내용 |
| --- | --- |
| Accentury_Server (여기) | `backend/` Spring Boot API, `ai/` FastAPI 채점 서버, `infra/` Terraform, `scripts/` 배포와 게시 스크립트, `tools/` 콘텐츠 발행 도구 |
| [Accentury_App](https://github.com/AISWM-2PL1/Accentury_App) | Android 앱, iOS 앱, 웹(Vite), 자산 |
| [Accentury_Prototype](https://github.com/AISWM-2PL1/Accentury_Prototype) | 분리 전 모노레포. 읽기 전용 아카이브. 커밋 메시지의 `#NNN`은 이 레포의 PR 번호다 |

브랜치 모델은 그대로다: `Dev` 병합이 staging 배포, `Release` 병합이 prod 배포 (`.github/workflows/deploy.yml`).

## 시작

| 무엇 | 어디 |
| --- | --- |
| backend 로컬 실행과 테스트 | [`backend/README.md`](backend/README.md) |
| ai 서버 | [`ai/README.md`](ai/README.md) |
| 인프라와 배포, GitHub 설정 | [`infra/README.md`](infra/README.md) |
| 로컬 풀스택 (DB + 가짜 AI + BE) | 루트 `docker compose up -d --build`. 앱과 웹은 Accentury_App을 따로 받아 이 스택을 겨눈다 (`web/README.md` "스택 띄우기") |

## 다른 레포와 맞물린 파일 (사람이 지키는 규칙)

레포를 나누면서 서로 읽던 파일은 복사본으로 풀었다. 원본을 바꾸면 복사본도 같이 고친다.

| 원본 | 복사본 | 언제 갱신하나 |
| --- | --- | --- |
| `backend/src/main/resources/db/migration/V1__baseline.sql` (정의 발행본) | Accentury_App `fixtures/definitions/<버전>/V1__baseline.sql` | 새 정의를 발행할 때 (앱, iOS, 웹의 PublishedGuideF0 테스트가 읽는다) |
| Accentury_App `app/src/main/AndroidManifest.xml`의 `android:path` | `infra/modules/edge/app-link-paths.json` | App Link 진입 경로를 바꿀 때 (`spa-rewrite.test.mjs`가 AASA와 대조한다) |
| Accentury_App `assets/share/<tier>.png` | `infra/share-assets/<tier>.png` | 등급 캐릭터 이미지를 다시 만들 때 (`scripts/publish-share-assets.sh`가 올린다) |

문서: [`docs/wiki`](docs/wiki)에는 서버 쪽 주제(관측성, 개인정보처리방침 근거, 이용 후기)만 있다. 나머지 위키는 Accentury_App에 있다.
