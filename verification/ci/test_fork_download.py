#!/usr/bin/env python3
"""真实失败分支的轻量替身测试：不联网、不写 hosts、不启动 Gradle。"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/ci-diagnose-fork-download.py'
with tempfile.TemporaryDirectory() as directory:
    root = Path(directory)
    calls = root / 'curl-calls'
    (root / 'curl').write_text('''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
args = sys.argv[1:]
assert args[0] == '--disable'
assert args[args.index('--proto') + 1] == '=https'
assert args[args.index('--noproxy') + 1] == 'maven.eazytec-cloud.com'
assert args[args.index('--connect-timeout') + 1] == '10'
assert args[args.index('--max-time') + 1] == '20'
assert '--ipv4' in args
assert not any(arg in args for arg in ['-k', '--insecure', '-L', '--location', '--retry'])
assert args[-1].endswith('/kotlinx-coroutines-test/1.10.2-1.0.0/kotlinx-coroutines-test-1.10.2-1.0.0.pom')
assert '/multiplatform/' not in args[-1]  # 不是初始化时的 KMP marker。
fallback = '--resolve' in args
if fallback:
    assert args[args.index('--resolve') + 1] == 'maven.eazytec-cloud.com:443:36.153.109.99'
with open(os.environ['MOCK_CURL_CALLS'], 'a') as calls:
    calls.write(json.dumps(args) + '\\n')
case = os.environ['MOCK_CASE']
if case == 'connect_timeout' and not fallback:
    print('000  0.000000 10.000000', end='')
    sys.exit(28)
body = '<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>org.jetbrains.kotlinx</groupId><artifactId>kotlinx-coroutines-test</artifactId><version>1.10.2-1.0.0</version></project>'
if case == 'different_bytes' and fallback:
    body += '<!-- different response bytes -->'
if case == 'wrong_pom':
    body = body.replace('1.10.2-1.0.0', '1.10.2')
Path(args[args.index('--output') + 1]).write_text(body)
address = '36.153.109.99' if fallback else '61.177.127.227'
if case == 'wrong_remote' and fallback:
    address = '8.8.8.8'
print('200 ' + address + ' 0.100000 0.200000', end='')
''')
    (root / 'curl').chmod(0o755)
    environment = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'],
                       MOCK_CURL_CALLS=str(calls), CI_FORK_HOST_FALLBACK_IP='36.153.109.99')
    for case in ['same_bytes', 'different_bytes', 'connect_timeout', 'wrong_pom', 'wrong_remote', 'invalid_fallback']:
        calls.unlink(missing_ok=True)
        environment.update(MOCK_CASE=case, CI_FORK_HOST_FALLBACK_IP='127.0.0.1' if case == 'invalid_fallback' else '36.153.109.99')
        result = subprocess.run(['python3', str(SCRIPT)], env=environment, text=True, capture_output=True, check=True)
        receipts = [json.loads(line) for line in result.stdout.splitlines() if line.startswith('{')]
        assert len(calls.read_text().splitlines()) == (1 if case == 'invalid_fallback' else 2), case
        assert receipts[-1]['diagnostic_only'] is True
        assert receipts[-1]['same_pom_sha256'] == (True if case == 'same_bytes' else False if case == 'different_bytes' else None), case
        if case == 'connect_timeout':
            assert receipts[0]['curl_exit'] == 28 and not receipts[0]['valid_pom']
            assert receipts[1]['valid_pom'] and 'sha256' in receipts[1]
        if case == 'wrong_pom':
            assert not any(receipt['valid_pom'] for receipt in receipts[:-1])
        if case == 'wrong_remote':
            assert not receipts[1]['valid_pom']

    # 执行 workflow 的实际 shell 分支，验证诊断成功也不会覆盖原构建失败。
    workflow = (ROOT / '.github/workflows/regression.yml').read_text()
    start = workflow.index('          if python3 scripts/ci-run.py --log ci-diagnostics/android-3.log')
    end = workflow.index('\n      - name:', start)
    block = '\n'.join(line[10:] for line in workflow[start:end].splitlines())
    (root / 'scripts').mkdir()
    shutil.copyfile(SCRIPT, root / 'scripts/ci-diagnose-fork-download.py')
    (root / 'scripts/ci-run.py').write_text('import os, sys\nargs = sys.argv[sys.argv.index("--") + 1:]\nos.execvp(args[0], args)\n')
    (root / 'gradlew').write_text('printf "called\\n" >> "$MOCK_GRADLE_CALLS"\nexit "$MOCK_GRADLE_EXIT"\n')
    gradle_calls = root / 'gradle-calls'
    for status in [0, 9]:
        calls.unlink(missing_ok=True)
        gradle_calls.unlink(missing_ok=True)
        environment.update(MOCK_CASE='connect_timeout', CI_FORK_HOST_FALLBACK_IP='36.153.109.99',
                           MOCK_GRADLE_CALLS=str(gradle_calls), MOCK_GRADLE_EXIT=str(status))
        result = subprocess.run(['bash', '-ec', block], cwd=root, env=environment, capture_output=True, text=True)
        assert result.returncode == status, (status, result.stdout, result.stderr)
        assert gradle_calls.read_text().splitlines() == ['called']
        assert calls.exists() == (status != 0)
    (root / 'scripts/ci-diagnose-fork-download.py').write_text('raise SystemExit(17)\n')
    result = subprocess.run(['bash', '-ec', block], cwd=root, env=environment, capture_output=True, text=True)
    assert result.returncode == 9, 'Diagnostic failure replaced the original Gradle exit'
print('fork download diagnostics: 6 transport/body cases and 3 real workflow branches passed')
