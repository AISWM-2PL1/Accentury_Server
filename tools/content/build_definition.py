#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""발행본 JSON과 Flyway 마이그레이션을 만든다 (KAN-182 콘텐츠 발행).

재료 셋을 합쳐 테스트 정의 하나를 낸다.

    가이드 곡선  KAN-17 산출물 (--guide-f0). 문장별 script_key, 대본, guideF0를 준다
    어휘 문항    vocabulary_gn.py의 WORDS
    버전 정보    --test-version, --score-version

**가이드 곡선을 갈아 끼우는 자리는 --guide-f0 하나다.** 새 산출물이 오면 경로만 바꿔 다시
돌리고, 나온 SQL을 새 testVersion의 마이그레이션으로 넣는다 - 발행본은 발행 후 불변이라
기존 정의를 UPDATE하지 않는다 (KAN-26). 이 스크립트는 발행본 JSON을 손으로 고치지 않게
하려고 있다.

**KAN-220 재베이스라인 (2026-09-21) 뒤의 파일 배치.** 아래 예시의 --out 파일(옛 V6, V8, V10, V11)은
전부 삭제됐고, 마지막 발행본 gn-2026.09.4의 INSERT 문은 backend/src/main/resources/db/migration/
V1__baseline.sql이 바이트 단위 그대로 품고 있다 (스키마 DDL 뒤). 예시는 각 발행본을 어떻게 만들었는지의
기록으로 남긴다 - 같은 재료로 다시 돌리면 V1 안의 INSERT와 같은 본문이 나와야 한다. 다음 발행은
V2__<version>.sql부터 다시 번호를 매긴다. V1 파일의 주석에는 달러 인용 구분자 문자열을 적지 않는다 -
앱, iOS, 웹의 발행본 전수 검사가 파일에서 처음 만나는 구분자 두 개 사이를 정의 JSON으로 읽는다.

사용 (첫 발행, 옛 V6)
    python3 build_definition.py --guide-f0 ~/Downloads/guide_f0_2026-09-04.json \\
        --test-version gn-2026.09.1 --out ../../backend/src/main/resources/db/migration/V6__gn_2026_09_1.sql

점수 버전만 바꾼 재발행 (KAN-200 - gn-2026.09.2 = gn-2026.09.1 본문 + sv-0.4)
    python3 build_definition.py --guide-f0 ~/Downloads/guide_f0_2026-09-04.json \\
        --test-version gn-2026.09.2 --score-version sv-0.4 --same-content-as gn-2026.09.1 \\
        --published-at 2026-09-10T00:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V8__gn_2026_09_2_sv_0_4.sql

--same-content-as는 선택지 섞기 시드를 그 버전으로 고정해 문항 본문이 바이트 단위로 같게
하고, 마이그레이션 머리말을 재발행용으로 바꾼다. 정의는 발행 후 불변이라(KAN-26) 점수
버전을 바꾸는 유일한 길이 새 testVersion 발행이다 (§5.4).

대본만 바꾼 재발행 (KAN-210 - gn-2026.09.3 = gn-2026.09.2 본문에 09-01 인계본 대본)
    python3 build_definition.py --guide-f0 ~/Downloads/guide_f0_2026-09-04.json \\
        --sentences ~/Desktop/handoff_sentences_2026-09-15.json \\
        --test-version gn-2026.09.3 --score-version sv-0.4 --choice-seed gn-2026.09.1 \\
        --sentences-from gn-2026.09.2 --published-at 2026-09-15T00:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V10__gn_2026_09_3_sentences.sql

인계본 2차 (KAN-210 - gn-2026.09.4 = 문장 3개가 빠진 인계본 + 곡선 결측 문장 3개 복귀로 145 유지)
    python3 build_definition.py --guide-f0 ~/Downloads/guide_f0_2026-09-04.json \\
        --sentences ~/Desktop/handoff_sentences_2026-09-15-2.json \\
        --include-excluded "2|43,2|71,2|79" \\
        --test-version gn-2026.09.4 --score-version sv-0.4 --choice-seed gn-2026.09.1 \\
        --sentences-from gn-2026.09.3 --published-at 2026-09-15T09:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V11__gn_2026_09_4_sentences.sql

세트 구성만 바꾼 재발행 (KAN-260 - gn-2026.10.1 = gn-2026.09.4 본문 + 7문항 세트 + sv-0.5)
    python3 build_definition.py --reissue-from ../../backend/src/main/resources/db/migration/V1__baseline.sql \\
        --same-content-as gn-2026.09.4 --test-version gn-2026.10.1 --score-version sv-0.5 \\
        --set-layout VVWVWWW --estimated-duration-sec 180 --published-at 2026-10-04T00:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V5__gn_2026_10_1_seven_items.sql

검수 결과로 문항을 바꾼 재발행 (KAN-276 - gn-2026.10.2 = gn-2026.10.1에 curation_gn_2026_10_2.py 적용)
    python3 build_definition.py --reissue-from ../../backend/src/main/resources/db/migration/V5__gn_2026_10_1_seven_items.sql \\
        --same-content-as gn-2026.10.1 --curation --test-version gn-2026.10.2 --score-version sv-0.5 \\
        --set-layout VVWVWWW --estimated-duration-sec 180 --published-at 2026-10-08T00:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V11__gn_2026_10_2_curated.sql

--curation은 바이트 대조 대신 검수 문서의 원문이 원본 문장과 같은지, 고친 문장의 어절 수가 같은지, 한
세트 안에 같은 문구가 두 번 오지 않는지를 본다. 어휘 선택지는 testVersion을 시드로 다시 섞는다.

--reissue-from은 재료(가이드 곡선, 인계본) 대신 이미 발행된 마이그레이션에서 --same-content-as
버전의 본문을 읽어 문항 목록을 그대로 옮긴다. 재료 파일이 손에 없어도 되고, 문항 본문이 원본과
바이트 단위로 같은지를 실행할 때마다 대조한다. --set-layout은 세트 하나의 구성과 출제 순서다
(V = 음성, W = 어휘. 생략하면 필드를 싣지 않아 음성 5 + 어휘 5 교차로 읽힌다).

--sentences는 KAN-159 전달본의 문장 목록 JSON이다. 가이드 곡선 문장의 대본을 script_key로
찾은 인계본 대본으로 덮어쓴다. 가이드 문장 중 인계본에 없는 키는 출시 문항에서 뺀다 - 인계본에서
빠진 문장은 더 이상 채점하지 않으므로(인계본 규칙) 남겨 둘 수 없다. 대본이 바뀌어도
scriptKey와 guideF0는 그대로다 - 인계본이 어절 수를 지키며 철자만 고쳤고 참조 본체도 그대로라서다.
새 대본의 띄어쓰기 어절 수가 인계본의 "어절" 값과 다르면 표준출력에 경고를 남긴다 (가이드 곡선이
어절당 20점 격자라 그 문장은 곡선과 대본의 어절 수가 어긋난다).

--include-excluded는 "어절이 통째로 빈 문장" 가운데 출시 문항으로 되돌릴 키다. 인계본이 문장을
빼서 음성 풀이 어휘 풀보다 작아졌을 때 두 풀을 같은 크기로 맞추는 용도다 (2026-09-15 결정). 그
문장은 곡선에 어절 하나가 빈 채로 실리고 프론트가 끊어 그린다 (KAN-102 AC3). 목록에 없는 키는
멈춘다. 자리는 가이드 곡선 파일의 순서다.

--choice-seed는 어휘 선택지 섞기 시드다. 생략하면 testVersion이다. 대본만 바꾼 재발행은 원본의
시드를 넘겨 어휘 문항(선택지 순서, 정답 choiceId)을 바이트 단위로 같게 한다 - e2e_smoke.py의
세트 1 정답 수 표를 그대로 쓰기 위해서다.

가이드 곡선 파일에 기대하는 것 (2026-09-04 전달본 기준)
    {"문장": [{"script_key": "1|1", "대본": "...", "어절": 11,
               "guideF0": {"unit": "semitone", "frameIntervalMs": 21.5, "values": [...]}}],
     "어절이 통째로 빈 문장": ["2|1", ...]}

"어절이 통째로 빈 문장"은 출시 문항에서 뺀다 (박재영 2026-09-04). 그 어절은 참조 화자
대부분이 대본과 다른 말을 해 자리가 비었고, 곡선에 구멍으로 남는다.

frameIntervalMs는 반올림해 싣는다 (2026-09-04 결정). 산출물의 값은 어절당 20점 정규화라
문장마다 다른 실수인데 발행본 스키마와 앱·웹이 이 필드를 정수로 읽는다. 가이드 레인은 절대
시간이 아니라 자기 길이로 폭 전체를 쓰므로(docs/wiki/pitch-curve.md §4) 반올림 오차가 화면에
드러나지 않는다. 오차는 이 스크립트가 실행할 때마다 표준출력에 찍는다.
"""
from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from vocabulary_gn import WORDS  # noqa: E402

#: 세트 하나가 각 풀에서 가져오는 문항 수 (VoiceSets.SET_SIZE와 같아야 한다)
SET_SIZE = 5

#: 선택지 라벨 - choiceId는 "<itemId><라벨>"이다 (기존 발행본 규칙)
CHOICE_LABELS = "abcd"

#: 예상 소요 시간 (초). 음성 5문항 × 약 18초(대본 읽기 + 녹음, 참조 발화 중앙 4.7초에
#: 화면 전환과 준비를 더한 값) + 어휘 5문항 × 약 12초 + 여유. 더미 정의의 240초는 음성
#: 문장이 "밥 뭇나?" 한 마디였을 때의 값이라 문장이 길어진 만큼 올렸다.
ESTIMATED_DURATION_SEC = 300


def load_guide(path: Path, include_excluded: list[str] | None = None) -> tuple[list[dict], list[str]]:
    """가이드 곡선 파일에서 출시 대상 문장만 뽑는다.

    include_excluded는 "어절이 통째로 빈 문장" 가운데 출시 문항으로 되돌릴 키다 (모듈 주석 참고).
    돌려주는 excluded는 되돌린 키를 뺀 나머지다.
    """
    data = json.loads(path.read_text(encoding="utf-8"))
    excluded = list(data.get("어절이 통째로 빈 문장", []))
    unknown = [k for k in (include_excluded or []) if k not in excluded]
    if unknown:
        raise SystemExit(f"--include-excluded의 키가 \"어절이 통째로 빈 문장\"에 없다: {' '.join(unknown)}")
    excluded = [k for k in excluded if k not in set(include_excluded or [])]
    sentences = [s for s in data["문장"] if s["script_key"] not in set(excluded)]
    return sentences, excluded


def apply_sentences(sentences: list[dict], path: Path) -> tuple[list[tuple[str, str, str]], list[str]]:
    """인계본(KAN-159 문장 목록)의 대본을 script_key로 덮어쓴다 (KAN-210).

    sentences를 제자리에서 고치고, 대본이 바뀐 문장의 (script_key, 이전 대본, 새 대본) 목록과
    인계본에 없어 뺀 키 목록을 돌려준다. 인계본 규칙상 인계본에 없는 키는 더 이상 채점하지
    않으므로 출시 문항에서 뺀다. 어절 수가 인계본의 "어절" 값과 어긋나면 경고만 남긴다 -
    곡선(어절당 20점)과 대본이 어긋나는 문장이라 사람이 봐야 하지만, 인계본이 정본이라
    스크립트가 고치지 않는다.
    """
    handoff = json.loads(path.read_text(encoding="utf-8"))
    by_key = {s["script_key"]: s for s in handoff["문장"]}
    dropped = [s["script_key"] for s in sentences if s["script_key"] not in by_key]
    sentences[:] = [s for s in sentences if s["script_key"] in by_key]
    changed: list[tuple[str, str, str]] = []
    for s in sentences:
        entry = by_key[s["script_key"]]
        new = entry["대본"]
        if len(new.split()) != entry["어절"]:
            print(f"[경고]   {s['script_key']} 대본 어절 {len(new.split())} != 인계본 어절 {entry['어절']}"
                  f" (곡선 격자와 어긋난다): {new}", file=sys.stderr)
        if new != s["대본"]:
            changed.append((s["script_key"], s["대본"], new))
            s["대본"] = new
    return changed, dropped


def build_items(sentences: list[dict], words: list[tuple], choice_seed: str) -> list[dict]:
    """풀 정의의 문항 목록 - 음성 N + 어휘 M, seq는 1..N+M 교차 연속이다.

    seq는 레지스트리가 풀 순서를 정하는 유일한 근거다(KAN-10 AC). 배열 순서와 seq가 같게
    두되, 세트를 만들 때 VoiceSets가 다시 매기므로 여기서는 풀 안의 자리만 정한다.

    choice_seed는 선택지 섞기의 시드다. 보통 testVersion이고, 점수 버전만 바꾼 재발행은
    원본 testVersion을 넘겨 선택지 순서(정답 choiceId)까지 같게 한다 (KAN-200).
    """
    if len(sentences) != len(words):
        raise SystemExit(
            f"음성 {len(sentences)}문항과 어휘 {len(words)}문항의 수가 다르다. "
            "세트가 한쪽 풀을 되풀이하게 되므로 두 풀을 같은 크기로 맞춰라")

    # 선택지 순서는 testVersion을 시드로 섞는다 - 정답이 늘 첫 자리면 찍어서 맞힐 수 있다.
    # 같은 시드면 언제 돌려도 같은 순서라 발행본이 재현된다.
    rng = random.Random(choice_seed)

    items: list[dict] = []
    seq = 1
    for index, (sentence, word) in enumerate(zip(sentences, words), start=1):
        items.append(voice_item(f"v{index}", seq, sentence))
        seq += 1
        items.append(vocabulary_item(f"w{index}", seq, word, rng))
        seq += 1
    return items


def topic_particle(word: str) -> str:
    """낱말 뒤에 붙일 보조사 - 받침이 있으면 "은", 없으면 "는".

    문항 문구가 "'구룸'는 표준어로..."처럼 나오면 눈에 걸린다. 145문항 중 30개가
    받침으로 끝나므로 손으로 적지 않고 여기서 갈라 붙인다.
    """
    last = word[-1]
    if not ("\uac00" <= last <= "\ud7a3"):
        return "는"
    return "은" if (ord(last) - 0xAC00) % 28 else "는"


def voice_item(item_id: str, seq: int, sentence: dict) -> dict:
    guide = sentence["guideF0"]
    return {
        "itemId": item_id,
        "seq": seq,
        "type": "VOICE",
        "prompt": sentence["대본"],
        "scriptKey": sentence["script_key"],
        "guideF0": {
            "unit": guide["unit"],
            # 반올림해 정수로 (모듈 주석 참고). 실수를 그대로 실으면 BE·앱 파싱이 깨진다.
            "frameIntervalMs": round(guide["frameIntervalMs"]),
            "values": guide["values"],
        },
    }


def vocabulary_item(item_id: str, seq: int, word: tuple, rng: random.Random) -> dict:
    dialect, answer, wrong, ask, _source, _confidence = word
    texts = [answer, *wrong]
    rng.shuffle(texts)
    choices = [{"choiceId": item_id + CHOICE_LABELS[i], "text": text}
               for i, text in enumerate(texts)]
    particle = topic_particle(dialect)
    prompt = (f"'{dialect}'{particle} 표준어로 무엇일까요?" if ask == "표준어"
              else f"'{dialect}'{particle} 무슨 뜻일까요?")
    return {
        "itemId": item_id,
        "seq": seq,
        "type": "VOCABULARY",
        "prompt": prompt,
        "choices": choices,
        "correctChoiceId": choices[texts.index(answer)]["choiceId"],
    }


def migration_sql(definition: dict, published_at: str, previous_version: str,
                  same_content_as: str | None = None, sentences_from: str | None = None,
                  sentences_note: str = "") -> str:
    """발행 마이그레이션 - 활성 전환은 담지 않는다 (2단계 롤아웃은 배포 순서로 지킨다).

    달러 인용($definition$)을 쓰는 것은 본문에 작은따옴표가 들어 있어서다 - 어휘 문항의
    "'정구지'는 표준어로 무엇일까요?" 같은 문구다 (V2와 같은 이유).
    """
    body = json.dumps(definition, ensure_ascii=False, indent=2)
    if "$definition$" in body:
        raise SystemExit("본문에 달러 인용 구분자가 들어 있다 - 다른 구분자를 써야 한다")
    version = definition["testVersion"]
    voices = sum(1 for item in definition["items"] if item["type"] == "VOICE")
    vocabulary = len(definition["items"]) - voices
    sets = (max(voices, vocabulary) + SET_SIZE - 1) // SET_SIZE
    if same_content_as:
        header = reissue_header(definition, same_content_as, voices, vocabulary, sets)
    elif sentences_from:
        header = sentences_header(definition, sentences_from, voices, vocabulary, sets, sentences_note)
    else:
        header = first_content_header(previous_version, voices, vocabulary, sets)

    return f"""\
{header}
insert into test_definition (test_version, dialect, score_version, body, published_at)
values ('{version}', '{definition["dialect"]}', '{definition["scoreVersion"]}', $definition${body}$definition$,
        timestamp with time zone '{published_at}');
"""


def reissue_header(definition: dict, same_content_as: str, voices: int, vocabulary: int, sets: int) -> str:
    """점수 버전만 바꾼 재발행의 머리말 (KAN-200)."""
    return f"""\
-- KAN-200: {same_content_as}의 본문 그대로 scoreVersion만 {definition["scoreVersion"]}로 바꾼 재발행 -
-- 음성 {voices}문항 + 어휘 {vocabulary}문항 = 세트 {sets}개.
--
-- 문항 본문(문장, scriptKey, guideF0, 어휘 선택지와 정답)은 {same_content_as}과 바이트 단위로
-- 같다. 선택지 섞기 시드를 {same_content_as}으로 고정해 만들었다. 이 파일은 손으로 쓰지 않는다 -
-- tools/content/build_definition.py --same-content-as {same_content_as} 가 만든다.
--
-- 새 testVersion을 발행하는 이유는 점수 버전 전환이다. 활성 점수 버전은 따로 지정하는 값이
-- 아니라 활성 정의가 선언한 scoreVersion을 따르고(§5.4, ScorePolicyRegistry), 정의는 발행 후
-- 불변이라(KAN-26) {definition["scoreVersion"]} 전환 = 새 정의 발행이다. 세션은 생성 시점의
-- scoreVersion에 고정되므로 전환 전 세션은 그대로 {same_content_as}의 점수 버전으로 집계된다.
--
-- 활성 전환은 이 파일이 하지 않는다. 2단계 롤아웃(KAN-26)이라 새 정의를 먼저 배포하고
-- 활성 전환은 그 다음 PUT /admin/v0/active-version 호출이다 - 순서가 뒤집히면 배포 중
-- 신규 버전 세션이 구 인스턴스에 닿아 404를 받는다 (KAN-101)."""


def sentences_header(definition: dict, sentences_from: str, voices: int, vocabulary: int,
                     sets: int, note: str) -> str:
    """음성 문장만 바꾼 재발행의 머리말 (KAN-210). note는 main이 만든 인계본 요약 줄이다."""
    return f"""\
-- KAN-210: {sentences_from}의 본문에 음성 문항만 KAN-159 전달본의 갱신 문장 목록(사람 선별 +
-- 읽기 쉬운 표준 철자)으로 바꾼 재발행 - 음성 {voices}문항 + 어휘 {vocabulary}문항 = 세트 {sets}개.
-- {note}
--
-- 남은 문항의 scriptKey와 guideF0, 어휘 문항(선택지 순서와 정답 choiceId)은 {sentences_from}과
-- 바이트 단위로 같다. 인계본이 어절 수를 지키며 철자만 고쳤고 참조 본체(08-31b)도 그대로라 곡선과
-- 참조는 다시 내지 않는다. 선택지 섞기 시드를 원본으로 고정해 만들었다. 이 파일은 손으로 쓰지
-- 않는다 - tools/content/build_definition.py --sentences 가 만든다.
--
-- 새 testVersion을 발행하는 이유는 정의가 발행 후 불변이라서다(KAN-26). 대본 한 글자를 고치는
-- 것도 새 정의 발행이다. scoreVersion은 {definition["scoreVersion"]} 그대로다.
--
-- 활성 전환은 이 파일이 하지 않는다. 2단계 롤아웃(KAN-26)이라 새 정의를 먼저 배포하고
-- 활성 전환은 그 다음 PUT /admin/v0/active-version 호출이다 - 순서가 뒤집히면 배포 중
-- 신규 버전 세션이 구 인스턴스에 닿아 404를 받는다 (KAN-101)."""


def first_content_header(previous_version: str, voices: int, vocabulary: int, sets: int) -> str:
    """첫 실콘텐츠 발행(KAN-182)의 머리말."""
    return f"""\
-- KAN-182: 정본 콘텐츠 발행 - 음성 {voices}문항 + 어휘 {vocabulary}문항 = 세트 {sets}개.
--
-- 더미 정의({previous_version})를 대신할 첫 실콘텐츠다. 음성 문장과 scriptKey는 KAN-159
-- 전달본, 가이드 곡선은 KAN-17 산출물(guide_f0_2026-09-04.json), 어휘는
-- tools/content/vocabulary_gn.py가 정본이다. 이 파일은 손으로 쓰지 않는다 -
-- tools/content/build_definition.py가 그 셋을 합쳐 만든다.
--
-- 가이드 곡선에 허용 밴드가 없다. KAN-17 산출물의 1안이 중앙선만 내기로 했고(박재영
-- 2026-09-04), 그에 맞춰 발행 검증의 bandLow·bandHigh를 optional로 되돌렸다
-- (TestDefinitionRegistry.validateVoice - 2026-08-09 확정을 뒤집은 것이다).
--
-- guideF0.frameIntervalMs는 산출물의 실수를 반올림한 값이다 (2026-09-04 결정). 발행본
-- 스키마와 앱·웹이 정수로 읽고, 가이드 레인은 자기 길이로 폭 전체를 쓰므로 오차가 화면에
-- 드러나지 않는다. values의 null은 무성 구간과 유효 발화 10명 미만인 칸이고 프론트가
-- 끊어 그린다 (KAN-102 AC3).
--
-- 활성 전환은 이 파일이 하지 않는다. 2단계 롤아웃(KAN-26)이라 새 정의를 먼저 배포하고
-- 활성 전환은 그 다음 PUT /admin/v0/active-version 호출이다 - 순서가 뒤집히면 배포 중
-- 신규 버전 세션이 구 인스턴스에 닿아 404를 받는다 (KAN-101)."""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--guide-f0", type=Path,
                        help="KAN-17 가이드 곡선 산출물 JSON. 갈아 끼우는 자리는 여기다 "
                             "(--reissue-from이 아니면 필수)")
    parser.add_argument("--test-version", default="gn-2026.09.1")
    parser.add_argument("--score-version", default="sv-0.3")
    parser.add_argument("--dialect", default="GYEONGNAM")
    parser.add_argument("--published-at", default="2026-09-04T00:00:00Z")
    parser.add_argument("--previous-version", default="gn-2026.08.1",
                        help="첫 실콘텐츠 머리말에서 '대신하는 더미 정의'로 적는 이름 (옛 V6 기록용). "
                             "--same-content-as, --sentences 재발행의 머리말에는 쓰이지 않는다")
    parser.add_argument("--same-content-as", metavar="TEST_VERSION",
                        help="점수 버전만 바꾼 재발행 - 이 버전의 선택지 순서를 그대로 쓰고 "
                             "머리말을 재발행용으로 바꾼다 (KAN-200)")
    parser.add_argument("--sentences", type=Path, metavar="HANDOFF_JSON",
                        help="KAN-159 전달본 문장 목록 - 가이드 곡선 문장의 대본을 script_key로 "
                             "덮어쓴다 (KAN-210)")
    parser.add_argument("--sentences-from", metavar="TEST_VERSION",
                        help="--sentences 재발행의 원본 testVersion - 머리말에 적는다 (KAN-210)")
    parser.add_argument("--include-excluded", metavar="KEY[,KEY...]", default="",
                        help="\"어절이 통째로 빈 문장\" 가운데 출시 문항으로 되돌릴 script_key - "
                             "음성 풀을 어휘 풀 크기에 맞출 때 쓴다 (KAN-210)")
    parser.add_argument("--choice-seed", metavar="TEST_VERSION",
                        help="어휘 선택지 섞기 시드 (생략하면 testVersion). 대본만 바꾼 재발행은 "
                             "원본 testVersion을 넘겨 어휘 문항을 그대로 둔다 (KAN-210)")
    parser.add_argument("--reissue-from", type=Path, metavar="MIGRATION_SQL",
                        help="재료 대신 이 마이그레이션에 발행된 --same-content-as 버전의 본문에서 "
                             "문항을 그대로 옮긴다 (KAN-260)")
    parser.add_argument("--curation", action="store_true",
                        help="--reissue-from 재발행에 검수 결과(curation_gn_2026_10_2.py)를 적용한다 (KAN-276)")
    parser.add_argument("--set-layout", metavar="PATTERN",
                        help="세트 하나의 구성과 출제 순서 - V(음성)와 W(어휘)의 문자열 (KAN-260). "
                             "--reissue-from과 함께 쓴다")
    parser.add_argument("--estimated-duration-sec", type=int, default=ESTIMATED_DURATION_SEC,
                        help=f"예상 소요 시간 (기본 {ESTIMATED_DURATION_SEC})")
    parser.add_argument("--out", type=Path, help="마이그레이션 SQL 경로 (생략하면 표준출력)")
    parser.add_argument("--json-out", type=Path, help="발행본 JSON도 따로 남길 경로")
    args = parser.parse_args()

    if args.reissue_from:
        reissue(args)
        return
    if args.guide_f0 is None:
        raise SystemExit("--guide-f0가 필요하다 (--reissue-from 재발행이 아니면)")
    if args.set_layout:
        raise SystemExit("--set-layout은 --reissue-from과 함께 쓴다")

    included = [k.strip() for k in args.include_excluded.split(",") if k.strip()]
    sentences, excluded = load_guide(args.guide_f0, included)
    if args.same_content_as == args.test_version:
        raise SystemExit("--same-content-as는 다른 testVersion이어야 한다 - 정의는 발행 후 불변이다 (KAN-26)")
    if args.same_content_as and args.sentences:
        raise SystemExit("--same-content-as와 --sentences는 같이 쓸 수 없다 - 대본이 바뀌면 같은 본문이 아니다")
    if bool(args.sentences) != bool(args.sentences_from):
        raise SystemExit("--sentences와 --sentences-from은 같이 써야 한다 - 머리말이 원본 testVersion을 적는다")
    if args.sentences_from == args.test_version:
        raise SystemExit("--sentences-from은 다른 testVersion이어야 한다 - 정의는 발행 후 불변이다 (KAN-26)")
    changed, dropped = apply_sentences(sentences, args.sentences) if args.sentences else ([], [])
    note = ""
    if args.sentences:
        stamp = json.loads(args.sentences.read_text(encoding="utf-8")).get("stamp", "?")
        note = (f"인계본 {args.sentences.name}(stamp {stamp}): 대본이 바뀐 문항 {len(changed)}개, "
                f"인계본에서 빠져 뺀 문항 {len(dropped)}개({' '.join(dropped) or '없음'}), "
                f"곡선 결측이라 빼 뒀다가 되돌린 문항 {len(included)}개({' '.join(included) or '없음'}).")
    items = build_items(sentences, WORDS, args.choice_seed or args.same_content_as or args.test_version)
    definition = {
        "testVersion": args.test_version,
        "scoreVersion": args.score_version,
        "dialect": args.dialect,
        "estimatedDurationSec": args.estimated_duration_sec,
        "items": items,
    }

    sql = migration_sql(definition, args.published_at, args.previous_version, args.same_content_as,
                        args.sentences_from, note)
    if args.out:
        args.out.write_text(sql, encoding="utf-8")
    else:
        print(sql)
    if args.json_out:
        args.json_out.write_text(json.dumps(definition, ensure_ascii=False, indent=2),
                                 encoding="utf-8")

    report(sentences, excluded, definition, args, sql, changed, dropped, included)


def published_definition(migration: Path, test_version: str) -> dict:
    """마이그레이션 파일에서 test_version 행의 본문을 찾아 읽는다 (KAN-260 --reissue-from)."""
    text = migration.read_text(encoding="utf-8")
    marker = f"values ('{test_version}',"
    at = text.find(marker)
    if at < 0:
        raise SystemExit(f"{migration}에 {test_version} 발행 행이 없다")
    start = text.index("$definition$", at) + len("$definition$")
    end = text.index("$definition$", start)
    return json.loads(text[start:end])


def validate_layout(pattern: str, voices: int, vocabulary: int) -> tuple[int, int]:
    """setLayout 형식과 풀 크기를 백엔드 발행 검증(SetLayout, TestDefinitionRegistry)과 같게 본다."""
    if not pattern or set(pattern) - {"V", "W"} or "V" not in pattern or "W" not in pattern:
        raise SystemExit(f"--set-layout은 V와 W로만, 각각 하나 이상 있어야 한다: {pattern}")
    v, w = pattern.count("V"), pattern.count("W")
    if voices < v or vocabulary < w:
        raise SystemExit(f"풀이 세트보다 작다: 음성 {voices} < {v} 또는 어휘 {vocabulary} < {w}")
    return v, w


def curated_items(source: dict, choice_seed: str) -> list[dict]:
    """검수 결과(curation_gn_2026_10_2.py)를 원본 정의에 적용한 문항 목록 (KAN-276).

    음성은 원본 문항에서 삭제분을 빼고 고친 문장만 바꾼다 - itemId, scriptKey, guideF0는 원본 그대로다.
    고친 문장은 검수 문서의 원문이 원본 문장과 같고 어절 수가 같아야 한다 (곡선이 어절당 20점 격자).
    어휘는 검수본으로 새로 만든다 - 남은 낱말은 원본 itemId와 물음을 물려받고, 신규는 원본 풀 다음
    번호(w146)부터 매긴다. 물려받는 itemId는 원본 문항 문구의 낱말로 찾는다. 선택지는 choice_seed로 섞는다.
    seq는 원본처럼 음성과 어휘를 번갈아 매기고, 짧은 풀이 끝나면 남은 쪽을 잇는다.
    """
    from curation_gn_2026_10_2 import VOCABULARY, VOICE_DELETED, VOICE_EDITED

    items = sorted(source["items"], key=lambda item: item["seq"])
    source_voices = [item for item in items if item["type"] == "VOICE"]
    source_words = [item for item in items if item["type"] == "VOCABULARY"]
    voice_ids = {item["itemId"] for item in source_voices}
    unknown = [i for i in [*VOICE_DELETED, *VOICE_EDITED] if i not in voice_ids]
    if unknown:
        raise SystemExit(f"검수 결과의 음성 itemId가 원본에 없다: {' '.join(unknown)}")
    overlap = set(VOICE_DELETED) & set(VOICE_EDITED)
    if overlap:
        raise SystemExit(f"삭제와 수정에 함께 있는 음성 문항: {' '.join(sorted(overlap))}")

    voices: list[dict] = []
    for item in source_voices:
        if item["itemId"] in VOICE_DELETED:
            continue
        item = dict(item)
        if item["itemId"] in VOICE_EDITED:
            before, after = VOICE_EDITED[item["itemId"]]
            if item["prompt"] != before:
                raise SystemExit(f"{item['itemId']} 검수 문서의 원문이 원본과 다르다:\n"
                                 f"  원본: {item['prompt']}\n  문서: {before}")
            if len(after.split()) != len(before.split()):
                raise SystemExit(f"{item['itemId']} 고친 문장의 어절 수가 다르다 (곡선 격자와 어긋난다): {after}")
            item["prompt"] = after
        voices.append(item)

    # 원본 문구는 "'낱말'는 표준어로 무엇일까요?" 꼴이다 - 첫 따옴표 쌍 안이 낱말이다.
    by_word = {item["prompt"].split("'")[1]: item for item in source_words}
    rng = random.Random(choice_seed)
    next_new = len(source_words) + 1
    words: list[dict] = []
    seen_ids: set[str] = set()
    for _category, dialect, answer, wrong, origin in VOCABULARY:
        if origin is None:
            item_id = f"w{next_new}"
            next_new += 1
            ask = "뜻"  # 신규가 든 네 분류는 원본에서 전부 "뜻" 물음이다
        else:
            if origin not in by_word:
                raise SystemExit(f"검수본의 원래 낱말 '{origin}'이 원본 어휘 문항에 없다")
            item_id = by_word[origin]["itemId"]
            ask = "표준어" if by_word[origin]["prompt"].endswith("표준어로 무엇일까요?") else "뜻"
        if item_id in seen_ids:
            raise SystemExit(f"검수본에서 같은 원래 낱말을 두 번 썼다: {origin}")
        seen_ids.add(item_id)
        if len({answer, *wrong}) != 1 + len(wrong):
            raise SystemExit(f"'{dialect}'의 선택지가 겹친다: {answer}, {wrong}")
        words.append(vocabulary_item(item_id, 0, (dialect, answer, wrong, ask, None, None), rng))

    dialects = [entry[1] for entry in VOCABULARY]
    if len(set(dialects)) != len(dialects):
        raise SystemExit("검수본에 같은 사투리 낱말이 두 번 있다")
    prompts = [voice["prompt"] for voice in voices]
    if len(set(prompts)) != len(prompts):
        raise SystemExit("음성 문장이 겹친다")

    result: list[dict] = []
    for index in range(max(len(voices), len(words))):
        for pool in (voices, words):
            if index < len(pool):
                result.append({**pool[index], "seq": len(result) + 1})
    return result


def require_distinct_sets(items: list[dict], v: int, w: int) -> None:
    """VoiceSets.derive와 같은 순환 규칙으로 세트를 나눠, 한 세트 안에 같은 문구가 두 번 오지 않는지 본다 (KAN-276)."""
    voices = [i["prompt"] for i in sorted(items, key=lambda i: i["seq"]) if i["type"] == "VOICE"]
    words = [i["prompt"] for i in sorted(items, key=lambda i: i["seq"]) if i["type"] == "VOCABULARY"]
    sets = max(-(-len(voices) // v), -(-len(words) // w))
    for k in range(sets):
        picked = ([voices[(k * v + o) % len(voices)] for o in range(v)]
                  + [words[(k * w + o) % len(words)] for o in range(w)])
        if len(set(picked)) != len(picked):
            raise SystemExit(f"세트 {k + 1}에 같은 문구가 두 번 들어간다: {picked}")


def reissue(args) -> None:
    """이미 발행된 본문의 문항을 그대로 두고 버전 속성만 바꾼 재발행 (KAN-260).

    --curation이면 문항을 검수 결과로 바꾼 재발행이다 (KAN-276). 바이트 대조 대신 curated_items의
    원문 대조와 세트 안 중복 검사를 한다.
    """
    if not args.same_content_as:
        raise SystemExit("--reissue-from에는 원본 testVersion(--same-content-as)이 필요하다")
    if args.same_content_as == args.test_version:
        raise SystemExit("--same-content-as는 다른 testVersion이어야 한다 - 정의는 발행 후 불변이다 (KAN-26)")
    if args.sentences or args.sentences_from or args.guide_f0:
        raise SystemExit("--reissue-from은 재료(--guide-f0, --sentences)를 읽지 않는다")
    source = published_definition(args.reissue_from, args.same_content_as)
    if args.curation:
        if not args.set_layout:
            raise SystemExit("--curation에는 --set-layout이 필요하다")
        source = {**source, "items": curated_items(source, args.choice_seed or args.test_version)}
    voices = sum(1 for item in source["items"] if item["type"] == "VOICE")
    vocabulary = len(source["items"]) - voices

    definition = {
        "testVersion": args.test_version,
        "scoreVersion": args.score_version,
        "dialect": source["dialect"],
        "estimatedDurationSec": args.estimated_duration_sec,
    }
    if args.set_layout:
        v, w = validate_layout(args.set_layout, voices, vocabulary)
        definition["setLayout"] = args.set_layout
    else:
        v, w = SET_SIZE, SET_SIZE
    definition["items"] = source["items"]

    if args.curation:
        require_distinct_sets(definition["items"], v, w)
        header = curation_header(definition, args.same_content_as, voices, vocabulary, v, w,
                                 -(-voices // v), -(-vocabulary // w))
    else:
        # 문항 본문이 원본과 바이트 단위로 같아야 한다 - 같은 직렬화 규칙으로 다시 써서 대조한다.
        same = (json.dumps(definition["items"], ensure_ascii=False, indent=2)
                == json.dumps(source["items"], ensure_ascii=False, indent=2))
        if not same:
            raise SystemExit("문항 본문이 원본과 다르다")

    sets = max(-(-voices // v), -(-vocabulary // w))
    if not args.curation:
        header = layout_header(definition, args.same_content_as, voices, vocabulary, v, w, sets)
    body = json.dumps(definition, ensure_ascii=False, indent=2)
    if "$definition$" in body:
        raise SystemExit("본문에 달러 인용 구분자가 들어 있다 - 다른 구분자를 써야 한다")
    sql = f"""\
{header}
insert into test_definition (test_version, dialect, score_version, body, published_at)
values ('{definition["testVersion"]}', '{definition["dialect"]}', '{definition["scoreVersion"]}', $definition${body}$definition$,
        timestamp with time zone '{args.published_at}');
"""
    if args.out:
        args.out.write_text(sql, encoding="utf-8")
    else:
        print(sql)
    if args.json_out:
        args.json_out.write_text(body, encoding="utf-8")
    print(f"[발행본] {definition['testVersion']} 음성 {voices} + 어휘 {vocabulary},"
          f" 세트 구성 {args.set_layout or 'VWVWVWVWVW'} = 세트 {sets}개", file=sys.stderr)
    if args.curation:
        print(f"[대조]   검수 원문이 {args.same_content_as}과 같고, 세트 {sets}개 안에 같은 문구가 없다",
              file=sys.stderr)
    else:
        print(f"[대조]   문항 {len(definition['items'])}개가 {args.same_content_as}과 바이트 단위로 같다",
              file=sys.stderr)
    print(f"[크기]   마이그레이션 {len(sql.encode('utf-8')) / 1024:.0f}KB", file=sys.stderr)


def curation_header(definition: dict, same_content_as: str, voices: int, vocabulary: int,
                    v: int, w: int, voice_sets: int, vocabulary_sets: int) -> str:
    """검수 결과로 문항을 바꾼 재발행의 머리말 (KAN-276)."""
    layout = definition["setLayout"]
    sets = max(voice_sets, vocabulary_sets)
    smaller = "어휘" if vocabulary_sets < voice_sets else "음성"
    return f"""\
-- KAN-276: {same_content_as}의 문항을 사람 검수 결과로 바꾼 재발행 - 세트 구성 {layout}(음성 {v} + 어휘 {w}),
-- 음성 {voices}문항 + 어휘 {vocabulary}문항 = 세트 {sets}개.
--
-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_definition.py --reissue-from --curation 이
-- {same_content_as}의 본문에 tools/content/curation_gn_2026_10_2.py(검수 결과)를 적용해 만든다.
--
-- 음성은 {same_content_as}에서 삭제분을 빼고 일부 문장만 고쳤다. 고친 문장은 어절 수가 원문과 같아 itemId,
-- scriptKey, guideF0는 원본 그대로다. 어휘는 검수본으로 다시 만들었다 - 남은 낱말은 원본 itemId를 물려받고
-- (사투리 표기나 정답을 고친 것 포함), 신규 낱말은 w146부터다. 오답은 검수본에서 전부 새로 썼다.
--
-- 두 풀의 크기가 달라 {smaller} 풀이 세트를 넘어 처음부터 되풀이된다 (VoiceSets 순환 규칙). 한 세트 안에
-- 같은 문구가 두 번 오지 않는 것은 빌드할 때 검사했다. scoreVersion은 채점 규칙이 같아 그대로다.
--
-- 활성 전환은 이 파일이 하지 않는다. 2단계 롤아웃(KAN-26)이라 새 정의를 먼저 배포하고
-- 활성 전환은 그 다음 PUT /admin/v0/active-version 호출이다."""


def layout_header(definition: dict, same_content_as: str, voices: int, vocabulary: int,
                  v: int, w: int, sets: int) -> str:
    """세트 구성을 바꾼 재발행의 머리말 (KAN-260)."""
    layout = definition.get("setLayout", "VWVWVWVWVW")
    return f"""\
-- KAN-260: {same_content_as}의 문항 본문 그대로 세트 구성을 {layout}(음성 {v} + 어휘 {w} = {v + w}문항)로
-- 바꾼 재발행 - 음성 {voices}문항 + 어휘 {vocabulary}문항 = 세트 {sets}개.
--
-- 문항 본문(문장, scriptKey, guideF0, 어휘 선택지와 정답)은 {same_content_as}과 바이트 단위로 같다.
-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_definition.py --reissue-from 이 발행된
-- {same_content_as}의 본문을 읽어 만들고, 실행할 때마다 문항 본문을 원본과 대조한다.
--
-- 바뀐 것은 버전 속성 넷이다: testVersion, scoreVersion({definition["scoreVersion"]}), estimatedDurationSec
-- ({definition["estimatedDurationSec"]}), setLayout. 세트 구성을 코드가 아니라 본문에 싣는 것은 규칙도 정의와 함께
-- 발행 후 불변이어야 해서다 (KAN-26) - 필드가 없는 기존 정의는 음성 5 + 어휘 5 교차로 읽혀 세트와
-- 응답 본문, ETag가 그대로이고, 배포 전에 만든 10문항 세션도 10문항으로 완료되고 재집계된다.
--
-- 활성 전환은 이 파일이 하지 않는다. 2단계 롤아웃(KAN-26)이라 새 정의를 먼저 배포하고
-- 활성 전환은 그 다음 PUT /admin/v0/active-version 호출이다. 이 정의는 그에 더해 앱과 웹이
-- 문항 수를 세트 응답에서 읽게 된 뒤(KAN-261)에 전환한다."""


def report(sentences, excluded, definition, args, sql, changed, dropped, included) -> None:
    """반올림 오차와 크기를 표준출력에 남긴다 - 발행 전에 눈으로 확인할 값이다."""
    if args.sentences:
        print(f"[대본]   인계본으로 바뀐 음성 문항 {len(changed)}개 / {len(sentences)}", file=sys.stderr)
        print(f"[삭제]   인계본에서 빠져 뺀 문항 {len(dropped)}개: {' '.join(dropped)}", file=sys.stderr)
        print(f"[복귀]   곡선 결측 목록에서 되돌린 문항 {len(included)}개: {' '.join(included)}", file=sys.stderr)
        for key, old, new in changed:
            print(f"         {key}\n           전: {old}\n           후: {new}", file=sys.stderr)
    errors = []
    for s in sentences:
        interval = s["guideF0"]["frameIntervalMs"]
        count = len(s["guideF0"]["values"])
        exact = interval * (count - 1)
        rounded = round(interval) * (count - 1)
        errors.append(abs(rounded - exact) / exact * 100)
    errors.sort()

    voices = sum(1 for item in definition["items"] if item["type"] == "VOICE")
    vocabulary = len(definition["items"]) - voices
    print(f"[발행본] {definition['testVersion']}"
          f" 음성 {voices} + 어휘 {vocabulary} = 세트 {(max(voices, vocabulary) + 4) // 5}개",
          file=sys.stderr)
    print(f"[제외]   어절이 통째로 빈 문장 {len(excluded)}개: {' '.join(excluded)}", file=sys.stderr)
    print(f"[반올림] frameIntervalMs 곡선 길이 오차 중앙 {errors[len(errors) // 2]:.2f}%"
          f" · 최대 {errors[-1]:.2f}%", file=sys.stderr)
    nulls = sum(1 for s in sentences if any(v is None for v in s["guideF0"]["values"]))
    print(f"[결측]   values에 null이 있는 문장 {nulls}개 (프론트가 끊어 그린다)", file=sys.stderr)
    print(f"[크기]   마이그레이션 {len(sql.encode('utf-8')) / 1024:.0f}KB", file=sys.stderr)


if __name__ == "__main__":
    main()
