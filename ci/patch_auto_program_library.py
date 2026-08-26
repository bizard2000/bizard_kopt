from pathlib import Path
import re

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# This patch runs AFTER patch_auto_probe_conditions.py.
# It turns the single saved Auto recipe into a selectable library of recipes.

# Library fields.
old = '    private EditText autoProgramName;\n'
new = '''    private EditText autoProgramName;
    private Spinner autoProgramSelector;
    private ArrayAdapter<String> autoProgramLibraryAdapter;
    private boolean autoLibraryLoading = false;
    private int autoLibrarySelected = 0;
    private static final String AUTO_LIBRARY_KEY = "auto_library_json_v1";
    private static final String AUTO_LIBRARY_SELECTED_KEY = "auto_library_selected_v1";
'''
if old not in s:
    raise SystemExit('autoProgramName field not found')
s = s.replace(old, new, 1)

# Expand the editor intro.
s = s.replace(
    'TextView info = txt("До 4 этапов. Для каждого этапа задаются температура камеры, выдержка и условие завершения: время, щуп K/T, ИЛИ или И. Таймер начинается после достижения уставки камеры. Условие щупа контролируется по фактической температуре продукта. После этапа — следующий активный этап или СТОП.", 13, false);',
    'TextView info = txt("Можно хранить несколько Auto-программ. В каждой — до 4 этапов с температурой камеры, выдержкой и условием завершения по времени и/или щупу K/T. Таймер начинается после достижения уставки камеры. После этапа — следующий активный этап или СТОП.", 13, false);',
    1
)

# Insert selector and New/Delete controls before the program name field.
old = '''        autoProgramName = edit("Название программы", InputType.TYPE_CLASS_TEXT);
        root.addView(autoProgramName, mw());
'''
new = '''        TextView libraryLabel = txt("Сохранённые программы", 14, true);
        libraryLabel.setPadding(dp(6), dp(4), dp(6), dp(2));
        root.addView(libraryLabel);

        autoProgramSelector = new Spinner(this);
        autoProgramLibraryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new java.util.ArrayList<String>());
        autoProgramLibraryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        autoProgramSelector.setAdapter(autoProgramLibraryAdapter);
        autoProgramSelector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if(autoLibraryLoading) return;
                loadAutoLibraryProgram(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        root.addView(autoProgramSelector, mw());

        LinearLayout libraryButtons = new LinearLayout(this);
        libraryButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button newProgram = wide("Новая");
        Button deleteProgram = wide("Удалить");
        newProgram.setOnClickListener(v -> createNewAutoLibraryProgram());
        deleteProgram.setOnClickListener(v -> deleteCurrentAutoLibraryProgram());
        libraryButtons.addView(newProgram, new LinearLayout.LayoutParams(0, -2, 1));
        libraryButtons.addView(deleteProgram, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(libraryButtons, mw());

        autoProgramName = edit("Название программы", InputType.TYPE_CLASS_TEXT);
        root.addView(autoProgramName, mw());
'''
if old not in s:
    raise SystemExit('autoProgramName UI block not found')
s = s.replace(old, new, 1)

# Replace single-profile persistence with a JSON-backed program library.
start = s.find('    private void loadAutoProgram() {')
end = s.find('    private boolean captureAutoProgram() {', start)
if start < 0 or end < 0:
    raise SystemExit('Auto load/save method range not found')

library_methods = r'''    private org.json.JSONObject legacyAutoProgramObject() throws org.json.JSONException {
        org.json.JSONObject p = new org.json.JSONObject();
        p.put("name", prefs.getString("auto_name", "Моя программа"));
        org.json.JSONArray stages = new org.json.JSONArray();
        for(int i=0;i<4;i++){
            org.json.JSONObject st = new org.json.JSONObject();
            boolean defEnabled = (i == 0);
            String defTemp = (i == 0) ? "40" : "";
            String defMin = (i == 0) ? "15" : "";
            st.put("enabled", prefs.getBoolean("auto_en_"+i, defEnabled));
            st.put("temp", prefs.getString("auto_temp_"+i, defTemp));
            st.put("minutes", prefs.getString("auto_min_"+i, defMin));
            st.put("condition", prefs.getInt("auto_cond_"+i, 0));
            st.put("probe", prefs.getString("auto_probe_"+i, ""));
            st.put("stop", prefs.getBoolean("auto_stop_"+i, false));
            stages.put(st);
        }
        p.put("stages", stages);
        return p;
    }

    private org.json.JSONArray readAutoLibrary() {
        try {
            String raw = prefs.getString(AUTO_LIBRARY_KEY, "");
            if(raw != null && raw.trim().length() > 0){
                org.json.JSONArray a = new org.json.JSONArray(raw);
                if(a.length() > 0) return a;
            }
        } catch(Exception ignored) { }
        org.json.JSONArray a = new org.json.JSONArray();
        try { a.put(legacyAutoProgramObject()); } catch(Exception ignored) { }
        prefs.edit().putString(AUTO_LIBRARY_KEY, a.toString()).putInt(AUTO_LIBRARY_SELECTED_KEY, 0).apply();
        return a;
    }

    private void writeAutoLibrary(org.json.JSONArray a) {
        prefs.edit().putString(AUTO_LIBRARY_KEY, a.toString()).putInt(AUTO_LIBRARY_SELECTED_KEY, autoLibrarySelected).apply();
    }

    private org.json.JSONObject editorAutoProgramObject() throws org.json.JSONException {
        org.json.JSONObject p = new org.json.JSONObject();
        String name = autoProgramName == null ? "" : autoProgramName.getText().toString().trim();
        if(name.length()==0) name = "Программа " + (autoLibrarySelected + 1);
        p.put("name", name);
        org.json.JSONArray stages = new org.json.JSONArray();
        for(int i=0;i<4;i++){
            org.json.JSONObject st = new org.json.JSONObject();
            st.put("enabled", autoStageEnabled[i].isChecked());
            st.put("temp", autoStageTemp[i].getText().toString().trim());
            st.put("minutes", autoStageMinutes[i].getText().toString().trim());
            st.put("condition", autoStageCondition[i].getSelectedItemPosition());
            st.put("probe", autoStageProbeTemp[i].getText().toString().trim());
            st.put("stop", autoStageStop[i].isChecked());
            stages.put(st);
        }
        p.put("stages", stages);
        return p;
    }

    private void applyAutoProgramObject(org.json.JSONObject p) {
        if(autoProgramName == null || p == null) return;
        autoLibraryLoading = true;
        try {
            autoProgramName.setText(p.optString("name", "Моя программа"));
            org.json.JSONArray stages = p.optJSONArray("stages");
            for(int i=0;i<4;i++){
                org.json.JSONObject st = stages != null ? stages.optJSONObject(i) : null;
                boolean defEnabled = (i == 0);
                String defTemp = (i == 0) ? "40" : "";
                String defMin = (i == 0) ? "15" : "";
                autoStageEnabled[i].setChecked(st != null ? st.optBoolean("enabled", defEnabled) : defEnabled);
                autoStageTemp[i].setText(st != null ? st.optString("temp", defTemp) : defTemp);
                autoStageMinutes[i].setText(st != null ? st.optString("minutes", defMin) : defMin);
                int cond = st != null ? st.optInt("condition", 0) : 0;
                if(cond < 0 || cond > 6) cond = 0;
                autoStageCondition[i].setSelection(cond);
                autoStageProbeTemp[i].setText(st != null ? st.optString("probe", "") : "");
                autoStageStop[i].setChecked(st != null && st.optBoolean("stop", false));
            }
        } finally {
            autoLibraryLoading = false;
        }
    }

    private void refreshAutoLibrarySelector(org.json.JSONArray a, int selected) {
        if(autoProgramSelector == null || autoProgramLibraryAdapter == null) return;
        autoLibraryLoading = true;
        autoProgramLibraryAdapter.clear();
        for(int i=0;i<a.length();i++){
            org.json.JSONObject p = a.optJSONObject(i);
            String name = p == null ? ("Программа "+(i+1)) : p.optString("name", "Программа "+(i+1));
            if(name.trim().length()==0) name = "Программа "+(i+1);
            autoProgramLibraryAdapter.add(name);
        }
        autoProgramLibraryAdapter.notifyDataSetChanged();
        if(selected < 0) selected = 0;
        if(selected >= a.length()) selected = Math.max(0, a.length()-1);
        autoLibrarySelected = selected;
        if(a.length() > 0) autoProgramSelector.setSelection(selected, false);
        autoLibraryLoading = false;
    }

    private void loadAutoProgram() {
        if(autoProgramName == null) return;
        org.json.JSONArray a = readAutoLibrary();
        int selected = prefs.getInt(AUTO_LIBRARY_SELECTED_KEY, 0);
        if(selected < 0 || selected >= a.length()) selected = 0;
        refreshAutoLibrarySelector(a, selected);
        org.json.JSONObject p = a.optJSONObject(selected);
        if(p != null) applyAutoProgramObject(p);
        autoLibrarySelected = selected;
    }

    private void loadAutoLibraryProgram(int position) {
        org.json.JSONArray a = readAutoLibrary();
        if(position < 0 || position >= a.length()) return;
        autoLibrarySelected = position;
        prefs.edit().putInt(AUTO_LIBRARY_SELECTED_KEY, position).apply();
        org.json.JSONObject p = a.optJSONObject(position);
        if(p != null) applyAutoProgramObject(p);
    }

    private void saveLegacyAutoFlat() {
        if(autoProgramName == null) return;
        SharedPreferences.Editor e = prefs.edit();
        e.putString("auto_name", autoProgramName.getText().toString().trim());
        for(int i=0;i<4;i++){
            e.putBoolean("auto_en_"+i, autoStageEnabled[i].isChecked());
            e.putString("auto_temp_"+i, autoStageTemp[i].getText().toString().trim());
            e.putString("auto_min_"+i, autoStageMinutes[i].getText().toString().trim());
            e.putInt("auto_cond_"+i, autoStageCondition[i].getSelectedItemPosition());
            e.putString("auto_probe_"+i, autoStageProbeTemp[i].getText().toString().trim());
            e.putBoolean("auto_stop_"+i, autoStageStop[i].isChecked());
        }
        e.apply();
    }

    private void saveAutoProgram() {
        if(autoProgramName == null) return;
        try {
            org.json.JSONArray a = readAutoLibrary();
            if(a.length()==0){ a.put(editorAutoProgramObject()); autoLibrarySelected = 0; }
            else {
                if(autoLibrarySelected < 0 || autoLibrarySelected >= a.length()) autoLibrarySelected = 0;
                a.put(autoLibrarySelected, editorAutoProgramObject());
            }
            writeAutoLibrary(a);
            refreshAutoLibrarySelector(a, autoLibrarySelected);
            saveLegacyAutoFlat();
        } catch(Exception ex){
            toast("Не удалось сохранить Auto программу");
        }
    }

    private void createNewAutoLibraryProgram() {
        try {
            org.json.JSONArray a = readAutoLibrary();
            org.json.JSONObject p = new org.json.JSONObject();
            p.put("name", "Новая программа " + (a.length()+1));
            org.json.JSONArray stages = new org.json.JSONArray();
            for(int i=0;i<4;i++){
                org.json.JSONObject st = new org.json.JSONObject();
                st.put("enabled", i==0);
                st.put("temp", i==0 ? "40" : "");
                st.put("minutes", i==0 ? "15" : "");
                st.put("condition", 0);
                st.put("probe", "");
                st.put("stop", false);
                stages.put(st);
            }
            p.put("stages", stages);
            a.put(p);
            autoLibrarySelected = a.length()-1;
            writeAutoLibrary(a);
            refreshAutoLibrarySelector(a, autoLibrarySelected);
            applyAutoProgramObject(p);
            toast("Создана новая Auto программа");
        } catch(Exception ex){ toast("Не удалось создать программу"); }
    }

    private void deleteCurrentAutoLibraryProgram() {
        if(androidAutoRunning){ toast("Нельзя удалить программу во время Auto"); return; }
        org.json.JSONArray old = readAutoLibrary();
        if(old.length() <= 1){
            toast("Должна остаться хотя бы одна программа");
            return;
        }
        org.json.JSONArray a = new org.json.JSONArray();
        for(int i=0;i<old.length();i++) if(i != autoLibrarySelected) a.put(old.opt(i));
        if(autoLibrarySelected >= a.length()) autoLibrarySelected = a.length()-1;
        writeAutoLibrary(a);
        refreshAutoLibrarySelector(a, autoLibrarySelected);
        org.json.JSONObject p = a.optJSONObject(autoLibrarySelected);
        if(p != null) applyAutoProgramObject(p);
        saveLegacyAutoFlat();
        toast("Auto программа удалена");
    }

'''
s = s[:start] + library_methods + s[end:]

# Bump modern app version after the probe-condition patch.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 5', g, count=1)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.5.0'", g, count=1)
gradle.write_text(g, encoding='utf-8')

# Sanity checks.
for token in ['autoProgramSelector', 'AUTO_LIBRARY_KEY', 'createNewAutoLibraryProgram', 'deleteCurrentAutoLibraryProgram', 'readAutoLibrary']:
    if token not in s:
        raise SystemExit('Auto library patch missing token: ' + token)

main.write_text(s, encoding='utf-8')
print('Programmable Auto upgraded to multi-program library; existing single profile migrates automatically')
