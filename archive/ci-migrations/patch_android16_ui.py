from pathlib import Path

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# Android 15/16 edge-to-edge: keep app controls outside status/navigation bars.
for old, new in [
    ('import android.graphics.drawable.GradientDrawable;\n', 'import android.graphics.drawable.GradientDrawable;\nimport android.graphics.drawable.ColorDrawable;\n'),
    ('import android.view.WindowManager;\n', 'import android.view.WindowInsets;\nimport android.view.WindowManager;\n'),
    ('import android.widget.EditText;\n', 'import android.widget.EditText;\nimport android.widget.FrameLayout;\n'),
    ('import android.widget.LinearLayout;\n', 'import android.widget.LinearLayout;\nimport android.widget.PopupWindow;\n'),
]:
    if new not in s:
        s = s.replace(old, new, 1)

field_marker = '    private int modeState = 9;\n'
field_repl = '''    private int modeState = 9;
    private PopupWindow sideMenuWindow;
    private View sideMenuPanel;
    private int systemInsetTop = 0;
    private int systemInsetBottom = 0;
'''
if field_marker in s:
    s = s.replace(field_marker, field_repl, 1)

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
            systemInsetTop = top;
            systemInsetBottom = bottom;
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

# Restore the SidebarV2 behavior from the original AIA: a right-side sliding panel,
# not an AlertDialog. MQTT is the only additional item in the native rebuild.
old_menu = '''    private void showMenu() {
        String[] items={"Настройка","Настройка ПИД","Ручной режим","Режим ПИД","Авто режим","MQTT","Выход"};
        new AlertDialog.Builder(this).setTitle("HomeSmoke").setItems(items,(d,w)->{
            switch(w){
                case 0: showPage(configPage,"Настройка"); break;
                case 1: showPage(pidPage,"Настройка ПИД"); break;
                case 2: selectMode(0,"Ручной режим"); break;
                case 3: selectMode(1,"Режим ПИД"); break;
                case 4: selectMode(2,"Авто режим"); break;
                case 5: showPage(mqttPage,"MQTT"); break;
                case 6: finish(); break;
            }
        }).show();
    }
'''
new_menu = '''    private void showMenu() {
        if(sideMenuWindow != null && sideMenuWindow.isShowing()) {
            closeSideMenu(null);
            return;
        }

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(92, 0, 0, 0));
        overlay.setPadding(0, systemInsetTop, 0, systemInsetBottom);

        int width = Math.min(dp(310), (int)(getResources().getDisplayMetrics().widthPixels * 0.84f));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.rgb(248,248,248));
        panel.setElevation(dp(12));
        panel.setPadding(0,0,0,dp(12));

        TextView header = txt("Домашняя коптильня", 21, true);
        header.setTextColor(Color.WHITE);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20),0,dp(12),0);
        header.setBackgroundColor(BLUE);
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(72)));

        addSideMenuItem(panel, "Настройка", () -> showPage(configPage,"Настройка"));
        addSideMenuItem(panel, "Настройка ПИД", () -> showPage(pidPage,"Настройка ПИД"));
        addSideMenuItem(panel, "Ручной режим", () -> selectMode(0,"Ручной режим"));
        addSideMenuItem(panel, "Режим ПИД", () -> selectMode(1,"Режим ПИД"));
        addSideMenuItem(panel, "Авто режим", () -> selectMode(2,"Авто режим"));
        addSideMenuItem(panel, "MQTT", () -> showPage(mqttPage,"MQTT"));
        addSideMenuItem(panel, "Выход", this::finish);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(width, -1, Gravity.END);
        overlay.addView(panel, pp);
        overlay.setOnClickListener(v -> closeSideMenu(null));
        panel.setOnClickListener(v -> { });

        PopupWindow popup = new PopupWindow(overlay, -1, -1, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setOnDismissListener(() -> {
            if(sideMenuWindow == popup) {
                sideMenuWindow = null;
                sideMenuPanel = null;
            }
        });
        sideMenuWindow = popup;
        sideMenuPanel = panel;
        popup.showAtLocation(menuButton, Gravity.TOP | Gravity.START, 0, 0);

        panel.post(() -> {
            panel.setTranslationX(panel.getWidth());
            panel.animate().translationX(0f).setDuration(220).start();
        });
    }

    private void addSideMenuItem(LinearLayout panel, String text, Runnable action) {
        TextView item = txt(text, 18, false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(22),0,dp(12),0);
        item.setBackgroundColor(Color.WHITE);
        item.setOnClickListener(v -> closeSideMenu(action));
        panel.addView(item, new LinearLayout.LayoutParams(-1, dp(58)));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(225,225,225));
        panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void closeSideMenu(Runnable after) {
        PopupWindow popup = sideMenuWindow;
        View panel = sideMenuPanel;
        if(popup == null || !popup.isShowing()) {
            if(after != null) after.run();
            return;
        }
        int shift = panel != null && panel.getWidth() > 0 ? panel.getWidth() : dp(310);
        if(panel == null) {
            popup.dismiss();
            if(after != null) after.run();
            return;
        }
        panel.animate().translationX(shift).setDuration(180).withEndAction(() -> {
            if(popup.isShowing()) popup.dismiss();
            if(after != null) after.run();
        }).start();
    }
'''
if old_menu not in s:
    raise SystemExit('showMenu block not found; refusing to patch unknown source')
s = s.replace(old_menu, new_menu, 1)

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

# Android back closes the sidebar first, as users expect from a drawer.
old_back = '''    @Override public void onBackPressed(){ if(backButton!=null&&backButton.getVisibility()==View.VISIBLE)backToMonitor();else super.onBackPressed(); }'''
new_back = '''    @Override public void onBackPressed(){
        if(sideMenuWindow!=null&&sideMenuWindow.isShowing()){ closeSideMenu(null); return; }
        if(backButton!=null&&backButton.getVisibility()==View.VISIBLE)backToMonitor();else super.onBackPressed();
    }'''
if old_back in s:
    s = s.replace(old_back, new_back, 1)

main.write_text(s, encoding='utf-8')

# Identify the fixed build separately.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 2", "versionCode 4")
g = g.replace("versionName '2.0.0'", "versionName '2.0.2'")
gradle.write_text(g, encoding='utf-8')
