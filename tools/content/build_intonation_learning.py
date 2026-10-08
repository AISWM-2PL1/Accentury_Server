#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""억양 학습 발행본 JSON과 Flyway 마이그레이션을 만든다 (KAN-264, API 명세서 §3.17).

레벨테스트 음성 풀(이미 발행된 테스트 정의의 VOICE 문항)을 원천으로 학습용 발행본을 따로 낸다
(2026-10-08 결정 - 대사 원고를 새로 쓰지 않고 재사용한다). 사투리 대사, scriptKey, 가이드 곡선은
원천 문항의 값을 그대로 옮긴다. 발행본은 발행 후 불변이라 기존 행을 UPDATE하지 않고, 내용이
바뀌면 새 contentVersion으로 다시 발행한다.

코스와 레벨 (2026-10-08 결정)
    레벨     대사의 어절 수(띄어쓰기 단위)로 정한다 - 9 이하 1, 10은 2, 11은 3, 12는 4, 13 이상 5.
             원천에 레벨과 주제 정보가 없어 사람 판단이 들어가지 않는 기계적 규칙을 쓴다.
    코스     레벨 안에서 원천 문항 순서대로 MAX_CARDS_PER_COURSE(10) 이하로 균등하게 쪼갠다
             (28개면 10 + 9 + 9). 코스 seq는 레벨 오름차순이다.
    비어 있는 값
             topic(주제), standard(표준어 원문), referenceAudioPath(기준 음원)는 전부 null이다.
             원천에 없고 기억으로 쓰지 않는다. 채워지면 새 발행본으로 낸다.

사용 (KAN-276 덮어쓰기 - 원천을 검수한 gn-2026.10.2로. 첫 발행은 V5의 gn-2026.10.1이었다)
    python3 build_intonation_learning.py \\
        --source-migration ../../backend/src/main/resources/db/migration/V11__gn_2026_10_2_curated.sql \\
        --source-version gn-2026.10.2 --content-version in-gn-2026.10.1 \\
        --published-at 2026-10-08T00:00:00Z \\
        --out ../../backend/src/main/resources/db/migration/V10__in_gn_2026_10_1_intonation_learning.sql
"""

from __future__ import annotations

import argparse
import json
import math
import re
from pathlib import Path

DIALECT = "GYEONGNAM"
MAX_CARDS_PER_COURSE = 10
DOLLAR_QUOTED = re.compile(r"\$(\w*)\$(.*?)\$\1\$", re.S)


def level_of(dialect: str) -> int:
    """어절 수 → 레벨 1~5 (9 이하, 10, 11, 12, 13 이상)."""
    words = len(dialect.split())
    return min(max(words - 8, 1), 5)


def balanced_chunks(count: int, max_size: int) -> list[int]:
    """count개를 max_size 이하로 가장 고르게 나눈 크기 목록 (28, 10 → [10, 9, 9])."""
    parts = math.ceil(count / max_size)
    base, extra = divmod(count, parts)
    return [base + (1 if i < extra else 0) for i in range(parts)]


def source_voice_items(migration: Path, test_version: str) -> list[dict]:
    """마이그레이션 SQL에서 testVersion이 맞는 정의 본문을 찾아 VOICE 문항을 seq 순으로 돌려준다."""
    for match in DOLLAR_QUOTED.finditer(migration.read_text(encoding="utf-8")):
        try:
            body = json.loads(match.group(2))
        except json.JSONDecodeError:
            continue
        if isinstance(body, dict) and body.get("testVersion") == test_version:
            if body.get("dialect") != DIALECT:
                raise SystemExit(f"원천 정의의 dialect가 {body.get('dialect')}다 - 경남만 발행한다")
            items = sorted((i for i in body["items"] if i["type"] == "VOICE"), key=lambda i: i["seq"])
            if not items:
                raise SystemExit(f"{test_version}에 VOICE 문항이 없다")
            return items
    raise SystemExit(f"{migration}에서 testVersion {test_version}의 정의를 찾지 못했다")


def build_courses(items: list[dict]) -> list[dict]:
    by_level: dict[int, list[dict]] = {}
    for item in items:
        by_level.setdefault(level_of(item["prompt"]), []).append(item)

    courses: list[dict] = []
    for level in sorted(by_level):
        pool = by_level[level]
        offset = 0
        for part, size in enumerate(balanced_chunks(len(pool), MAX_CARDS_PER_COURSE), start=1):
            chunk = pool[offset:offset + size]
            offset += size
            seq = len(courses) + 1
            course_id = f"ic{seq:02d}"
            courses.append({
                "courseId": course_id,
                "seq": seq,
                "level": level,
                "topic": None,
                "title": f"레벨 {level} 코스 {part}",
                "cards": [{
                    "cardId": f"{course_id}c{index:02d}",
                    "seq": index,
                    "standard": None,
                    "dialect": item["prompt"],
                    "scriptKey": item["scriptKey"],
                    "guideF0": item["guideF0"],
                    "referenceAudioPath": None,
                } for index, item in enumerate(chunk, start=1)],
            })
    return courses


def migration_sql(definition: dict, source_version: str, published_at: str) -> str:
    body = json.dumps(definition, ensure_ascii=False, indent=2)
    if "$definition$" in body:
        raise SystemExit("본문에 달러 인용 구분자가 들어 있다 - 다른 구분자를 써야 한다")
    version = definition["contentVersion"]
    courses = definition["courses"]
    cards = sum(len(c["cards"]) for c in courses)
    levels: dict[int, int] = {}
    for c in courses:
        levels[c["level"]] = levels.get(c["level"], 0) + 1
    level_summary = ", ".join(f"레벨 {level} {count}개" for level, count in sorted(levels.items()))
    return (
        f"-- KAN-264: 억양 학습 발행본 {version} - 코스 {len(courses)}개, 카드 {cards}장 ({level_summary}).\n"
        "--\n"
        "-- 이 파일은 손으로 쓰지 않는다 - tools/content/build_intonation_learning.py가 레벨테스트 정의\n"
        f"-- {source_version}의 VOICE 문항에서 만든다. 대사, scriptKey, 가이드 곡선은 원천 그대로이고, 레벨은\n"
        "-- 어절 수, 코스는 레벨 안에서 10카드 상한으로 균등 분할이다. 주제, 표준어 원문, 기준 음원은\n"
        "-- 원천에 없어 전부 null이다 (명세서 §3.17, 2026-10-08 결정).\n"
        "--\n"
        "-- 발행 후 불변이다 (§5.4와 같은 규칙). 표준어 원문이나 기준 음원이 채워지면 이 행을 UPDATE하지\n"
        "-- 않고 새 contentVersion으로 INSERT한다. 활성 전환 행은 없다 - 서버가 발행 시각이 가장 늦은\n"
        "-- 발행본을 목록과 상세에 쓴다.\n"
        "--\n"
        "-- 예외 (KAN-276, 2026-10-08): 학습 발행본이 아직 초기 단계라 이 행은 검수한 원천으로 같은 contentVersion을\n"
        "-- 덮어쓴 것이다. 이미 적용된 환경(staging)은 정의 행 교체와 flyway repair로 맞춘다. 다음부터는 새 contentVersion이다.\n"
        "insert into intonation_learning_definition (content_version, dialect, body, published_at)\n"
        f"values ('{version}', '{definition['dialect']}', $definition${body}$definition$,\n"
        f"        timestamp with time zone '{published_at}');\n"
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source-migration", required=True, type=Path,
                        help="원천 테스트 정의가 든 마이그레이션 SQL")
    parser.add_argument("--source-version", required=True, help="원천 testVersion, 예: gn-2026.10.1")
    parser.add_argument("--content-version", required=True, help="예: in-gn-2026.10.1")
    parser.add_argument("--published-at", required=True, help="ISO-8601 UTC, 예: 2026-10-08T00:00:00Z")
    parser.add_argument("--out", required=True, help="마이그레이션 SQL 출력 경로")
    parser.add_argument("--json-out", help="발행본 JSON도 따로 남길 경로 (선택)")
    args = parser.parse_args()

    definition = {
        "contentVersion": args.content_version,
        "dialect": DIALECT,
        "courses": build_courses(source_voice_items(args.source_migration, args.source_version)),
    }
    Path(args.out).write_text(migration_sql(definition, args.source_version, args.published_at),
                              encoding="utf-8")
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(definition, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    for c in definition["courses"]:
        print(f"{c['courseId']} L{c['level']} {c['title']}: 카드 {len(c['cards'])}장")
    print(f"코스 {len(definition['courses'])}개 → {args.out}")


if __name__ == "__main__":
    main()
