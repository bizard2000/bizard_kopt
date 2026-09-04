package com.bizard.homesmokeremote;

import android.app.Activity;
import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Material 3 Expressive presentation layer for HomeSmoke Remote 2.1+.
 * Presentation only: MQTT, Arduino protocol, telemetry, Auto logic and history are untouched.
 */
public final class ExpressiveRemoteApplication extends Application {
    private static final long REFRESH_MS = 420L;

    static final int PRIMARY = Color.rgb(0, 100, 81);
    static final int ON_PRIMARY = Color.WHITE;
    static final int PRIMARY_CONTAINER = Color.rgb(191, 242, 225);
    static final int ON_PRIMARY_CONTAINER = Color.rgb(0, 33, 25);
    static final int SECONDARY_CONTAINER = Color.rgb(219, 234, 225);
    static final int TERTIARY_CONTAINER = Color.rgb(203, 234, 245);
    static final int ON_TERTIARY_CONTAINER = Color.rgb(0, 31, 39);
    static final int SURFACE = Color.rgb(248, 250, 248);
    static final int SURFACE_CONTAINER_LOW = Color.rgb(242, 246, 243);
    static final int SURFACE_CONTAINER = Color.rgb(236, 241, 238);
    static final int SURFACE_CONTAINER_HIGH = Color.rgb(229, 235, 232);
    static final int ON_SURFACE = Color.rgb(24, 29, 27);
    static final int ON_SURFACE_VARIANT = Color.rgb(65, 73, 69);
    static final int OUTLINE = Color.rgb(113, 121, 117);
    static final int OUTLINE_VARIANT = Color.rgb(193, 201, 197);

    static final int GREEN = Color.rgb(31, 143, 83);
    static final int BLUE = Color.rgb(36, 105, 178);
    static final int ORANGE = Color.rgb(201, 112, 0);
    static final int RED = Color.rgb(186, 26, 26);
    static final int OFF = Color.rgb(105, 115, 110);
    static final int WARN_CONTAINER = Color.rgb(255, 236, 205);
    static final int DISABLED_CONTROL = Color.rgb(225, 230, 227);
    static final int DISABLED_TEXT = Color.rgb(128, 137, 132);
    private static final int TOP_BAR = Color.rgb(0, 76, 62);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final WeakHashMap<Activity, Runnable> refreshers = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> animated = new WeakHashMap<>();

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                stop(activity);
                Runnable r = new Runnable() {
                    @Override public void run() {
                        apply(activity);
                        main.postDelayed(this, REFRESH_MS);
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

        styleWindow(activity);
        styleKnownCards(root);
        styleButtons(root);
        styleInputs(root);
        styleChecks(root);
        styleFilterChips(root);
        styleTypography(root);
        updateDisplayedVersion(activity, root);
        if (activity instanceof MainActivity) applyMainState(root);

        // Deliberately last. Some legacy page refinements still replace backgrounds after attach;
        // the app bar is the final visual authority and must always remain high-contrast.
        styleTopBar(root);
    }

    private void styleWindow(Activity activity) {
        activity.getWindow().setStatusBarColor(TOP_BAR);
        activity.getWindow().setNavigationBarColor(SURFACE);
        View decor = activity.getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= 23) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    private void styleTopBar(ViewGroup root) {
        View bar = findLikelyTopBar(root);
        if (!(bar instanceof ViewGroup)) return;
        bar.setAlpha(1f);
        bar.setBackground(topBarShape(bar));
        if (Build.VERSION.SDK_INT >= 21) bar.setElevation(dp(bar, 2));

        for (TextView t : directAndNestedTexts((ViewGroup) bar)) {
            String s = text(t);
            if (s.isEmpty()) continue;
            t.setTextColor(Color.WHITE);

            if (isTopBarTitle(s)) {
                t.setTextSize(17);
                t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                t.setSingleLine(true);
            } else if (isTopBarSubtitle(s)) {
                t.setTextSize(10);
            }

            if (s.equals("MQTT") || s.equals("SMOKE") || s.equals("TEST")) {
                t.setTextSize(8);
                t.setMinWidth(0);
                t.setMinimumWidth(0);
                t.setPadding(dp(t, 5), dp(t, 3), dp(t, 5), dp(t, 3));
            }

            if (t.isClickable() && isTopBarSymbol(s)) {
                t.setBackground(ripple(t, Color.argb(28, 255, 255, 255), 22, 0, 0));
                t.setGravity(Gravity.CENTER);
                ViewGroup.LayoutParams lp = t.getLayoutParams();
                if (lp != null) {
                    lp.width = dp(t, 32);
                    t.setLayoutParams(lp);
                }
            }
        }
    }

    private View findLikelyTopBar(ViewGroup content) {
        if (content.getChildCount() == 0) return null;
        View root = content.getChildAt(0);
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) root;
        if (g.getChildCount() == 0) return null;
        View first = g.getChildAt(0);
        return first instanceof ViewGroup ? first : null;
    }

    private void styleKnownCards(ViewGroup root) {
        styleCardByLabel(root, "Камера", PRIMARY_CONTAINER, 32, true);
        styleCardByLabel(root, "Щуп K", SECONDARY_CONTAINER, 28, false);
        styleCardByLabel(root, "Щуп T", SECONDARY_CONTAINER, 28, false);
        styleCardByLabel(root, "ТЭН", WARN_CONTAINER, 28, false);
        styleCardByLabel(root, "Режим", TERTIARY_CONTAINER, 28, false);

        String[] regular = new String[]{
                "Связь", "Auto", "Удалённое управление", "История команд Remote",
                "Период", "Линии графика", "Точка графика", "MQTT подключение", "Интерфейс",
                "Уведомления", "Сеансы копчения", "Журнал событий Remote", "Итог сеанса",
                "График сеанса", "События этого сеанса", "Состояние MQTT", "Телеметрия",
                "Локальная история", "Remote", "Система"
        };
        for (String label : regular) styleCardByLabel(root, label, SURFACE_CONTAINER_LOW, 26, false);
        styleCardByLabel(root, "Тестовый режим", WARN_CONTAINER, 28, false);
        styleCardByLabel(root, "ТЕСТОВЫЕ ДАННЫЕ", WARN_CONTAINER, 24, false);

        for (TextView more : findAllExact(root, "Подробнее о сеансе  ›")) {
            ViewGroup card = nearestBackgroundGroup(more, 4);
            if (card != null) styleCard(card, containsText(card, "ТЕСТ") ? WARN_CONTAINER : SURFACE_CONTAINER_LOW, 28);
        }

        for (TextView t : allTexts(root)) {
            String s = text(t);
            if (s.startsWith("Управление недоступно")) {
                t.setTextColor(Color.rgb(115, 74, 0));
                t.setBackground(ripple(t, WARN_CONTAINER, 18, Color.rgb(232, 184, 105), 1));
                t.setPadding(dp(t, 12), dp(t, 9), dp(t, 12), dp(t, 9));
            }
            if (s.startsWith("Команды ещё") || s.startsWith("Remote →") || s.startsWith("HomeSmoke →") || s.startsWith("Arduino →")) {
                t.setBackground(ripple(t, TERTIARY_CONTAINER, 18, 0, 0));
                t.setTextColor(ON_TERTIARY_CONTAINER);
            }
        }
    }

    private void styleCardByLabel(ViewGroup root, String label, int fill, int radius, boolean hero) {
        TextView title = findExact(root, label);
        if (title == null) title = findStartsWith(root, label);
        if (title == null) return;
        ViewGroup card = nearestBackgroundGroup(title, 4);
        if (card == null || card == findLikelyTopBar(root)) return;
        styleCard(card, fill, radius);
        if (hero) {
            for (TextView t : directAndNestedTexts(card)) {
                String s = text(t);
                if (s.matches("[-—0-9.,]+\\s*°C")) {
                    t.setTextSize(52);
                    t.setTextColor(ON_PRIMARY_CONTAINER);
                    t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                } else if (s.startsWith("Уставка")) {
                    t.setTextSize(15);
                    t.setTextColor(PRIMARY);
                }
            }
        }
    }

    private void styleCard(ViewGroup card, int fill, int radius) {
        card.setBackground(shape(card, fill, radius, OUTLINE_VARIANT, 0));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(card, 1));
        animateOnce(card);
    }

    private void styleButtons(ViewGroup root) {
        List<Button> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        for (Button b : buttons) {
            // CheckBox extends Button. Styling it here caused the 2.1.0 full-width green capsules.
            if (b instanceof CheckBox) continue;

            String s = text(b);
            if (s.isEmpty()) continue;
            b.setAllCaps(false);
            b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            b.setLetterSpacing(0.005f);
            b.setMinHeight(dp(b, 44));
            if (isTopBarSymbol(s)) continue;

            int fill = PRIMARY, foreground = ON_PRIMARY, stroke = 0;
            if (s.startsWith("STOP") || s.equals("Остановить")) {
                fill = RED;
            } else if (s.equals("Отключить MQTT")) {
                fill = SURFACE_CONTAINER_HIGH; foreground = ON_SURFACE; stroke = OUTLINE_VARIANT;
            } else if (s.startsWith("Экспорт CSV")) {
                fill = PRIMARY_CONTAINER; foreground = ON_PRIMARY_CONTAINER;
            } else if (s.startsWith("Экспорт JSON")) {
                fill = TERTIARY_CONTAINER; foreground = ON_TERTIARY_CONTAINER;
            } else if (s.equals("Показать") || s.equals("Скрыть")) {
                fill = SURFACE_CONTAINER_HIGH; foreground = ON_SURFACE;
            } else if (s.endsWith("▾")) {
                fill = SURFACE_CONTAINER; foreground = ON_SURFACE; stroke = OUTLINE_VARIANT;
            } else if (isGraphRange(s)) {
                boolean selected = b.getCurrentTextColor() == Color.WHITE;
                fill = selected ? PRIMARY : SURFACE_CONTAINER_HIGH;
                foreground = selected ? ON_PRIMARY : ON_SURFACE_VARIANT;
                stroke = selected ? 0 : OUTLINE_VARIANT;
            }
            if (!b.isEnabled()) {
                fill = DISABLED_CONTROL; foreground = DISABLED_TEXT; stroke = 0;
            }
            b.setTextColor(foreground);
            b.setBackground(ripple(b, fill, 22, stroke, stroke == 0 ? 0 : 1));
            if (Build.VERSION.SDK_INT >= 21) b.setElevation(0);
        }
    }

    private void styleInputs(ViewGroup root) {
        List<EditText> inputs = new ArrayList<>();
        collectEditTexts(root, inputs);
        for (EditText e : inputs) {
            boolean enabled = e.isEnabled();
            e.setTextColor(enabled ? ON_SURFACE : DISABLED_TEXT);
            e.setHintTextColor(enabled ? Color.rgb(102, 113, 107) : DISABLED_TEXT);
            e.setTextSize(15);
            e.setPadding(dp(e, 14), 0, dp(e, 14), 0);
            e.setBackground(ripple(e, enabled ? SURFACE_CONTAINER_LOW : SURFACE_CONTAINER,
                    18, enabled ? OUTLINE_VARIANT : SURFACE_CONTAINER_HIGH, 1));
        }
    }

    private void styleChecks(ViewGroup root) {
        List<CheckBox> checks = new ArrayList<>();
        collectChecks(root, checks);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked, android.R.attr.state_enabled},
                new int[]{-android.R.attr.state_checked, android.R.attr.state_enabled},
                new int[]{-android.R.attr.state_enabled}
        };
        ColorStateList tint = new ColorStateList(states, new int[]{PRIMARY, OUTLINE, OUTLINE_VARIANT});

        for (CheckBox c : checks) {
            String label = normalizedSeriesLabel(text(c));
            if (isGraphSeriesLabel(label)) {
                boolean selected = c.isChecked();
                c.setButtonDrawable(null);
                c.setGravity(Gravity.CENTER);
                c.setTextSize(12);
                c.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                c.setTextColor(selected ? ON_PRIMARY : ON_SURFACE_VARIANT);
                c.setPadding(dp(c, 10), dp(c, 7), dp(c, 10), dp(c, 7));
                c.setMinHeight(dp(c, 42));
                c.setBackground(ripple(c, selected ? PRIMARY : SURFACE_CONTAINER_HIGH,
                        20, selected ? 0 : OUTLINE_VARIANT, selected ? 0 : 1));
                continue;
            }

            c.setBackgroundColor(Color.TRANSPARENT);
            c.setTextColor(c.isEnabled() ? ON_SURFACE : DISABLED_TEXT);
            c.setTextSize(14);
            c.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setMinHeight(dp(c, 48));
            c.setPadding(0, 0, 0, 0);
            if (Build.VERSION.SDK_INT >= 21) c.setButtonTintList(tint);
        }
    }

    private void styleFilterChips(ViewGroup root) {
        String[] filters = {"Все", "Связь", "Команды", "Auto", "Температура", "Сеансы", "TEST"};
        for (String filter : filters) {
            for (TextView chip : findAllExact(root, filter)) {
                if (!chip.isClickable() || chip instanceof Button) continue;
                boolean selected = chip.getCurrentTextColor() == Color.WHITE;
                chip.setTextColor(selected ? ON_PRIMARY : ON_SURFACE_VARIANT);
                chip.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
                chip.setBackground(ripple(chip, selected ? PRIMARY : SURFACE_CONTAINER_HIGH,
                        18, selected ? 0 : OUTLINE_VARIANT, selected ? 0 : 1));
                chip.setPadding(dp(chip, 13), dp(chip, 8), dp(chip, 13), dp(chip, 8));
            }
        }
        TextView clear = findExact(root, "Очистить");
        if (clear != null && clear.isClickable()) {
            clear.setTextColor(PRIMARY);
            clear.setBackground(ripple(clear, Color.TRANSPARENT, 18, 0, 0));
        }
    }

    private void styleTypography(ViewGroup root) {
        for (TextView t : allTexts(root)) {
            String s = text(t);
            if (s.isEmpty()) continue;
            // Interactive components own their foreground color. Recoloring them here was the
            // second source of dark text on selected chips/buttons in 2.1.0.
            if (t instanceof Button || isTopBarText(t) || isClickableFilter(t, s) || isFunctionalChip(s)) continue;

            if (isSectionHeading(s)) {
                t.setTextColor(ON_SURFACE);
                t.setTextSize(18);
                t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            } else if (isSecondaryText(s, t)) {
                t.setTextColor(ON_SURFACE_VARIANT);
            }
        }
    }

    private void applyMainState(ViewGroup root) {
        boolean hasOldData = findStartsWith(root, "Обновлено ") != null
                || findStartsWith(root, "Последние данные · ") != null
                || findStartsWith(root, "Последние данные:") != null;
        boolean stale = hasOldData && (findExact(root, "Не отвечает") != null
                || findExact(root, "Данные устарели") != null
                || findExact(root, "НЕТ ДАННЫХ") != null
                || findExact(root, "СТАРЫЕ ДАННЫЕ") != null);

        styleLiveMetric(root, "Камера", stale, PRIMARY);
        styleLiveMetric(root, "Щуп K", stale, GREEN);
        styleLiveMetric(root, "Щуп T", stale, BLUE);
        styleLiveMetric(root, "ТЭН", stale, ORANGE);
        styleMode(root, stale);
        styleAuto(root, stale);
        styleTimestamp(root, stale);
        styleRemoteControls(root);
        normalizeUnavailableMetricCaptions(root);
    }

    private void normalizeUnavailableMetricCaptions(ViewGroup root) {
        TextView heaterLabel = findExact(root, "ТЭН");
        if (heaterLabel != null) {
            ViewGroup card = nearestBackgroundGroup(heaterLabel, 3);
            if (card != null) {
                for (TextView t : directAndNestedTexts(card)) {
                    if (text(t).startsWith("Последнее:")) {
                        t.setText("— %");
                        t.setTextColor(OFF);
                    }
                }
            }
        }

        TextView modeLabel = findExact(root, "Режим");
        if (modeLabel != null) {
            ViewGroup card = nearestBackgroundGroup(modeLabel, 3);
            if (card != null) {
                for (TextView t : directAndNestedTexts(card)) {
                    if (text(t).startsWith("Последний:")) {
                        t.setText("—");
                        t.setTextColor(OFF);
                    }
                }
            }
        }
    }

    private void styleLiveMetric(ViewGroup root, String label, boolean stale, int activeColor) {
        TextView title = findExact(root, label);
        if (title == null) return;
        ViewGroup card = nearestBackgroundGroup(title, 3);
        if (card == null) return;
        for (TextView t : directAndNestedTexts(card)) {
            String s = text(t);
            if (s.equals(label) || s.startsWith("Уставка") || s.startsWith("Тренд")) continue;
            if (s.contains("°C") || s.contains("%")) t.setTextColor(stale ? OFF : activeColor);
        }
    }

    private void styleMode(ViewGroup root, boolean stale) {
        TextView label = findExact(root, "Режим");
        if (label == null) return;
        ViewGroup card = nearestBackgroundGroup(label, 3);
        if (card == null) return;
        for (TextView t : directAndNestedTexts(card)) {
            String s = text(t);
            if (s.equals("Режим")) continue;
            t.setTextColor(stale ? OFF : modeColor(s));
        }
    }

    private void styleAuto(ViewGroup root, boolean stale) {
        TextView title = findExact(root, "Auto");
        if (title == null) return;
        ViewGroup card = nearestBackgroundGroup(title, 4);
        if (card == null) return;
        for (TextView t : directAndNestedTexts(card)) {
            String s = text(t);
            if (s.equals("Auto")) continue;
            if (s.equals("АКТИВНО")) {
                t.setTextColor(Color.WHITE);
                t.setBackground(ripple(t, stale ? OFF : BLUE, 16, 0, 0));
            } else if (s.equals("ВЫКЛ")) {
                t.setTextColor(Color.WHITE);
                t.setBackground(ripple(t, OFF, 16, 0, 0));
            } else if (stale) {
                t.setTextColor(OFF);
            }
        }
    }

    private void styleTimestamp(ViewGroup root, boolean stale) {
        TextView t = findStartsWith(root, "Обновлено ");
        if (t == null) t = findStartsWith(root, "Последние данные · ");
        if (t == null) t = findStartsWith(root, "Последние данные:");
        if (t == null) return;
        String s = text(t);
        if (stale && s.startsWith("Обновлено ")) t.setText("Последние данные · " + s.substring("Обновлено ".length()));
        else if (!stale && s.startsWith("Последние данные · ")) t.setText("Обновлено " + s.substring("Последние данные · ".length()));
        t.setTextColor(ON_SURFACE_VARIANT);
    }

    private void styleRemoteControls(ViewGroup root) {
        TextView availability = findStartsWith(root, "Управление ");
        boolean unavailable = availability != null && text(availability).startsWith("Управление недоступно");
        TextView apply = findExact(root, "Применить");
        TextView stop = findExact(root, "STOP · выключить нагрев");
        if (apply != null) styleControlButton(apply, unavailable, PRIMARY);
        if (stop != null) styleControlButton(stop, unavailable, RED);

        EditText setpoint = findEditTextByHint(root, "Уставка 0…100 °C");
        if (setpoint != null) {
            setpoint.setAlpha(1f);
            setpoint.setTextColor(unavailable ? DISABLED_TEXT : ON_SURFACE);
            setpoint.setHintTextColor(unavailable ? DISABLED_TEXT : ON_SURFACE_VARIANT);
            setpoint.setBackground(ripple(setpoint,
                    unavailable ? SURFACE_CONTAINER : SURFACE_CONTAINER_LOW,
                    18, unavailable ? SURFACE_CONTAINER_HIGH : OUTLINE_VARIANT, 1));
        }
    }

    private void styleControlButton(TextView button, boolean unavailable, int activeColor) {
        button.setAlpha(1f);
        button.setTextColor(unavailable ? DISABLED_TEXT : Color.WHITE);
        button.setBackground(ripple(button, unavailable ? DISABLED_CONTROL : activeColor, 22, 0, 0));
    }

    private void updateDisplayedVersion(Activity activity, ViewGroup root) {
        String version;
        try { version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (Exception e) { return; }

        for (TextView t : allTexts(root)) {
            String s = text(t);
            String replaced = s;
            if (s.contains("HomeSmoke Remote ")) {
                replaced = replaced.replaceFirst("HomeSmoke Remote \\d+\\.\\d+\\.\\d+", "HomeSmoke Remote " + version);
            }
            replaced = replaced.replace("Android 5+", "Android 6+");
            if (replaced.contains("Android 5.0/5.1:")) {
                replaced = replaced.replace(
                        "Android 5.0/5.1: защищённое хранилище этой реализации недоступно; используйте доверенную сеть/VPN.",
                        "Android 6+: пароль MQTT хранится через Android Keystore.");
            }
            if (!replaced.equals(s)) t.setText(replaced);
        }
    }

    private void animateOnce(View v) {
        if (animated.containsKey(v)) return;
        animated.put(v, Boolean.TRUE);
        v.setAlpha(0.94f);
        v.setTranslationY(dp(v, 4));
        v.animate().alpha(1f).translationY(0f).setDuration(220L)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f)).start();
    }

    private Drawable topBarShape(View v) {
        float d = v.getResources().getDisplayMetrics().density;
        ShapeAppearanceModel model = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(0).setTopRightCornerSize(0)
                .setBottomLeftCornerSize(26f * d).setBottomRightCornerSize(26f * d).build();
        MaterialShapeDrawable m = new MaterialShapeDrawable(model);
        m.setFillColor(ColorStateList.valueOf(TOP_BAR));
        return m;
    }

    private Drawable shape(View v, int fill, int radiusDp, int stroke, int strokeDp) {
        float d = v.getResources().getDisplayMetrics().density;
        ShapeAppearanceModel model = ShapeAppearanceModel.builder().setAllCornerSizes(radiusDp * d).build();
        MaterialShapeDrawable m = new MaterialShapeDrawable(model);
        m.setFillColor(ColorStateList.valueOf(fill));
        if (strokeDp > 0 && stroke != 0) m.setStroke(strokeDp * d, stroke);
        return m;
    }

    private Drawable ripple(View v, int fill, int radiusDp, int stroke, int strokeDp) {
        Drawable content = shape(v, fill, radiusDp, stroke, strokeDp);
        Drawable mask = shape(v, Color.WHITE, radiusDp, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(38, 0, 70, 57)), content, mask);
    }

    private ViewGroup nearestBackgroundGroup(View start, int maxLevels) {
        View current = start;
        for (int i = 0; i < maxLevels; i++) {
            if (!(current.getParent() instanceof ViewGroup)) return null;
            ViewGroup parent = (ViewGroup) current.getParent();
            if (parent.getBackground() != null && !(parent instanceof android.widget.ScrollView)) return parent;
            current = parent;
        }
        return null;
    }

    private static int modeColor(String s) {
        String x = s == null ? "" : s.replace("●", "").trim().toUpperCase(Locale.ROOT);
        if (x.equals("РУЧНОЙ")) return ORANGE;
        if (x.equals("PID")) return GREEN;
        if (x.equals("AUTO")) return BLUE;
        if (x.equals("STOP")) return RED;
        return ON_SURFACE;
    }

    private static boolean isTopBarTitle(String s) {
        return s.equals("HomeSmoke Remote") || s.equals("График") || s.equals("Настройки MQTT")
                || s.equals("Сеансы и журнал") || s.equals("Сеанс") || s.equals("Состояние системы");
    }

    private static boolean isTopBarSubtitle(String s) {
        return s.equals("Удалённое управление") || s.startsWith("Локальная история")
                || s.startsWith("HomeSmoke Remote ");
    }

    private static boolean isTopBarSymbol(String s) {
        return s.equals("‹") || s.equals("↗") || s.equals("⚙") || s.equals("▤") || s.equals("ⓘ");
    }

    private static boolean isGraphRange(String s) {
        return s.equals("1ч") || s.equals("3ч") || s.equals("6ч") || s.equals("12ч") || s.equals("24ч") || s.equals("Сеанс");
    }

    private static String normalizedSeriesLabel(String s) {
        return s == null ? "" : s.replace("━", "").trim();
    }

    private static boolean isGraphSeriesLabel(String s) {
        return s.equals("Камера") || s.equals("Уставка") || s.equals("Щуп K") || s.equals("Щуп T");
    }

    private static boolean isFunctionalChip(String s) {
        return s.equals("ОФЛАЙН") || s.equals("ОНЛАЙН") || s.equals("ГОТОВО") || s.equals("НЕТ ДАННЫХ")
                || s.equals("СТАРЫЕ ДАННЫЕ") || s.equals("ТЕСТ") || s.equals("АКТИВНО") || s.equals("ВЫКЛ")
                || s.equals("АКТИВЕН") || s.equals("ЗАВЕРШЁН") || s.equals("MQTT") || s.equals("SMOKE");
    }

    private static boolean isSectionHeading(String s) {
        String[] headings = {
                "Связь", "Камера", "Auto", "Удалённое управление", "История команд Remote", "Период",
                "Линии графика", "Точка графика", "MQTT подключение", "Интерфейс", "Уведомления",
                "Тестовый режим", "Сеансы копчения", "Журнал событий Remote", "Итог сеанса",
                "График сеанса", "События этого сеанса", "Состояние MQTT", "Телеметрия", "Локальная история",
                "Remote", "Система"
        };
        for (String h : headings) if (s.equals(h)) return true;
        return false;
    }

    private static boolean isSecondaryText(String s, TextView t) {
        return t.getTextSize() / t.getResources().getDisplayMetrics().scaledDensity <= 13.5f
                && !s.contains("°C") && !s.contains("%") && !s.startsWith("STOP");
    }

    private static boolean isClickableFilter(TextView t, String s) {
        if (!t.isClickable()) return false;
        return s.equals("Все") || s.equals("Связь") || s.equals("Команды") || s.equals("Auto")
                || s.equals("Температура") || s.equals("Сеансы") || s.equals("TEST") || s.equals("Очистить");
    }

    private boolean isTopBarText(TextView t) {
        View parent = t.getParent() instanceof View ? (View) t.getParent() : null;
        for (int i = 0; i < 4 && parent != null; i++) {
            ViewGroup content = t.getRootView().findViewById(android.R.id.content);
            if (content != null && findLikelyTopBar(content) == parent) return true;
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
        return false;
    }

    private static boolean containsText(View root, String target) {
        if (root instanceof TextView && target.equals(text((TextView) root))) return true;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) if (containsText(g.getChildAt(i), target)) return true;
        }
        return false;
    }

    private static TextView findExact(ViewGroup root, String target) {
        for (TextView t : allTexts(root)) if (target.equals(text(t))) return t;
        return null;
    }

    private static List<TextView> findAllExact(ViewGroup root, String target) {
        List<TextView> out = new ArrayList<>();
        for (TextView t : allTexts(root)) if (target.equals(text(t))) out.add(t);
        return out;
    }

    private static TextView findStartsWith(ViewGroup root, String prefix) {
        for (TextView t : allTexts(root)) if (text(t).startsWith(prefix)) return t;
        return null;
    }

    private static EditText findEditTextByHint(ViewGroup root, String hint) {
        List<EditText> all = new ArrayList<>();
        collectEditTexts(root, all);
        for (EditText e : all) {
            CharSequence h = e.getHint();
            if (h != null && hint.equals(h.toString())) return e;
        }
        return null;
    }

    private static List<TextView> allTexts(ViewGroup root) {
        List<TextView> out = new ArrayList<>();
        collectTexts(root, out);
        return out;
    }

    private static List<TextView> directAndNestedTexts(ViewGroup root) {
        return allTexts(root);
    }

    private static void collectTexts(View v, List<TextView> out) {
        if (v instanceof TextView) out.add((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectTexts(g.getChildAt(i), out);
        }
    }

    private static void collectButtons(View v, List<Button> out) {
        if (v instanceof Button) out.add((Button) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectButtons(g.getChildAt(i), out);
        }
    }

    private static void collectEditTexts(View v, List<EditText> out) {
        if (v instanceof EditText) out.add((EditText) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectEditTexts(g.getChildAt(i), out);
        }
    }

    private static void collectChecks(View v, List<CheckBox> out) {
        if (v instanceof CheckBox) out.add((CheckBox) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectChecks(g.getChildAt(i), out);
        }
    }

    private static String text(TextView t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }

    private static int dp(View v, int value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }
}
