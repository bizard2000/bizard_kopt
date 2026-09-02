package com.bizard.homesmokemqtt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bizard.homesmokecore.AutoEngine;
import com.bizard.homesmokecore.AutoProgram;
import com.bizard.homesmokecore.AutoStage;
import com.bizard.homesmokecore.Telemetry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** HomeSmoke 2.6 presentation layer. Long-running control lives in HomeSmokeService. */
public class MainActivity extends Activity implements HomeSmokeService.Listener {
    private static final int BLUE=Color.rgb(7,92,170), BG=Color.rgb(239,241,244), GREEN=Color.rgb(33,145,75), RED=Color.rgb(185,45,45), ORANGE=Color.rgb(220,125,20);
    private static final int REQ_BT=1001,REQ_NOTIFICATIONS=1002;

    private HomeSmokeService service;
    private boolean bound;
    private HomeSmokeService.State state;
    private ProgramRepository programRepo;
    private List<AutoProgram> programs=new ArrayList<>();
    private SharedPreferences prefs;

    private LinearLayout pageHost;
    private TextView title,btBadge,mqttBadge,cameraValue,kValue,tValue,setpointValue,powerValue,modeValue,lastCommandValue,autoValue,errorValue;
    private Button menuButton,backButton;
    private PopupWindow drawer;
    private int insetTop,insetBottom;

    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){service=((HomeSmokeService.LocalBinder)binder).getService();bound=true;service.setListener(MainActivity.this);state=service.getState();showDashboard();autoConnectMqtt();}
        @Override public void onServiceDisconnected(ComponentName name){bound=false;service=null;renderState(null);}
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);prefs=getSharedPreferences("homesmoke_full",MODE_PRIVATE);programRepo=new ProgramRepository(this);programs=programRepo.load();
        View root=buildRoot();setContentView(root);applyInsets(root);requestPermissionsIfNeeded();
        bindService(new Intent(this,HomeSmokeService.class),connection,BIND_AUTO_CREATE);
    }
    @Override protected void onDestroy(){if(bound){service.setListener(null);unbindService(connection);bound=false;}super.onDestroy();}

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(56)));pageHost=new LinearLayout(this);pageHost.setOrientation(LinearLayout.VERTICAL);root.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));return root;
    }
    private View buildBar(){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(6),0,dp(8),0);bar.setBackgroundColor(BLUE);
        backButton=button("←");backButton.setTextSize(28);backButton.setTextColor(Color.WHITE);backButton.setVisibility(View.GONE);backButton.setOnClickListener(v->showDashboard());bar.addView(backButton,new LinearLayout.LayoutParams(dp(44),dp(44)));
        menuButton=button("☰");menuButton.setTextSize(27);menuButton.setTextColor(Color.WHITE);menuButton.setOnClickListener(v->showDrawer());bar.addView(menuButton,new LinearLayout.LayoutParams(dp(44),dp(44)));
        title=text("Домашняя коптильня",19,true);title.setTextColor(Color.WHITE);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        btBadge=badge("BT");mqttBadge=badge("MQTT");bar.addView(btBadge);bar.addView(mqttBadge);return bar;
    }

    private void showDashboard(){
        closeDrawer();setPageTitle("Домашняя коптильня",false);LinearLayout p=page();
        LinearLayout cam=card();cam.addView(center("ТЕМПЕРАТУРА КАМЕРЫ",14,true));cameraValue=center("— °C",52,true);cam.addView(cameraValue);setpointValue=center("Уставка: — °C",16,false);cam.addView(setpointValue);cam.setOnClickListener(v->cameraControl());p.addView(cam,margins(8,8,8,5));

        LinearLayout probes=new LinearLayout(this);probes.setOrientation(LinearLayout.HORIZONTAL);LinearLayout kc=smallCard("Щуп K");kValue=center("— °C",28,true);kc.addView(kValue);LinearLayout tc=smallCard("Щуп T");tValue=center("— °C",28,true);tc.addView(tValue);addHalf(probes,kc,8,4);addHalf(probes,tc,4,8);p.addView(probes);

        LinearLayout process=card();powerValue=info(process,"Мощность ТЭНа","— %");modeValue=info(process,"Режим Arduino","—");lastCommandValue=info(process,"Последняя команда","—");p.addView(process,margins(8,5,8,5));

        LinearLayout modes=new LinearLayout(this);modes.setOrientation(LinearLayout.HORIZONTAL);Button manual=modeButton("РУЧНОЙ",ORANGE),pid=modeButton("PID",GREEN),auto=modeButton("AUTO",BLUE),stop=modeButton("СТОП",RED);
        manual.setOnClickListener(v->{if(requireService())service.selectManual();});pid.setOnClickListener(v->{if(requireService())service.selectPid();});auto.setOnClickListener(v->showPrograms());stop.setOnClickListener(v->confirmStop());
        modes.addView(manual,weight());modes.addView(pid,weight());modes.addView(auto,weight());modes.addView(stop,weight());p.addView(modes,margins(8,5,8,5));

        LinearLayout ac=card();ac.addView(text("АВТО ПРОГРАММА",14,true));autoValue=text("Auto выключено",15,false);autoValue.setPadding(0,dp(7),0,dp(7));ac.addView(autoValue);Button stopAuto=button("Остановить Auto / ТЭН");stopAuto.setTextColor(Color.WHITE);stopAuto.setBackground(round(RED,12));stopAuto.setOnClickListener(v->confirmStop());ac.addView(stopAuto,new LinearLayout.LayoutParams(-1,dp(48)));p.addView(ac,margins(8,5,8,5));
        errorValue=text("",13,true);errorValue.setTextColor(RED);errorValue.setPadding(dp(12),dp(4),dp(12),dp(12));p.addView(errorValue);
        setPage(p);renderState(state);
    }

    private void showPrograms(){
        closeDrawer();setPageTitle("Авто программы",true);programs=programRepo.load();LinearLayout p=page();
        TextView intro=text("Программы выполняются на стороне Android. Arduino остаётся исполнительным PID-контроллером. Выдержка считается только когда температура камеры находится в заданном допуске.",13,false);intro.setPadding(dp(10),dp(8),dp(10),dp(10));p.addView(intro);
        Button add=primary("+ Новая программа");add.setOnClickListener(v->{AutoProgram x=ProgramRepository.defaultProgram();x.name="Новая программа "+(programs.size()+1);programs.add(x);programRepo.save(programs);editProgram(programs.size()-1);});p.addView(add,margins(8,2,8,8));
        for(int i=0;i<programs.size();i++){final int idx=i;AutoProgram pr=programs.get(i);LinearLayout c=card();c.addView(text(pr.name,20,true));TextView sum=text(programSummary(pr),13,false);sum.setPadding(0,dp(5),0,dp(8));c.addView(sum);LinearLayout row=new LinearLayout(this);Button run=smallAction("▶ Запустить",GREEN),edit=smallAction("Изменить",BLUE),copy=smallAction("Копия",ORANGE);run.setOnClickListener(v->startProgram(idx));edit.setOnClickListener(v->editProgram(idx));copy.setOnClickListener(v->{programs.add(pr.copy());programRepo.save(programs);showPrograms();});row.addView(run,weight());row.addView(edit,weight());row.addView(copy,weight());c.addView(row);p.addView(c,margins(8,4,8,5));}
        LinearLayout io=new LinearLayout(this);Button export=button("Экспорт JSON"),imp=button("Импорт JSON");export.setOnClickListener(v->exportPrograms());imp.setOnClickListener(v->importPrograms());io.addView(export,weight());io.addView(imp,weight());p.addView(io,margins(8,8,8,16));setPage(p);
    }

    private void editProgram(int index){
        if(index<0||index>=programs.size())return;AutoProgram pr=programs.get(index);setPageTitle("Редактор программы",true);LinearLayout p=page();
        EditText name=edit("Название программы",InputType.TYPE_CLASS_TEXT);name.setText(pr.name);p.addView(name);EditText desc=edit("Описание",InputType.TYPE_CLASS_TEXT);desc.setText(pr.description);p.addView(desc);
        final CheckBox[] enabled=new CheckBox[4],stop=new CheckBox[4];final EditText[] stageName=new EditText[4],target=new EditText[4],tol=new EditText[4],stable=new EditText[4],hold=new EditText[4],probe=new EditText[4];final Spinner[] cond=new Spinner[4],activation=new Spinner[4];
        String[] conditions={"Только время","Только щуп K","Только щуп T","Время ИЛИ щуп K","Время ИЛИ щуп T","Время И щуп K","Время И щуп T"};String[] activations={"Щуп активен сразу","Щуп после готовности камеры"};
        for(int i=0;i<4;i++){AutoStage s=pr.stages.get(i);LinearLayout c=card();enabled[i]=check("Этап "+(i+1)+" включён");enabled[i].setChecked(s.enabled);c.addView(enabled[i]);stageName[i]=edit("Название этапа",InputType.TYPE_CLASS_TEXT);stageName[i].setText(s.name);c.addView(stageName[i]);
            target[i]=numberEdit("Температура камеры, °C",s.chamberTarget);tol[i]=numberEdit("Допуск ±°C",s.tolerance);stable[i]=numberEdit("Стабилизация в диапазоне, секунд",s.stableSeconds);hold[i]=numberEdit("Выдержка, минут",s.holdMs/60000.0);c.addView(target[i]);c.addView(tol[i]);c.addView(stable[i]);c.addView(hold[i]);
            cond[i]=spinner(conditions);cond[i].setSelection(conditionIndex(s.finishCondition));c.addView(cond[i]);probe[i]=numberEdit("Температура щупа, °C",s.probeTarget);c.addView(probe[i]);activation[i]=spinner(activations);activation[i].setSelection(s.probeActivation==AutoStage.ProbeActivation.IMMEDIATE?0:1);c.addView(activation[i]);stop[i]=check("После этапа — СТОП");stop[i].setChecked(s.stopAfter);c.addView(stop[i]);p.addView(c,margins(8,6,8,6));}
        LinearLayout actions=new LinearLayout(this);Button save=smallAction("Сохранить",GREEN),run=smallAction("Сохранить и запустить",BLUE),del=smallAction("Удалить",RED);actions.addView(save,weight());actions.addView(run,weight());actions.addView(del,weight());p.addView(actions,margins(8,8,8,20));
        View.OnClickListener saver=v->{if(applyEditor(pr,name,desc,enabled,stop,stageName,target,tol,stable,hold,probe,cond,activation)){programRepo.save(programs);toast("Программа сохранена");if(v==run){startProgram(index);}else showPrograms();}};save.setOnClickListener(saver);run.setOnClickListener(saver);del.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Удалить программу?").setMessage(pr.name).setPositiveButton("Удалить",(d,w)->{if(programs.size()<=1){toast("Должна остаться хотя бы одна программа");return;}programs.remove(index);programRepo.save(programs);showPrograms();}).setNegativeButton("Отмена",null).show());setPage(p);
    }

    private boolean applyEditor(AutoProgram pr,EditText name,EditText desc,CheckBox[] enabled,CheckBox[] stop,EditText[] stageName,EditText[] target,EditText[] tol,EditText[] stable,EditText[] hold,EditText[] probe,Spinner[] cond,Spinner[] activation){
        try{String n=name.getText().toString().trim();if(n.isEmpty()){toast("Введите название программы");return false;}pr.name=n;pr.description=desc.getText().toString().trim();boolean any=false;
            for(int i=0;i<4;i++){AutoStage s=pr.stages.get(i);s.enabled=enabled[i].isChecked();any|=s.enabled;s.name=stageName[i].getText().toString().trim();if(s.name.isEmpty())s.name="Этап "+(i+1);s.chamberTarget=parse(target[i]);s.tolerance=parse(tol[i]);s.stableSeconds=(int)parse(stable[i]);s.holdMs=(long)(parse(hold[i])*60000.0);s.finishCondition=conditionAt(cond[i].getSelectedItemPosition());s.probeTarget=parseOptional(probe[i],0);s.probeActivation=activation[i].getSelectedItemPosition()==0?AutoStage.ProbeActivation.IMMEDIATE:AutoStage.ProbeActivation.AFTER_CHAMBER_READY;s.stopAfter=stop[i].isChecked();validateStage(s,i);}
            if(!any){toast("Включите хотя бы один этап");return false;}pr.modifiedAt=System.currentTimeMillis();return true;
        }catch(Exception e){toast("Ошибка программы: "+e.getMessage());return false;}
    }

    private void validateStage(AutoStage s,int i){if(!s.enabled)return;if(s.chamberTarget<0||s.chamberTarget>100)throw new IllegalArgumentException("этап "+(i+1)+": камера 0..100°C");if(s.tolerance<0||s.tolerance>10)throw new IllegalArgumentException("этап "+(i+1)+": допуск 0..10°C");if(s.stableSeconds<0||s.stableSeconds>3600)throw new IllegalArgumentException("этап "+(i+1)+": стабилизация 0..3600 сек");if(s.holdMs<0||s.holdMs>86400000L)throw new IllegalArgumentException("этап "+(i+1)+": выдержка до 24 ч");if((s.usesK()||s.usesT())&&(s.probeTarget<0||s.probeTarget>100))throw new IllegalArgumentException("этап "+(i+1)+": щуп 0..100°C");}

    private void startProgram(int idx){if(!requireService())return;if(idx<0||idx>=programs.size())return;if(service.startAuto(programs.get(idx)))showDashboard();else toast(service.getState().lastError);}

    private void showPid(){setPageTitle("Настройка PID",true);LinearLayout p=page();Telemetry t=state==null?null:state.telemetry;p.addView(text("Текущие: kP "+val(t==null?Double.NaN:t.kP)+"   kI "+val(t==null?Double.NaN:t.kI)+"   kD "+val(t==null?Double.NaN:t.kD)+"   zP "+val(t==null?Double.NaN:t.zP),16,true),margins(10,10,10,10));addPidRow(p,"kP","p");addPidRow(p,"kI","i");addPidRow(p,"kD","d");addPidRow(p,"zP","z");TextView note=text("Каждая кнопка меняет только свой коэффициент. Масштаб ×100 сохранён строго по исходному протоколу.",13,false);note.setPadding(dp(10),dp(12),dp(10),dp(12));p.addView(note);setPage(p);}
    private void addPidRow(LinearLayout p,String label,String prefix){LinearLayout r=card();r.addView(text(label,18,true));EditText e=edit("Значение",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);r.addView(e);Button b=primary("Применить "+label);b.setOnClickListener(v->{if(requireService()&&service.setPidCoefficient(prefix,e.getText().toString()))toast(label+" отправлен");else toast("Не удалось отправить "+label);});r.addView(b);p.addView(r,margins(8,4,8,4));}

    private void showSettings(){setPageTitle("Bluetooth и настройки",true);LinearLayout p=page();LinearLayout bt=card();bt.addView(text("Bluetooth",20,true));TextView st=text(state!=null&&state.bluetoothConnected?"Подключено: "+state.bluetoothName:"Не подключено",15,false);st.setPadding(0,dp(8),0,dp(8));bt.addView(st);Button choose=primary("Выбрать Bluetooth устройство");choose.setOnClickListener(v->chooseBluetooth());bt.addView(choose);Button off=button("Отключить Bluetooth");off.setOnClickListener(v->{if(requireService())service.disconnectBluetooth();});bt.addView(off);p.addView(bt,margins(8,8,8,5));CheckBox keep=check("Не выключать экран при открытом приложении");keep.setChecked(prefs.getBoolean("keep",false));keep.setOnCheckedChangeListener((b,c)->{prefs.edit().putBoolean("keep",c).apply();applyKeepScreen(c);});p.addView(keep,margins(8,8,8,8));setPage(p);}

    private void showMqtt(){setPageTitle("MQTT",true);LinearLayout p=page();p.addView(text("Device ID: "+(service==null?prefs.getString("device_id","—"):service.deviceId()),13,true),margins(10,8,10,8));EditText broker=edit("Broker / IP",InputType.TYPE_CLASS_TEXT),port=edit("Port",InputType.TYPE_CLASS_NUMBER),status=edit("Status topic",InputType.TYPE_CLASS_TEXT),cmd=edit("Command topic",InputType.TYPE_CLASS_TEXT),ack=edit("ACK topic",InputType.TYPE_CLASS_TEXT),user=edit("Логин",InputType.TYPE_CLASS_TEXT),pass=edit("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);broker.setText(prefs.getString("broker",""));port.setText(prefs.getString("port","1883"));status.setText(prefs.getString("topic","homesmoke/status"));cmd.setText(prefs.getString("cmd_topic","homesmoke/cmd"));ack.setText(prefs.getString("ack_topic","homesmoke/ack"));user.setText(prefs.getString("user",""));pass.setText(prefs.getString("pass",""));p.addView(broker);p.addView(port);p.addView(status);p.addView(cmd);p.addView(ack);p.addView(user);p.addView(pass);CheckBox tls=check("TLS");tls.setChecked(prefs.getBoolean("tls",false));CheckBox auto=check("Автоподключение MQTT");auto.setChecked(prefs.getBoolean("mqtt_auto",false));p.addView(tls);p.addView(auto);Button connect=primary("Сохранить и подключить");connect.setOnClickListener(v->{if(requireService()){service.configureMqtt(broker.getText().toString(),port.getText().toString(),status.getText().toString(),cmd.getText().toString(),ack.getText().toString(),user.getText().toString(),pass.getText().toString(),tls.isChecked(),auto.isChecked());service.startMqtt();toast("MQTT подключение запущено");}});p.addView(connect,margins(8,8,8,4));Button disconnect=button("Отключить MQTT");disconnect.setOnClickListener(v->{if(requireService())service.stopMqtt();});p.addView(disconnect,margins(8,2,8,10));TextView note=text("Команды Remote имеют ID и считаются выполненными только после подтверждения новой уставки в телеметрии Arduino. Команды старше 2 минут отклоняются.",13,false);note.setPadding(dp(10),dp(8),dp(10),dp(14));p.addView(note);setPage(p);}

    private void showDiagnostics(){setPageTitle("Диагностика",true);LinearLayout p=page();Telemetry t=state==null?null:state.telemetry;LinearLayout c=card();c.addView(text("Bluetooth: "+(state!=null&&state.bluetoothConnected?"OK":"нет"),15,true));c.addView(text("MQTT: "+(state==null?"—":state.mqttState),15,false));c.addView(text("Auto: "+(state==null?"—":state.autoState+" · "+state.autoStatus),15,false));if(t!=null){c.addView(text("Камера: "+val(t.chamber)+" °C",14,false));c.addView(text("K: "+val(t.probeK)+" °C   T: "+val(t.probeT)+" °C",14,false));c.addView(text("Уставка: "+val(t.chamberSetpoint)+" °C   ТЭН: "+val(t.heaterPower)+" %",14,false));c.addView(text("PID: P="+val(t.kP)+" I="+val(t.kI)+" D="+val(t.kD)+" z="+val(t.zP),14,false));TextView raw=text("RAW:\n"+t.raw,12,false);raw.setTypeface(Typeface.MONOSPACE);raw.setTextIsSelectable(true);raw.setPadding(0,dp(10),0,0);c.addView(raw);}p.addView(c,margins(8,8,8,8));setPage(p);}

    private void cameraControl(){if(!requireService())return;if(service.isAutoRunning()){toast("Уставкой управляет Auto программа");return;}Telemetry t=state==null?null:state.telemetry;if(t==null){toast("Нет данных Arduino");return;}if(t.mode==1)valueDialog("Уставка камеры 0..100 °C",v->service.setChamberSetpoint(v));else if(t.mode==0)valueDialog("Мощность ТЭНа 0..100 %",v->service.setManualPower(v));else toast("Выберите РУЧНОЙ или PID режим");}
    private interface ValueAction{boolean apply(double value);}private void valueDialog(String caption,ValueAction action){EditText e=numberEdit("0..100",0);new AlertDialog.Builder(this).setTitle(caption).setView(e).setPositiveButton("Применить",(d,w)->{try{double v=parse(e);if(v<0||v>100||!action.apply(v))toast("Не удалось применить значение");}catch(Exception ex){toast("Неверное значение");}}).setNegativeButton("Отмена",null).show();}
    private void confirmStop(){new AlertDialog.Builder(this).setTitle("СТОП").setMessage("Выключить ТЭН и остановить Auto?").setPositiveButton("СТОП",(d,w)->{if(requireService())service.stopHeating();}).setNegativeButton("Отмена",null).show();}

    private void chooseBluetooth(){if(!requireService())return;if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);return;}BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null){toast("Bluetooth не поддерживается");return;}if(!a.isEnabled()){toast("Включите Bluetooth");return;}Set<BluetoothDevice> set=a.getBondedDevices();if(set==null||set.isEmpty()){toast("Нет спаренных устройств");return;}List<BluetoothDevice> devices=new ArrayList<>(set);String[] names=new String[devices.size()];for(int i=0;i<devices.size();i++){BluetoothDevice d=devices.get(i);String n;try{n=d.getName();}catch(Exception e){n=null;}names[i]=(n==null?"Устройство":n)+"\n"+d.getAddress();}new AlertDialog.Builder(this).setTitle("Bluetooth устройства").setItems(names,(d,w)->service.connectBluetooth(devices.get(w).getAddress())).setNegativeButton("Отмена",null).show();}

    private void exportPrograms(){try{String json=programRepo.exportJson(programs);ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("HomeSmoke programs",json));EditText e=new EditText(this);e.setText(json);e.setTextIsSelectable(true);e.setMinLines(8);new AlertDialog.Builder(this).setTitle("Программы JSON — скопировано").setView(e).setPositiveButton("OK",null).show();}catch(Exception e){toast("Ошибка экспорта");}}
    private void importPrograms(){EditText e=new EditText(this);e.setHint("Вставьте JSON библиотеки программ");e.setMinLines(10);e.setGravity(Gravity.TOP);new AlertDialog.Builder(this).setTitle("Импорт программ").setView(e).setPositiveButton("Импорт",(d,w)->{try{programs=programRepo.importJson(e.getText().toString());showPrograms();toast("Импортировано программ: "+programs.size());}catch(Exception ex){toast("Неверный JSON");}}).setNegativeButton("Отмена",null).show();}

    private void showDrawer(){if(drawer!=null&&drawer.isShowing()){closeDrawer();return;}FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(90,0,0,0));overlay.setPadding(0,insetTop,0,insetBottom);int width=Math.min(dp(320),(int)(getResources().getDisplayMetrics().widthPixels*.86));LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setBackgroundColor(Color.WHITE);TextView head=text("Домашняя коптильня",21,true);head.setTextColor(Color.WHITE);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(20),0,dp(10),0);head.setBackgroundColor(BLUE);panel.addView(head,new LinearLayout.LayoutParams(-1,dp(72)));drawerItem(panel,"Монитор",this::showDashboard);drawerItem(panel,"Авто программы",this::showPrograms);drawerItem(panel,"Настройка PID",this::showPid);drawerItem(panel,"Bluetooth и настройки",this::showSettings);drawerItem(panel,"MQTT",this::showMqtt);drawerItem(panel,"Диагностика",this::showDiagnostics);drawerItem(panel,"СТОП / ТЭН выкл.",this::confirmStop);drawerItem(panel,"Выход",this::finish);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(width,-1,Gravity.END);overlay.addView(panel,pp);overlay.setOnClickListener(v->closeDrawer());panel.setOnClickListener(v->{});PopupWindow pop=new PopupWindow(overlay,-1,-1,true);pop.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));pop.setOutsideTouchable(true);pop.setOnDismissListener(()->{if(drawer==pop)drawer=null;});drawer=pop;pop.showAtLocation(menuButton,Gravity.TOP|Gravity.START,0,0);panel.post(()->{panel.setTranslationX(panel.getWidth());panel.animate().translationX(0).setDuration(180).start();});}
    private void drawerItem(LinearLayout panel,String label,Runnable action){TextView t=text(label,18,false);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(22),0,dp(10),0);t.setOnClickListener(v->{closeDrawer();action.run();});panel.addView(t,new LinearLayout.LayoutParams(-1,dp(56)));View line=new View(this);line.setBackgroundColor(Color.rgb(230,230,230));panel.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    private void closeDrawer(){if(drawer!=null&&drawer.isShowing())drawer.dismiss();drawer=null;}

    @Override public void onState(HomeSmokeService.State s){runOnUiThread(()->{state=s;renderState(s);});}
    private void renderState(HomeSmokeService.State s){if(btBadge!=null)setBadge(btBadge,s!=null&&s.bluetoothConnected);if(mqttBadge!=null)setBadge(mqttBadge,s!=null&&s.mqttConnected);if(cameraValue==null)return;Telemetry t=s==null?null:s.telemetry;cameraValue.setText(t==null?"— °C":val(t.chamber)+" °C");kValue.setText(t==null?"— °C":val(t.probeK)+" °C");tValue.setText(t==null?"— °C":val(t.probeT)+" °C");setpointValue.setText(t==null?"Уставка: — °C":"Уставка: "+val(t.chamberSetpoint)+" °C");powerValue.setText(t==null?"— %":val(t.heaterPower)+" %");modeValue.setText(t==null?"—":modeName(t.mode));lastCommandValue.setText(t==null?"—":t.lastCommand);autoValue.setText(s==null?"Auto выключено":s.autoStatus);errorValue.setText(s==null?"":s.lastError);}
    private void autoConnectMqtt(){if(service!=null&&prefs.getBoolean("mqtt_auto",false)&&!service.isMqttConnected())service.startMqtt();}

    private void requestPermissionsIfNeeded(){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);applyKeepScreen(prefs.getBoolean("keep",false));}
    private void applyInsets(View root){if(Build.VERSION.SDK_INT<21)return;root.setOnApplyWindowInsetsListener((v,i)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());l=x.left;t=x.top;r=x.right;b=x.bottom;}else{l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();}insetTop=t;insetBottom=b;v.setPadding(l,t,r,b);return i;});root.requestApplyInsets();if(Build.VERSION.SDK_INT>=21){getWindow().setStatusBarColor(BLUE);getWindow().setNavigationBarColor(BG);}}
    private void applyKeepScreen(boolean on){if(on)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}

    private void setPage(LinearLayout p){pageHost.removeAllViews();ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(p,new ScrollView.LayoutParams(-1,-2));pageHost.addView(s,new LinearLayout.LayoutParams(-1,-1));}
    private void setPageTitle(String s,boolean back){title.setText(s);backButton.setVisibility(back?View.VISIBLE:View.GONE);menuButton.setVisibility(back?View.GONE:View.VISIBLE);}
    private LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(4),dp(4),dp(4),dp(24));p.setBackgroundColor(BG);return p;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(round(Color.WHITE,14));return c;}
    private LinearLayout smallCard(String label){LinearLayout c=card();TextView l=center(label,15,false);l.setTextColor(Color.DKGRAY);c.addView(l);return c;}
    private void addHalf(LinearLayout row,View v,int left,int right){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(left),dp(4),dp(right),dp(4));row.addView(v,p);}
    private TextView info(LinearLayout parent,String label,String initial){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(5),0,dp(5));TextView a=text(label,15,false),b=text(initial,17,true);b.setGravity(Gravity.END);r.addView(a,new LinearLayout.LayoutParams(0,-2,1));r.addView(b);parent.addView(r);return b;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.rgb(25,25,25));if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView center(String s,int sp,boolean bold){TextView t=text(s,sp,bold);t.setGravity(Gravity.CENTER);return t;}
    private TextView badge(String s){TextView t=text(s,10,true);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);t.setPadding(dp(7),dp(4),dp(7),dp(4));setBadge(t,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(32));p.setMargins(dp(3),0,0,0);t.setLayoutParams(p);return t;}
    private void setBadge(TextView t,boolean on){t.setBackground(round(on?GREEN:Color.rgb(100,100,100),10));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private Button primary(String s){Button b=button(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setBackground(round(BLUE,10));b.setMinHeight(dp(50));return b;}
    private Button modeButton(String s,int color){Button b=button(s);b.setTextSize(13);b.setTextColor(Color.WHITE);b.setBackground(round(color,9));return b;}
    private Button smallAction(String s,int color){Button b=modeButton(s,color);b.setMinHeight(dp(48));return b;}
    private CheckBox check(String s){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(16);c.setTextColor(Color.BLACK);c.setPadding(dp(8),dp(5),dp(8),dp(5));return c;}
    private EditText edit(String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(16);e.setInputType(type);e.setSingleLine(true);e.setBackgroundColor(Color.WHITE);e.setPadding(dp(12),0,dp(12),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(dp(8),dp(4),dp(8),dp(4));e.setLayoutParams(p);return e;}
    private EditText numberEdit(String hint,double value){EditText e=edit(hint,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);if(value!=0)e.setText(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());return e;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setPadding(dp(8),dp(4),dp(8),dp(4));return s;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private boolean requireService(){if(service==null){toast("Сервис HomeSmoke ещё запускается");return false;}return true;}
    private static double parse(EditText e){return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}
    private static double parseOptional(EditText e,double d){String s=e.getText().toString().trim();return s.isEmpty()?d:Double.parseDouble(s.replace(',','.'));}
    private static String val(double v){if(Double.isNaN(v)||Double.isInfinite(v))return "—";return String.format(Locale.US,Math.abs(v-Math.rint(v))<.005?"%.0f":"%.2f",v);}
    private static String modeName(int mode){switch(mode){case 0:return "Ручной";case 1:return "PID";case 3:return "СТОП";default:return String.valueOf(mode);}}
    private static int conditionIndex(AutoStage.FinishCondition c){switch(c){case PROBE_K:return 1;case PROBE_T:return 2;case TIME_OR_K:return 3;case TIME_OR_T:return 4;case TIME_AND_K:return 5;case TIME_AND_T:return 6;default:return 0;}}
    private static AutoStage.FinishCondition conditionAt(int i){switch(i){case 1:return AutoStage.FinishCondition.PROBE_K;case 2:return AutoStage.FinishCondition.PROBE_T;case 3:return AutoStage.FinishCondition.TIME_OR_K;case 4:return AutoStage.FinishCondition.TIME_OR_T;case 5:return AutoStage.FinishCondition.TIME_AND_K;case 6:return AutoStage.FinishCondition.TIME_AND_T;default:return AutoStage.FinishCondition.TIME;}}
    private static String programSummary(AutoProgram p){StringBuilder b=new StringBuilder();int n=0;for(AutoStage s:p.stages)if(s.enabled){if(n++>0)b.append("  →  ");b.append(s.name).append(" ").append(val(s.chamberTarget)).append("°C");if(s.usesTime())b.append("/").append(val(s.holdMs/60000.0)).append("м");}return n==0?"Нет активных этапов":b.toString();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onBackPressed(){if(drawer!=null&&drawer.isShowing()){closeDrawer();return;}if(backButton!=null&&backButton.getVisibility()==View.VISIBLE){showDashboard();return;}super.onBackPressed();}
}
