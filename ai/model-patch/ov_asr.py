#!/usr/bin/env python3
"""OpenVINO int8 Whisper 상주 프로세스 (베타 이미지).

모델 이미지의 파이썬은 transformers 4.57이고 KAN-303에서 잰 OpenVINO 조합은 transformers 5.5다. 한 파이썬에 섞으면
둘 중 하나가 바뀌어 측정값과 다른 조합이 되므로, 이 파일은 별도 venv(/opt/venv-ov)에서 돌고 track2_asr.py가
파이프로 음성을 넘긴다. 호출 방식은 bench_whisper.py의 ov-int8과 같다.

입력 (stdin): 8바이트 리틀 엔디언 샘플 수 + float32 16kHz 음성
출력 (stdout): 기동 뒤 {"ready": true} 한 줄, 그 뒤 요청마다 {"text": ...} 또는 {"error": ...} 한 줄
"""
import ctypes
import json
import os
import signal
import struct
import sys

SR = 16000
MAX_NEW_TOKENS = 200


def read_exact(stream, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = stream.read(n - len(buf))
        if not chunk:
            raise EOFError
        buf += chunk
    return bytes(buf)


def main() -> int:
    # 부모(추론 워커)가 죽으면 같이 죽는다
    ctypes.CDLL("libc.so.6", use_errno=True).prctl(1, signal.SIGKILL)
    # 라이브러리가 stdout에 찍는 줄이 응답과 섞이지 않게 응답용 fd를 따로 잡고 1번은 stderr로 돌린다
    out = os.fdopen(os.dup(1), "w", encoding="utf-8", buffering=1)
    os.dup2(2, 1)

    import numpy as np
    import transformers
    from optimum.intel import OVModelForSpeechSeq2Seq
    from transformers import WhisperProcessor

    transformers.logging.set_verbosity_error()
    ir_dir = sys.argv[1]
    model = OVModelForSpeechSeq2Seq.from_pretrained(ir_dir, ov_config={
        "PERFORMANCE_HINT": "LATENCY",
        "INFERENCE_NUM_THREADS": int(os.environ.get("ACCENTURY_WHISPER_OV_THREADS", "2")),
    })
    proc = WhisperProcessor.from_pretrained(ir_dir)
    out.write(json.dumps({"ready": True}) + "\n")

    stdin = sys.stdin.buffer
    while True:
        try:
            (n,) = struct.unpack("<Q", read_exact(stdin, 8))
            x = np.frombuffer(read_exact(stdin, n * 4), dtype=np.float32)
        except EOFError:
            return 0
        try:
            feats = proc(x, sampling_rate=SR, return_tensors="pt").input_features
            ids = model.generate(feats, language="korean", task="transcribe", max_new_tokens=MAX_NEW_TOKENS)
            text = proc.batch_decode(ids, skip_special_tokens=True)[0].strip()
            out.write(json.dumps({"text": text}, ensure_ascii=False) + "\n")
        except Exception as e:  # noqa: BLE001
            out.write(json.dumps({"error": f"{type(e).__name__}: {e}"}, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
