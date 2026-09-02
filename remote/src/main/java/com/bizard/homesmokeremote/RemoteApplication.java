package com.bizard.homesmokeremote;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** Presentation-only freshness styling for retained MQTT telemetry. */
public final class RemoteApplication extends Application {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final WeakHashMap<Activity, Runnable> refreshers = new WeakHashMap<>();

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (!(activity instanceof MainActivity)) return;
                stop(activity);
                Runnable r = new Runnable() {
                    @Override public void run() {
                        apply(activity);
                        main.postDelayed(this, 500L);
                    }
                };
                refreshers.put(activity, r);
                main.post(r);
            }

            @Override public void onActivityPaused(Activity activity) { stop(activity); }
            @Override public void onActivityDestroyed(Activity activity) { stop(activity); }
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
    }

    private void stop(Activity activity) {
        Runnable r = refreshers.remove(activity);
        if (r != null) main.removeCallbacks(r);
    }

    private void apply(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) content;

        boolean hasOldData = findStartsWith(root, "Обновлено ") != null || findStartsWith(root, "Последние данные · ") != null;
        boolean stale = hasOldData && (findExact(root, "Не отвечает") != null || findExact(root, "НЕТ ДАННЫХ") != null);

        styleCard(root, "Камера", stale, Kind.CAMERA);
        styleCard(root, "Щуп K", stale, Kind.PLAIN);
        styleCard(root, "Щуп T", stale, Kind.PLAIN);
        styleCard(root, "ТЭН", stale, Kind.HEATER);
        styleCard(root, "Режим", stale, Kind.MODE);
        styleCard(root, "Последняя команда", stale, Kind.PLAIN);
        styleAuto(root, stale);
        styleTimestamp(root, stale);
        updateDisplayedVersion(activity, root);
    }

    private enum Kind { CAMERA, PLAIN, HEATER, MODE }

    private void styleCard(ViewGroup root, String label, boolean stale, Kind kind) {
        TextView title = findExact(root, label);
        if (title == null || !(title.getParent() instanceof ViewGroup)) return;
        ViewGroup card = (ViewGroup) title.getParent();
        List<TextView> texts = new ArrayList<>();
        collectTexts(card, texts);
        for (TextView t : texts) {
            String s = text(t);
            if (s.equals(label)) continue;
            if (stale) {
                t.setTextColor(OFF);
                continue;
            }
            if (kind == Kind.CAMERA && s.startsWith("Уставка")) t.setTextColor(BLUE_DARK);
            else if (kind == Kind.HEATER) t.setTextColor(ORANGE);
            else if (kind == Kind.MODE) t.setTextColor(modeColor(s));
            else t.setTextColor(TEXT);
        }
    }

    private void styleAuto(ViewGroup root, boolean stale) {
        TextView title = findExact(root, "Auto");
        if (title == null || !(title.getParent() instanceof ViewGroup)) return;
        ViewGroup header = (ViewGroup) title.getParent();
        ViewGroup card = header.getParent() instanceof ViewGroup ? (ViewGroup) header.getParent() : header;
        List<TextView> texts = new ArrayList<>();
        collectTexts(card, texts);
        for (TextView t : texts) {
            String s = text(t);
            if (s.equals("Auto")) continue;
            if (stale) {
                t.setTextColor(OFF);
                if (s.equals("АКТИВНО") || s.equals("ВЫКЛ")) {
                    t.setTextColor(Color.WHITE);
                    t.setBackground(round(OFF, 12, t));
                }
            } else if (s.equals("АКТИВНО")) {
                t.setTextColor(Color.WHITE);
                t.setBackground(round(BLUE, 12, t));
            } else if (s.equals("ВЫКЛ")) {
                t.setTextColor(Color.WHITE);
                t.setBackground(round(OFF, 12, t));
            }
        }
    }

    private void styleTimestamp(ViewGroup root, boolean stale) {
        TextView t = findStartsWith(root, "Обновлено ");
        if (t == null) t = findStartsWith(root, "Последние данные · ");
        if (t == null) return;
        String s = text(t);
        if (stale && s.startsWith("Обновлено ")) t.setText("Последние данные · " + s.substring("Обновлено ".length()));
        else if (!stale && s.startsWith("Последние данные · ")) t.setText("Обновлено " + s.substring("Последние данные · ".length()));
        t.setTextColor(OFF);
    }

    private void updateDisplayedVersion(Activity activity, ViewGroup root) {
        String version;
        try {
            version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return;
        }
        List<TextView> texts = new ArrayList<>();
        collectTexts(root, texts);
        for (TextView t : texts) {
            String s = text(t);
            if (s.contains("HomeSmoke Remote 2.0.3")) t.setText(s.replace("HomeSmoke Remote 2.0.3", "HomeSmoke Remote " + version));
        }
    }

    private static int modeColor(String s) {
        if ("Ручной".equalsIgnoreCase(s)) return ORANGE;
        if ("PID".equalsIgnoreCase(s)) return GREEN;
        if ("AUTO".equalsIgnoreCase(s)) return BLUE;
        if ("STOP".equalsIgnoreCase(s)) return RED;
        return TEXT;
    }

    private static GradientDrawable round(int color, int radiusDp, View v) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float d = v.getResources().getDisplayMetrics().density;
        g.setCornerRadius(radiusDp * d);
        return g;
    }

    private static TextView findExact(ViewGroup root, String target) {
        List<TextView> all = new ArrayList<>();
        collectTexts(root, all);
        for (TextView t : all) if (target.equals(text(t))) return t;
        return null;
    }

    private static TextView findStartsWith(ViewGroup root, String prefix) {
        List<TextView> all = new ArrayList<>();
        collectTexts(root, all);
        for (TextView t : all) if (text(t).startsWith(prefix)) return t;
        return null;
    }

    private static void collectTexts(View v, List<TextView> out) {
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectTexts(g.getChildAt(i), out);
        }
    }

    private static String text(TextView t) { return t.getText() == null ? "" : t.getText().toString().trim(); }

    private static final int BLUE = Color.rgb(31,122,210);
    private static final int BLUE_DARK = Color.rgb(26,91,164);
    private static final int TEXT = Color.rgb(21,31,47);
    private static final int GREEN = Color.rgb(35,151,83);
    private static final int RED = Color.rgb(229,40,40);
    private static final int ORANGE = Color.rgb(231,138,7);
    private static final int OFF = Color.rgb(116,129,145);
}
