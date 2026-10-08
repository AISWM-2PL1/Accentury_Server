package app.accentury.backend.learning;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WordAttemptAnswerRepository extends JpaRepository<WordAttemptAnswer, String> {

    /** 멱등 재전송과 재제출 판별의 진입점 - 유니크 제약과 같은 키 조합의 단건 조회. */
    Optional<WordAttemptAnswer> findByAttemptIdAndItemId(String attemptId, String itemId);

    /** 시도의 답안 전부 - 완료 판정과 정답률, 오답 목록의 입력 (§3.16). */
    List<WordAttemptAnswer> findByAttemptId(String attemptId);

    long countByAttemptId(String attemptId);
}
