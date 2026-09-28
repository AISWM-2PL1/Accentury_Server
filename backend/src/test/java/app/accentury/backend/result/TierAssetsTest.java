package app.accentury.backend.result;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 등급별 결과 자산 설정 검증의 단위 명세 (KAN-25, KAN-132).
 * <p>
 * 유효한 설정을 한 곳씩 망가뜨려 기동 거부를 확인한다 - ScorePolicyRegistryTest와
 * 같은 구성이다. 자산 없는 등급이 결과 확정 뒤에 발견되면 저장된 결과를 응답으로
 * 만들 수 없으므로, 전부 기동 시점에 잡혀야 한다.
 */
class TierAssetsTest {

    private static final String BASE_URL = "https://img.test/share";

    /** 코멘트 검증은 ResultComments의 몫이라 여기서는 비워 둔다 (KAN-249). */
    private static final AccenturyProperties.Comments NO_COMMENTS =
            new AccenturyProperties.Comments(null, null, null, null);

    @Test
    void 유효한_설정은_소문자_키를_대문자_code로_조회한다() {
        TierAssets assets = new TierAssets(props(validResult()));

        // 설정 키는 소문자(relaxed binding), 저장된 등급 code는 대문자다 - 조회가 잇는다.
        assertEquals("나는 명예주민!", assets.assetFor("HONORARY").shareText());
        assertEquals("나는 외지인!", assets.assetFor("OUTSIDER").shareText());
        assertEquals("https://web.test/t?c=share", assets.webTestUrl());
    }

    // === KAN-132 - 이미지 URL은 기준 URL + 등급 code 소문자 + .png ===

    @Test
    void 이미지_URL은_기준_URL에_등급_code_파일명을_붙인_것이다() {
        TierAssets assets = new TierAssets(props(validResult()));

        // 자산 정본(assets/share/README)의 파일명 규칙 - 등급 code 소문자 + .png
        assertEquals("https://img.test/share/native.png", assets.assetFor("NATIVE").imageUrl());
        assertEquals("https://img.test/share/outsider.png", assets.assetFor("OUTSIDER").imageUrl());
        assertEquals("https://img.test/share/wannabe.png", assets.assetFor("WANNABE").imageUrl());
    }

    @Test
    void 기준_URL의_끝_슬래시는_한_번만_들어간다() {
        TierAssets assets = new TierAssets(props(new AccenturyProperties.Result(
                "https://web.test/t", BASE_URL + "/", validTiers(), NO_COMMENTS)));

        assertEquals("https://img.test/share/traveler.png", assets.assetFor("TRAVELER").imageUrl());
    }

    @Test
    void 기준_URL이_없거나_https가_아니면_기동_거부다() {
        // 카카오는 https 이미지만 가져간다 - http나 상대 경로는 카드가 이미지 없이 나가는 조용한 실패다.
        for (String invalid : new String[] {null, " ", "http://img.test/share", "/share", "img.test/share"}) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new TierAssets(props(new AccenturyProperties.Result("https://web.test/t", invalid, validTiers(), NO_COMMENTS))),
                    "거부돼야 한다: " + invalid);
            assertTrue(e.getMessage().contains("asset-base-url"), e.getMessage());
        }
    }

    @Test
    void 등급_자산이_하나라도_빠지면_기동_거부다() {
        Map<String, AccenturyProperties.TierAsset> tiers = new HashMap<>(validTiers());
        tiers.remove("wannabe");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new TierAssets(props(new AccenturyProperties.Result("https://web.test/t", BASE_URL, tiers, NO_COMMENTS))));
        assertTrue(e.getMessage().contains("WANNABE"));
    }

    @Test
    void 모르는_등급_code의_자산은_기동_거부다() {
        // 오타(honorery 등)는 조용한 누락으로 이어진다 - 5개 검사만으로는 오타+누락 조합을 못 잡는다.
        Map<String, AccenturyProperties.TierAsset> tiers = new HashMap<>(validTiers());
        tiers.put("legend", tiers.get("native"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new TierAssets(props(new AccenturyProperties.Result("https://web.test/t", BASE_URL, tiers, NO_COMMENTS))));
        assertTrue(e.getMessage().contains("legend"));
    }

    @Test
    void 빈_값이_있으면_기동_거부다() {
        Map<String, AccenturyProperties.TierAsset> tiers = new HashMap<>(validTiers());
        tiers.put("traveler", new AccenturyProperties.TierAsset(" "));

        assertThrows(IllegalStateException.class,
                () -> new TierAssets(props(new AccenturyProperties.Result("https://web.test/t", BASE_URL, tiers, NO_COMMENTS))));
    }

    @Test
    void 웹_테스트_URL이_없으면_기동_거부다() {
        assertThrows(IllegalStateException.class,
                () -> new TierAssets(props(new AccenturyProperties.Result(null, BASE_URL, validTiers(), NO_COMMENTS))));
    }

    // === 헬퍼 ===

    private static AccenturyProperties.Result validResult() {
        return new AccenturyProperties.Result("https://web.test/t?c=share", BASE_URL, validTiers(), NO_COMMENTS);
    }

    private static Map<String, AccenturyProperties.TierAsset> validTiers() {
        return Map.of(
                "outsider", new AccenturyProperties.TierAsset("나는 외지인!"),
                "traveler", new AccenturyProperties.TierAsset("나는 여행객!"),
                "wannabe", new AccenturyProperties.TierAsset("나는 호소인!"),
                "honorary", new AccenturyProperties.TierAsset("나는 명예주민!"),
                "native", new AccenturyProperties.TierAsset("나는 토박이!"));
    }

    /** 기본값 조합에 result만 갈아끼운 설정 - 검증 대상은 result뿐이다. */
    private static AccenturyProperties props(AccenturyProperties.Result result) {
        return PropertiesFixture.withResult(result);
    }
}
