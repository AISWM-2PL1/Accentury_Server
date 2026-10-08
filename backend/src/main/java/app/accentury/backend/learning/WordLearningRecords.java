package app.accentury.backend.learning;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 계정의 단어 학습 기록 파기 (KAN-265, 명세서 §3.14, §5.5) - 회원 탈퇴가 계정 파기와 같은 트랜잭션에서 부른다.
 * <p>
 * 시도를 지우면 답안은 FK {@code on delete cascade}로 따라 지워지고, 오답은 계정에 매달려 있어 따로 지운다.
 * 발행본은 콘텐츠라 건드리지 않는다.
 */
@Component
public class WordLearningRecords {

    private final WordSetAttemptRepository attempts;
    private final WordWrongAnswerRepository wrongAnswers;

    WordLearningRecords(WordSetAttemptRepository attempts, WordWrongAnswerRepository wrongAnswers) {
        this.attempts = attempts;
        this.wrongAnswers = wrongAnswers;
    }

    /** 파기한 행 수 (시도 + 오답). 호출부에 트랜잭션 필요. */
    public int purge(UUID userId) {
        return attempts.deleteByUserId(userId) + wrongAnswers.deleteByUserId(userId);
    }
}
