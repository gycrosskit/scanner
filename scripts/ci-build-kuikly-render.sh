#!/usr/bin/env bash
# Build the pinned real native runtime required by Kuikly consumer final linking.
set -euo pipefail
build_dir=${1:?Usage: ci-build-kuikly-render.sh absolute-build-directory}
[[ "$build_dir" = /* ]] || { echo 'Build directory must be absolute' >&2; exit 1; }
case "$(uname -m)" in arm64|x86_64) native_arch=$(uname -m) ;; *) echo 'Unsupported macOS architecture' >&2; exit 1 ;; esac
mkdir -p "$build_dir"
framework_parent="$build_dir/Products"
cat > "$build_dir/Podfile" <<'POD'
install! 'cocoapods', :integrate_targets => false
platform :ios, '15.0'
use_frameworks!
target 'KuiklyRenderProbe' do
  pod 'OpenKuiklyIOSRender', '2.28.0'
end
POD
(cd "$build_dir" && CP_HOME_DIR="$build_dir/cocoapods" pod install) >&2
xcodebuild -project "$build_dir/Pods/Pods.xcodeproj" -scheme OpenKuiklyIOSRender \
  -configuration Debug -sdk iphonesimulator \
  -derivedDataPath "$build_dir/DerivedData" -arch "$native_arch" \
  ONLY_ACTIVE_ARCH=YES CONFIGURATION_BUILD_DIR="$framework_parent" CODE_SIGNING_ALLOWED=NO build >&2
binary="$framework_parent/OpenKuiklyIOSRender.framework/OpenKuiklyIOSRender"
test -f "$binary"
lipo "$binary" -verify_arch "$native_arch"
nm -gU "$binary" | grep '_com_tencent_kuikly_IsCurrentOnContextThread$' >&2
printf '%s\n' "$framework_parent"
