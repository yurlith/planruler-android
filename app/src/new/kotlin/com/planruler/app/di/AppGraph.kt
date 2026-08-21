package com.planruler.app.di

import android.content.Context
import com.planruler.document.android.AndroidDocumentGateway
import com.planruler.crm.local.CrmDatabase
import com.planruler.crm.local.RoomCrmRepository
import com.planruler.engine.default.DefaultMeasurementEngine
import com.planruler.engine.default.DefaultSnapEngine
import com.planruler.export.android.AndroidExportGateway
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.project.local.FileProjectRepository

/** The only composition root: feature modules receive interfaces, never implementations. */
class AppGraph(context: Context) {
    private val app = context.applicationContext
    val projects = FileProjectRepository(app.filesDir.resolve("projects"))
    private val crmDatabase = CrmDatabase.create(app)
    val crm = RoomCrmRepository(crmDatabase)
    val documents = AndroidDocumentGateway(app)
    val exports = AndroidExportGateway(app)

    /** The 3D engine is stateless and quota bound, so one instance serves every screen. */
    val fabrication3d: Fabrication3DEngine = DefaultFabrication3DEngine(EngineLimits3D.MOBILE)

    fun newEngine() = DefaultMeasurementEngine()
    fun newSnapEngine() = DefaultSnapEngine()
}
