package com.planruler.feature.pipecalculator

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneGesture3DTest {
    private val camera = SceneCameraGestureState3D(
        yaw = -32f,
        pitch = 24f,
        zoom = 1.15f,
        panX = 0f,
        panY = 0f,
    )

    @Test
    fun `one finger drag orbits without moving the model on screen`() {
        val moved = applySceneGesture3D(camera, pointerCount = 1, pan = Offset(100f, -50f), zoomChange = 1f)

        assertTrue(moved.yaw != camera.yaw)
        assertTrue(moved.pitch != camera.pitch)
        assertEquals(0f, moved.panX)
        assertEquals(0f, moved.panY)
        assertEquals(camera.zoom, moved.zoom)
    }

    @Test
    fun `pure two finger pan never rotates the assembly`() {
        val moved = applySceneGesture3D(camera, pointerCount = 2, pan = Offset(80f, 45f), zoomChange = 1f)

        assertEquals(camera.yaw, moved.yaw)
        assertEquals(camera.pitch, moved.pitch)
        assertEquals(80f, moved.panX)
        assertEquals(45f, moved.panY)
        assertEquals(camera.zoom, moved.zoom)
    }

    @Test
    fun `two finger gesture pans and zooms together within safe bounds`() {
        val moved = applySceneGesture3D(camera, pointerCount = 2, pan = Offset(-25f, 12f), zoomChange = 2f)
        val clamped = applySceneGesture3D(moved, pointerCount = 2, pan = Offset.Zero, zoomChange = 100f)

        assertEquals(-25f, moved.panX)
        assertEquals(12f, moved.panY)
        assertEquals(2.3f, moved.zoom, 0.0001f)
        assertEquals(8f, clamped.zoom)
    }
}
