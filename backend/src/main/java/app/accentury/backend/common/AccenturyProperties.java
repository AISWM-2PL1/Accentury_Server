package app.accentury.backend.common;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 서비스 전역 설정 - 세션과 업로드, 분석, 결과 정책.
 * <p>
 * <b>활성 테스트 버전과 점수 버전은 여기 없다</b> (KAN-26). 발행 입력이 DB로 확정되면서
 * (2026-08-09, §6) 활성 버전은 {@code active_test_version} 한 행이 정본이 됐고, 점수 버전은
 * 그 정의가 선언한 값을 따른다 - 세션은 생성 시점의 두 버전에 고정된다 (§5.4). 설정 파일과
 * DB에 같은 값이 둘 있으면 어긋날 수 있는데, 어긋난 배포를 기동 검사로 막느니 정본을 하나로
 * 줄이는 편이 낫다는 판단이다.
 *
 * @param session        익명 세션 정책 (KAN-9)과 세션 생성 요청 제한 (KAN-28)
 * @param analysis       분석 상태 폴링 정책 (KAN-23, KAN-24)과 AI 회로 차단 정책 (KAN-28)
 * @param upload         음성 업로드 요청 제한 (KAN-23, KAN-28)과 임시파일 정책 (KAN-27)
 * @param vocab          어휘 답안 요청 제한 (KAN-28)
 * @param completion     완료 API 요청 제한 (KAN-16)
 * @param cors           웹 테스트 CORS allowlist (KAN-23, KAN-31)
 * @param result         결과 응답의 등급별 자산과 공유 URL (KAN-25)
 * @param analytics      익명 집계 카운터의 일자 경계와 조회 상한 (KAN-106)
 * @param admin          운영자 전용 API(§6)의 공유 시크릿 (KAN-106, KAN-26)
 * @param share          카카오톡 공유 웹훅의 검증 키와 수신 기록 보존 기간 (KAN-164)
 * @param feedback       결과 화면 이용 후기의 요청 제한과 보존 기간 (KAN-211)
 * @param training       staging 전용 학습 데이터 S3 (KAN-201) - 버킷이 없으면 저장 코드가 호출되지 않는다.
 * @param auth           앱 계정 인증 (KAN-223) - Access JWT, Refresh 회전, IdP 검증 설정
 * @param learning       단어 학습 API의 요청 제한 (KAN-265)
 * @param translation    사투리 텍스트 번역의 LLM 호출과 prod 번역 기록 버킷 (KAN-266)
 * @param trustedProxies 요청 제한의 기준 IP를 정할 때 신뢰하는 프록시 대역 (KAN-28, §2.5).
 *                       CIDR 또는 단일 IP 목록이고, 직접 접속한 상대가 이 목록에 들어야만
 *                       {@code X-Forwarded-For}를 읽는다. 비어 있으면 헤더를 무시하고 접속 IP만
 *                       쓴다 - 프록시 없는 배포에서 헤더 위조로 제한을 우회하지 못하게 하는
 *                       안전한 기본값이다. <b>ALB 뒤에 배포할 때는 반드시 지정해야 한다</b> -
 *                       지정하지 않으면 모든 사용자가 ALB IP 하나를 공유해 서로의 한도를 깎는다.
 */
@ConfigurationProperties(prefix = "accentury")
public record AccenturyProperties(Session session,
                                  @DefaultValue Analysis analysis, @DefaultValue Upload upload,
                                  @DefaultValue Vocab vocab,
                                  @DefaultValue Completion completion, @DefaultValue Cors cors,
                                  @DefaultValue Result result, @DefaultValue Analytics analytics,
                                  @DefaultValue Admin admin, @DefaultValue Share share,
                                  @DefaultValue Feedback feedback,
                                  @DefaultValue Training training,
                                  @DefaultValue Auth auth,
                                  @DefaultValue Learning learning,
                                  @DefaultValue Translation translation,
                                  @DefaultValue List<String> trustedProxies) {

    /**
     * @param ttl                세션 토큰 수명 - 테스트 소요 5분의 여유 배수인 30분 (§2.1, §7)
     * @param rateLimitPerMinute IP당 분당 세션 생성 허용 횟수 (§2.5, KAN-28). 인증이 없는
     *                           엔드포인트라(§2.1) IP가 유일한 키다. 정상 응시자는 응시 1회당
     *                           1건이고 재응시해도 분당 1~2건이므로, 공유 IP(학교, 카페 NAT) 뒤의
     *                           동시 시작을 덮는 여유 배수로 잡는다. 임계치는 부하 테스트 후
     *                           확정한다 (§7, KAN-40).
     */
    public record Session(Duration ttl, @DefaultValue("30") int rateLimitPerMinute) {
    }

    /**
     * @param pollAfterMs          다음 상태 조회까지 클라이언트가 기다릴 시간 - 서버가 통제하고,
     *                             부하 상승 시 값을 올려 폴링 압력을 줄인다 (§5.3).
     * @param congestedPollAfterMs 혼잡 판정 시의 폴링 간격 - 분석이 밀리면 폴링이 부하를
     *                             증폭하므로(§5.3 - 대기 체류 20배) 서버가 스스로 간격을 올린다 (KAN-24).
     * @param congestionThreshold  혼잡 판정 기준 - 전 인스턴스의 진행 중(PROCESSING) 분석 작업 수가
     *                             이 값 이상이면 {@code congestedPollAfterMs}를 반환한다 (KAN-167).
     *                             기본 6은 AI의 1분 처리량이다 (실모델 1건 10초, KAN-57 c7i.xlarge) -
     *                             그만큼 쌓이면 대기열이 1분을 넘기 시작한다 (KAN-172). 이전 30은
     *                             워커 4개 전제라 대기열 5분이 쌓여야 켜졌다.
     * @param congestionCacheTtl   혼잡 판정이 DB의 PROCESSING 건수를 다시 세지 않고 쓰는 시간
     *                             (KAN-167). 판정은 모든 상태 응답 경로에 놓이므로 폴링마다 세면
     *                             판정 자체가 폴링 증폭에 얹힌다. 기준 폴링 간격(800ms)과 같은
     *                             자릿수인 1초면 인스턴스당 초당 count 1회로 묶이고 판정 지연은
     *                             폴링 한 번 분량이다 - 밀림은 초 단위 추론이 수십 건 쌓여야 생겨
     *                             그 안에 임계치를 넘나들지 않는다. 0이면 매번 센다.
     * @param retention            분석 작업 보존 기간 - 세션, 결과와 같은 24시간 (§5.5)
     * @param processingTimeout    실행 잔류 한도 - 워커가 실행을 시작하고도(startedAt) 종결을
     *                             못 남긴 작업을 이 시간 뒤 RETRYABLE_FAILED로 정리한다 (KAN-24).
     *                             AI 호출 재시도 전체 소요(aiTimeout x 시도 횟수 + 대기)보다 길어야 한다.
     *                             큐 대기 시간은 세지 않는다 (Codex sol 리뷰 P1). 기본 300초는
     *                             85초 x 3회 + 백오프 0.9초 = 255.9초 위의 반올림이다 (KAN-172).
     * @param queuedTimeout        큐 유실 한도 - 실행을 시작하지 못한 채 이 시간이 지난 작업의
     *                             정리. 접수와 실행 사이 프로세스 사망 대비다. 정상 큐 소진
     *                             시간보다 길게, 복구가 사용자에게 보이도록 세션 TTL보다는
     *                             짧게 잡는다 (Codex sol 리뷰 P2).
     * @param aiBaseUrl            AI 분석 서버(FastAPI) 주소 (§4.1). 미설정이면 분석을 전달하지
     *                             않는 개발 모드다 - 작업은 PROCESSING으로 남다가 타임아웃 처리된다.
     * @param aiTimeout            AI 호출의 읽기 타임아웃 - 기본 85초 (KAN-172). 연결 타임아웃은 이 값과
     *                             따로 5초 고정이다 ({@code AnalysisDispatchConfig.AI_CONNECT_TIMEOUT}) -
     *                             같이 두면 미도달 연결 실패가 읽기 타임아웃으로 잘못 접힌다. 실모델 1건 P95는
     *                             11.1초(KAN-57 c7i.xlarge, bf16 + MFA align_one)지만 AI는 한 번에 하나만
     *                             추론하고 그 상한(75초)은 lock 대기와 워커 재적재까지 포함하므로, 한 AI
     *                             호스트에 겹치는 호출 6건(태스크당 워커 3개 x 롤링 배포 중 태스크 2개,
     *                             KAN-272)의 6 x 11초 = 67초와 재적재 31초 + 추론 11초 = 42초를
     *                             AI 상한이 덮고 이 값은 그보다 길어야 한다 - AI가 먼저 끊고 503을 돌려준다
     *                             (KAN-22). 반대면 BE는 포기했는데 AI는 계속 추론해 워커와 임시파일을 붙든다.
     * @param aiRetries            AI 일시 장애(연결 실패, 5xx)의 재전송 횟수 - 오디오가 메모리에
     *                             살아 있는 전달 시점에만 가능하다 (FR-DP-01, KAN-24 재큐잉).
     *                             읽기 타임아웃은 재전송하지 않는다 (KAN-172) - 실모델은 타임아웃 시점에
     *                             아직 추론 중일 가능성이 높아 재전송이 중복 분석을 얹는다.
     * @param dispatchConcurrency  분석 전달 워커 수 - 기본 3이고 AI 호스트 최대 대수와 같은 값이다 (KAN-272).
     *                             실모델은 호스트마다 추론을 한 번에 하나만 돌리므로(단일 lock, 8GB에서 2건이면
     *                             OOM. KAN-57) AI가 1대면 뒤의 호출이 AI 안에서 차례를 기다리고, 2대 이상이면
     *                             내부 ALB가 나눠 병렬이 된다. 1이던 때는 AI를 늘려도 처리량이 늘지 않았다.
     *                             태스크당 값이라 태스크가 여럿이면 그만큼 겹친다.
     * @param aiHealthTimeout      회로 복구 프로브({@code GET /internal/v0/health}, §4.2)의 연결과
     *                             읽기 타임아웃 (KAN-28). 추론 없이 즉답하는 엔드포인트라 분석
     *                             호출보다 짧게 잡는다 - 프로브가 스케줄러 스레드를 오래 붙들면
     *                             같은 풀의 다른 잡(타임아웃 정리, 임시파일 청소)이 밀린다.
     * @param circuitFailureThreshold AI 장애가 이만큼 연속되면 회로를 연다 (KAN-28, §4.2).
     *                             회로가 열린 동안 업로드는 GPU를 소모하지 않는 503
     *                             {@code ANALYSIS_UNAVAILABLE}로 즉시 끊기고, 큐에 남아 있던
     *                             작업도 AI를 부르지 않고 종결한다. 1건짜리 순간 장애로 열리지
     *                             않을 만큼 크고, 장애가 지속될 때 워커가 타임아웃을 반복하며
     *                             묶이지 않을 만큼 작아야 한다.
     * @param circuitProbeInterval 회로가 열린 뒤 health 프로브를 다시 던지는 간격 (KAN-28).
     *                             첫 프로브까지의 대기(쿨다운)도 같은 값이다. 사용자 요청을
     *                             프로브로 쓰지 않는다 - 오디오는 재전송할 수 없어(FR-DP-01)
     *                             프로브로 뽑힌 사용자만 실패를 떠안기 때문이다.
     * @param aiToken              AI 서버와 나눠 갖는 내부 호출 시크릿 (KAN-36). AI가 전용 호스트로
     *                             갈라지면서 "같은 compose 네트워크라 backend만 부른다"는 전제가 사라져,
     *                             보안 그룹 한 겹 뒤에 요청마다 {@code X-Accentury-Internal-Token} 헤더로
     *                             대조한다. 미설정이면 헤더를 붙이지 않는다 - 로컬 개발 편의이고, 배포
     *                             프로파일은 값을 요구한다 ({@code DeploymentConfigGuard}).
     * @param shutdownBudget       종료 신호 뒤 실행 중인 분석의 완료를 기다리는 상한 (KAN-166).
     *                             대기 중(미시작) 작업은 기다리지 않고 즉시 실패로 정리하므로,
     *                             이 값은 "AI 호출 1회가 끝나는 데 걸리는 최악 시간"만 덮으면
     *                             된다 - aiTimeout보다 길어야 한다 (기동 시 검증). 웹 요청
     *                             유예(spring.lifecycle.timeout-per-shutdown-phase)와 합쳐
     *                             컨테이너 강제 종료 상한(compose stop_grace_period, ECS
     *                             stopTimeout 120초) 안에 들어야 한다.
     */
    public record Analysis(@DefaultValue("800") long pollAfterMs,
                           @DefaultValue("3000") long congestedPollAfterMs,
                           @DefaultValue("6") int congestionThreshold,
                           @DefaultValue("1s") Duration congestionCacheTtl,
                           @DefaultValue("24h") Duration retention,
                           @DefaultValue("300s") Duration processingTimeout,
                           @DefaultValue("5m") Duration queuedTimeout,
                           @Nullable String aiBaseUrl,
                           @DefaultValue("85s") Duration aiTimeout,
                           @DefaultValue("2") int aiRetries,
                           @DefaultValue("3") int dispatchConcurrency,
                           @DefaultValue("2s") Duration aiHealthTimeout,
                           @DefaultValue("5") int circuitFailureThreshold,
                           @DefaultValue("5s") Duration circuitProbeInterval,
                           @DefaultValue("90s") Duration shutdownBudget,
                           @Nullable String aiToken) {
    }

    /**
     * @param rateLimitPerMinute        IP당 분당 업로드 허용 횟수 (§2.5, KAN-28).
     *                                  임계치는 부하 테스트 후 확정한다 (§7, KAN-40).
     * @param sessionRateLimitPerMinute 세션당 분당 업로드 허용 횟수 (§2.5 - IP와 세션 이중 제한,
     *                                  KAN-28). IP 제한은 NAT 뒤 다수 사용자를 고려해 느슨하므로,
     *                                  세션 하나가 그 여유를 혼자 쓰지 못하게 막는 두 번째 축이다.
     *                                  정상 응시는 음성 5문항 x 1회 = 5건이고 최악이 문항당 시도
     *                                  상한 5회 x 5문항 = 25건이라(§5.1), 멱등 재전송까지 덮는
     *                                  여유 배수로 잡는다.
     * @param tempDir                   업로드 임시파일 전용 디렉터리 (KAN-27). 정상 경로에서는 파일이
     *                                  생기지 않지만(메모리 전용 불변식), 생긴다면 반드시 이 한 곳에
     *                                  모여야 청소 잡과 권한 제한이 닿는다.
     *                                  {@code spring.servlet.multipart.location}과 같은 값이어야 하고,
     *                                  기동 시 {@code VoiceTempDirectory}가 둘의 일치를 강제한다.
     * @param tempRetention             잔존 임시파일 삭제 기준 - 수정 시각이 이보다 오래된 파일만
     *                                  지운다 (KAN-27 - 30분). 업로드 1건은 초 단위로 끝나므로 살아
     *                                  있는 요청의 파일을 앞질러 지우지 않는 여유 배수다.
     */
    public record Upload(@DefaultValue("30") int rateLimitPerMinute,
                         @DefaultValue("60") int sessionRateLimitPerMinute,
                         @Nullable String tempDir,
                         @DefaultValue("30m") Duration tempRetention) {
    }

    /**
     * @param rateLimitPerMinute 세션당 분당 어휘 답안 제출 허용 횟수 (§2.5, KAN-28).
     *                           인증 뒤에만 닿는 경로라 세션이 키다. 정상 응시는 어휘 5문항
     *                           x 1회 = 5건이고 문항당 답안은 하나뿐이라(§3.5 - 새 키 재제출은
     *                           409) 그 이상은 재전송이거나 남용이다.
     */
    public record Vocab(@DefaultValue("60") int rateLimitPerMinute) {
    }

    /**
     * @param rateLimitPerMinute 세션당 분당 {@code /complete} 허용 횟수 (§2.5, KAN-16 AC).
     *                           폴링 대상 엔드포인트라(§3.6) IP가 아니라 세션 단위다 - NAT 뒤의
     *                           여러 정상 세션이 서로의 한도를 깎지 않는다. 서버가 내려주는
     *                           {@code pollAfterMs} 기준값 800ms를 그대로 따르는 클라이언트가
     *                           분당 약 75회이므로(§5.3 규칙 1 - 정상 트래픽), 60초 실행 잔류
     *                           한도(§3.4)까지 이어지는 합법 폴링이 걸리지 않게 그 위로 잡는다
     *                           (Codex sol 리뷰 P2). 임계치는 부하 테스트 후 확정한다 (§7, KAN-40).
     */
    public record Completion(@DefaultValue("120") int rateLimitPerMinute) {
    }

    /**
     * @param allowedOrigins 스탠드얼론 웹 테스트(KAN-31) 오리진 allowlist (§2.5).
     *                       비어 있으면 교차 출처 요청을 허용하지 않는다.
     */
    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }

    /**
     * {@code /result} 응답의 등급별 자산 (§3.7, KAN-25) - 서버가 내려주므로 앱 배포 없이
     * 설정 변경만으로 문구와 이미지를 교체할 수 있다 (KAN-29, 30 소비).
     * 완결성(5개 등급 전부, 빈 값 없음)은 기동 시 {@code TierAssets}가 강제한다.
     *
     * @param webTestUrl   공유 카드가 여는 웹 테스트 URL - 공유 유입 계측용 캠페인 파라미터가
     *                     붙은 완성 URL을 설정값 그대로 반환한다 (2026-08-14 확정 - 전 등급
     *                     공통 고정값 하나, KAN-30). 개인 식별 요소를 넣지 않는다.
     * @param assetBaseUrl 등급 이미지의 기준 URL (KAN-132) - 등급 code마다 {@code {기준}/{code 소문자}.png}가
     *                     {@code share.imageUrl}이다. 값 5개 대신 1개인 것은 환경마다 도메인만 다르고
     *                     파일명은 등급 code로 정해져 있어서다 (자산 정본 {@code assets/share/}, KAN-162).
     *                     배포에서는 SSM {@code ACCENTURY_RESULT_ASSETBASEURL}이 채운다. 이미지 교체는 S3
     *                     업로드만으로 반영되고 설정과 배포는 건드리지 않는다.
     * @param tiers        등급 code(소문자 키) → 자산. 키는 {@code ScorePolicyRegistry.TIER_CODES}와
     *                     대소문자 무시 1:1이어야 한다.
     * @param comments     결과 코멘트 문구 (KAN-249) - 등급이 아니라 억양과 단어 점수의 비교로 고른다.
     *                     완결성(빈 값 없음, 자리 표시)은 기동 시 {@code ResultComments}가 강제한다.
     */
    public record Result(@Nullable String webTestUrl, @Nullable String assetBaseUrl,
                         @DefaultValue Map<String, TierAsset> tiers,
                         @DefaultValue Comments comments) {
    }

    /**
     * 익명 집계 카운터 (KAN-106, SRS FR-AN-10) - 영속 데이터로 허용된 유일한 것이다 (NFR-PR-03).
     *
     * @param zone          집계 행의 일자 경계를 정하는 타임존 (2026-08-17 확정 - Asia/Seoul).
     *                      리포트를 읽는 사람의 하루(KAN-20 등급 분포)와 행의 하루가 같아야
     *                      한다 - UTC로 자르면 KST 09:00에 날짜가 바뀌어 "8월 17일 응시 수"가
     *                      한국의 8월 17일과 어긋난다. 저장 시각 체계(Instant)와는 별개다.
     * @param maxQueryDays  조회 한 번의 최대 기간(일). 실수로 전 기간을 훑는 질의가 운영 DB를
     *                      붙잡지 않게 막는 상한이다 - 넘으면 400이다.
     */
    public record Analytics(@DefaultValue("Asia/Seoul") ZoneId zone,
                            @DefaultValue("366") int maxQueryDays) {
    }

    /**
     * 운영자 전용 API(§6)의 인증 (KAN-106, KAN-26).
     *
     * @param token 관리자 엔드포인트 셋({@code GET /admin/v0/analytics},
     *              {@code PUT /admin/v0/active-version}, {@code GET /admin/v0/test-definitions})이
     *              공유하는 시크릿 - 세션 토큰이 아니라 §6이 규정한 별도 관리자 인증이다.
     *              <b>미설정이 기본값이고, 그러면 엔드포인트 자체가 등록되지 않는다</b> (404) -
     *              설정을 빼먹어도 열려 있는 경로가 생기지 않게 하는 안전한 기본값이다
     *              (trustedProxies와 같은 계열). 검사는 {@code AdminAuth}가 한 곳에서 한다.
     *              <p>
     *              KAN-106 시절 이름은 {@code accentury.analytics.admin-token}이었다. 관리자
     *              API가 셋으로 늘면서 집계 네임스페이스 아래 두는 것이 맞지 않게 되어 옮겼다
     *              (2026-08-19, KAN-26). 환경 변수 이름도 {@code ACCENTURY_ADMIN_TOKEN}으로
     *              바뀐다 - 아직 배포 전이라 옮길 설정이 없다.
     */
    public record Admin(@Nullable String token) {
    }

    /**
     * 카카오톡 공유 웹훅 수신 (§3.8, KAN-164).
     *
     * @param kakaoAdminKey    카카오디벨로퍼스 콘솔의 앱 Admin 키. 카카오가 웹훅마다
     *                         {@code Authorization: KakaoAK {이 값}}으로 싣고, 검증은 그 일치 확인이
     *                         전부다 (서명 알고리즘이 없다). <b>미설정이 기본값이고, 그러면 웹훅
     *                         경로 자체가 등록되지 않는다</b> (404) - {@link Admin#token()}과 같은
     *                         안전한 기본값이다. 검사는 {@code KakaoWebhookAuth}가 한 곳에서 한다.
     *                         운영에서는 SSM {@code ACCENTURY_SHARE_KAKAOADMINKEY}로 들어온다.
     * @param receiptRetention 중복 콜백을 거르는 수신 기록({@code share_webhook_receipt})의 보존
     *                         기간 - 지나면 정리 잡이 지운다. 카카오의 재전송 간격을 모르므로
     *                         세션과 결과의 24시간보다 넉넉히 잡는다.
     */
    public record Share(@Nullable String kakaoAdminKey,
                        @DefaultValue("7d") Duration receiptRetention) {
    }

    /**
     * 결과 화면의 이용 후기 (KAN-211, {@code feedback} 패키지).
     *
     * @param rateLimitPerMinute 세션당 분당 후기 제출 허용 횟수 (§2.5, KAN-28). 인증 뒤에만 닿는
     *                           경로라 세션이 키다. 세션당 후기는 하나뿐이라 정상 응시는 1건이고,
     *                           그 이상은 재전송이거나 남용이다 - 오타를 고쳐 다시 보내는 경로가
     *                           없으므로 어휘 답안(60)만큼 여유를 둘 이유가 없다.
     * @param retention          후기 보존 기간 - 세션과 결과의 24시간(§5.5)과 독립으로 1년이다.
     *                           후기는 세션의 부속물이 아니라 제품 개선의 입력이라 세션이 파기된
     *                           뒤에도 남아야 하고({@code session_feedback}에 FK가 없는 이유),
     *                           그래도 무한은 아니다 - 회신용 이메일이 개인 식별 정보다.
     * @param slackWebhookUrl    개발팀이 후기를 읽는 슬랙 채널({@code #feedback})의 Incoming Webhook URL
     *                           (KAN-211 2단계). <b>없거나 자리 표시 값({@link SsmPlaceholder#UNSET})이면
     *                           알림만 꺼지고 저장은 그대로다</b> - 슬랙은 후기 저장의 부수 기능이라
     *                           {@code admin.token}이나 {@code kakao-admin-key}처럼 경로를 없애는 쪽이
     *                           아니다. 로컬과 테스트는 이 상태로 뜬다. 운영에서는 SSM SecureString
     *                           {@code ACCENTURY_FEEDBACK_SLACKWEBHOOKURL}이 넣는다 - 슬랙 콘솔이
     *                           발급하는 값이라 Terraform은 자리만 만든다. 두 환경이 채널 하나를
     *                           함께 쓰므로 값도 같고, 구분은 메시지 머리의 환경 라벨이 한다
     *                           ({@code FeedbackSlackNotifier}).
     */
    public record Feedback(@DefaultValue("10") int rateLimitPerMinute,
                           @DefaultValue("365d") Duration retention,
                           @Nullable String slackWebhookUrl) {
    }

    /**
     * 학습 음성 수집 (KAN-201, KAN-269, {@code training} 패키지). 두 환경이 음성 전용 버킷 하나를 접두로 나눠 쓰고,
     * 음성은 음성 저장에 선택 동의한 세션만 남긴다. 동의하지 않은 익명 세션은 음성 없이 라벨 JSON만 남긴다 (KAN-274).
     *
     * @param bucket         음성 WAV와 라벨 JSON을 넣을 S3 버킷 이름. <b>미설정이 기본값이고, 그러면 S3 클라이언트도
     *                       저장 빈도 만들어지지 않는다</b> - 로컬과 테스트가 이 상태다. 배포에서는 SSM
     *                       {@code ACCENTURY_TRAINING_BUCKET}으로 들어온다 (config 모듈, 수집을 켠 환경에만 있다).
     *                       빈 문자열은 설정 실수로 보고 기동을 세운다 ({@code TrainingConfig}).
     * @param region         그 버킷의 리전. 비우면 SDK 기본 체인(태스크의 {@code AWS_REGION})이다 - 배포 프로파일은
     *                       CloudWatch 레지스트리와 같은 값을 명시한다 (application-deploy.yml).
     * @param keyPrefix      객체 키의 환경 접두 ({@code staging} 또는 {@code prod}, 슬래시 없이). 버킷이 있으면 필수다 -
     *                       태스크 역할이 자기 접두에만 쓸 수 있어, 없으면 저장이 전부 AccessDenied로 삼켜진다.
     *                       SSM {@code ACCENTURY_TRAINING_KEYPREFIX}가 넣는다. 없거나 형식이 틀리면 기동을 세운다.
     * @param consentVersion 게시 중인 음성 저장 동의 버전. 세션 생성(§3.1)과 계정 동의 등록의 요청 값이 이 값과
     *                       정확히 같아야 받는다 - 방침 버전({@code Auth#privacyPolicyVersion})과 같은 규율이다.
     *                       버킷이 없는 환경에서도 동의는 기록한다(저장만 하지 않는다).
     */
    public record Training(@Nullable String bucket, @Nullable String region, @Nullable String keyPrefix,
                           @DefaultValue(Training.VOICE_CONSENT_VERSION) String consentVersion) {

        /** 게시 중인 음성 저장 동의 버전 - 동의 문구를 고칠 때 앱과 웹의 상수와 함께 올린다 (KAN-269). */
        public static final String VOICE_CONSENT_VERSION = "2026-10-04";
    }

    /**
     * 앱 계정 인증 (KAN-223, {@code auth} 패키지, 명세서 §2.1, §3.9~§3.13).
     *
     * @param jwtSecret          Access JWT(HS256)의 서명 키. 32바이트 이상이어야 하고 기동 시
     *                           {@code AccessTokens}가 검증한다. 배포에서는 SSM SecureString
     *                           {@code ACCENTURY_AUTH_JWTSECRET}이 넣고 deploy 프로파일은 없으면 기동을 세운다
     *                           ({@code DeploymentConfigGuard}). <b>로컬에서 비어 있으면 기동마다 새 난수 키를
     *                           쓴다</b> - 재기동하면 발급한 Access가 전부 무효가 되지만 로컬에서는 refresh가
     *                           다시 받아 온다. 레포에 고정 키를 적어 두면 그 키가 배포로 새는 길이 생긴다.
     * @param issuer             Access JWT의 {@code iss}. 검증은 정확 일치다.
     * @param accessTokenTtl     Access JWT 수명 - 30분 (NFR-SC-03). 짧기 때문에 로그아웃에 블랙리스트를 두지 않는다.
     * @param refreshTokenTtl    Refresh 토큰 수명 - 30일. 회전할 때마다 새 토큰이 다시 이 수명을 받는다.
     * @param rateLimitPerMinute IP당 분당 로그인과 refresh 허용 횟수 (§2.5). 인증 없는 경로라 IP가 유일한 키이고,
     *                           두 경로가 한 통을 나눠 쓴다. 세션 생성(30)과 같은 값이다.
     * @param fakeIdp            개발용 가짜 IdP 스위치 - 켜면 {@code fake:<sub>} 모양의 토큰을 IdP 호출 없이
     *                           그 sub로 받는다 (명세서 §3.9). 로컬과 FE 개발용이고, deploy 프로파일에서 켜면
     *                           기동이 실패한다 ({@code AuthConfig}).
     * @param googleClientId     구글 서버용(웹) OAuth 클라이언트 ID - ID 토큰의 {@code aud}와 정확 일치해야 한다.
     *                           Android와 iOS가 모두 이 값을 serverClientId로 지정해 aud를 하나로 모은다 (KAN-224).
     * @param appleBundleId      iOS 번들 ID - 애플 identityToken의 {@code aud}와 정확 일치해야 한다.
     * @param kakaoAppId         카카오 앱 ID(숫자) - {@code access_token_info}의 {@code app_id}와 일치해야 한다.
     *                           다른 앱이 받은 토큰으로 우리 계정에 들어오는 것을 막는 유일한 검사다.
     *                           세 IdP 값은 시크릿이 아니다. 배포에서는 SSM String 파라미터가 넣고, 콘솔에서 값을
     *                           받기 전에는 자리 표시 값({@link SsmPlaceholder#UNSET})이다 - 그 IdP의 로그인은
     *                           전부 401 {@code AUTH_IDP_TOKEN_INVALID}이고 다른 IdP와 응시는 영향이 없다.
     * @param naverClientId      네이버 로그인 Client ID - SDK refresh token을 교환할 때 쓴다 (KAN-243). 네이버 사용자 조회
     *                           API는 토큰의 발급 앱을 알려 주지 않아서, 우리 Client ID와 Secret으로 교환에 성공하는
     *                           것이 다른 앱의 토큰을 막는 유일한 검사다. 앱(Android, iOS)의 {@code NAVER_CLIENT_ID}와
     *                           같은 값이고 시크릿이 아니다(SSM String). 자리 표시 값이면 카카오 앱 ID와 같이 네이버
     *                           로그인만 전부 401이다.
     * @param naverClientSecret  네이버 로그인 Client Secret - 위 교환의 짝이다. SSM SecureString이고, 비었거나 자리 표시
     *                           값이면 위와 같다.
     * @param appleTeamId        애플 개발자 팀 ID - 탈퇴 때 애플 토큰 revoke(KAN-241)의 client_secret JWT {@code iss}다.
     *                           시크릿이 아니다. 아래 둘과 번들 ID 중 하나라도 비었거나 자리 표시 값이면 revoke를 건너뛰고
     *                           WARN만 남긴다 - 탈퇴 자체는 성공한다.
     * @param appleKeyId         Sign in with Apple 키의 Key ID - client_secret JWT 헤더의 {@code kid}다.
     * @param applePrivateKey    그 키의 .p8 원문(PKCS#8 PEM) - client_secret JWT를 ES256으로 서명한다. SSM SecureString이다.
     * @param googleJwksUrl      구글 JWKS 주소. 테스트가 가짜 JWKS로 바꾸는 자리다.
     * @param appleJwksUrl       애플 JWKS 주소. 위와 같다.
     * @param kakaoApiBaseUrl    카카오 API 기준 주소 ({@code kapi.kakao.com}). 테스트가 MockWebServer로 바꾼다.
     * @param naverApiBaseUrl    네이버 API 기준 주소 ({@code openapi.naver.com}). 위와 같다.
     * @param naverAuthBaseUrl   네이버 인증 서버 기준 주소 ({@code nid.naver.com}) - 토큰 교환(KAN-243)이 부른다. 위와 같다.
     * @param appleAuthBaseUrl   애플 인증 서버 기준 주소 ({@code appleid.apple.com}) - 탈퇴의 토큰 교환과 revoke가 부른다. 위와 같다.
     * @param idpTimeout         IdP 호출(JWKS 조회 포함)의 연결과 읽기 타임아웃. 넘으면 502 {@code AUTH_IDP_UNAVAILABLE}이다.
     * @param privacyPolicyVersion 게시 중인 개인정보처리방침 버전 (KAN-240). 가입 요청의 {@code privacyPolicyVersion}이
     *                           이 값과 정확히 같아야 동의로 기록한다. 다르면 400 {@code AUTH_CONSENT_REQUIRED}다 -
     *                           옛 앱 빌드나 조작된 요청이 계정 고지가 없는 옛 방침에 "동의"한 것으로 남지 않게 한다.
     *                           기본값 {@link #PRIVACY_POLICY_VERSION}은 {@code infra/privacy/privacy.html}의
     *                           {@code accentury-policy-version} 메타와 같아야 하고 {@code privacy.test.mjs}가 둘을
     *                           대조한다. 방침을 개정하면 이 상수, privacy.html, 앱 두 곳(Android, iOS)의 상수를 함께 올린다.
     */
    public record Auth(@Nullable String jwtSecret,
                       @DefaultValue("accentury") String issuer,
                       @DefaultValue("30m") Duration accessTokenTtl,
                       @DefaultValue("30d") Duration refreshTokenTtl,
                       @DefaultValue("30") int rateLimitPerMinute,
                       @DefaultValue("false") boolean fakeIdp,
                       @Nullable String googleClientId,
                       @Nullable String appleBundleId,
                       @Nullable String kakaoAppId,
                       @Nullable String naverClientId,
                       @Nullable String naverClientSecret,
                       @Nullable String appleTeamId,
                       @Nullable String appleKeyId,
                       @Nullable String applePrivateKey,
                       @DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String googleJwksUrl,
                       @DefaultValue("https://appleid.apple.com/auth/keys") String appleJwksUrl,
                       @DefaultValue("https://kapi.kakao.com") String kakaoApiBaseUrl,
                       @DefaultValue("https://openapi.naver.com") String naverApiBaseUrl,
                       @DefaultValue("https://nid.naver.com") String naverAuthBaseUrl,
                       @DefaultValue("https://appleid.apple.com") String appleAuthBaseUrl,
                       @DefaultValue("5s") Duration idpTimeout,
                       @DefaultValue(Auth.PRIVACY_POLICY_VERSION) String privacyPolicyVersion) {

        /** 게시 중인 개인정보처리방침 버전 - privacy.html의 {@code accentury-policy-version} 메타와 같은 값이다 (KAN-240). */
        public static final String PRIVACY_POLICY_VERSION = "2026-10-04";
    }

    /**
     * 단어 학습 (KAN-265, 명세서 §3.16)과 억양 학습 채점 (KAN-267, §3.19) - {@code learning} 패키지.
     *
     * @param rateLimitPerMinute           단어 학습의 계정당 분당 쓰기 요청(시도 시작, 답안, 완료 - 한 통) 허용 횟수
     *                                     (§2.5). 정상은 세트 하나에 시도 1 + 답안 10 이하 + 완료 1이라 어휘 답안(60)과
     *                                     같은 여유 배수다. 목록과 상세 조회는 제한하지 않는다.
     * @param intonationRateLimitPerMinute 억양 학습 녹음 업로드의 계정당 분당 허용 횟수 (§2.5, KAN-267). 한 건마다
     *                                     AI 추론을 쓰고 레벨테스트와 같은 큐를 나눠 쓰므로 단어 학습보다 낮다 - 녹음과
     *                                     대기만으로 한 건에 10초 이상 걸려 정상 사용은 분당 수 건이다. 결과 조회는
     *                                     제한하지 않는다.
     * @param intonationScoreVersion       억양 학습 점수 변환에 쓰는 점수 버전 (§3.19, 2026-10-10 결정) - 레벨테스트와
     *                                     같은 억양 전처리(구간 계수)를 이 버전의 seed에서 읽는다. AI 요청의
     *                                     {@code scoreVersion}과 시도 기록에도 이 값이 실린다. 발행된 버전이 아니면
     *                                     기동하지 않는다.
     * @param perfectScoreThreshold        100점 처리 기준 (§3.19, 2026-10-10 결정) - 변환 점수가 이 값 이상이고 올리고
     *                                     내릴 음절이 하나도 없으면 100점이다. 학습에만 적용한다. 0~100 밖이면
     *                                     기동하지 않는다.
     */
    public record Learning(@DefaultValue("60") int rateLimitPerMinute,
                           @DefaultValue("20") int intonationRateLimitPerMinute,
                           @DefaultValue("sv-0.5") String intonationScoreVersion,
                           @DefaultValue("90") int perfectScoreThreshold) {
    }

    /**
     * 사투리 텍스트 번역 (KAN-266, {@code translation} 패키지, 명세서 §3.18).
     *
     * @param apiKey       Gemini API 키 (Google AI Studio, 환경별 프로젝트). SSM SecureString
     *                     {@code ACCENTURY_TRANSLATION_APIKEY}가 넣는다 - 콘솔이 발급하는 값이라 Terraform은 자리만 만든다.
     *                     <b>없거나 자리 표시 값({@link SsmPlaceholder#UNSET})이면 번역이 전부 503
     *                     {@code TRANSLATION_UNAVAILABLE}이다</b> - 기동과 다른 기능은 영향이 없다 (후기 슬랙 URL과 같은 사정).
     * @param model        호출할 모델 이름. 코드 수정 없이 바꾸는 자리다 (2026-10-08 결정 - 품질이 모자라면
     *                     {@code gemini-3.8-flash}). 배포에서는 SSM {@code ACCENTURY_TRANSLATION_MODEL}이 넣는다.
     * @param baseUrl      Gemini API 기준 주소. 테스트가 MockWebServer로 바꾸는 자리다.
     * @param timeout      Gemini 호출 전체 상한 - 재시도까지 이 안에서 끝난다 (2026-10-08 결정, 동기 응답).
     * @param recordBucket 번역 기록 버킷 이름 (2026-10-08 결정, prod만). <b>미설정이 기본값이고, 그러면 S3 클라이언트도 기록
     *                     빈도 대체 ID도 만들어지지 않는다</b> - staging, 로컬, 테스트가 이 상태다. SSM
     *                     {@code ACCENTURY_TRANSLATION_RECORDBUCKET}이 prod에만 있다. 빈 문자열은 설정 실수로 보고 기동을 세운다.
     * @param region       그 버킷의 리전. 비우면 SDK 기본 체인이다 ({@link Training#region()}과 같다).
     */
    public record Translation(@Nullable String apiKey,
                              @DefaultValue(Translation.DEFAULT_MODEL) String model,
                              @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
                              @DefaultValue("10s") Duration timeout,
                              @Nullable String recordBucket,
                              @Nullable String region) {

        /** 2026-10-08 결정 모델 - 구글 모델 문서가 속도와 비용이 중요한 단순 텍스트 작업에 권장하는 최신 모델이다. */
        public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

        /** 입력 길이 상한 - 앞뒤 공백을 뺀 코드 포인트 수다. FE와 공유하는 값이라 설정이 아니라 상수다 (명세서 §3.18). */
        public static final int MAX_INPUT_LENGTH = 100;

        /** 키가 들어 있는가 - 비었거나 자리 표시 값이면 번역은 503이다. */
        public boolean apiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank() && !SsmPlaceholder.UNSET.equals(apiKey);
        }
    }

    /**
     * 등급 하나의 공유 문구 (§3.7 - share.text). 공유 이미지 URL은 여기 없다 -
     * {@link Result#assetBaseUrl()}과 등급 code로 만든다 (KAN-132). 결과 코멘트도 여기 없다 -
     * 등급이 아니라 점수 비교로 고르므로 {@link Comments}에 있다 (KAN-249).
     *
     * @param shareText 공유 카드 문구 - 이름 없는 1인칭 (KAN-30)
     */
    public record TierAsset(@Nullable String shareText) {
    }

    /**
     * 결과 화면 코멘트 (§3.7 comment, KAN-249). 최고 등급이 아니면 억양 점수와 단어 점수를 비교해
     * 앞의 셋 중 하나를 쓰고, 문구 안의 {@code {nextTier}}에는 한 단계 위 등급의 이름이 들어간다.
     * 등급 이름 뒤에 조사를 붙이지 않고 "까지"로 잇는 것은 받침 유무(경남 토박이)와 무관하게
     * 문장이 맞게 하려는 것이다.
     *
     * @param intonationAhead 억양 점수 &gt; 단어 점수
     * @param even            억양 점수 = 단어 점수
     * @param vocabularyAhead 억양 점수 &lt; 단어 점수
     * @param top             최고 등급 - 점수와 무관한 고정 문구
     */
    public record Comments(@Nullable String intonationAhead, @Nullable String even,
                           @Nullable String vocabularyAhead, @Nullable String top) {
    }
}
