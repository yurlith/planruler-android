package com.planruler.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Keeps independently runnable UI tests isolated from Android system pickers. */
internal fun bringPlanRulerToForeground() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    repeat(3) {
        if (device.hasObject(By.pkg("com.planruler.app"))) return
        device.pressBack()
        if (device.wait(Until.hasObject(By.pkg("com.planruler.app")), 2_000)) return
    }
    check(device.hasObject(By.pkg("com.planruler.app"))) {
        "PlanRuler did not return to the foreground before the test"
    }
}
