#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""앱 학습 발행본 두 개(억양, 단어)를 실사용 난이도 순으로 만든다 (KAN-345, API 명세서 §3.16과 §3.17).

앱 학습 탭의 구간 5개(KAN-311 지도)에 맞춘 발행본이다. 한 구간은 억양 코스 5개와 단어 세트 4개, 합쳐서 9개다.
구간 번호는 발행본의 level(1부터 5)에 싣는다 - 서버와 앱이 이미 레벨로 묶고 추천하므로 응답 모양은 그대로다.

    억양   코스 25개 x 카드 3장 = 75장. level = 구간, 구간 안의 순서는 seq
    단어   세트 20개 x 문항 3개 = 60문항. 검수한 어휘가 72개라 세트당 4문항(80개)은 채우지 못한다 (2026-11-15 결정)

원천은 첫 발행본과 같다 - 억양은 레벨테스트 정의의 VOICE 문항, 단어는 검수한 어휘(curation_gn_2026_10_2.py)다.
새 문장과 새 낱말은 넣지 않는다. 달라진 것은 고르는 법과 순서다. 순서의 근거는 item_difficulty.py가 만든
집계 파일이다 (레벨테스트 실사용 기록).

고르는 법과 순서 (2026-11-15 결정)
    억양 후보   NaN 비율이 NAN_RATE_LIMIT(5%) 이상인 문항은 뺀다. NaN 비율 = unscorable / (completed + unscorable).
                채점이 자주 실패하는 문장은 학습 중에도 다시 녹음하라는 안내가 자주 뜬다.
    억양 쉬움   high / completed - 높은 점수(집계 파일의 highScore 이상)를 받은 응시의 비율. 클수록 쉽다.
    단어 쉬움   correct / answered - 정답률. 레벨테스트는 사투리 → 표준어로 묻고 학습은 표준어 → 사투리로 묻지만
                같은 낱말의 정답률을 그대로 쓴다. 학습 방향의 기록이 쌓이면 새 발행본에서 바꾼다.
    고르기      쉬운 순으로 세운 뒤 필요한 수보다 많으면 순위에서 같은 간격으로 뽑는다 (가장 쉬운 것과 가장 어려운
                것은 늘 들어간다). 한쪽 끝만 잘라내지 않아 난이도의 폭이 원천과 같다.
    묶기        뽑은 것을 쉬운 순 그대로 3개씩 자른다. 그래서 구간 1이 가장 쉽고 구간 5가 가장 어렵고, 구간 안에서도
                뒤로 갈수록 어렵다.
    동점        쉬움이 같으면 평균 점수(억양)가 높은 쪽, 그래도 같으면 원천 순서가 앞이다.

단어 문항의 모양은 첫 발행본과 같다 (build_word_learning.py) - 표준어 → 사투리 한 방향, 보기 4개가 전부 사투리
낱말이고 오답 3개는 같은 분류의 다른 낱말에서 contentVersion을 시드로 뽑는다. 세트가 분류를 섞으므로 세트의
category는 "종합"이다.

발행본은 발행 후 불변이다. 집계를 다시 뽑아 순서를 바꾸려면 새 contentVersion으로 발행한다.

사용
    python3 build_app_curriculum.py --difficulty difficulty_gn_2026_11.json \\
        --source-migration ../../backend/src/main/resources/db/migration/V15__gn_2026_10_2_voice_emotion.sql \\
        --intonation-version in-gn-2026.11.1 --word-version wd-gn-2026.11.1 \\
        --published-at 2026-11-15T00:00:00Z \\
        --intonation-out ../../backend/src/main/resources/db/migration/V20__in_gn_2026_11_1_app_curriculum.sql \\
        --word-out ../../backend/src/main/resources/db/migration/V21__wd_gn_2026_11_1_app_curriculum.sql \\
        --report-out curriculum_gn_2026_11.md
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_intonation_learning import source_voice_items  # noqa: E402
from build_word_learning import CHOICE_COUNT, CHOICE_LABELS, object_particle, topic_particle  # noqa: E402
from curation_gn_2026_10_2 import VOCABULARY  # noqa: E402

DIALECT = "GYEONGNAM"
STAGES = 5
COURSES_PER_STAGE = 5
CARDS_PER_COURSE = 3
SETS_PER_STAGE = 4
ITEMS_PER_SET = 3
NAN_RATE_LIMIT = 0.05
MIXED_CATEGORY = "종합"


def evenly_spaced(ordered: list, count: int) -> list:
    """ordered에서 count개를 순위 간격이 같게 뽑는다 - 양 끝은 늘 들어간다."""
    if len(ordered) < count:
        raise SystemExit(f"후보가 {len(ordered)}개라 {count}개를 뽑을 수 없다")
    if len(ordered) == count:
        return list(ordered)
    step = (len(ordered) - 1) / (count - 1)
    indexes = [int(i * step + 0.5) for i in range(count)]
    if len(set(indexes)) != count:
        raise SystemExit("같은 간격으로 뽑은 자리가 겹친다")
    return [ordered[i] for i in indexes]


def nan_rate(row: dict) -> float:
    scored = row["completed"] + row["unscorable"]
    return row["unscorable"] / scored if scored else 1.0


def voice_plan(difficulty: dict, items: list[dict]) -> tuple[list[dict], list[dict]]:
    """(뽑은 문항을 쉬운 순으로, NaN 비율로 뺀 문항) - 각 원소는 집계 행에 item과 ease, nanRate를 더한 것이다."""
    stats = {row["scriptKey"]: row for row in difficulty["voice"]}
    candidates, excluded = [], []
    for order, item in enumerate(items):
        row = stats.get(item["scriptKey"])
        if row is None or row["completed"] == 0:
            raise SystemExit(f"{item['itemId']}({item['scriptKey']})의 집계가 없다 - 집계 파일을 다시 만든다")
        entry = {**row, "item": item, "order": order, "nanRate": nan_rate(row),
                 "ease": row["high"] / row["completed"], "mean": row["scoreSum"] / row["completed"]}
        (excluded if entry["nanRate"] >= NAN_RATE_LIMIT else candidates).append(entry)
    candidates.sort(key=lambda e: (-e["ease"], -e["mean"], e["order"]))
    excluded.sort(key=lambda e: -e["nanRate"])
    return evenly_spaced(candidates, STAGES * COURSES_PER_STAGE * CARDS_PER_COURSE), excluded


def word_plan(difficulty: dict) -> list[dict]:
    """뽑은 낱말을 쉬운 순으로 - 각 원소는 집계 행에 분류, 표준어, ease를 더한 것이다."""
    stats = {row["dialect"]: row for row in difficulty["vocabulary"]}
    entries = []
    for order, (category, dialect, standard, _wrong, _origin) in enumerate(VOCABULARY):
        row = stats.get(dialect)
        if row is None or row["answered"] == 0:
            raise SystemExit(f"낱말 '{dialect}'의 집계가 없다 - 집계 파일을 다시 만든다")
        entries.append({**row, "category": category, "standard": standard, "order": order,
                        "ease": row["correct"] / row["answered"]})
    entries.sort(key=lambda e: (-e["ease"], e["order"]))
    return evenly_spaced(entries, STAGES * SETS_PER_STAGE * ITEMS_PER_SET)


def build_courses(plan: list[dict]) -> list[dict]:
    courses = []
    for index in range(0, len(plan), CARDS_PER_COURSE):
        seq = len(courses) + 1
        level = (seq - 1) // COURSES_PER_STAGE + 1
        course_id = f"ic{seq:02d}"
        courses.append({
            "courseId": course_id,
            "seq": seq,
            "level": level,
            "topic": None,
            "title": f"레벨 {level} 코스 {(seq - 1) % COURSES_PER_STAGE + 1}",
            "cards": [{
                "cardId": f"{course_id}c{number:02d}",
                "seq": number,
                "standard": None,
                "dialect": entry["item"]["prompt"],
                "scriptKey": entry["item"]["scriptKey"],
                "guideF0": entry["item"]["guideF0"],
                "referenceAudioPath": None,
            } for number, entry in enumerate(plan[index:index + CARDS_PER_COURSE], start=1)],
        })
    return courses


def build_sets(plan: list[dict], content_version: str) -> list[dict]:
    by_category: dict[str, list[str]] = {}
    for category, dialect, *_ in VOCABULARY:
        by_category.setdefault(category, []).append(dialect)
    rng = random.Random(content_version)
    sets = []
    for index in range(0, len(plan), ITEMS_PER_SET):
        seq = len(sets) + 1
        level = (seq - 1) // SETS_PER_STAGE + 1
        set_id = f"ws{seq:02d}"
        cards, items = [], []
        for number, entry in enumerate(plan[index:index + ITEMS_PER_SET], start=1):
            dialect, standard = entry["dialect"], entry["standard"]
            card_id = f"{set_id}c{number:02d}"
            item_id = f"{set_id}q{number:02d}"
            cards.append({"cardId": card_id, "standard": standard, "dialect": dialect})
            pool = [word for word in by_category[entry["category"]] if word != dialect]
            texts = [dialect, *rng.sample(pool, CHOICE_COUNT - 1)]
            rng.shuffle(texts)
            choices = [{"choiceId": item_id + CHOICE_LABELS[i], "text": text} for i, text in enumerate(texts)]
            items.append({
                "itemId": item_id,
                "seq": number,
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
            "level": level,
            "category": MIXED_CATEGORY,
            "title": f"레벨 {level} 단어 {(seq - 1) % SETS_PER_STAGE + 1}",
            "cards": cards,
            "items": items,
        })
    return sets


def migration_sql(table: str, definition: dict, header: list[str], published_at: str) -> str:
    body = json.dumps(definition, ensure_ascii=False, indent=2)
    if "$definition$" in body:
        raise SystemExit("본문에 달러 인용 구분자가 들어 있다 - 다른 구분자를 써야 한다")
    comment = "".join(f"-- {line}\n" if line else "--\n" for line in header)
    return (
        f"{comment}"
        f"insert into {table} (content_version, dialect, body, published_at)\n"
        f"values ('{definition['contentVersion']}', '{definition['dialect']}', $definition${body}$definition$,\n"
        f"        timestamp with time zone '{published_at}');\n"
    )


def common_header(difficulty: dict) -> list[str]:
    return [
        "",
        "이 파일은 손으로 쓰지 않는다 - tools/content/build_app_curriculum.py가 만든다. 순서의 근거는",
        f"tools/content/item_difficulty.py의 집계다 (레벨테스트 {difficulty['poolPrefix']}* 실사용 기록, "
        f"{difficulty['snapshotDate']} 기준).",
        "level은 앱 학습 탭의 구간 번호(1부터 5)다. 구간 1이 가장 쉽고, 구간 안에서도 seq가 클수록 어렵다.",
        "",
        "발행 후 불변이다 (§5.4와 같은 규칙). 순서나 구성이 바뀌면 이 행을 UPDATE하지 않고 새 contentVersion으로",
        "INSERT한다. 활성 전환 행은 없다 - 서버가 발행 시각이 가장 늦은 발행본을 목록과 상세에 쓴다. 그래서 이",
        "마이그레이션이 적용되면 앞선 발행본의 진도는 목록에서 빠지고(기록은 남는다) 새 발행본의 진도가 0부터다.",
    ]


def percent(value: float) -> str:
    return f"{value * 100:.1f}%"


def report(difficulty: dict, voice: list[dict], excluded: list[dict], courses: list[dict],
           words: list[dict], sets: list[dict], versions: tuple[str, str]) -> str:
    lines = [
        f"# 앱 학습 발행본 구성표 ({versions[0]}, {versions[1]})",
        "",
        "build_app_curriculum.py가 만든 파일이다. 손으로 고치지 않는다.",
        "",
        f"- 근거: 레벨테스트 {difficulty['poolPrefix']}* 실사용 기록, {difficulty['snapshotDate']} 기준",
        f"- 억양 쉬움: 점수 {difficulty['highScore']}점 이상을 받은 응시의 비율",
        f"- 억양 제외: NaN 비율 {NAN_RATE_LIMIT * 100:.0f}% 이상",
        "- 단어 쉬움: 정답률",
        "",
        "## 억양 코스",
        "",
        "| 구간 | 코스 | 카드 | 원천 문항 | 응시 | NaN 비율 | 쉬움 | 평균 점수 | 대사 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    cursor = iter(voice)
    for course in courses:
        for card in course["cards"]:
            entry = next(cursor)
            lines.append(f"| {course['level']} | {course['courseId']} | {card['cardId']} | {entry['itemId']} | "
                         f"{entry['completed']} | {percent(entry['nanRate'])} | {percent(entry['ease'])} | "
                         f"{entry['mean']:.1f} | {card['dialect']} |")
    lines += [
        "",
        f"## NaN 비율로 뺀 억양 문항 ({len(excluded)}개)",
        "",
        "| 원천 문항 | 응시 | NaN | NaN 비율 | 대사 |",
        "| --- | --- | --- | --- | --- |",
    ]
    for entry in excluded:
        lines.append(f"| {entry['itemId']} | {entry['completed']} | {entry['unscorable']} | "
                     f"{percent(entry['nanRate'])} | {entry['item']['prompt']} |")
    lines += [
        "",
        "## 단어 세트",
        "",
        "| 구간 | 세트 | 문항 | 원천 문항 | 응답 | 정답률 | 표준어 | 사투리 | 분류 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    cursor = iter(words)
    for word_set in sets:
        for item in word_set["items"]:
            entry = next(cursor)
            lines.append(f"| {word_set['level']} | {word_set['setId']} | {item['itemId']} | {entry['itemId']} | "
                         f"{entry['answered']} | {percent(entry['ease'])} | {entry['standard']} | "
                         f"{entry['dialect']} | {entry['category']} |")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--difficulty", required=True, type=Path, help="item_difficulty.py가 만든 집계 JSON")
    parser.add_argument("--source-migration", required=True, type=Path,
                        help="원천 테스트 정의가 든 마이그레이션 SQL")
    parser.add_argument("--intonation-version", required=True, help="예: in-gn-2026.11.1")
    parser.add_argument("--word-version", required=True, help="예: wd-gn-2026.11.1")
    parser.add_argument("--published-at", required=True, help="ISO-8601 UTC, 예: 2026-11-15T00:00:00Z")
    parser.add_argument("--intonation-out", required=True, type=Path, help="억양 마이그레이션 SQL 출력 경로")
    parser.add_argument("--word-out", required=True, type=Path, help="단어 마이그레이션 SQL 출력 경로")
    parser.add_argument("--report-out", type=Path, help="구성표(Markdown) 출력 경로 (선택)")
    args = parser.parse_args()

    difficulty = json.loads(args.difficulty.read_text(encoding="utf-8"))
    source_version = difficulty["sourceVersion"]
    voice, excluded = voice_plan(difficulty, source_voice_items(args.source_migration, source_version))
    words = word_plan(difficulty)
    courses = build_courses(voice)
    sets = build_sets(words, args.word_version)

    intonation = {"contentVersion": args.intonation_version, "dialect": DIALECT, "courses": courses}
    word = {"contentVersion": args.word_version, "dialect": DIALECT, "sets": sets}
    args.intonation_out.write_text(migration_sql("intonation_learning_definition", intonation, [
        f"KAN-345: 억양 학습 발행본 {args.intonation_version} - 구간 {STAGES}개 x 코스 {COURSES_PER_STAGE}개 x 카드 "
        f"{CARDS_PER_COURSE}장 = {len(voice)}장.",
        *common_header(difficulty),
        "",
        f"원천은 레벨테스트 정의 {source_version}의 VOICE 문항이다. NaN 비율이 {NAN_RATE_LIMIT * 100:.0f}% 이상인 "
        f"{len(excluded)}개를 빼고, 남은 문항을",
        f"쉬운 순(점수 {difficulty['highScore']}점 이상을 받은 응시의 비율)으로 세워 같은 간격으로 {len(voice)}개를 "
        "뽑았다. 대사, scriptKey, 가이드",
        "곡선은 원천 그대로이고 주제, 표준어 원문, 기준 음원은 전부 null이다 (명세서 §3.17).",
    ], args.published_at), encoding="utf-8")
    args.word_out.write_text(migration_sql("word_learning_definition", word, [
        f"KAN-345: 단어 학습 발행본 {args.word_version} - 구간 {STAGES}개 x 세트 {SETS_PER_STAGE}개 x 문항 "
        f"{ITEMS_PER_SET}개 = {len(words)}문항.",
        *common_header(difficulty),
        "",
        f"원천은 검수한 레벨테스트 어휘 {len(VOCABULARY)}개(curation_gn_2026_10_2.py)다. 정답률이 높은 순으로 세워 같은 "
        f"간격으로 {len(words)}개를",
        "뽑았다. 문항은 표준어 → 사투리 한 방향이고 오답은 같은 분류에서 contentVersion을 시드로 뽑는다 (명세서 §3.16).",
    ], args.published_at), encoding="utf-8")
    if args.report_out:
        args.report_out.write_text(
            report(difficulty, voice, excluded, courses, words, sets,
                   (args.intonation_version, args.word_version)), encoding="utf-8")

    for stage in range(1, STAGES + 1):
        stage_voice = voice[(stage - 1) * COURSES_PER_STAGE * CARDS_PER_COURSE:stage * COURSES_PER_STAGE * CARDS_PER_COURSE]
        stage_words = words[(stage - 1) * SETS_PER_STAGE * ITEMS_PER_SET:stage * SETS_PER_STAGE * ITEMS_PER_SET]
        print(f"구간 {stage}: 억양 쉬움 {percent(stage_voice[-1]['ease'])}~{percent(stage_voice[0]['ease'])}, "
              f"단어 정답률 {percent(stage_words[-1]['ease'])}~{percent(stage_words[0]['ease'])}")
    print(f"억양 코스 {len(courses)}개 (제외 {len(excluded)}개) → {args.intonation_out}")
    print(f"단어 세트 {len(sets)}개 → {args.word_out}")


if __name__ == "__main__":
    main()
