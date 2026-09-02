package com.bizard.homesmokemqtt;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visual layer for HomeSmoke 2.6+.
 *
 * The controller protocol, Bluetooth transport and Auto engine stay in the existing service/core.
 * This class only improves presentation and discoverability of the current UI.
 */
public final class HomeSmokeApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity) UiPolish.attach(activity);
            }
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof MainActivity) UiPolish.attach(activity);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}

final class UiPolish {
    private static final int BLUE = Color.rgb(7,92,170);
    private static final int BLUE_DARK = Color.rgb(5,68,128);
    private static final int BLUE_SOFT = Color.rgb(235,245,255);
    private static final int BG = Color.rgb(239,242,246);
    private static final int CARD = Color.WHITE;
    private static final int FIELD = Color.rgb(247,249,251);
    private static final int BORDER = Color.rgb(214,220,227);
    private static final int TEXT = Color.rgb(31,36,42);
    private static final int MUTED = Color.rgb(92,101,112);
    private static final int GREEN = Color.rgb(33,145,75);
    private static final int RED = Color.rgb(185,45,45);
    private static final int ORANGE = Color.rgb(220,125,20);

    private static final WeakHashMap<Activity, Boolean> ATTACHED = new WeakHashMap<>();
    private static final WeakHashMap<EditText, TextView> INPUT_LABELS = new WeakHashMap<>();
    private static final WeakHashMap<Spinner, TextView> SPINNER_LABELS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Button> CHAMBER_ACTIONS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, ProgressBar> HEATER_BARS = new WeakHashMap<>();
    private static final WeakHashMap<Spinner, Boolean> SPINNER_LISTENERS = new WeakHashMap<>();
    private static final Pattern PERCENT = Pattern.compile("(-?\\d+(?:[\\.,]\\d+)?)\\s*%");

    private UiPolish() {}

    static void attach(Activity activity) {
        if (ATTACHED.containsKey(activity)) return;
        ATTACHED.put(activity, Boolean.TRUE);
        final View root = activity.getWindow().getDecorView();
        final boolean[] scheduled = {false};
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (scheduled[0]) return;
                scheduled[0] = true;
                root.postDelayed(() -> {
                    scheduled[0] = false;
                    polish(activity);
                }, 60);
            }
        });
        root.post(() -> polish(activity));
    }

    private static void polish(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        content.setBackgroundColor(BG);
        styleTree(activity, (ViewGroup) content);
        polishDashboard(activity, (ViewGroup) content);
        polishProgramEditor(activity, (ViewGroup) content);
        polishProgramList((ViewGroup) content);
    }

    private static void styleTree(Activity activity, ViewGroup root) {
        List<View> all = new ArrayList<>();
        collect(root, all);
        for (View v : all) {
            if (v instanceof Button) styleButton(activity, (Button) v);
            else if (v instanceof EditText) styleEdit(activity, (EditText) v);
            else if (v instanceof Spinner) styleSpinner(activity, (Spinner) v);
        }
    }

    private static void styleButton(Activity activity, Button b) {
        String s = textOf(b).trim();
        b.setAllCaps(false);
        if ("☰".equals(s) || "←".equals(s)) {
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setTextColor(Color.WHITE);
            b.setElevation(0f);
            b.setPadding(0,0,0,0);
            b.setMinWidth(0);
            b.setMinHeight(0);
            return;
        }

        int color = BLUE;
        if (s.contains("СТОП") || s.contains("Останов") || s.contains("Удалить")) color = RED;
        else if (s.equalsIgnoreCase("PID") || s.contains("Сохранить") || s.contains("Запустить")) color = GREEN;
        else if (s.contains("РУЧНОЙ") || s.contains("Копия")) color = ORANGE;
        else if (s.contains("Отключить") || s.contains("Отмена")) color = Color.rgb(112,120,130);

        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans", Typeface.BOLD));
        b.setBackground(round(color, dp(activity, 12), 0, Color.TRANSPARENT));
        b.setElevation(dp(activity, 3));
        b.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
    }

    private static void styleEdit(Activity activity, EditText e) {
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(132,139,147));
        e.setTextSize(16);
        e.setSingleLine(e.getInputType() != android.text.InputType.TYPE_CLASS_TEXT || e.getMaxLines() <= 1);
        e.setBackground(round(FIELD, dp(activity, 11), dp(activity, 1), BORDER));
        e.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11));
        e.setMinHeight(dp(activity, 50));
    }

    private static void styleSpinner(Activity activity, Spinner s) {
        s.setBackground(round(FIELD, dp(activity, 11), dp(activity, 1), BORDER));
        s.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        s.setMinimumHeight(dp(activity, 50));
    }

    private static void polishDashboard(Activity activity, ViewGroup root) {
        TextView chamberTitle = findText(root, "ТЕМПЕРАТУРА КАМЕРЫ");
        if (chamberTitle != null && chamberTitle.getParent() instanceof ViewGroup) {
            ViewGroup card = (ViewGroup) chamberTitle.getParent();
            card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(247,251,255), Color.WHITE}));
            if (card.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) card.getBackground()).setCornerRadius(dp(activity, 20));
            }
            card.setElevation(dp(activity, 7));
            chamberTitle.setTextColor(BLUE_DARK);
            chamberTitle.setTextSize(13);
            chamberTitle.setLetterSpacing(0.04f);

            if (!CHAMBER_ACTIONS.containsKey(card)) {
                Button action = new Button(activity);
                action.setText("Задать / изменить");
                action.setAllCaps(false);
                action.setTextColor(Color.WHITE);
                action.setTextSize(15);
                action.setTypeface(Typeface.DEFAULT_BOLD);
                action.setBackground(round(BLUE, dp(activity, 12), 0, Color.TRANSPARENT));
                action.setElevation(dp(activity, 2));
                action.setOnClickListener(v -> card.performClick());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 48));
                lp.setMargins(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 4));
                card.addView(action, lp);
                TextView hint = new TextView(activity);
                hint.setText("PID — уставка температуры · Ручной — мощность ТЭНа");
                hint.setTextColor(MUTED);
                hint.setTextSize(12);
                hint.setGravity(Gravity.CENTER);
                hint.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 4));
                card.addView(hint);
                CHAMBER_ACTIONS.put(card, action);
            }
        }

        styleNamedCard(activity, root, "Щуп K", Color.WHITE, 4);
        styleNamedCard(activity, root, "Щуп T", Color.WHITE, 4);
        styleNamedCard(activity, root, "Мощность ТЭНа", Color.WHITE, 4);
        styleNamedCard(activity, root, "АВТО ПРОГРАММА", BLUE_SOFT, 5);
        styleNamedCard(activity, root, "АВТОМАТИЧЕСКАЯ ПРОГРАММА", BLUE_SOFT, 5);

        TextView autoHeader = findText(root, "АВТО ПРОГРАММА");
        if (autoHeader != null) {
            autoHeader.setText("АВТОМАТИЧЕСКАЯ ПРОГРАММА");
            autoHeader.setTextColor(BLUE_DARK);
        }

        TextView heaterLabel = findText(root, "Мощность ТЭНа");
        if (heaterLabel != null) {
            ViewGroup card = ancestorLinear(heaterLabel);
            if (card != null && !HEATER_BARS.containsKey(card)) {
                ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgressTintList(ColorStateList.valueOf(ORANGE));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(225,229,234)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 8));
                lp.setMargins(0, dp(activity, 10), 0, dp(activity, 4));
                card.addView(bar, lp);
                HEATER_BARS.put(card, bar);
            }
            ViewGroup card = ancestorLinear(heaterLabel);
            ProgressBar bar = card == null ? null : HEATER_BARS.get(card);
            if (bar != null) bar.setProgress(readPercent(card));
        }
    }

    private static void polishProgramList(ViewGroup root) {
        List<TextView> titles = findTextsStarting(root, "Новая программа");
        for (TextView t : titles) {
            t.setTextColor(BLUE_DARK);
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    private static void polishProgramEditor(Activity activity, ViewGroup root) {
        TextView screenTitle = findText(root, "Редактор программы");
        if (screenTitle == null) return;

        List<EditText> edits = new ArrayList<>();
        collectEdits(root, edits);
        for (EditText e : edits) ensureInputLabel(activity, e);

        List<CheckBox> checks = new ArrayList<>();
        collectChecks(root, checks);
        for (CheckBox c : checks) {
            String tx = textOf(c);
            if (!tx.matches("Этап \\d+ включён")) continue;
            if (!(c.getParent() instanceof ViewGroup)) continue;
            ViewGroup stage = (ViewGroup) c.getParent();
            stage.setBackground(round(CARD, dp(activity, 18), dp(activity, 1), Color.rgb(226,231,237)));
            stage.setElevation(dp(activity, 5));
            c.setTextColor(BLUE_DARK);
            c.setTypeface(Typeface.DEFAULT_BOLD);
            c.setTextSize(17);
            ensureSpinnerLabels(activity, stage);
            updateProbeVisibility(stage);
            localizeStagePreview(stage);
        }
    }

    private static void ensureInputLabel(Activity activity, EditText e) {
        if (INPUT_LABELS.containsKey(e)) return;
        CharSequence hint = e.getHint();
        if (hint == null || hint.toString().trim().isEmpty()) return;
        if (!(e.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) e.getParent();
        int index = parent.indexOfChild(e);
        TextView label = new TextView(activity);
        label.setText(prettyFieldName(hint.toString()));
        label.setTextColor(TEXT);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 4));
        parent.addView(label, index);
        INPUT_LABELS.put(e, label);
    }

    private static String prettyFieldName(String s) {
        if (s.startsWith("Температура камеры")) return "Температура камеры, °C";
        if (s.startsWith("Допуск")) return "Допуск температуры, ±°C";
        if (s.startsWith("Стабилизация")) return "Стабилизация камеры в диапазоне, сек";
        if (s.startsWith("Выдержка")) return "Время выдержки, мин";
        if (s.startsWith("Температура щупа")) return "Целевая температура щупа, °C";
        return s;
    }

    private static void ensureSpinnerLabels(Activity activity, ViewGroup stage) {
        List<Spinner> spinners = new ArrayList<>();
        collectSpinners(stage, spinners);
        for (int i = 0; i < spinners.size(); i++) {
            Spinner spinner = spinners.get(i);
            if (!SPINNER_LABELS.containsKey(spinner) && spinner.getParent() instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) spinner.getParent();
                int index = parent.indexOfChild(spinner);
                TextView label = new TextView(activity);
                label.setText(i == 0 ? "Условие завершения этапа" : "Когда учитывать температуру щупа");
                label.setTextColor(TEXT);
                label.setTextSize(13);
                label.setTypeface(Typeface.DEFAULT_BOLD);
                label.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 4));
                parent.addView(label, index);
                SPINNER_LABELS.put(spinner, label);
            }
            if (i == 0 && !SPINNER_LISTENERS.containsKey(spinner)) {
                SPINNER_LISTENERS.put(spinner, Boolean.TRUE);
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        stage.post(() -> updateProbeVisibility(stage));
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            }
        }
    }

    private static void updateProbeVisibility(ViewGroup stage) {
        List<Spinner> spinners = new ArrayList<>();
        collectSpinners(stage, spinners);
        if (spinners.isEmpty()) return;
        Spinner condition = spinners.get(0);
        Object item = condition.getSelectedItem();
        boolean usesProbe = item != null && !"Только время".equals(item.toString());

        EditText probe = findEditByHint(stage, "Температура щупа");
        if (probe != null) {
            probe.setVisibility(usesProbe ? View.VISIBLE : View.GONE);
            TextView label = INPUT_LABELS.get(probe);
            if (label != null) label.setVisibility(usesProbe ? View.VISIBLE : View.GONE);
        }
        if (spinners.size() > 1) {
            Spinner activation = spinners.get(1);
            activation.setVisibility(usesProbe ? View.VISIBLE : View.GONE);
            TextView label = SPINNER_LABELS.get(activation);
            if (label != null) label.setVisibility(usesProbe ? View.VISIBLE : View.GONE);
        }
    }

    private static void localizeStagePreview(ViewGroup stage) {
        List<TextView> texts = new ArrayList<>();
        collectTexts(stage, texts);
        for (TextView t : texts) {
            String s = textOf(t);
            if (!s.startsWith("Камера ")) continue;
            s = s.replace("TIME_AND_K", "время И щуп K")
                    .replace("TIME_AND_T", "время И щуп T")
                    .replace("TIME_OR_K", "время ИЛИ щуп K")
                    .replace("TIME_OR_T", "время ИЛИ щуп T")
                    .replace("PROBE_K", "щуп K")
                    .replace("PROBE_T", "щуп T")
                    .replace("TIME", "только время")
                    .replace("AFTER_CHAMBER_READY", "после стабилизации камеры")
                    .replace("IMMEDIATE", "сразу");
            t.setText(s);
            t.setTextColor(MUTED);
            t.setTextSize(12);
        }
    }

    private static void styleNamedCard(Activity activity, ViewGroup root, String label, int color, int elevation) {
        TextView t = findText(root, label);
        if (t == null || !(t.getParent() instanceof ViewGroup)) return;
        ViewGroup card = (ViewGroup) t.getParent();
        card.setBackground(round(color, dp(activity, 18), dp(activity, 1), Color.rgb(230,234,239)));
        card.setElevation(dp(activity, elevation));
    }

    private static ViewGroup ancestorLinear(View v) {
        View p = v;
        for (int i = 0; i < 3 && p != null; i++) {
            if (p.getParent() instanceof LinearLayout) return (LinearLayout) p.getParent();
            if (p.getParent() instanceof View) p = (View) p.getParent(); else break;
        }
        return null;
    }

    private static int readPercent(ViewGroup root) {
        List<TextView> texts = new ArrayList<>();
        collectTexts(root, texts);
        for (TextView t : texts) {
            Matcher m = PERCENT.matcher(textOf(t).replace(',', '.'));
            if (m.find()) {
                try {
                    int p = (int)Math.round(Double.parseDouble(m.group(1)));
                    return Math.max(0, Math.min(100, p));
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static GradientDrawable round(int color, float radius, int strokeWidth, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    private static int dp(Activity a, int x) {
        return Math.round(x * a.getResources().getDisplayMetrics().density);
    }

    private static String textOf(TextView t) {
        CharSequence c = t.getText();
        return c == null ? "" : c.toString();
    }

    private static TextView findText(ViewGroup root, String exact) {
        List<TextView> texts = new ArrayList<>();
        collectTexts(root, texts);
        for (TextView t : texts) if (exact.equals(textOf(t))) return t;
        return null;
    }

    private static List<TextView> findTextsStarting(ViewGroup root, String prefix) {
        List<TextView> texts = new ArrayList<>();
        collectTexts(root, texts);
        List<TextView> result = new ArrayList<>();
        for (TextView t : texts) if (textOf(t).startsWith(prefix)) result.add(t);
        return result;
    }

    private static EditText findEditByHint(ViewGroup root, String prefix) {
        List<EditText> edits = new ArrayList<>();
        collectEdits(root, edits);
        for (EditText e : edits) {
            CharSequence h = e.getHint();
            if (h != null && h.toString().startsWith(prefix)) return e;
        }
        return null;
    }

    private static void collect(View v, List<View> out) {
        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0;i<g.getChildCount();i++) collect(g.getChildAt(i), out);
        }
    }
    private static void collectTexts(View v, List<TextView> out) {
        if (v instanceof TextView) out.add((TextView)v);
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collectTexts(g.getChildAt(i),out);
        }
    }
    private static void collectEdits(View v, List<EditText> out) {
        if (v instanceof EditText) out.add((EditText)v);
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collectEdits(g.getChildAt(i),out);
        }
    }
    private static void collectChecks(View v, List<CheckBox> out) {
        if (v instanceof CheckBox) out.add((CheckBox)v);
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collectChecks(g.getChildAt(i),out);
        }
    }
    private static void collectSpinners(View v, List<Spinner> out) {
        if (v instanceof Spinner) out.add((Spinner)v);
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collectSpinners(g.getChildAt(i),out);
        }
    }
}
