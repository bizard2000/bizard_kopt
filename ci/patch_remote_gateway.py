from pathlib import Path

main = Path('app/src/main/java/com/bizard/homesmokemqtt/MainActivity.java')
s = main.read_text(encoding='utf-8')

# JSON parser for the small app-level MQTT command protocol.
if 'import org.json.JSONObject;' not in s:
    s = s.replace('import java.util.UUID;\n', 'import java.util.UUID;\n\nimport org.json.JSONObject;\n', 1)

old = '    private EditText broker, port, topic, user, pass;\n'
new = '    private EditText broker, port, topic, commandTopic, ackTopic, user, pass;\n'
if old not in s:
    raise SystemExit('MQTT field declaration not found')
s = s.replace(old, new, 1)

old = '''        broker=edit("Broker / IP",InputType.TYPE_CLASS_TEXT); port=edit("Port",InputType.TYPE_CLASS_NUMBER);
        topic=edit("Topic",InputType.TYPE_CLASS_TEXT); user=edit("Логин",InputType.TYPE_CLASS_TEXT);
        pass=edit("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(broker); root.addView(port); root.addView(topic); root.addView(user); root.addView(pass);'''
new = '''        broker=edit("Broker / IP",InputType.TYPE_CLASS_TEXT); port=edit("Port",InputType.TYPE_CLASS_NUMBER);
        topic=edit("Status topic",InputType.TYPE_CLASS_TEXT);
        commandTopic=edit("Command topic",InputType.TYPE_CLASS_TEXT);
        ackTopic=edit("ACK topic",InputType.TYPE_CLASS_TEXT);
        user=edit("Логин",InputType.TYPE_CLASS_TEXT);
        pass=edit("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(broker); root.addView(port); root.addView(topic); root.addView(commandTopic); root.addView(ackTopic); root.addView(user); root.addView(pass);'''
if old not in s:
    raise SystemExit('buildMqtt fields block not found')
s = s.replace(old, new, 1)

old = '        TextView n=txt("Каждый полный Bluetooth-пакет автоматически публикуется в MQTT как JSON.",13,false); n.setPadding(dp(10),dp(12),dp(10),dp(12)); root.addView(n);\n'
new = '        TextView n=txt("Телеметрия публикуется в Status topic. Удалённая уставка принимается из Command topic и передаётся контроллеру только как подтверждённая команда k<значение>\\\\0.",13,false); n.setPadding(dp(10),dp(12),dp(10),dp(12)); root.addView(n);\n'
if old not in s:
    raise SystemExit('MQTT note block not found')
s = s.replace(old, new, 1)

old = '''        broker.setText(prefs.getString("broker","")); port.setText(prefs.getString("port","1883")); topic.setText(prefs.getString("topic","homesmoke/status"));
        user.setText(prefs.getString("user","")); pass.setText(prefs.getString("pass","")); tls.setChecked(prefs.getBoolean("tls",false)); retain.setChecked(prefs.getBoolean("retain",true));'''
new = '''        broker.setText(prefs.getString("broker","")); port.setText(prefs.getString("port","1883")); topic.setText(prefs.getString("topic","homesmoke/status"));
        commandTopic.setText(prefs.getString("cmd_topic","homesmoke/cmd")); ackTopic.setText(prefs.getString("ack_topic","homesmoke/ack"));
        user.setText(prefs.getString("user","")); pass.setText(prefs.getString("pass","")); tls.setChecked(prefs.getBoolean("tls",false)); retain.setChecked(prefs.getBoolean("retain",true));'''
if old not in s:
    raise SystemExit('loadSettings MQTT block not found')
s = s.replace(old, new, 1)

old = '''                .putString("port",port.getText().toString().trim()).putString("topic",topic.getText().toString().trim()).putString("user",user.getText().toString())
                .putString("pass",pass.getText().toString()).putBoolean("tls",tls.isChecked()).putBoolean("retain",retain.isChecked()).apply();'''
new = '''                .putString("port",port.getText().toString().trim()).putString("topic",topic.getText().toString().trim())
                .putString("cmd_topic",commandTopic.getText().toString().trim()).putString("ack_topic",ackTopic.getText().toString().trim())
                .putString("user",user.getText().toString()).putString("pass",pass.getText().toString()).putBoolean("tls",tls.isChecked()).putBoolean("retain",retain.isChecked()).apply();'''
if old not in s:
    raise SystemExit('saveSettings MQTT block not found')
s = s.replace(old, new, 1)

old = '''    private void connectMqtt(){
        saveSettings(); String host=broker.getText().toString().trim(); if(host.isEmpty()){toast("Укажите MQTT broker");return;} int p;
        try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){toast("Неверный MQTT port");return;}
        disconnectMqtt(); mqttStatus.setText("MQTT: подключение…"); final MqttClient c=new MqttClient(host,p,tls.isChecked(),user.getText().toString(),pass.getText().toString()); mqtt=c;
        new Thread(()->{try{c.connect();runOnUiThread(()->mqttStatus.setText("MQTT: подключен к "+host+":"+p));}catch(Exception e){c.close();if(mqtt==c)mqtt=null;runOnUiThread(()->mqttStatus.setText("MQTT: ошибка — "+e.getMessage()));}},"HomeSmoke-MQTT-connect").start();
    }
'''
new = '''    private void connectMqtt(){
        saveSettings(); String host=broker.getText().toString().trim(); if(host.isEmpty()){toast("Укажите MQTT broker");return;} int p;
        try{p=Integer.parseInt(port.getText().toString().trim());}catch(Exception e){toast("Неверный MQTT port");return;}
        final String cmd=commandTopic.getText().toString().trim().isEmpty()?"homesmoke/cmd":commandTopic.getText().toString().trim();
        disconnectMqtt(); mqttStatus.setText("MQTT: подключение…"); final MqttClient c=new MqttClient(host,p,tls.isChecked(),user.getText().toString(),pass.getText().toString()); mqtt=c;
        c.setMessageListener(this::handleMqttCommand);
        new Thread(()->{try{c.connect();c.subscribe(cmd);runOnUiThread(()->mqttStatus.setText("MQTT: подключен, команды: "+cmd));}catch(Exception e){c.close();if(mqtt==c)mqtt=null;runOnUiThread(()->mqttStatus.setText("MQTT: ошибка — "+e.getMessage()));}},"HomeSmoke-MQTT-connect").start();
    }

    private void handleMqttCommand(String incomingTopic,String payload){
        String expected=prefs.getString("cmd_topic","homesmoke/cmd").trim();
        if(expected.isEmpty()) expected="homesmoke/cmd";
        if(!expected.equals(incomingTopic)) return;
        try{
            JSONObject o=new JSONObject(payload);
            if(!"set_temp".equals(o.optString("cmd",""))) return;
            double value=o.getDouble("value");
            if(value<0||value>100){ publishRemoteAck(value,false,"out_of_range"); return; }
            String normalized=BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            runOnUiThread(()->{
                if(!isBtConnected()){
                    publishRemoteAck(value,false,"bluetooth_not_connected");
                    toast("MQTT: команда получена, но Bluetooth не подключен");
                    return;
                }
                boolean ok=sendBt("k"+normalized+TERMINATOR);
                publishRemoteAck(value,ok,ok?"sent_to_controller":"bluetooth_send_error");
                if(ok) toast("MQTT: уставка "+normalized+" °C отправлена");
            });
        }catch(Exception e){
            publishRemoteAck(Double.NaN,false,"bad_command");
        }
    }

    private void publishRemoteAck(double value,boolean ok,String message){
        MqttClient m=mqtt; if(m==null||!m.isConnected()) return;
        try{
            JSONObject a=new JSONObject();
            a.put("ts",System.currentTimeMillis()); a.put("cmd","set_temp");
            if(!Double.isNaN(value)) a.put("value",value);
            a.put("ok",ok); a.put("message",message);
            String t=prefs.getString("ack_topic","homesmoke/ack").trim(); if(t.isEmpty())t="homesmoke/ack";
            m.publish(t,a.toString(),false);
        }catch(Exception ignored){}
    }
'''
if old not in s:
    raise SystemExit('connectMqtt block not found')
s = s.replace(old, new, 1)

main.write_text(s, encoding='utf-8')

# Gateway build number, applied after the 2.0.3 protocol-mapping patch.
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = g.replace("versionCode 5", "versionCode 6")
g = g.replace("versionName '2.0.3'", "versionName '2.1.0'")
gradle.write_text(g, encoding='utf-8')
