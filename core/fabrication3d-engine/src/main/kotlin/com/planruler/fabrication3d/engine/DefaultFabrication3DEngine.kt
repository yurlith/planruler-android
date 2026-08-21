package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyGraph3D
import com.planruler.fabrication3d.ChainEditor3D
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.fabrication3d.MeshTessellator3D
import com.planruler.fabrication3d.PartFactory3D
import com.planruler.fabrication3d.ProfileCustomizer3D
import com.planruler.fabrication3d.RouteSolver3D

/**
 * The only public type of the engine module. Callers resolve this from the composition
 * root and reach every capability through the interfaces declared in the API module.
 */
class DefaultFabrication3DEngine(
    override val limits: EngineLimits3D = EngineLimits3D.MOBILE,
) : Fabrication3DEngine {

    private val partFactory = DefaultPartFactory3D()
    private val assemblyGraph = DefaultAssemblyGraph3D(limits)
    private val chainEditor = DefaultChainEditor3D(limits, partFactory, assemblyGraph)

    override val parts: PartFactory3D get() = partFactory
    override val graph: AssemblyGraph3D get() = assemblyGraph
    override val mesh: MeshTessellator3D = DefaultMeshTessellator3D(limits)
    override val chains: ChainEditor3D get() = chainEditor
    override val router: RouteSolver3D = DefaultRouteSolver3D(limits, chainEditor, assemblyGraph)
    override val profiles: ProfileCustomizer3D = DefaultProfileCustomizer3D(limits)
}
