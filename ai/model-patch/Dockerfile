# syntax=docker/dockerfile:1
# 모델 전달본 f77011c 위에 MFA 상주화와 OpenVINO int8 Whisper를 얹은 이미지다. 빌드와 push는 build.sh로 한다.
#
# OpenVINO 패키지는 transformers 5.5가 필요해 모델 파이썬(transformers 4.57)에 섞지 않고 /opt/venv-ov에 둔다.
# 기존 Whisper 가중치와 기본 파이썬 torch는 지운다. 지운 파일 시스템을 한 층으로 다시 담아야 이미지가 실제로
# 줄어서, 베이스의 ENV, WORKDIR, ENTRYPOINT, CMD를 아래에 다시 적는다.
ARG MODEL_REGISTRY=325771561913.dkr.ecr.ap-northeast-2.amazonaws.com
ARG BASE_TAG=f77011c
ARG IR_TAG=6415e0d
FROM ${MODEL_REGISTRY}/accentury/ai-model:${IR_TAG} AS ir

FROM ${MODEL_REGISTRY}/accentury/ai-model:${BASE_TAG} AS src
COPY ov.requirements ov.constraints /tmp/ov/
RUN python -m pip install --no-cache-dir uv \
 && uv venv /opt/venv-ov --python /usr/local/bin/python3 \
 && uv pip install --no-cache --python /opt/venv-ov/bin/python -r /tmp/ov/ov.requirements -c /tmp/ov/ov.constraints \
      --extra-index-url https://download.pytorch.org/whl/cpu --index-strategy unsafe-best-match \
 && python -m pip uninstall -y uv torch \
 && rm -rf /tmp/ov /opt/hf/hub/models--openai--whisper-large-v3
COPY --from=ir /opt/whisper-ov-int8 /opt/whisper-ov-int8
COPY serve.py mfa_one.py /app/src/scoring/
COPY track2_asr.py ov_asr.py /app/src/stt/

FROM scratch
COPY --from=src / /
ARG CODE_HASH
ENV PATH=/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=C.UTF-8 \
    GPG_KEY=7169605F62C751356D054A26A821E680E5FA6305 \
    PYTHON_VERSION=3.12.14 \
    PYTHON_SHA256=5c8462af5790baf43a321a1559dbe0db06d1be4300fb85fb53c40060668e548a \
    DEBIAN_FRONTEND=noninteractive \
    PIP_NO_CACHE_DIR=1 \
    MAMBA_ROOT_PREFIX=/opt/conda \
    ACCENTURY_MFA_BIN=/opt/conda/envs/mfa/bin \
    ACCENTURY_MFA_DICT=/app/dict_aug_track1.txt \
    ACCENTURY_DATA=/data \
    HF_HOME=/opt/hf \
    HF_HUB_OFFLINE=1 \
    MFA_ROOT_DIR=/opt/mfa \
    ACCENTURY_CODE_HASH=${CODE_HASH} \
    ACCENTURY_WHISPER_OV_DIR=/opt/whisper-ov-int8 \
    ACCENTURY_WHISPER_OV_THREADS=2
WORKDIR /app
ENTRYPOINT ["python"]
CMD ["src/scoring/serve.py", "--help"]
