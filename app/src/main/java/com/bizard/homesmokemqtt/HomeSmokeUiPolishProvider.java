package com.bizard.homesmokemqtt;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Presentation-only polish applied to dynamically created HomeSmoke views.
 *
 * MainActivity builds several widgets at runtime. This provider keeps those widgets
 * aligned with the active HomeSmoke palette without touching Bluetooth, MQTT,
 * AutoEngine, Arduino commands, telemetry or protocol behavior.
 */
public final class HomeSmokeUiPolishProvider extends ContentProvider {
    private static final int OLD_BLUE = Color.rgb(11, 103, 178);
    private static final int OLD_ORANGE = Color.rgb(239, 108, 0);

    private final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> listeners = new WeakHashMap<>();
    private final Set<View> polished = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (!(context instanceof Application)) return true;
        Application application = (Application) context;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (isHomeSmokeActivity(activity)) attach(activity);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                if (isHomeSmokeActivity(activity)) polish(activity);
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {
                detach(activity);
            }
        });
        return true;
    }

    private static boolean isHomeSmokeActivity(Activity activity) {
        return activity instanceof ModernHomeSmokeActivity || activity instanceof FieldTestActivity;
    }

    private void attach(Activity activity) {
        if (listeners.containsKey(activity)) return;
        View root = activity.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> polish(activity);
        listeners.put(activity, listener);
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        root.post(() -> polish(activity));
    }

    private void detach(Activity activity) {
        ViewTreeObserver.OnGlobalLayoutListener listener = listeners.remove(activity);
        if (listener == null) return;
        View root = activity.getWindow().getDecorView();
        if (root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private void polish(Activity activity) {
        View root = activity.getWindow().getDecorView();
        HomeSmokeTheme.Palette palette = HomeSmokeTheme.palette(activity);
        polishTree(root, palette);
    }

    private void polishTree(View view, HomeSmokeTheme.Palette palette) {
        if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            if (polished.add(view)) polishSpinnerGeometry(spinner, palette);
            polishSpinnerSelection(spinner, palette);
        } else if (view instanceof ProgressBar) {
            if (polished.add(view)) polishProgress((ProgressBar) view, palette);
        } else if (view instanceof TextView) {
            repairVersionLabel((TextView) view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                polishTree(group.getChildAt(i), palette);
            }
        }
    }

    private void polishSpinnerGeometry(Spinner spinner, HomeSmokeTheme.Palette palette) {
        spinner.setBackground(roundStroke(palette.surfaceAlt, 12, palette.border, 1, spinner));
        spinner.setPopupBackgroundDrawable(round(palette.surface, 12, spinner));
        spinner.setPadding(dp(spinner, 12), 0, dp(spinner, 10), 0);
    }

    private void polishSpinnerSelection(Spinner spinner, HomeSmokeTheme.Palette palette) {
        View selected = spinner.getSelectedView();
        if (selected instanceof TextView && polished.add(selected)) {
            TextView text = (TextView) selected;
            text.setTextColor(palette.text);
            text.setTextSize(15);
        }
    }

    private void polishProgress(ProgressBar progress, HomeSmokeTheme.Palette palette) {
        ColorStateList tint = progress.getProgressTintList();
        int current = tint == null ? OLD_BLUE : tint.getDefaultColor();
        int accent = isHeaterAccent(current, palette) ? palette.warning : palette.primary;
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(palette.surfaceAlt));
        progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
    }

    static boolean isHeaterAccent(int color, HomeSmokeTheme.Palette palette) {
        return color == OLD_ORANGE || color == palette.warning;
    }

    private void repairVersionLabel(TextView text) {
        CharSequence raw = text.getText();
        if (raw == null) return;
        String value = raw.toString();
        if (value.startsWith("HomeSmoke 2.6.") && !value.equals("HomeSmoke " + BuildConfig.VERSION_NAME)) {
            text.setText("HomeSmoke " + BuildConfig.VERSION_NAME);
        }
    }

    private static GradientDrawable round(int fill, int radiusDp, View view) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(view, radiusDp));
        return shape;
    }

    private static GradientDrawable roundStroke(int fill, int radiusDp, int stroke, int strokeDp, View view) {
        GradientDrawable shape = round(fill, radiusDp, view);
        shape.setStroke(dp(view, strokeDp), stroke);
        return shape;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
