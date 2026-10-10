#!/usr/bin/env python3
"""失败后探测原始 coroutines-test POM；只记录证据，不修改 hosts 或重跑 Gradle。"""
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

HOST = 'maven.eazytec-cloud.com'
URL = (f'https://{HOST}/nexus/repository/maven-public/org/jetbrains/kotlinx/'
       'kotlinx-coroutines-test/1.10.2-1.0.0/kotlinx-coroutines-test-1.10.2-1.0.0.pom')
probes = [('primary', None)]
fallback = os.environ.get('CI_FORK_HOST_FALLBACK_IP', '')
if fallback:
    try:
        address = ipaddress.IPv4Address(fallback)
        if not address.is_global or address.is_multicast:
            raise ValueError('not a public unicast IPv4')
        probes.append(('configured_fallback', str(address)))
    except ValueError:
        print('Configured fallback skipped: invalid public IPv4')

receipts = []
with tempfile.TemporaryDirectory() as directory:
    for name, address in probes:
        body = Path(directory) / f'{name}.pom'
        command = ['curl', '--disable', '--fail', '--silent', '--show-error', '--proto', '=https',
                   '--ipv4', '--noproxy', HOST, '--connect-timeout', '10', '--max-time', '20',
                   '--output', str(body), '--write-out', '%{http_code} %{remote_ip} %{time_connect} %{time_total}']
        if address:
            command += ['--resolve', f'{HOST}:443:{address}']
        result = subprocess.run(command + [URL], capture_output=True, text=True, timeout=25)
        parts = result.stdout.split()
        receipt = dict(endpoint=name, url=URL, curl_exit=result.returncode, transport=result.stdout,
                       error=result.stderr.strip(), valid_pom=False)
        if result.returncode == 0 and len(parts) == 4 and parts[0] == '200' and (not address or parts[1] == address):
            try:
                data = body.read_bytes()
                project = ET.fromstring(data)
                ns = '{http://maven.apache.org/POM/4.0.0}'
                expected = ('org.jetbrains.kotlinx', 'kotlinx-coroutines-test', '1.10.2-1.0.0')
                actual = tuple(project.findtext(ns + field) for field in ('groupId', 'artifactId', 'version'))
                if project.tag == ns + 'project' and actual == expected:
                    receipt.update(valid_pom=True, sha256=hashlib.sha256(data).hexdigest(), bytes=len(data))
            except (OSError, ET.ParseError):
                pass
        receipts.append(receipt)
        print(json.dumps(receipt, sort_keys=True))
matching = (receipts[0]['sha256'] == receipts[1]['sha256']
            if len(receipts) == 2 and all(item['valid_pom'] for item in receipts) else None)
print(json.dumps(dict(same_pom_sha256=matching, diagnostic_only=True), sort_keys=True))
