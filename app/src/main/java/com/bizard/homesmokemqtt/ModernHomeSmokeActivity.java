package com.bizard.homesmokemqtt;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Modern presentation layer for HomeSmoke 2.6.
 *
 * The existing MainActivity remains the single source of behaviour: Bluetooth, MQTT,
 * PID commands, Auto programs and protocol interaction are untouched. This class only
 * normalizes visual styling after each programmatic screen is built.
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
        if (styled.add(view)) styleView(view);
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

        if (bg instanceof GradientDrawable && layout.getElevation() > 0f && layout.getElevation() <= dp(3)) {
            layout.setBackground(cardDrawable());
            layout.setElevation(dp(1));
            int horizontal = Math.max(layout.getPaddingLeft(), dp(14));
            layout.setPadding(horizontal, dp(12), horizontal, dp(12));
        }
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
        if ("HomeSmoke 2.6.2".equals(value)) {
            text.setText("HomeSmoke 2.6.3");
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
                lp.topMargin = dp(8);
                lp.bottomMargin = dp(3);
                text.setLayoutParams(lp);
            }
        }
    }

    private void styleEditText(EditText field) {
        field.setTextColor(TEXT);
        field.setHintTextColor(Color.rgb(148, 163, 184));
        field.setTextSize(15);
        field.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        field.setBackground(fieldDrawable());
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setMinHeight(dp(48));

        ViewGroup.LayoutParams lp = field.getLayoutParams();
        if (lp != null && lp.height > dp(48)) {
            lp.height = dp(48);
            field.setLayoutParams(lp);
        }
    }

    private void styleCheckBox(CheckBox box) {
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setTextColor(TEXT);
        box.setTextSize(14);
        box.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.setPadding(0, dp(4), dp(4), dp(4));
        box.setMinHeight(dp(44));
        box.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{PRIMARY, Color.rgb(148, 163, 184)}));
    }

    private void styleButton(Button button) {
        button.setAllCaps(false);
        button.setBackgroundTintList(null);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(14);
        button.setMinHeight(dp(46));
        button.setPadding(dp(12), 0, dp(12), 0);

        String label = button.getText() == null ? "" : button.getText().toString().trim();

        if (label.startsWith("STOP")) {
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);
            button.setMinHeight(dp(54));
            button.setBackground(rippleSolid(DANGER, 15));
            return;
        }

        if (label.equals("Ручной") || label.equals("PID") || label.equals("Auto") || label.equals("Программы")) {
            button.setTextSize(label.equals("Программы") ? 12 : 13);
            button.setMinHeight(dp(44));
            return;
        }

        if (label.equals("Запустить")) {
            solidButton(button, SUCCESS);
        } else if (label.equals("Копия")) {
            outlineButton(button, WARNING, WARNING);
        } else if (label.equals("Изменить") || label.startsWith("Экспорт") || label.startsWith("Импорт")) {
            outlineButton(button, PRIMARY, PRIMARY);
        } else if (label.contains("Удалить") || label.contains("Остановить программу")) {
            outlineButton(button, DANGER, DANGER);
        } else if (label.startsWith("Отключить")) {
            outlineButton(button, Color.rgb(203, 213, 225), MUTED);
        } else if (button.getCurrentTextColor() == Color.WHITE
                || label.startsWith("Задать")
                || label.startsWith("Выбрать")
                || label.startsWith("Применить")
                || label.startsWith("Сохранить")
                || label.startsWith("+ Новая")) {
            solidButton(button, PRIMARY);
        } else {
            outlineButton(button, BORDER, TEXT);
        }
    }

    private void solidButton(Button button, int color) {
        button.setTextColor(Color.WHITE);
        button.setBackground(rippleSolid(color, 14));
    }

    private void outlineButton(Button button, int stroke, int textColor) {
        button.setTextColor(textColor);
        button.setBackground(rippleOutline(SURFACE, stroke, 14));
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
        shape.setCornerRadius(dp(13));
        shape.setStroke(dp(1), BORDER);
        return shape;
    }

    private Drawable rippleSolid(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radiusDp));
        return new RippleDrawable(
                ColorStateList.valueOf(withAlpha(Color.WHITE, 42)),
                shape,
                null);
    }

    private Drawable rippleOutline(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        shape.setStroke(dp(1), stroke);
        return new RippleDrawable(
                ColorStateList.valueOf(withAlpha(PRIMARY, 26)),
                shape,
                null);
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

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
