#!/usr/bin/env python3
"""Run the production Swift delegate in a Simulator; --baseline demonstrates the released regression."""
import argparse
import hashlib
import json
import platform
import plistlib
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('output', type=Path)
parser.add_argument('--baseline', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
path = 'iosApp/Sources/GycScannerNative/ScannerPreviewView.swift'
source = subprocess.check_output(['git', 'show', '9596dab036e803aeaa3e35fd09f1fe98b6e21704:' + path], cwd=root) if args.baseline else (root / path).read_bytes()
combined = output / 'Combined.swift'
combined.write_bytes(source + b'\n' + (root / 'tests/scanner-roi.swift').read_bytes())
assert combined.read_bytes().startswith(source)
mode = 'baseline' if args.baseline else 'fixed'
bundle = 'io.github.gycrosskit.scanner.roi-fixture.' + mode
app = output / 'ScannerROIProbe.app'
app.mkdir()
info = {'CFBundleIdentifier': bundle, 'CFBundleExecutable': 'ScannerROIProbe', 'CFBundleName': 'ScannerROIProbe', 'CFBundleVersion': '1', 'CFBundleShortVersionString': '1.0', 'CFBundlePackageType': 'APPL', 'LSRequiresIPhoneOS': True, 'NSCameraUsageDescription': 'No camera capture in this software delegate fixture'}
(app / 'Info.plist').write_bytes(plistlib.dumps(info))
sdk = subprocess.check_output(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'], text=True).strip()
arch = platform.machine()
assert arch in ('arm64', 'x86_64'), 'Unsupported Simulator architecture: ' + arch
command = ['xcrun', 'swiftc', '-parse-as-library', '-target', arch + '-apple-ios15.0-simulator', '-sdk', sdk, str(combined)]
if args.baseline:
    command += ['-D', 'BASELINE_ROI']
with (output / 'typecheck.log').open('w') as log:
    subprocess.run(command + ['-typecheck'], stdout=log, stderr=subprocess.STDOUT, check=True)
with (output / 'build.log').open('w') as log:
    subprocess.run(command + ['-o', str(app / 'ScannerROIProbe')], stdout=log, stderr=subprocess.STDOUT, check=True)
subprocess.run(['codesign', '--force', '--sign', '-', str(app)], check=True)
devices = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devices', 'available', '--json']))['devices']
iphones = [d for entries in devices.values() for d in entries if d['isAvailable'] and 'iPhone' in d['name']]
assert iphones, 'No available iPhone Simulator'
device = next((d for d in iphones if d['state'] == 'Booted'), iphones[0])
udid = device['udid']
if device['state'] != 'Booted':
    subprocess.run(['xcrun', 'simctl', 'boot', udid], check=True)
    subprocess.run(['xcrun', 'simctl', 'bootstatus', udid, '-b'], check=True)
subprocess.run(['xcrun', 'simctl', 'install', udid, str(app)], check=True)
container = Path(subprocess.check_output(['xcrun', 'simctl', 'get_app_container', udid, bundle, 'data'], text=True).strip())
result = container / 'Documents/result.json'
# simctl install preserves App data. A prior run must never be mistaken for this launch.
result.unlink(missing_ok=True)
with (output / 'launch.log').open('w') as log:
    subprocess.run(['xcrun', 'simctl', 'launch', udid, bundle], stdout=log, stderr=subprocess.STDOUT, check=True)
for _ in range(30):
    if result.is_file():
        break
    time.sleep(1)
assert result.is_file(), 'No fixture result produced'
data = json.loads(result.read_text())
assert len(data['checks']) == 10, 'Incomplete fixture run'
assert data['pass'] == all(check['pass'] for check in data['checks']), 'Inconsistent fixture JSON'
data.update(mode=mode, production_source_sha256=hashlib.sha256(source).hexdigest(), source_prefix_unchanged=True, simulator_udid=udid, simulator_arch=arch)
(output / 'result.json').write_text(json.dumps(data, indent=2) + '\n')
print(json.dumps(data, indent=2))
raise SystemExit(0 if data['pass'] else 1)
