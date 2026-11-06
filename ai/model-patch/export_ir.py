#!/usr/bin/env python3
"""Whisper large-v3를 OpenVINO int8 IR로 변환한다. IR을 새로 만들 때만 쓴다.

지금 이미지의 IR은 ai-model:6415e0d 안의 /opt/whisper-ov-int8에 있다. ov.requirements 환경에서 돌린다.

    python export_ir.py <저장 폴더>
"""
import sys

from huggingface_hub import snapshot_download
from optimum.intel import OVModelForSpeechSeq2Seq
from transformers import WhisperProcessor

REVISION = "06f233fe06e710322aca913c1bc4249a0d71fce1"  # 운영 가중치와 같은 판이다

out = sys.argv[1]
model = snapshot_download("openai/whisper-large-v3", revision=REVISION,
                          allow_patterns=["*.json", "*.txt", "model.safetensors"])
exported = OVModelForSpeechSeq2Seq.from_pretrained(model, export=True, load_in_8bit=True, compile=False)
exported.save_pretrained(out)
WhisperProcessor.from_pretrained(model).save_pretrained(out)
