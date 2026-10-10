package app.accentury.backend.learning;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 계정의 학습 기록 파기 (명세서 §3.14, §5.5) - 회원 탈퇴가 계정 파기와 같은 트랜잭션에서 부른다.
 * <p>
 * 단어 학습(KAN-265)은 시도를 지우면 답안이 FK {@code on delete cascade}로 따라 지워지고, 오답은 계정에 매달려 있어
 * 따로 지운다. 억양 학습 채점(KAN-267)은 시도 행만 있다 - 음성 버킷에 남은 학습 녹음과 대응표 행은 레벨테스트 음성과
 * 같이 탈퇴 뒤에도 남는다 (§3.19). 발행본은 콘텐츠라 건드리지 않는다.
 */
@Component
public class LearningRecords {

    private final WordSetAttemptRepository wordAttempts;
    private final WordWrongAnswerRepository wrongAnswers;
    private final IntonationAttemptRepository intonationAttempts;

    LearningRecords(WordSetAttemptRepository wordAttempts, WordWrongAnswerRepository wrongAnswers,
                    IntonationAttemptRepository intonationAttempts) {
        this.wordAttempts = wordAttempts;
        this.wrongAnswers = wrongAnswers;
        this.intonationAttempts = intonationAttempts;
    }

    /** 파기한 행 수 (단어 시도 + 오답 + 억양 시도). 호출부에 트랜잭션 필요. */
    public int purge(UUID userId) {
        return wordAttempts.deleteByUserId(userId) + wrongAnswers.deleteByUserId(userId)
                + intonationAttempts.deleteByUserId(userId);
    }
}
