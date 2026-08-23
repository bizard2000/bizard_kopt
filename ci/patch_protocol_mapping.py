from pathlib import Path

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# Homesmoke.aia uses App Inventor lists, which are 1-based.
# The Bluetooth frame begins with '|', so Java split("\\|", -1) creates a[0] == "".
# Therefore AIA list item N maps to Java array index N-1.
# Verified directly from Screen1.bky:
# item 2 camera, 3 probe K, 4 T_k, 5 T_p, 6 heater power, 7 mode,
# 8 status, 9 kP, 10 kI, 11 kD, 12 zP, 13 probe T.

old_update = '''    private void updateMonitor(String raw,String[] a) {
        tempCamera.setText(display(get(a,2))); tempK.setText(display(get(a,3))); tempT.setText(display(get(a,13)));
        line1.setText("Т° к - "+get(a,4)+" С°.   Т° п - "+get(a,5)+" С°.");
        line2.setText("Мощность ТЭНа - "+get(a,6)+" %.   Реж. работы - "+get(a,7)+".");
        line3.setText("kP "+get(a,9)+"   kI "+get(a,10)+"   kD "+get(a,11)+"   zP "+get(a,12)); statusLine.setText("Статус: "+get(a,8)); line4.setText("Данные Arduino: "+raw);
        curP.setText("kP-"+get(a,9)); curI.setText("kI-"+get(a,10)); curD.setText("kD-"+get(a,11)); curZ.setText("zP-"+get(a,12));
    }'''
new_update = '''    private void updateMonitor(String raw,String[] a) {
        // Exact mapping from the original Homesmoke.aia / Screen1.bky.
        tempCamera.setText(display(get(a,1))); tempK.setText(display(get(a,2))); tempT.setText(display(get(a,12)));
        line1.setText("Т° к - "+get(a,3)+" С°.   Т° п - "+get(a,4)+" С°.");
        line2.setText("Мощность ТЭНа - "+get(a,5)+" %.   Реж. работы - "+get(a,6)+".");
        line3.setText("kP "+get(a,8)+"   kI "+get(a,9)+"   kD "+get(a,10)+"   zP "+get(a,11)); statusLine.setText("Статус: "+get(a,7)); line4.setText("Данные Arduino: "+raw);
        curP.setText("kP-"+get(a,8)); curI.setText("kI-"+get(a,9)); curD.setText("kD-"+get(a,10)); curZ.setText("zP-"+get(a,11));
    }'''
if old_update not in s:
    raise SystemExit('updateMonitor block not found; refusing to patch unknown source')
s = s.replace(old_update, new_update, 1)

old_log = '''                String x=ts+";"+get(a,2)+";"+get(a,3)+";"+get(a,4)+";"+get(a,5)+";"+get(a,6)+";"+get(a,7)+";"+get(a,8)+";"+get(a,9)+";"+get(a,10)+";"+get(a,11)+";"+get(a,12)+";"+get(a,13)+";\\\""+raw.replace("\\\"","\\\"\\\"")+"\\\"\\n";'''
new_log = '''                String x=ts+";"+get(a,1)+";"+get(a,2)+";"+get(a,3)+";"+get(a,4)+";"+get(a,5)+";"+get(a,6)+";"+get(a,7)+";"+get(a,8)+";"+get(a,9)+";"+get(a,10)+";"+get(a,11)+";"+get(a,12)+";\\\""+raw.replace("\\\"","\\\"\\\"")+"\\\"\\n";'''
if old_log not in s:
    raise SystemExit('CSV mapping block not found; refusing to patch unknown source')
s = s.replace(old_log, new_log, 1)

old_json = '''    private String json(String raw,String[] a){ return "{\\\"ts\\\":"+System.currentTimeMillis()+",\\\"raw\\\":\\\""+esc(raw)+"\\\",\\\"temp_ds\\\":\\\""+esc(get(a,2))+"\\\",\\\"temp_tip_k\\\":\\\""+esc(get(a,3))+"\\\",\\\"temp_k\\\":\\\""+esc(get(a,4))+"\\\",\\\"temp_p\\\":\\\""+esc(get(a,5))+"\\\",\\\"heater_power\\\":\\\""+esc(get(a,6))+"\\\",\\\"mode\\\":\\\""+esc(get(a,7))+"\\\",\\\"status\\\":\\\""+esc(get(a,8))+"\\\",\\\"kP\\\":\\\""+esc(get(a,9))+"\\\",\\\"kI\\\":\\\""+esc(get(a,10))+"\\\",\\\"kD\\\":\\\""+esc(get(a,11))+"\\\",\\\"zP\\\":\\\""+esc(get(a,12))+"\\\",\\\"temp_tip_t\\\":\\\""+esc(get(a,13))+"\\\"}"; }'''
new_json = '''    private String json(String raw,String[] a){ return "{\\\"ts\\\":"+System.currentTimeMillis()+",\\\"raw\\\":\\\""+esc(raw)+"\\\",\\\"temp_ds\\\":\\\""+esc(get(a,1))+"\\\",\\\"temp_tip_k\\\":\\\""+esc(get(a,2))+"\\\",\\\"temp_k\\\":\\\""+esc(get(a,3))+"\\\",\\\"temp_p\\\":\\\""+esc(get(a,4))+"\\\",\\\"heater_power\\\":\\\""+esc(get(a,5))+"\\\",\\\"mode\\\":\\\""+esc(get(a,6))+"\\\",\\\"status\\\":\\\""+esc(get(a,7))+"\\\",\\\"kP\\\":\\\""+esc(get(a,8))+"\\\",\\\"kI\\\":\\\""+esc(get(a,9))+"\\\",\\\"kD\\\":\\\""+esc(get(a,10))+"\\\",\\\"zP\\\":\\\""+esc(get(a,11))+"\\\",\\\"temp_tip_t\\\":\\\""+esc(get(a,12))+"\\\"}"; }'''
if old_json not in s:
    raise SystemExit('MQTT JSON mapping block not found; refusing to patch unknown source')
s = s.replace(old_json, new_json, 1)

main.write_text(s, encoding='utf-8')

# Build number for the protocol-mapping fix.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 4", "versionCode 5")
g = g.replace("versionName '2.0.2'", "versionName '2.0.3'")
gradle.write_text(g, encoding='utf-8')
