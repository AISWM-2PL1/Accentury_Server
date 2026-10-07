/**
 * 단어 학습 (KAN-265, API 명세서 §3.16) - 어휘 세트와 카드 조회, 객관식 문항 제출과 서버 채점, 세트 완료, 계정별 오답.
 * <p>
 * 학습은 로그인 필수다 (2026-10-04 결정). 전 경로가 계정 Access 토큰 뒤에 있고 세션과 무관하다. 콘텐츠는
 * 레벨테스트 정의와 같은 방식의 발행본({@link app.accentury.backend.learning.WordLearningRegistry})이고,
 * 채점은 AI를 거치지 않고 서버가 발행본의 정답표와 대조한다.
 */
@NullMarked
package app.accentury.backend.learning;

import org.jspecify.annotations.NullMarked;
