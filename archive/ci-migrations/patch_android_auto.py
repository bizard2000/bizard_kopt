from pathlib import Path

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# Android-hosted automatic program. Arduino remains only an executor (manual/PID/OFF).
# Auto stages preserve the intent of the original Arduino program but fix its two defects:
# stage timers start once, and product thresholds use >= instead of float ==.

field = '    private int modeState = 9;\n'
fields = '''    private int modeState = 9;
    private volatile boolean androidAutoRunning = false;
    private volatile int androidAutoStage = 0; // 0 off, 1 drying, 2 roasting, 3 cooking, 4 complete, 5 aborted
    private long androidAutoThresholdSince = 0L;
    private long androidAutoLastHeartbeat = 0L;
    private TextView androidAutoStatus;
    private static final long AUTO_DRY_MS = 30L * 60L * 1000L;
    private static final long AUTO_ROAST_MS = 20L * 60L * 1000L;
    private static final long AUTO_HEARTBEAT_MS = 2000L;
'''
if field not in s:
    raise SystemExit('modeState field not found')
s = s.replace(field, fields, 1)

# Prominent auto state line on the monitor, independent from the raw-data visibility setting.
old = '''        root.addView(rawBox,mw());
        TextView hint = center("Нажмите на температуру камеры для задания температуры/мощности в выбранном режиме",13);'''
new = '''        root.addView(rawBox,mw());
        androidAutoStatus = center("Авто Android: выключен",15);
        androidAutoStatus.setPadding(dp(10),dp(9),dp(10),dp(9));
        androidAutoStatus.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(androidAutoStatus,mw());
        TextView hint = center("Нажмите на температуру камеры для задания температуры/мощности в выбранном режиме",13);'''
if old not in s:
    raise SystemExit('monitor insertion point not found')
s = s.replace(old, new, 1)

# Menu: Auto is now an Android program. Add an explicit STOP item.
old = '''        addSideMenuItem(panel, "Авто режим", () -> selectMode(2,"Авто режим"));
        addSideMenuItem(panel, "MQTT", () -> showPage(mqttPage,"MQTT"));'''
new = '''        addSideMenuItem(panel, "Авто режим (Android)", this::startAndroidAuto);
        addSideMenuItem(panel, "СТОП / ТЭН выкл.", this::stopHeating);
        addSideMenuItem(panel, "MQTT", () -> showPage(mqttPage,"MQTT"));'''
if old not in s:
    raise SystemExit('sidebar auto item not found')
s = s.replace(old, new, 1)

# Manual/PID selection cancels Android Auto first. mode=2 is never sent to Arduino anymore.
old = '''    private void selectMode(int mode,String title) {
        String cmd=mode==0?"a0":mode==1?"a1":"a2";
        modeState=mode;
        showMonitor(title);
        if(isBtConnected()) sendBt(cmd+TERMINATOR);
        toast(title+" выбран");
    }'''
new = '''    private void selectMode(int mode,String title) {
        if(mode!=0 && mode!=1) return;
        cancelAndroidAuto(false, "");
        String cmd=mode==0?"a0":"a1";
        modeState=mode;
        showMonitor(title);
        if(isBtConnected()) sendBt(cmd+TERMINATOR);
        toast(title+" выбран");
    }'''
if old not in s:
    raise SystemExit('selectMode block not found')
s = s.replace(old, new, 1)

# Exact protocol mapping already applied by patch_protocol_mapping.py.
old = '''    private void updateMonitor(String raw,String[] a) {
        // Exact mapping from the original Homesmoke.aia / Screen1.bky.
        tempCamera.setText(display(get(a,1))); tempK.setText(display(get(a,2))); tempT.setText(display(get(a,12)));
        line1.setText("Т° к - "+get(a,3)+" С°.   Т° п - "+get(a,4)+" С°.");
        line2.setText("Мощность ТЭНа - "+get(a,5)+" %.   Реж. работы - "+get(a,6)+".");
        line3.setText("kP "+get(a,8)+"   kI "+get(a,9)+"   kD "+get(a,10)+"   zP "+get(a,11)); statusLine.setText("Статус: "+get(a,7)); line4.setText("Данные Arduino: "+raw);
        curP.setText("kP-"+get(a,8)); curI.setText("kI-"+get(a,9)); curD.setText("kD-"+get(a,10)); curZ.setText("zP-"+get(a,11));
    }'''
new = '''    private void updateMonitor(String raw,String[] a) {
        // Exact mapping from the original Homesmoke.aia / Arduino telemetry frame.
        tempCamera.setText(display(get(a,1))); tempK.setText(display(get(a,2))); tempT.setText(display(get(a,12)));
        line1.setText("Т° к - "+get(a,3)+" С°.   Т° п - "+get(a,4)+" С°.");
        line2.setText("Мощность ТЭНа - "+get(a,5)+" %.   Реж. Arduino - "+get(a,6)+".");
        line3.setText("kP "+get(a,8)+"   kI "+get(a,9)+"   kD "+get(a,10)+"   zP "+get(a,11));
        statusLine.setText("Последняя команда: "+get(a,7));
        line4.setText("Данные Arduino: "+raw);
        curP.setText("kP-"+get(a,8)); curI.setText("kI-"+get(a,9)); curD.setText("kD-"+get(a,10)); curZ.setText("zP-"+get(a,11));
        processAndroidAuto(parseTelemetryNumber(get(a,1)), parseTelemetryNumber(get(a,2)));
    }'''
if old not in s:
    raise SystemExit('mapped updateMonitor block not found')
s = s.replace(old, new, 1)

# Remote setpoint is rejected while Android Auto owns the chamber setpoint.
old = '''            double value=o.getDouble("value");
            if(value<0||value>100){ publishRemoteAck(value,false,"out_of_range"); return; }'''
new = '''            double value=o.getDouble("value");
            if(value<0||value>100){ publishRemoteAck(value,false,"out_of_range"); return; }
            if(androidAutoRunning){ publishRemoteAck(value,false,"android_auto_running"); return; }'''
if old not in s:
    raise SystemExit('remote set_temp validation block not found')
s = s.replace(old, new, 1)

# Add the Android auto engine before cameraClick().
marker = '    private void cameraClick() {\n'
auto_methods = r'''    private void startAndroidAuto() {
        if(androidAutoRunning){ toast("Авто режим уже выполняется"); return; }
        if(!isBtConnected()){
            modeState=2;
            showMonitor("Авто режим (Android)");
            setAndroidAutoStatus("Авто: Bluetooth не подключен");
            toast("Для запуска авто подключите коптильню по Bluetooth");
            return;
        }

        androidAutoRunning=true;
        androidAutoStage=1;
        androidAutoThresholdSince=0L;
        androidAutoLastHeartbeat=0L;
        modeState=2;
        showMonitor("Авто: Обсушка");
        setAndroidAutoStatus("Авто: ОБСУШКА · камера 60°C · переход: K≥34°C или 30 мин после Tкамеры≥57°C");

        // x1/h are new safety commands. Old Arduino firmware simply ignores them,
        // while the revised firmware uses them as a host-auto watchdog.
        if(!sendBt("x1"+TERMINATOR) || !sendBt("a1"+TERMINATOR) || !sendBt("k60"+TERMINATOR) || !sendBt("h"+TERMINATOR)){
            cancelAndroidAuto(true,"Ошибка запуска Auto");
            return;
        }
        androidAutoLastHeartbeat=android.os.SystemClock.elapsedRealtime();
        toast("Авто режим запущен: обсушка 60°C");
    }

    private void processAndroidAuto(double chamber, double probeK) {
        if(!androidAutoRunning) return;
        long now=android.os.SystemClock.elapsedRealtime();

        if(now-androidAutoLastHeartbeat>=AUTO_HEARTBEAT_MS){
            if(!isBtConnected() || !sendBt("h"+TERMINATOR)){
                cancelAndroidAuto(false,"Bluetooth потерян — Auto остановлен");
                return;
            }
            androidAutoLastHeartbeat=now;
        }

        if(!validAutoSensor(chamber) || !validAutoSensor(probeK)){
            cancelAndroidAuto(true,"Ошибка датчика камеры или щупа K");
            return;
        }

        if(androidAutoStage==1){
            if(androidAutoThresholdSince==0L && chamber>=57.0) androidAutoThresholdSince=now;
            if(androidAutoThresholdSince>0L){
                long elapsed=now-androidAutoThresholdSince;
                setAndroidAutoStatus("Авто: ОБСУШКА · 60°C · K="+fmt1(probeK)+"°C · таймер "+formatAutoTime(elapsed)+" / 30:00");
                if(probeK>=34.0 || elapsed>=AUTO_DRY_MS) advanceAndroidAuto(2);
            } else {
                setAndroidAutoStatus("Авто: ОБСУШКА · нагрев камеры до 57°C · сейчас "+fmt1(chamber)+"°C");
            }
        }
        else if(androidAutoStage==2){
            if(androidAutoThresholdSince==0L && chamber>=85.0) androidAutoThresholdSince=now;
            if(androidAutoThresholdSince>0L){
                long elapsed=now-androidAutoThresholdSince;
                setAndroidAutoStatus("Авто: ОБЖАРКА · 90°C · K="+fmt1(probeK)+"°C · таймер "+formatAutoTime(elapsed)+" / 20:00");
                if(probeK>=54.0 || elapsed>=AUTO_ROAST_MS) advanceAndroidAuto(3);
            } else {
                setAndroidAutoStatus("Авто: ОБЖАРКА · нагрев камеры до 85°C · сейчас "+fmt1(chamber)+"°C");
            }
        }
        else if(androidAutoStage==3){
            setAndroidAutoStatus("Авто: ВАРКА · камера 80°C · завершение при Tкамеры≥77°C и K≥69°C · K="+fmt1(probeK)+"°C");
            if(chamber>=77.0 && probeK>=69.0) finishAndroidAuto();
        }
    }

    private void advanceAndroidAuto(int stage) {
        androidAutoStage=stage;
        androidAutoThresholdSince=0L;
        if(stage==2){
            if(!sendBt("k90"+TERMINATOR)){ cancelAndroidAuto(true,"Не удалось задать 90°C"); return; }
            titleView.setText("Авто: Обжарка");
            setAndroidAutoStatus("Авто: ОБЖАРКА · камера 90°C · переход: K≥54°C или 20 мин после Tкамеры≥85°C");
        } else if(stage==3){
            if(!sendBt("k80"+TERMINATOR)){ cancelAndroidAuto(true,"Не удалось задать 80°C"); return; }
            titleView.setText("Авто: Варка");
            setAndroidAutoStatus("Авто: ВАРКА · камера 80°C · завершение при Tкамеры≥77°C и K≥69°C");
        }
    }

    private void finishAndroidAuto() {
        if(isBtConnected()){
            sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        androidAutoRunning=false;
        androidAutoStage=4;
        androidAutoThresholdSince=0L;
        modeState=9;
        titleView.setText("Авто: Готово");
        setAndroidAutoStatus("Авто завершено · ТЭН выключен");
        toast("Авто программа завершена. ТЭН выключен");
    }

    private void cancelAndroidAuto(boolean stopHeater, String reason) {
        boolean wasRunning=androidAutoRunning;
        androidAutoRunning=false;
        androidAutoThresholdSince=0L;
        if(isBtConnected()){
            if(stopHeater) sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        if(wasRunning){
            androidAutoStage=5;
            modeState=9;
            if(reason==null || reason.length()==0) reason="Авто остановлен";
            setAndroidAutoStatus("Авто: ОСТАНОВЛЕНО · "+reason);
            if(stopHeater) titleView.setText("Авто: Остановлено");
        }
    }

    private void stopHeating() {
        cancelAndroidAuto(false,"");
        if(isBtConnected()){
            sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        modeState=9;
        androidAutoStage=0;
        showMonitor("СТОП");
        setAndroidAutoStatus("ТЭН выключен");
        toast("СТОП: ТЭН выключен");
    }

    private void setAndroidAutoStatus(String text) {
        if(androidAutoStatus!=null) androidAutoStatus.setText(text);
    }

    private boolean validAutoSensor(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && v>-40.0 && v<200.0;
    }

    private double parseTelemetryNumber(String s) {
        try { return Double.parseDouble(s.trim().replace(',','.')); }
        catch(Exception e){ return Double.NaN; }
    }

    private String fmt1(double v) {
        return String.format(Locale.US,"%.1f",v);
    }

    private String formatAutoTime(long ms) {
        long total=Math.max(0L,ms/1000L);
        return String.format(Locale.US,"%02d:%02d",total/60L,total%60L);
    }

    private String androidAutoStageName() {
        switch(androidAutoStage){
            case 1:return "drying";
            case 2:return "roasting";
            case 3:return "cooking";
            case 4:return "complete";
            case 5:return "aborted";
            default:return "off";
        }
    }

'''
if marker not in s:
    raise SystemExit('cameraClick marker not found')
s = s.replace(marker, auto_methods + marker, 1)

# MQTT telemetry: keep old "status" for Remote 1.0 compatibility and add explicit
# last_command + Android-auto state fields.
old = '''    private String json(String raw,String[] a){ return "{\\\"ts\\\":"+System.currentTimeMillis()+",\\\"raw\\\":\\\""+esc(raw)+"\\\",\\\"temp_ds\\\":\\\""+esc(get(a,1))+"\\\",\\\"temp_tip_k\\\":\\\""+esc(get(a,2))+"\\\",\\\"temp_k\\\":\\\""+esc(get(a,3))+"\\\",\\\"temp_p\\\":\\\""+esc(get(a,4))+"\\\",\\\"heater_power\\\":\\\""+esc(get(a,5))+"\\\",\\\"mode\\\":\\\""+esc(get(a,6))+"\\\",\\\"status\\\":\\\""+esc(get(a,7))+"\\\",\\\"kP\\\":\\\""+esc(get(a,8))+"\\\",\\\"kI\\\":\\\""+esc(get(a,9))+"\\\",\\\"kD\\\":\\\""+esc(get(a,10))+"\\\",\\\"zP\\\":\\\""+esc(get(a,11))+"\\\",\\\"temp_tip_t\\\":\\\""+esc(get(a,12))+"\\\"}"; }'''
new = '''    private String json(String raw,String[] a){ return "{\\\"ts\\\":"+System.currentTimeMillis()+",\\\"raw\\\":\\\""+esc(raw)+"\\\",\\\"temp_ds\\\":\\\""+esc(get(a,1))+"\\\",\\\"temp_tip_k\\\":\\\""+esc(get(a,2))+"\\\",\\\"temp_k\\\":\\\""+esc(get(a,3))+"\\\",\\\"temp_p\\\":\\\""+esc(get(a,4))+"\\\",\\\"heater_power\\\":\\\""+esc(get(a,5))+"\\\",\\\"mode\\\":\\\""+esc(get(a,6))+"\\\",\\\"status\\\":\\\""+esc(get(a,7))+"\\\",\\\"last_command\\\":\\\""+esc(get(a,7))+"\\\",\\\"kP\\\":\\\""+esc(get(a,8))+"\\\",\\\"kI\\\":\\\""+esc(get(a,9))+"\\\",\\\"kD\\\":\\\""+esc(get(a,10))+"\\\",\\\"zP\\\":\\\""+esc(get(a,11))+"\\\",\\\"temp_tip_t\\\":\\\""+esc(get(a,12))+"\\\",\\\"android_auto_running\\\":"+androidAutoRunning+",\\\"android_auto_stage\\\":\\\""+androidAutoStageName()+"\\\"}"; }'''
if old not in s:
    raise SystemExit('mapped json method not found')
s = s.replace(old,new,1)

# CSV terminology only; field position remains unchanged.
s = s.replace('timestamp;temp_ds;temp_tip_k;temp_k;temp_p;heater_power;mode;status;kP;kI;kD;zP;temp_tip_t;raw',
              'timestamp;temp_ds;temp_tip_k;temp_k;temp_p;heater_power;mode;last_command;kP;kI;kD;zP;temp_tip_t;raw')

# On app shutdown, do not leave an Android-hosted Auto heating unattended.
old = '''    @Override protected void onDestroy(){ saveSettings();closeBluetoothInternal();disconnectMqtt();super.onDestroy(); }'''
new = '''    @Override protected void onDestroy(){
        if(androidAutoRunning && isBtConnected()){
            sendBt("a3"+TERMINATOR);
            sendBt("x0"+TERMINATOR);
        }
        androidAutoRunning=false;
        saveSettings();closeBluetoothInternal();disconnectMqtt();super.onDestroy();
    }'''
if old not in s:
    raise SystemExit('onDestroy block not found')
s = s.replace(old,new,1)

main.write_text(s, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 6", "versionCode 7")
g = g.replace("versionName '2.1.0'", "versionName '2.2.0'")
gradle.write_text(g, encoding='utf-8')
