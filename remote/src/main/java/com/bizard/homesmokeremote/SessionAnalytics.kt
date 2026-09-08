package com.bizard.homesmokeremote

import java.util.ArrayList

/** Pure local calculations over already stored Remote telemetry. No protocol changes. */
internal object SessionAnalytics {
    val OUTAGE_THRESHOLD_MS: Long = 15000L
    private val SETPOINT_CHANGE_EPS: Double = 0.5

    @JvmStatic
    fun analyze(source: List<TelemetryHistoryStore.Sample?>?): Result? {
        if (source == null || source!!.isEmpty()) return Result.empty()
        val samples: ArrayList<TelemetryHistoryStore.Sample?> =
            ArrayList<TelemetryHistoryStore.Sample?>(source)
        samples.sortBy { it!!.ts }

        var outages: Int = 0
        var setpointChanges: Int = 0
        var outageMs: Long = 0L
        var validTelemetryMs: Long = 0L
        var cameraTime: Long = 0L
        var heaterTime: Long = 0L
        var errorTime: Long = 0L
        var band1Ms: Long = 0L
        var band2Ms: Long = 0L
        var band3Ms: Long = 0L
        var cameraIntegral: Double = 0.0
        var heaterIntegral: Double = 0.0
        var errorIntegral: Double = 0.0
        var maxOvershoot: Double = java.lang.Double.NaN
        var initialSetpoint: Double = java.lang.Double.NaN
        var targetStartTs: Long = 0L
        var timeToFirstTargetMs: Long = -1L
        var firstK: Double = java.lang.Double.NaN
        var lastK: Double = java.lang.Double.NaN
        var firstT: Double = java.lang.Double.NaN
        var lastT: Double = java.lang.Double.NaN
        var finiteCamera: Int = 0
        var finiteHeater: Int = 0
        var finiteError: Int = 0
        var cameraPointSum: Double = 0.0
        var heaterPointSum: Double = 0.0
        var errorPointSum: Double = 0.0

        var previous: TelemetryHistoryStore.Sample? = null
        for (s: TelemetryHistoryStore.Sample? in samples) {
            if (finite(s!!.camera)) {
                cameraPointSum += s!!.camera
                finiteCamera++
            }
            if (finite(s!!.heater)) {
                heaterPointSum += s!!.heater
                finiteHeater++
            }
            if (finite(s!!.camera) && validSetpoint(s!!.setpoint)) {
                val err: Double = Math.abs(s!!.camera - s!!.setpoint)
                errorPointSum += err
                finiteError++
                val over: Double = s!!.camera - s!!.setpoint
                if (java.lang.Double.isNaN(maxOvershoot) || over > maxOvershoot) maxOvershoot = over
            }
            if (finite(s!!.probeK)) {
                if (java.lang.Double.isNaN(firstK)) firstK = s!!.probeK
                lastK = s!!.probeK
            }
            if (finite(s!!.probeT)) {
                if (java.lang.Double.isNaN(firstT)) firstT = s!!.probeT
                lastT = s!!.probeT
            }
            if (java.lang.Double.isNaN(initialSetpoint) && validSetpoint(s!!.setpoint)) {
                initialSetpoint = s!!.setpoint
                targetStartTs = s!!.ts
            }
            if (
                timeToFirstTargetMs < 0 &&
                    validSetpoint(initialSetpoint) &&
                    finite(s!!.camera) &&
                    validSetpoint(s!!.setpoint) &&
                    Math.abs(s!!.setpoint - initialSetpoint) <= SETPOINT_CHANGE_EPS &&
                    Math.abs(s!!.camera - initialSetpoint) <= 1.0
            ) {
                timeToFirstTargetMs = Math.max(0L, s!!.ts - targetStartTs)
            }

            if (previous != null) {
                val dt: Long = s!!.ts - previous!!.ts
                if (dt > OUTAGE_THRESHOLD_MS) {
                    outages++
                    outageMs += dt
                } else if (dt > 0) {
                    validTelemetryMs += dt
                    if (finite(previous!!.camera) && finite(s!!.camera)) {
                        cameraIntegral += ((previous!!.camera + s!!.camera) / 2.0) * dt
                        cameraTime += dt
                    }
                    if (finite(previous!!.heater) && finite(s!!.heater)) {
                        heaterIntegral += ((previous!!.heater + s!!.heater) / 2.0) * dt
                        heaterTime += dt
                    }
                    val sameSetpoint: Boolean =
                        validSetpoint(previous!!.setpoint) &&
                            validSetpoint(s!!.setpoint) &&
                            Math.abs(previous!!.setpoint - s!!.setpoint) <= SETPOINT_CHANGE_EPS
                    if (sameSetpoint && finite(previous!!.camera) && finite(s!!.camera)) {
                        val e1: Double = Math.abs(previous!!.camera - previous!!.setpoint)
                        val e2: Double = Math.abs(s!!.camera - s!!.setpoint)
                        val em: Double = (e1 + e2) / 2.0
                        errorIntegral += em * dt
                        errorTime += dt
                        if (em <= 1.0) band1Ms += dt
                        if (em <= 2.0) band2Ms += dt
                        if (em <= 3.0) band3Ms += dt
                    }
                }
                if (
                    validSetpoint(previous!!.setpoint) &&
                        validSetpoint(s!!.setpoint) &&
                        Math.abs(previous!!.setpoint - s!!.setpoint) > SETPOINT_CHANGE_EPS
                )
                    setpointChanges++
            }
            previous = s
        }

        val avgCamera: Double =
            if (cameraTime > 0) cameraIntegral / cameraTime
            else (if (finiteCamera > 0) cameraPointSum / finiteCamera else java.lang.Double.NaN)
        val avgHeater: Double =
            if (heaterTime > 0) heaterIntegral / heaterTime
            else (if (finiteHeater > 0) heaterPointSum / finiteHeater else java.lang.Double.NaN)
        val avgAbsError: Double =
            if (errorTime > 0) errorIntegral / errorTime
            else (if (finiteError > 0) errorPointSum / finiteError else java.lang.Double.NaN)
        val stability1: Double =
            if (errorTime > 0) 100.0 * band1Ms / errorTime else java.lang.Double.NaN
        val stability2: Double =
            if (errorTime > 0) 100.0 * band2Ms / errorTime else java.lang.Double.NaN
        val stability3: Double =
            if (errorTime > 0) 100.0 * band3Ms / errorTime else java.lang.Double.NaN
        val probeKDelta: Double =
            if (finite(firstK) && finite(lastK)) lastK - firstK else java.lang.Double.NaN
        val probeTDelta: Double =
            if (finite(firstT) && finite(lastT)) lastT - firstT else java.lang.Double.NaN
        if (!java.lang.Double.isNaN(maxOvershoot) && maxOvershoot < 0) maxOvershoot = 0.0

        return Result(
            true,
            samples.size,
            avgCamera,
            avgHeater,
            avgAbsError,
            maxOvershoot,
            stability1,
            stability2,
            stability3,
            initialSetpoint,
            timeToFirstTargetMs,
            setpointChanges,
            probeKDelta,
            probeTDelta,
            outages,
            outageMs,
            validTelemetryMs,
            errorTime,
        )
    }

    private fun finite(v: Double): Boolean {
        return !java.lang.Double.isNaN(v) && !java.lang.Double.isInfinite(v)
    }

    private fun validSetpoint(v: Double): Boolean {
        return finite(v) && v > 0.0
    }

    internal class Result(
        @JvmField val available: Boolean,
        @JvmField val samples: Int,
        @JvmField val averageCamera: Double,
        @JvmField val averageHeater: Double,
        @JvmField val averageAbsoluteError: Double,
        @JvmField val maxOvershoot: Double,
        @JvmField val stability1: Double,
        @JvmField val stability2: Double,
        @JvmField val stability3: Double,
        @JvmField val initialSetpoint: Double,
        @JvmField val timeToFirstTargetMs: Long,
        @JvmField val setpointChanges: Int,
        @JvmField val probeKDelta: Double,
        @JvmField val probeTDelta: Double,
        @JvmField val outageEpisodes: Int,
        @JvmField val outageMs: Long,
        @JvmField val validTelemetryMs: Long,
        @JvmField val errorMetricMs: Long,
    ) {
        companion object {

            fun empty(): Result? {
                return Result(
                    false,
                    0,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    -1L,
                    0,
                    java.lang.Double.NaN,
                    java.lang.Double.NaN,
                    0,
                    0L,
                    0L,
                    0L,
                )
            }
        }
    }
}
