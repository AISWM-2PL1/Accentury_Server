package app.accentury.backend.result;

import app.accentury.backend.PropertiesFixture;
import app.accentury.backend.common.AccenturyProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 결과 코멘트 선택의 단위 명세 (KAN-249).
 * <p>
 * 코멘트는 등급이 아니라 억양과 단어 점수의 비교로 고른다. 비교 갈래 셋과 최고 등급, 그리고
 * 설정 검증을 TierAssetsTest와 같은 구성(유효한 설정을 한 곳씩 망가뜨리기)으로 확인한다.
 */
class ResultCommentsTest {

    private static final AccenturyProperties.Comments VALID = new AccenturyProperties.Comments(
            "억양 앞섬 {nextTier}", "같음 {nextTier}", "단어 앞섬 {nextTier}", "최고");

    @Test
    void 억양이_단어보다_높으면_억양_앞섬_문구에_다음_등급이_들어간다() {
        assertEquals("억양 앞섬 경남 토박이", comments(VALID).commentFor(75, 60, "경남 토박이"));
    }

    @Test
    void 단어가_억양보다_높으면_단어_앞섬_문구다() {
        // 등급별 고정 코멘트가 틀리던 사례 - 단어 100, 억양 60이어도 종합 73으로 명예주민이다.
        assertEquals("단어 앞섬 경남 토박이", comments(VALID).commentFor(60, 100, "경남 토박이"));
    }

    @Test
    void 두_점수가_같으면_같음_문구다() {
        assertEquals("같음 명예주민", comments(VALID).commentFor(60, 60, "명예주민"));
        // 1점 차이는 같음이 아니다 - 비교는 응답에 실리는 정수 점수 그대로다.
        assertEquals("억양 앞섬 명예주민", comments(VALID).commentFor(61, 60, "명예주민"));
    }

    @Test
    void 최고_등급은_점수와_무관하게_고정_문구다() {
        ResultComments comments = comments(VALID);

        assertEquals("최고", comments.commentFor(100, 80, null));
        assertEquals("최고", comments.commentFor(80, 100, null));
        assertEquals("최고", comments.commentFor(90, 90, null));
    }

    @Test
    void 문구가_비었으면_기동_거부다() {
        AccenturyProperties.Comments[] invalid = {
                new AccenturyProperties.Comments(null, "같음 {nextTier}", "단어 앞섬 {nextTier}", "최고"),
                new AccenturyProperties.Comments("억양 앞섬 {nextTier}", " ", "단어 앞섬 {nextTier}", "최고"),
                new AccenturyProperties.Comments("억양 앞섬 {nextTier}", "같음 {nextTier}", "", "최고"),
                new AccenturyProperties.Comments("억양 앞섬 {nextTier}", "같음 {nextTier}", "단어 앞섬 {nextTier}", null),
        };
        for (AccenturyProperties.Comments comments : invalid) {
            assertThrows(IllegalStateException.class, () -> comments(comments), "거부돼야 한다: " + comments);
        }
    }

    @Test
    void 비교_문구에_자리_표시가_없으면_기동_거부다() {
        // 자리 표시가 빠지면 모든 등급에 같은 문장이 나가는 조용한 실패다.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> comments(new AccenturyProperties.Comments(
                        "억양 앞섬 {nextTier}", "같음", "단어 앞섬 {nextTier}", "최고")));
        assertTrue(e.getMessage().contains("even"), e.getMessage());
    }

    private static ResultComments comments(AccenturyProperties.Comments comments) {
        return new ResultComments(PropertiesFixture.withResult(
                new AccenturyProperties.Result(null, null, Map.of(), comments)));
    }
}
