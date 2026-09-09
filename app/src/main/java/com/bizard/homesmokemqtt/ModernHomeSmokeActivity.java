package com.bizard.homesmokemqtt;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Modern presentation layer for HomeSmoke 2.6.
 *
 * MainActivity remains the source of behaviour. This class changes presentation only:
 * no Bluetooth/MQTT/PID/Auto/protocol behaviour is modified here.
 */
public class ModernHomeSmokeActivity extends MainActivity {
    private static final int OLD_BLUE = Color.rgb(11, 103, 178);
    private static final int OLD_BLUE_DARK = Color.rgb(8, 77, 135);
    private static final int OLD_BG = Color.rgb(243, 246, 249);
    private static final int OLD_TEXT = Color.rgb(31, 41, 55);
    private static final int OLD_MUTED = Color.rgb(103, 116, 137);
    private static final int OLD_GREEN = Color.rgb(46, 125, 50);
    private static final int OLD_RED = Color.rgb(198, 40, 40);
    private static final int OLD_ORANGE = Color.rgb(239, 108, 0);
    private static final String THEME_CARD_TAG = "homesmoke_theme_card";

    private int APP_BAR;
    private int PRIMARY;
    private int PRIMARY_DARK;
    private int BACKGROUND;
    private int SURFACE;
    private int SURFACE_ALT;
    private int BORDER;
    private int TEXT;
    private int MUTED;
    private int SUCCESS;
    private int WARNING;
    private int DANGER;
    private int DANGER_SOFT;
    private int NEUTRAL_SOFT;
    private int EDIT_SOFT;
    private int EDIT_TEXT;
    private int COPY_SOFT;
    private int COPY_TEXT;
    private int HINT;
    private int UNCHECKED;
    private boolean darkTheme;

    private final Set<View> styled = Collections.newSetFromMap(new WeakHashMap<>());
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;

    @Override
    protected void onCreate(Bundle state) {
        HomeSmokeTheme.applyActivityTheme(this);
        loadPalette();
        super.onCreate(state);
        HomeSmokeTheme.applySystemBars(this, darkTheme);

        final View root = getWindow().getDecorView();
        layoutListener = () -> styleTree(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        root.post(() -> styleTree(root));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (HomeSmokeTheme.isDark(this) != darkTheme) {
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        View root = getWindow().getDecorView();
        if (layoutListener != null && root.getViewTreeObserver().isAlive()) {
            root.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }
        styled.clear();
        super.onDestroy();
    }

    private void loadPalette() {
        HomeSmokeTheme.Palette p = HomeSmokeTheme.palette(this);
        darkTheme = p.dark;
        APP_BAR = p.appBar;
        PRIMARY = p.primary;
        PRIMARY_DARK = p.primaryText;
        BACKGROUND = p.background;
        SURFACE = p.surface;
        SURFACE_ALT = p.surfaceAlt;
        BORDER = p.border;
        TEXT = p.text;
        MUTED = p.muted;
        SUCCESS = p.success;
        WARNING = p.warning;
        DANGER = p.danger;
        DANGER_SOFT = p.dangerSoft;
        NEUTRAL_SOFT = p.neutralSoft;
        EDIT_SOFT = p.editSoft;
        EDIT_TEXT = p.editText;
        COPY_SOFT = p.copySoft;
        COPY_TEXT = p.copyText;
        HINT = p.hint;
        UNCHECKED = p.unchecked;
    }

    private void styleTree(View view) {
        if (view == null) return;

        // Geometry-affecting styling is applied only once. Later passes may repair
        // only visual state that Android/theme code can overwrite. These repairs do
        // not touch size, margins, padding, scale or translation, so they cannot
        // reintroduce the one-pixel oscillation seen on some devices.
        if (styled.add(view)) {
            styleView(view);
        } else if (view instanceof CheckBox) {
            refreshCheckBoxAppearance((CheckBox) view);
        } else if (view instanceof Button) {
            refreshDynamicButtonAppearance((Button) view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleTree(group.getChildAt(i));
            }
        }
    }

    private void styleView(View view) {
        if (view instanceof CheckBox) {
            styleCheckBox((CheckBox) view);
            return;
        }
        if (view instanceof Button) {
            styleButton((Button) view);
            return;
        }
        if (view instanceof EditText) {
            styleEditText((EditText) view);
            return;
        }
        if (view instanceof ScrollView) {
            ScrollView scroll = (ScrollView) view;
            scroll.setSaveEnabled(false);
            scroll.setBackgroundColor(BACKGROUND);
            scroll.post(() -> scroll.scrollTo(0, 0));
        }
        if (view instanceof TextView) {
            styleText((TextView) view);
        }
        if (view instanceof LinearLayout) {
            styleContainer((LinearLayout) view);
        }
    }

    private void styleContainer(LinearLayout layout) {
        Drawable bg = layout.getBackground();
        if (bg instanceof ColorDrawable) {
            int color = ((ColorDrawable) bg).getColor();
            if (color == OLD_BG) {
                layout.setBackgroundColor(BACKGROUND);
            } else if (color == OLD_BLUE || color == OLD_BLUE_DARK) {
                layout.setBackgroundColor(APP_BAR);
            } else if (color == Color.WHITE) {
                layout.setBackgroundColor(SURFACE);
            }
        }

        if (isPidEditorCard(layout)) {
            compactPidEditorCard(layout);
        }

        if (bg instanceof GradientDrawable && layout.getElevation() > 0f && layout.getElevation() <= dp(3)) {
            layout.setBackground(cardDrawable());
            layout.setElevation(dp(1));
            int horizontal = Math.max(layout.getPaddingLeft(), dp(14));
            layout.setPadding(horizontal, dp(10), horizontal, dp(10));
        }
    }

    private boolean isPidEditorCard(LinearLayout layout) {
        if (layout.getChildCount() < 4) return false;
        View first = layout.getChildAt(0);
        if (!(first instanceof TextView)) return false;
        String label = ((TextView) first).getText() == null ? "" : ((TextView) first).getText().toString().trim();
        return label.equals("kP") || label.equals("kI") || label.equals("kD") || label.equals("zP");
    }

    private void compactPidEditorCard(LinearLayout layout) {
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(12), dp(9), dp(12), dp(9));

        TextView label = (TextView) layout.getChildAt(0);
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(44)));

        View valueCaption = layout.getChildAt(1);
        valueCaption.setVisibility(View.GONE);

        View value = layout.getChildAt(2);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        vp.setMargins(dp(4), 0, dp(8), 0);
        value.setLayoutParams(vp);

        View action = layout.getChildAt(3);
        if (action instanceof Button) {
            ((Button) action).setText("Применить");
            ((Button) action).setTextSize(12);
        }
        action.setLayoutParams(new LinearLayout.LayoutParams(dp(104), dp(44)));
    }

    private void styleText(TextView text) {
        int color = text.getCurrentTextColor();
        if (color == OLD_TEXT) text.setTextColor(TEXT);
        else if (color == OLD_MUTED) text.setTextColor(MUTED);
        else if (color == OLD_BLUE || color == OLD_BLUE_DARK) text.setTextColor(PRIMARY_DARK);
        else if (color == OLD_GREEN) text.setTextColor(SUCCESS);
        else if (color == OLD_RED) text.setTextColor(DANGER);
        else if (color == OLD_ORANGE) text.setTextColor(WARNING);

        String value = text.getText() == null ? "" : text.getText().toString();
        if (value.startsWith("HomeSmoke 2.6.")) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        } else if ("История Auto пока пуста".equals(value)) {
            text.setText("История пока пуста\nПосле запуска Auto-программы здесь появятся записи и графики температуры.");
            text.setTextColor(MUTED);
            text.setTextSize(16);
            text.setLineSpacing(0f, 1.18f);
            text.setPadding(dp(20), dp(28), dp(20), dp(28));
        }

        if (isFieldLabel(value)) {
            text.setTextSize(12);
            text.setTextColor(MUTED);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            ViewGroup.LayoutParams raw = text.getLayoutParams();
            if (raw instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
                lp.topMargin = dp(5);
                lp.bottomMargin = dp(2);
                text.setLayoutParams(lp);
            }
        }

        if (value.startsWith("Bluetooth:") || value.startsWith("MQTT:") || value.startsWith("Auto:") || value.startsWith("Камера стабилизирована:")) {
            text.setTextSize(14);
            text.setPadding(0, dp(2), 0, dp(2));
        }
    }

    private void styleEditText(EditText field) {
        field.setTextColor(TEXT);
        field.setHintTextColor(HINT);
        field.setTextSize(15);
        field.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        field.setBackground(fieldDrawable());
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setMinHeight(dp(44));

        ViewGroup.LayoutParams lp = field.getLayoutParams();
        if (lp != null && lp.height > dp(44)) {
            lp.height = dp(44);
            field.setLayoutParams(lp);
        }
    }

    private void styleCheckBox(CheckBox box) {
        box.animate().cancel();
        box.setStateListAnimator(null);
        box.setTranslationX(0f);
        box.setTranslationY(0f);
        box.setScaleX(1f);
        box.setScaleY(1f);
        box.setBackground(new ColorDrawable(Color.TRANSPARENT));
        box.setBackgroundTintList(null);
        box.setElevation(0f);
        box.setTextColor(ColorStateList.valueOf(TEXT));
        box.setTextSize(14);
        box.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(0, dp(2), dp(4), dp(2));
        box.setMinWidth(0);
        box.setMinimumWidth(0);
        box.setMinHeight(dp(40));
        box.setMinimumHeight(dp(40));
        box.setSingleLine(false);
        box.setButtonTintList(checkTint());

        CharSequence label = box.getText();
        if (label != null && "Не выключать экран при открытом приложении".contentEquals(label)) {
            box.post(() -> ensureThemeCard(box));
        }
    }

    private void refreshCheckBoxAppearance(CheckBox box) {
        Drawable background = box.getBackground();
        if (!(background instanceof ColorDrawable)
                || ((ColorDrawable) background).getColor() != Color.TRANSPARENT) {
            box.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
        if (box.getBackgroundTintList() != null) box.setBackgroundTintList(null);
        if (box.getStateListAnimator() != null) box.setStateListAnimator(null);
        if (box.getElevation() != 0f) box.setElevation(0f);
        if (box.getCurrentTextColor() != TEXT) box.setTextColor(ColorStateList.valueOf(TEXT));
        box.setButtonTintList(checkTint());
    }

    private ColorStateList checkTint() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{PRIMARY, UNCHECKED});
    }

    private void ensureThemeCard(CheckBox anchor) {
        ViewParent rawParent = anchor.getParent();
        if (!(rawParent instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) rawParent;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (THEME_CARD_TAG.equals(parent.getChildAt(i).getTag())) return;
        }

        int anchorIndex = parent.indexOfChild(anchor);
        if (anchorIndex < 0) return;

        LinearLayout card = new LinearLayout(this);
        card.setTag(THEME_CARD_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(cardDrawable());
        card.setElevation(dp(1));

        TextView title = new TextView(this);
        title.setText("Оформление");
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(MUTED);
        card.addView(title);

        TextView description = new TextView(this);
        description.setText("Системная тема автоматически следует настройке Android.");
        description.setTextSize(13);
        description.setTextColor(MUTED);
        description.setPadding(0, dp(6), 0, dp(10));
        card.addView(description);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addThemeButton(row, "Системная", HomeSmokeTheme.Mode.SYSTEM);
        addThemeButton(row, "Светлая", HomeSmokeTheme.Mode.LIGHT);
        addThemeButton(row, "Тёмная", HomeSmokeTheme.Mode.DARK);
        card.addView(row, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(10), dp(5), dp(10), dp(5));
        parent.addView(card, anchorIndex, lp);
    }

    private void addThemeButton(LinearLayout row, String label, HomeSmokeTheme.Mode mode) {
        Button button = new Button(this);
        button.setText(label);
        button.setTag(mode);
        button.setOnClickListener(v -> {
            if (HomeSmokeTheme.mode(this) == mode) return;
            HomeSmokeTheme.setMode(this, mode);
            recreate();
        });
        styleThemeButton(button, mode);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, lp);
    }

    private void styleButton(Button button) {
        button.animate().cancel();
        button.setStateListAnimator(null);
        button.setTranslationX(0f);
        button.setTranslationY(0f);
        button.setScaleX(1f);
        button.setScaleY(1f);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(14);
        button.setMinHeight(dp(44));
        button.setPadding(dp(12), 0, dp(12), 0);

        Object tag = button.getTag();
        if (tag instanceof HomeSmokeTheme.Mode) {
            styleThemeButton(button, (HomeSmokeTheme.Mode) tag);
            return;
        }

        String label = normalizeDynamicLabel(button);
        String upper = label.toUpperCase(Locale.ROOT);

        if (upper.contains("STOP") && !upper.contains("ПОСЛЕ ЭТАПА")) {
            button.setTextSize(16);
            button.setMinHeight(dp(52));
            solidButton(button, DANGER, 15);
            return;
        }

        if (label.equals("Ручной") || label.equals("РУЧНОЙ") || label.equals("PID") || label.equals("AUTO")) {
            if (label.equals("Ручной")) button.setText("РУЧНОЙ");
            button.setTextSize(12);
            button.setMinHeight(dp(42));
            button.setSingleLine(true);
            button.setGravity(Gravity.CENTER);
            return;
        }

        if (label.equals("Запустить")) {
            solidButton(button, SUCCESS, 13);
        } else if (label.equals("Копия")) {
            softButton(button, COPY_SOFT, COPY_TEXT, 13);
        } else if (label.equals("Изменить") || label.startsWith("Экспорт") || label.startsWith("Импорт")) {
            softButton(button, EDIT_SOFT, EDIT_TEXT, 13);
        } else if (label.contains("Удалить")) {
            softButton(button, DANGER_SOFT, DANGER, 13);
        } else if (label.contains("Остановить программу")) {
            solidButton(button, DANGER, 13);
        } else if (label.startsWith("Отключить")) {
            softButton(button, NEUTRAL_SOFT, MUTED, 13);
        } else if (label.equals("Сохранить и подключить")) {
            solidButton(button, SUCCESS, 13);
        } else if (label.startsWith("Задать")
                || label.startsWith("Выбрать")
                || label.startsWith("Применить")
                || label.startsWith("Сохранить")
                || label.startsWith("+ Новая")) {
            solidButton(button, PRIMARY, 13);
        } else {
            softButton(button, NEUTRAL_SOFT, TEXT, 13);
        }
    }

    private void styleThemeButton(Button button, HomeSmokeTheme.Mode mode) {
        boolean selected = HomeSmokeTheme.mode(this) == mode;
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(12);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setPadding(dp(5), 0, dp(5), 0);
        if (selected) solidButton(button, PRIMARY, 12);
        else softButton(button, NEUTRAL_SOFT, TEXT, 12);
    }

    private String normalizeDynamicLabel(Button button) {
        String label = button.getText() == null ? "" : button.getText().toString().trim();
        if (label.equals("Программы") || label.equals("Auto")) {
            button.setText("AUTO");
            return "AUTO";
        }
        return label;
    }

    private void refreshDynamicButtonAppearance(Button button) {
        Object tag = button.getTag();
        if (tag instanceof HomeSmokeTheme.Mode) {
            styleThemeButton(button, (HomeSmokeTheme.Mode) tag);
            return;
        }

        String label = normalizeDynamicLabel(button);
        String upper = label.toUpperCase(Locale.ROOT);

        if (upper.contains("STOP") && !upper.contains("ПОСЛЕ ЭТАПА")) {
            repairButtonColors(button, DANGER, Color.WHITE);
        } else if (label.equals("Запустить")) {
            repairButtonColors(button, SUCCESS, Color.WHITE);
        } else if (label.equals("Копия")) {
            repairButtonColors(button, COPY_SOFT, COPY_TEXT);
        } else if (label.equals("Изменить") || label.startsWith("Экспорт") || label.startsWith("Импорт")) {
            repairButtonColors(button, EDIT_SOFT, EDIT_TEXT);
        } else if (label.contains("Удалить")) {
            repairButtonColors(button, DANGER_SOFT, DANGER);
        } else if (label.contains("Остановить программу")) {
            repairButtonColors(button, DANGER, Color.WHITE);
        } else if (label.startsWith("Отключить")) {
            repairButtonColors(button, NEUTRAL_SOFT, MUTED);
        } else if (label.equals("Сохранить и подключить")) {
            repairButtonColors(button, SUCCESS, Color.WHITE);
        } else if (label.startsWith("Задать")
                || label.startsWith("Выбрать")
                || label.startsWith("Применить")
                || label.startsWith("Сохранить")
                || label.startsWith("+ Новая")) {
            repairButtonColors(button, PRIMARY, Color.WHITE);
        }
    }

    private void repairButtonColors(Button button, int fill, int textColor) {
        ColorStateList tint = button.getBackgroundTintList();
        if (tint == null || tint.getDefaultColor() != fill) {
            button.setBackgroundTintList(ColorStateList.valueOf(fill));
        }
        if (button.getCurrentTextColor() != textColor) {
            button.setTextColor(textColor);
        }
    }

    private void solidButton(Button button, int color, int radiusDp) {
        button.setTextColor(Color.WHITE);
        button.setBackground(round(color, radiusDp));
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setElevation(dp(1));
    }

    private void softButton(Button button, int fill, int textColor, int radiusDp) {
        button.setTextColor(textColor);
        button.setBackground(round(fill, radiusDp));
        button.setBackgroundTintList(ColorStateList.valueOf(fill));
        button.setElevation(0f);
    }

    private Drawable cardDrawable() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(SURFACE);
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1), BORDER);
        return shape;
    }

    private Drawable fieldDrawable() {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(SURFACE_ALT);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(1), BORDER);
        return shape;
    }

    private Drawable round(int fill, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        return shape;
    }

    private boolean isFieldLabel(String value) {
        return value.equals("Название программы")
                || value.equals("Описание")
                || value.equals("Название этапа")
                || value.startsWith("Температура камеры")
                || value.startsWith("Допуск температуры")
                || value.startsWith("Стабилизация камеры")
                || value.startsWith("Время выдержки")
                || value.equals("Условие завершения")
                || value.startsWith("Температура продукта")
                || value.startsWith("Когда включить контроль щупа")
                || value.equals("Значение")
                || value.equals("Broker / IP")
                || value.equals("Port")
                || value.endsWith("topic")
                || value.equals("Логин")
                || value.equals("Пароль");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
