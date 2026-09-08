#!/usr/bin/env python3
"""Check the installable Remote artifact, including its stable update signature."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1])
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
build_tools = sorted((sdk / 'build-tools').iterdir(), key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.name)))[-1]
def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True)

badging = run(build_tools / 'aapt', 'dump', 'badging', apk)
for expected in ["name='com.bizard.homesmokeremote'", "versionCode='35'", "versionName='2.3.0'", "sdkVersion:'23'", "targetSdkVersion:'35'"]:
    assert expected in badging, f'Missing APK metadata: {expected}'
xmltree = run(build_tools / 'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
assert 'activity-alias' in xmltree and 'com.bizard.homesmokeremote.GraphUxActivity' in xmltree
certificate = run(build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in certificate
# Compare with the same signing key used by all previously published Remote updates.
key = run('keytool', '-exportcert', '-rfc', '-keystore', 'remote/homesmoke-remote-debug.keystore', '-storepass', 'homesmoke', '-alias', 'homesmoke')
import base64
key_der = base64.b64decode(''.join(key.splitlines()[1:-1]))
expected_digest = hashlib.sha256(key_der).hexdigest()
assert f'certificate SHA-256 digest: {expected_digest}' in certificate
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    dex = b''.join(archive.read(name) for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name))
    assert b'Lkotlin/Metadata;' in dex, 'Kotlin metadata missing from DEX'
    for name in ['MainActivity', 'GraphUxActivity', 'GraphUxFixActivity', 'HistoryActivity', 'SessionDetailActivity', 'SystemStatusActivity', 'RemoteApplication', 'MqttClient', 'SecretStore', 'TelemetryHistoryStore', 'OperationalHistoryStore', 'SessionAnalytics', 'TemperatureChartView']:
        assert f'Lcom/bizard/homesmokeremote/{name};'.encode() in dex, f'Missing DEX class: {name}'
print(f'Remote 2.1.4 APK verified: Android 6+, package, launcher, Kotlin classes, ZIP and stable signing certificate; {apk.stat().st_size} bytes')
print('SHA-256:', hashlib.sha256(apk.read_bytes()).hexdigest())
