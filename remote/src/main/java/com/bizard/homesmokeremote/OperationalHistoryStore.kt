package com.bizard.homesmokeremote

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/** Persistent local sessions and operational event journal. No protocol data is changed. */
internal class OperationalHistoryStore(context: Context?) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    val lastProcessedTs: Long
        @Synchronized
        get() {
            val db: SQLiteDatabase? = getReadableDatabase()
            val c: Cursor? =
                db!!.query(
                    "meta",
                    arrayOf<String>("v"),
                    "k=?",
                    arrayOf<String>("last_sample_ts"),
                    null,
                    null,
                    null,
                )
            try {
                if (!c!!.moveToFirst()) return 0L
                try {
                    return java.lang.Long.parseLong(c!!.getString(0))
                } catch (ignored: Exception) {
                    return 0L
                }
            } finally {
                c!!.close()
            }
        }

    public override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL(
            "CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ts INTEGER NOT NULL, end_ts INTEGER NOT NULL DEFAULT 0, last_ts INTEGER NOT NULL, samples INTEGER NOT NULL DEFAULT 0, camera_min REAL, camera_max REAL, k_min REAL, k_max REAL, t_min REAL, t_max REAL, heater_max REAL, outage_ms INTEGER NOT NULL DEFAULT 0)"
        )
        db!!.execSQL("CREATE INDEX idx_sessions_start ON sessions(start_ts DESC)")
        db!!.execSQL(
            "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, type TEXT NOT NULL, message TEXT NOT NULL)"
        )
        db!!.execSQL("CREATE INDEX idx_events_ts ON events(ts DESC)")
        db!!.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)")
    }

    public override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db!!.execSQL("DROP TABLE IF EXISTS sessions")
        db!!.execSQL("DROP TABLE IF EXISTS events")
        db!!.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    @Synchronized
    fun processSamples(samples: List<TelemetryHistoryStore.Sample?>?): List<Transition?>? {
        val transitions: ArrayList<Transition?> = ArrayList<Transition?>()
        if (samples == null || samples!!.isEmpty()) return transitions
        val db: SQLiteDatabase? = getWritableDatabase()
        var processed: Long = lastProcessedTs
        var active: Session? = getActiveSession(db)
        db!!.beginTransaction()
        try {
            for (s: TelemetryHistoryStore.Sample? in samples!!) {
                if (s == null || s!!.ts <= processed) continue
                if (active == null) {
                    active = startSession(db, s!!.ts)
                    addEventInternal(db, s!!.ts, "session", "Сеанс начат")
                    transitions.add(Transition(Transition.START, active))
                } else {
                    val gap: Long = s!!.ts - active!!.lastTs
                    if (gap > SESSION_SPLIT_MS) {
                        val finished: Session? = closeSession(db, active, active!!.lastTs)
                        addEventInternal(
                            db,
                            active!!.lastTs,
                            "session",
                            "Сеанс завершён · " + duration(finished!!.durationMs())!!,
                        )
                        transitions.add(Transition(Transition.END, finished))
                        active = startSession(db, s!!.ts)
                        addEventInternal(db, s!!.ts, "session", "Сеанс начат")
                        transitions.add(Transition(Transition.START, active))
                    } else if (gap > OUTAGE_THRESHOLD_MS) {
                        active!!.outageMs += gap
                    }
                }
                active = updateSession(db, active, s)
                processed = s!!.ts
            }
            putMeta(db, "last_sample_ts", (processed).toString())
            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
        trimEvents(db)
        return transitions
    }

    @Synchronized
    fun closeIfInactive(now: Long): Transition? {
        val db: SQLiteDatabase? = getWritableDatabase()
        val active: Session? = getActiveSession(db)
        if (active == null || now - active!!.lastTs <= SESSION_SPLIT_MS) return null
        val finished: Session? = closeSession(db, active, active!!.lastTs)
        addEventInternal(
            db,
            active!!.lastTs,
            "session",
            "Сеанс завершён · " + duration(finished!!.durationMs())!!,
        )
        trimEvents(db)
        return Transition(Transition.END, finished)
    }

    @Synchronized
    fun addEvent(ts: Long, type: String?, message: String?) {
        if (message == null || message!!.trim({ it <= ' ' }).isEmpty()) return
        val db: SQLiteDatabase? = getWritableDatabase()
        addEventInternal(
            db,
            if (ts > 0) ts else System.currentTimeMillis(),
            if (type == null) "info" else type,
            message!!.trim({ it <= ' ' }),
        )
        trimEvents(db)
    }

    @Synchronized
    fun querySessions(limit: Int): List<Session?>? {
        val out: ArrayList<Session?> = ArrayList<Session?>()
        val db: SQLiteDatabase? = getReadableDatabase()
        val c: Cursor? =
            db!!.query(
                "sessions",
                null,
                null,
                null,
                null,
                null,
                "start_ts DESC",
                (Math.max(1, limit)).toString(),
            )
        try {
            while (c!!.moveToNext()) out.add(readSession(c))
        } finally {
            c!!.close()
        }
        return out
    }

    @Synchronized
    fun querySession(id: Long): Session? {
        if (id <= 0) return null
        val db: SQLiteDatabase? = getReadableDatabase()
        val c: Cursor? =
            db!!.query(
                "sessions",
                null,
                "id=?",
                arrayOf<String>((id).toString()),
                null,
                null,
                null,
                "1",
            )
        try {
            return if (c!!.moveToFirst()) readSession(c) else null
        } finally {
            c!!.close()
        }
    }

    @Synchronized
    fun queryEvents(limit: Int): List<Event?>? {
        val out: ArrayList<Event?> = ArrayList<Event?>()
        val db: SQLiteDatabase? = getReadableDatabase()
        val c: Cursor? =
            db!!.query(
                "events",
                arrayOf<String>("id", "ts", "type", "message"),
                null,
                null,
                null,
                null,
                "ts DESC",
                (Math.max(1, limit)).toString(),
            )
        try {
            while (c!!.moveToNext()) out.add(
                Event(c!!.getLong(0), c!!.getLong(1), c!!.getString(2), c!!.getString(3))
            )
        } finally {
            c!!.close()
        }
        return out
    }

    @Synchronized
    fun queryEvents(from: Long, to: Long, limit: Int): List<Event?>? {
        val out: ArrayList<Event?> = ArrayList<Event?>()
        val db: SQLiteDatabase? = getReadableDatabase()
        val c: Cursor? =
            db!!.query(
                "events",
                arrayOf<String>("id", "ts", "type", "message"),
                "ts>=? AND ts<=?",
                arrayOf<String>((Math.max(0L, from)).toString(), (Math.max(from, to)).toString()),
                null,
                null,
                "ts DESC",
                (Math.max(1, limit)).toString(),
            )
        try {
            while (c!!.moveToNext()) out.add(
                Event(c!!.getLong(0), c!!.getLong(1), c!!.getString(2), c!!.getString(3))
            )
        } finally {
            c!!.close()
        }
        return out
    }

    @Synchronized
    fun clearEvents() {
        getWritableDatabase()!!.delete("events", null, null)
    }

    private fun startSession(db: SQLiteDatabase?, ts: Long): Session? {
        val v: ContentValues = ContentValues()
        v.put("start_ts", ts)
        v.put("end_ts", 0)
        v.put("last_ts", ts)
        v.put("samples", 0)
        v.put("outage_ms", 0)
        val id: Long = db!!.insertOrThrow("sessions", null, v)
        return Session(
            id,
            ts,
            0,
            ts,
            0,
            java.lang.Double.NaN,
            java.lang.Double.NaN,
            java.lang.Double.NaN,
            java.lang.Double.NaN,
            java.lang.Double.NaN,
            java.lang.Double.NaN,
            java.lang.Double.NaN,
            0,
        )
    }

    private fun updateSession(
        db: SQLiteDatabase?,
        x: Session?,
        s: TelemetryHistoryStore.Sample?,
    ): Session? {
        x!!.lastTs = s!!.ts
        x!!.samples++
        x!!.cameraMin = min(x!!.cameraMin, s!!.camera)
        x!!.cameraMax = max(x!!.cameraMax, s!!.camera)
        x!!.kMin = min(x!!.kMin, s!!.probeK)
        x!!.kMax = max(x!!.kMax, s!!.probeK)
        x!!.tMin = min(x!!.tMin, s!!.probeT)
        x!!.tMax = max(x!!.tMax, s!!.probeT)
        x!!.heaterMax = max(x!!.heaterMax, s!!.heater)
        val v: ContentValues = ContentValues()
        v.put("last_ts", x!!.lastTs)
        v.put("samples", x!!.samples)
        v.put("outage_ms", x!!.outageMs)
        put(v, "camera_min", x!!.cameraMin)
        put(v, "camera_max", x!!.cameraMax)
        put(v, "k_min", x!!.kMin)
        put(v, "k_max", x!!.kMax)
        put(v, "t_min", x!!.tMin)
        put(v, "t_max", x!!.tMax)
        put(v, "heater_max", x!!.heaterMax)
        db!!.update("sessions", v, "id=?", arrayOf<String>((x!!.id).toString()))
        return x
    }

    private fun closeSession(db: SQLiteDatabase?, x: Session?, endTs: Long): Session? {
        x!!.endTs = Math.max(x!!.startTs, endTs)
        val v: ContentValues = ContentValues()
        v.put("end_ts", x!!.endTs)
        db!!.update("sessions", v, "id=?", arrayOf<String>((x!!.id).toString()))
        return x
    }

    private fun getActiveSession(db: SQLiteDatabase?): Session? {
        val c: Cursor? = db!!.query("sessions", null, "end_ts=0", null, null, null, "id DESC", "1")
        try {
            return if (c!!.moveToFirst()) readSession(c) else null
        } finally {
            c!!.close()
        }
    }

    internal class Session(
        val id: Long,
        val startTs: Long,
        var endTs: Long,
        var lastTs: Long,
        var samples: Int,
        var cameraMin: Double,
        var cameraMax: Double,
        var kMin: Double,
        var kMax: Double,
        var tMin: Double,
        var tMax: Double,
        var heaterMax: Double,
        var outageMs: Long,
    ) {
        fun effectiveEnd(): Long {
            return if (endTs > 0) endTs else lastTs
        }

        fun durationMs(): Long {
            return Math.max(0, effectiveEnd() - startTs)
        }

        fun active(): Boolean {
            return endTs == 0L
        }

        fun title(): String? {
            return SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date(startTs))
        }
    }

    internal class Event(val id: Long, val ts: Long, val type: String?, val message: String?)

    internal class Transition(val kind: Int, val session: Session?) {
        companion object {
            val START: Int = 1
            val END: Int = 2
        }
    }

    companion object {
        private val DB_NAME: String = "remote_operational_history.db"
        private val DB_VERSION: Int = 1
        private val OUTAGE_THRESHOLD_MS: Long = 15000L
        private val SESSION_SPLIT_MS: Long = 10L * 60L * 1000L
        private val MAX_EVENTS: Int = 500

        private fun readSession(c: Cursor?): Session? {
            return Session(
                c!!.getLong(c!!.getColumnIndexOrThrow("id")),
                c!!.getLong(c!!.getColumnIndexOrThrow("start_ts")),
                c!!.getLong(c!!.getColumnIndexOrThrow("end_ts")),
                c!!.getLong(c!!.getColumnIndexOrThrow("last_ts")),
                c!!.getInt(c!!.getColumnIndexOrThrow("samples")),
                d(c, "camera_min"),
                d(c, "camera_max"),
                d(c, "k_min"),
                d(c, "k_max"),
                d(c, "t_min"),
                d(c, "t_max"),
                d(c, "heater_max"),
                c!!.getLong(c!!.getColumnIndexOrThrow("outage_ms")),
            )
        }

        private fun d(c: Cursor?, name: String?): Double {
            val i: Int = c!!.getColumnIndexOrThrow(name)
            return if (c!!.isNull(i)) java.lang.Double.NaN else c!!.getDouble(i)
        }

        private fun min(a: Double, b: Double): Double {
            if (java.lang.Double.isNaN(b) || java.lang.Double.isInfinite(b)) return a
            return if (java.lang.Double.isNaN(a)) b else Math.min(a, b)
        }

        private fun max(a: Double, b: Double): Double {
            if (java.lang.Double.isNaN(b) || java.lang.Double.isInfinite(b)) return a
            return if (java.lang.Double.isNaN(a)) b else Math.max(a, b)
        }

        private fun put(v: ContentValues?, k: String?, x: Double) {
            if (java.lang.Double.isNaN(x) || java.lang.Double.isInfinite(x)) v!!.putNull(k)
            else v!!.put(k, x)
        }

        private fun putMeta(db: SQLiteDatabase?, k: String?, v: String?) {
            val cv: ContentValues = ContentValues()
            cv.put("k", k)
            cv.put("v", v)
            db!!.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }

        private fun addEventInternal(
            db: SQLiteDatabase?,
            ts: Long,
            type: String?,
            message: String?,
        ) {
            val v: ContentValues = ContentValues()
            v.put("ts", ts)
            v.put("type", type)
            v.put("message", message)
            db!!.insert("events", null, v)
        }

        private fun trimEvents(db: SQLiteDatabase?) {
            db!!.execSQL(
                "DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY ts DESC LIMIT " +
                    MAX_EVENTS +
                    ")"
            )
        }

        private fun duration(ms: Long): String? {
            val min: Long = Math.max(0, ms / 60000L)
            val h: Long = min / 60
            val m: Long = min % 60
            return if (h > 0) h.toString() + " ч " + m.toString() + " мин"
            else m.toString() + " мин"
        }
    }
}
