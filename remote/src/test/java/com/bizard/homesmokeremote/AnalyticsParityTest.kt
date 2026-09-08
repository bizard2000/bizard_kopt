package com.bizard.homesmokeremote

import org.junit.Assert.*
import org.junit.Test

class AnalyticsParityTest {
    @Test fun matchesJava213GoldenResultsFor64IrregularSessions() {
        val fixture = javaClass.getResourceAsStream("/session_analytics_java_2_1_3.tsv")!!
        val samples = mutableListOf<TelemetryHistoryStore.Sample>()
        var result: SessionAnalytics.Result? = null
        var case = ""
        var cases = 0
        fixture.bufferedReader().useLines { lines ->
            lines.filterNot { it.startsWith("#") }.forEach { line ->
                val columns = line.split('\t')
                when (columns[0]) {
                    "CASE" -> { samples.clear(); result = null; case = columns[1]; cases++ }
                    "S" -> samples.add(TelemetryHistoryStore.Sample(
                        columns[1].toLong(), columns[2].toDouble(), columns[3].toDouble(),
                        columns[4].toDouble(), columns[5].toDouble(), columns[6].toDouble()
                    ))
                    "R" -> {
                        if (result == null) result = SessionAnalytics.analyze(samples)
                        val field = SessionAnalytics.Result::class.java.getDeclaredField(columns[1])
                        field.isAccessible = true
                        assertEquals("Java parity: case $case, ${columns[1]}", columns[2], field.get(result).toString())
                    }
                }
            }
        }
        assertEquals(64, cases)
    }
}
