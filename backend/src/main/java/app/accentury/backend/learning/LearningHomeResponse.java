package app.accentury.backend.learning;

import app.accentury.backend.result.ResultResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * {@code GET /v0/learning/home}의 200 응답 (명세서 §3.20, KAN-252 H-1).
 *
 * @param levelTestTaken      계정이 레벨테스트를 완주한 기록이 있는지 - 홈 Primary 카드가 이 값으로 바뀐다
 * @param levelTest           최근 완주의 등급 - 기록이 없으면 null
 * @param recommendedCourseId 코스 목록(§3.17)의 추천과 같은 값 - 없으면 null
 * @param recommendedSetId    세트 목록(§3.16)의 추천과 같은 값 - 없으면 null
 */
public record LearningHomeResponse(boolean levelTestTaken, @Nullable LevelTest levelTest,
                                   @Nullable String recommendedCourseId, @Nullable String recommendedSetId) {

    /** 최근 완주 - {@code tier}는 결과(§3.7)의 {@code tier}와 같은 모양이다. 점수는 남기지 않아 없다. */
    public record LevelTest(String testVersion, String scoreVersion, ResultResponse.Tier tier, Instant completedAt) {

        static LevelTest from(LevelTestRecords.Entry entry) {
            return new LevelTest(entry.testVersion(), entry.scoreVersion(),
                    new ResultResponse.Tier(entry.tierCode(), entry.tierName(), entry.tierRank(), entry.tierCount()),
                    entry.completedAt());
        }
    }
}
