package com.planruler.app

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.planruler.engine.api.SnapContext
import com.planruler.engine.default.DefaultMeasurementEngine
import com.planruler.engine.default.DefaultSnapEngine
import com.planruler.model.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformanceSmokeTest {
    @Test
    fun twoThousandMeasurementsRemainInteractive() {
        val measurements = List(2_000) { index ->
            val x = (index % 100).toDouble() * 10.0
            val y = (index / 100).toDouble() * 10.0
            Measurement(
                id = MeasurementId("perf-$index"),
                type = MeasurementType.DISTANCE,
                points = listOf(DocPoint(x, y), DocPoint(x + 8.0, y + 4.0)),
                pageIndex = 0,
                createdAtEpochMs = index.toLong(),
            )
        }
        val engine = DefaultMeasurementEngine()
        val snap = DefaultSnapEngine()
        engine.restore(Calibration.pdfRatio(50.0), LengthUnit.METER, measurements)

        val evaluateStarted = SystemClock.elapsedRealtimeNanos()
        repeat(4) { measurements.forEach(engine::evaluate) }
        val evaluateMs = (SystemClock.elapsedRealtimeNanos() - evaluateStarted) / 1_000_000

        val snapStarted = SystemClock.elapsedRealtimeNanos()
        repeat(100) { index ->
            snap.resolve(
                DocPoint(index.toDouble(), index.toDouble()),
                SnapContext(measurements, sensitivityDocumentUnits = 3.0),
            )
        }
        val snapMs = (SystemClock.elapsedRealtimeNanos() - snapStarted) / 1_000_000

        println("PLANRULER_PERF evaluate_8000_ms=$evaluateMs snap_100x2000_ms=$snapMs")
        assertTrue("Evaluation regression: ${evaluateMs}ms", evaluateMs < 3_000)
        assertTrue("Snapping regression: ${snapMs}ms", snapMs < 3_000)
    }
}
