# 등급 공유 이미지 복사본 (KAN-132, KAN-221)

`scripts/publish-share-assets.sh <env>`가 웹 S3 버킷 `share/<tier>.png`로 올리는 원본이다.

정본은 Accentury_App의 `assets/share/<tier>.png`(`assets/characters/build.py` 산출물)이고 여기는
그 복사본이다. 레포 분리(KAN-221) 전에는 한 레포라 스크립트가 `assets/share`를 직접 읽었다.

캐릭터나 등급 이미지를 다시 만들면 Accentury_App에서 만든 PNG 5장을 여기로 복사한 뒤 스크립트를
돌린다. 파일명이 곧 계약이다 (등급 code 소문자, backend `TierAssets.imageUrl`).

| 파일 | 등급 |
| --- | --- |
| outsider.png | 외지인 |
| traveler.png | 여행자 |
| wannabe.png | 워너비 |
| honorary.png | 명예 시민 |
| native.png | 토박이 |
