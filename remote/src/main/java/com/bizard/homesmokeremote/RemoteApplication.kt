package com.bizard.homesmokeremote

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.util.ArrayList

/** Presentation-only freshness and availability styling for HomeSmoke Remote. */
class RemoteApplication : Application() {
    private val main: Handler = Handler(Looper.getMainLooper())
    private val refreshers: java.util.WeakHashMap<Activity?, Runnable?> =
        java.util.WeakHashMap<Activity?, Runnable?>()

    public override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                public override fun onActivityResumed(activity: Activity) {
                    if (!(activity is MainActivity)) return
                    stop(activity)
                    val r: Runnable =
                        object : Runnable {
                            public override fun run() {
                                apply(activity)
                                main.postDelayed(this, 500L)
                            }
                        }
                    refreshers.put(activity, r)
                    main.post(r)
                }

                public override fun onActivityPaused(activity: Activity) {
                    stop(activity)
                }

                public override fun onActivityDestroyed(activity: Activity) {
                    stop(activity)
                }

                public override fun onActivityCreated(activity: Activity, state: Bundle?) {}

                public override fun onActivityStarted(activity: Activity) {}

                public override fun onActivityStopped(activity: Activity) {}

                public override fun onActivitySaveInstanceState(
                    activity: Activity,
                    state: Bundle,
                ) {}
            }
        )
    }

    private fun stop(activity: Activity?) {
        val r: Runnable? = refreshers.remove(activity)
        if (r != null) main.removeCallbacks(r)
    }

    private fun apply(activity: Activity?) {
        val content: View? = activity!!.findViewById<View?>(android.R.id.content)
        if (!(content is ViewGroup)) return
        val root: ViewGroup? = content as ViewGroup?

        val hasOldData: Boolean =
            (findStartsWith(root, "Обновлено ") != null ||
                findStartsWith(root, "Последние данные · ") != null ||
                findStartsWith(root, "Последние данные:") != null)
        val stale: Boolean =
            (hasOldData &&
                ((findExact(root, "Не отвечает") != null ||
                    findExact(root, "Данные устарели") != null ||
                    findExact(root, "НЕТ ДАННЫХ") != null ||
                    findExact(root, "СТАРЫЕ ДАННЫЕ") != null)))

        styleCard(root, "Камера", stale, Kind.CAMERA)
        styleCard(root, "Щуп K", stale, Kind.PLAIN)
        styleCard(root, "Щуп T", stale, Kind.PLAIN)
        styleCard(root, "ТЭН", stale, Kind.HEATER)
        styleCard(root, "Режим", stale, Kind.MODE)
        styleCard(root, "Последняя команда", stale, Kind.PLAIN)
        styleAuto(root, stale)
        styleTimestamp(root, stale)
        styleRemoteControls(root)
        normalizeUnavailableMetricCaptions(root, stale)
        updateDisplayedVersion(activity, root)
    }

    private enum class Kind {
        CAMERA,
        PLAIN,
        HEATER,
        MODE,
    }

    private fun styleCard(root: ViewGroup?, label: String?, stale: Boolean, kind: Kind?) {
        val title: TextView? = findExact(root, label)
        if (title == null || !(title!!.getParent() is ViewGroup)) return
        val card: ViewGroup? = title!!.getParent() as ViewGroup?
        val texts: MutableList<TextView?> = ArrayList<TextView?>()
        collectTexts(card, texts)
        for (t: TextView? in texts) {
            val s: String? = text(t)
            if (s == label) continue
            if (stale) {
                t!!.setTextColor(OFF)
                continue
            }
            if (kind == Kind.CAMERA && s!!.startsWith("Уставка")) t!!.setTextColor(BLUE_DARK)
            else if (kind == Kind.HEATER) t!!.setTextColor(ORANGE)
            else if (kind == Kind.MODE) t!!.setTextColor(modeColor(s)) else t!!.setTextColor(TEXT)
        }
    }

    private fun styleAuto(root: ViewGroup?, stale: Boolean) {
        val title: TextView? = findExact(root, "Auto")
        if (title == null || !(title!!.getParent() is ViewGroup)) return
        val header: ViewGroup? = title!!.getParent() as ViewGroup?
        val card: ViewGroup? =
            if (header!!.getParent() is ViewGroup) header!!.getParent() as ViewGroup? else header
        val texts: MutableList<TextView?> = ArrayList<TextView?>()
        collectTexts(card, texts)
        for (t: TextView? in texts) {
            val s: String? = text(t)
            if (s == "Auto") continue
            if (stale) {
                t!!.setTextColor(OFF)
                if (s == "АКТИВНО" || s == "ВЫКЛ") {
                    t!!.setTextColor(Color.WHITE)
                    t!!.setBackground(round(OFF, 12, t))
                }
            } else if (s == "АКТИВНО") {
                t!!.setTextColor(Color.WHITE)
                t!!.setBackground(round(BLUE, 12, t))
            } else if (s == "ВЫКЛ") {
                t!!.setTextColor(Color.WHITE)
                t!!.setBackground(round(OFF, 12, t))
            }
        }
    }

    private fun styleTimestamp(root: ViewGroup?, stale: Boolean) {
        var t: TextView? = findStartsWith(root, "Обновлено ")
        if (t == null) t = findStartsWith(root, "Последние данные · ")
        if (t == null) t = findStartsWith(root, "Последние данные:")
        if (t == null) return
        val s: String? = text(t)
        if (stale && s!!.startsWith("Обновлено "))
            t!!.setText("Последние данные · " + s!!.substring("Обновлено ".length)!!)
        else if (!stale && s!!.startsWith("Последние данные · "))
            t!!.setText("Обновлено " + s!!.substring("Последние данные · ".length)!!)
        t!!.setTextColor(OFF)
    }

    /** MainActivity owns enabled/disabled logic; this only clarifies the visual state. */
    private fun styleRemoteControls(root: ViewGroup?) {
        val availability: TextView? = findStartsWith(root, "Управление ")
        val unavailable: Boolean =
            availability != null && text(availability)!!.startsWith("Управление недоступно")

        val apply: TextView? = findExact(root, "Применить")
        val stop: TextView? = findExact(root, "STOP · выключить нагрев")
        if (apply != null) styleControlButton(apply, unavailable, BLUE)
        if (stop != null) styleControlButton(stop, unavailable, RED)

        val setpoint: EditText? = findEditTextByHint(root, "Уставка 0…100 °C")
        if (setpoint != null) {
            setpoint!!.setAlpha(1f)
            setpoint!!.setTextColor(if (unavailable) OFF else TEXT)
            setpoint!!.setHintTextColor(if (unavailable) DISABLED_TEXT else FIELD_HINT)
            setpoint!!.setBackground(
                roundStroke(
                    if (unavailable) FIELD_DISABLED else FIELD_ACTIVE,
                    13,
                    if (unavailable) DISABLED_BORDER else BORDER,
                    1,
                    setpoint,
                )
            )
        }
    }

    private fun styleControlButton(button: TextView?, unavailable: Boolean, activeColor: Int) {
        button!!.setAlpha(1f)
        button!!.setTextColor(if (unavailable) DISABLED_TEXT else Color.WHITE)
        button!!.setBackground(
            round(if (unavailable) DISABLED_CONTROL else activeColor, 14, button)
        )
    }

    /**
     * Keep the compact classic cards while avoiding heavy "Последнее:" captions when data is
     * unavailable.
     */
    private fun normalizeUnavailableMetricCaptions(root: ViewGroup?, stale: Boolean) {
        val heaterLabel: TextView? = findExact(root, "ТЭН")
        if (heaterLabel != null && heaterLabel!!.getParent() is ViewGroup) {
            val texts: MutableList<TextView?> = ArrayList<TextView?>()
            collectTexts(heaterLabel!!.getParent() as ViewGroup?, texts)
            for (t: TextView? in texts) {
                val s: String? = text(t)
                if (s == "Последнее: —" || (stale && s!!.startsWith("Последнее:")))
                    t!!.setText("— %")
            }
        }
        val modeLabel: TextView? = findExact(root, "Режим")
        if (modeLabel != null && modeLabel!!.getParent() is ViewGroup) {
            val texts: MutableList<TextView?> = ArrayList<TextView?>()
            collectTexts(modeLabel!!.getParent() as ViewGroup?, texts)
            for (t: TextView? in texts) {
                val s: String? = text(t)
                if (s == "Последний: —" || (stale && s!!.startsWith("Последний:"))) t!!.setText("—")
            }
        }
    }

    private fun updateDisplayedVersion(activity: Activity?, root: ViewGroup?) {
        val version: String?
        try {
            version =
                activity!!
                    .getPackageManager()!!
                    .getPackageInfo(activity!!.getPackageName(), 0)!!
                    .versionName
        } catch (e: Exception) {
            return
        }

        val texts: MutableList<TextView?> = ArrayList<TextView?>()
        collectTexts(root, texts)
        for (t: TextView? in texts) {
            val s: String? = text(t)
            var replaced: String? = s
            if (s!!.contains("HomeSmoke Remote ")) {
                replaced =
                    replaced!!.replaceFirst(
                        ("HomeSmoke Remote \\d+\\.\\d+\\.\\d+").toRegex(),
                        "HomeSmoke Remote " + version!!,
                    )
            }
            replaced = replaced!!.replace("Android 5+", "Android 6+")
            replaced =
                replaced!!.replace(
                    "Android 5.0/5.1: защищённое хранилище этой реализации недоступно; используйте доверенную сеть/VPN.",
                    "Android 6+: пароль MQTT хранится через Android Keystore.",
                )
            if (replaced != s) t!!.setText(replaced)
        }
    }

    companion object {

        private fun modeColor(s: String?): Int {
            if ("Ручной".equals(s!!, ignoreCase = true)) return ORANGE
            if ("PID".equals(s!!, ignoreCase = true)) return GREEN
            if ("AUTO".equals(s!!, ignoreCase = true)) return BLUE
            if ("STOP".equals(s!!, ignoreCase = true)) return RED
            return TEXT
        }

        private fun round(color: Int, radiusDp: Int, v: View?): GradientDrawable? {
            val g: GradientDrawable = GradientDrawable()
            g.setColor(color)
            val d: Float = v!!.getResources()!!.getDisplayMetrics()!!.density
            g.setCornerRadius(radiusDp * d)
            return g
        }

        private fun roundStroke(
            color: Int,
            radiusDp: Int,
            stroke: Int,
            widthDp: Int,
            v: View?,
        ): GradientDrawable? {
            val g: GradientDrawable? = round(color, radiusDp, v)
            val d: Float = v!!.getResources()!!.getDisplayMetrics()!!.density
            g!!.setStroke(Math.max(1, Math.round(widthDp * d)), stroke)
            return g
        }

        private fun findExact(root: ViewGroup?, target: String?): TextView? {
            val all: MutableList<TextView?> = ArrayList<TextView?>()
            collectTexts(root, all)
            for (t: TextView? in all) if (target == text(t)) return t
            return null
        }

        private fun findStartsWith(root: ViewGroup?, prefix: String?): TextView? {
            val all: MutableList<TextView?> = ArrayList<TextView?>()
            collectTexts(root, all)
            for (t: TextView? in all) if (text(t)!!.startsWith(prefix!!)) return t
            return null
        }

        private fun findEditTextByHint(root: ViewGroup?, hint: String?): EditText? {
            val all: MutableList<EditText?> = ArrayList<EditText?>()
            collectEditTexts(root, all)
            for (e: EditText? in all) {
                val h: CharSequence? = e!!.getHint()
                if (h != null && hint == h!!.toString()) return e
            }
            return null
        }

        private fun collectTexts(v: View?, out: MutableList<TextView?>?) {
            if (v is TextView) out!!.add(v as TextView?)
            if (v is ViewGroup) {
                val g: ViewGroup? = v as ViewGroup?
                for (i: Int in 0 until g!!.getChildCount()) collectTexts(g!!.getChildAt(i), out)
            }
        }

        private fun collectEditTexts(v: View?, out: MutableList<EditText?>?) {
            if (v is EditText) out!!.add(v as EditText?)
            if (v is ViewGroup) {
                val g: ViewGroup? = v as ViewGroup?
                for (i: Int in 0 until g!!.getChildCount()) collectEditTexts(g!!.getChildAt(i), out)
            }
        }

        private fun text(t: TextView?): String? {
            return if (t!!.getText() == null) ""
            else t!!.getText()!!.toString()!!.trim({ it <= ' ' })
        }

        private val BLUE: Int = Color.rgb(31, 122, 210)
        private val BLUE_DARK: Int = Color.rgb(26, 91, 164)
        private val TEXT: Int = Color.rgb(21, 31, 47)
        private val GREEN: Int = Color.rgb(35, 151, 83)
        private val RED: Int = Color.rgb(229, 40, 40)
        private val ORANGE: Int = Color.rgb(231, 138, 7)
        private val OFF: Int = Color.rgb(116, 129, 145)
        private val BORDER: Int = Color.rgb(220, 225, 232)
        private val FIELD_HINT: Int = Color.rgb(151, 164, 180)
        private val DISABLED_CONTROL: Int = Color.rgb(226, 231, 236)
        private val DISABLED_TEXT: Int = Color.rgb(137, 148, 160)
        private val DISABLED_BORDER: Int = Color.rgb(216, 222, 228)
        private val FIELD_ACTIVE: Int = Color.rgb(250, 251, 252)
        private val FIELD_DISABLED: Int = Color.rgb(246, 248, 250)
    }
}
