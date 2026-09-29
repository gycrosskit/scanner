#!/usr/bin/env bash
set -euo pipefail
archive=scanner-maven.tar.gz
curl -fL --retry 3 -o "$archive" "https://github.com/gycrosskit/scanner/releases/download/${VERSION}/${archive}"
echo "f131f066e79bcd93b4edbe9888c4cacf0e1bb0a96a4715b24f8153166ebca122  $archive" | sha256sum -c -
mkdir -p "$HOME/.m2/repository" build/release-maven
tar -xzf "$archive" -C "$HOME/.m2/repository"
tar -xzf "$archive" -C build/release-maven
python3 - <<'PY'
import json
from pathlib import Path
# JitPack 会把 classified sources/metadata 的 URL 改为不存在的 plain JAR。
changed = 0
for root in (Path.home() / '.m2/repository/com/github/gycrosskit/scanner', Path('build/release-maven')):
    for file in root.rglob('*.module'):
        data = json.loads(file.read_text())
        variants = [v for v in data['variants'] if not v['name'].endswith(('SourcesElements-published', 'MetadataElements-published'))]
        if len(variants) != len(data['variants']):
            data['variants'] = variants
            file.write_text(json.dumps(data, indent=2))
            changed += 1
if not changed:
    raise SystemExit('No JitPack KMP metadata variants were fixed')
PY
