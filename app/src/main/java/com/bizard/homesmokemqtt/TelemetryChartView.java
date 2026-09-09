package com.bizard.homesmokemqtt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

final class TelemetryChartView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final HomeSmokeTheme.Palette palette;
    private List<HistoryStore.ChartPoint> points = new ArrayList<>();

    TelemetryChartView(Context c) {
        super(c);
        palette = HomeSmokeTheme.palette(c);
        p.setStrokeWidth(dp(2));
        p.setTextSize(dp(11));
        setBackgroundColor(palette.surface);
    }

    void setPoints(List<HistoryStore.ChartPoint> x) {
        points = x == null ? new ArrayList<>() : x;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float l = dp(38), r = w - dp(8), top = dp(24), bot = h - dp(30);
        p.setColor(palette.border);
        p.setStyle(Paint.Style.STROKE);
        c.drawRect(l, top, r, bot, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(palette.muted);
        c.drawText("100", dp(5), top + dp(4), p);
        c.drawText("50", dp(12), (top + bot) / 2, p);
        c.drawText("0", dp(18), bot, p);
        if (points.size() < 2) {
            c.drawText("Нет данных для графика", l + dp(10), top + dp(30), p);
            return;
        }

        int chamber = palette.danger;
        int setpoint = palette.primaryText;
        int probeK = palette.success;
        int probeT = palette.dark ? 0xFFC084FC : Color.rgb(160, 70, 180);
        drawSeries(c, l, r, top, bot, chamber, 0);
        drawSeries(c, l, r, top, bot, setpoint, 1);
        drawSeries(c, l, r, top, bot, probeK, 2);
        drawSeries(c, l, r, top, bot, probeT, 3);
        legend(c, l, chamber, "Камера", 0);
        legend(c, l, setpoint, "Уставка", 1);
        legend(c, l, probeK, "K", 2);
        legend(c, l, probeT, "T", 3);
    }

    private void drawSeries(Canvas c, float l, float r, float top, float bot, int color, int which) {
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        float px = 0, py = 0;
        boolean havePrevious = false;
        for (int i = 0; i < points.size(); i++) {
            HistoryStore.ChartPoint q = points.get(i);
            double v = which == 0 ? q.chamber : which == 1 ? q.setpoint : which == 2 ? q.k : q.t;
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                havePrevious = false;
                continue;
            }
            float x = l + (r - l) * i / (points.size() - 1f);
            float y = bot - (float) Math.max(0, Math.min(100, v)) / 100f * (bot - top);
            if (havePrevious) c.drawLine(px, py, x, y, p);
            px = x;
            py = y;
            havePrevious = true;
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void legend(Canvas c, float l, int color, String s, int i) {
        p.setColor(color);
        float x = l + i * dp(72);
        c.drawText(s, x, dp(15), p);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
