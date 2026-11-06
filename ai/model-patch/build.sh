#!/usr/bin/env bash
# 모델 이미지를 다시 만든다.
#
#   ai/model-patch/build.sh           로컬에만 빌드한다 (accentury-ai-model:<태그>)
#   ai/model-patch/build.sh --push    ECR accentury/ai-model:<태그>로 올린다
#
# 태그는 모델 레포 커밋이 아니라 sha256("base=<BASE_TAG>" + 빌드 입력 파일)의 앞 7자리다. ECR 태그가 IMMUTABLE이라
# 같은 태그가 이미 있으면 push하지 않는다. OpenVINO int8 IR은 ECR의 ai-model:6415e0d에서 가져온다.
set -euo pipefail
cd "$(dirname "$0")"

BASE_TAG=f77011c
REGISTRY=325771561913.dkr.ecr.ap-northeast-2.amazonaws.com
TAG=$( { printf 'base=%s' "$BASE_TAG"; cat Dockerfile ov.lock serve.py mfa_one.py track2_asr.py ov_asr.py; } | shasum -a 256 | cut -c1-7)
echo "base ${BASE_TAG} -> tag ${TAG}"

aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null
if [[ "${1:-}" == "--push" ]]; then
  if aws ecr describe-images --region ap-northeast-2 --repository-name accentury/ai-model \
       --image-ids imageTag="$TAG" >/dev/null 2>&1; then
    echo "ECR에 ${TAG}가 이미 있다. 다시 올리지 않는다."
    exit 0
  fi
  docker buildx build --platform linux/amd64 --build-arg BASE_TAG="$BASE_TAG" --build-arg CODE_HASH="$TAG" \
    -t "$REGISTRY/accentury/ai-model:$TAG" --push .
else
  docker buildx build --platform linux/amd64 --load --build-arg BASE_TAG="$BASE_TAG" --build-arg CODE_HASH="$TAG" \
    -t "accentury-ai-model:$TAG" .
fi
echo "$TAG"
