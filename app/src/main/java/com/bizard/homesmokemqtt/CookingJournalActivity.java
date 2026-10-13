package com.bizard.homesmokemqtt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.bizard.homesmokecore.Telemetry;
import org.json.*;

/** Manual journal controls; stage changes never change PID or Arduino commands. */
public final class CookingJournalActivity extends Activity {
    private CookingJournal journal;
    private HomeSmokeService service;
    private boolean bound;
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){service=((HomeSmokeService.LocalBinder)binder).getService();journal=service.cookingJournal();bound=true;refresh();}
        @Override public void onServiceDisconnected(ComponentName name){service=null;bound=false;refresh();}
    };
    private final Runnable ticker=new Runnable(){@Override public void run(){
        refresh();handler.postDelayed(this,5000);
    }};
    private Telemetry telemetry(){HomeSmokeService.State s=service==null?null:service.getState();return s==null?null:s.telemetry;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);journal=new CookingJournal(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
        status=new TextView(this);status.setTextSize(18);root.addView(status);
        Button start=new Button(this);start.setText("Начать приготовление");root.addView(start);
        Button next=new Button(this);next.setText("Следующий этап");root.addView(next);
        Button finish=new Button(this);finish.setText("Завершить запись");root.addView(finish);
        Button previous=new Button(this);previous.setText("Последние сохранённые приготовления");root.addView(previous);
        previous.setOnClickListener(v->showSaved());
        TextView note=new TextView(this);note.setText("Этапы создаются только кнопкой. Изменение уставки не создаёт этап. Управление камерой остаётся на главном экране.");root.addView(note);
        setContentView(root);
        start.setOnClickListener(v->ask("Название приготовления","Рёбра",name->ask("Первый этап","Сушка",stage->act(()->journal.start(name,stage,telemetry())))));
        next.setOnClickListener(v->ask("Следующий этап","Копчение",name->act(()->journal.next(name,telemetry()))));
        finish.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Завершить приготовление?").setMessage("Это завершает только запись журнала, но не выключает нагрев. Для выключения используйте STOP на главном экране.").setNegativeButton("Отмена",null).setPositiveButton("Завершить запись",(d,w)->act(()->journal.finish(telemetry()))).show());
        bindService(new Intent(this,HomeSmokeService.class),connection,BIND_AUTO_CREATE);
    }
    private void showSaved(){
        java.io.File dir=new java.io.File(getFilesDir(),"cooking-journals");
        java.io.File[] files=dir.listFiles((d,n)->n.startsWith("cooking_")&&n.endsWith(".json"));
        if(files==null||files.length==0){new AlertDialog.Builder(this).setMessage("Сохранённых приготовлений пока нет").setPositiveButton("OK",null).show();return;}
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        String[] labels=new String[files.length];
        for(int i=0;i<files.length;i++){try{JSONObject j=new JSONObject(readFile(files[i]));labels[i]=j.optString("name")+" — "+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(j.optLong("startedAt")));}catch(Exception e){labels[i]=files[i].getName();}}
        new AlertDialog.Builder(this).setTitle("Приготовления").setItems(labels,(d,which)->showSavedDetails(files[which])).setPositiveButton("Закрыть",null).show();
    }
    private static String readFile(java.io.File f)throws Exception{try(java.io.FileInputStream in=new java.io.FileInputStream(f);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private void showSavedDetails(java.io.File file){try{
        JSONObject j=new JSONObject(readFile(file));JSONArray stages=j.getJSONArray("stages");StringBuilder b=new StringBuilder(j.optString("name")).append("\\n");
        for(int i=0;i<stages.length();i++){JSONObject stage=stages.getJSONObject(i);JSONObject a=stage.optJSONObject("start"),z=stage.optJSONObject("end");b.append("\\n").append(i+1).append(". ").append(stage.optString("name")).append("\\n").append("Начало: ").append(stage.optLong("startedAt")).append(" / конец: ").append(stage.optLong("endedAt")).append("\\n").append("Камера: ").append(a==null?"—":a.optString("chamber","—")).append(" → ").append(z==null?"—":z.optString("chamber","—")).append(" °C").append("\\n").append("Продукт K: ").append(a==null?"—":a.optString("probeK","—")).append(" → ").append(z==null?"—":z.optString("probeK","—")).append(" °C").append("\\n");}
        new AlertDialog.Builder(this).setTitle("Журнал").setMessage(b.toString()).setPositiveButton("OK",null).show();
    }catch(Exception e){new AlertDialog.Builder(this).setMessage("Ошибка чтения: "+e.getMessage()).setPositiveButton("OK",null).show();}}
    private interface InputAction{void run(String value);}
    private interface Action{void run()throws Exception;}
    private void ask(String title,String hint,InputAction action){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint(hint);
        new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{String value=input.getText().toString().trim();if(value.isEmpty())Toast.makeText(this,"Введите название",Toast.LENGTH_LONG).show();else action.run(value);}).show();
    }
    private void act(Action action){try{action.run();refresh();}catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}}
    private void refresh(){
        if(status==null)return;
        JSONObject j=journal.snapshot();
        if(j==null){status.setText("Нет активного приготовления");return;}
        JSONArray stages=j.optJSONArray("stages");JSONObject last=stages==null||stages.length()==0?null:stages.optJSONObject(stages.length()-1);
        status.setText("Приготовление: "+j.optString("name")+"\nЭтап "+(stages==null?0:stages.length())+": "+(last==null?"":last.optString("name"))+"\nТелеметрия: "+(telemetry()==null?"не получена":"подключена"));
    }
    @Override protected void onResume(){super.onResume();handler.post(ticker);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(connection);super.onDestroy();}
}