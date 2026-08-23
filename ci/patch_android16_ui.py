from pathlib import Path

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# Android 15/16 edge-to-edge: keep app controls outside status/navigation bars.
if 'import android.view.WindowInsets;' not in s:
    s = s.replace('import android.view.WindowManager;\n', 'import android.view.WindowInsets;\nimport android.view.WindowManager;\n')

old = '''        setContentView(buildRoot());
        loadSettings();'''
new = '''        View content = buildRoot();
        setContentView(content);
        applySystemInsets(content);
        loadSettings();'''
if old in s:
    s = s.replace(old, new, 1)

marker = '''    private View buildRoot() {'''
method = '''    private void applySystemInsets(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        root.requestApplyInsets();
        getWindow().setStatusBarColor(BLUE);
        getWindow().setNavigationBarColor(BG);
    }

'''
if method not in s:
    s = s.replace(marker, method + marker, 1)

# Make title fit narrow phones better.
s = s.replace('titleView = txt(" Домашняя коптильня",21,false);', 'titleView = txt(" Домашняя коптильня",18,false);')

# Match original AIA behavior: selecting a mode changes local mode even without Bluetooth.
old_select = '''    private void selectMode(int mode,String title) {
        String cmd=mode==0?"a0":mode==1?"a1":"a2";
        if(!sendBt(cmd+TERMINATOR)) return;
        modeState=mode; showMonitor(title); toast(title+" выбран");
    }'''
new_select = '''    private void selectMode(int mode,String title) {
        String cmd=mode==0?"a0":mode==1?"a1":"a2";
        modeState=mode;
        showMonitor(title);
        if(isBtConnected()) sendBt(cmd+TERMINATOR);
        toast(title+" выбран");
    }'''
if old_select not in s:
    raise SystemExit('selectMode block not found; refusing to patch unknown source')
s = s.replace(old_select, new_select, 1)

main.write_text(s, encoding='utf-8')

# Identify the fixed build separately.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 2", "versionCode 3")
g = g.replace("versionName '2.0.0'", "versionName '2.0.1'")
gradle.write_text(g, encoding='utf-8')
