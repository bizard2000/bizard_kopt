package com.bizard.homesmokemqtt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends Activity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQ_BT = 1001;

    private final StringBuilder btBuffer = new StringBuilder();
    private BluetoothSocket btSocket;
    private Thread btThread;
    private MqttClient mqtt;

    private TextView btStatus, mqttStatus;
    private final TextView[] values = new TextView[14];
    private EditText broker, port, topic, user, pass;
    private CheckBox tls, retain;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("homesmoke", MODE_PRIVATE);
        setContentView(buildUi());
        loadPrefs();
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(root);

        TextView title = text("HomeSmoke MQTT", 24, true);
        root.addView(title);

        btStatus = text("Bluetooth: отключен", 15, true);
        root.addView(btStatus);
        Button btBtn = button("Подключить Bluetooth");
        btBtn.setOnClickListener(v -> chooseBluetooth());
        root.addView(btBtn);

        root.addView(section("Данные контроллера"));
        String[] names = {"", "", "Temp DS", "Temp tip K", "Temp K", "Temp P", "Мощность", "Режим", "Статус", "kP", "kI", "kD", "zP", "Temp tip T"};
        for (int i = 2; i <= 13; i++) {
            values[i] = text(names[i] + ": —", 15, false);
            values[i].setPadding(0, dp(3), 0, dp(3));
            root.addView(values[i]);
        }

        root.addView(section("MQTT"));
        broker = edit("Broker / IP", InputType.TYPE_CLASS_TEXT);
        port = edit("Port", InputType.TYPE_CLASS_NUMBER);
        topic = edit("Topic", InputType.TYPE_CLASS_TEXT);
        user = edit("Login", InputType.TYPE_CLASS_TEXT);
        pass = edit("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(broker); root.addView(port); root.addView(topic); root.addView(user); root.addView(pass);

        LinearLayout flags = new LinearLayout(this);
        flags.setOrientation(LinearLayout.HORIZONTAL);
        tls = new CheckBox(this); tls.setText("TLS");
        retain = new CheckBox(this); retain.setText("Retain");
        flags.addView(tls); flags.addView(retain);
        root.addView(flags);

        mqttStatus = text("MQTT: отключен", 15, true);
        root.addView(mqttStatus);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button mqttConnect = button("MQTT подключить");
        Button mqttDisconnect = button("Отключить");
        mqttConnect.setOnClickListener(v -> connectMqtt());
        mqttDisconnect.setOnClickListener(v -> disconnectMqtt());
        buttons.addView(mqttConnect, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(mqttDisconnect, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);

        TextView note = text("Каждый полный Bluetooth-кадр автоматически публикуется в MQTT как JSON. Формат кадра: поля через |, окончание end.", 12, false);
        note.setPadding(0, dp(10), 0, 0);
        root.addView(note);
        return scroll;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }

    private EditText edit(String hint, int type) {
        EditText e = new EditText(this); e.setHint(hint); e.setInputType(type); return e;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private void loadPrefs() {
        broker.setText(prefs.getString("broker", ""));
        port.setText(prefs.getString("port", "1883"));
        topic.setText(prefs.getString("topic", "homesmoke/status"));
        user.setText(prefs.getString("user", ""));
        pass.setText(prefs.getString("pass", ""));
        tls.setChecked(prefs.getBoolean("tls", false));
        retain.setChecked(prefs.getBoolean("retain", true));
    }

    private void savePrefs() {
        prefs.edit()
                .putString("broker", broker.getText().toString().trim())
                .putString("port", port.getText().toString().trim())
                .putString("topic", topic.getText().toString().trim())
                .putString("user", user.getText().toString())
                .putString("pass", pass.getText().toString())
                .putBoolean("tls", tls.isChecked())
                .putBoolean("retain", retain.isChecked())
                .apply();
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void chooseBluetooth() {
        if (!hasBtPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { toast("Bluetooth не поддерживается"); return; }
        if (!adapter.isEnabled()) { toast("Включите Bluetooth"); return; }
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) { toast("Нет спаренных Bluetooth-устройств"); return; }
        ArrayList<BluetoothDevice> devices = new ArrayList<>(bonded);
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice d = devices.get(i);
            names[i] = (d.getName() == null ? "Без имени" : d.getName()) + "\n" + d.getAddress();
        }
        new AlertDialog.Builder(this).setTitle("Bluetooth устройство")
                .setItems(names, (dialog, which) -> connectBluetooth(devices.get(which)))
                .setNegativeButton("Отмена", null).show();
    }

    private void connectBluetooth(BluetoothDevice device) {
        closeBluetooth();
        setBtStatus("Bluetooth: подключение к " + safeName(device));
        btThread = new Thread(() -> {
            try {
                if (!hasBtPermission()) throw new SecurityException("Нет разрешения Bluetooth");
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                btSocket = socket;
                socket.connect();
                runOnUiThread(() -> setBtStatus("Bluetooth: подключен — " + safeName(device)));
                readBluetooth(socket.getInputStream());
            } catch (Exception e) {
                runOnUiThread(() -> setBtStatus("Bluetooth: ошибка — " + e.getMessage()));
                closeBluetooth();
            }
        }, "HomeSmoke-Bluetooth");
        btThread.start();
    }

    private String safeName(BluetoothDevice d) {
        try { return d.getName() == null ? d.getAddress() : d.getName(); }
        catch (SecurityException e) { return "устройство"; }
    }

    private void readBluetooth(InputStream in) throws IOException {
        byte[] buf = new byte[512];
        while (!Thread.currentThread().isInterrupted()) {
            int n = in.read(buf);
            if (n < 0) throw new EOFException("соединение закрыто");
            if (n == 0) continue;
            String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
            synchronized (btBuffer) {
                btBuffer.append(chunk);
                extractFrames();
            }
        }
    }

    private void extractFrames() {
        while (true) {
            int end = btBuffer.indexOf("end");
            if (end < 0) {
                if (btBuffer.length() > 8192) btBuffer.delete(0, btBuffer.length() - 4096);
                return;
            }
            String frame = btBuffer.substring(0, end + 3);
            btBuffer.delete(0, end + 3);
            while (btBuffer.length() > 0 && (btBuffer.charAt(0) == '\r' || btBuffer.charAt(0) == '\n')) btBuffer.deleteCharAt(0);
            processFrame(frame);
        }
    }

    private void processFrame(String frame) {
        String[] a = frame.split("\\|", -1);
        runOnUiThread(() -> {
            String[] names = {"", "", "Temp DS", "Temp tip K", "Temp K", "Temp P", "Мощность", "Режим", "Статус", "kP", "kI", "kD", "zP", "Temp tip T"};
            for (int i = 2; i <= 13; i++) values[i].setText(names[i] + ": " + get(a, i));
        });
        MqttClient m = mqtt;
        if (m != null && m.isConnected()) {
            try { m.publish(topic.getText().toString().trim(), json(frame, a), retain.isChecked()); }
            catch (Exception e) { runOnUiThread(() -> setMqttStatus("MQTT: ошибка публикации — " + e.getMessage())); }
        }
    }

    private String get(String[] a, int i) { return i >= 0 && i < a.length ? a[i].trim() : ""; }

    private String json(String raw, String[] a) {
        return "{" +
                "\"raw\":\"" + esc(raw) + "\"," +
                "\"temp_ds\":\"" + esc(get(a,2)) + "\"," +
                "\"temp_tip_k\":\"" + esc(get(a,3)) + "\"," +
                "\"temp_k\":\"" + esc(get(a,4)) + "\"," +
                "\"temp_p\":\"" + esc(get(a,5)) + "\"," +
                "\"heater_power\":\"" + esc(get(a,6)) + "\"," +
                "\"mode\":\"" + esc(get(a,7)) + "\"," +
                "\"status\":\"" + esc(get(a,8)) + "\"," +
                "\"kP\":\"" + esc(get(a,9)) + "\"," +
                "\"kI\":\"" + esc(get(a,10)) + "\"," +
                "\"kD\":\"" + esc(get(a,11)) + "\"," +
                "\"zP\":\"" + esc(get(a,12)) + "\"," +
                "\"temp_tip_t\":\"" + esc(get(a,13)) + "\"}";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private void connectMqtt() {
        savePrefs();
        String host = broker.getText().toString().trim();
        if (host.isEmpty()) { toast("Укажите MQTT broker"); return; }
        int p;
        try { p = Integer.parseInt(port.getText().toString().trim()); }
        catch (Exception e) { toast("Неверный MQTT port"); return; }
        disconnectMqtt();
        setMqttStatus("MQTT: подключение…");
        final MqttClient client = new MqttClient(host, p, tls.isChecked(), user.getText().toString(), pass.getText().toString());
        mqtt = client;
        new Thread(() -> {
            try {
                client.connect();
                runOnUiThread(() -> setMqttStatus("MQTT: подключен к " + host + ":" + p));
            } catch (Exception e) {
                client.close();
                if (mqtt == client) mqtt = null;
                runOnUiThread(() -> setMqttStatus("MQTT: ошибка — " + e.getMessage()));
            }
        }, "HomeSmoke-MQTT-connect").start();
    }

    private void disconnectMqtt() {
        MqttClient m = mqtt; mqtt = null;
        if (m != null) m.close();
        if (mqttStatus != null) setMqttStatus("MQTT: отключен");
    }

    private void setBtStatus(String s) { btStatus.setText(s); }
    private void setMqttStatus(String s) { mqttStatus.setText(s); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void closeBluetooth() {
        BluetoothSocket s = btSocket; btSocket = null;
        if (s != null) try { s.close(); } catch (Exception ignored) {}
        Thread t = btThread; btThread = null;
        if (t != null && t != Thread.currentThread()) t.interrupt();
    }

    @Override protected void onDestroy() {
        closeBluetooth(); disconnectMqtt(); super.onDestroy();
    }

    static class MqttClient {
        private final String host, username, password;
        private final int port;
        private final boolean tls;
        private Socket socket;
        private InputStream in;
        private OutputStream out;
        private volatile boolean connected;
        private Thread keepAlive;
        private final AtomicInteger packetId = new AtomicInteger(1);

        MqttClient(String host, int port, boolean tls, String username, String password) {
            this.host = host; this.port = port; this.tls = tls; this.username = username; this.password = password;
        }

        synchronized void connect() throws IOException {
            if (tls) socket = SSLSocketFactory.getDefault().createSocket();
            else socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 10000);
            socket.setSoTimeout(15000);
            in = socket.getInputStream(); out = socket.getOutputStream();

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeUtf(body, "MQTT"); body.write(4);
            int flags = 0x02;
            if (!username.isEmpty()) flags |= 0x80;
            if (!password.isEmpty()) flags |= 0x40;
            body.write(flags); body.write(0); body.write(30);
            writeUtf(body, "HomeSmoke_" + Long.toHexString(System.nanoTime()));
            if (!username.isEmpty()) writeUtf(body, username);
            if (!password.isEmpty()) writeUtf(body, password);
            sendPacket(0x10, body.toByteArray());

            int header = in.read();
            if (header != 0x20) throw new IOException("нет CONNACK");
            int remaining = readRemaining(in);
            byte[] ack = readFully(in, remaining);
            if (ack.length < 2 || ack[1] != 0) throw new IOException("CONNACK=" + (ack.length > 1 ? ack[1] & 255 : -1));
            socket.setSoTimeout(0);
            connected = true;
            keepAlive = new Thread(() -> {
                while (connected) {
                    try { Thread.sleep(20000); synchronized (MqttClient.this) { if (connected) sendPacket(0xC0, new byte[0]); } }
                    catch (Exception e) { close(); break; }
                }
            }, "HomeSmoke-MQTT-keepalive");
            keepAlive.start();
        }

        boolean isConnected() { return connected && socket != null && socket.isConnected() && !socket.isClosed(); }

        synchronized void publish(String topic, String payload, boolean retain) throws IOException {
            if (!isConnected()) throw new IOException("не подключен");
            if (topic == null || topic.trim().isEmpty()) topic = "homesmoke/status";
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeUtf(body, topic);
            int id = packetId.getAndUpdate(v -> v >= 65535 ? 1 : v + 1);
            body.write((id >>> 8) & 255); body.write(id & 255);
            body.write(payload.getBytes(StandardCharsets.UTF_8));
            sendPacket(0x32 | (retain ? 1 : 0), body.toByteArray());
        }

        private synchronized void sendPacket(int header, byte[] body) throws IOException {
            if (out == null) throw new IOException("socket закрыт");
            out.write(header); writeRemaining(out, body.length); out.write(body); out.flush();
        }

        synchronized void close() {
            connected = false;
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
            socket = null; in = null; out = null;
            if (keepAlive != null && keepAlive != Thread.currentThread()) keepAlive.interrupt();
            keepAlive = null;
        }

        private static void writeUtf(ByteArrayOutputStream out, String s) throws IOException {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            out.write((b.length >>> 8) & 255); out.write(b.length & 255); out.write(b);
        }

        private static void writeRemaining(OutputStream out, int n) throws IOException {
            do { int d = n % 128; n /= 128; if (n > 0) d |= 0x80; out.write(d); } while (n > 0);
        }

        private static int readRemaining(InputStream in) throws IOException {
            int multiplier = 1, value = 0, digit;
            do {
                digit = in.read(); if (digit < 0) throw new EOFException();
                value += (digit & 127) * multiplier; multiplier *= 128;
                if (multiplier > 128 * 128 * 128 * 128) throw new IOException("bad remaining length");
            } while ((digit & 128) != 0);
            return value;
        }

        private static byte[] readFully(InputStream in, int n) throws IOException {
            byte[] b = new byte[n]; int p = 0;
            while (p < n) { int r = in.read(b, p, n - p); if (r < 0) throw new EOFException(); p += r; }
            return b;
        }
    }
}
