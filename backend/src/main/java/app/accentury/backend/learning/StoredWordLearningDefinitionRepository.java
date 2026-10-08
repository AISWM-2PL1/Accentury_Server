package app.accentury.backend.learning;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * 단어 학습 발행본 저장소 - <b>읽기만 연다</b>. 발행은 마이그레이션뿐이라 {@code save()}와 {@code delete()}를
 * 열지 않는다 ({@code StoredTestDefinitionRepository}와 같은 방침).
 */
public interface StoredWordLearningDefinitionRepository extends Repository<StoredWordLearningDefinition, String> {

    /** 발행된 발행본 전부 - 기동 시 한 번 읽는다. 발행 시각 오름차순이라 마지막이 가장 늦은 발행본이다. */
    List<StoredWordLearningDefinition> findAllByOrderByPublishedAtAscContentVersionAsc();
}
