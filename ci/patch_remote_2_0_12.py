from pathlib import Path

main = Path('remote/src/main/java/com/bizard/homesmokeremote/MainActivity.java')
gradle = Path('remote/build.gradle')

text = main.read_text(encoding='utf-8')

replacements = [
    (
        'TextView versionText=text("HomeSmoke Remote 2.0.11 · Android 5+",12,false,MUTED);',
        'TextView versionText=text("HomeSmoke Remote 2.0.12 · Android 5+",12,false,MUTED);'
    ),
    (
        'subtitle.setText("HomeSmoke Remote 2.0.11");',
        'subtitle.setText("HomeSmoke Remote 2.0.12");'
    ),
    (
'''        if(!fresh||"—".equals(name)){
            mode.setText(name);
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            return;
        }
''',
'''        if(!fresh){
            mode.setText("Последний: "+name);
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            return;
        }
        if("—".equals(name)){
            mode.setText("—");
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            return;
        }
'''
    ),
    (
'''        int pct=clamp((int)Math.round(value),0,100);
        boolean heating=pct>0;
        power.setText(heating?"Нагрев · "+pct+" %":"Выкл. · 0 %");
        power.setTextColor(fresh?(heating?ORANGE:MUTED):OFF);
        heaterProgress.setProgress(pct);
        if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(fresh?(heating?ORANGE:OFF):OFF));
''',
'''        int pct=clamp((int)Math.round(value),0,100);
        if(!fresh){
            power.setText("Последнее: "+pct+" %");
            power.setTextColor(OFF);
            heaterProgress.setProgress(pct);
            if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(OFF));
            return;
        }
        boolean heating=pct>0;
        power.setText(heating?"Нагрев · "+pct+" %":"Выкл. · 0 %");
        power.setTextColor(heating?ORANGE:MUTED);
        heaterProgress.setProgress(pct);
        if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(heating?ORANGE:OFF));
'''
    ),
    (
'''        }else{
            power.setTextColor(OFF);
            if(heaterProgress!=null&&Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(OFF));
            mode.setText(modeName(lastModeRaw));
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            autoChip.setBackground(round(OFF,12));
            autoStatus.setTextColor(OFF);
        }
''',
'''        }else{
            if(Double.isNaN(lastPowerValue))power.setText("Последнее: —");
            else power.setText("Последнее: "+clamp((int)Math.round(lastPowerValue),0,100)+" %");
            power.setTextColor(OFF);
            if(heaterProgress!=null&&Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(OFF));
            mode.setText("Последний: "+modeName(lastModeRaw));
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            autoChip.setBackground(round(OFF,12));
            autoStatus.setTextColor(OFF);
        }
'''
    ),
    (
        '            tempTrend.setText("Последние данные · "+relativeAge(lastTelemetryAt));',
        '            tempTrend.setText("Состояние недоступно · данные устарели");'
    ),
]

for old, new in replacements:
    if old in text:
        text = text.replace(old, new, 1)
    elif new not in text:
        raise SystemExit(f'Expected source fragment not found:\n{old[:160]}')

main.write_text(text, encoding='utf-8')

g = gradle.read_text(encoding='utf-8')
g = g.replace('versionCode 13', 'versionCode 14')
g = g.replace("versionName '2.0.11'", "versionName '2.0.12'")
if 'versionCode 14' not in g or "versionName '2.0.12'" not in g:
    raise SystemExit('Remote version patch failed')
gradle.write_text(g, encoding='utf-8')

print('Remote 2.0.12 stale presentation patch applied')
