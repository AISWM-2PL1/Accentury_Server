package app.accentury.backend.learning;

import java.util.List;

/**
 * 단어 학습 발행본 (KAN-265, 명세서 §3.16) - 정답과 해설까지 든 서버 내부 모델.
 * <p>
 * 발행본은 {@code tools/content/build_word_learning.py}가 레벨테스트 어휘 풀에서 만들고 마이그레이션의 INSERT로
 * 들어온다. 발행 후 불변이다 - 바뀌면 새 {@code contentVersion}이다. 클라이언트가 받는 모양은
 * {@link WordSetResponse}와 {@link WordSetListResponse}이고 거기에는 정답과 해설이 없다.
 *
 * @param contentVersion 발행본 버전 (예: wd-gn-2026.10.1) - 시도가 고정하는 값
 * @param dialect        대상 방언 - MVP는 GYEONGNAM 고정
 * @param sets           어휘 세트 (seq 오름차순으로 정렬해 둔다)
 */
public record WordLearningDefinition(String contentVersion, String dialect, List<Set> sets) {

    /**
     * 어휘 세트 하나 = 어휘 분류 하나(10카드를 넘으면 쪼갠 조각) (§3.16).
     *
     * @param setId    세트 식별자 (예: ws01)
     * @param seq      목록 순서 (1부터, 분류 순)
     * @param level    레벨 1부터 5 - 낱말 근거(코퍼스, 일반, 사전)의 평균으로 발행 때 정한다
     * @param category 어휘 분류 이름 (예: 음식과 식재료)
     * @param title    세트 제목 - 분류가 쪼개졌으면 뒤에 번호가 붙는다
     * @param cards    어휘 카드 (W-2)
     * @param items    객관식 문항 (W-3) - 카드 하나에 문항 하나, seq 오름차순
     */
    public record Set(String setId, int seq, int level, String category, String title,
                      List<Card> cards, List<Item> items) {

        /** 세트 안 문항 - 없으면 빈 값. 제출 검증이 쓴다 (§3.16 - 이 세트의 문항이 아니면 422). */
        public java.util.Optional<Item> item(String itemId) {
            return items.stream().filter(item -> item.itemId().equals(itemId)).findFirst();
        }
    }

    /**
     * 어휘 카드 - 표준어와 사투리 대응 (FR-WD-01).
     *
     * @param cardId   카드 식별자 (예: ws01c01)
     * @param standard 표준어 (또는 뜻풀이)
     * @param dialect  경남 사투리 낱말
     */
    public record Card(String cardId, String standard, String dialect) {
    }

    /**
     * 치환 문항 - 표준어에서 사투리로 한 방향, 4지선다 (FR-WD-02).
     *
     * @param itemId          문항 식별자 (예: ws01q01)
     * @param seq             세트 안 순서 (1부터)
     * @param cardId          이 문항이 확인하는 카드
     * @param prompt          문항 문구
     * @param choices         보기 4개 - 전부 사투리 낱말
     * @param correctChoiceId 정답 보기 - 조회 응답에 싣지 않는다
     * @param explanation     해설 - 제출 응답에서만 준다 (FR-WD-03)
     */
    public record Item(String itemId, int seq, String cardId, String prompt, List<Choice> choices,
                       String correctChoiceId, String explanation) {

        /** 보기 - 없으면 빈 값. */
        public java.util.Optional<Choice> choice(String choiceId) {
            return choices.stream().filter(choice -> choice.choiceId().equals(choiceId)).findFirst();
        }

        /** 정답 보기의 낱말 - 발행 검증이 정답이 보기 안에 있음을 보장한다. */
        public String correctText() {
            return choice(correctChoiceId).orElseThrow().text();
        }
    }

    /** 보기 하나. 정오 정보는 없다 - 정답은 문항의 {@code correctChoiceId}에만 있다. */
    public record Choice(String choiceId, String text) {
    }
}
