#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${VERSION:?Provide an immutable release tag}"
[[ "$VERSION" =~ ^[0-9][0-9A-Za-z._-]*$ ]] || { echo 'Invalid release tag' >&2; exit 1; }
staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT
# 从不可变版本 Tag 取得提交和 tree，沿用 JitPack 的回执与归档校验。
repo_root="$PWD"
git init -q "$staging/tag"
git -C "$staging/tag" fetch -q --no-tags --depth=1 https://github.com/gycrosskit/scanner.git "refs/tags/$VERSION"
git -C "$staging/tag" checkout -q --detach FETCH_HEAD
commit="$(git -C "$staging/tag" rev-parse --verify HEAD)"
(cd "$staging/tag" && bash "$repo_root/jitpack-install.sh" scanner "$staging/maven")
python3 scripts/check-maven.py "$staging/maven" com.github.gycrosskit.scanner "$VERSION" scanner-core,scanner-kuikly ios_arm64,ios_x64,ios_simulator_arm64,ohos_arm64
# 标签必须解析为不可变发布提交；不把当前 PR 的 SHA 当成已发布版本。
publications=scanner-core,scanner-core-android,scanner-core-iosarm64,scanner-core-iosx64,scanner-core-iossimulatorarm64,scanner-core-ohosarm64,scanner-kuikly,scanner-kuikly-ohosarm64
case "$VERSION" in
  0.1.2|0.1.3|0.1.4|0.1.5) ;; # 保持已发布标签的精确 inventory。
  *) publications+=,scanner-kuikly-android,scanner-kuikly-iosarm64,scanner-kuikly-iosx64,scanner-kuikly-iossimulatorarm64 ;;
esac
python3 scripts/check-public-maven.py --repo scanner --version "$VERSION" --commit "$commit" \
  --expected-publications "$publications" --output-dir "${CI_DIAGNOSTICS_DIR:-ci-diagnostics}/public"
