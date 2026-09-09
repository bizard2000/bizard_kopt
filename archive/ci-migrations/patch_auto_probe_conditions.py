from pathlib import Path
import re

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# This patch runs AFTER patch_programmable_auto.py.
# It adds per-stage completion logic based on time and/or product probe K/T.

# Imports available on Android 4.0+.
if 'import android.widget.Spinner;\n' not in s:
    s = s.replace('import android.widget.ScrollView;\n', 'import android.widget.ScrollView;\nimport android.widget.Spinner;\nimport android.widget.ArrayAdapter;\n', 1)

# Editor/runtime fields.
old = '''    private final CheckBox[] autoStageStop = new CheckBox[4];
    private final boolean[] runStageEnabled = new boolean[4];
    private final double[] runStageTemp = new double[4];
    private final long[] runStageHoldMs = new long[4];
    private final boolean[] runStageStop = new boolean[4];
'''
new = '''    private final CheckBox[] autoStageStop = new CheckBox[4];
    private final Spinner[] autoStageCondition = new Spinner[4];
    private final EditText[] autoStageProbeTemp = new EditText[4];
    private final boolean[] runStageEnabled = new boolean[4];
    private final double[] runStageTemp = new double[4];
    private final long[] runStageHoldMs = new long[4];
    private final boolean[] runStageStop = new boolean[4];
    private final int[] runStageCondition = new int[4];
    private final double[] runStageProbeTemp = new double[4];
'''
if old not in s:
    raise SystemExit('programmable Auto fields not found')
s = s.replace(old, new, 1)

# Stage editor: completion logic selector + product probe threshold.
old = '''            card.addView(autoStageTemp[i], mw());
            card.addView(autoStageMinutes[i], mw());

            autoStageStop[i] = check(" После этапа выключить ТЭН");
'''
new = '''            card.addView(autoStageTemp[i], mw());
            card.addView(autoStageMinutes[i], mw());

            autoStageCondition[i] = new Spinner(this);
            String[] conditionItems = new String[]{
                    "Завершение: только время",
                    "Завершение: только щуп K",
                    "Завершение: только щуп T",
                    "Завершение: время ИЛИ щуп K",
                    "Завершение: время ИЛИ щуп T",
                    "Завершение: время И щуп K",
                    "Завершение: время И щуп T"
            };
            ArrayAdapter<String> conditionAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, conditionItems);
            conditionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            autoStageCondition[i].setAdapter(conditionAdapter);
            card.addView(autoStageCondition[i], mw());

            autoStageProbeTemp[i] = edit("Температура щупа для условия, °C", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            card.addView(autoStageProbeTemp[i], mw());

            autoStageStop[i] = check(" После этапа выключить ТЭН");
'''
if old not in s:
    raise SystemExit('stage editor insertion point not found')
s = s.replace(old, new, 1)

# Explain semantics clearly in the editor.
s = s.replace(
    'TextView info = txt("До 4 этапов. На каждом этапе Arduino работает в ПИД-режиме. Таймер выдержки начинается только после достижения заданной температуры камеры. После этапа можно перейти к следующему активному этапу или сразу выключить ТЭН.", 13, false);',
    'TextView info = txt("До 4 этапов. Для каждого этапа задаются температура камеры, выдержка и условие завершения: время, щуп K/T, ИЛИ или И. Таймер начинается после достижения уставки камеры. Условие щупа контролируется по фактической температуре продукта. После этапа — следующий активный этап или СТОП.", 13, false);',
    1
)

# Load saved condition/probe threshold.
old = '''            autoStageMinutes[i].setText(prefs.getString("auto_min_"+i, defMin));
            autoStageStop[i].setChecked(prefs.getBoolean("auto_stop_"+i, false));
'''
new = '''            autoStageMinutes[i].setText(prefs.getString("auto_min_"+i, defMin));
            autoStageCondition[i].setSelection(prefs.getInt("auto_cond_"+i, 0));
            autoStageProbeTemp[i].setText(prefs.getString("auto_probe_"+i, ""));
            autoStageStop[i].setChecked(prefs.getBoolean("auto_stop_"+i, false));
'''
if old not in s:
    raise SystemExit('loadAutoProgram block not found')
s = s.replace(old, new, 1)

# Save condition/probe threshold.
old = '''            e.putString("auto_min_"+i, autoStageMinutes[i].getText().toString().trim());
            e.putBoolean("auto_stop_"+i, autoStageStop[i].isChecked());
'''
new = '''            e.putString("auto_min_"+i, autoStageMinutes[i].getText().toString().trim());
            e.putInt("auto_cond_"+i, autoStageCondition[i].getSelectedItemPosition());
            e.putString("auto_probe_"+i, autoStageProbeTemp[i].getText().toString().trim());
            e.putBoolean("auto_stop_"+i, autoStageStop[i].isChecked());
'''
if old not in s:
    raise SystemExit('saveAutoProgram block not found')
s = s.replace(old, new, 1)

# Runtime capture defaults.
old = '''            runStageTemp[i] = 0.0;
            runStageHoldMs[i] = 0L;
            if(!runStageEnabled[i]) continue;
'''
new = '''            runStageTemp[i] = 0.0;
            runStageHoldMs[i] = 0L;
            runStageCondition[i] = autoStageCondition[i].getSelectedItemPosition();
            runStageProbeTemp[i] = Double.NaN;
            if(!runStageEnabled[i]) continue;
'''
if old not in s:
    raise SystemExit('capture defaults not found')
s = s.replace(old, new, 1)

# Replace stage validation/parsing with condition-aware parsing.
old = '''            try {
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
'''
new = '''            try {
                double t = Double.parseDouble(autoStageTemp[i].getText().toString().trim().replace(',','.'));
                String minRaw = autoStageMinutes[i].getText().toString().trim().replace(',','.');
                double m = minRaw.length()==0 ? 0.0 : Double.parseDouble(minRaw);
                if(t < 0.0 || t > 100.0){ toast("Этап "+(i+1)+": температура камеры должна быть 0..100 °C"); return false; }
                if(m < 0.0 || m > 1440.0){ toast("Этап "+(i+1)+": выдержка должна быть 0..1440 минут"); return false; }
                runStageTemp[i] = t;
                runStageHoldMs[i] = (long)(m * 60_000.0);

                if(autoConditionUsesProbe(runStageCondition[i])){
                    String probeRaw = autoStageProbeTemp[i].getText().toString().trim().replace(',','.');
                    if(probeRaw.length()==0){ toast("Этап "+(i+1)+": задайте температуру щупа"); return false; }
                    double p = Double.parseDouble(probeRaw);
                    if(p < 0.0 || p > 100.0){ toast("Этап "+(i+1)+": температура щупа должна быть 0..100 °C"); return false; }
                    runStageProbeTemp[i] = p;
                }
            } catch(Exception ex){
                toast("Этап "+(i+1)+": проверьте температуру, время и условие");
                return false;
            }
'''
if old not in s:
    raise SystemExit('capture validation block not found')
s = s.replace(old, new, 1)

# Pass both product probes into the Auto engine.
old_call = 'processAndroidAuto(parseTelemetryNumber(get(a,1)), parseTelemetryNumber(get(a,2)));'
new_call = 'processAndroidAuto(parseTelemetryNumber(get(a,1)), parseTelemetryNumber(get(a,2)), parseTelemetryNumber(get(a,12)));'
if old_call not in s:
    raise SystemExit('Auto telemetry call not found')
s = s.replace(old_call, new_call, 1)

# Stage-entry text now describes the selected completion condition.
old = '        setAndroidAutoStatus("Этап "+(idx+1)+": нагрев до "+target+"°C · затем выдержка "+formatHoldMinutes(runStageHoldMs[idx]));\n'
new = '        setAndroidAutoStatus("Этап "+(idx+1)+": камера "+target+"°C · "+autoConditionDescription(idx));\n'
if old not in s:
    raise SystemExit('enterAndroidAutoStage status line not found')
s = s.replace(old, new, 1)

# Replace generic time-only runtime evaluator with time/probe logic.
start = s.find('    private void processAndroidAuto(')
end = s.find('    private void completeAndroidAutoStage(', start)
if start < 0 or end < 0:
    raise SystemExit('processAndroidAuto range not found')

runtime = r'''    private boolean autoConditionUsesTime(int cond) {
        return cond == 0 || cond == 3 || cond == 4 || cond == 5 || cond == 6;
    }

    private boolean autoConditionUsesProbe(int cond) {
        return cond >= 1 && cond <= 6;
    }

    private boolean autoConditionUsesProbeK(int cond) {
        return cond == 1 || cond == 3 || cond == 5;
    }

    private boolean autoConditionUsesProbeT(int cond) {
        return cond == 2 || cond == 4 || cond == 6;
    }

    private boolean autoConditionMet(int cond, boolean timeDone, boolean probeDone) {
        switch(cond){
            case 0: return timeDone;
            case 1:
            case 2: return probeDone;
            case 3:
            case 4: return timeDone || probeDone;
            case 5:
            case 6: return timeDone && probeDone;
            default: return timeDone;
        }
    }

    private String autoConditionDescription(int idx) {
        int cond = runStageCondition[idx];
        String hold = "время " + formatHoldMinutes(runStageHoldMs[idx]);
        String probe = (autoConditionUsesProbeK(cond) ? "щуп K ≥ " : "щуп T ≥ ") + fmt1(runStageProbeTemp[idx]) + "°C";
        switch(cond){
            case 0: return hold;
            case 1:
            case 2: return probe;
            case 3:
            case 4: return hold + " ИЛИ " + probe;
            case 5:
            case 6: return hold + " И " + probe;
            default: return hold;
        }
    }

    private void processAndroidAuto(double chamber, double probeK, double probeT) {
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
        int cond = runStageCondition[idx];
        double target = runStageTemp[idx];
        long hold = runStageHoldMs[idx];

        double selectedProbe = Double.NaN;
        String probeName = "";
        if(autoConditionUsesProbeK(cond)){ selectedProbe = probeK; probeName = "K"; }
        else if(autoConditionUsesProbeT(cond)){ selectedProbe = probeT; probeName = "T"; }

        if(autoConditionUsesProbe(cond) && !validAutoSensor(selectedProbe)){
            cancelAndroidAuto(true,"Ошибка щупа "+probeName+" на этапе "+(idx+1));
            return;
        }

        if(androidAutoReachedSince == 0L && chamber >= target){
            androidAutoReachedSince = now;
        }

        boolean timeDone = false;
        long elapsed = 0L;
        if(autoConditionUsesTime(cond) && androidAutoReachedSince > 0L){
            elapsed = now - androidAutoReachedSince;
            timeDone = elapsed >= hold;
        }

        boolean probeDone = false;
        if(autoConditionUsesProbe(cond)){
            probeDone = selectedProbe >= runStageProbeTemp[idx];
        }

        if(autoConditionMet(cond, timeDone, probeDone)){
            completeAndroidAutoStage(idx);
            return;
        }

        StringBuilder st = new StringBuilder();
        st.append("Этап ").append(idx+1).append(": ");
        if(chamber < target){
            st.append("нагрев ").append(fmt1(chamber)).append("/").append(fmt1(target)).append("°C");
        } else {
            st.append("камера ").append(fmt1(chamber)).append("°C");
        }
        if(autoConditionUsesTime(cond)){
            if(androidAutoReachedSince == 0L) st.append(" · таймер ждёт уставку");
            else st.append(" · время ").append(formatAutoTime(elapsed)).append("/").append(formatAutoTime(hold));
        }
        if(autoConditionUsesProbe(cond)){
            st.append(" · ").append(probeName).append(" ").append(fmt1(selectedProbe)).append("/").append(fmt1(runStageProbeTemp[idx])).append("°C");
        }
        if(cond==3 || cond==4) st.append(" · условие ИЛИ");
        else if(cond==5 || cond==6) st.append(" · условие И");
        setAndroidAutoStatus(st.toString());
    }

'''
s = s[:start] + runtime + s[end:]

# Bump modern app version after all previous version patches.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 9', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.4.0'", g, count=1)
gradle.write_text(g, encoding='utf-8')

main.write_text(s, encoding='utf-8')
print('Programmable Auto extended: time / probe K / probe T / OR / AND')
