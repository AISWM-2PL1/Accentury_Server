package app.accentury.backend.learning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WordWrongAnswerRepository extends JpaRepository<WordWrongAnswer, String> {

    /**
     * 오답 1건 반영 - 없으면 만들고 있으면 횟수와 마지막 시각을 올린다 (§3.16). 호출부에 트랜잭션 필요.
     * <p>
     * 네이티브 upsert인 이유는 같은 계정의 두 시도가 같은 문항을 동시에 틀릴 때 "조회 뒤 저장"이 유니크 충돌로
     * 500이 되기 때문이다 - 시도 행 잠금은 시도 단위라 계정 단위 경합을 막지 못한다.
     *
     * @param id 새 행이 만들어질 때 쓸 식별자 ({@code wwa_} + UUID) - 이미 있으면 버려진다
     */
    @Modifying
    @Query(value = """
            insert into word_wrong_answer
                (id, user_id, content_version, set_id, item_id, wrong_count, first_wrong_at, last_wrong_at)
            values (:id, :userId, :contentVersion, :setId, :itemId, 1, :at, :at)
            on conflict (user_id, content_version, item_id) do update
                set wrong_count = word_wrong_answer.wrong_count + 1,
                    last_wrong_at = excluded.last_wrong_at
            """, nativeQuery = true)
    void recordWrong(@Param("id") String id, @Param("userId") UUID userId,
                     @Param("contentVersion") String contentVersion, @Param("setId") String setId,
                     @Param("itemId") String itemId, @Param("at") Instant at);

    Optional<WordWrongAnswer> findByUserIdAndContentVersionAndItemId(UUID userId, String contentVersion, String itemId);

    /** 계정의 오답 전부 - 복습 API(범위 밖)와 탈퇴 검증이 읽는다. */
    List<WordWrongAnswer> findByUserId(UUID userId);

    /** 탈퇴 때 계정의 오답을 전부 지운다 (§3.14). 호출부에 트랜잭션 필요. */
    @Modifying
    @Query("delete from WordWrongAnswer w where w.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
