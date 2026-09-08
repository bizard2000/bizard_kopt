package com.bizard.homesmokeremote

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.ArrayList

/** Local 24-hour cache of fresh HomeSmoke telemetry for charts. */
internal class TelemetryHistoryStore(context: Context?) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private var lastInsertedAt: Long = 0L
    private var insertsSincePrune: Int = 0

    public override fun onCreate(db: SQLiteDatabase?) {
        db!!.execSQL(
            "CREATE TABLE " +
                TABLE +
                " (ts INTEGER PRIMARY KEY, camera REAL, setpoint REAL, probe_k REAL, probe_t REAL, heater REAL)"
        )
        db!!.execSQL("CREATE INDEX idx_samples_ts ON " + TABLE + "(ts)")
        createBoundaryTable(db)
    }

    public override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createBoundaryTable(db)
            return
        }
        db!!.execSQL("DROP TABLE IF EXISTS " + TABLE)
        db!!.execSQL("DROP TABLE IF EXISTS " + BOUNDARIES)
        onCreate(db)
    }

    @Synchronized
    fun addFresh(sample: Sample?) {
        if (sample == null || sample!!.ts <= 0) return
        if (lastInsertedAt > 0 && sample!!.ts - lastInsertedAt < MIN_SAMPLE_INTERVAL_MS) return
        val db: SQLiteDatabase? = getWritableDatabase()
        val v: ContentValues = ContentValues()
        v.put("ts", sample!!.ts)
        putDouble(v, "camera", sample!!.camera)
        putDouble(v, "setpoint", sample!!.setpoint)
        putDouble(v, "probe_k", sample!!.probeK)
        putDouble(v, "probe_t", sample!!.probeT)
        putDouble(v, "heater", sample!!.heater)
        db!!.insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE)
        lastInsertedAt = sample!!.ts
        insertsSincePrune++
        if (insertsSincePrune >= 60) {
            val cutoff: Long = System.currentTimeMillis() - RETENTION_MS
            prune(db, cutoff)
            pruneBoundaries(db, cutoff)
            insertsSincePrune = 0
        }
    }

    /** Explicit local boundary, used for test runs and other locally known session starts. */
    @Synchronized
    fun markSessionBoundary(ts: Long) {
        if (ts <= 0) return
        val db: SQLiteDatabase? = getWritableDatabase()
        val v: ContentValues = ContentValues()
        v.put("ts", ts)
        db!!.insertWithOnConflict(BOUNDARIES, null, v, SQLiteDatabase.CONFLICT_IGNORE)
        pruneBoundaries(db, System.currentTimeMillis() - RETENTION_MS)
    }

    @Synchronized
    fun query(from: Long, to: Long, maxPoints: Int): List<Sample?>? {
        val all: ArrayList<Sample?> = ArrayList<Sample?>()
        val db: SQLiteDatabase? = getReadableDatabase()
        val boundaries: List<Long?>? = queryBoundaries(db, from, to)
        var boundaryIndex: Int = 0
        var prevTs: Long = 0L
        var segmentId: Int = 0
        var sessionId: Int = 0
        val c: Cursor? =
            db!!.query(
                TABLE,
                arrayOf<String>("ts", "camera", "setpoint", "probe_k", "probe_t", "heater"),
                "ts>=? AND ts<=?",
                arrayOf<String>((from).toString(), (to).toString()),
                null,
                null,
                "ts ASC",
            )
        try {
            while (c!!.moveToNext()) {
                val ts: Long = c!!.getLong(0)
                var explicitBoundary: Boolean = false
                while (
                    boundaryIndex < boundaries!!.size && boundaries!!.get(boundaryIndex)!! <= ts
                ) {
                    val b: Long = boundaries!!.get(boundaryIndex++)!!
                    if (prevTs > 0 && b > prevTs) explicitBoundary = true
                }
                if (prevTs > 0) {
                    val gap: Long = ts - prevTs
                    if (explicitBoundary || gap > VISUAL_GAP_MS) segmentId++
                    if (explicitBoundary || gap > DEFAULT_SESSION_SPLIT_MS) sessionId++
                }
                all.add(
                    Sample(
                        ts,
                        readDouble(c, 1),
                        readDouble(c, 2),
                        readDouble(c, 3),
                        readDouble(c, 4),
                        readDouble(c, 5),
                        segmentId,
                        sessionId,
                    )
                )
                prevTs = ts
            }
        } finally {
            c!!.close()
        }
        if (maxPoints <= 0 || all.size <= maxPoints) return all
        if (maxPoints == 1) return listOf<Sample?>(all.get(all.size - 1))
        val out: ArrayList<Sample?> = ArrayList<Sample?>(maxPoints)
        val step: Double = (all.size - 1.0) / (maxPoints - 1.0)
        var last: Int = -1
        for (i: Int in 0 until maxPoints) {
            val idx: Int = Math.round(i * step).toInt()
            if (idx == last) continue
            out.add(all.get(idx))
            last = idx
        }
        if (out.isEmpty() || out.get(out.size - 1)!!.ts != all.get(all.size - 1)!!.ts)
            out.add(all.get(all.size - 1))
        return out
    }

    @Synchronized
    fun countSamples(from: Long, to: Long): Int {
        val db: SQLiteDatabase? = getReadableDatabase()
        val c: Cursor? =
            db!!.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE ts>=? AND ts<=?",
                arrayOf<String>((from).toString(), (to).toString()),
            )
        try {
            return if (c!!.moveToFirst()) c!!.getInt(0) else 0
        } finally {
            c!!.close()
        }
    }

    @Synchronized
    fun latestTimestamp(): Long {
        val db: SQLiteDatabase? = getReadableDatabase()
        val c: Cursor? = db!!.rawQuery("SELECT MAX(ts) FROM " + TABLE, null)
        try {
            return if (c!!.moveToFirst() && !c!!.isNull(0)) c!!.getLong(0) else 0L
        } finally {
            c!!.close()
        }
    }

    /** Returns the first sample of the latest logical session, surviving app restarts. */
    @Synchronized
    fun latestSessionStart(to: Long, splitMs: Long): Long {
        val db: SQLiteDatabase? = getReadableDatabase()
        val from: Long = Math.max(0L, to - RETENTION_MS)
        val boundaries: List<Long?>? = queryBoundaries(db, from, to)
        var boundaryIndex: Int = 0
        var prevTs: Long = 0L
        var start: Long = 0L
        val c: Cursor? =
            db!!.query(
                TABLE,
                arrayOf<String>("ts"),
                "ts>=? AND ts<=?",
                arrayOf<String>((from).toString(), (to).toString()),
                null,
                null,
                "ts ASC",
            )
        try {
            while (c!!.moveToNext()) {
                val ts: Long = c!!.getLong(0)
                var explicitBoundary: Boolean = false
                while (
                    boundaryIndex < boundaries!!.size && boundaries!!.get(boundaryIndex)!! <= ts
                ) {
                    val b: Long = boundaries!!.get(boundaryIndex++)!!
                    if (prevTs > 0 && b > prevTs) explicitBoundary = true
                }
                if (
                    start == 0L ||
                        prevTs == 0L ||
                        explicitBoundary ||
                        ts - prevTs > Math.max(1L, splitMs)
                )
                    start = ts
                prevTs = ts
            }
        } finally {
            c!!.close()
        }
        return start
    }

    /**
     * Counts logical sessions in a selected graph range; short telemetry outages remain one
     * session.
     */
    @Synchronized
    fun countSessions(from: Long, to: Long, splitMs: Long): Int {
        val db: SQLiteDatabase? = getReadableDatabase()
        val boundaries: List<Long?>? = queryBoundaries(db, from, to)
        var boundaryIndex: Int = 0
        var count: Int = 0
        var prevTs: Long = 0L
        val c: Cursor? =
            db!!.query(
                TABLE,
                arrayOf<String>("ts"),
                "ts>=? AND ts<=?",
                arrayOf<String>((from).toString(), (to).toString()),
                null,
                null,
                "ts ASC",
            )
        try {
            while (c!!.moveToNext()) {
                val ts: Long = c!!.getLong(0)
                var explicitBoundary: Boolean = false
                while (
                    boundaryIndex < boundaries!!.size && boundaries!!.get(boundaryIndex)!! <= ts
                ) {
                    val b: Long = boundaries!!.get(boundaryIndex++)!!
                    if (prevTs > 0 && b > prevTs) explicitBoundary = true
                }
                if (prevTs == 0L) count = 1
                else if (explicitBoundary || ts - prevTs > Math.max(1L, splitMs)) count++
                prevTs = ts
            }
        } finally {
            c!!.close()
        }
        return count
    }

    internal class Sample
    @JvmOverloads
    constructor(
        @JvmField val ts: Long,
        @JvmField val camera: Double,
        @JvmField val setpoint: Double,
        @JvmField val probeK: Double,
        @JvmField val probeT: Double,
        @JvmField val heater: Double,
        @JvmField val segmentId: Int = 0,
        @JvmField val sessionId: Int = 0,
    )

    companion object {
        private val DB_NAME: String = "remote_telemetry_history.db"
        private val DB_VERSION: Int = 2
        private val TABLE: String = "samples"
        private val BOUNDARIES: String = "session_boundaries"
        private val RETENTION_MS: Long = 24L * 60L * 60L * 1000L
        private val MIN_SAMPLE_INTERVAL_MS: Long = 5000L
        private val VISUAL_GAP_MS: Long = 30000L
        private val DEFAULT_SESSION_SPLIT_MS: Long = 10L * 60L * 1000L

        private fun createBoundaryTable(db: SQLiteDatabase?) {
            db!!.execSQL("CREATE TABLE IF NOT EXISTS " + BOUNDARIES + " (ts INTEGER PRIMARY KEY)")
            db!!.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_session_boundaries_ts ON " + BOUNDARIES + "(ts)"
            )
        }

        private fun queryBoundaries(db: SQLiteDatabase?, from: Long, to: Long): List<Long?>? {
            val out: ArrayList<Long?> = ArrayList<Long?>()
            val c: Cursor? =
                db!!.query(
                    BOUNDARIES,
                    arrayOf<String>("ts"),
                    "ts>=? AND ts<=?",
                    arrayOf<String>((from).toString(), (to).toString()),
                    null,
                    null,
                    "ts ASC",
                )
            try {
                while (c!!.moveToNext()) out.add(c!!.getLong(0))
            } finally {
                c!!.close()
            }
            return out
        }

        private fun putDouble(v: ContentValues?, key: String?, value: Double) {
            if (java.lang.Double.isNaN(value) || java.lang.Double.isInfinite(value))
                v!!.putNull(key)
            else v!!.put(key, value)
        }

        private fun readDouble(c: Cursor?, column: Int): Double {
            return if (c!!.isNull(column)) java.lang.Double.NaN else c!!.getDouble(column)
        }

        private fun prune(db: SQLiteDatabase?, cutoff: Long) {
            db!!.delete(TABLE, "ts<?", arrayOf<String>((cutoff).toString()))
        }

        private fun pruneBoundaries(db: SQLiteDatabase?, cutoff: Long) {
            db!!.delete(BOUNDARIES, "ts<?", arrayOf<String>((cutoff).toString()))
        }
    }
}
