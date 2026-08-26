from pathlib import Path
import re
import shutil

src = Path('app')
dst = Path('legacy')
if dst.exists():
    shutil.rmtree(dst)
shutil.copytree(src, dst)

# --- Gradle: dedicated Android 4.x build ---
gradle = dst / 'build.gradle'
g = gradle.read_text(encoding='utf-8')
g = re.sub(r"applicationId\s+'[^']+'", "applicationId 'com.bizard.homesmokemqtt.legacy'", g, count=1)
g = re.sub(r'minSdk\s+\d+', 'minSdk 14', g, count=1)
g = re.sub(r'targetSdk\s+\d+', 'targetSdk 19', g, count=1)
g = re.sub(r'versionCode\s+\d+', 'versionCode 4', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.4.0-legacy4'", g, count=1)
gradle.write_text(g, encoding='utf-8')

# --- Manifest: only permissions/attributes understood and needed on Android 4.x ---
manifest = dst / 'src/main/AndroidManifest.xml'
m = manifest.read_text(encoding='utf-8')
m = re.sub(r'\s*<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"\s*/>\s*', '\n', m)
m = re.sub(r'\s+android:usesCleartextTraffic="true"', '', m)
m = re.sub(r'\s+android:exported="true"', '', m)
manifest.write_text(m, encoding='utf-8')

# Holo exists on API 11; Material/status-bar attributes do not exist on Android 4.0.
styles = dst / 'src/main/res/values/styles.xml'
styles.write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:style/Theme.Holo.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowNoTitle">true</item>
    </style>
</resources>
''', encoding='utf-8')

# --- MainActivity: remove Android 5+/6+/12+ framework dependencies while
# preserving Bluetooth, PID, sidebar, exact AIA mapping, MQTT gateway and programmable Android Auto. ---
main = dst / 'src/main/java/com/bizard/homesmokemqtt/MainActivity.java'
s = main.read_text(encoding='utf-8')

s = s.replace('import android.view.WindowInsets;\n', '')
s = s.replace('import java.nio.charset.StandardCharsets;\n', 'import java.nio.charset.Charset;\n')

# The Android 16 build applies insets after setContentView. Legacy Android does not need them.
s = s.replace('''        View content = buildRoot();
        setContentView(content);
        applySystemInsets(content);
        loadSettings();''', '''        setContentView(buildRoot());
        loadSettings();''')

# Remove runtime BLUETOOTH_CONNECT permission request (introduced in Android 12).
s = re.sub(r'\n\s*if \(Build\.VERSION\.SDK_INT >= 31 && !hasBtPermission\(\)\)\s*\n\s*requestPermissions\([^;]+;\s*', '\n', s, count=1)

# Remove the whole WindowInsets/status/navigation-bar helper inserted for Android 15/16.
s = re.sub(r'\n\s*private void applySystemInsets\(View root\) \{.*?\n\s*\}\n\n(?=\s*private View buildRoot\(\))', '\n', s, flags=re.S)

# Elevation is API 21. The sliding panel works without elevation on Android 4.x.
s = s.replace('        panel.setElevation(dp(12));\n', '')

# withEndAction is API 16. Close immediately on API 14/15; opening animation stays (API 12).
start = s.find('    private void closeSideMenu(Runnable after) {')
if start != -1:
    end = s.find('\n    private void ', start + 10)
    if end == -1:
        raise SystemExit('Could not find end of closeSideMenu')
    s = s[:start] + '''    private void closeSideMenu(Runnable after) {
        PopupWindow popup = sideMenuWindow;
        if(popup != null && popup.isShowing()) popup.dismiss();
        if(after != null) after.run();
    }
''' + s[end:]
else:
    raise SystemExit('closeSideMenu not found; refusing unknown UI source')

# setBackground(Drawable) is API 16; deprecated setBackgroundDrawable works on API 1+.
s = s.replace('.setBackground(', '.setBackgroundDrawable(')

# No runtime permissions on Android 4.x.
s = re.sub(r'private boolean hasBtPermission\(\)\s*\{[^}]*\}', 'private boolean hasBtPermission(){ return true; }', s, count=1)
s = re.sub(r'\s*if\(!hasBtPermission\(\)\)\{\s*requestPermissions\([^;]+;\s*return;\s*\}\s*', '\n        ', s, count=1)

# StandardCharsets is API 19. Charset.forName is available on old Android.
s = s.replace('StandardCharsets.UTF_8', 'UTF8')
class_marker = 'public class MainActivity extends Activity {\n'
if class_marker in s and 'private static final Charset UTF8' not in s:
    s = s.replace(class_marker, class_marker + '    private static final Charset UTF8 = Charset.forName("UTF-8");\n', 1)

main.write_text(s, encoding='utf-8')

# --- MQTT client compatibility ---
mq = dst / 'src/main/java/com/bizard/homesmokemqtt/MqttClient.java'
q = mq.read_text(encoding='utf-8')
q = q.replace('import java.nio.charset.StandardCharsets;\n', 'import java.nio.charset.Charset;\n')
q = q.replace('import java.util.concurrent.atomic.AtomicInteger;\n', '')
q = q.replace('    private final AtomicInteger packetId = new AtomicInteger(1);\n', '    private int packetId = 1;\n')
q = q.replace('StandardCharsets.UTF_8', 'UTF8')
class_marker = 'final class MqttClient {\n'
if class_marker in q and 'private static final Charset UTF8' not in q:
    q = q.replace(class_marker, class_marker + '    private static final Charset UTF8 = Charset.forName("UTF-8");\n', 1)
q = re.sub(
    r'\s*private int nextPacketId\(\)\{\s*return packetId\.getAndUpdate\(v->v>=65535\?1:v\+1\);\s*\}',
    '\n    private synchronized int nextPacketId(){ int id=packetId; packetId=(packetId>=65535?1:packetId+1); return id; }',
    q,
    count=1
)
if 'getAndUpdate' in q or 'StandardCharsets' in q:
    raise SystemExit('Legacy MQTT still contains unsupported Java/Android APIs')
mq.write_text(q, encoding='utf-8')

# Sanity checks: do not publish a legacy APK if modern-only framework calls remain.
for forbidden in ['WindowInsets', 'setElevation(', 'requestApplyInsets(', 'setStatusBarColor(', 'setNavigationBarColor(', 'requestPermissions(', 'checkSelfPermission(', 'BLUETOOTH_CONNECT']:
    if forbidden in s:
        raise SystemExit('Legacy MainActivity still contains forbidden API: ' + forbidden)

print('HomeSmoke Legacy module prepared: minSdk 14 / Android 4.0+ / programmable Auto with probe conditions included')
