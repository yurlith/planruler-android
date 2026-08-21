package com.planruler.feature.pipecalculator

import androidx.compose.runtime.saveable.SaverScope
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class ChainPlanSaverTest {
    private val scope = SaverScope { true }

    private fun roundTrip(plan: ChainPlan3D): ChainPlan3D {
        val saved = with(ChainPlanSaver) { scope.save(plan) }
        return requireNotNull(ChainPlanSaver.restore(requireNotNull(saved)))
    }

    @Test
    fun `an empty plan survives a round trip`() {
        assertEquals(ChainPlan3D(), roundTrip(ChainPlan3D()))
    }

    @Test
    fun `a full chain survives a round trip`() {
        val plan = ChainPlan3D(
            start = ChainStart3D(
                position = Vec3(120.0, -80.5, 250.25),
                direction = Vec3(0.0, 1.0, 0.0),
                upHint = Vec3(0.0, 0.0, 1.0),
                terminal = ChainTerminal3D.FLANGE,
            ),
            steps = listOf(
                ChainStep3D.Pipe(427.5),
                ChainStep3D.Elbow(45.0, 30.75),
                ChainStep3D.Pipe(757.25),
                ChainStep3D.Elbow(-45.0, 0.0),
                ChainStep3D.Flange(12.5),
            ),
        )

        assertEquals(plan, roundTrip(plan))
    }

    @Test
    fun `an open ended chain keeps its terminal kind`() {
        val plan = ChainPlan3D(
            start = ChainStart3D(terminal = ChainTerminal3D.OPEN_END),
            steps = listOf(ChainStep3D.Pipe(300.0), ChainStep3D.Cap(0.0)),
        )

        assertEquals(plan, roundTrip(plan))
    }

    @Test
    fun `a branched chain survives a round trip`() {
        val plan = ChainPlan3D(
            steps = listOf(
                ChainStep3D.Pipe(300.0),
                ChainStep3D.Tee(
                    rollDeg = 90.0,
                    branch = listOf(ChainStep3D.Pipe(180.0), ChainStep3D.Flange(0.0)),
                ),
                ChainStep3D.Pipe(240.0),
                ChainStep3D.Flange(0.0),
            ),
        )

        assertEquals(plan, roundTrip(plan))
    }

    @Test
    fun `nested branches survive a round trip`() {
        val plan = ChainPlan3D(
            steps = listOf(
                ChainStep3D.Tee(
                    rollDeg = 0.0,
                    branch = listOf(
                        ChainStep3D.Pipe(120.0),
                        ChainStep3D.Tee(45.0, listOf(ChainStep3D.Pipe(90.0))),
                        ChainStep3D.Cap(0.0),
                    ),
                ),
                ChainStep3D.Pipe(200.0),
            ),
        )

        assertEquals(plan, roundTrip(plan))
    }

    @Test
    fun `a reducer survives a round trip`() {
        val plan = ChainPlan3D(
            steps = listOf(
                ChainStep3D.Pipe(300.0),
                ChainStep3D.Reducer(
                    lengthMm = 76.0,
                    smallNominalDiameter = 40,
                    smallOutsideDiameterMm = 48.3,
                    smallWallThicknessMm = 2.6,
                    eccentric = true,
                    rollDeg = 15.5,
                ),
                ChainStep3D.Cap(0.0),
            ),
        )

        assertEquals(plan, roundTrip(plan))
    }

    @Test
    fun `a concentric reducer keeps that flag across a round trip`() {
        val plan = ChainPlan3D(
            steps = listOf(ChainStep3D.Reducer(76.0, 40, 48.3, 2.6, eccentric = false)),
        )

        assertEquals(plan, roundTrip(plan))
    }

    @Test
    fun `an unbalanced branch marker restores as an empty chain`() {
        listOf(
            "0,0,0,1,0,0,0,0,1,FLANGE|T,0.0;P,100.0",
            "0,0,0,1,0,0,0,0,1,FLANGE|P,100.0;/",
        ).forEach { raw ->
            assertEquals("restoring '$raw'", ChainPlan3D(), ChainPlanSaver.restore(raw))
        }
    }

    @Test
    fun `corrupt state restores as an empty chain rather than a half built model`() {
        listOf("", "garbage", "1,2,3|P,4", "0,0,0,1,0,0,0,0,1,NOPE|P,300.0").forEach { raw ->
            assertEquals("restoring '$raw'", ChainPlan3D(), ChainPlanSaver.restore(raw))
        }
    }

    @Test
    fun `a zero start direction is rejected`() {
        val encoded = "0,0,0,0,0,0,0,0,1,FLANGE|P,300.0"

        assertEquals(ChainPlan3D(), ChainPlanSaver.restore(encoded))
    }
}
