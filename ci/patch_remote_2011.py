from pathlib import Path

path = Path("remote/src/main/java/com/bizard/homesmokeremote/MainActivity.java")
s = path.read_text(encoding="utf-8")

old = '''    private void applyTelemetryFreshness(boolean fresh){
        int main=fresh?TEXT:OFF;
        int secondary=fresh?BLUE_DARK:OFF;
        float alpha=fresh?1f:STALE_ALPHA;
        if(telemetryCamCard!=null)telemetryCamCard.setAlpha(alpha);
        if(telemetryProbesRow!=null)telemetryProbesRow.setAlpha(alpha);
        if(telemetryStatsRow!=null)telemetryStatsRow.setAlpha(alpha);
        if(telemetryAutoCard!=null)telemetryAutoCard.setAlpha(alpha);
        camera.setTextColor(main);
        k.setTextColor(main);
        t.setTextColor(main);
        cameraSummary.setTextColor(secondary);
        tempTrend.setTextColor(fresh?lastTrendColor:OFF);
        if(fresh)updateHeaterUi(lastPowerValue);else power.setTextColor(OFF);
        lastCommand.setTextColor(main);
        autoProgram.setTextColor(main);
        autoStage.setTextColor(main);
        if(heaterProgress!=null){
            heaterProgress.setAlpha(fresh?1f:.72f);
            if(Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(fresh?ORANGE:OFF));
        }
        if(fresh){
            updateModeUi(lastModeRaw);
            if(lastAutoRunning){autoChip.setBackground(round(BLUE,12));autoStatus.setTextColor(BLUE_DARK);}else{autoChip.setBackground(round(OFF,12));autoStatus.setTextColor(MUTED);}
        }else{
            mode.setTextColor(OFF);
            autoChip.setBackground(round(OFF,12));
            autoStatus.setTextColor(OFF);
        }
    }
'''

new = '''    private void applyTelemetryFreshness(boolean fresh){
        int main=fresh?TEXT:OFF;
        int secondary=fresh?BLUE_DARK:OFF;
        float alpha=fresh?1f:STALE_ALPHA;
        if(telemetryCamCard!=null)telemetryCamCard.setAlpha(alpha);
        if(telemetryProbesRow!=null)telemetryProbesRow.setAlpha(alpha);
        if(telemetryStatsRow!=null)telemetryStatsRow.setAlpha(alpha);
        if(telemetryAutoCard!=null)telemetryAutoCard.setAlpha(alpha);
        camera.setTextColor(main);
        k.setTextColor(main);
        t.setTextColor(main);
        cameraSummary.setTextColor(secondary);
        tempTrend.setTextColor(fresh?lastTrendColor:OFF);
        lastCommand.setTextColor(main);
        autoProgram.setTextColor(main);
        autoStage.setTextColor(main);
        if(heaterProgress!=null)heaterProgress.setAlpha(fresh?1f:.72f);
        if(fresh){
            updateHeaterUi(lastPowerValue);
            updateModeUi(lastModeRaw);
            if(lastAutoRunning){autoChip.setBackground(round(BLUE,12));autoStatus.setTextColor(BLUE_DARK);}else{autoChip.setBackground(round(OFF,12));autoStatus.setTextColor(MUTED);}
        }else{
            power.setTextColor(OFF);
            if(heaterProgress!=null&&Build.VERSION.SDK_INT>=21)heaterProgress.setProgressTintList(ColorStateList.valueOf(OFF));
            mode.setText(modeName(lastModeRaw));
            mode.setTextColor(OFF);
            mode.setBackgroundColor(Color.TRANSPARENT);
            mode.setPadding(dp(6),dp(2),dp(6),dp(2));
            autoChip.setBackground(round(OFF,12));
            autoStatus.setTextColor(OFF);
        }
    }
'''

if old in s:
    s = s.replace(old, new, 1)
elif new not in s:
    raise SystemExit("Remote 2.0.11 freshness block is in an unexpected state")

if 'private void updateHeaterUi(double value)' not in s:
    raise SystemExit("Remote 2.0.11 presentation patch is missing")

s = s.replace('HomeSmoke Remote 2.0.9', 'HomeSmoke Remote 2.0.11')
s = s.replace('HomeSmoke Remote 2.0.10', 'HomeSmoke Remote 2.0.11')

path.write_text(s, encoding="utf-8")
print("Remote 2.0.11 presentation source is ready")
