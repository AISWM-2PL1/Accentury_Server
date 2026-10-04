# 개인정보처리방침 근거 장부 (KAN-176)

`infra/privacy/privacy.html`에 적힌 문장 하나하나가 **어떤 코드·티켓·결정을 근거로 삼았는지**를
드는 장부다. 방침 본문이 정본이고 이 문서는 그 뒤를 받친다 — 왜 그렇게 썼는가, 그리고 코드가
바뀌면 본문의 어디를 고쳐야 하는가.

이 장부가 따로 필요한 이유는 문서가 코드보다 오래 살기 때문이다. "분석이 끝나면 즉시 삭제한다",
"14일 보관한다" 같은 문장은 우리가 코드로 지키고 있는 약속인데, 그 코드를 고치는 사람에게는
방침이 보이지 않는다. 계약 테스트(`infra/privacy/privacy.test.mjs`)가 핵심 수치를 붙들어 1차
경보를 울리고, 테스트가 못 붙드는 나머지 문장은 이 표가 맡는다.

- 티켓: **KAN-176** (방침 본문 작성)
- 본문 정본: [`infra/privacy/privacy.html`](../../infra/privacy/privacy.html) — 14개 절
- 계약 테스트: [`infra/privacy/privacy.test.mjs`](../../infra/privacy/privacy.test.mjs) 34건 (KAN-269에서 선택 동의 3건을 더하고 환경별 본문 3건을 고쳐 썼다. 그 가운데 staging 전용 고지 검사는 두 환경 본문 일치 검사로 바꿨다. KAN-240에서 계정 절 4건과 방침 버전 3건 추가. KAN-239에서 환경별 본문 3건 추가. 1항 표 검사는 행 목록으로 확장한다 — KAN-164 행 포함. KAN-196 1단계에서 사업자 확정 3건, KAN-197 1단계에서 웹 사업자 1건, KAN-211 4단계에서 이용 후기 2건 추가), CI `edge-test` 잡에 결선 (`.github/workflows/test.yml`)
- 이용 후기: **KAN-211** (2026-09-15). 앱 계정(KAN-223)이 생기기 전까지는 회신 이메일이 이 서비스가 받는 유일한 개인 식별 정보였다. 구현 정본은 [`feedback.md`](feedback.md)
- 계정 PII: **KAN-240** (2026-09-29). 앱 소셜 로그인(KAN-223)의 `app_user`가 이메일, 이름, 생년월일, 성별, 출신 지역, IdP 식별값을 담는 첫 PII 테이블이라 1, 3, 5, 6, 7, 11, 12, 14항을 계정 기준으로 고쳤다. 방침 버전은 `2026-09-29`이고 서버가 가입 동의를 이 값과 정확 일치로만 받는다 (4절 「방침 버전」)
- 음성 저장 선택 동의: **KAN-269** (2026-10-04). 음성 저장과 AI 모델 학습 활용에 선택 동의한 응시의 음성을 두 환경 모두 음성 전용 버킷에 보관한다. 「음성은 저장하지 않는다」를 선택 동의를 하지 않은 경우로 한정하고 1, 3, 5, 6, 7, 11, 12항을 고쳤다. staging 전용 고지 블록은 비웠다. 방침 버전은 `2026-10-04`다
- 호스팅과 게시 경로: **KAN-133** — `infra/privacy/README.md`, `scripts/publish-privacy.sh`
- 앱 안 링크: **KAN-177** — 인트로 하단 한 줄, 구현 완료. 계약과 여는 방법은 [`webview-bridge.md` §4](webview-bridge.md), URL 정본은 `web/src/legal/privacyPolicy.ts` · 스토어 등록: **KAN-174**(Play) **KAN-175**(App Store)
- 광고 도입: **KAN-196**(앱 SDK·동의 UI·ATT·스토어 신고) **KAN-197**(웹 광고). 사업자는 **앱이 Google LLC의 Google AdMob**(2026-09-11 팀장 결정, Firebase 프로젝트 공유), **브라우저 단독 실행이 같은 회사의 Google AdSense**(2026-09-13 팀장 결정 — AdMob은 웹을 지원하지 않는다, [`ads-web-adsense.md`](ads-web-adsense.md)). KAN-196은 전 단계 완료, KAN-197도 전 단계 완료(2026-09-13 — 1단계 방침·동의 문안·문서, 2단계 웹 동의 저장, 3단계 태그·슬롯, 4단계 배포 변수·`ads.txt`). 코드는 닫혔고 **광고가 실제로 나오기까지 남은 것은 사람 손의 절차**다 — AdSense 가입·사이트 심사·변수 등록이고, 그 표가 [`ads-web-adsense.md`](ads-web-adsense.md) §10이다
- 카카오 공유 웹훅 수신: **KAN-164**. 1, 5, 9항에 반영했다 (KAN-176 작업 브랜치가 갈라진 뒤 Dev에 들어온 것을 PR #89 리뷰에서 따라잡았다)

## 1. 절별 근거 매핑

본문 절 번호 순이다. 「계약 테스트」 열은 `privacy.test.mjs`의 테스트 이름이고, 비어 있으면
그 문장은 테스트가 아니라 이 표만으로 지켜진다.

### 1항 — 처리 목적·항목·보유 기간

표의 행 단위로 쪼갠다.

| 표의 행 | 핵심 주장 | 근거 | 계약 테스트 |
|---|---|---|---|
| 음성 녹음 | 분석이 끝나면 즉시 삭제 | `ai/app/tempstore.py:10-14` (컨텍스트 매니저가 성공·실패·예외·취소 모두 `finally`에서 삭제), KAN-27 | `음성은 분석 직후 즉시 삭제라고 적혀 있다`·`1항 표의 보유 기간이 행마다 코드와 맞는다` |
| 음성 저장과 학습 활용 (선택 동의) | 음성 저장과 AI 모델 학습 활용은 필수 방침 동의와 별개인 선택 동의이고 거부해도 이용 제한이 없다. 대상은 웹과 앱에서 동의한 응시자이며 동의 문안이 만 14세 이상 확인을 겸한다. 두 환경의 본문이 같다 | 2026-10-04 사용자 결정 (KAN-269, 5절 결정 기록). 구현은 `backend`의 `training` 패키지(`VoiceConsents`가 업로드마다 동의를 판정하고 `S3TrainingSampleStore`가 저장 직전에 다시 확인한다), `session/SessionService`(익명 세션의 `voiceConsentVersion`), `auth/UserService`(계정 동의 등록과 철회), `V6__voice_consent.sql`(동의 열과 대응표 `training_voice_owner`)이다. 앞 판(KAN-239)의 staging 전용 고지(동의한 테스터 계정만, 가명 세션, 보유 기간 끝날)는 지웠고 `<!-- staging-only -->` 블록은 비어 있다 | `prod 본문에 음성 저장 선택 동의 절이 있고 대상, 항목, 장소, 기간, 철회를 적는다`, `두 환경의 게시 본문이 같다 - 음성 고지에 환경별 차이가 없다`, `prod 본문은 표식 없이 끝까지 게시되고 옛 staging 고지가 없다` |
| 음성 저장과 학습 활용 (선택 동의) | 보관 항목은 문항마다의 WAV와 라벨 파일(분석 작업 ID, 세션 ID, 문항 ID, 출신 지역 10개 권역 코드, 스크립트 키, 테스트 버전, 채점 버전, 음성 길이, 최종 분석 상태, AI 억양 원점수, 음질 코드, 모델 버전, AI 채점 버전, 오류 코드, 상관 ID, 동의 버전과 동의 시각, 저장 시각). ID는 가명화하지 않고 그대로 둔다 | KAN-269 결정. 라벨 필드가 늘거나 줄면 본문 목록과 계약 테스트의 항목 배열을 함께 고친다 | `prod 본문에 음성 저장 선택 동의 절이 있고…` |
| 음성 저장과 학습 활용 (선택 동의) | 앱 계정은 계정 내부 ID와 음성이 저장된 세션 ID를 잇는 연결 표를 두고, 세션 만료와 탈퇴 뒤에도 남긴다. 내부 ID만 담는다 | KAN-269 결정. 철회나 탈퇴 때 그 계정의 음성을 찾기 위한 표다. 계정의 개인정보 열은 탈퇴 때 파기한다(KAN-241) | `prod 본문에 음성 저장 선택 동의 절이 있고…` |
| 음성 저장과 학습 활용 (선택 동의) | 웹 익명 세션은 동의를 세션에 기록하고, 세션이 만료되면 저장된 음성을 특정인에게 귀속할 수 없어 개별 삭제 요청을 맞출 수 없다 | 웹 세션은 익명이고 30분 또는 완료 후 24시간 뒤 삭제된다(「테스트 세션」 행) | `prod 본문에 음성 저장 선택 동의 절이 있고…` |
| 음성 저장과 학습 활용 (선택 동의) | 보관 장소는 서울 리전의 S3 버킷, 버전 기록 켬, 저장 시 암호화, HTTPS 전용, 읽기는 모델 학습 담당자로 제한 | KAN-269 결정: 공유 음성 전용 버킷 `accentury-voice-<account>`, 접두사 `staging/`과 `prod/`. Terraform은 `infra/bootstrap/voice.tf`(버킷, 버전 관리, 암호화, TLS 강제와 GetObject 제한 정책)와 `infra/modules/fargate`(태스크 역할의 환경 접두 PutObject)다 | `prod 본문에 음성 저장 선택 동의 절이 있고…`, `데이터 소재지가 서울 리전이라고 적혀 있다` |
| 음성 저장과 학습 활용 (선택 동의) | 보유 기간은 「학습 목적 달성 시까지」이고 자동 만료가 없다 | KAN-269 결정. **이 문구는 잠정이다** - 개인정보 보호책임자의 검토를 기다린다(6절). 버킷에 수명주기 만료 규칙을 두지 않는다 | `1항 표의 보유 기간이 행마다 코드와 맞는다` (「음성 저장과 학습 활용 (선택 동의)」 행), `선택 동의가 5, 6, 7, 11, 12항에도 반영돼 있다` |
| 음성 저장과 학습 활용 (선택 동의) | 철회: 앱은 앱 안에서 철회하고 그 뒤의 녹음은 저장하지 않는다. 이미 저장된 음성은 13항 문의처로 요청하면 운영자가 처리한다 | KAN-269 결정. 저장된 음성의 처리는 운영자 수작업이다 | `prod 본문에 음성 저장 선택 동의 절이 있고…`, `선택 동의가 5, 6, 7, 11, 12항에도 반영돼 있다` |
| 음성 녹음 | 임시 파일 청소 기준 30분 | `ai/app/config.py:18`, `backend/src/main/resources/application.yml:167` (`upload.temp-retention: 30m`), `ai/app/tempstore.py:13,169-177` | `임시 파일 청소 기준 30분이 적혀 있다` |
| 음성 녹음 | 기기에도 파일로 남기지 않음 | `app/src/main/java/com/accentury/app/audio/WavWriter.kt:14-15` (업로드는 `toWavBytes`만 쓴다), `ios/AccenturyTests/RecordingFileLifecycleTests.swift:20` (`testAFullRecordEnqueueDiscardCycleLeavesNoWavOnDisk`) | — |
| 음성 녹음 | **선택 동의를 하지 않은 경우** DB와 S3에 저장 안 함 | 2026-09-01 팀 회의 결정은 KAN-269(2026-10-04)로 선택 동의를 하지 않은 응시에만 남았다. 엔티티에 오디오 컬럼 없음 (`backend/src/main/java/app/accentury/backend/session/TestSession.java`, `backend/src/main/java/app/accentury/backend/result/TestResult.java`, `backend/src/main/java/app/accentury/backend/vocab/VocabAnswer.java`) | `「음성을 저장하지 않는다」는 선택 동의를 하지 않은 경우로 한정돼 있다` |
| 계정 (앱) | 수집 항목은 `app_user`의 열 전부, 목적은 항목별, 보유는 **탈퇴까지** | `backend/src/main/resources/db/migration/V2__app_user.sql` (열 목록과 주석), `backend/src/main/java/app/accentury/backend/auth/AppUser.java`. 목적은 2026-09-29 사용자 결정(KAN-240): 이메일과 이름은 회원 식별과 문의 응대, 탈퇴 본인 확인 / 생년월일은 만 14세 미만 가입 제한 / 성별은 응시자 구성 파악과 서비스 개선 / 출신 지역은 응시 기록에 붙여 지역별 구성 파악과 억양 분석 개선. 코드에는 성별과 이름의 활용처가 아직 없다. 정리 잡이 없고 탈퇴(KAN-241)가 유일한 파기 경로다 | `1항 표의 보유 기간이 행마다 코드와 맞는다` (「계정 (앱)」 행), `1항에 계정 절이 있고 수집 항목과 목적과 보유 기간을 적는다` |
| 계정 (앱) | 다섯 항목(이메일, 이름, 생년월일, 성별, 출신 지역)이 다 있어야 테스트를 시작한다 | `ProfileStatus`, 세션 생성의 403 `AUTH_PROFILE_INCOMPLETE` (`AuthApiTest.프로필이_미완료인_계정은_403이고_세션이_생기지_않는다`) |- |
| 계정 (앱) | 계정 세션도 24시간 규칙 그대로이고 계정에 결과가 쌓이지 않음, 세션 출신 지역은 계정 값 | `test_session.user_id`(V2), `SessionService.create`가 계정 세션의 region을 계정에서 읽는다 (`AuthApiTest.Access_토큰으로_만든_세션은_계정에_귀속되고_출신지역은_계정의_값이다`) | `1항에 계정 절이 있고…` |
| 계정 (앱) | **서버 운영 로그에 세션과 계정의 대응을 남기지 않음** | `SessionService`의 `세션 생성` 로그가 `userId` 대신 `account=true|false`만 남긴다 (KAN-240). 로그인, 로그아웃, 프로필 저장 로그는 `userId`만 있고 `sessionId`가 없다 | `AuthApiTest.세션_생성_로그에_계정_id가_없고_계정_세션_여부만_남는다`, `1항에 계정 절이 있고…` |
| 로그인 토큰 | Refresh 해시와 계정 id만 **30일**, 로그아웃 시 패밀리 폐기 | `backend/src/main/java/app/accentury/backend/auth/RefreshTokens.java` 클래스 javadoc (`rt:{해시}` TTL 30일, `rtfam:*`), `application.yml` `auth.refresh-token-ttl: 30d`, `AuthService.logout`. 앱 쪽 보관은 Android Keystore와 iOS 키체인 (`Accentury_App` 레포 `auth/AuthTokens.kt`의 `KeystoreTokenStore`) | `1항 표의 보유 기간이 행마다 코드와 맞는다` (「로그인 토큰」 행) |
| 테스트 세션 | 토큰은 해시값만 저장 | `backend/src/main/java/app/accentury/backend/session/TestSession.java:36,44-46` (44행 주석 「토큰 원문은 어디에도 저장하지 않는다」 + `token_hash` 컬럼뿐), KAN-9 | — |
| 테스트 세션 | 세션의 처음 수명은 30분 | `backend/src/main/resources/application.yml:114` (`session.ttl: 30m`), `backend/src/main/java/app/accentury/backend/session/SessionService.java:133` (`expiresAt = now.plus(properties.session().ttl())`) | `1항 표의 보유 기간이 행마다 코드와 맞는다` |
| 테스트 세션 | 미완주 세션은 만든 지 30분 뒤 만료 정리 | `backend/src/main/java/app/accentury/backend/session/SessionService.java:133,365-372` (`purgeExpired`가 `deleteByExpiresAtBefore`, 10분 주기), `backend/src/main/resources/db/migration/V1__baseline.sql:98,124,146` (`on delete cascade` — 어휘 답안·분석 작업·결과가 세션 행과 함께 지워진다. KAN-220 재베이스라인 뒤 행 번호) | 위와 같음 |
| 테스트 세션 | 완주 세션은 **완료 시점부터** 24시간 뒤 삭제 | `backend/src/main/resources/application.yml:128` (`analysis.retention: 24h`), `backend/src/main/java/app/accentury/backend/result/CompletionService.java:169-178` (`resultExpiresAt = 완료 시각 + retention` → `markCompleted`), `backend/src/main/java/app/accentury/backend/session/TestSession.java:140-157` (완료 시 세션 만료를 결과 만료로 다시 잡는다 — 토큰 수명도 함께 24시간이 된다) | 위와 같음 + `세션 보유 기간의 기준점이 「생성 시점」으로 되돌아가지 않았다` |
| 테스트 결과·어휘 답안 | 완료 시점부터 24시간 | `backend/src/main/java/app/accentury/backend/result/TestResultRetention.java:22,33-40` (`deleteByExpiresAtBefore` 60분 주기), `backend/src/main/java/app/accentury/backend/result/CompletionService.java:170-175` (`expiresAt`을 저장 시점에 확정), KAN-25·KAN-15 | 위와 같음 |
| 테스트 결과·어휘 답안 | 미완주 세션의 것은 세션과 함께 삭제 | `backend/src/main/resources/db/migration/V1__baseline.sql:98,124,146` (CASCADE, KAN-220 재베이스라인 뒤 행 번호), `backend/src/main/java/app/accentury/backend/session/SessionService.java:355-372` (`purgeExpired` 주석이 "하위 3테이블도 함께 지운다"를 명시) | 위와 같음 |
| 익명 통계 | 합계값만, 세션·IP·개인 점수 없음 | `backend/src/main/java/app/accentury/backend/analytics/DailyCounter.java:49-111` (엔티티의 컬럼 전부 — 일자·버전·트래픽 구분과 건수·점수 **합계**뿐이고 세션 id·IP·개별 점수 컬럼이 없다), KAN-106 | — |
| 익명 통계 | 카카오톡 공유 **전송 완료 수**를 일자·캠페인별 합계로만 쌓고 세션과 잇지 않음 | `backend/src/main/resources/db/migration/V1__baseline.sql:260-267` (옛 V7, KAN-220 재베이스라인으로 V1에 합쳐짐. `share_daily_counter`의 컬럼은 `stat_date`·`campaign`·`sent`뿐이고 250-254행 주석이 「세션과 연결하지 않는 것이 이 집계의 요구」를 적었다), `backend/src/main/java/app/accentury/backend/share/KakaoShareWebhookController.java:107` (본문에서 읽는 값은 `campaign` 하나 — 46-49행 주석 「채팅방 정보(CHAT_TYPE, HASH_CHAT_ID)는 읽지도 저장하지도 않는다」), `backend/src/main/java/app/accentury/backend/share/ShareCounters.java:48-55` (`recordSent`가 올리는 것은 일자·캠페인 카운터 1뿐), KAN-164 | `1항 표의 보유 기간이 행마다 코드와 맞는다` (「익명 통계」 행) |
| 공유 전송 알림 기록 | 카카오가 붙인 불투명 식별값만, **7일** 뒤 삭제 | `backend/src/main/resources/db/migration/V1__baseline.sql:269-273` (옛 V7. `share_webhook_receipt`는 `resource_id`·`received_at` 두 컬럼, 256-259행 주석이 「채팅방 해시·종류·세션 id·토큰·점수는 어디에도 저장하지 않는다」), `backend/src/main/java/app/accentury/backend/common/AccenturyProperties.java:249` (`@DefaultValue("7d") Duration receiptRetention`), `backend/src/main/resources/application.yml:237-241` (같은 값의 주석과 예시), `backend/src/main/java/app/accentury/backend/share/ShareWebhookReceiptRetention.java:36-40` (60분 주기 `purgeExpired`가 `now - retention` 이전을 지운다) | `1항 표의 보유 기간이 행마다 코드와 맞는다` (「공유 전송 알림 기록」 행) |
| 공유 전송 알림 기록 | 식별값이 **서버 운영 로그(14일)에도 남을 수 있음** | `backend/src/main/java/app/accentury/backend/share/KakaoShareWebhookController.java:109-110` (수신마다 `resourceId=`를 INFO로 남긴다 — 109행 주석이 「중복 콜백을 추적하는 유일한 단서」라 적었다), `backend/src/main/java/app/accentury/backend/share/ShareCounters.java:50` (중복 콜백도 같은 값을 남긴다), 보존은 「서버 운영 로그」 행과 같은 14일 | — |
| 익명 통계 | 재응시·만료로 되돌리지 않음 | `backend/src/main/java/app/accentury/backend/analytics/DailyCounter.java:33`, `backend/src/main/java/app/accentury/backend/analytics/AnalyticsCounters.java:45` (둘 다 "되돌리지 않는다"를 명시) | — |
| 접속 IP | 메모리에서만 세고 저장 안 함 | `backend/src/main/java/app/accentury/backend/common/FixedWindowRateLimiter.java:9,12,20` (`ConcurrentHashMap` 인메모리 윈도우), `backend/src/main/java/app/accentury/backend/common/ClientIps.java:52-58` (판정만 하고 반환) | — |
| 서버 운영 로그 | 14일 | `infra/modules/fargate/variables.tf:173-176` (`log_retention_days` 기본값 14) | `1항 표의 보유 기간이 행마다 코드와 맞는다` |
| 보안 로그 | 7일, 인증 헤더는 가림 | `infra/modules/waf/variables.tf:47-50` (기본값 7), `infra/modules/waf/main.tf:525,548,554` (샘플 저장 끔 + `redacted_fields`) | `1항 표의 보유 기간이 행마다 코드와 맞는다` |
| 이용 통계 이벤트 | 보존은 GA 설정에 따름 (기본값 2개월) | **미확인** — 콘솔 기본값이고 레포에 근거가 없다 (7항 참조) | — |
| 비정상 종료 로그 | 90일 | **미확인** — Crashlytics 콘솔 기본값이고 레포에 근거가 없다 (7항 참조). 수집 항목 자체는 KAN-33, `docs/wiki/analytics.md` §8 | `1항 표의 보유 기간이 행마다 코드와 맞는다` (표기가 90일인지만 붙든다) |
| 이용 후기 | 별점·본문·**회신 이메일(선택)**과 결과 스냅샷을 받고 **1년** 보관 | `backend/src/main/resources/db/migration/V1__baseline.sql:290-305` (옛 V12, KAN-220 재베이스라인으로 V1에 합쳐짐. 컬럼 전부 — 입력 셋과 스냅샷 다섯. 283-287행 주석이 「test_session에 FK를 걸지 않는다」와 그 이유를 적었다), `backend/src/main/java/app/accentury/backend/common/AccenturyProperties.java`의 `Feedback.retention` (`@DefaultValue("365d")`), `backend/src/main/resources/application.yml:179` (같은 값과 주석), `backend/src/main/java/app/accentury/backend/feedback/FeedbackRetention.java:36-43` (60분 주기 `purgeExpired`가 `created_at` 기준으로 지운다), KAN-211 | `1항 표의 보유 기간이 행마다 코드와 맞는다` (「이용 후기」 행)·`이용 후기 절이 있고 이메일이 선택·회신 전용이라고 적혀 있다` |
| 이용 후기 | 세션이 지워진 뒤에도 남고, 함께 남는 것은 **등급·테스트/점수 버전·이용 경로**뿐 | FK 없음 + 저장 시점 스냅샷 복사 (`V1__baseline.sql:290-308`(옛 V12, `session_feedback`)의 `tier_code`·`test_version`·`score_version`·`platform`·`traffic`, `FeedbackService.submit`의 스냅샷 단계). 세션·결과는 24시간(§5.5)이고 후기는 1년이라 두 수명이 독립이다 | 위와 같음 |
| 이용 후기 | **내부 알림에 이메일 주소를 싣지 않음** | `backend/src/main/java/app/accentury/backend/feedback/FeedbackSlackNotifier.java`의 `message` (연락처는 `있음`/`없음`만 붙인다 — javadoc이 「채널은 개발팀 전원이 보고 슬랙 무료 플랜은 지난 메시지를 지우지 않는다」를 근거로 적었다), 마지막 관문은 `backend/src/main/java/app/accentury/backend/common/LogMasking.java`의 `EMAIL` 패턴 | `후기 이메일이 내부 알림에 실리지 않는다고 적혀 있다` |
| 이용 후기 | 본문·이메일을 **로그에 남기지 않음** | `FeedbackService` 클래스 javadoc (§2.6), `LogMasking`의 `EMAIL`이 경로와 무관하게 한 번 더 가린다 | 위와 같음 (11항 몫) |
| 광고 | Google 개인정보처리방침에 따름 | 2026-09-07 팀 결정(광고 도입) + 2026-09-11 팀장 결정(사업자 = Google AdMob), KAN-196·KAN-197. 코드 배선은 KAN-196 2~4단계에서 완료 (`docs/wiki/ads-admob.md`) | `맞춤형 광고 절이 있고 동의·거부 방법이 적혀 있다` |

같은 항의 산문 부분은 위 행들의 풀이다. 「재응시하면 이전 세션과 결과를 즉시 삭제」는
`backend/src/main/java/app/accentury/backend/session/SessionService.java:28,87-88,201-203,245-247`과
`backend/src/main/java/app/accentury/backend/result/TestResultRepository.java:28`,
`backend/src/main/java/app/accentury/backend/vocab/VocabAnswerRepository.java:32`,
`backend/src/main/java/app/accentury/backend/analysis/AnalysisJobRepository.java:98`이 근거이며 KAN-107 소관이다.
「캠페인 코드 64자 이하」는 `backend/src/main/java/app/accentury/backend/session/CreateSessionRequest.java`가 붙든다.

### 2~14항

| 절 | 핵심 주장 | 근거 | 계약 테스트 |
|---|---|---|---|
| 2. 제3자 제공 | 광고 사업자 제공은 제3자 제공에 해당 | 광고 SDK가 자기 목적으로 처리하는 구조 — 2026-09-07 팀 결정, KAN-196 | — |
| 2. 제3자 제공 | 제공받는 자는 Google LLC (Google AdMob, Google AdSense), 보유 기간은 Google 개인정보처리방침에 따름 | 2026-09-11 팀장 결정 (KAN-196 1단계) + 2026-09-13 웹 사업자 확정 (KAN-197 1단계). 둘 다 Google LLC라 행을 나누지 않고 제공받는 자·제공 항목에 웹 몫을 더했다 — 4항과 같은 이유다. 그래서 KAN-196이 붙들던 「Google LLC (Google AdMob)」 정확 일치를 부분 일치로 완화했다. 이 자리는 2026-09-11 전까지 「확정 후 기재」였다 | `2항 제3자 제공 표와 10항 광고 절에 Google AdMob이 적혀 있다`·`브라우저 웹의 광고 사업자 Google AdSense가 2·4·8·10항에 적혀 있다`·`「확정 후 기재」 자리표시자가 남아 있지 않다` |
| 2. 제3자 제공 | 위탁·공유는 제3자 제공이 아님 | 3항(위탁)과 9항(이용자 선택 공유)의 구분 | — |
| 3. 위탁 | AWS 서울 리전(ap-northeast-2) | `infra/envs/prod/terraform.tfvars:3`, `infra/envs/staging/terraform.tfvars:3` | `데이터 소재지가 서울 리전이라고 적혀 있다` |
| 3. 위탁 | Google LLC(Firebase Analytics·Crashlytics) | KAN-33, `docs/wiki/analytics.md` | — |
| 3. 위탁 | AWS 행의 위탁 업무에 「로그인 토큰 저장소」(ElastiCache Redis)를 더했다 | `infra/envs/*/main.tf`의 `redis_host` (모듈 `data`), 서울 리전 |- |
| 3. 소셜 로그인 제공자 | 제공자 넷(구글, 카카오, 네이버, 애플 iOS만)과 제공자별로 받는 항목. 제공자는 수탁자가 아니고, 서버는 토큰 확인만 하며 이용자 정보를 보내지 않는다 | `backend/src/main/java/app/accentury/backend/auth/GoogleIdpVerifier.java` (`sub`, 확인된 `email`, `name`, `picture`), `AppleIdpVerifier.java` (`sub`, `email`, 최초 1회 `user.name`, 릴레이 주소 `privaterelay.appleid.com`을 그대로 둔다), `KakaoIdpVerifier.java` (`is_email_valid`와 `is_email_verified`가 둘 다 참일 때만 이메일, 이름, 닉네임, 프로필 이미지, 양력일 때만 생년월일, 성별), `NaverIdpVerifier.java` (이메일, 이름, 생년월일, 성별, 닉네임, 프로필 이미지. 휴대전화 번호는 읽지 않는다). 명세서 §3.9 「IdP별 검증」 표. 티켓(KAN-240)이 3항에 두라고 해서 위탁 표 아래 별도 절로 뒀다 | `3항에 로그인 제공자 넷과 받는 정보, 애플 전달용 이메일이 있다` |
| 4. 국외 이전 | 이전 대상은 이용 통계·오류 로그·광고 관련 항목이고 이전받는 자는 전부 Google LLC | 음성·세션·결과는 서울 리전 (`infra/envs/prod/terraform.tfvars:3`, `infra/envs/staging/terraform.tfvars:3`), Google(Firebase·GA·AdMob)만 국외 | `데이터 소재지가 서울 리전이라고 적혀 있다` |
| 4. 국외 이전 | 광고 관련 항목은 기존 Google LLC `<dl>`에 합쳐 적었다 — 이전받는 자에 Google AdMob·Google AdSense, 이전되는 항목에 10항의 광고 항목(앱의 광고 식별자 또는 웹의 광고 쿠키), 이용 목적에 맞춤형 광고 표시·성과 측정 | AdMob도 AdSense도 Google LLC라 이전받는 자·국가·방법이 Firebase와 같다. 별도 행을 만들면 같은 사업자를 두 번 고지하게 된다 (2026-09-11 KAN-196 1단계, 웹 몫은 2026-09-13 KAN-197 1단계). 이 자리는 그 전까지 「확정 후 기재」였다 | `4항 국외 이전에 광고 항목이 들어 있다`·`브라우저 웹의 광고 사업자 Google AdSense가 2·4·8·10항에 적혀 있다` |
| 4. 국외 이전 | **이용 후기는 Slack Technologies, LLC (미국)로 이전된다.** Google `<dl>`과 나란히 별도 `<dl>` 한 벌. 이전 항목은 후기 내용·별점·등급·테스트/점수 버전·이용 경로이고 **이메일 주소는 가지 않는다**. 보유는 Slack 워크스페이스 보존 정책(무료 플랜 기준 최근 90일 열람) | `FeedbackSlackNotifier`가 커밋 뒤 채널 `#feedback`으로 올린다. **국외 이전으로 분류한 판단**: 후기 본문은 이용자가 자유 서술한 내용이라 개인정보가 섞일 수 있고(이름·연락처를 본문에 적는 사람이 있다), 받는 쪽이 미국 사업자다. 「가능성」만으로 고지하는 쪽을 고른 것은 안 적었다가 실제로 섞인 날 되돌릴 방법이 없기 때문이다 — 무료 플랜은 지난 메시지를 지우지 않는다. **대안(3항 위탁에 두는 안)을 쓰지 않은 이유**: 위탁은 우리 목적의 처리를 맡기는 것이고 Slack은 메시지를 우리 대신 처리하는 것이 아니라 **전달·보관할 뿐**이다. 게다가 3항에 두면 「처리 장소: 미국 등 국외」 한 줄로 끝나 이전 항목·목적·기간이 드러나지 않는다 — Firebase가 3항과 4항에 **둘 다** 적힌 것과 같은 이유로 4항이 본 자리다. Google 행과 합치지 않은 것은 사업자가 다르기 때문이다(AdMob·AdSense를 합친 근거가 「둘 다 Google LLC」였다). 2026-09-15 결정, KAN-211 | `이용 후기 절이 있고 이메일이 선택·회신 전용이라고 적혀 있다` (4항 Slack 조각) |
| 4. 이용 통계 이벤트 | 수집 항목 목록 | `web/src/analytics/events.ts:71` (`AnalyticsEvent` 유니온이 이름·파라미터의 정본), `docs/wiki/analytics.md` §1 | — |
| 4. 이용 통계 이벤트 | 응시 구분 무작위 키는 탭을 닫으면 사라짐 | `web/src/analytics/testId.ts:21-23,93` (`sessionStorage`) | — |
| 4. 이용 통계 이벤트 | 세션 id·토큰·문항·점수 원값을 싣지 않음 | `docs/wiki/analytics.md` §3 「익명 규칙」, `web/src/analytics/events.ts` | — |
| 4. 이용 통계 이벤트 | 광고 식별자를 쓰지 않음 (Android·iOS·웹 각각) | `app/src/main/AndroidManifest.xml:43-49` (`google_analytics_adid_collection_enabled=false`, 개인화 신호 false), `ios/Accentury/Analytics/FirebaseEventSink.swift:23-32` (`FirebaseAnalyticsCore`로 계측 라이브러리 안에서만 AdSupport·ATT를 배제 — KAN-196 4단계부터 AdMob SDK가 AdSupport·ATT를 링크하므로 「바이너리에 없다」는 더는 사실이 아니고, 계측이 IDFA를 안 읽는다는 것만 지킨다, `docs/wiki/ads-admob.md` §7.1), `web/src/analytics/ga4.ts:75-81` (`allow_google_signals:false`, `allow_ad_personalization_signals:false`) | — |
| 4. 비정상 종료 로그 | 스택·기기·OS·앱 버전, 사용자 ID 없음 | KAN-33, `docs/wiki/analytics.md` §3 「크래시 리포트에도 같은 규칙이 선다」 | — |
| 5. 파기 | 계정은 탈퇴 시 지체 없이 파기, 탈퇴 API 전까지는 6항 요청을 운영자가 처리. 로그인 토큰은 30일 또는 로그아웃 시 폐기. 첫 문단의 「자동으로」에 탈퇴 요청 처리 예외를 달았다 | 탈퇴는 KAN-241 (`DELETE /v0/users/me`, 명세서 §3.14). 수작업 삭제는 `test_session.user_id`의 FK가 NO ACTION이라 그 계정의 세션 귀속을 먼저 끊거나(`update test_session set user_id = null`) 24시간 뒤 세션이 지워진 다음에 `app_user` 행을 지운다 | `5, 6, 7, 12항이 계정 기준으로 다시 쓰였다` |
| 5. 파기 | 파기 시점 여섯 가지 | 1항의 근거를 그대로 반복한다 (음성 즉시·30분, 미완주 세션 30분, 완주 세션 완료 후 24시간, 재응시 즉시, 공유 전송 알림 식별값 7일, 로그 14일/7일) | `음성은 분석 직후…`·`임시 파일 청소 기준 30분…`·`세션·결과 보유 기간 24시간…` |
| 6. 정보주체 권리 | 앱 계정은 열람, 정정, 삭제, 탈퇴를 13항 이메일로 요청. **탈퇴 API 전 임시 절차**: 제공자를 적어 계정 이메일로 요청, 계정 이메일과 대조해 본인 확인 뒤 삭제하고 회신 | 2026-09-29 사용자 결정(KAN-240, 보호책임자 이메일 요청). 앱 안 탈퇴는 KAN-241이 나오면 이 문단을 바꾼다. 처리는 DB 직접 조회다 (`app_user`를 `provider`와 `email`로 찾는다. 이메일은 유일 제약이 없어 제공자까지 받아야 한 행으로 좁혀진다). 계정 이메일로 보낼 수 없는 경우(애플 전달용 주소는 등록한 발신 도메인만 받아 우리 Gmail로는 오가지 못한다, 추가 정보 전에 멈춘 계정은 `email`이 null이다)는 「방법을 따로 안내」로만 적었다. 실제 확인 수단은 정하지 않았다 (6절 팀 확인). Codex 리뷰 P2 반영 | `5, 6, 7, 12항이 계정 기준으로 다시 쓰였다` |
| 6. 정보주체 권리 | 브라우저 웹에는 특정 이용자의 정보를 지목할 수단이 없음 | 웹 세션은 익명 (`TestSession.userId`가 null, 웹은 로그인을 부르지 않는다, 명세서 §2.1) |- |
| 6. 정보주체 권리 | 탭을 닫으면 세션 토큰·응시 키는 사라지고, 진행 기록은 결과 화면 진입에서 지워진다 | `web/src/session/webSession.ts`·`web/src/analytics/testId.ts:21-23` (`sessionStorage`) 대 `web/src/progress/progressSnapshot.ts` (`localStorage`, 키 `accentury:progress:<sessionId>`). 삭제 배선은 `web/src/App.tsx`의 `ResultRoute`(`clearSnapshot`)와 `IntroRoute`(`sweepSnapshots` — 결과까지 못 간 응시의 잔여 키) 두 자리다 (KAN-198) | `진행 기록의 삭제 시점이 적혀 있다` |
| 6. 정보주체 권리 | **후기에 이메일을 적은 경우에는 그 주소로 13항 문의처에 열람·삭제를 요청할 수 있다** | 첫 문단의 전제(「개인을 알아볼 수 있는 값이 없습니다」)를 이 티켓이 부분적으로 깬다 — 회신 이메일이 계정 없는 서비스에서 이용자를 **지목할 수 있는 유일한 값**이다. 전제를 지우지 않고 예외를 목록 뒤에 더한 것은 나머지 데이터(세션·결과·통계)에는 여전히 지목 수단이 없기 때문이다. **처리 절차는 운영자 수작업이다**: 요청받은 주소로 `session_feedback.contact_email`을 조회해 해당 행을 삭제한다. 관리자 조회 API가 없어 DB 직접 조회이고, 자동화는 이월했다 ([`feedback.md`](feedback.md) §10). 2026-09-15 결정, KAN-211 | `이용 후기 절이 있고 이메일이 선택·회신 전용이라고 적혀 있다` (6항 「삭제」·「13항」 조각) |
| 7. 만 14세 미만 | 앱은 생년월일로 만 14세 미만 가입을 거절하고 그 생년월일을 저장하지 않음. IdP가 준 생년월일은 계정을 만들기 전에 거절 | `UserService.updateProfile` (저장 전 `ProfileRules.age` 검사, 400 `AUTH_UNDER_AGE`), `AuthService.rejectUnderAge` (로그인 단계, `findOrCreate` 전), `AuthServiceUnderAgeTest`, `AuthApiTest.만_14세_미만은_400이고_생년월일도_남지_않는다` | `5, 6, 7, 12항이 계정 기준으로 다시 쓰였다` |
| 7. 만 14세 미만 | 아동 대상 아님, 마켓에도 그렇게 등록 | `Accentury_App` 레포 `docs/wiki/play-store-listing.md` §6 (타겟 연령 18세 이상, 2026-09-22 등록. KAN-174는 App PR #5로 2026-09-24 병합) | — |
| 7. 만 14세 미만 | 아동에게 맞춤형 광고 미표시 | 2026-09-07 팀 결정. 아동 대상 아님을 유지하고 AdMob SDK의 아동 대상 플래그는 off — KAN-196 3단계(Android `AdsController.initialize`, `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`)·4단계(iOS `AdsController.start`, `tagForChildDirectedTreatment = false`)에서 완료, `docs/wiki/ads-admob.md` §6·§7.1 | — |
| 8. 자동 수집 장치 | 웹에는 GA4 태그가 쿠키를 저장 | `web/src/analytics/ga4.ts:75-81` — `config`에 쿠키를 끄는 옵션이 없다(기본 동작이 쿠키 설정) | — |
| 8. 자동 수집 장치 | 앱 WebView에는 웹 태그(GA4·AdSense)를 깔지 않음 | `web/src/main.tsx:28` — `isStandaloneWeb`일 때만 `installGa4Tag()`. 광고 태그도 **같은 게이트**를 쓰되 자리가 다르다 (KAN-197 3단계, 2026-09-13): `web/src/ads/AdSlot.tsx`가 슬롯을 마운트할 때 판정한다 (근거는 `docs/wiki/ads-web-adsense.md` §2.1). GA4는 이중 계측 때문이고 AdSense는 앱 WebView 내 웹 광고 태그가 정책상 허용되지 않기 때문이다 (같은 문서 §2). 그래서 WebView allowlist에 광고 도메인을 넣지 않는다 | `AnalysisWaitingScreen.test.tsx`의 `앱 WebView 안에서는 웹 광고 태그가 설치되지 않는다 (KAN-197 AC)` |
| 8. 자동 수집 장치 | 진행 기록은 브라우저 저장소에 두되 결과 화면 진입에서 지우고, 끊긴 응시의 기록은 다음 인트로 진입에서 걷는다 | `web/src/progress/progressSnapshot.ts` (키 `accentury:progress:<sessionId>`, `defaultSnapshotStorage`가 `window.localStorage`), `web/src/App.tsx` `ResultRoute`의 `clearSnapshot`·`IntroRoute`의 `sweepSnapshots`. 복원에 실패한 스냅샷(형태 손상·버전 불일치·재생 거부)도 `restoreProgress`가 그 자리에서 버린다 (KAN-198) | `진행 기록의 삭제 시점이 적혀 있다` |
| 8. 자동 수집 장치 | 세션 토큰·응시 키는 탭 저장소 | `web/src/session/webSession.ts`, `web/src/analytics/testId.ts:21-23` (`sessionStorage`) | — |
| 8. 자동 수집 장치 | 앱의 광고 SDK가 광고 식별자를 사용 | 2026-09-07 팀 결정, 사업자는 Google AdMob (2026-09-11). 코드 배선은 KAN-196 2~4단계에서 완료 (`docs/wiki/ads-admob.md` §4) | — |
| 8. 자동 수집 장치 | 브라우저 웹에는 Google AdSense 광고 태그도 설치되며 광고 쿠키를 저장한다. 거부하면 관심사 추정에 쓰지 않고 빈도 제한·집계 보고·부정 사용 방지에만 쓴다. 차단·삭제 방법은 GA4 쿠키와 같다 | 2026-09-13 팀장 결정 (KAN-197 1단계). 8항은 「쿠키를 심는 장치」를 묻는 절이라 광고 쿠키가 10항에만 있으면 빠진 고지가 된다. 비맞춤에서도 쿠키를 계속 쓰는 것은 Google 「맞춤 광고 및 맞춤 설정되지 않은 광고」 — 빈도 제한과 집계된 광고 보고에 쓴다고 적혀 있다 (https://support.google.com/adsense/answer/9007336). 태그 설치는 KAN-197 3단계 (`docs/wiki/ads-web-adsense.md` §4) | `브라우저 웹의 광고 사업자 Google AdSense가 2·4·8·10항에 적혀 있다` |
| 9. 공유 기능 | payload에 점수·세션·음성이 없음 | `app/src/main/java/com/accentury/app/bridge/SharePayload.kt:28-31` (필드는 `imageUrl`·`text`·`webTestUrl` 셋), `web/src/share/shareResult.ts:57-59` (payload 필드가 `imageUrl`·`text`·`webTestUrl` 셋뿐, KAN-30 요구) | — |
| 9. 공유 기능 | 링크를 받은 사람은 자기 테스트를 시작 | `docs/wiki/app-links.md` §1 — 링크가 읽는 쿼리는 `c` 하나뿐 | — |
| 9. 공유 기능 | 전송 완료 알림(웹훅)에서 남기는 것은 전송 건수와 식별값뿐 | `backend/src/main/java/app/accentury/backend/share/KakaoShareWebhookController.java:100-112`(핸들러가 하는 일은 인증·중복 판별·카운터 1 증가 셋), `backend/src/main/resources/db/migration/V1__baseline.sql:259` (옛 V7. 「채팅방 해시(HASH_CHAT_ID), 채팅방 종류, 세션 id, 토큰, 점수는 어디에도 저장하지 않는다」), KAN-164 | — |
| 9. 공유 기능 | 웹훅 검증은 Admin 키 일치 확인 (서명 없음) | `backend/src/main/java/app/accentury/backend/share/KakaoWebhookAuth.java:96-112` (상수 시간 비교), 키 값은 로그에서 가려진다 (`backend/src/main/java/app/accentury/backend/common/LogMasking.java`의 `KAKAO_AK`·`NAMED_SECRET`) | — |
| 10. 광고 | 광고 절이 존재하고 동의·거부 경로가 있음 | 2026-09-07 팀 결정, KAN-196·KAN-197 | `맞춤형 광고 절이 있고 동의·거부 방법이 적혀 있다` |
| 10. 광고 | 「광고와 추적이 없습니다」는 이제 거짓 | 같은 결정으로 삭제한 문장. 되살아나는 것을 테스트가 막는다 | `"광고와 추적이 없습니다"가 남아 있지 않다` |
| 10. 광고 | 앱의 사업자는 Google LLC의 Google AdMob. 앱의 광고 자리는 분석 대기 중 전면 광고와 재응시 전 보상형 광고 둘 | 2026-09-11 팀장 결정 (KAN-196 1단계). 이 자리는 그 전까지 「확정 후 기재」였다 | `2항 제3자 제공 표와 10항 광고 절에 Google AdMob이 적혀 있다` |
| 10. 광고 | 브라우저 웹의 사업자는 같은 회사의 Google AdSense. 자리는 분석 대기 배너 1개뿐이고 **웹의 재응시에는 광고가 없다**. 처리 항목에 광고 쿠키와 브라우저 종류·버전이 는다 | 2026-09-13 팀장 결정 (KAN-197 1단계) — AdMob이 웹을 지원하지 않는다. 한 절이 앱·웹을 함께 말하므로 「보상형 광고를 끝까지 보신 뒤에 재응시」가 웹에도 걸리는 것처럼 읽히면 안 된다. 배선은 KAN-197 3~4단계 (`docs/wiki/ads-web-adsense.md` §5) | `브라우저 웹의 광고 사업자 Google AdSense가 2·4·8·10항에 적혀 있다` |
| 10. 광고 | 동의는 앱 첫 실행 때 동의 시트로 받고, 철회는 인트로 하단 개인정보처리방침 링크(KAN-177) 옆의 맞춤형 광고 링크에서 한다 | 2026-09-11 팀장 결정. UI는 KAN-196 2단계 웹 동의 시트(`webview-bridge.md` §8), 저장은 3·4단계 네이티브. 이 자리는 그 전까지 「도입 시 위치 확정」이었다 | `「확정 후 기재」 자리표시자가 남아 있지 않다` |
| 10. 광고 | 브라우저 웹도 첫 화면의 같은 안내 시트로 묻되 **선택은 브라우저 저장소**에 둔다. 철회는 같은 인트로 링크, 브라우저 쿠키 차단·삭제, Google 광고 설정(https://adssettings.google.com) 셋 | 2026-09-13 팀장 결정 (KAN-197 1단계). 시트는 같은 컴포넌트이고 문안 중 사업자·수집 항목만 갈린다 — `web/src/ads/adConsentText.ts`의 `AdVendor` (`docs/wiki/ads-web-adsense.md` §3). 브리지가 없는 실행이라 네이티브 저장소에 닿을 길이 없다 (`webview-bridge.md` §8.1). 저장 배선은 KAN-197 2단계에서 끝났다 (2026-09-13) — 키 `accentury:adConsent`, `web/src/ads/webAdConsentStore.ts`. 시트와 철회 링크는 **광고 ID가 든 브라우저 빌드**에만 뜬다 (팀 결정 2026-09-13, PR #109 리뷰 — `web/src/ads/adConsent.ts`의 `resolveAdConsentSource`가 `adSenseIdsFromEnv`를 함께 본다). 이 행의 방침 문장은 그대로 참이다: 광고가 나가는 웹의 서술이고, 광고가 없는 빌드는 그 절이 말하는 대상이 아니다 | `브라우저 웹의 광고 사업자 Google AdSense가 2·4·8·10항에 적혀 있다` |
| 10. 광고 | 거부 시 맞춤형이 아닌 일반 광고만. 이때 광고 식별자는 관심사 추정에는 쓰지 않고 빈도 제한·집계 보고·부정 사용 방지에만 쓴다. iOS ATT 거부 시 IDFA 미사용 | AdMob 비개인화 광고 요청(npa) — KAN-196 3단계(Android `AdRequests.kt`)·4단계(iOS `AdRequests.swift`)에서 배선 완료. npa=1은 식별자 전송을 막는 플래그가 아니다 — Google 「맞춤 광고 및 맞춤 설정되지 않은 광고」: 비맞춤 광고도 빈도 제한·집계 광고 보고·사기 및 악용 방지에 쿠키·모바일 광고 식별자를 쓴다 (https://support.google.com/admob/answer/7676680). 1단계 초안의 「광고 식별자 대신 IP 주소와 기기 정보」는 그래서 거짓이었고 리뷰 P1-4(2026-09-11)에서 고쳤다 | `맞춤형 광고 절이 있고 동의·거부 방법이 적혀 있다`·`비맞춤 광고에서 「광고 식별자 대신」이라고 적지 않는다` |
| 10. 광고 | 보유 기간은 Google 개인정보처리방침, 국외 이전은 4항 | Google LLC는 국외 사업자. 방침 링크는 4항의 `https://policies.google.com/privacy`와 같다 | — |
| 11. 안전성 확보 | HTTPS, 토큰 해시, 임시 파일 최소 권한, 로그 비식별, WAF, 관리자 토큰 | `backend/src/main/java/app/accentury/backend/session/TestSession.java:45-46`, `ai/app/tempstore.py:6-16`, `infra/modules/waf/main.tf:548,554`, `backend/src/main/java/app/accentury/backend/common/AccenturyProperties.java:29` (admin 시크릿) | — |
| 11. 안전성 확보 | 계정의 이메일, 이름, 생년월일과 로그인 토큰 원문을 로그에 남기지 않고, 로그에 세션과 계정의 대응을 남기지 않음. 로그인 토큰은 해시만 저장 | 명세서 §2.6 (KAN-223), `LogMasking`의 `rt_`와 JWT 패턴, `AuthApiTest.로그에_이메일_이름_토큰_원문이_남지_않는다`, `AuthApiTest.세션_생성_로그에_계정_id가_없고_계정_세션_여부만_남는다` |- |
| 11. 안전성 확보 | 후기의 이메일은 로그와 내부 알림에 남기지 않음 | `FeedbackService` javadoc (§2.6), `FeedbackSlackNotifier.message` (연락처는 유무만), `LogMasking`의 `EMAIL` 패턴이 마지막 관문 | `후기 이메일이 내부 알림에 실리지 않는다고 적혀 있다` |
| 12. 동의 방식 | 앱은 첫 로그인에서 방침 동의를 받고(동의 체크 전에는 로그인 버튼 비활성), 서버가 동의 시각과 버전을 계정에 기록. 서버는 게시 중인 버전만 받는다 | `AuthService.findOrCreateOnce` (가입 시 `privacyConsent`가 true이고 `privacyPolicyVersion`이 `AccenturyProperties.Auth.privacyPolicyVersion`과 정확히 같을 때만 계정 생성, 아니면 400 `AUTH_CONSENT_REQUIRED`), `AuthApiTest.방침_버전이_게시_중인_버전과_다르면_400이고_계정이_생기지_않는다`. 로그인 화면은 KAN-224 (`Accentury_App` 레포) | `5, 6, 7, 12항이 계정 기준으로 다시 쓰였다` |
| 12. 동의 방식 | 웹은 동의 화면을 따로 두지 않되 광고는 별도 동의 | KAN-2 「동의 화면 범위 제외」 결정 + 2026-09-07 광고 결정의 부분 번복 (6항 참조) |- |
| 12. 동의 방식 | **후기의 회신 이메일은 이용자가 직접 적는 선택 입력이고, 시트 안내 + 방침 링크를 읽은 뒤의 [보내기]를 동의로 본다** | 수집 고지는 입력칸 옆에 있다 — `web/src/feedback/feedbackText.ts`의 `FEEDBACK_EMAIL_HINT`(「답변을 원하시면 적어 주세요. 후기 확인에만 쓰고 다른 데 쓰지 않아요.」)와 그 아래 `FEEDBACK_DETAIL_LEAD` + `legal/PrivacyPolicyLink`. 광고 동의처럼 별도 시트를 세우지 않은 이유: 이메일은 **안 적으면 수집이 없는** 선택 입력이라, 적는 행위 자체가 의사 표시다. 12항 첫 문단의 「동의 화면을 따로 두지 않는다」와 어긋나지 않는다 | — |
| 13. 보호책임자 | 이성주, team2pl1@gmail.com | 2026-09-07 팀 결정 | `연락처가 있다 (Play·App Store 심사가 요구하는 항목)` |
| 14. 시행일 | 시행일 = 방침 버전 = `2026-10-04`. 머리와 14항 두 자리, `accentury-policy-version` 메타, backend `AccenturyProperties.Auth.PRIVACY_POLICY_VERSION`이 한 값 | KAN-269 (2026-10-04, 음성 저장 선택 동의 개정본). 그 앞 값은 KAN-240의 `2026-09-29`였다(2026-09-29 사용자 결정: 계정 절이 들어간 개정본에 새 버전을 붙이고 시행일도 같은 날로). 앱 두 곳의 상수(`LoginScreenState.kt`, `LoginScreenState.swift`)도 같은 값이다 | `게시 HTML의 방침 버전이 BE 설정 기본값과 같다`, `application.yml이 방침 버전을 덮어쓰지 않는다`, `시행일 표기 두 자리가 방침 버전과 같고…` |
| 전 절 | 법정 필수 절이 빠지지 않음 | 「개인정보 보호법」 제30조 + 실제 처리(국외 이전, 자동 수집 장치, 광고) | `법정 필수 절이 모두 있다` |
| 페이지 전체 | 외부 CSS·글꼴·스크립트 0 | KAN-133 AC "본문 교체는 S3 업로드 하나" | `외부 자원을 하나도 쓰지 않는다` |
| 페이지 전체 | noindex 없음, 자리표시자 문구 없음 | KAN-133 → KAN-176 인계 | `자리표시자를 막던 noindex가 없다`·`자리표시자 문구가 남아 있지 않다` |

## 2. 스토어 신고 대조표

Play 데이터 안전 답안의 정본은 `Accentury_App` 레포의 `docs/wiki/play-store-listing.md` §5, §6이다
(KAN-174, App PR #5로 2026-09-24 병합). 레포 분리(KAN-221) 뒤라 그 문서는 이 레포에서 고치지 않는다.
아래 「확정 답안」 열이 그 문서 §5, §6과 같은지는 App 레포에서 대조한다.
App Store 라벨(KAN-175)의 정본도 이제 `Accentury_App` 레포 `docs/wiki/app-store-listing.md` §6이다. 아래 App Store
표는 그 문서가 생기기 전의 2026-09-11 스냅샷이라, 어긋나면 §6이 이긴다.

답안은 2026-09-11 사업자 확정(Google AdMob)으로 굳었다. KAN-174 문서는 광고 도입 결정 이전에
쓰였으므로 지금 답과 확정 답안이 어긋나는 자리가 넷 있다.

| Play 데이터 안전 질문 | 지금 KAN-174 답 | 방침 본문의 대응 | 확정 답안 (2026-09-11) |
|---|---|---|---|
| 데이터를 수집·공유하나 | 예 | 1항 표 전체 | 그대로 예 |
| 제3자와 공유하나 | **아니요** ("광고 네트워크로 보내는 곳도 없다") | 2항 제3자 제공 표 — Google LLC (Google AdMob) 1행 | **예**. 제공 항목은 광고 식별자·IP·기기 정보·노출/클릭 기록, 받는 곳은 Google AdMob |
| 광고 ID 수집 | **수집하지 않음** (`google_analytics_adid_collection_enabled=false`) | 8항·10항 — 광고 SDK가 광고 식별자를 사용 | **수집함**. 목적은 「광고 또는 마케팅」. Analytics 쪽 false는 그대로 두고 AdMob SDK 몫을 따로 신고 |
| 광고 포함 (§6 콘텐츠 등급) | **아니요** | 10항 「서비스에는 광고가 표시됩니다」 | **예**. 콘텐츠 등급 설문도 다시 제출 |
| 음성 또는 사운드 녹음 | 수집됨, 「일시적으로만 처리되며 저장되지 않음」 | 1항 음성 행, 「음성 녹음」 산문, 「음성 저장과 AI 모델 학습 활용 (선택 동의)」 | **바뀜 (KAN-269, 2026-10-04)**. 선택 동의한 응시의 음성을 보관하므로 「일시적으로만 처리」 체크를 푼다. App Store 라벨의 오디오 데이터도 「수집」으로 바뀐다. 답안 정본은 `Accentury_App` 레포 `docs/wiki/play-store-listing.md` §5와 `docs/wiki/app-store-listing.md` §6이고, 콘솔 양식은 계정 소유자가 직접 고친다 |
| 앱 상호작용 | 수집됨, 목적 분석 | 1항 이용 통계 이벤트 행, 4항 | 변화 없음 |
| 비정상 종료 로그 | 수집됨, 목적 분석 | 1항 비정상 종료 로그 행, 4항 | 변화 없음 |
| 삭제 요청 방법 제공 | 아니요 | 6항 | **바뀜 (KAN-240)**. 앱 계정은 삭제(탈퇴)를 요청할 수 있다 (6항 임시 절차, 앱 안 탈퇴는 KAN-241). Play는 계정을 만드는 앱에 계정 삭제 방법을 요구한다 |
| 개인 정보(이메일, 이름, 생년월일, 성별) 수집 | 신고 없음 | 1항 「계정」, 3항 제공자 표 | **바뀜 (KAN-240)**. 앱 로그인(KAN-224) 출시 전에 Play 데이터 안전과 App Store 라벨에 계정 항목을 신고한다 |

App Store(KAN-175)도 같은 이유로 세 줄이 바뀐다. 이 표는 KAN-175 문서(`app-store-listing.md` §6)로 옮겨졌고, 계정 항목(KAN-240)의 신고는 그 문서에서 고친다.

| App Store 개인정보 라벨 | 지금 기준 | 확정 답안 (2026-09-11) |
|---|---|---|
| 「추적(Tracking)」 해당 여부 | 해당 없음 — IDFA 미수집, ATT 프롬프트 불필요 (`docs/wiki/analytics.md` §8) | **해당함**. ATT 프롬프트 필요, 「사용자를 추적하는 데 사용되는 데이터」에 식별자·사용 데이터 신고. ATT 거부 시 IDFA 미사용 |
| 식별자 › 기기 ID | 신고 없음 | **신고함** (AdMob SDK의 IDFA — 이용자가 ATT를 허용한 경우) |
| 사용 데이터 › 제품 상호작용 | 「사용자와 연결되지 않음」 | AdMob SDK 몫은 「추적에 사용됨」으로 별도 표기 |
| 진단 › 비정상 종료·성능 | 「사용자와 연결되지 않음」 | 변화 없음 |

## 3. prod 게시 게이트

**staging은 지금 그대로 게시해도 된다.** `scripts/publish-privacy.sh staging`에 막는 조건이 없고,
자리표시자가 남은 문서를 심사관이 보지 않는다.

prod 게시(`scripts/publish-privacy.sh prod`) 전에는 아래를 전부 닫는다.

| # | 자리 | 본문 행 | 닫는 조건 |
|---|---|---|---|
| 1 | 2항 제3자 제공 표 「광고 사업자 (확정 후 기재)」 | 294 | **완료 (2026-09-11, KAN-196 1단계)** — Google LLC (Google AdMob) |
| 2 | 4항 광고 국외 이전 「(확정 후 기재)」 | 361~363 | **완료 (2026-09-11)** — Google LLC 행에 합침 |
| 3 | 10항 「광고 사업자(확정 후 기재)」 | 499 | **완료 (2026-09-11)** |
| 4 | 10항 동의 설정 위치 「(도입 시 위치 확정)」 | 530 | **완료 (2026-09-11)** — 첫 실행 동의 시트 + 인트로 하단 링크로 확정 서술. UI 자체는 KAN-196 3단계가 만든다 |
| 5 | 10항 「4항과 이 항, 2항의 제3자 제공 표를 함께 채웁니다 (확정 후 기재)」 | 539~541 | **완료 (2026-09-11)** — 확정 문장으로 교체 |
| 6 | 시행일 「정식 게시일에 기재합니다」 | 머리, 14항 | **완료 (2026-09-29, KAN-240)** — 자리표시자는 남아 있지 않다. 시행일 = 방침 버전이다. prod 게시일이 이 날짜와 다르면 두 자리, 버전 메타, backend 상수, 앱 두 곳의 상수를 함께 게시일로 올린다 (4절 「방침 버전」) |
| 9 | 계정 PII 반영 (KAN-240) | 1, 3, 5, 6, 7, 11, 12, 14항 | **본문 반영 완료 (2026-09-29)**. 앱 로그인(KAN-224)을 prod에 내기 전에 이 본문이 prod에 게시돼 있어야 하고, 스토어 신고(2절의 KAN-240 두 행)도 그 전에 고친다 |
| 7 | 스토어 답안 일치 | — | 정본은 `Accentury_App` 레포 — App Store 라벨은 `docs/wiki/app-store-listing.md` §6 (KAN-175, 2026-09-23), Play 데이터 안전은 `docs/wiki/play-store-listing.md` §5·§6 (KAN-174). 둘이 2절 「확정 답안」대로 갱신됐는지 그 레포에서 확인한다 |
| 8 | 10항의 웹 광고 고지와 실제 배선 일치 | 10항 (배너 1개·웹 재응시 광고 없음) | **코드와 배포 배선은 끝났다** (2026-09-13, KAN-197 4단계) — 대기 화면 배너(`web/src/ads/AdSlot.tsx`), 빌드 변수와 `ads.txt` 생성 스텝(`.github/workflows/web-deploy.yml`), 태그 있는 빌드·없는 빌드 양쪽의 완주 E2E. 남은 것은 코드가 아니라 **운영 절차**다: AdSense 사이트 승인 → 광고 단위 생성 → `gh variable set` 두 개 → Release 배포. 그 넷이 끝나 `https://accentury.app/ads.txt`가 열리고 대기 화면에서 광고 요청이 나가야 고지가 사실이 된다. 그때까지는 방침이 앞서 있다 (`docs/wiki/ads-web-adsense.md` §10 체크리스트) |

1~5의 자리표시자는 확정 전에는 계약 테스트로 막지 않았다 — 막으면 확정 전 단계의 본문을 커밋할
수 없어 문서가 코드보다 뒤처지기 때문이다. 2026-09-11 확정 뒤로는 반대로 되살아나는 쪽을
막는다 (`「확정 후 기재」 자리표시자가 남아 있지 않다`, `privacy.test.mjs` 머리주석).

## 4. 변경 절차

코드에 적힌 사실이 바뀌면 순서는 이렇다.

1. **계약 테스트가 깨진다** — `node --test 'infra/privacy/*.test.mjs'`. 깨지지 않는 사실이면 2번부터 사람이 시작한다.
2. **본문을 고친다** — `infra/privacy/privacy.html`.
3. **이 문서의 매핑을 갱신한다** — 1절의 해당 행. 근거 경로와 행 번호까지.
4. **스토어 답안을 갱신한다** — `Accentury_App` 레포 `docs/wiki/play-store-listing.md` §5, §6 (KAN-174)과 `Accentury_App` 레포 `docs/wiki/app-store-listing.md` §6 (KAN-175). 신고와 실제 동작이 어긋나는 것이 정책 위반이다.
5. **게시한다** — `scripts/publish-privacy.sh staging` → 확인 → `scripts/publish-privacy.sh prod` (3절 게이트를 먼저 닫는다).

예를 들어 `infra/modules/fargate/variables.tf`의 `log_retention_days`를 14에서 30으로 올리면
본문에서 고칠 자리는 두 곳이다 — **1항 표의 「서버 운영 로그」 행**과 **5항 파기 목록의 로그 줄**.
계약 테스트는 로그 일수를 붙들지 않으므로 이때는 아무것도 깨지지 않는다. 이 표가 유일한 경보다.

반대로 음성 즉시 삭제·30분 청소·24시간 보존·서울 리전을 건드리면 테스트가 먼저 깨진다.
1항 표는 행 단위로 붙들려 있어(`1항 표의 보유 기간이 행마다 코드와 맞는다`), 보유 기간 칸의
수치나 기준점이 바뀌면 다른 절에 같은 낱말이 남아 있어도 그 행에서 걸린다.

### 방침 버전 (KAN-240)

가입 동의 버전은 네 곳이 한 값이어야 한다. 방침을 개정하면 넷을 함께 올린다.

| 자리 | 값의 쓰임 |
|---|---|
| `infra/privacy/privacy.html`의 `<meta name="accentury-policy-version">`와 시행일 두 자리 | 이용자가 읽는 문서의 버전 |
| backend `AccenturyProperties.Auth.PRIVACY_POLICY_VERSION` (`auth.privacyPolicyVersion`의 기본값) | 서버가 받는 유일한 동의 버전. 다르면 400 `AUTH_CONSENT_REQUIRED` |
| `Accentury_App` 레포 Android `app/src/main/java/com/accentury/app/auth/LoginScreenState.kt`의 `PRIVACY_POLICY_VERSION` | Android 앱이 가입 때 보내는 값 |
| `Accentury_App` 레포 iOS `ios/AccenturyCore/Sources/AccenturyCore/Auth/LoginScreenState.swift`의 `privacyPolicyVersion` | iOS 앱이 가입 때 보내는 값 |

앞의 둘은 `privacy.test.mjs`가 대조하고, 앱 두 곳은 레포가 달라 테스트로 묶지 못한다. 서버 배포와 앱 상수가
어긋나면 그동안 앱의 **새 가입**이 전부 400이다(재로그인은 동의 필드를 보지 않아 영향이 없다). 그래서 순서는
앱 상수를 먼저 올린 빌드를 준비하고, 서버와 방침 게시를 같은 날 한다. 개정 뒤 기존 계정의 재동의 흐름은
아직 없다 (명세서 §7 미결, KAN-240 범위 제외).

## 5. 결정 기록

| 날짜 | 결정 | 본문에 남은 자리 |
|---|---|---|
| 2026-10-01 | **스토어 답안의 정본을 `Accentury_App` 레포 문서로 넘긴다 (KAN-175 심사 제출 준비).** 시행일은 KAN-240이 2026-09-29로 이미 기재했고 계약 테스트가 방침 버전·backend 상수와 함께 묶어 두어, 날짜를 다시 올리는 것은 prod 게시일에 네 자리와 앱 두 상수를 같이 올릴 때의 일이다. 이 회차는 문서 포인터만 고쳤다 | 3절 게이트 6·7행, 4절 4단계, 2절 머리 (본문 무변경) |
| 2026-09-01 | 사용자 음성을 S3에 저장하지 않는다 | 1항 「음성은 데이터베이스나 S3 같은 영속 저장소에 저장하지 않습니다」 |
| 2026-09-28 | **staging 게시본에만 학습 수집 예외를 적는다 (KAN-239).** staging 학습 버킷(KAN-201)이 고지와 달리 누구의 음성이든 모으고 있어, 수집 대상을 동의한 테스터 계정으로 한정하고 세션을 가명화한 뒤 그 사실을 staging 본문에 적었다. prod 게시본은 무변경이고(위 행의 문구 그대로) 파일은 하나로 두되 `staging-only` 주석 블록을 게시 스크립트가 prod에서 잘라낸다. 그 전까지 쌓인 382개 객체는 동의 여부를 확인할 수 없어 전량 파기했다 | 1항 음성 산문 뒤 staging-only 블록 |
| 2026-10-04 | **KAN-239의 수집 한정을 되돌린다.** 사용자 결정으로 동의 테스터 한정, 세션과 작업 ID 가명화, 수명주기 만료일, 버킷 정책과 학습 읽기 역할, 스위치 이름(`consented-bucket`)을 KAN-201 상태로 되돌렸다. staging은 익명 웹 세션을 포함한 모든 음성을 다시 저장한다. staging 본문의 학습 수집 고지는 고치지 않기로 해 본문(동의한 테스터만, 가명, 보유 기간)과 구현이 어긋난 채다 | 1항 음성 산문 뒤 staging-only 블록 (무변경) |
| 2026-10-04 | **음성 저장을 두 환경 공통의 선택 동의로 바꾼다 (KAN-269).** 위 행의 「본문과 구현이 어긋난 채」를 이 결정이 닫는다. 음성은 공유 음성 전용 버킷 `accentury-voice-<account>`에 접두사 `staging/`과 `prod/`로 나눠 저장한다. 저장 대상은 음성 저장과 AI 모델 학습 활용에 선택 동의한 응시뿐이고(웹과 앱 모두), 이 동의는 필수 방침 동의와 별개다. 세션 ID와 작업 ID는 가명화하지 않고 그대로 둔다. 앱 계정은 계정 ID와 음성 세션 ID를 잇는 연결 표를 두어 세션 만료와 탈퇴 뒤에도 남긴다. **prod도 이제 음성을 저장한다** - 2026-09-01 결정(「사용자 음성을 S3에 저장하지 않는다」)은 선택 동의를 하지 않은 응시에만 남는다. 방침 버전을 `2026-10-04`로 올렸다. staging 전용 고지 블록은 비웠고 자르는 경로는 남겼다 | 1항 표 「음성 녹음」, 「음성 저장과 학습 활용 (선택 동의)」, 「테스트 세션」 행과 「음성 녹음」 산문, 새 절 「음성 저장과 AI 모델 학습 활용 (선택 동의)」, 「계정」 절, 3항 AWS 행과 산문, 5, 6, 7, 11, 12, 14항, 머리말 |
| 2026-09-29 | **앱 계정 PII를 방침에 반영한다 (KAN-240).** 방침 버전을 `2026-09-29`로 올리고 시행일도 같은 날로 적었다. 서버는 가입 동의를 이 버전과 정확 일치로만 받는다(형식 정규식 검사를 대체). 탈퇴 API(KAN-241) 전의 임시 탈퇴 절차는 보호책임자 이메일 요청이다. 성별, 이름, 이메일의 처리 목적은 회원 관리와 응시자 통계로 적었다(코드에는 아직 활용처가 없다). IdP 표는 티켓대로 3항에 뒀다. 세션 생성 로그에서 `userId`를 뺐다 | 1항 표 「계정 (앱)」, 「로그인 토큰」 행과 「계정」 절, 3항 제공자 표, 5, 6, 7, 11, 12, 14항 |
| 2026-09-01 | 방침 URL은 `/privacy.html` (확장자 없는 경로는 SPA 재작성에 먹힌다) | 14항 URL, `infra/privacy/README.md` |
| 2026-09-07 | 개인정보 보호책임자 이성주, 문의처 team2pl1@gmail.com | 13항 |
| 2026-09-07 | 시행일은 초안 날짜가 아니라 정식 게시일에 기재한다 | 102·571행 (prod 게이트) |
| 2026-09-07 | **1차 배포에 맞춤형 광고를 넣는다 (앱과 웹 모두).** 사업자 미정 | 1항 광고 행, 2항 제3자 제공 표, 4항, 8항, 10항 전체, 12항 |
| 2026-09-07 | PR #89 리뷰: 분기 뒤 Dev에 들어온 **KAN-164(카카오 공유 웹훅 수신)**를 본문에 반영한다 — 저장하는 것은 전송 완료 수와 중복 판별용 식별값(7일)뿐 | 1항 표 「익명 통계」·「공유 전송 알림 기록」 행과 산문, 5항 파기 목록, 9항 |
| 2026-09-07 | 위 결정으로 KAN-2의 「동의 화면 범위 제외」가 부분 번복됐다 — 개인정보 수집·이용 동의 화면은 여전히 두지 않지만, **맞춤형 광고 동의 UI는 별도로 둔다** | 12항 「다만 맞춤형 광고는 별도로 동의를 받습니다」 |
| 2026-09-11 | **광고 사업자는 Google LLC의 Google AdMob** (Firebase 프로젝트 공유, KAN-196 팀장 결정). 광고 자리는 분석 대기 화면 전면 광고와 결과 화면 「다시 테스트하기」 보상형 광고 둘. 동의는 인트로 첫 실행 동의 시트, 철회는 인트로 하단 방침 링크(KAN-177) 옆 링크. 거부 시 비맞춤 광고(npa)만, iOS ATT 거부 시 IDFA 미사용. 아동 대상 아님 유지 | 1항 광고 행, 2항 제3자 제공 표, 4항 Google LLC `<dl>`, 10항 도입·거부와 철회·보유 기간과 국외 이전 |
| 2026-09-13 | **브라우저 단독 실행의 광고 사업자는 Google LLC의 Google AdSense** (KAN-197 팀장 결정 — AdMob은 웹을 지원하지 않는다). 자리는 분석 대기 화면 배너 1개이고 웹의 재응시에는 광고가 없다. 동의는 웹이 묻고 브라우저 저장소에 두며, 앱 WebView 안에서는 이 웹 광고 태그를 설치하지 않는다 (정책). 정본은 `docs/wiki/ads-web-adsense.md` | 2·4·8·10항 |
| 2026-09-15 | **결과 화면에서 이용 후기를 받는다 (KAN-211).** 별점(선택)·본문(1~500자, 필수)·회신 이메일(선택)을 세션당 1건, **1년** 보관하고 저장 시점 등급·테스트/점수 버전·플랫폼·트래픽을 함께 복사한다(세션과 FK 없음). 저장이 커밋되면 슬랙 `#feedback`으로 알림이 나가되 **이메일은 싣지 않는다**. 계측은 `feedback_opened`·`feedback_submitted(rating)` 둘뿐. **회신 이메일이 이 서비스가 받는 유일한 개인 식별 정보라**, 6항의 「개인을 알아볼 수 있는 값이 없다」 전제에 예외를 더하고 그 주소로 삭제·열람을 요청할 경로를 적었다. Slack은 3항 위탁이 아니라 **4항 국외 이전**으로 분류했다(위 4항 행의 근거). 정본은 [`feedback.md`](feedback.md) | 1항 표와 그 아래 「이용 후기」 절, 4항 Slack `<dl>`, 5항 파기 목록, 6항 권리 마지막 문단, 11항 목록, 12항 |
| 2026-09-11 | KAN-196 리뷰 반영. (P1-4) 비맞춤 광고 문장에서 「광고 식별자 대신」을 뺐다 — npa는 식별자를 빈도 제한·집계·부정 방지에 계속 쓴다(위 10항 근거 행). (P2-1) 10항 「앱과 웹에」 → 「앱에」 — 웹 광고는 KAN-197에서 사업자·형식이 정해지면 그때 적는다. 2026-09-07 「앱과 웹 모두」 결정 자체는 유효하고 웹 몫의 고지만 미룬 것이다 | 10항 도입 문장, 10항 「동의」 문단, `privacy.test.mjs` 「광고 식별자 대신」 가드 |

## 6. 팀 확인이 필요한 것

- **Crashlytics 90일, GA4 2개월** — 둘 다 콘솔 기본값으로 적었고 레포에는 근거가 없다. 콘솔에서 실제 설정을 확인해 다르면 1항 표와 4항을 고친다.
- **운영 주체 표기** — 지금은 「Accentury 팀(이박이일)」이다. 사업자 등록이 없어 상호·대표자·주소를 적지 않았다. 광고 수익이 생기면 사업자 표기가 필요한지 확인해야 한다.
- **선택 동의 음성의 보유 기간 문구** (KAN-269) - 본문은 「학습 목적 달성 시까지」로 적었고 자동 만료가 없다. 이 문구는 잠정이고 개인정보 보호책임자의 검토를 기다린다. 검토 결과에 따라 1항 표, 선택 동의 절, 5항 파기 목록, 계약 테스트의 해당 문자열을 함께 고친다.
- **웹의 만 14세 미만** (KAN-269) - 웹에는 계정과 생년월일 확인이 없어, 음성 저장 선택 동의의 문안이 만 14세 이상 확인을 겸한다(7항). 본인 확인 수단이 따로 없다는 한계는 그대로다.
- **방침 버전 날짜와 실제 prod 게시일** (KAN-269) - 버전과 시행일을 `2026-10-04`로 적었다. prod 게시일이 이 날짜와 다르면 게시일로 다시 맞춘다(4절 「방침 버전」의 네 자리와 앱 두 상수).
- **앱 상수와 서버 배포의 순서** (KAN-269) - 서버는 가입 동의의 방침 버전이 게시 버전과 정확히 같지 않으면 가입을 거절한다. `2026-10-04` 상수를 담은 앱이 서버 배포보다 먼저이거나 함께 나가야 한다. 어긋난 동안에는 앱의 새 가입이 막힌다.
- **연령 기준 세 가지의 관계** - Play 타겟층은 18세 이상만 신고했고(`Accentury_App` 레포 `docs/wiki/play-store-listing.md` §6, 「누구를 겨냥했나」이지 연령 제한이 아니다), 가입은 만 14세 이상만 받는다(7항, `ProfileRules.MINIMUM_AGE`). App Store 등급은 `app-store-listing.md`에서 정한다. 셋이 서로 모순은 아니지만, 14~17세 가입을 받는 것이 Play 18세 이상 신고와 함께 문제 되지 않는지 스토어 신고를 고칠 때 확인한다.
- **만 14세 미만이 추가 정보 단계에서 거절될 때 이미 만들어진 계정 행** - IdP가 생년월일을 주지 않은 경우(구글, 애플) 계정 행은 로그인 때 만들어지고, 생년월일은 추가 정보 화면에서 거절된다. 이때 생년월일은 저장하지 않지만 IdP가 준 이메일과 이름은 계정 행에 남는다. 7항은 「그 생년월일은 저장하지 않는다」까지만 약속한다. 거절된 계정 행을 지울지는 탈퇴(KAN-241)와 함께 정한다.
- **이메일로 본인 확인이 안 되는 계정의 탈퇴 요청** (KAN-240 Codex 리뷰 P2) - 애플 전달용 주소 사용자와 이메일 등록 전에 멈춘 계정은 6항의 「계정 이메일로 요청」을 따를 수 없어, 방침은 「확인 방법을 따로 안내」까지만 약속한다. 앱 안 탈퇴(KAN-241)가 나오면 사라지는 문제지만, 그 전에 요청이 오면 무엇으로 본인임을 확인할지 개인정보 담당이 정한다.
- **성별과 이름의 활용처** - 방침은 목적을 「응시자 구성 파악과 서비스 개선」, 「회원 식별과 문의 응대」로 적었지만 코드에 아직 쓰는 곳이 없다. 수집 최소화 관점에서 계속 받을지 개인정보 담당이 확인한다.
- **광고 사업자** — 2026-09-11 앱은 Google AdMob, 2026-09-13 브라우저 웹은 Google AdSense로 확정돼 3절의 자리표시자 1~5를 닫았다. 남은 게이트는 시행일(6), 스토어 답안 일치(7), 웹 광고 배선 일치(8)다.
- **Slack을 국외 이전으로 분류한 것이 맞는지 최종 확인** (KAN-211) — 후기 본문에 개인정보가 섞일 **가능성**을 근거로 4항에 적었다. 위탁(3항)으로 보는 해석도 성립하고, 그 경우 4항 `<dl>`을 3항 표의 한 행으로 옮긴다. 둘 다 적는 길(Firebase처럼)도 있다. 고지가 빠지는 쪽이 아니라 **어느 절에 적는가**의 문제라 게시 전까지 바꿀 수 있다.
- **prod 게시 전 시행일** (KAN-211과 무관하게 열려 있는 3절 게이트 6번) — 본문 머리와 14항 두 자리를 게시 당일 날짜로 바꾼다. 이 티켓이 본문을 늘렸어도 그 게이트는 그대로다.
- **후기 삭제 요청의 처리 주체** — 6항이 약속한 「지체 없이 삭제」는 지금 운영자 수작업이다(DB 직접 조회). 요청이 실제로 오기 시작하면 누가 받고 얼마 안에 처리하는지를 정해야 한다.
- **웹 광고와 스토어 신고의 관계** — 2절의 확정 답안은 앱(AdMob) 기준이다. AdSense는 웹 전용이라 Play·App Store 신고가 달라지지 않지만, 스토어 문서를 갱신할 때 「광고 ID 수집」 답이 앱 몫이라는 것을 흐리지 않게 적는다.
