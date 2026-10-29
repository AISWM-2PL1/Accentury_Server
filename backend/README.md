# backend

Spring Boot 4.1 (Java 25) + PostgreSQL 16 + Redis 7. 앱과 웹이 부르는 레벨테스트 API(세션, 업로드, 결과, 후기)와
AI 서버 호출, 앱 전용 계정 API(소셜 로그인, 추가 정보, 탈퇴)와 학습 API(단어, 억양, 진도와 홈), 번역 API,
관리자 API(`/admin/v0`)를 맡는다. Redis는 Refresh 토큰과 번역 일일 한도에만 쓴다. API 정본은 Notion
"App-Backend API 명세서"이고, 인프라와 배포는 [`infra/README.md`](../infra/README.md)가 정본이다.

## 로컬 실행

```
docker compose up -d      # PostgreSQL 16 (accentury / postgres / local, 127.0.0.1:5432) + Redis 7 (127.0.0.1:6379)
./gradlew bootRun         # 기동 시 Flyway가 스키마와 발행본을 만든다
./gradlew test            # Testcontainers PostgreSQL과 Redis(redis:7) - Docker가 떠 있어야 한다
```

AI 서버 없이도 기동한다 (`accentury.analysis.ai-base-url` 미설정 = 전달하지 않는 개발 모드). Redis도 연결을 첫
사용 때 맺으므로 없어도 기동하고, Redis를 쓰는 로그인, refresh, 번역 일일 한도만 실패한다. 설정 값과 그 이유는 전부
`src/main/resources/application.yml` 주석에 있다.

### 로그인 필수 API를 로컬에서 돌리기

계정 API, 학습 API, 번역 API는 앱 계정의 Access JWT가 있어야 한다. IdP 콘솔 없이 돌리려면 아래 설정을 준다.

| 설정 (`application.yml` 키) | 환경 변수 | 로컬에서 주는 값 |
| --- | --- | --- |
| `accentury.auth.fake-idp` | `ACCENTURY_AUTH_FAKEIDP` | `true`. `idToken`(카카오와 네이버는 `accessToken`)을 `fake:<sub>`로 보내면 IdP를 부르지 않고 그 sub로 로그인한다. deploy 프로파일에서 켜면 기동이 실패한다 (`AuthConfig`). |
| `accentury.auth.jwt-secret` | `ACCENTURY_AUTH_JWTSECRET` | 32바이트 이상의 아무 문자열. 비우면 기동마다 새 난수 키라 재기동하면 발급한 토큰이 전부 무효가 된다. |
| `accentury.translation.api-key` | `ACCENTURY_TRANSLATION_APIKEY` | Gemini API 키. 없으면 번역이 전부 503 `TRANSLATION_UNAVAILABLE`이고 나머지 API는 그대로 돈다. 가짜 번역기는 없다. |

```
./gradlew bootRun --args="--accentury.auth.fake-idp=true --accentury.auth.jwt-secret=local-jwt-secret-0123456789abcdef0123456789abcdef"
```

가입은 `POST /v0/auth/login`에 `{"provider": "GOOGLE", "idToken": "fake:dev1", "privacyConsent": true,
"privacyPolicyVersion": "<AccenturyProperties.Auth.PRIVACY_POLICY_VERSION 값>"}`를 보낸다. 계정으로 세션을 만들려면
`PUT /v0/users/me/profile`로 추가 정보를 먼저 채운다 (그 전에는 403 `AUTH_PROFILE_INCOMPLETE`). 억양 학습 채점은
AI 서버로 보내므로 `accentury.analysis.ai-base-url`과 `accentury.analysis.ai-token`도 필요하다.

루트 `docker compose up -d --build`(로컬 풀스택)는 fake-idp와 jwt-secret을 이미 켜 두고 가짜 AI와 Redis까지
같이 띄운다 (번역 API 키는 없다). 루트 compose의 DB는 5433이라 이 디렉터리의 개발 DB(5432)와 겹치지 않는다.

## Flyway 마이그레이션

스키마 정본은 `src/main/resources/db/migration`이다. Hibernate는 `ddl-auto: validate`로만 돌아 엔티티와
스키마가 어긋나면 기동을 막고, validate가 안 보는 길이, nullable, 인덱스 집합은 `SchemaBaselineTest`가
대조한다 (KAN-123).

`V1__baseline.sql`은 옛 V1~V12를 하나로 합친 재베이스라인 파일이다 (KAN-220). 합친 출처 표는 그 파일 머리
주석에 있고, staging과 prod의 이력에서 V1은 `1 / BASELINE` 한 행이다 (V1을 실행하지 않고 기록만 했다). 전환
경위는 `docs/wiki/infra-history.md`의 KAN-220 항목, 절차와 복구 경로는 [`docs/ops/rds-flyway.md`](../docs/ops/rds-flyway.md) "RDS 운영자 접속과 Flyway
재베이스라인 (KAN-220)" 절에 있다.

| 파일 | 내용 |
| --- | --- |
| `V1__baseline.sql` | 재베이스라인 스키마와 정의 gn-2026.09.4, 그 활성 포인터 (KAN-220) |
| `V2__app_user.sql` | 앱 계정 `app_user`와 세션의 계정 귀속 (KAN-223) |
| `V3__test_session_user_set_null.sql` | 탈퇴 때 세션의 계정 FK를 `on delete set null`로 (KAN-241) |
| `V4__active_version_audit_caller_ip.sql` | 활성 버전 전환 감사에 호출 IP (KAN-244) |
| `V5__gn_2026_10_1_seven_items.sql` | 정의 gn-2026.10.1 발행, 7문항 세트 VVWVWWW (KAN-260) |
| `V6__voice_consent.sql` | 음성 저장 선택 동의와 계정과 음성의 대응표 (KAN-269) |
| `V7__word_learning.sql` | 단어 학습 발행본, 시도, 답안, 오답 테이블 (KAN-265) |
| `V8__wd_gn_2026_10_1_word_learning.sql` | 단어 학습 발행본 wd-gn-2026.10.1 (KAN-265) |
| `V9__intonation_learning.sql` | 억양 학습 발행본 테이블 (KAN-264) |
| `V10__in_gn_2026_10_1_intonation_learning.sql` | 억양 학습 발행본 in-gn-2026.10.1 (KAN-264) |
| `V11__gn_2026_10_2_curated.sql` | 정의 gn-2026.10.2 발행, 사람 검수본 (KAN-276) |
| `V12__translation_subject.sql` | 번역 기록의 계정별 대체 ID 대응표 (KAN-266) |
| `V13__intonation_learning_attempt.sql` | 억양 학습 채점 시도 (KAN-267) |
| `V14__level_test_record.sql` | 계정의 레벨테스트 최근 등급 기록 (KAN-268) |
| `V15__gn_2026_10_2_voice_emotion.sql` | gn-2026.10.2 음성 문항에 감정 톤을 제자리 UPDATE (KAN-282, 아래 불변 예외) |
| `V16__learning_attendance.sql` | 계정의 출석일 기록 (KAN-286) |
| `V17__test_session_complete_requested_at.sql` | 세션의 첫 `/complete` 호출 시각 (KAN-284) |

| 상황 | 하는 일 |
| --- | --- |
| 빈 DB (테스트, 새 로컬) | V1부터 차례로 실행된다. |
| 옛 V1~V12 이력이 남은 로컬 DB | 체크섬이 어긋나 기동이 막힌다. `docker compose down -v && docker compose up -d`로 리셋한다 (개발 데이터는 버려도 된다). |

규칙:

- 적용된 마이그레이션 파일은 고치지 않는다 (checksum 검증). 스키마 변경도 정의 변경도 새 파일이다.
- 테스트 정의는 발행 후 불변이다 (KAN-26, §5.4). 대본 한 글자를 고쳐도 새 testVersion을 새 마이그레이션으로
  발행하고, 활성 전환은 배포 뒤 `PUT /admin/v0/active-version`이 따로 한다 (2단계 롤아웃).
  예외는 하나다. V15는 세션, 채점, 세트 구성이 바뀌지 않는 화면용 필드(`emotion`) 하나만 더하는 일이라
  gn-2026.10.2를 새 버전 없이 제자리에서 UPDATE했다 (2026-10-13 결정, KAN-282). 활성 전환 없이 배포만으로
  반영된다.
- 발행 마이그레이션은 손으로 쓰지 않는다. 레벨테스트 정의는 `tools/content/build_definition.py`, 단어 학습
  발행본은 `tools/content/build_word_learning.py`, 억양 학습 발행본은 `tools/content/build_intonation_learning.py`가
  만든다. 학습 발행본도 발행 후 불변이고 활성 포인터가 없다. 서버가 발행 시각이 가장 늦은 행을 쓴다.
- 정의를 품은 파일의 주석에 달러 인용 구분자 문자열을 적지 않는다. 앱, iOS, 웹의 발행본 전수 검사가 복사본
  (Accentury_App `fixtures/definitions/<버전>/`)에서 처음 만나는 구분자 두 개 사이를 정의 JSON으로 읽는다.
- 테스트 전용 발행본은 `src/test/resources/db/testdata`(V899 더미 gn-2026.08.1과 테스트 활성 되돌림, V900
  구버전, V901 풀 픽스처)에 두고 test 프로파일만 읽는다 (`application-test.yml`).
