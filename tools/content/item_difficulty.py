#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""레벨테스트 실사용 기록에서 문항별 난이도 집계를 만든다 (KAN-345).

앱 학습 발행본(build_app_curriculum.py)이 세트를 쉬운 순으로 놓을 때 쓰는 근거다. 원천은 음성 전용 버킷의
메타 JSON이다 (backend training 패키지가 쓰는 객체 - 음성 문항은 S3TrainingSampleStore, 어휘 문항은
S3VocabAnswerSampleStore). 이 스크립트는 버킷에 닿지 않는다. 메타 JSON만 내려받은 로컬 폴더를 읽는다.

    aws s3 sync s3://accentury-voice-<계정>/prod/ <폴더>/ --exclude "*" --include "*.json"

버킷은 읽기만 한다 - 음성(WAV)은 내려받지 않고, 버킷의 객체를 지우거나 바꾸지 않는다. 결과 파일에는 문항별
합계만 남고 세션 ID, 작업 ID 같은 건별 값은 들어가지 않는다.

집계 규칙
    음성   원천 정의(--source-version)의 VOICE 문항을 scriptKey로 묶는다. 같은 scriptKey면 채점 기준 문장이
           같으므로 testVersion이 달라도(--pool-prefix로 시작하는 정의 전부) 한 문항으로 센다.
           gn-2026.10.2가 낱말을 고친 넷(v10, v13, v68, v91)은 화면 대사가 gn-2026.10.1과 다른데, 2026-11-15
           집계에서 이 넷의 gn-2026.10.1 응시는 0건이라 다른 문장이 섞이지 않았다. 다시 집계할 때 대사가
           바뀐 문항에 옛 정의의 응시가 있으면 --pool-prefix를 원천 정의 하나로 좁힌다.
           한 사람이 같은 문항을 다시 녹음한 것도 응시 한 건씩 센다 - NaN 비율이 녹음 한 번이 채점 불가로
           끝날 확률이어야 하기 때문이다. 마지막 결과만 세면 NaN 뒤에 다시 녹음해 성공한 건이 사라진다.
           completed    채점이 끝나 점수가 있는 응시
           unscorable   점수가 NaN으로 나온 응시 (ANALYSIS_UNSCORABLE)
           misread      다른 문장을 읽은 응시 (ANALYSIS_MISREAD) - 문항이 아니라 응시자 쪽 사유라 비율에 넣지 않는다
           tooQuiet     소리가 작은 응시 (AUDIO_TOO_QUIET) - 위와 같다
           high         점수가 --high-score(90) 이상인 응시. 90은 전체 점수의 가운데 값 언저리다
           scoreSum     점수 합 - 평균은 scoreSum / completed
    어휘   원천 정의의 VOCABULARY 문항을 itemId로 묶는다. 보기가 정의마다 달라 원천 정의의 응시만 센다.
           answered, correct

사용
    python3 item_difficulty.py --meta-dir <폴더> \\
        --source-migration ../../backend/src/main/resources/db/migration/V15__gn_2026_10_2_voice_emotion.sql \\
        --source-version gn-2026.10.2 --pool-prefix gn-2026.10. --snapshot-date 2026-11-15 \\
        --out difficulty_gn_2026_11.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_intonation_learning import source_definition  # noqa: E402

QUOTED_WORD = re.compile(r"^'([^']+)'")
UNSCORABLE = "ANALYSIS_UNSCORABLE"
MISREAD = "ANALYSIS_MISREAD"
TOO_QUIET = "AUDIO_TOO_QUIET"


def dialect_word(item: dict) -> str:
    """어휘 문항의 물음("'정구지'는 표준어로 무엇일까요?")에서 사투리 낱말을 꺼낸다."""
    match = QUOTED_WORD.match(item["prompt"])
    if not match:
        raise SystemExit(f"{item['itemId']}의 물음에서 사투리 낱말을 찾지 못했다: {item['prompt']}")
    return match.group(1)


def aggregate(meta_dir: Path, definition: dict, pool_prefix: str, high_score: int) -> dict:
    source_version = definition["testVersion"]
    voice = {
        item["scriptKey"]: {
            "itemId": item["itemId"], "scriptKey": item["scriptKey"], "completed": 0, "unscorable": 0,
            "misread": 0, "tooQuiet": 0, "high": 0, "scoreSum": 0,
        }
        for item in definition["items"] if item["type"] == "VOICE"
    }
    vocab = {
        item["itemId"]: {"itemId": item["itemId"], "dialect": dialect_word(item), "answered": 0, "correct": 0}
        for item in definition["items"] if item["type"] == "VOCABULARY"
    }
    files = 0
    for folder, _, names in os.walk(meta_dir):
        for name in names:
            if not name.endswith(".json"):
                continue
            with open(os.path.join(folder, name), encoding="utf-8") as handle:
                meta = json.load(handle)
            files += 1
            version = meta.get("testVersion", "")
            if meta.get("itemType") == "VOCABULARY":
                row = vocab.get(meta.get("itemId")) if version == source_version else None
                if row is not None:
                    row["answered"] += 1
                    row["correct"] += 1 if meta["correct"] else 0
                continue
            row = voice.get(meta.get("scriptKey")) if version.startswith(pool_prefix) else None
            if row is None:
                continue
            score = meta.get("intonationScore")
            error = meta.get("errorCode")
            if meta.get("outcome") == "COMPLETED" and score is not None:
                row["completed"] += 1
                row["scoreSum"] += score
                row["high"] += 1 if score >= high_score else 0
            elif error == UNSCORABLE:
                row["unscorable"] += 1
            elif error == MISREAD:
                row["misread"] += 1
            elif error == TOO_QUIET:
                row["tooQuiet"] += 1
    if files == 0:
        raise SystemExit(f"{meta_dir}에 메타 JSON이 없다")
    return {"files": files, "voice": list(voice.values()), "vocabulary": list(vocab.values())}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--meta-dir", required=True, type=Path, help="메타 JSON을 내려받은 폴더")
    parser.add_argument("--source-migration", required=True, type=Path, help="원천 테스트 정의가 든 마이그레이션 SQL")
    parser.add_argument("--source-version", required=True, help="원천 testVersion, 예: gn-2026.10.2")
    parser.add_argument("--pool-prefix", required=True, help="음성 응시를 함께 셀 testVersion 접두, 예: gn-2026.10.")
    parser.add_argument("--snapshot-date", required=True, help="메타 JSON을 내려받은 날짜, 예: 2026-11-15")
    parser.add_argument("--high-score", type=int, default=90, help="높은 점수의 기준 (기본 90)")
    parser.add_argument("--out", required=True, type=Path, help="집계 JSON 출력 경로")
    args = parser.parse_args()

    definition = source_definition(args.source_migration, args.source_version)
    result = aggregate(args.meta_dir, definition, args.pool_prefix, args.high_score)
    summary = {
        "sourceVersion": args.source_version,
        "poolPrefix": args.pool_prefix,
        "snapshotDate": args.snapshot_date,
        "highScore": args.high_score,
        "metaFiles": result["files"],
        "voice": result["voice"],
        "vocabulary": result["vocabulary"],
    }
    args.out.write_text(json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    completed = sum(row["completed"] for row in summary["voice"])
    unscorable = sum(row["unscorable"] for row in summary["voice"])
    answered = sum(row["answered"] for row in summary["vocabulary"])
    print(f"메타 {result['files']}개 - 음성 {len(summary['voice'])}문항 (채점 {completed}건, NaN {unscorable}건), "
          f"어휘 {len(summary['vocabulary'])}문항 (응답 {answered}건) → {args.out}")


if __name__ == "__main__":
    main()
