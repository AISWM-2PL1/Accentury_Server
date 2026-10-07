#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""단어 학습 발행본 JSON과 Flyway 마이그레이션을 만든다 (KAN-265, API 명세서 §3.16).

레벨테스트 어휘 풀(vocabulary_gn.py의 WORDS, 145개)을 원천으로 학습용 발행본을 따로 낸다
(2026-10-07 결정 - 별도 풀을 새로 만들지 않고 재사용한다). 발행본은 발행 후 불변이라 기존
행을 UPDATE하지 않고, 내용이 바뀌면 새 contentVersion으로 다시 발행한다.

세트와 레벨 (2026-10-07 결정)
    세트     vocabulary_gn.py의 분류 주석("# ── 음식과 식재료 ──")이 세트 단위다. 한 분류가
             MAX_CARDS_PER_SET(10)를 넘으면 균등하게 쪼갠다 (23개면 8 + 8 + 7).
    레벨     낱말의 근거를 점수로 본다 - 코퍼스(대화에 실제로 나온 낱말) 0, 일반 1, 사전 2.
             세트 레벨 = 1 + round(세트 평균 점수 x 2). 분류 안에서 점수 오름차순으로 정렬한
             뒤 쪼개므로 앞 세트가 쉬운 세트다.
    문항     표준어 → 사투리 한 방향. 카드 하나에 문항 하나이고 보기 4개가 전부 사투리 낱말이다.
             오답 3개는 같은 분류의 다른 낱말에서 contentVersion을 시드로 뽑는다 - 같은 버전이면
             언제 돌려도 같은 발행본이 나온다.
    해설     템플릿 문구("'정구지'는 경남에서 '부추'를 이르는 말입니다."). 낱말별 손 해설은 후속
             티켓이고 새 발행본으로만 바꾼다.

사용 (첫 발행)
    python3 build_word_learning.py --content-version wd-gn-2026.10.1 \\
        --published-at 2026-10-07T00:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V8__wd_gn_2026_10_1_word_learning.sql
"""

from __future__ import annotations

import argparse
import json
import math
import random
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from vocabulary_gn import WORDS  # noqa: E402

DIALECT = "GYEONGNAM"
MAX_CARDS_PER_SET = 10
CHOICE_COUNT = 4
CHOICE_LABELS = "abcd"
SOURCE_SCORE = {"코퍼스": 0, "일반": 1, "사전": 2}
CATEGORY_MARKER = re.compile(r"^\s*#\s*─+\s*(.+?)\s*─+\s*$")
ENTRY_START = re.compile(r'^\s*\("([^"]+)",')


def categorized_words() -> list[tuple[str, tuple]]:
    """(분류, 낱말 튜플) 목록 - 분류는 vocabulary_gn.py의 주석 구분선에서 읽는다.

    WORDS에는 분류 필드가 없다(레벨테스트는 분류를 쓰지 않는다). 파일의 항목 순서와 WORDS의
    순서가 같다는 것을 사투리 낱말로 한 줄씩 대조한다 - 어긋나면 세트가 엉뚱한 분류를 받는다.
    """
    source = Path(__file__).resolve().parent / "vocabulary_gn.py"
    category = None
    result: list[tuple[str, tuple]] = []
    for line in source.read_text(encoding="utf-8").splitlines():
        marker = CATEGORY_MARKER.match(line)
        if marker:
            category = marker.group(1)
            continue
        entry = ENTRY_START.match(line)
        if not entry:
            continue
        if category is None:
            raise SystemExit(f"분류 주석 앞에 항목이 있다: {line.strip()}")
        index = len(result)
        if index >= len(WORDS) or WORDS[index][0] != entry.group(1):
            expected = WORDS[index][0] if index < len(WORDS) else "(없음)"
            raise SystemExit(f"{index + 1}번째 항목이 WORDS와 어긋난다: 파일 {entry.group(1)!r}, WORDS {expected!r}")
        result.append((category, WORDS[index]))
    if len(result) != len(WORDS):
        raise SystemExit(f"분류를 읽은 항목 {len(result)}개와 WORDS {len(WORDS)}개가 다르다")
    return result


def object_particle(word: str) -> str:
    """목적격 조사 - 받침이 있으면 "을", 없으면 "를". 한글이 아니면 "를"."""
    last = word[-1]
    if not ("가" <= last <= "힣"):
        return "를"
    return "을" if (ord(last) - 0xAC00) % 28 else "를"


def topic_particle(word: str) -> str:
    """보조사 - 받침이 있으면 "은", 없으면 "는"."""
    last = word[-1]
    if not ("가" <= last <= "힣"):
        return "는"
    return "은" if (ord(last) - 0xAC00) % 28 else "는"


def balanced_chunks(count: int, max_size: int) -> list[int]:
    """count개를 max_size 이하로 가장 고르게 나눈 크기 목록 (23, 10 → [8, 8, 7])."""
    parts = math.ceil(count / max_size)
    base, extra = divmod(count, parts)
    return [base + (1 if i < extra else 0) for i in range(parts)]


def level_of(words: list[tuple]) -> int:
    mean = sum(SOURCE_SCORE[w[4]] for w in words) / len(words)
    return 1 + math.floor(mean * 2 + 0.5)


def build_sets(content_version: str) -> list[dict]:
    entries = categorized_words()
    categories: dict[str, list[tuple]] = {}
    for category, word in entries:
        categories.setdefault(category, []).append(word)

    rng = random.Random(content_version)
    sets: list[dict] = []
    for category, words in categories.items():
        if len(words) < CHOICE_COUNT:
            raise SystemExit(f"분류 '{category}'의 낱말이 {len(words)}개라 오답 3개를 뽑을 수 없다")
        ordered = sorted(words, key=lambda w: SOURCE_SCORE[w[4]])  # 안정 정렬 - 같은 점수는 원천 순서
        sizes = balanced_chunks(len(ordered), MAX_CARDS_PER_SET)
        offset = 0
        for part, size in enumerate(sizes, start=1):
            chunk = ordered[offset:offset + size]
            offset += size
            seq = len(sets) + 1
            set_id = f"ws{seq:02d}"
            title = category if len(sizes) == 1 else f"{category} {part}"
            cards = []
            items = []
            for index, word in enumerate(chunk, start=1):
                dialect, standard = word[0], word[1]
                card_id = f"{set_id}c{index:02d}"
                item_id = f"{set_id}q{index:02d}"
                cards.append({"cardId": card_id, "standard": standard, "dialect": dialect})
                pool = [w[0] for w in words if w[0] != dialect]
                texts = [dialect, *rng.sample(pool, CHOICE_COUNT - 1)]
                rng.shuffle(texts)
                choices = [{"choiceId": item_id + CHOICE_LABELS[i], "text": text}
                           for i, text in enumerate(texts)]
                items.append({
                    "itemId": item_id,
                    "seq": index,
                    "cardId": card_id,
                    "prompt": f"'{standard}'{object_particle(standard)} 경남 사투리로 무엇이라 할까요?",
                    "choices": choices,
                    "correctChoiceId": choices[texts.index(dialect)]["choiceId"],
                    "explanation": f"'{dialect}'{topic_particle(dialect)} 경남에서 '{standard}'"
                                   f"{object_particle(standard)} 이르는 말입니다.",
                })
            sets.append({
                "setId": set_id,
                "seq": seq,
                "level": level_of(chunk),
                "category": category,
                "title": title,
                "cards": cards,
                "items": items,
            })
    return sets


def migration_sql(definition: dict, published_at: str) -> str:
    body = json.dumps(definition, ensure_ascii=False, indent=2)
    if "$definition$" in body:
        raise SystemExit("본문에 달러 인용 구분자가 들어 있다 - 다른 구분자를 써야 한다")
    version = definition["contentVersion"]
    sets = definition["sets"]
    cards = sum(len(s["cards"]) for s in sets)
    levels = {}
    for s in sets:
        levels[s["level"]] = levels.get(s["level"], 0) + 1
    level_summary = ", ".join(f"레벨 {level} {count}개" for level, count in sorted(levels.items()))
    return (
        f"-- KAN-265: 단어 학습 발행본 {version} - 어휘 세트 {len(sets)}개, 카드 {cards}장 ({level_summary}).\n"
        "--\n"
        "-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_word_learning.py가 레벨테스트 어휘 풀\n"
        "-- (vocabulary_gn.py)에서 만든다. 세트는 분류별(10카드 상한), 레벨은 낱말 근거의 평균, 문항은\n"
        "-- 표준어 → 사투리 한 방향이고 오답은 같은 분류에서 contentVersion을 시드로 뽑는다 (명세서 §3.16).\n"
        "--\n"
        "-- 발행 후 불변이다 (§5.4와 같은 규칙). 해설이나 세트 구성이 바뀌면 이 행을 UPDATE하지 않고\n"
        "-- 새 contentVersion으로 INSERT한다 - 진행 중인 시도가 자기 버전의 문항을 계속 봐야 한다.\n"
        "-- 활성 전환 행은 없다 - 서버가 발행 시각이 가장 늦은 발행본을 목록과 상세에 쓴다.\n"
        "insert into word_learning_definition (content_version, dialect, body, published_at)\n"
        f"values ('{version}', '{definition['dialect']}', $definition${body}$definition$,\n"
        f"        timestamp with time zone '{published_at}');\n"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--content-version", required=True, help="예: wd-gn-2026.10.1")
    parser.add_argument("--published-at", required=True, help="ISO-8601 UTC, 예: 2026-10-07T00:00:00Z")
    parser.add_argument("--out", required=True, help="마이그레이션 SQL 출력 경로")
    parser.add_argument("--json-out", help="발행본 JSON도 따로 남길 경로 (선택)")
    args = parser.parse_args()

    definition = {
        "contentVersion": args.content_version,
        "dialect": DIALECT,
        "sets": build_sets(args.content_version),
    }
    Path(args.out).write_text(migration_sql(definition, args.published_at), encoding="utf-8")
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(definition, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    for s in definition["sets"]:
        print(f"{s['setId']} L{s['level']} {s['title']}: 카드 {len(s['cards'])}장")
    print(f"세트 {len(definition['sets'])}개 → {args.out}")


if __name__ == "__main__":
    main()
