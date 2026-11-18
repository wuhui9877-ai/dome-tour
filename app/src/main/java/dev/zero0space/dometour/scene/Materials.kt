package dev.zero0space.dometour.scene

import android.util.Log
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PolygonFillMode
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3

private const val TAG = "DomeTourMaterials"

/** Builds a [Color4] from a 0xRRGGBB hex value. */
fun rgb(hex: Int, alpha: Float = 1f): Color4 {
    val r = ((hex shr 16) and 0xFF) / 255f
    val g = ((hex shr 8) and 0xFF) / 255f
    val b = (hex and 0xFF) / 255f
    return Color4(r, g, b, alpha)
}

/**
 * Creates a [MeshResource] holding a procedural [MeshModel], via the SDK's
 * `MeshResource.createWithMeshModel` factory (the no-arg constructor is
 * internal). Returns null (and logs) on failure; callers must tolerate a
 * null mesh and skip the affected parts.
 */
fun meshResource(model: MeshModel, name: String = "procedural"): MeshResource? {
    return try {
        MeshResource.createWithMeshModel(model, computeBounds(model.positions), name)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create MeshResource [$name]: ${e.message}")
        null
    }
}

private fun computeBounds(positions: List<Vector3>): BoundingBox {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var maxZ = -Float.MAX_VALUE
    positions.forEach { p ->
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.z < minZ) minZ = p.z
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
        if (p.z > maxZ) maxZ = p.z
    }
    if (minX > maxX) {
        // Empty mesh: degenerate bounds.
        return BoundingBox(Vector3(0f, 0f, 0f), Vector3(0f, 0f, 0f))
    }
    return BoundingBox(
        Vector3((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f),
        Vector3((maxX - minX) / 2f, (maxY - minY) / 2f, (maxZ - minZ) / 2f)
    )
}

/**
 * All materials in this project are unlit: the diorama is a stylized neon
 * miniature, and unlit materials render deterministically without any scene
 * lighting setup (no lights, IBL, or environment configuration needed).
 * Face culling is disabled everywhere so mesh winding can never hide
 * geometry.
 *
 * Materials are created via the SDK's `UnlitMaterial.create` factory (the
 * constructor is private). Factories return null on failure; callers must
 * tolerate a null material and skip the affected parts.
 */

/** Flat opaque color. */
fun opaqueMaterial(color: Color4): UnlitMaterial? = try {
    UnlitMaterial.create().apply {
        setBaseColor(color)
        setCullingMode(MaterialCullingMode.NONE)
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to create opaque material: ${e.message}")
    null
}

/** See-through color, e.g. dome shell and water. */
fun transparentMaterial(color: Color4, opacity: Float): UnlitMaterial? = try {
    UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
        setBaseColor(color)
        setOpacity(opacity)
        setCullingMode(MaterialCullingMode.NONE)
        setDepthWrite(false)
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to create transparent material: ${e.message}")
    null
}

/** Additive glow: window lights, lanterns, strings, highlight ring. */
fun glowMaterial(color: Color4, opacity: Float): UnlitMaterial? = try {
    UnlitMaterial.create(BlendingMode.ADD).apply {
        setBaseColor(color)
        setOpacity(opacity)
        setCullingMode(MaterialCullingMode.NONE)
        setDepthWrite(false)
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to create glow material: ${e.message}")
    null
}

/** Wireframe overlay: the dome's latitude/longitude lattice lines. */
fun latticeMaterial(color: Color4, opacity: Float): UnlitMaterial? = try {
    UnlitMaterial.create(BlendingMode.ADD).apply {
        setBaseColor(color)
        setOpacity(opacity)
        setCullingMode(MaterialCullingMode.NONE)
        setPolygonFillMode(PolygonFillMode.LINE)
        setDepthWrite(false)
    }
} catch (e: Exception) {
    Log.e(TAG, "Failed to create lattice material: ${e.message}")
    null
}
