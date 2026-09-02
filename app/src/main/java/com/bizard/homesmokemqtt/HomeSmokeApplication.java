package com.bizard.homesmokemqtt;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Presentation-only layer for HomeSmoke 2.6+ (Android 6+).
 * Controller protocol, Bluetooth transport, MQTT and Auto engine remain unchanged.
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
    private static final int NAVY = Color.rgb(11,31,51);
    private static final int NAVY_2 = Color.rgb(18,50,78);
    private static final int BLUE = Color.rgb(20,119,212);
    private static final int BLUE_DARK = Color.rgb(10,83,150);
    private static final int BLUE_SOFT = Color.rgb(234,245,255);
    private static final int GREEN = Color.rgb(32,142,84);
    private static final int GREEN_SOFT = Color.rgb(235,248,240);
    private static final int RED = Color.rgb(190,55,55);
    private static final int ORANGE = Color.rgb(220,125,20);
    private static final int BG = Color.rgb(244,247,250);
    private static final int CARD = Color.WHITE;
    private static final int FIELD = Color.rgb(248,250,252);
    private static final int BORDER = Color.rgb(220,226,232);
    private static final int TEXT = Color.rgb(28,35,43);
    private static final int MUTED = Color.rgb(91,103,116);

    private static final WeakHashMap<Activity, Boolean> ATTACHED = new WeakHashMap<>();
    private static final WeakHashMap<EditText, TextView> INPUT_LABELS = new WeakHashMap<>();
    private static final WeakHashMap<Spinner, TextView> SPINNER_LABELS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Button> CHAMBER_ACTIONS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, ProgressBar> HEATER_BARS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, View> MODE_CAPTIONS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, TextView> PROCESS_HEADERS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, Boolean> PROGRAM_CARDS = new WeakHashMap<>();
    private static final WeakHashMap<Spinner, Boolean> SPINNER_LISTENERS = new WeakHashMap<>();
    private static final Pattern PERCENT = Pattern.compile("(-?\\d+(?:[\\.,]\\d+)?)\\s*%");

    private UiPolish() {}

    static void attach(Activity activity) {
        if (ATTACHED.containsKey(activity)) return;
        ATTACHED.put(activity, Boolean.TRUE);
        activity.getWindow().setStatusBarColor(NAVY);
        activity.getWindow().setNavigationBarColor(BG);
        final View root = activity.getWindow().getDecorView();
        final boolean[] scheduled = {false};
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (scheduled[0]) return;
                scheduled[0] = true;
                root.postDelayed(() -> {
                    scheduled[0] = false;
                    polish(activity);
                }, 55);
            }
        });
        root.post(() -> polish(activity));
    }

    private static void polish(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;
        content.setBackgroundColor(BG);
        ViewGroup root = (ViewGroup) content;
        styleTree(activity, root);
        polishChrome(activity, root);
        polishDashboard(activity, root);
        polishProgramEditor(activity, root);
        polishProgramList(activity, root);
        polishGenericForms(activity, root);
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

    private static void polishChrome(Activity activity, ViewGroup root) {
        TextView bt = findText(root, "BT");
        TextView mqtt = findText(root, "MQTT");
        ViewGroup bar = bt != null && bt.getParent() instanceof ViewGroup ? (ViewGroup) bt.getParent() : null;
        if (bar == null && mqtt != null && mqtt.getParent() instanceof ViewGroup) bar = (ViewGroup) mqtt.getParent();
        if (bar != null) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{NAVY, NAVY_2});
            bar.setBackground(bg);
            bar.setPadding(dp(activity, 8), 0, dp(activity, 10), 0);
            for (int i=0;i<bar.getChildCount();i++) {
                View child = bar.getChildAt(i);
                if (child instanceof TextView) {
                    TextView t = (TextView) child;
                    if (!"BT".equals(textOf(t)) && !"MQTT".equals(textOf(t))) t.setTextColor(Color.WHITE);
                }
            }
        }
        styleBadge(activity, bt);
        styleBadge(activity, mqtt);
    }

    private static void styleBadge(Activity activity, TextView badge) {
        if (badge == null) return;
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 7), dp(activity, 3), dp(activity, 7), dp(activity, 3));
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

        if ("РУЧНОЙ".equalsIgnoreCase(s)) { b.setText("Ручной"); s = "Ручной"; }
        if ("AUTO".equalsIgnoreCase(s)) { b.setText("Программы"); s = "Программы"; }

        int color = BLUE;
        if (s.contains("СТОП") || s.contains("Останов") || s.contains("Удалить")) color = RED;
        else if (s.equalsIgnoreCase("PID") || s.contains("Сохранить") || s.contains("Запустить")) color = GREEN;
        else if (s.equalsIgnoreCase("Ручной") || s.contains("Копия")) color = ORANGE;
        else if (s.contains("Отключить") || s.contains("Отмена") || s.contains("Выход")) color = Color.rgb(105,116,128);

        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans", Typeface.BOLD));
        b.setBackground(round(color, dp(activity, 13), 0, Color.TRANSPARENT));
        b.setElevation(dp(activity, 2));
        b.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        b.setMinHeight(dp(activity, 48));
    }

    private static void styleEdit(Activity activity, EditText e) {
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(130,140,151));
        e.setTextSize(16);
        e.setBackground(round(FIELD, dp(activity, 12), dp(activity, 1), BORDER));
        e.setPadding(dp(activity, 14), dp(activity, 11), dp(activity, 14), dp(activity, 11));
        e.setMinHeight(dp(activity, 52));
    }

    private static void styleSpinner(Activity activity, Spinner s) {
        s.setBackground(round(FIELD, dp(activity, 12), dp(activity, 1), BORDER));
        s.setPadding(dp(activity, 9), 0, dp(activity, 9), 0);
        s.setMinimumHeight(dp(activity, 52));
    }

    private static void polishDashboard(Activity activity, ViewGroup root) {
        TextView chamberTitle = findText(root, "ТЕМПЕРАТУРА КАМЕРЫ");
        if (chamberTitle == null) return;

        if (chamberTitle.getParent() instanceof ViewGroup) {
            ViewGroup chamberCard = (ViewGroup) chamberTitle.getParent();
            GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(238,247,255), Color.WHITE});
            gradient.setCornerRadius(dp(activity, 22));
            gradient.setStroke(dp(activity, 1), Color.rgb(211,229,244));
            chamberCard.setBackground(gradient);
            chamberCard.setElevation(dp(activity, 5));
            chamberCard.setPadding(dp(activity, 18), dp(activity, 17), dp(activity, 18), dp(activity, 15));
            chamberTitle.setTextColor(BLUE_DARK);
            chamberTitle.setTextSize(12);
            chamberTitle.setTypeface(Typeface.DEFAULT_BOLD);
            chamberTitle.setLetterSpacing(0.06f);

            List<TextView> cardTexts = new ArrayList<>();
            collectTexts(chamberCard, cardTexts);
            for (TextView t : cardTexts) {
                String tx = textOf(t);
                if (tx.endsWith("°C") && !tx.startsWith("Уставка")) {
                    t.setTextColor(NAVY);
                    t.setTypeface(Typeface.create("sans", Typeface.BOLD));
                } else if (tx.startsWith("Уставка")) {
                    t.setTextColor(BLUE_DARK);
                    t.setBackground(round(BLUE_SOFT, dp(activity, 12), 0, Color.TRANSPARENT));
                    t.setPadding(dp(activity, 10), dp(activity, 6), dp(activity, 10), dp(activity, 6));
                }
            }

            if (!CHAMBER_ACTIONS.containsKey(chamberCard)) {
                Button action = new Button(activity);
                action.setText("Изменить уставку / мощность");
                action.setAllCaps(false);
                action.setTextColor(Color.WHITE);
                action.setTextSize(15);
                action.setTypeface(Typeface.DEFAULT_BOLD);
                action.setBackground(round(BLUE, dp(activity, 13), 0, Color.TRANSPARENT));
                action.setElevation(dp(activity, 2));
                action.setOnClickListener(v -> chamberCard.performClick());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 50));
                lp.setMargins(dp(activity, 8), dp(activity, 12), dp(activity, 8), dp(activity, 4));
                chamberCard.addView(action, lp);
                TextView hint = new TextView(activity);
                hint.setText("PID: температура камеры · Ручной: мощность ТЭНа");
                hint.setTextColor(MUTED);
                hint.setTextSize(12);
                hint.setGravity(Gravity.CENTER);
                hint.setPadding(dp(activity, 6), dp(activity, 2), dp(activity, 6), dp(activity, 2));
                chamberCard.addView(hint);
                CHAMBER_ACTIONS.put(chamberCard, action);
            }
        }

        styleNamedCard(activity, root, "Щуп K", BLUE_SOFT, 3, BLUE_DARK);
        styleNamedCard(activity, root, "Щуп T", GREEN_SOFT, 3, GREEN);
        styleNamedCard(activity, root, "АВТО ПРОГРАММА", Color.rgb(245,249,255), 4, BLUE_DARK);
        styleNamedCard(activity, root, "АВТОМАТИЧЕСКАЯ ПРОГРАММА", Color.rgb(245,249,255), 4, BLUE_DARK);

        TextView autoHeader = findText(root, "АВТО ПРОГРАММА");
        if (autoHeader != null) autoHeader.setText("АВТОМАТИЧЕСКАЯ ПРОГРАММА");

        polishModeControls(activity, root);
        polishProcessCard(activity, root);
    }

    private static void polishModeControls(Activity activity, ViewGroup root) {
        Button manual = findButton(root, "Ручной", "РУЧНОЙ");
        Button pid = findButton(root, "PID");
        Button programs = findButton(root, "Программы", "AUTO");
        Button stop = findButton(root, "СТОП");
        if (manual == null || pid == null || programs == null || stop == null) return;
        if (!(manual.getParent() instanceof ViewGroup)) return;
        ViewGroup row = (ViewGroup) manual.getParent();
        if (row.getParent() instanceof ViewGroup && !MODE_CAPTIONS.containsKey(row)) {
            ViewGroup parent = (ViewGroup) row.getParent();
            int index = parent.indexOfChild(row);
            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            TextView head = label(activity, "УПРАВЛЕНИЕ", 12, true, NAVY);
            head.setLetterSpacing(0.05f);
            TextView hint = label(activity, "Ручной — мощность · PID — температура · Программы — автоматический цикл", 12, false, MUTED);
            hint.setPadding(0, dp(activity, 3), 0, 0);
            box.addView(head);
            box.addView(hint);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 2));
            parent.addView(box, index, lp);
            MODE_CAPTIONS.put(row, box);
        }
        manual.setContentDescription("Ручной режим: управление мощностью ТЭНа");
        pid.setContentDescription("PID режим: управление температурой камеры");
        programs.setContentDescription("Автоматические программы копчения");
        stop.setContentDescription("Остановить нагрев и автоматическую программу");
    }

    private static void polishProcessCard(Activity activity, ViewGroup root) {
        TextView heaterLabel = findText(root, "Мощность ТЭНа");
        if (heaterLabel == null) return;
        ViewGroup processCard = processCardFor(heaterLabel);
        if (processCard == null) return;
        processCard.setBackground(round(CARD, dp(activity, 18), dp(activity, 1), Color.rgb(225,232,239)));
        processCard.setElevation(dp(activity, 3));

        if (!PROCESS_HEADERS.containsKey(processCard)) {
            TextView header = label(activity, "СОСТОЯНИЕ КОНТРОЛЛЕРА", 12, true, NAVY);
            header.setLetterSpacing(0.05f);
            header.setPadding(0, 0, 0, dp(activity, 6));
            processCard.addView(header, 0);
            PROCESS_HEADERS.put(processCard, header);
        }
        if (!HEATER_BARS.containsKey(processCard)) {
            ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressTintList(ColorStateList.valueOf(ORANGE));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(226,231,236)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(activity, 8));
            lp.setMargins(0, dp(activity, 10), 0, dp(activity, 3));
            processCard.addView(bar, lp);
            HEATER_BARS.put(processCard, bar);
        }
        ProgressBar bar = HEATER_BARS.get(processCard);
        if (bar != null) bar.setProgress(readPercent(processCard));
    }

    private static void polishProgramList(Activity activity, ViewGroup root) {
        Button anyRun = findButton(root, "▶ Запустить");
        if (anyRun == null) return;
        List<Button> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (Button b : buttons) {
            if (!"▶ Запустить".equals(textOf(b))) continue;
            ViewGroup row = b.getParent() instanceof ViewGroup ? (ViewGroup)b.getParent() : null;
            ViewGroup card = row != null && row.getParent() instanceof ViewGroup ? (ViewGroup)row.getParent() : null;
            if (card == null || PROGRAM_CARDS.containsKey(card)) continue;
            card.setBackground(round(CARD, dp(activity, 18), dp(activity, 1), Color.rgb(225,232,239)));
            card.setElevation(dp(activity, 3));
            List<TextView> texts = new ArrayList<>();
            collectTexts(card, texts);
            if (!texts.isEmpty()) {
                TextView title = texts.get(0);
                title.setTextColor(NAVY);
                title.setTypeface(Typeface.DEFAULT_BOLD);
            }
            PROGRAM_CARDS.put(card, Boolean.TRUE);
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
            stage.setBackground(round(CARD, dp(activity, 18), dp(activity, 1), Color.rgb(224,231,238)));
            stage.setElevation(dp(activity, 4));
            c.setTextColor(NAVY);
            c.setTypeface(Typeface.DEFAULT_BOLD);
            c.setTextSize(17);
            ensureSpinnerLabels(activity, stage);
            updateProbeVisibility(stage);
            localizeStagePreview(stage);
        }
    }

    private static void polishGenericForms(Activity activity, ViewGroup root) {
        if (findText(root, "Редактор программы") != null) return;
        List<EditText> edits = new ArrayList<>();
        collectEdits(root, edits);
        if (edits.size() > 8) return;
        for (EditText e : edits) ensureInputLabel(activity, e);
    }

    private static void ensureInputLabel(Activity activity, EditText e) {
        if (INPUT_LABELS.containsKey(e)) return;
        CharSequence hint = e.getHint();
        if (hint == null || hint.toString().trim().isEmpty()) return;
        if (!(e.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) e.getParent();
        int index = parent.indexOfChild(e);
        TextView label = label(activity, prettyFieldName(hint.toString()), 13, true, TEXT);
        label.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 4));
        parent.addView(label, index);
        INPUT_LABELS.put(e, label);
    }

    private static String prettyFieldName(String s) {
        if (s.startsWith("Температура камеры")) return "Температура камеры, °C";
        if (s.startsWith("Допуск")) return "Допуск температуры, ±°C";
        if (s.startsWith("Стабилизация")) return "Стабилизация камеры в диапазоне, сек";
        if (s.startsWith("Выдержка")) return "Время выдержки, мин";
        if (s.startsWith("Температура щупа")) return "Целевая температура щупа, °C";
        if ("Broker / IP".equals(s)) return "MQTT broker / IP";
        if ("Port".equals(s)) return "Порт";
        if ("Status topic".equals(s)) return "Topic телеметрии";
        if ("Command topic".equals(s)) return "Topic команд";
        if ("ACK topic".equals(s)) return "Topic подтверждений";
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
                TextView label = label(activity, i == 0 ? "Условие завершения этапа" : "Когда учитывать температуру щупа", 13, true, TEXT);
                label.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 4));
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

    private static void styleNamedCard(Activity activity, ViewGroup root, String label, int color, int elevation, int titleColor) {
        TextView t = findText(root, label);
        if (t == null || !(t.getParent() instanceof ViewGroup)) return;
        ViewGroup card = (ViewGroup) t.getParent();
        card.setBackground(round(color, dp(activity, 18), dp(activity, 1), Color.rgb(224,232,239)));
        card.setElevation(dp(activity, elevation));
        t.setTextColor(titleColor);
        t.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private static ViewGroup processCardFor(TextView heaterLabel) {
        if (!(heaterLabel.getParent() instanceof ViewGroup)) return null;
        ViewGroup row = (ViewGroup) heaterLabel.getParent();
        if (row.getParent() instanceof ViewGroup) return (ViewGroup) row.getParent();
        return row;
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

    private static TextView label(Activity activity, String text, int sp, boolean bold, int color) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
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

    private static Button findButton(ViewGroup root, String... values) {
        List<Button> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (Button b : buttons) {
            String text = textOf(b);
            for (String value : values) if (value.equals(text)) return b;
        }
        return null;
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
    private static void collectButtons(View v, List<Button> out) {
        if (v instanceof Button) out.add((Button)v);
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) collectButtons(g.getChildAt(i),out);
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
