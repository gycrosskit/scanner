#!/usr/bin/env bash
set -euo pipefail
archive=scanner-maven.tar.gz
curl -fL --retry 3 -o "$archive" "https://github.com/gycrosskit/scanner/releases/download/${VERSION}/${archive}"
sha256sum -c release-checksums.txt
mkdir -p "$HOME/.m2/repository" build/release-maven
tar -xzf "$archive" -C "$HOME/.m2/repository"
tar -xzf "$archive" -C build/release-maven
# metadata 在归档前正规化；安装同一校验字节，不在消费端再次改写。
python3 scripts/check-maven.py build/release-maven com.github.gycrosskit.scanner "$VERSION" \
  scanner-core,scanner-kuikly ios_arm64,ios_simulator_arm64,ios_x64,ohos_arm64
