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
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private static final int APP_BAR = Color.rgb(15, 39, 68);
    private static final int PRIMARY = Color.rgb(37, 99, 235);
    private static final int PRIMARY_DARK = Color.rgb(29, 78, 216);
    private static final int BACKGROUND = Color.rgb(245, 247, 250);
    private static final int SURFACE = Color.WHITE;
    private static final int SURFACE_ALT = Color.rgb(248, 250, 252);
    private static final int BORDER = Color.rgb(226, 232, 240);
    private static final int TEXT = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int SUCCESS = Color.rgb(22, 163, 74);
    private static final int WARNING = Color.rgb(245, 158, 11);
    private static final int DANGER = Color.rgb(220, 38, 38);
    private static final int DANGER_SOFT = Color.rgb(254, 242, 242);
    private static final int NEUTRAL_SOFT = Color.rgb(241, 245, 249);

    private final Set<View> styled = Collections.newSetFromMap(new WeakHashMap<>());
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(APP_BAR);
        getWindow().setNavigationBarColor(BACKGROUND);

        final View root = getWindow().getDecorView();
        layoutListener = () -> styleTree(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        root.post(() -> styleTree(root));
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

    private void styleTree(View view) {
        if (view == null) return;
        boolean firstPass = styled.add(view);
        if (firstPass) {
            styleView(view);
        } else if (view instanceof CheckBox) {
            // CompoundButton state changes can cause the platform theme to re-apply
            // background/tint attributes. Reassert the plain settings-row appearance.
            styleCheckBox((CheckBox) view);
        } else if (view instanceof Button) {
            refreshDynamicButton((Button) view);
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
        if ("HomeSmoke 2.6.2".equals(value)
                || "HomeSmoke 2.6.3".equals(value)
                || "HomeSmoke 2.6.4".equals(value)) {
            text.setText("HomeSmoke 2.6.5");
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
        field.setHintTextColor(Color.rgb(148, 163, 184));
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
        // A CheckBox is a CompoundButton, not an action button. Android theme tints were
        // producing a large filled button behind these controls on some devices.
        box.setBackground(null);
        box.setBackgroundTintList(null);
        box.setStateListAnimator(null);
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
        box.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{PRIMARY, Color.rgb(148, 163, 184)}));
    }

    private void refreshDynamicButton(Button button) {
        String label = button.getText() == null ? "" : button.getText().toString().trim();
        String upper = label.toUpperCase(Locale.ROOT);

        if (label.equals("Программы") || label.equals("Auto")) {
            button.setText("AUTO");
            button.setTextSize(12);
            button.setSingleLine(true);
            button.setGravity(Gravity.CENTER);
            return;
        }
        if (label.equals("Ручной")) {
            button.setText("РУЧНОЙ");
            button.setTextSize(12);
            button.setSingleLine(true);
            button.setGravity(Gravity.CENTER);
            return;
        }
        if (upper.contains("STOP") && !upper.contains("ПОСЛЕ ЭТАПА")) {
            solidButton(button, DANGER, 15);
        }
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(14);
        button.setMinHeight(dp(44));
        button.setPadding(dp(12), 0, dp(12), 0);

        String label = button.getText() == null ? "" : button.getText().toString().trim();
        String upper = label.toUpperCase(Locale.ROOT);

        if (label.equals("Программы")) {
            button.setText("AUTO");
            label = "AUTO";
            upper = "AUTO";
        } else if (label.equals("Auto")) {
            button.setText("AUTO");
            label = "AUTO";
            upper = "AUTO";
        }

        if (upper.contains("STOP") && !upper.contains("ПОСЛЕ ЭТАПА")) {
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);
            button.setMinHeight(dp(52));
            solidButton(button, DANGER, 15);
            return;
        }

        if (label.equals("Ручной") || label.equals("PID") || label.equals("AUTO")) {
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
            softButton(button, Color.rgb(255, 247, 237), Color.rgb(194, 65, 12), 13);
        } else if (label.equals("Изменить") || label.startsWith("Экспорт") || label.startsWith("Импорт")) {
            softButton(button, Color.rgb(239, 246, 255), PRIMARY_DARK, 13);
        } else if (label.contains("Удалить")) {
            softButton(button, DANGER_SOFT, DANGER, 13);
        } else if (label.contains("Остановить программу")) {
            solidButton(button, DANGER, 13);
        } else if (label.startsWith("Отключить")) {
            softButton(button, NEUTRAL_SOFT, MUTED, 13);
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
