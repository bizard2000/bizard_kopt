package com.bizard.homesmokeremote

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lightweight Android 5+ chart for locally cached HomeSmoke telemetry. */
internal class TemperatureChartView(context: Context?) : View(context) {

    private val density: Float
    private var themePalette: RemotePalette = RemoteTheme.palette(getContext())
    private val gridPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sessionPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var data: List<TelemetryHistoryStore.Sample?>? =
        emptyList<TelemetryHistoryStore.Sample?>()
    private var showCamera: Boolean = true
    private var showSetpoint: Boolean = true
    private var showK: Boolean = true
    private var showT: Boolean = true
    private var selected: Int = -1
    private var selectionListener: OnSelectionListener? = null
    private val timeFormat: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val shortTimeFormat: SimpleDateFormat =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    internal fun interface OnSelectionListener {
        fun onSelected(sample: TelemetryHistoryStore.Sample?)
    }

    init {
        density = getResources()!!.getDisplayMetrics()!!.density
        gridPaint.setStrokeWidth(dp(1f))
        axisPaint.setTextSize(sp(10f))
        linePaint.setStyle(Paint.Style.STROKE)
        linePaint.setStrokeWidth(dp(2f))
        linePaint.setStrokeCap(Paint.Cap.ROUND)
        linePaint.setStrokeJoin(Paint.Join.ROUND)
        selectedPaint.setStrokeWidth(dp(1f))
        sessionPaint.setStrokeWidth(dp(1f))
        sessionPaint.setPathEffect(DashPathEffect(floatArrayOf(dp(3f), dp(4f)), 0f))
        applyThemeColors()
        setClickable(true)
    }

    fun setDarkTheme(darkTheme: Boolean) {
        if (themePalette.dark == darkTheme) return
        themePalette = RemoteTheme.palette(darkTheme)
        applyThemeColors()
        invalidate()
    }

    private fun applyThemeColors() {
        setBackgroundColor(themePalette.surface)
        gridPaint.setColor(GRID)
        axisPaint.setColor(MUTED)
        selectedPaint.setColor(themePalette.outline)
        sessionPaint.setColor(SESSION)
    }

    fun setData(samples: List<TelemetryHistoryStore.Sample?>?) {
        data = if (samples == null) emptyList<TelemetryHistoryStore.Sample?>() else samples
        if (selected >= data!!.size) selected = -1
        invalidate()
    }

    fun setSeries(camera: Boolean, setpoint: Boolean, probeK: Boolean, probeT: Boolean) {
        showCamera = camera
        showSetpoint = setpoint
        showK = probeK
        showT = probeT
        invalidate()
    }

    fun setOnSelectionListener(listener: OnSelectionListener?) {
        selectionListener = listener
    }

    protected override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w: Int = getWidth()
        val h: Int = getHeight()
        if (w <= 0 || h <= 0) return
        val left: Float = dp(42f)
        val right: Float = w - dp(10f)
        val top: Float = dp(26f)
        val heaterBottom: Float = h - dp(24f)
        val heaterTop: Float = h - dp(78f)
        val tempBottom: Float = heaterTop - dp(24f)
        if (data!!.isEmpty()) {
            val p: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
            p.setColor(MUTED)
            p.setTextSize(sp(14f))
            p.setTextAlign(Paint.Align.CENTER)
            canvas!!.drawText("Свежих данных за этот период нет", w / 2f, h / 2f, p)
            return
        }

        val minTs: Long = data!!.get(0)!!.ts
        var maxTs: Long = data!!.get(data!!.size - 1)!!.ts
        if (maxTs <= minTs) maxTs = minTs + 1
        val mm: DoubleArray? = tempBounds()
        var min: Double = mm!![0]
        var max: Double = mm!![1]
        if (java.lang.Double.isNaN(min) || java.lang.Double.isNaN(max)) {
            min = 0.0
            max = 100.0
        } else if (Math.abs(max - min) < 0.5) {
            min -= 2.0
            max += 2.0
        } else {
            val pad: Double = Math.max(1.0, (max - min) * 0.10)
            min -= pad
            max += pad
        }

        axisPaint.setTextAlign(Paint.Align.RIGHT)
        for (i: Int in 0..4) {
            val y: Float = top + (tempBottom - top) * i / 4f
            canvas!!.drawLine(left, y, right, y, gridPaint)
            val value: Double = max - (max - min) * i / 4.0
            canvas!!.drawText(format(value)!!, left - dp(5f), y + sp(3f), axisPaint)
        }
        axisPaint.setTextAlign(Paint.Align.LEFT)
        canvas!!.drawText("°C", dp(8f), top - sp(5f), axisPaint)

        drawSessionSeparators(canvas, minTs, maxTs, left, right, top, heaterBottom)
        drawSeries(
            canvas,
            minTs,
            maxTs,
            min,
            max,
            left,
            right,
            top,
            tempBottom,
            0,
            CAMERA,
            showCamera,
        )
        drawSeries(
            canvas,
            minTs,
            maxTs,
            min,
            max,
            left,
            right,
            top,
            tempBottom,
            1,
            SETPOINT,
            showSetpoint,
        )
        drawSeries(canvas, minTs, maxTs, min, max, left, right, top, tempBottom, 2, PROBE_K, showK)
        drawSeries(canvas, minTs, maxTs, min, max, left, right, top, tempBottom, 3, PROBE_T, showT)
        drawLegend(canvas, left, top - dp(11f))

        canvas!!.drawLine(left, heaterTop, right, heaterTop, gridPaint)
        canvas!!.drawLine(left, heaterBottom, right, heaterBottom, gridPaint)
        axisPaint.setTextAlign(Paint.Align.LEFT)
        canvas!!.drawText("ТЭН, %", dp(8f), heaterTop - dp(6f), axisPaint)
        drawHeater(canvas, minTs, maxTs, left, right, heaterTop, heaterBottom)

        val axisTime: SimpleDateFormat =
            if ((maxTs - minTs) < 10L * 60L * 1000L) shortTimeFormat else timeFormat
        for (i: Int in 0..3) {
            val x: Float = left + (right - left) * i / 3f
            val ts: Long = minTs + Math.round((maxTs - minTs) * i / 3.0)
            axisPaint.setTextAlign(
                if (i == 0) Paint.Align.LEFT
                else (if (i == 3) Paint.Align.RIGHT else Paint.Align.CENTER)
            )
            canvas!!.drawText(axisTime.format(Date(ts)), x, h - dp(7f), axisPaint)
        }
        axisPaint.setTextAlign(Paint.Align.CENTER)

        if (selected >= 0 && selected < data!!.size) {
            val s: TelemetryHistoryStore.Sample? = data!!.get(selected)
            val x: Float = xFor(s!!.ts, minTs, maxTs, left, right)
            canvas!!.drawLine(x, top, x, heaterBottom, selectedPaint)
            drawSelectionPoint(canvas, x, s!!.camera, min, max, top, tempBottom, CAMERA, showCamera)
            drawSelectionPoint(
                canvas,
                x,
                s!!.setpoint,
                min,
                max,
                top,
                tempBottom,
                SETPOINT,
                showSetpoint,
            )
            drawSelectionPoint(canvas, x, s!!.probeK, min, max, top, tempBottom, PROBE_K, showK)
            drawSelectionPoint(canvas, x, s!!.probeT, min, max, top, tempBottom, PROBE_T, showT)
        }
    }

    private fun drawSessionSeparators(
        c: Canvas?,
        minTs: Long,
        maxTs: Long,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
    ) {
        if (data!!.size < 2) return
        var previous: Int = data!!.get(0)!!.sessionId
        var transitions: Int = 0
        for (i: Int in 1 until data!!.size) if (data!!.get(i)!!.sessionId != previous) {
            transitions++
            previous = data!!.get(i)!!.sessionId
        }
        if (transitions == 0) return
        previous = data!!.get(0)!!.sessionId
        for (i: Int in 1 until data!!.size) {
            val s: TelemetryHistoryStore.Sample? = data!!.get(i)
            if (s!!.sessionId == previous) continue
            val x: Float = xFor(s!!.ts, minTs, maxTs, left, right)
            c!!.drawLine(x, top, x, bottom, sessionPaint)
            previous = s!!.sessionId
        }
    }

    private fun tempBounds(): DoubleArray? {
        var min: Double = java.lang.Double.NaN
        var max: Double = java.lang.Double.NaN
        for (s: TelemetryHistoryStore.Sample? in data!!) {
            if (showCamera) {
                val r: DoubleArray? = include(min, max, s!!.camera)
                min = r!![0]
                max = r!![1]
            }
            if (showSetpoint) {
                val r: DoubleArray? = include(min, max, s!!.setpoint)
                min = r!![0]
                max = r!![1]
            }
            if (showK) {
                val r: DoubleArray? = include(min, max, s!!.probeK)
                min = r!![0]
                max = r!![1]
            }
            if (showT) {
                val r: DoubleArray? = include(min, max, s!!.probeT)
                min = r!![0]
                max = r!![1]
            }
        }
        return doubleArrayOf(min, max)
    }

    private fun drawSeries(
        c: Canvas?,
        minTs: Long,
        maxTs: Long,
        min: Double,
        max: Double,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        field: Int,
        color: Int,
        enabled: Boolean,
    ) {
        if (!enabled) return
        val path: Path = Path()
        var started: Boolean = false
        var segment: Int = -1
        for (s: TelemetryHistoryStore.Sample? in data!!) {
            val value: Double = value(s, field)
            if (java.lang.Double.isNaN(value) || java.lang.Double.isInfinite(value)) {
                started = false
                segment = -1
                continue
            }
            val x: Float = xFor(s!!.ts, minTs, maxTs, left, right)
            val y: Float = yFor(value, min, max, top, bottom)
            if (!started || s!!.segmentId != segment) {
                path.moveTo(x, y)
                started = true
            } else path.lineTo(x, y)
            segment = s!!.segmentId
        }
        linePaint.setColor(color)
        linePaint.setStrokeWidth(dp(if (field == 1) 1.6f else 2f))
        c!!.drawPath(path, linePaint)
        drawSinglePointSegments(c, minTs, maxTs, min, max, left, right, top, bottom, field, color)
    }

    /** A one-sample segment would otherwise be invisible because Path.moveTo draws no mark. */
    private fun drawSinglePointSegments(
        c: Canvas?,
        minTs: Long,
        maxTs: Long,
        min: Double,
        max: Double,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        field: Int,
        color: Int,
    ) {
        val dot: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.setColor(color)
        dot.setStyle(Paint.Style.FILL)
        for (i: Int in data!!.indices) {
            val s: TelemetryHistoryStore.Sample? = data!!.get(i)
            val sameBefore: Boolean = i > 0 && data!!.get(i - 1)!!.segmentId == s!!.segmentId
            val sameAfter: Boolean =
                i + 1 < data!!.size && data!!.get(i + 1)!!.segmentId == s!!.segmentId
            if (sameBefore || sameAfter) continue
            val v: Double = value(s, field)
            if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v)) continue
            c!!.drawCircle(
                xFor(s!!.ts, minTs, maxTs, left, right),
                yFor(v, min, max, top, bottom),
                dp(2.2f),
                dot,
            )
        }
    }

    private fun drawHeater(
        c: Canvas?,
        minTs: Long,
        maxTs: Long,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
    ) {
        val path: Path = Path()
        var started: Boolean = false
        var segment: Int = -1
        for (s: TelemetryHistoryStore.Sample? in data!!) {
            if (java.lang.Double.isNaN(s!!.heater) || java.lang.Double.isInfinite(s!!.heater)) {
                started = false
                segment = -1
                continue
            }
            val v: Double = Math.max(0.0, Math.min(100.0, s!!.heater))
            val x: Float = xFor(s!!.ts, minTs, maxTs, left, right)
            val y: Float = (bottom - (bottom - top) * (v / 100.0)).toFloat()
            if (!started || s!!.segmentId != segment) {
                path.moveTo(x, y)
                started = true
            } else path.lineTo(x, y)
            segment = s!!.segmentId
        }
        linePaint.setColor(HEATER)
        linePaint.setStrokeWidth(dp(1.8f))
        c!!.drawPath(path, linePaint)
        drawSingleHeaterPoints(c, minTs, maxTs, left, right, top, bottom)
    }

    private fun drawSingleHeaterPoints(
        c: Canvas?,
        minTs: Long,
        maxTs: Long,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
    ) {
        val dot: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        dot.setColor(HEATER)
        dot.setStyle(Paint.Style.FILL)
        for (i: Int in data!!.indices) {
            val s: TelemetryHistoryStore.Sample? = data!!.get(i)
            val sameBefore: Boolean = i > 0 && data!!.get(i - 1)!!.segmentId == s!!.segmentId
            val sameAfter: Boolean =
                i + 1 < data!!.size && data!!.get(i + 1)!!.segmentId == s!!.segmentId
            if (
                sameBefore ||
                    sameAfter ||
                    java.lang.Double.isNaN(s!!.heater) ||
                    java.lang.Double.isInfinite(s!!.heater)
            )
                continue
            val v: Double = Math.max(0.0, Math.min(100.0, s!!.heater))
            val y: Float = (bottom - (bottom - top) * (v / 100.0)).toFloat()
            c!!.drawCircle(xFor(s!!.ts, minTs, maxTs, left, right), y, dp(2f), dot)
        }
    }

    private fun drawLegend(c: Canvas?, x: Float, y: Float) {
        val p: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        p.setTextSize(sp(9f))
        p.setColor(TEXT)
        var pos: Float = x
        if (showCamera) pos = legendItem(c, p, pos, y, CAMERA, "Камера")
        if (showSetpoint) pos = legendItem(c, p, pos, y, SETPOINT, "Уставка")
        if (showK) pos = legendItem(c, p, pos, y, PROBE_K, "K")
        if (showT) legendItem(c, p, pos, y, PROBE_T, "T")
    }

    private fun legendItem(
        c: Canvas?,
        text: Paint?,
        x: Float,
        y: Float,
        color: Int,
        label: String?,
    ): Float {
        val p: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        p.setColor(color)
        p.setStrokeWidth(dp(2f))
        c!!.drawLine(x, y, x + dp(12f), y, p)
        c!!.drawText(label!!, x + dp(16f), y + sp(3f), text!!)
        return x + dp(20f) + text!!.measureText(label) + dp(10f)
    }

    private fun drawSelectionPoint(
        c: Canvas?,
        x: Float,
        value: Double,
        min: Double,
        max: Double,
        top: Float,
        bottom: Float,
        color: Int,
        enabled: Boolean,
    ) {
        if (!enabled || java.lang.Double.isNaN(value) || java.lang.Double.isInfinite(value)) return
        val p: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
        p.setColor(color)
        p.setStyle(Paint.Style.FILL)
        c!!.drawCircle(x, yFor(value, min, max, top, bottom), dp(3f), p)
    }

    public override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (data!!.isEmpty()) return super.onTouchEvent(event)
        val action: Int = event!!.getActionMasked()
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            getParent()!!.requestDisallowInterceptTouchEvent(true)
            val left: Float = dp(42f)
            val right: Float = getWidth() - dp(10f)
            val minTs: Long = data!!.get(0)!!.ts
            var maxTs: Long = data!!.get(data!!.size - 1)!!.ts
            if (maxTs <= minTs) maxTs = minTs + 1
            val ratio: Double =
                Math.max(0f, Math.min(1f, (event!!.getX() - left) / (right - left))).toDouble()
            val target: Long = minTs + Math.round((maxTs - minTs) * ratio)
            var best: Int = 0
            var bestDiff: Long = java.lang.Long.MAX_VALUE
            for (i: Int in data!!.indices) {
                val d: Long = Math.abs(data!!.get(i)!!.ts - target)
                if (d < bestDiff) {
                    bestDiff = d
                    best = i
                }
            }
            selected = best
            invalidate()
            if (selectionListener != null) selectionListener!!.onSelected(data!!.get(best))
            return true
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            getParent()!!.requestDisallowInterceptTouchEvent(false)
            performClick()
            return true
        }
        return true
    }

    public override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dp(v: Float): Float {
        return v * density
    }

    private fun sp(v: Float): Float {
        return v * getResources()!!.getDisplayMetrics()!!.scaledDensity
    }

    private val CAMERA: Int
        get() = if (themePalette.dark) Color.rgb(126, 196, 232) else Color.rgb(32, 74, 82)
    private val SETPOINT: Int
        get() = if (themePalette.dark) Color.rgb(232, 137, 95) else Color.rgb(165, 72, 34)
    private val PROBE_K: Int
        get() = if (themePalette.dark) Color.rgb(116, 198, 157) else Color.rgb(40, 101, 76)
    private val PROBE_T: Int
        get() = if (themePalette.dark) Color.rgb(194, 157, 238) else Color.rgb(126, 87, 194)
    private val HEATER: Int get() = themePalette.orange
    private val GRID: Int get() = themePalette.outline
    private val MUTED: Int get() = themePalette.muted
    private val TEXT: Int get() = themePalette.ink
    private val SESSION: Int get() = themePalette.off

    companion object {
        private fun include(min: Double, max: Double, v: Double): DoubleArray? {
            var min = min
            var max = max
            if (java.lang.Double.isNaN(v) || java.lang.Double.isInfinite(v))
                return doubleArrayOf(min, max)
            if (java.lang.Double.isNaN(min) || v < min) min = v
            if (java.lang.Double.isNaN(max) || v > max) max = v
            return doubleArrayOf(min, max)
        }

        private fun value(s: TelemetryHistoryStore.Sample?, field: Int): Double {
            if (field == 0) return s!!.camera
            if (field == 1) return s!!.setpoint
            if (field == 2) return s!!.probeK
            return s!!.probeT
        }

        private fun xFor(ts: Long, minTs: Long, maxTs: Long, left: Float, right: Float): Float {
            return left + (right - left) * ((ts - minTs) / (maxTs - minTs).toDouble()).toFloat()
        }

        private fun yFor(
            value: Double,
            min: Double,
            max: Double,
            top: Float,
            bottom: Float,
        ): Float {
            return (bottom - (bottom - top) * ((value - min) / (max - min))).toFloat()
        }

        private fun format(v: Double): String? {
            return if (Math.abs(v - Math.rint(v)) < 0.05) (Math.rint(v).toLong()).toString()
            else String.format(Locale.getDefault(), "%.1f", v)
        }
    }
}
