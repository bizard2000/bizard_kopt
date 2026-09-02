package com.bizard.homesmokemqtt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bizard.homesmokecore.AutoEngine;
import com.bizard.homesmokecore.AutoProgram;
import com.bizard.homesmokecore.AutoStage;
import com.bizard.homesmokecore.Telemetry;

import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** HomeSmoke 2.6 UI. Bluetooth, MQTT and Auto run in HomeSmokeService. */
public class MainActivity extends Activity implements HomeSmokeService.Listener {
    private static final int BLUE=Color.rgb(11,103,178);
    private static final int BLUE_DARK=Color.rgb(8,77,135);
    private static final int BG=Color.rgb(243,246,249);
    private static final int SURFACE_ALT=Color.rgb(234,239,245);
    private static final int BORDER=Color.rgb(215,224,233);
    private static final int TEXT=Color.rgb(31,41,55);
    private static final int MUTED=Color.rgb(103,116,137);
    private static final int GREEN=Color.rgb(46,125,50);
    private static final int RED=Color.rgb(198,40,40);
    private static final int ORANGE=Color.rgb(239,108,0);
    private static final int REQ_BT=1001,REQ_NOTIFICATIONS=1002;

    private HomeSmokeService service;
    private boolean bound;
    private HomeSmokeService.State state;
    private SharedPreferences prefs;
    private ProgramRepository programRepo;
    private HistoryStore historyStore;
    private List<AutoProgram> programs=new ArrayList<>();

    private LinearLayout pageHost;
    private TextView title,subtitle,btBadge,mqttBadge;
    private TextView cameraValue,setpointValue,kValue,kTargetValue,tValue,tTargetValue,powerValue,errorValue;
    private TextView autoValue,autoStageValue,autoChamberValue,autoProbeValue,autoStabilizationValue,autoHoldValue,autoProgressCaption;
    private ProgressBar heaterProgress,autoProgressBar;
    private Button manualModeButton,pidModeButton,autoModeButton,cameraActionButton,autoChooseButton,autoStopButton;
    private ImageButton menuButton,backButton;
    private PopupWindow drawer;
    private int insetTop,insetBottom;

    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            service=((HomeSmokeService.LocalBinder)b).getService();
            bound=true;
            service.setListener(MainActivity.this);
            state=service.getState();
            showDashboard();
            autoConnectMqtt();
        }
        @Override public void onServiceDisconnected(ComponentName n){
            bound=false;
            service=null;
            renderState(null);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("homesmoke_full",MODE_PRIVATE);
        programRepo=new ProgramRepository(this);
        historyStore=new HistoryStore(this);
        programs=programRepo.load();
        View root=buildRoot();
        setContentView(root);
        applyInsets(root);
        requestPermissionsIfNeeded();
        bindService(new Intent(this,HomeSmokeService.class),connection,BIND_AUTO_CREATE);
    }

    @Override protected void onDestroy(){
        if(bound){
            service.setListener(null);
            unbindService(connection);
            bound=false;
        }
        super.onDestroy();
    }

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(buildBar(),new LinearLayout.LayoutParams(-1,dp(64)));
        pageHost=new LinearLayout(this);
        pageHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(pageHost,new LinearLayout.LayoutParams(-1,0,1));
        return root;
    }

    private View buildBar(){
        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4),0,dp(8),0);
        bar.setBackgroundColor(BLUE);
        bar.setElevation(dp(4));

        backButton=iconButton(R.drawable.ic_arrow_back_24);
        backButton.setVisibility(View.GONE);
        backButton.setContentDescription("Назад");
        backButton.setOnClickListener(v->showDashboard());
        bar.addView(backButton,new LinearLayout.LayoutParams(dp(48),dp(48)));

        menuButton=iconButton(R.drawable.ic_menu_24);
        menuButton.setContentDescription("Меню");
        menuButton.setOnClickListener(v->showDrawer());
        bar.addView(menuButton,new LinearLayout.LayoutParams(dp(48),dp(48)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_VERTICAL);
        title=text("HomeSmoke",18,true);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        subtitle=text("Домашняя коптильня",11,false);
        subtitle.setTextColor(Color.rgb(220,235,248));
        subtitle.setSingleLine(true);
        titles.addView(title);
        titles.addView(subtitle);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);
        tp.setMargins(dp(2),0,dp(5),0);
        bar.addView(titles,tp);

        btBadge=badge("BT");
        mqttBadge=badge("MQTT");
        bar.addView(btBadge);
        bar.addView(mqttBadge);
        return bar;
    }

    private void showDashboard(){
        closeDrawer();
        setPageTitle("HomeSmoke",false);
        programs=programRepo.load();
        LinearLayout p=page();

        LinearLayout camera=card();
        TextView cameraLabel=text("Камера",18,true);
        cameraLabel.setTextColor(MUTED);
        camera.addView(cameraLabel);
        cameraValue=text("— °C",56,true);
        cameraValue.setGravity(Gravity.CENTER_HORIZONTAL);
        cameraValue.setTextColor(TEXT);
        cameraValue.setPadding(0,dp(4),0,0);
        camera.addView(cameraValue);
        setpointValue=text("Уставка — °C",18,true);
        setpointValue.setGravity(Gravity.CENTER_HORIZONTAL);
        setpointValue.setTextColor(BLUE_DARK);
        camera.addView(setpointValue);
        cameraActionButton=primary("Задать температуру");
        cameraActionButton.setOnClickListener(v->cameraControl());
        camera.addView(cameraActionButton,buttonMargins(0,12,0,0));
        p.addView(camera,margins(10,10,10,6));

        LinearLayout probes=new LinearLayout(this);
        probes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout kc=probeCard("Щуп K");
        kValue=center("— °C",30,true);
        kTargetValue=center("цель —",13,false);
        kTargetValue.setTextColor(MUTED);
        kc.addView(kValue);
        kc.addView(kTargetValue);
        LinearLayout tc=probeCard("Щуп T");
        tValue=center("— °C",30,true);
        tTargetValue=center("цель —",13,false);
        tTargetValue.setTextColor(MUTED);
        tc.addView(tValue);
        tc.addView(tTargetValue);
        addHalf(probes,kc,10,5);
        addHalf(probes,tc,5,10);
        p.addView(probes);

        LinearLayout heat=card();
        LinearLayout heatHead=new LinearLayout(this);
        heatHead.setGravity(Gravity.CENTER_VERTICAL);
        heatHead.addView(text("ТЭН",17,true),new LinearLayout.LayoutParams(0,-2,1));
        powerValue=text("— %",22,true);
        powerValue.setTextColor(ORANGE);
        heatHead.addView(powerValue);
        heat.addView(heatHead);
        heaterProgress=horizontalProgress(ORANGE);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(9));
        hp.setMargins(0,dp(10),0,dp(2));
        heat.addView(heaterProgress,hp);
        p.addView(heat,margins(10,6,10,6));

        LinearLayout modeCard=card();
        modeCard.addView(sectionTitle("Режим работы"));
        LinearLayout modes=new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        manualModeButton=modeButton("Ручной");
        pidModeButton=modeButton("PID");
        autoModeButton=modeButton("Auto");
        manualModeButton.setOnClickListener(v->{if(requireService())service.selectManual();});
        pidModeButton.setOnClickListener(v->{if(requireService())service.selectPid();});
        autoModeButton.setOnClickListener(v->showPrograms());
        modes.addView(manualModeButton,weightButton());
        modes.addView(pidModeButton,weightButton());
        modes.addView(autoModeButton,weightButton());
        modeCard.addView(modes);
        p.addView(modeCard,margins(10,6,10,6));

        Button stop=action("STOP · выключить нагрев",RED);
        stop.setTextSize(18);
        stop.setMinHeight(dp(56));
        stop.setOnClickListener(v->confirmStop());
        p.addView(stop,margins(10,6,10,8));

        LinearLayout auto=card();
        auto.addView(sectionTitle("Авто"));
        autoValue=text("Программа не запущена",19,true);
        autoValue.setPadding(0,dp(6),0,dp(3));
        auto.addView(autoValue);
        autoStageValue=text("",15,true);
        autoStageValue.setTextColor(BLUE_DARK);
        auto.addView(autoStageValue);
        autoChamberValue=text("",14,false);
        autoChamberValue.setPadding(0,dp(6),0,0);
        auto.addView(autoChamberValue);
        autoProbeValue=text("",14,false);
        auto.addView(autoProbeValue);
        autoStabilizationValue=text("",14,true);
        autoStabilizationValue.setPadding(0,dp(6),0,0);
        auto.addView(autoStabilizationValue);
        autoHoldValue=text("",14,false);
        auto.addView(autoHoldValue);
        autoProgressCaption=text("",12,false);
        autoProgressCaption.setTextColor(MUTED);
        autoProgressCaption.setPadding(0,dp(8),0,dp(3));
        auto.addView(autoProgressCaption);
        autoProgressBar=horizontalProgress(BLUE);
        auto.addView(autoProgressBar,new LinearLayout.LayoutParams(-1,dp(9)));
        autoChooseButton=primary("Выбрать программу");
        autoChooseButton.setOnClickListener(v->showPrograms());
        auto.addView(autoChooseButton,buttonMargins(0,12,0,0));
        autoStopButton=action("Остановить программу",RED);
        autoStopButton.setOnClickListener(v->confirmStop());
        auto.addView(autoStopButton,buttonMargins(0,12,0,0));
        p.addView(auto,margins(10,6,10,6));

        errorValue=text("",13,true);
        errorValue.setTextColor(RED);
        errorValue.setPadding(dp(12),dp(4),dp(12),dp(12));
        p.addView(errorValue);

        setPage(p);
        renderState(state);
    }

    private void showPrograms(){
        closeDrawer();
        setPageTitle("Авто программы",true);
        programs=programRepo.load();
        LinearLayout p=page();
        TextView intro=text("До 4 этапов. Выдержка считается только пока температура камеры находится в заданном диапазоне. При выходе из допуска таймер автоматически ставится на паузу.",14,false);
        intro.setTextColor(MUTED);
        intro.setPadding(dp(10),dp(8),dp(10),dp(10));
        p.addView(intro);

        Button add=primary("+ Новая программа");
        add.setOnClickListener(v->{
            AutoProgram x=ProgramRepository.defaultProgram();
            x.name="Новая программа "+(programs.size()+1);
            programs.add(x);
            programRepo.save(programs);
            editProgram(programs.size()-1);
        });
        p.addView(add,margins(10,2,10,8));

        for(int i=0;i<programs.size();i++){
            final int idx=i;
            AutoProgram pr=programs.get(i);
            LinearLayout c=card();
            c.addView(text(pr.name,20,true));
            if(pr.description!=null&&!pr.description.trim().isEmpty()){
                TextView d=text(pr.description,13,false);
                d.setTextColor(MUTED);
                d.setPadding(0,dp(3),0,0);
                c.addView(d);
            }
            TextView sum=text(programSummary(pr),13,false);
            sum.setPadding(0,dp(7),0,dp(10));
            c.addView(sum);
            LinearLayout row=new LinearLayout(this);
            Button run=smallAction("Запустить",GREEN);
            Button edit=smallAction("Изменить",BLUE);
            Button copy=smallAction("Копия",ORANGE);
            run.setOnClickListener(v->startProgram(idx));
            edit.setOnClickListener(v->editProgram(idx));
            copy.setOnClickListener(v->{programs.add(pr.copy());programRepo.save(programs);showPrograms();});
            row.addView(run,weightButton());
            row.addView(edit,weightButton());
            row.addView(copy,weightButton());
            c.addView(row);
            p.addView(c,margins(10,5,10,5));
        }

        LinearLayout io=new LinearLayout(this);
        Button export=button("Экспорт JSON");
        Button imp=button("Импорт JSON");
        export.setOnClickListener(v->exportPrograms());
        imp.setOnClickListener(v->importPrograms());
        io.addView(export,weightButton());
        io.addView(imp,weightButton());
        p.addView(io,margins(10,8,10,16));
        setPage(p);
    }

    private static final class StageEditor {
        CheckBox enabled,stop;
        EditText name,target,tolerance,stable,hold,probe;
        Spinner condition,activation;
        LinearLayout body,probeBlock;
        TextView probeType,preview;
    }

    private void editProgram(int index){
        if(index<0||index>=programs.size())return;
        AutoProgram pr=programs.get(index);
        setPageTitle("Редактор программы",true);
        LinearLayout p=page();

        LinearLayout general=card();
        general.addView(sectionTitle("Программа"));
        EditText name=editorEdit(InputType.TYPE_CLASS_TEXT);
        name.setText(pr.name);
        addLabeledField(general,"Название программы",name);
        EditText desc=editorEdit(InputType.TYPE_CLASS_TEXT);
        desc.setText(pr.description);
        addLabeledField(general,"Описание",desc);
        p.addView(general,margins(10,8,10,6));

        final StageEditor[] editors=new StageEditor[4];
        String[] conditions={"Только время","Только щуп K","Только щуп T","Время ИЛИ щуп K","Время ИЛИ щуп T","Время И щуп K","Время И щуп T"};
        String[] activations={"Сразу после начала этапа","После стабилизации камеры"};

        for(int i=0;i<4;i++){
            final int stageIndex=i;
            AutoStage s=pr.stages.get(i);
            StageEditor e=new StageEditor();
            editors[i]=e;
            LinearLayout c=card();

            LinearLayout header=new LinearLayout(this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            TextView h=text("Этап "+(i+1),19,true);
            header.addView(h,new LinearLayout.LayoutParams(0,-2,1));
            e.enabled=check("Вкл.");
            e.enabled.setChecked(s.enabled);
            header.addView(e.enabled);
            c.addView(header);

            e.body=new LinearLayout(this);
            e.body.setOrientation(LinearLayout.VERTICAL);
            e.name=editorEdit(InputType.TYPE_CLASS_TEXT);
            e.name.setText(s.name);
            addLabeledField(e.body,"Название этапа",e.name);
            e.target=editorInteger(s.chamberTarget);
            addLabeledField(e.body,"Температура камеры, °C",e.target);
            e.tolerance=editorNumber(s.tolerance);
            addLabeledField(e.body,"Допуск температуры, ±°C",e.tolerance);
            e.stable=editorInteger(s.stableSeconds);
            addLabeledField(e.body,"Стабилизация камеры, сек",e.stable);
            e.hold=editorNumber(s.holdMs/60000.0);
            addLabeledField(e.body,"Время выдержки, мин",e.hold);
            e.condition=spinner(conditions);
            e.condition.setSelection(conditionIndex(s.finishCondition));
            addLabeledSpinner(e.body,"Условие завершения",e.condition);

            e.probeBlock=new LinearLayout(this);
            e.probeBlock.setOrientation(LinearLayout.VERTICAL);
            e.probeBlock.setPadding(dp(12),dp(10),dp(12),dp(10));
            e.probeBlock.setBackground(roundStroke(SURFACE_ALT,10,BORDER,1));
            TextView probeSection=sectionTitle("Контроль продукта");
            e.probeBlock.addView(probeSection);
            e.probeType=text("Щуп: —",15,true);
            e.probeType.setPadding(0,dp(4),0,dp(4));
            e.probeBlock.addView(e.probeType);
            e.probe=editorNumber(s.probeTarget);
            addLabeledField(e.probeBlock,"Температура продукта, °C",e.probe);
            e.activation=spinner(activations);
            e.activation.setSelection(s.probeActivation==AutoStage.ProbeActivation.IMMEDIATE?0:1);
            addLabeledSpinner(e.probeBlock,"Когда включить контроль щупа",e.activation);
            LinearLayout.LayoutParams pbp=new LinearLayout.LayoutParams(-1,-2);
            pbp.setMargins(0,dp(10),0,dp(2));
            e.body.addView(e.probeBlock,pbp);

            e.stop=check("Выполнить STOP после этапа");
            e.stop.setChecked(s.stopAfter);
            e.body.addView(e.stop);

            e.preview=text("",13,false);
            e.preview.setTextColor(MUTED);
            e.preview.setPadding(dp(10),dp(10),dp(10),dp(2));
            e.body.addView(e.preview);
            c.addView(e.body);

            e.enabled.setOnCheckedChangeListener((button,checked)->setStageEditorEnabled(e,checked));
            e.condition.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){refreshStageEditor(editors[stageIndex]);}
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });
            e.activation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){refreshStageEditor(editors[stageIndex]);}
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });
            watch(e.name,()->refreshStageEditor(e));
            watch(e.target,()->refreshStageEditor(e));
            watch(e.tolerance,()->refreshStageEditor(e));
            watch(e.stable,()->refreshStageEditor(e));
            watch(e.hold,()->refreshStageEditor(e));
            watch(e.probe,()->refreshStageEditor(e));
            refreshStageEditor(e);
            setStageEditorEnabled(e,s.enabled);
            p.addView(c,margins(10,6,10,6));
        }

        LinearLayout actions=new LinearLayout(this);
        Button save=smallAction("Сохранить",GREEN);
        Button run=smallAction("Сохранить + старт",BLUE);
        Button del=smallAction("Удалить",RED);
        actions.addView(save,weightButton());
        actions.addView(run,weightButton());
        actions.addView(del,weightButton());
        p.addView(actions,margins(10,8,10,20));

        View.OnClickListener saver=v->{
            if(applyEditor(pr,name,desc,editors)){
                programRepo.save(programs);
                toast("Программа сохранена");
                if(v==run)startProgram(index); else showPrograms();
            }
        };
        save.setOnClickListener(saver);
        run.setOnClickListener(saver);
        del.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Удалить программу?")
                .setMessage(pr.name)
                .setPositiveButton("Удалить",(d,w)->{
                    if(programs.size()<=1){toast("Должна остаться хотя бы одна программа");return;}
                    programs.remove(index);
                    programRepo.save(programs);
                    showPrograms();
                })
                .setNegativeButton("Отмена",null)
                .show());
        setPage(p);
    }

    private void setStageEditorEnabled(StageEditor e,boolean enabled){
        if(e==null||e.body==null)return;
        e.body.setAlpha(enabled?1f:.45f);
        setEnabledRecursive(e.body,enabled);
        if(e.enabled!=null)e.enabled.setEnabled(true);
    }

    private void setEnabledRecursive(View v,boolean enabled){
        v.setEnabled(enabled);
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)setEnabledRecursive(g.getChildAt(i),enabled);
        }
    }

    private void refreshStageEditor(StageEditor e){
        if(e==null||e.condition==null)return;
        AutoStage.FinishCondition c=conditionAt(e.condition.getSelectedItemPosition());
        boolean usesK=usesK(c),usesT=usesT(c);
        e.probeBlock.setVisibility((usesK||usesT)?View.VISIBLE:View.GONE);
        e.probeType.setText(usesK?"Щуп: K":usesT?"Щуп: T":"Щуп: —");
        e.preview.setText(editorStageDescription(e,c));
    }

    private String editorStageDescription(StageEditor e,AutoStage.FinishCondition c){
        String chamber=displayEdit(e.target,"—");
        String tol=displayEdit(e.tolerance,"—");
        String hold=displayEdit(e.hold,"—");
        String stable=displayEdit(e.stable,"0");
        String probe=displayEdit(e.probe,"—");
        StringBuilder b=new StringBuilder();
        switch(c){
            case TIME:b.append("Этап завершится после ").append(hold).append(" минут выдержки.");break;
            case PROBE_K:b.append("Этап завершится, когда щуп K достигнет ").append(probe).append(" °C.");break;
            case PROBE_T:b.append("Этап завершится, когда щуп T достигнет ").append(probe).append(" °C.");break;
            case TIME_OR_K:b.append("Этап завершится по первому условию: ").append(hold).append(" минут выдержки или K ").append(probe).append(" °C.");break;
            case TIME_OR_T:b.append("Этап завершится по первому условию: ").append(hold).append(" минут выдержки или T ").append(probe).append(" °C.");break;
            case TIME_AND_K:b.append("Этап завершится после выполнения обоих условий: ").append(hold).append(" минут выдержки и K ").append(probe).append(" °C.");break;
            case TIME_AND_T:b.append("Этап завершится после выполнения обоих условий: ").append(hold).append(" минут выдержки и T ").append(probe).append(" °C.");break;
        }
        if(usesTime(c)){
            b.append(" Отсчёт идёт только после стабилизации камеры при ").append(chamber).append(" ±").append(tol).append(" °C в течение ").append(stable).append(" сек.");
        }
        if(usesK(c)||usesT(c)){
            b.append(e.activation.getSelectedItemPosition()==0?" Контроль щупа активен сразу.":" Контроль щупа начнётся после стабилизации камеры.");
        }
        if(e.stop.isChecked())b.append(" После этапа будет выполнен STOP.");
        return b.toString();
    }

    private boolean applyEditor(AutoProgram pr,EditText name,EditText desc,StageEditor[] editors){
        try{
            String n=name.getText().toString().trim();
            if(n.isEmpty())throw new IllegalArgumentException("Введите название программы");
            pr.name=n;
            pr.description=desc.getText().toString().trim();
            boolean any=false;
            for(int i=0;i<4;i++){
                StageEditor e=editors[i];
                AutoStage s=pr.stages.get(i);
                s.enabled=e.enabled.isChecked();
                any|=s.enabled;
                s.name=e.name.getText().toString().trim();
                if(s.name.isEmpty())s.name="Этап "+(i+1);
                s.chamberTarget=parse(e.target);
                s.tolerance=parse(e.tolerance);
                s.stableSeconds=(int)parse(e.stable);
                s.holdMs=(long)(parse(e.hold)*60000.0);
                s.finishCondition=conditionAt(e.condition.getSelectedItemPosition());
                s.probeTarget=parseOptional(e.probe,0);
                s.probeActivation=e.activation.getSelectedItemPosition()==0?AutoStage.ProbeActivation.IMMEDIATE:AutoStage.ProbeActivation.AFTER_CHAMBER_READY;
                s.stopAfter=e.stop.isChecked();
                validateStage(s,i);
            }
            if(!any)throw new IllegalArgumentException("Включите хотя бы один этап");
            pr.modifiedAt=System.currentTimeMillis();
            return true;
        }catch(Exception e){
            toast("Ошибка программы: "+e.getMessage());
            return false;
        }
    }

    private void validateStage(AutoStage s,int i){
        if(!s.enabled)return;
        if(s.chamberTarget<0||s.chamberTarget>100||Math.abs(s.chamberTarget-Math.rint(s.chamberTarget))>.000001)throw new IllegalArgumentException("этап "+(i+1)+": камера — целое 0…100°C");
        if(s.tolerance<0||s.tolerance>10)throw new IllegalArgumentException("этап "+(i+1)+": допуск 0…10°C");
        if(s.stableSeconds<0||s.stableSeconds>3600)throw new IllegalArgumentException("этап "+(i+1)+": стабилизация 0…3600 сек");
        if(s.holdMs<0||s.holdMs>86400000L)throw new IllegalArgumentException("этап "+(i+1)+": выдержка до 24 ч");
        if((s.usesK()||s.usesT())&&(s.probeTarget<0||s.probeTarget>100))throw new IllegalArgumentException("этап "+(i+1)+": щуп 0…100°C");
    }

    private void startProgram(int idx){
        if(!requireService()||idx<0||idx>=programs.size())return;
        if(service.startAuto(programs.get(idx)))showDashboard();
        else toast(service.getState().lastError);
    }

    private void showHistory(){
        setPageTitle("История",true);
        LinearLayout p=page();
        List<File> files=historyStore.list();
        if(files.isEmpty()){
            p.addView(center("История Auto пока пуста",16,false),margins(10,24,10,8));
            setPage(p);
            return;
        }
        for(File f:files){
            LinearLayout c=card();
            String display=f.getName().replace(".csv","");
            c.addView(text(display,16,true));
            TextView meta=text("Размер: "+f.length()+" байт · "+new SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(f.lastModified())),12,false);
            meta.setTextColor(MUTED);
            c.addView(meta);
            Button open=primary("Открыть график");
            open.setOnClickListener(v->showHistoryFile(f));
            c.addView(open,buttonMargins(0,8,0,0));
            p.addView(c,margins(10,5,10,5));
        }
        setPage(p);
    }

    private void showHistoryFile(File f){
        setPageTitle("График процесса",true);
        LinearLayout p=page();
        p.addView(text(f.getName(),15,true),margins(10,8,10,4));
        TelemetryChartView chart=new TelemetryChartView(this);
        try{chart.setPoints(historyStore.readPoints(f,500));}
        catch(Exception e){toast("Не удалось прочитать историю");}
        p.addView(chart,new LinearLayout.LayoutParams(-1,dp(320)));
        TextView legend=text("Камера · уставка · K · T",12,false);
        legend.setTextColor(MUTED);
        legend.setPadding(dp(10),dp(8),dp(10),dp(8));
        p.addView(legend);
        TextView path=text("Файл: "+f.getAbsolutePath(),11,false);
        path.setTextIsSelectable(true);
        p.addView(path,margins(10,4,10,12));
        setPage(p);
    }

    private void showPid(){
        setPageTitle("Настройка PID",true);
        LinearLayout p=page();
        Telemetry t=state==null?null:state.telemetry;
        LinearLayout current=card();
        current.addView(sectionTitle("Текущие коэффициенты"));
        current.addView(text("kP  "+val(t==null?Double.NaN:t.kP)+"     kI  "+val(t==null?Double.NaN:t.kI),16,true));
        current.addView(text("kD  "+val(t==null?Double.NaN:t.kD)+"     zP  "+val(t==null?Double.NaN:t.zP),16,true));
        p.addView(current,margins(10,8,10,8));
        addPidRow(p,"kP","p");
        addPidRow(p,"kI","i");
        addPidRow(p,"kD","d");
        addPidRow(p,"zP","z");
        TextView hint=text("Можно вводить дробные значения через точку или запятую. Перед отправкой коэффициент умножается на 100 без округления до целого.",13,false);
        hint.setTextColor(MUTED);
        p.addView(hint,margins(10,10,10,16));
        setPage(p);
    }

    private void addPidRow(LinearLayout p,String label,String prefix){
        LinearLayout r=card();
        r.addView(text(label,18,true));
        EditText e=editorEdit(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        addLabeledField(r,"Значение",e);
        Button b=primary("Применить "+label);
        b.setOnClickListener(v->{
            if(requireService()&&service.setPidCoefficient(prefix,e.getText().toString()))toast(label+" отправлен");
            else toast("Не удалось отправить "+label);
        });
        r.addView(b,buttonMargins(0,8,0,0));
        p.addView(r,margins(10,4,10,4));
    }

    private void showSettings(){
        setPageTitle("Bluetooth и настройки",true);
        LinearLayout p=page();
        LinearLayout bt=card();
        bt.addView(sectionTitle("Bluetooth"));
        TextView st=text(state!=null&&state.bluetoothConnected?"Подключено: "+state.bluetoothName:"Не подключено",15,true);
        st.setPadding(0,dp(8),0,dp(8));
        st.setTextColor(state!=null&&state.bluetoothConnected?GREEN:MUTED);
        bt.addView(st);
        Button choose=primary("Выбрать Bluetooth устройство");
        choose.setOnClickListener(v->chooseBluetooth());
        bt.addView(choose);
        Button off=button("Отключить Bluetooth");
        off.setOnClickListener(v->{if(requireService())service.disconnectBluetooth();});
        bt.addView(off,buttonMargins(0,6,0,0));
        p.addView(bt,margins(10,8,10,5));
        CheckBox keep=check("Не выключать экран при открытом приложении");
        keep.setChecked(prefs.getBoolean("keep",false));
        keep.setOnCheckedChangeListener((b,c)->{prefs.edit().putBoolean("keep",c).apply();applyKeepScreen(c);});
        p.addView(keep,margins(10,8,10,8));
        setPage(p);
    }

    private void showMqtt(){
        setPageTitle("MQTT",true);
        LinearLayout p=page();
        LinearLayout c=card();
        TextView id=text("Device ID: "+(service==null?prefs.getString("device_id","—"):service.deviceId()),13,true);
        id.setTextColor(MUTED);
        c.addView(id);
        EditText broker=editorEdit(InputType.TYPE_CLASS_TEXT);
        EditText port=editorEdit(InputType.TYPE_CLASS_NUMBER);
        EditText status=editorEdit(InputType.TYPE_CLASS_TEXT);
        EditText cmd=editorEdit(InputType.TYPE_CLASS_TEXT);
        EditText ack=editorEdit(InputType.TYPE_CLASS_TEXT);
        EditText user=editorEdit(InputType.TYPE_CLASS_TEXT);
        EditText pass=editorEdit(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        broker.setText(prefs.getString("broker",""));
        port.setText(prefs.getString("port","1883"));
        status.setText(prefs.getString("topic","homesmoke/status"));
        cmd.setText(prefs.getString("cmd_topic","homesmoke/cmd"));
        ack.setText(prefs.getString("ack_topic","homesmoke/ack"));
        user.setText(prefs.getString("user",""));
        pass.setText(service!=null?service.mqttPassword():new SecretStore(this).get());
        addLabeledField(c,"Broker / IP",broker);
        addLabeledField(c,"Port",port);
        addLabeledField(c,"Status topic",status);
        addLabeledField(c,"Command topic",cmd);
        addLabeledField(c,"ACK topic",ack);
        addLabeledField(c,"Логин",user);
        addLabeledField(c,"Пароль",pass);
        CheckBox tls=check("TLS");
        tls.setChecked(prefs.getBoolean("tls",false));
        CheckBox auto=check("Автоподключение MQTT");
        auto.setChecked(prefs.getBoolean("mqtt_auto",false));
        c.addView(tls);
        c.addView(auto);
        Button connect=primary("Сохранить и подключить");
        connect.setOnClickListener(v->{
            if(requireService()){
                service.configureMqtt(broker.getText().toString(),port.getText().toString(),status.getText().toString(),cmd.getText().toString(),ack.getText().toString(),user.getText().toString(),pass.getText().toString(),tls.isChecked(),auto.isChecked());
                service.startMqtt();
                toast("MQTT подключение запущено");
            }
        });
        c.addView(connect,buttonMargins(0,8,0,4));
        Button disconnect=button("Отключить MQTT");
        disconnect.setOnClickListener(v->{if(requireService())service.stopMqtt();});
        c.addView(disconnect,buttonMargins(0,2,0,8));
        TextView note=text("Remote-команда считается применённой только после подтверждения новой уставки в телеметрии Arduino. Устаревшие команды отклоняются.",13,false);
        note.setTextColor(MUTED);
        c.addView(note);
        p.addView(c,margins(10,8,10,14));
        setPage(p);
    }

    private void showDiagnostics(){
        setPageTitle("Диагностика",true);
        LinearLayout p=page();
        Telemetry t=state==null?null:state.telemetry;
        LinearLayout c=card();
        c.addView(text("Bluetooth: "+(state!=null&&state.bluetoothConnected?"OK":"нет"),15,true));
        c.addView(text("MQTT: "+(state==null?"—":state.mqttState),15,false));
        c.addView(text("Auto: "+(state==null?"—":autoStateName(state.autoState)+" · "+state.autoStatus),15,false));
        if(state!=null)c.addView(text("Камера стабилизирована: "+(state.chamberReady?"да":"нет")+" · выдержка: "+formatDuration(state.autoHoldMs),14,false));
        if(t!=null){
            c.addView(text("Камера: "+val(t.chamber)+" °C",14,false));
            c.addView(text("K: "+val(t.probeK)+" °C   T: "+val(t.probeT)+" °C",14,false));
            c.addView(text("Уставка: "+val(t.chamberSetpoint)+" °C   ТЭН: "+val(t.heaterPower)+" %",14,false));
            c.addView(text("PID: P="+val(t.kP)+" I="+val(t.kI)+" D="+val(t.kD)+" z="+val(t.zP),14,false));
            TextView raw=text("RAW:\n"+t.raw,12,false);
            raw.setTypeface(Typeface.MONOSPACE);
            raw.setTextIsSelectable(true);
            raw.setPadding(0,dp(10),0,0);
            c.addView(raw);
        }
        p.addView(c,margins(10,8,10,8));
        setPage(p);
    }

    private void cameraControl(){
        if(!requireService())return;
        if(service.isAutoRunning()){
            toast("Уставкой управляет Auto программа");
            return;
        }
        Telemetry t=state==null?null:state.telemetry;
        if(t==null){toast("Нет данных Arduino");return;}
        if(t.mode==1)valueDialog("Уставка камеры, °C",t.chamberSetpoint,v->service.setChamberSetpoint(v));
        else if(t.mode==0)valueDialog("Мощность ТЭНа, %",t.heaterPower,v->service.setManualPower(v));
        else toast("Выберите Ручной или PID режим");
    }

    private interface ValueAction{boolean apply(double value);}

    private void valueDialog(String caption,double current,ValueAction action){
        EditText e=editorInteger(Double.isNaN(current)?0:current);
        new AlertDialog.Builder(this)
                .setTitle(caption)
                .setMessage("Введите целое значение 0…100")
                .setView(e)
                .setPositiveButton("Применить",(d,w)->{
                    try{
                        double v=parse(e);
                        if(v<0||v>100||Math.abs(v-Math.rint(v))>.000001||!action.apply(v))toast("Нужно целое число 0…100");
                    }catch(Exception ex){toast("Неверное значение");}
                })
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void confirmStop(){
        new AlertDialog.Builder(this)
                .setTitle("STOP")
                .setMessage("Выключить нагрев и остановить Auto-программу?")
                .setPositiveButton("STOP",(d,w)->{if(requireService())service.stopHeating();})
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void chooseBluetooth(){
        if(!requireService())return;
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);
            return;
        }
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(a==null){toast("Bluetooth не поддерживается");return;}
        if(!a.isEnabled()){toast("Включите Bluetooth");return;}
        Set<BluetoothDevice> set=a.getBondedDevices();
        if(set==null||set.isEmpty()){toast("Нет спаренных устройств");return;}
        List<BluetoothDevice> devices=new ArrayList<>(set);
        String[] names=new String[devices.size()];
        for(int i=0;i<devices.size();i++){
            BluetoothDevice d=devices.get(i);
            String n;
            try{n=d.getName();}catch(Exception e){n=null;}
            names[i]=(n==null?"Устройство":n)+"\n"+d.getAddress();
        }
        new AlertDialog.Builder(this)
                .setTitle("Bluetooth устройства")
                .setItems(names,(d,w)->service.connectBluetooth(devices.get(w).getAddress()))
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void exportPrograms(){
        try{
            String json=programRepo.exportJson(programs);
            ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            c.setPrimaryClip(ClipData.newPlainText("HomeSmoke programs",json));
            EditText e=new EditText(this);
            e.setText(json);
            e.setTextIsSelectable(true);
            e.setMinLines(8);
            new AlertDialog.Builder(this).setTitle("JSON скопирован").setView(e).setPositiveButton("OK",null).show();
        }catch(Exception e){toast("Ошибка экспорта");}
    }

    private void importPrograms(){
        EditText e=new EditText(this);
        e.setHint("Вставьте JSON библиотеки программ");
        e.setMinLines(10);
        e.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this)
                .setTitle("Импорт программ")
                .setView(e)
                .setPositiveButton("Импорт",(d,w)->{
                    try{
                        programs=programRepo.importJson(e.getText().toString());
                        showPrograms();
                        toast("Импортировано: "+programs.size());
                    }catch(Exception ex){toast("Неверный JSON");}
                })
                .setNegativeButton("Отмена",null)
                .show();
    }

    private void showDrawer(){
        if(drawer!=null&&drawer.isShowing()){closeDrawer();return;}
        FrameLayout overlay=new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(105,0,0,0));
        overlay.setPadding(0,insetTop,0,insetBottom);
        int width=Math.min(dp(320),(int)(getResources().getDisplayMetrics().widthPixels*.86));
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20),0,dp(10),0);
        head.setBackgroundColor(BLUE);
        TextView h1=text("HomeSmoke "+versionName(),21,true);
        h1.setTextColor(Color.WHITE);
        TextView h2=text("Домашняя коптильня",13,false);
        h2.setTextColor(Color.rgb(220,235,248));
        head.addView(h1);
        head.addView(h2);
        panel.addView(head,new LinearLayout.LayoutParams(-1,dp(82)));

        drawerItem(panel,"Монитор",this::showDashboard);
        drawerItem(panel,"Авто программы",this::showPrograms);
        drawerItem(panel,"История / графики",this::showHistory);
        drawerItem(panel,"Настройка PID",this::showPid);
        drawerItem(panel,"Bluetooth и настройки",this::showSettings);
        drawerItem(panel,"MQTT",this::showMqtt);
        drawerItem(panel,"Диагностика",this::showDiagnostics);
        drawerItem(panel,"STOP · нагрев выкл.",this::confirmStop);
        drawerItem(panel,"Выход",this::finish);

        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(width,-1,Gravity.START);
        overlay.addView(panel,pp);
        overlay.setOnClickListener(v->closeDrawer());
        panel.setOnClickListener(v->{});
        PopupWindow pop=new PopupWindow(overlay,-1,-1,true);
        pop.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pop.setOutsideTouchable(true);
        pop.setOnDismissListener(()->{if(drawer==pop)drawer=null;});
        drawer=pop;
        pop.showAtLocation(menuButton,Gravity.TOP|Gravity.START,0,0);
        panel.post(()->{
            panel.setTranslationX(-panel.getWidth());
            panel.animate().translationX(0).setDuration(180).start();
        });
    }

    private void drawerItem(LinearLayout panel,String label,Runnable action){
        TextView t=text(label,17,false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(22),0,dp(10),0);
        if(label.startsWith("STOP"))t.setTextColor(RED);
        t.setOnClickListener(v->{closeDrawer();action.run();});
        panel.addView(t,new LinearLayout.LayoutParams(-1,dp(54)));
        View line=new View(this);
        line.setBackgroundColor(Color.rgb(235,239,243));
        panel.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void closeDrawer(){
        if(drawer!=null&&drawer.isShowing())drawer.dismiss();
        drawer=null;
    }

    @Override public void onState(HomeSmokeService.State s){
        runOnUiThread(()->{state=s;renderState(s);});
    }

    private void renderState(HomeSmokeService.State s){
        if(btBadge!=null)setBadge(btBadge,s!=null&&s.bluetoothConnected);
        if(mqttBadge!=null)setBadge(mqttBadge,s!=null&&s.mqttConnected);
        if(cameraValue==null)return;

        Telemetry t=s==null?null:s.telemetry;
        cameraValue.setText(t==null?"— °C":val(t.chamber)+" °C");
        setpointValue.setText(t==null?"Уставка — °C":"Уставка "+val(t.chamberSetpoint)+" °C");
        kValue.setText(t==null?"— °C":val(t.probeK)+" °C");
        tValue.setText(t==null?"— °C":val(t.probeT)+" °C");
        powerValue.setText(t==null?"— %":val(t.heaterPower)+" %");
        heaterProgress.setProgress(t==null?0:clampPercent(t.heaterPower));
        errorValue.setText(s==null?"":s.lastError);

        boolean autoRunning=s!=null&&s.autoState==AutoEngine.State.RUNNING;
        boolean manual=!autoRunning&&t!=null&&t.mode==0;
        boolean pid=!autoRunning&&t!=null&&t.mode==1;
        applyModeStyle(manualModeButton,manual,ORANGE);
        applyModeStyle(pidModeButton,pid,GREEN);
        applyModeStyle(autoModeButton,autoRunning,BLUE);

        if(autoRunning){
            cameraActionButton.setText("Температурой управляет Auto");
            cameraActionButton.setEnabled(false);
            cameraActionButton.setAlpha(.6f);
        }else if(pid){
            cameraActionButton.setText("Задать температуру");
            cameraActionButton.setEnabled(true);
            cameraActionButton.setAlpha(1f);
        }else if(manual){
            cameraActionButton.setText("Задать мощность ТЭНа");
            cameraActionButton.setEnabled(true);
            cameraActionButton.setAlpha(1f);
        }else{
            cameraActionButton.setText("Выберите Ручной или PID режим");
            cameraActionButton.setEnabled(false);
            cameraActionButton.setAlpha(.6f);
        }

        ActiveStageInfo info=activeStageInfo(s);
        if(autoRunning){
            autoChooseButton.setVisibility(View.GONE);
            autoStopButton.setVisibility(View.VISIBLE);
            autoStageValue.setVisibility(View.VISIBLE);
            autoChamberValue.setVisibility(View.VISIBLE);
            autoStabilizationValue.setVisibility(View.VISIBLE);
            autoProgressCaption.setVisibility(View.VISIBLE);
            autoProgressBar.setVisibility(View.VISIBLE);
            if(info!=null){
                AutoStage stage=info.stage;
                autoValue.setText(info.program.name);
                autoStageValue.setText(stage.name+" · этап "+info.activePosition+" из "+info.activeCount);
                autoChamberValue.setText("Камера: "+(t==null?"—":val(t.chamber))+" / "+val(stage.chamberTarget)+" °C");
                if(stage.usesK()){
                    autoProbeValue.setVisibility(View.VISIBLE);
                    autoProbeValue.setText("Щуп K: "+(t==null?"—":val(t.probeK))+" / "+val(stage.probeTarget)+" °C");
                    kTargetValue.setText("цель "+val(stage.probeTarget)+" °C");
                    tTargetValue.setText("цель —");
                }else if(stage.usesT()){
                    autoProbeValue.setVisibility(View.VISIBLE);
                    autoProbeValue.setText("Щуп T: "+(t==null?"—":val(t.probeT))+" / "+val(stage.probeTarget)+" °C");
                    tTargetValue.setText("цель "+val(stage.probeTarget)+" °C");
                    kTargetValue.setText("цель —");
                }else{
                    autoProbeValue.setVisibility(View.GONE);
                    kTargetValue.setText("цель —");
                    tTargetValue.setText("цель —");
                }
                if(s.chamberReady){
                    autoStabilizationValue.setText("Камера стабилизирована — выдержка идёт");
                    autoStabilizationValue.setTextColor(GREEN);
                }else if(stage.usesTime()&&s.autoHoldMs>0){
                    autoStabilizationValue.setText("Камера вне диапазона — выдержка на паузе");
                    autoStabilizationValue.setTextColor(ORANGE);
                }else{
                    autoStabilizationValue.setText("Стабилизация: ожидание диапазона "+val(stage.chamberTarget)+" ±"+val(stage.tolerance)+" °C");
                    autoStabilizationValue.setTextColor(MUTED);
                }
                if(stage.usesTime()){
                    autoHoldValue.setVisibility(View.VISIBLE);
                    autoHoldValue.setText("Выдержка: "+formatDuration(s.autoHoldMs)+" / "+formatDuration(stage.holdMs));
                }else autoHoldValue.setVisibility(View.GONE);
                int progress=autoProgressPercent(stage,t,s.autoHoldMs);
                autoProgressBar.setProgress(progress);
                autoProgressCaption.setText("Прогресс этапа · "+progress+"%");
            }else{
                autoValue.setText(s.autoStatus==null||s.autoStatus.trim().isEmpty()?"Auto выполняется":s.autoStatus);
                autoStageValue.setText("Этап "+(s.autoStageIndex+1));
                autoChamberValue.setText(t==null?"Камера: —":"Камера: "+val(t.chamber)+" °C");
                autoProbeValue.setVisibility(View.GONE);
                autoHoldValue.setVisibility(View.VISIBLE);
                autoHoldValue.setText("Выдержка: "+formatDuration(s.autoHoldMs));
                autoStabilizationValue.setText(s.chamberReady?"Камера стабилизирована":"Стабилизация камеры");
                autoStabilizationValue.setTextColor(s.chamberReady?GREEN:MUTED);
                autoProgressBar.setProgress(0);
                autoProgressCaption.setText("Прогресс этапа");
                kTargetValue.setText("цель —");
                tTargetValue.setText("цель —");
            }
        }else{
            autoValue.setText(s==null||s.autoStatus==null||s.autoStatus.trim().isEmpty()||"Auto выключено".equals(s.autoStatus)?"Программа не запущена":s.autoStatus);
            autoChooseButton.setVisibility(View.VISIBLE);
            autoStopButton.setVisibility(View.GONE);
            autoStageValue.setVisibility(View.GONE);
            autoChamberValue.setVisibility(View.GONE);
            autoProbeValue.setVisibility(View.GONE);
            autoStabilizationValue.setVisibility(View.GONE);
            autoHoldValue.setVisibility(View.GONE);
            autoProgressCaption.setVisibility(View.GONE);
            autoProgressBar.setVisibility(View.GONE);
            kTargetValue.setText("цель —");
            tTargetValue.setText("цель —");
        }
    }

    private ActiveStageInfo activeStageInfo(HomeSmokeService.State s){
        if(s==null||s.autoState!=AutoEngine.State.RUNNING||s.autoStageIndex<0)return null;
        if(programs==null||programs.isEmpty())programs=programRepo.load();
        String status=s.autoStatus==null?"":s.autoStatus;
        for(AutoProgram p:programs){
            if(p==null||p.name==null)continue;
            if(!(status.equals(p.name)||status.startsWith(p.name+" · ")))continue;
            if(s.autoStageIndex>=p.stages.size())continue;
            AutoStage stage=p.stages.get(s.autoStageIndex);
            int count=0,pos=0;
            for(int i=0;i<p.stages.size()&&i<4;i++){
                if(p.stages.get(i).enabled){
                    count++;
                    if(i<=s.autoStageIndex)pos=count;
                }
            }
            return new ActiveStageInfo(p,stage,Math.max(1,pos),Math.max(1,count));
        }
        return null;
    }

    private static final class ActiveStageInfo {
        final AutoProgram program;
        final AutoStage stage;
        final int activePosition,activeCount;
        ActiveStageInfo(AutoProgram program,AutoStage stage,int activePosition,int activeCount){
            this.program=program;
            this.stage=stage;
            this.activePosition=activePosition;
            this.activeCount=activeCount;
        }
    }

    private static int autoProgressPercent(AutoStage stage,Telemetry t,long holdMs){
        double timeProgress=stage.usesTime()?(stage.holdMs<=0?1.0:Math.min(1.0,Math.max(0.0,holdMs/(double)stage.holdMs))):1.0;
        double probeProgress=1.0;
        if(stage.usesK())probeProgress=probeRatio(t==null?Double.NaN:t.probeK,stage.probeTarget);
        else if(stage.usesT())probeProgress=probeRatio(t==null?Double.NaN:t.probeT,stage.probeTarget);
        double p;
        switch(stage.finishCondition){
            case TIME_OR_K:case TIME_OR_T:p=Math.max(timeProgress,probeProgress);break;
            case TIME_AND_K:case TIME_AND_T:p=Math.min(timeProgress,probeProgress);break;
            case PROBE_K:case PROBE_T:p=probeProgress;break;
            default:p=timeProgress;
        }
        return clampPercent(p*100.0);
    }

    private static double probeRatio(double value,double target){
        if(Double.isNaN(value)||Double.isInfinite(value)||target<=0)return 0.0;
        return Math.min(1.0,Math.max(0.0,value/target));
    }

    private void autoConnectMqtt(){
        if(service!=null&&prefs.getBoolean("mqtt_auto",false)&&!service.isMqttConnected())service.startMqtt();
    }

    private void requestPermissionsIfNeeded(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
        applyKeepScreen(prefs.getBoolean("keep",false));
    }

    private void applyInsets(View root){
        if(Build.VERSION.SDK_INT<21)return;
        root.setOnApplyWindowInsetsListener((v,i)->{
            int l,t,r,b;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets x=i.getInsets(WindowInsets.Type.systemBars());
                l=x.left;t=x.top;r=x.right;b=x.bottom;
            }else{
                l=i.getSystemWindowInsetLeft();t=i.getSystemWindowInsetTop();r=i.getSystemWindowInsetRight();b=i.getSystemWindowInsetBottom();
            }
            insetTop=t;
            insetBottom=b;
            v.setPadding(l,t,r,b);
            return i;
        });
        root.requestApplyInsets();
        getWindow().setStatusBarColor(BLUE_DARK);
        getWindow().setNavigationBarColor(BG);
    }

    private void applyKeepScreen(boolean on){
        if(on)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void setPage(LinearLayout p){
        pageHost.removeAllViews();
        ScrollView s=new ScrollView(this);
        s.setFillViewport(true);
        s.addView(p,new ScrollView.LayoutParams(-1,-2));
        pageHost.addView(s,new LinearLayout.LayoutParams(-1,-1));
    }

    private void setPageTitle(String s,boolean back){
        if(back){
            title.setText(s);
            subtitle.setText("HomeSmoke · Домашняя коптильня");
        }else{
            title.setText("HomeSmoke");
            subtitle.setText("Домашняя коптильня");
        }
        backButton.setVisibility(back?View.VISIBLE:View.GONE);
        menuButton.setVisibility(back?View.GONE:View.VISIBLE);
    }

    private LinearLayout page(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(2),dp(2),dp(2),dp(24));
        p.setBackgroundColor(BG);
        return p;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(14),dp(16),dp(14));
        c.setBackground(roundStroke(Color.WHITE,14,BORDER,1));
        c.setElevation(dp(2));
        return c;
    }

    private LinearLayout probeCard(String label){
        LinearLayout c=card();
        TextView l=center(label,14,true);
        l.setTextColor(MUTED);
        c.addView(l);
        return c;
    }

    private TextView sectionTitle(String s){
        TextView t=text(s,15,true);
        t.setTextColor(MUTED);
        return t;
    }

    private void addHalf(LinearLayout row,View v,int left,int right){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(left),dp(4),dp(right),dp(4));
        row.addView(v,p);
    }

    private TextView text(String s,int sp,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(TEXT);
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView center(String s,int sp,boolean bold){
        TextView t=text(s,sp,bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView badge(String s){
        TextView t=text(s,10,true);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(7),dp(4),dp(7),dp(4));
        setBadge(t,false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));
        p.setMargins(dp(3),0,0,0);
        t.setLayoutParams(p);
        return t;
    }

    private void setBadge(TextView t,boolean on){
        t.setBackground(round(on?GREEN:Color.rgb(78,94,114),10));
        t.setAlpha(on?1f:.78f);
    }

    private ImageButton iconButton(int res){
        ImageButton b=new ImageButton(this);
        b.setImageResource(res);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(12),dp(12),dp(12),dp(12));
        return b;
    }

    private Button button(String s){
        Button b=new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setBackground(roundStroke(Color.WHITE,10,BORDER,1));
        b.setMinHeight(dp(48));
        return b;
    }

    private Button primary(String s){return action(s,BLUE);}

    private Button action(String s,int color){
        Button b=button(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(color,10));
        b.setMinHeight(dp(50));
        return b;
    }

    private Button modeButton(String s){
        Button b=button(s);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(50));
        return b;
    }

    private Button smallAction(String s,int color){
        Button b=action(s,color);
        b.setTextSize(13);
        b.setMinHeight(dp(48));
        return b;
    }

    private void applyModeStyle(Button b,boolean active,int color){
        if(b==null)return;
        b.setBackground(round(active?color:SURFACE_ALT,10));
        b.setTextColor(active?Color.WHITE:TEXT);
        b.setElevation(active?dp(2):0);
    }

    private CheckBox check(String s){
        CheckBox c=new CheckBox(this);
        c.setText(s);
        c.setTextSize(15);
        c.setTextColor(TEXT);
        c.setPadding(dp(6),dp(5),dp(6),dp(5));
        return c;
    }

    private EditText editorEdit(int type){
        EditText e=new EditText(this);
        e.setTextSize(16);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(155,165,178));
        e.setInputType(type);
        e.setSingleLine(true);
        e.setBackground(roundStroke(Color.WHITE,9,BORDER,1));
        e.setPadding(dp(12),0,dp(12),0);
        e.setMinHeight(dp(52));
        return e;
    }

    private EditText editorNumber(double value){
        EditText e=editorEdit(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if(value!=0)e.setText(BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
        return e;
    }

    private EditText editorInteger(double value){
        EditText e=editorEdit(InputType.TYPE_CLASS_NUMBER);
        if(value!=0)e.setText(String.valueOf((int)Math.rint(value)));
        return e;
    }

    private void addLabeledField(LinearLayout parent,String label,EditText field){
        TextView l=text(label,13,true);
        l.setTextColor(MUTED);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(10),0,dp(5));
        parent.addView(l,lp);
        parent.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));
    }

    private void addLabeledSpinner(LinearLayout parent,String label,Spinner field){
        TextView l=text(label,13,true);
        l.setTextColor(MUTED);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(10),0,dp(5));
        parent.addView(l,lp);
        LinearLayout wrap=new LinearLayout(this);
        wrap.setPadding(dp(6),0,dp(6),0);
        wrap.setBackground(roundStroke(Color.WHITE,9,BORDER,1));
        wrap.addView(field,new LinearLayout.LayoutParams(-1,dp(52)));
        parent.addView(wrap);
    }

    private Spinner spinner(String[] values){
        Spinner s=new Spinner(this);
        ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setPadding(dp(6),0,dp(6),0);
        return s;
    }

    private ProgressBar horizontalProgress(int color){
        ProgressBar p=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        p.setProgress(0);
        p.setProgressTintList(ColorStateList.valueOf(color));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE_ALT));
        return p;
    }

    private GradientDrawable round(int color,int radius){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable roundStroke(int color,int radius,int strokeColor,int strokeDp){
        GradientDrawable g=round(color,radius);
        g.setStroke(dp(strokeDp),strokeColor);
        return g;
    }

    private LinearLayout.LayoutParams margins(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(l),dp(t),dp(r),dp(b));
        return p;
    }

    private LinearLayout.LayoutParams buttonMargins(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
        p.setMargins(dp(l),dp(t),dp(r),dp(b));
        return p;
    }

    private LinearLayout.LayoutParams weightButton(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);
        p.setMargins(dp(3),dp(2),dp(3),dp(2));
        return p;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private boolean requireService(){
        if(service==null){toast("Сервис HomeSmoke ещё запускается");return false;}
        return true;
    }

    private void watch(EditText e,Runnable action){
        e.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){action.run();}
            @Override public void afterTextChanged(Editable s){}
        });
    }

    private String versionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception e){return "2.6";}
    }

    private static double parse(EditText e){return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}
    private static double parseOptional(EditText e,double d){String s=e.getText().toString().trim();return s.isEmpty()?d:Double.parseDouble(s.replace(',','.'));}
    private static String displayEdit(EditText e,String fallback){String s=e.getText().toString().trim();return s.isEmpty()?fallback:s.replace(',','.');}
    private static String val(double v){if(Double.isNaN(v)||Double.isInfinite(v))return "—";return String.format(Locale.US,Math.abs(v-Math.rint(v))<.005?"%.0f":"%.2f",v);}
    private static String modeName(int mode){switch(mode){case 0:return "Ручной";case 1:return "PID";case 2:return "Arduino Auto";case 3:return "STOP";default:return String.valueOf(mode);}}
    private static String autoStateName(AutoEngine.State s){if(s==null)return "—";switch(s){case RUNNING:return "выполняется";case COMPLETED:return "завершено";case ABORTED:return "остановлено";default:return "выключено";}}
    private static int conditionIndex(AutoStage.FinishCondition c){switch(c){case PROBE_K:return 1;case PROBE_T:return 2;case TIME_OR_K:return 3;case TIME_OR_T:return 4;case TIME_AND_K:return 5;case TIME_AND_T:return 6;default:return 0;}}
    private static AutoStage.FinishCondition conditionAt(int i){switch(i){case 1:return AutoStage.FinishCondition.PROBE_K;case 2:return AutoStage.FinishCondition.PROBE_T;case 3:return AutoStage.FinishCondition.TIME_OR_K;case 4:return AutoStage.FinishCondition.TIME_OR_T;case 5:return AutoStage.FinishCondition.TIME_AND_K;case 6:return AutoStage.FinishCondition.TIME_AND_T;default:return AutoStage.FinishCondition.TIME;}}
    private static boolean usesTime(AutoStage.FinishCondition c){return c==AutoStage.FinishCondition.TIME||c==AutoStage.FinishCondition.TIME_OR_K||c==AutoStage.FinishCondition.TIME_OR_T||c==AutoStage.FinishCondition.TIME_AND_K||c==AutoStage.FinishCondition.TIME_AND_T;}
    private static boolean usesK(AutoStage.FinishCondition c){return c==AutoStage.FinishCondition.PROBE_K||c==AutoStage.FinishCondition.TIME_OR_K||c==AutoStage.FinishCondition.TIME_AND_K;}
    private static boolean usesT(AutoStage.FinishCondition c){return c==AutoStage.FinishCondition.PROBE_T||c==AutoStage.FinishCondition.TIME_OR_T||c==AutoStage.FinishCondition.TIME_AND_T;}
    private static String programSummary(AutoProgram p){StringBuilder b=new StringBuilder();int n=0;for(AutoStage s:p.stages)if(s.enabled){if(n++>0)b.append(" → ");b.append(s.name).append(" ").append(val(s.chamberTarget)).append("°C");if(s.usesTime())b.append(" / ").append(val(s.holdMs/60000.0)).append(" мин");}return n==0?"Нет активных этапов":b.toString();}
    private static String formatDuration(long ms){long q=Math.max(0,ms/1000),h=q/3600,m=(q%3600)/60,s=q%60;return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);}
    private static int clampPercent(double v){if(Double.isNaN(v)||Double.isInfinite(v))return 0;return (int)Math.max(0,Math.min(100,Math.round(v)));}

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    @Override public void onBackPressed(){
        if(drawer!=null&&drawer.isShowing()){closeDrawer();return;}
        if(backButton!=null&&backButton.getVisibility()==View.VISIBLE){showDashboard();return;}
        super.onBackPressed();
    }
}
