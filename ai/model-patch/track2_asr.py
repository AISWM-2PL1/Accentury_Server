#!/usr/bin/env python3
"""트랙2 ASR 러너 — `docs/TRACK2_STT_PIPELINE_SPEC.md` §6 B1-② 의 구현체

  소리 배열 하나를 텍스트 하나로 바꾼다. 그게 전부다.

하지 않는 것 (§6 「하지 말아야 할 것」)
  파일 경로를 모른다 · 조건 A/B 를 모른다 · 배치로 묶지 않는다

`run_asr.py:151~164` 의 whisper 분기를 그대로 옮긴 것이다. 저쪽은 8모델 비교와
속도 계측용이라 손대지 않는다 (§6 「왜 run_asr.py 에 얹지 않는가」).
"""
import os

REPO = "openai/whisper-large-v3"        # inference 명세 §3. 새로 고를 항목이 아니다.
SR = 16000


def pick_device(want: str = "auto") -> str:
    if os.environ.get("ACCENTURY_WHISPER_OV_DIR"):      # OpenVINO 이미지에는 기본 파이썬 torch가 없다
        return "cpu"
    import torch
    if want != "auto":
        return want
    if torch.cuda.is_available():
        return "cuda"
    if torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def dtype_for(device: str):
    """GPU(cuda·mps)는 float16, cpu 는 bfloat16 (2026-09-08 KAN-57 — c7i 실측 전사 66/76 동일·점수 69/70 동일·2.4배, x86 AMX 가속. 옛 기본 float32 는 환경변수로).

    2026-08-28 정정 (inference 명세 §3 supersede). 08-26 결정은 「T4 float16 이면 기존
    경남 CER 16.25 와 조건이 달라진다」였으나 전제가 틀렸다 — 기존 기준선 둘은 mps 로 쟀고
    (`registry/asr/whisper-large-v3__benchmark_all.json:23` · `registry/finetune/baseline_test.json:3`)
    `run_asr.py:154` 는 mps 를 float16 으로 돌린다. 즉 기준선이 float16 이다.
    CUDA 도 float16 이어야 같은 조건이고, T4 실측 2.2배 빠르다(100클립, 가설 99/100 동일).
    """
    import torch
    want = os.environ.get("ACCENTURY_WHISPER_DTYPE")          # KAN-57 양자화 실측용 — float32 · bfloat16 · int8
    if want:
        return torch.float32 if want == "int8" else getattr(torch, want)
    return torch.float16 if device in ("cuda", "mps") else torch.bfloat16   # cpu 기본 bf16 — KAN-57 채택 (2026-09-08). 되돌리기: ACCENTURY_WHISPER_DTYPE=float32


def adapter_id(path) -> dict:
    """어댑터를 무엇으로 식별할 것인가 — 경로만으로는 같은 이름의 다른 판을 못 가린다.
    `adapter_model.safetensors` 의 sha256 을 조각 메타에 남긴다 (`_run_meta.json`)."""
    import hashlib
    from pathlib import Path as _P
    d = _P(path).resolve()
    w = d / "adapter_model.safetensors"
    if not w.exists():
        w = d / "adapter_model.bin"
    if not w.exists():
        raise SystemExit(f"어댑터 가중치가 없다: {d}")
    h = hashlib.sha256()
    with open(w, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return {"path": str(d), "file": w.name, "sha256": h.hexdigest(), "bytes": w.stat().st_size}


def _build_ov(ir_dir: str):
    """별도 venv의 `ov_asr.py`를 한 번 띄우고 발화마다 파이프로 넘긴다. 워커와 같은 프로세스 그룹이라 함께 거둬진다."""
    import json
    import struct
    import subprocess
    from pathlib import Path
    python = os.environ.get("ACCENTURY_WHISPER_OV_PYTHON", "/opt/venv-ov/bin/python")
    # -I: 이 프로세스의 PYTHONPATH와 스크립트 폴더를 venv에 섞지 않는다
    p = subprocess.Popen([python, "-I", str(Path(__file__).with_name("ov_asr.py")), ir_dir],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE)

    def reply() -> dict:
        line = p.stdout.readline()
        if not line:
            raise RuntimeError(f"OpenVINO 프로세스가 끝났다 returncode={p.poll()}")
        return json.loads(line)

    if not reply().get("ready"):
        raise RuntimeError("OpenVINO 프로세스 기동 실패")

    def run(x):
        data = x.astype("float32", copy=False).tobytes()
        p.stdin.write(struct.pack("<Q", len(data) // 4) + data)
        p.stdin.flush()
        res = reply()
        if "error" in res:
            raise RuntimeError(res["error"])
        return res["text"]

    return run


def build(device: str, adapter=None):
    """(run, repo, dtype, adapter_meta). 모델 로드는 한 번 — 세트마다 다시 올리지 않는다.

    `adapter` 를 주면 그 LoRA 어댑터를 원본 위에 얹는다 (`train_lora_whisper.py` 가 저장한
    폴더). **가중치를 병합하지 않는다** — `merge_and_unload()` 는 bf16 으로 학습한 델타를
    float16 본체에 더하면서 반올림이 한 번 더 끼고, 그러면 무튜닝 조각과 조건이 달라진다.
    어댑터 없이 부르면 옛 동작 그대로다."""
    if os.environ.get("ACCENTURY_WHISPER_OV_DIR"):      # OpenVINO int8 베타 이미지 - 이 환경 변수를 빼면 아래 torch 경로로 돈다
        return _build_ov(os.environ["ACCENTURY_WHISPER_OV_DIR"]), REPO, "ov-int8", None
    import torch
    from transformers import WhisperForConditionalGeneration, WhisperProcessor
    dt = dtype_for(device)
    proc = WhisperProcessor.from_pretrained(REPO)
    m = WhisperForConditionalGeneration.from_pretrained(REPO, dtype=dt).to(device).eval()
    want = os.environ.get("ACCENTURY_WHISPER_DTYPE")
    if want == "int8":       # Linear 층 동적 양자화 — x86 fbgemm 필요, 맥에선 NoQEngine
        if device != "cpu":  # CUDA/MPS 에 CPU 양자화를 얹지 않는다 — 코덱스 2026-09-08 #10
            raise ValueError(f"ACCENTURY_WHISPER_DTYPE=int8 은 cpu 전용 — device={device}")
        m = torch.ao.quantization.quantize_dynamic(m, {torch.nn.Linear}, dtype=torch.qint8)
    ad = None
    if adapter:
        from peft import PeftModel
        ad = adapter_id(adapter)
        m = PeftModel.from_pretrained(m, ad["path"], torch_dtype=dt).to(device).eval()
        m.config.use_cache = True
    forced = proc.get_decoder_prompt_ids(language="korean", task="transcribe")

    def run(x):
        # batch 1. 묶으면 부동소수점 누적 순서가 바뀌어 가설이 흔들린다 (inference 명세 §9).
        feats = proc(x, sampling_rate=SR, return_tensors="pt").input_features.to(device, dt)
        with torch.no_grad():
            ids = m.generate(feats, forced_decoder_ids=forced, max_new_tokens=200)
        return proc.batch_decode(ids, skip_special_tokens=True)[0].strip()

    return run, REPO, ("int8" if want == "int8" else str(dt).replace("torch.", "")), ad   # 코덱스 2026-09-08 #4
