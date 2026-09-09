from pathlib import Path
import re

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# This patch runs AFTER patch_android_auto.py and converts its fixed
# drying/roasting/cooking sequence into a user-configurable 4-stage program.

# Add auto page to the page set.
s = s.replace(
    '    private LinearLayout pageHost, monitorPage, configPage, pidPage, mqttPage;\n',
    '    private LinearLayout pageHost, monitorPage, configPage, pidPage, mqttPage, autoPage;\n',
    1
)

# Replace the fixed Auto field block.
old_fields = '''    private int modeState = 9;
    private volatile boolean androidAutoRunning = false;
    private volatile int androidAutoStage = 0; // 0 off, 1 drying, 2 roasting, 3 cooking, 4 complete, 5 aborted
    private long androidAutoThresholdSince = 0L;
    private long androidAutoLastHeartbeat = 0L;
    private TextView androidAutoStatus;
    private static final long AUTO_DRY_MS = 30L * 60L * 1000L;
    private static final long AUTO_ROAST_MS = 20L * 60L * 1000L;
    private static final long AUTO_HEARTBEAT_MS = 2000L;
'''
new_fields = '''    private int modeState = 9;
    private volatile boolean androidAutoRunning = false;
    // 0=off, 1..4=active user stage, 5=complete, 6=aborted
    private volatile int androidAutoStage = 0;
    private long androidAutoReachedSince = 0L;
    private long androidAutoLastHeartbeat = 0L;
    private TextView androidAutoStatus;
    private EditText autoProgramName;
    private final CheckBox[] autoStageEnabled = new CheckBox[4];
    private final EditText[] autoStageTemp = new EditText[4];
    private final EditText[] autoStageMinutes = new EditText[4];
    private final CheckBox[] autoStageStop = new CheckBox[4];
    private final boolean[] runStageEnabled = new boolean[4];
    private final double[] runStageTemp = new double[4];
    private final long[] runStageHoldMs = new long[4];
    private final boolean[] runStageStop = new boolean[4];
    private String runningProgramName = "";
    private static final long AUTO_HEARTBEAT_MS = 2000L;
'''
if old_fields not in s:
    raise SystemExit('fixed Auto field block not found')
s = s.replace(old_fields, new_fields, 1)

# Build Auto editor page.
old_build = '''        pidPage = buildPid();
        mqttPage = buildMqtt();
        return root;'''
new_build = '''        pidPage = buildPid();
        mqttPage = buildMqtt();
        autoPage = buildAutoProgram();
        return root;'''
if old_build not in s:
    raise SystemExit('buildRoot page block not found')
s = s.replace(old_build, new_build, 1)

# Insert editor UI before MQTT page builder.
marker = '    private LinearLayout buildMqtt() {\n'
ui = r'''    private LinearLayout buildAutoProgram() {
        LinearLayout root = page();
        TextView h = center("Авто программа", 22);
        h.setPadding(0, dp(8), 0, dp(8));
        root.addView(h);

        TextView info = txt("До 4 этапов. На каждом этапе Arduino работает в ПИД-режиме. Таймер выдержки начинается только после достижения заданной температуры камеры. После этапа можно перейти к следующему активному этапу или сразу выключить ТЭН.", 13, false);
        info.setPadding(dp(10), dp(4), dp(10), dp(12));
        root.addView(info);

        autoProgramName = edit("Название программы", InputType.TYPE_CLASS_TEXT);
        root.addView(autoProgramName, mw());

        for (int i = 0; i < 4; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(10));
            card.setBackgroundDrawable(round(Color.rgb(235,235,235), Color.rgb(150,150,150), 10));

            autoStageEnabled[i] = check(" Этап " + (i + 1) + " включен");
            autoStageEnabled[i].setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(autoStageEnabled[i]);

            autoStageTemp[i] = edit("Температура камеры, °C", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            autoStageMinutes[i] = edit("Выдержка после достижения, минут", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(autoStageTemp[i], mw());
            card.addView(autoStageMinutes[i], mw());

            autoStageStop[i] = check(" После этапа выключить ТЭН");
            card.addView(autoStageStop[i]);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(dp(4), dp(4), dp(4), dp(8));
            root.addView(card, cp);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button save = wide("Сохранить");
        Button start = wide("Запустить");
        Button stop = wide("СТОП");
        save.setOnClickListener(v -> { saveAutoProgram(); toast("Авто программа сохранена"); });
        start.setOnClickListener(v -> startAndroidAuto());
        stop.setOnClickListener(v -> stopHeating());
        buttons.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(start, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons, mw());

        TextView note = txt("Если после этапа не выбран СТОП, программа перейдёт к следующему включённому этапу. Если следующих этапов нет — ТЭН выключится автоматически.", 13, false);
        note.setPadding(dp(10), dp(12), dp(10), dp(14));
        root.addView(note);
        return root;
    }

'''
if marker not in s:
    raise SystemExit('buildMqtt marker not found')
s = s.replace(marker, ui + marker, 1)

# Menu opens editor instead of immediately starting fixed Auto.
old_menu = '        addSideMenuItem(panel, "Авто режим (Android)", this::startAndroidAuto);\n'
new_menu = '        addSideMenuItem(panel, "Авто программа", () -> showPage(autoPage,"Авто программа"));\n'
if old_menu not in s:
    raise SystemExit('Android Auto menu item not found')
s = s.replace(old_menu, new_menu, 1)

# Load/save the program in SharedPreferences.
load_marker = '        updateLogStatus(); applyDisplay();\n'
load_repl = '        loadAutoProgram();\n        updateLogStatus(); applyDisplay();\n'
if load_marker not in s:
    raise SystemExit('loadSettings end not found')
s = s.replace(load_marker, load_repl, 1)

save_marker = '        updateLogStatus();\n    }\n    private void applyDisplay() {'
save_repl = '        saveAutoProgram();\n        updateLogStatus();\n    }\n    private void applyDisplay() {'
if save_marker not in s:
    raise SystemExit('saveSettings end not found')
s = s.replace(save_marker, save_repl, 1)

# Replace all fixed Auto runtime methods with generic 4-stage engine.
start = s.find('    private void startAndroidAuto() {')
end = s.find('    private void cameraClick() {', start)
if start < 0 or end < 0:
    raise SystemExit('Auto runtime method range not found')

runtime = r'''    private void loadAutoProgram() {
        if(autoProgramName == null) return;
        autoProgramName.setText(prefs.getString("auto_name", "Моя программа"));
        for(int i=0;i<4;i++){
            boolean defEnabled = (i == 0);
            String defTemp = (i == 0) ? "40" : "";
            String defMin = (i == 0) ? "15" : "";
            autoStageEnabled[i].setChecked(prefs.getBoolean("auto_en_"+i, defEnabled));
            autoStageTemp[i].setText(prefs.getString("auto_temp_"+i, defTemp));
            autoStageMinutes[i].setText(prefs.getString("auto_min_"+i, defMin));
            autoStageStop[i].setChecked(prefs.getBoolean("auto_stop_"+i, false));
        }
    }

    private void saveAutoProgram() {
        if(autoProgramName == null) return;
        SharedPreferences.Editor e = prefs.edit();
        e.putString("auto_name", autoProgramName.getText().toString().trim());
        for(int i=0;i<4;i++){
            e.putBoolean("auto_en_"+i, autoStageEnabled[i].isChecked());
            e.putString("auto_temp_"+i, autoStageTemp[i].getText().toString().trim());
            e.putString("auto_min_"+i, autoStageMinutes[i].getText().toString().trim());
            e.putBoolean("auto_stop_"+i, autoStageStop[i].isChecked());
        }
        e.apply();
    }

    private boolean captureAutoProgram() {
        saveAutoProgram();
        boolean any = false;
        for(int i=0;i<4;i++){
            runStageEnabled[i] = autoStageEnabled[i].isChecked();
            runStageStop[i] = autoStageStop[i].isChecked();
            runStageTemp[i] = 0.0;
            runStageHoldMs[i] = 0L;
            if(!runStageEnabled[i]) continue;
            any = true;
            try {
                double t = Double.parseDouble(autoStageTemp[i].getText().toString().trim().replace(',','.'));
                double m = Double.parseDouble(autoStageMinutes[i].getText().toString().trim().replace(',','.'));
                if(t < 0.0 || t > 100.0){ toast("Этап "+(i+1)+": температура должна быть 0..100 °C"); return false; }
                if(m < 0.0 || m > 1440.0){ toast("Этап "+(i+1)+": выдержка должна быть 0..1440 минут"); return false; }
                runStageTemp[i] = t;
                runStageHoldMs[i] = (long)(m * 60_000.0);
            } catch(Exception ex){
                toast("Этап "+(i+1)+": проверьте температуру и время");
                return false;
            }
        }
        if(!any){ toast("Включите хотя бы один этап"); return false; }
        runningProgramName = autoProgramName.getText().toString().trim();
        if(runningProgramName.length()==0) runningProgramName = "Авто программа";
        return true;
    }

    private int firstEnabledStage() { return nextEnabledStage(-1); }

    private int nextEnabledStage(int after) {
        for(int i=after+1;i<4;i++) if(runStageEnabled[i]) return i;
        return -1;
    }

    private void startAndroidAuto() {
        if(androidAutoRunning){ toast("Авто программа уже выполняется"); return; }
        if(!captureAutoProgram()) return;
        if(!isBtConnected()){
            showMonitor("Авто программа");
            setAndroidAutoStatus("Авто: Bluetooth не подключен");
            toast("Для запуска авто подключите коптильню по Bluetooth");
            return;
        }

        int first = firstEnabledStage();
        if(first < 0) return;
        androidAutoRunning = true;
        androidAutoReachedSince = 0L;
        androidAutoLastHeartbeat = 0L;
        modeState = 2;
        showMonitor("Авто: "+runningProgramName);

        if(!sendBt("x1"+TERMINATOR) || !sendBt("a1"+TERMINATOR) || !sendBt("h"+TERMINATOR)){
            cancelAndroidAuto(true,"Ошибка запуска Auto");
            return;
        }
        androidAutoLastHeartbeat = android.os.SystemClock.elapsedRealtime();
        if(!enterAndroidAutoStage(first)) return;
        toast("Авто программа запущена");
    }

    private boolean enterAndroidAutoStage(int idx) {
        if(idx < 0 || idx >= 4 || !runStageEnabled[idx]) return false;
        androidAutoStage = idx + 1;
        androidAutoReachedSince = 0L;
        String target = BigDecimal.valueOf(runStageTemp[idx]).stripTrailingZeros().toPlainString();
        if(!sendBt("k"+target+TERMINATOR)){
            cancelAndroidAuto(true,"Не удалось задать температуру этапа "+(idx+1));
            return false;
        }
        titleView.setText("Авто: этап "+(idx+1)+" из 4");
        setAndroidAutoStatus("Этап "+(idx+1)+": нагрев до "+target+"°C · затем выдержка "+formatHoldMinutes(runStageHoldMs[idx]));
        return true;
    }

    private void processAndroidAuto(double chamber, double ignoredProbeK) {
        if(!androidAutoRunning) return;
        long now = android.os.SystemClock.elapsedRealtime();

        if(now - androidAutoLastHeartbeat >= AUTO_HEARTBEAT_MS){
            if(!isBtConnected() || !sendBt("h"+TERMINATOR)){
                cancelAndroidAuto(false,"Bluetooth потерян — Auto остановлен");
                return;
            }
            androidAutoLastHeartbeat = now;
        }

        if(!validAutoSensor(chamber)){
            cancelAndroidAuto(true,"Ошибка датчика температуры камеры");
            return;
        }

        int idx = androidAutoStage - 1;
        if(idx < 0 || idx >= 4) return;
        double target = runStageTemp[idx];
        long hold = runStageHoldMs[idx];

        if(androidAutoReachedSince == 0L){
            if(chamber >= target){
                androidAutoReachedSince = now;
                setAndroidAutoStatus("Этап "+(idx+1)+": "+fmt1(target)+"°C достигнуто · выдержка 00:00 / "+formatAutoTime(hold));
                if(hold <= 0L) completeAndroidAutoStage(idx);
            } else {
                setAndroidAutoStatus("Этап "+(idx+1)+": нагрев до "+fmt1(target)+"°C · сейчас "+fmt1(chamber)+"°C");
            }
            return;
        }

        long elapsed = now - androidAutoReachedSince;
        setAndroidAutoStatus("Этап "+(idx+1)+": выдержка при "+fmt1(target)+"°C · "+formatAutoTime(elapsed)+" / "+formatAutoTime(hold));
        if(elapsed >= hold) completeAndroidAutoStage(idx);
    }

    private void completeAndroidAutoStage(int idx) {
        if(!androidAutoRunning) return;
        if(runStageStop[idx]){
            finishAndroidAuto("Этап "+(idx+1)+" завершён — выбран СТОП");
            return;
        }
        int next = nextEnabledStage(idx);
        if(next < 0){
            finishAndroidAuto("Все активные этапы завершены");
            return;
        }
        enterAndroidAutoStage(next);
    }

    private void finishAndroidAuto(String reason) {
        if(isBtConnected()){
            sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        androidAutoRunning = false;
        androidAutoStage = 5;
        androidAutoReachedSince = 0L;
        modeState = 9;
        titleView.setText("Авто: Готово");
        setAndroidAutoStatus("Авто завершено · "+reason+" · ТЭН выключен");
        toast("Авто программа завершена. ТЭН выключен");
    }

    private void cancelAndroidAuto(boolean stopHeater, String reason) {
        boolean wasRunning = androidAutoRunning;
        androidAutoRunning = false;
        androidAutoReachedSince = 0L;
        if(isBtConnected()){
            if(stopHeater) sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        if(wasRunning){
            androidAutoStage = 6;
            modeState = 9;
            if(reason == null || reason.length() == 0) reason = "Авто остановлено";
            setAndroidAutoStatus("Авто: ОСТАНОВЛЕНО · "+reason);
            titleView.setText("Авто: Остановлено");
        }
    }

    private void stopHeating() {
        cancelAndroidAuto(false,"");
        if(isBtConnected()){
            sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        modeState = 9;
        androidAutoStage = 0;
        showMonitor("СТОП");
        setAndroidAutoStatus("ТЭН выключен");
        toast("СТОП: ТЭН выключен");
    }

    private void setAndroidAutoStatus(String text) {
        if(androidAutoStatus != null) androidAutoStatus.setText(text);
    }

    private boolean validAutoSensor(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v > -40.0 && v < 200.0;
    }

    private double parseTelemetryNumber(String s) {
        try { return Double.parseDouble(s.trim().replace(',','.')); }
        catch(Exception e){ return Double.NaN; }
    }

    private String fmt1(double v) { return String.format(Locale.US,"%.1f",v); }

    private String formatAutoTime(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if(hours > 0) return String.format(Locale.US,"%02d:%02d:%02d",hours,minutes,seconds);
        return String.format(Locale.US,"%02d:%02d",minutes,seconds);
    }

    private String formatHoldMinutes(long ms) {
        double min = ms / 60000.0;
        return BigDecimal.valueOf(min).stripTrailingZeros().toPlainString()+" мин";
    }

    private String androidAutoStageName() {
        if(androidAutoStage >= 1 && androidAutoStage <= 4) return "stage_"+androidAutoStage;
        if(androidAutoStage == 5) return "complete";
        if(androidAutoStage == 6) return "aborted";
        return "off";
    }

'''
s = s[:start] + runtime + s[end:]

# Camera click while Auto owns setpoint should not open manual setpoint dialog.
s = s.replace(
    '        else if(modeState==2) toast("Выбран авто режим работы (обсушка, обжарка, варка.)");\n',
    '        else if(modeState==2) toast("Уставкой управляет выполняемая Авто программа");\n',
    1
)

# Version after the 2.2.0 fixed-auto patch.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 7", "versionCode 8")
g = g.replace("versionName '2.2.0'", "versionName '2.3.0'")
gradle.write_text(g, encoding='utf-8')

main.write_text(s, encoding='utf-8')
print('Programmable Android Auto prepared: 4 user stages, target + hold + next/STOP')
