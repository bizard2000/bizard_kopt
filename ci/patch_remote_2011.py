from pathlib import Path

path = Path("remote/src/main/java/com/bizard/homesmokeremote/MainActivity.java")
s = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match, found {count}: {old[:80]!r}")
    s = s.replace(old, new, 1)


replace_once(
    '    private double lastCameraValue=Double.NaN,lastSetpointValue=Double.NaN;\n',
    '    private double lastCameraValue=Double.NaN,lastSetpointValue=Double.NaN,lastPowerValue=Double.NaN;\n'
    '    private int lastTrendColor=MUTED;\n'
)

replace_once(
    '        TextView versionText=text("HomeSmoke Remote 2.0.9 · Android 5+",12,false,MUTED);',
    '        TextView versionText=text("HomeSmoke Remote 2.0.11 · Android 5+",12,false,MUTED);'
)
replace_once(
    '        subtitle.setText("HomeSmoke Remote 2.0.9");',
    '        subtitle.setText("HomeSmoke Remote 2.0.11");'
)

replace_once(
    '                power.setText(Double.isNaN(powerValue)?"— %":formatPlain(powerValue)+" %");\n'
    '                if(!Double.isNaN(powerValue))heaterProgress.setProgress(clamp((int)Math.round(powerValue),0,100));\n'
    '                else heaterProgress.setProgress(0);\n',
    '                lastPowerValue=powerValue;\n'
    '                updateHeaterUi(powerValue);\n'
)

replace_once(
'''    private void updateModeUi(String raw){
        lastModeRaw=raw;
        mode.setText(modeName(raw));
        int color=TEXT;
        if("0".equals(raw))color=ORANGE;
        else if("1".equals(raw))color=GREEN;
        else if("2".equals(raw))color=BLUE;
        else if("3".equals(raw))color=RED;
        mode.setTextColor(isTelemetryFresh()?color:OFF);
    }
''',
'''    private void updateModeUi(String raw){
        lastModeRaw=raw;
        String name=modeName(raw);
        boolean fresh=isTelemetryFresh();
        if(!fresh||"—".equals(name)){
            mode.setText(name);
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            return;
        }
        int color=OFF;
        if("0".equals(raw))color=ORANGE;
        else if("1".equals(raw))color=GREEN;
        else if("2".equals(raw))color=BLUE;
        else if("3".equals(raw))color=RED;
        mode.setText("● "+name);
        mode.setTextColor(Color.WHITE);
        mode.setPadding(dp(9),dp(3),dp(9),dp(3));
        mode.setBackground(round(color,12));
    }

    private void updateHeaterUi(double value){
        lastPowerValue=value;
        boolean fresh=isTelemetryFresh();
        if(Double.isNaN(value)){
            power.setText("—");
            power.setTextColor(fresh?MUTED:OFF);
            heaterProgress.setProgress(0);
            if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(OFF));
            return;
        }
        int pct=clamp((int)Math.round(value),0,100);
        boolean heating=pct>0;
        power.setText(heating?"Нагрев · "+pct+" %":"Выкл. · 0 %");
        power.setTextColor(fresh?(heating?ORANGE:MUTED):OFF);
        heaterProgress.setProgress(pct);
        if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(fresh?(heating?ORANGE:OFF):OFF));
    }
'''
)

replace_once(
'''        tempTrend.setTextColor(fresh?MUTED:OFF);
        power.setTextColor(fresh?ORANGE:OFF);
''',
'''        tempTrend.setTextColor(fresh?lastTrendColor:OFF);
        if(fresh)updateHeaterUi(lastPowerValue);else power.setTextColor(OFF);
'''
)

replace_once(
'''        if(tempSamples.size()<2){tempTrend.setText("Тренд накапливается");return;}
        TempSample newest=tempSamples.get(tempSamples.size()-1);
        TempSample base=null;
        for(int i=tempSamples.size()-2;i>=0;i--){
            TempSample x=tempSamples.get(i);
            if(newest.ts-x.ts>=60000L){base=x;if(newest.ts-x.ts>=TREND_WINDOW_MS)break;}
        }
        if(base==null){tempTrend.setText("Тренд накапливается");return;}
        double diff=newest.value-base.value;
        long minutes=Math.max(1,Math.round((newest.ts-base.ts)/60000.0));
        if(Math.abs(diff)<0.15)tempTrend.setText("→ стабильно · "+minutes+" мин");
        else if(diff>0)tempTrend.setText("↗ +"+oneDecimal(diff)+" °C / "+minutes+" мин");
        else tempTrend.setText("↘ −"+oneDecimal(-diff)+" °C / "+minutes+" мин");
''',
'''        if(tempSamples.size()<2){
            lastTrendColor=MUTED;
            tempTrend.setTextColor(lastTrendColor);
            tempTrend.setText("Состояние камеры · анализируется");
            return;
        }
        TempSample newest=tempSamples.get(tempSamples.size()-1);
        TempSample base=null;
        for(int i=tempSamples.size()-2;i>=0;i--){
            TempSample x=tempSamples.get(i);
            if(newest.ts-x.ts>=60000L){base=x;if(newest.ts-x.ts>=TREND_WINDOW_MS)break;}
        }
        if(base==null){
            lastTrendColor=MUTED;
            tempTrend.setTextColor(lastTrendColor);
            tempTrend.setText("Состояние камеры · анализируется");
            return;
        }
        double diff=newest.value-base.value;
        long minutes=Math.max(1,Math.round((newest.ts-base.ts)/60000.0));
        if(Math.abs(diff)<0.15){
            lastTrendColor=GREEN;
            tempTrend.setText("Стабильно · → "+minutes+" мин");
        }else if(diff>0){
            lastTrendColor=ORANGE;
            tempTrend.setText("Нагрев · ↗ +"+oneDecimal(diff)+" °C / "+minutes+" мин");
        }else{
            lastTrendColor=BLUE_DARK;
            tempTrend.setText("Остывает · ↘ −"+oneDecimal(-diff)+" °C / "+minutes+" мин");
        }
        tempTrend.setTextColor(lastTrendColor);
'''
)

# Ensure no stale version label remains in the Java source.
s = s.replace('HomeSmoke Remote 2.0.10', 'HomeSmoke Remote 2.0.11')

path.write_text(s, encoding="utf-8")
print("Remote 2.0.11 UI presentation patch applied")
