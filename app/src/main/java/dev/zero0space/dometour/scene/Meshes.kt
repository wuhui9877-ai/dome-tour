package dev.zero0space.dometour.scene

import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural low-poly mesh builders for the dome-city diorama.
 *
 * All meshes are generated in pure Kotlin from vertex lists — no model files,
 * no 3D engine. Meshes are built at unit size and instanced through entity
 * transforms (scale), so the whole city shares a handful of mesh resources.
 *
 * Winding order is kept consistent (counter-clockwise seen from outside), and
 * every material in this project disables face culling, so a flipped face can
 * never make geometry invisible.
 */

private class MeshBuilder {
    val positions = ArrayList<Vector3>()
    val indices = ArrayList<Int>()
    val normals = ArrayList<Vector3>()

    /** Adds one triangle with a shared face normal. */
    fun tri(a: Vector3, b: Vector3, c: Vector3, normal: Vector3) {
        val base = positions.size
        positions.add(a)
        positions.add(b)
        positions.add(c)
        normals.add(normal)
        normals.add(normal)
        normals.add(normal)
        indices.add(base)
        indices.add(base + 1)
        indices.add(base + 2)
    }

    /** Adds one quad as two triangles: a-b-c and a-c-d. */
    fun quad(a: Vector3, b: Vector3, c: Vector3, d: Vector3, normal: Vector3) {
        tri(a, b, c, normal)
        tri(a, c, d, normal)
    }

    fun build(): MeshModel = MeshModel(positions, indices, normals)
}

/** Axis-aligned box centered on the origin, [size] per axis. */
fun boxMesh(size: Vector3): MeshModel {
    val hx = size.x / 2f
    val hy = size.y / 2f
    val hz = size.z / 2f
    val b = MeshBuilder()
    // +X face
    b.quad(
        Vector3(hx, -hy, hz), Vector3(hx, -hy, -hz),
        Vector3(hx, hy, -hz), Vector3(hx, hy, hz),
        Vector3(1f, 0f, 0f)
    )
    // -X face
    b.quad(
        Vector3(-hx, -hy, -hz), Vector3(-hx, -hy, hz),
        Vector3(-hx, hy, hz), Vector3(-hx, hy, -hz),
        Vector3(-1f, 0f, 0f)
    )
    // +Y face
    b.quad(
        Vector3(-hx, hy, hz), Vector3(hx, hy, hz),
        Vector3(hx, hy, -hz), Vector3(-hx, hy, -hz),
        Vector3(0f, 1f, 0f)
    )
    // -Y face
    b.quad(
        Vector3(-hx, -hy, -hz), Vector3(hx, -hy, -hz),
        Vector3(hx, -hy, hz), Vector3(-hx, -hy, hz),
        Vector3(0f, -1f, 0f)
    )
    // +Z face
    b.quad(
        Vector3(-hx, -hy, hz), Vector3(hx, -hy, hz),
        Vector3(hx, hy, hz), Vector3(-hx, hy, hz),
        Vector3(0f, 0f, 1f)
    )
    // -Z face
    b.quad(
        Vector3(hx, -hy, -hz), Vector3(-hx, -hy, -hz),
        Vector3(-hx, hy, -hz), Vector3(hx, hy, -hz),
        Vector3(0f, 0f, -1f)
    )
    return b.build()
}

/**
 * Cylinder (or cone when [radiusTop] is 0) centered on the origin, axis = Y,
 * [height] tall, [segments] sides.
 */
fun cylinderMesh(
    radiusTop: Float,
    radiusBottom: Float,
    height: Float,
    segments: Int
): MeshModel {
    val b = MeshBuilder()
    val half = height / 2f
    val ringTop = ArrayList<Vector3>(segments + 1)
    val ringBottom = ArrayList<Vector3>(segments + 1)
    for (i in 0..segments) {
        val a = (2.0 * PI * i / segments).toFloat()
        val ca = cos(a)
        val sa = sin(a)
        ringTop.add(Vector3(radiusTop * ca, half, radiusTop * sa))
        ringBottom.add(Vector3(radiusBottom * ca, -half, radiusBottom * sa))
    }
    for (i in 0 until segments) {
        val a = (2.0 * PI * (i + 0.5f) / segments).toFloat()
        val normal = Vector3(cos(a), 0f, sin(a))
        b.quad(ringBottom[i], ringBottom[i + 1], ringTop[i + 1], ringTop[i], normal)
    }
    // Top cap fan.
    val topCenter = Vector3(0f, half, 0f)
    for (i in 0 until segments) {
        b.tri(topCenter, ringTop[i], ringTop[i + 1], Vector3(0f, 1f, 0f))
    }
    // Bottom cap fan.
    val bottomCenter = Vector3(0f, -half, 0f)
    for (i in 0 until segments) {
        b.tri(bottomCenter, ringBottom[i + 1], ringBottom[i], Vector3(0f, -1f, 0f))
    }
    return b.build()
}

/** UV sphere centered on the origin. */
fun sphereMesh(radius: Float, latSegments: Int, lonSegments: Int): MeshModel {
    val b = MeshBuilder()
    val rings = ArrayList<List<Vector3>>(latSegments + 1)
    for (lat in 0..latSegments) {
        val phi = (PI * lat / latSegments).toFloat()
        val ring = ArrayList<Vector3>(lonSegments + 1)
        for (lon in 0..lonSegments) {
            val theta = (2.0 * PI * lon / lonSegments).toFloat()
            val sp = sin(phi)
            ring.add(
                Vector3(
                    radius * sp * cos(theta),
                    radius * cos(phi),
                    radius * sp * sin(theta)
                )
            )
        }
        rings.add(ring)
    }
    for (lat in 0 until latSegments) {
        for (lon in 0 until lonSegments) {
            val a = rings[lat][lon]
            val c = rings[lat][lon + 1]
            val d = rings[lat + 1][lon + 1]
            val e = rings[lat + 1][lon]
            // Per-vertex normals would be ideal; the face normal of the quad
            // center is a good approximation for unlit rendering.
            val cx = (a.x + c.x + d.x + e.x) / 4f
            val cy = (a.y + c.y + d.y + e.y) / 4f
            val cz = (a.z + c.z + d.z + e.z) / 4f
            val len = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-6f)
            b.quad(a, c, d, e, Vector3(cx / len, cy / len, cz / len))
        }
    }
    return b.build()
}

/**
 * Upper hemisphere (dome) with its equator on the XZ plane (y = 0) and apex
 * at y = [radius]. Latitude/longitude grid — a smooth geodesic-style shell,
 * deliberately not a hexagonal lattice.
 */
fun hemisphereMesh(radius: Float, latSegments: Int, lonSegments: Int): MeshModel {
    val b = MeshBuilder()
    val rings = ArrayList<List<Vector3>>(latSegments + 1)
    for (lat in 0..latSegments) {
        // phi from 0 (apex) to PI/2 (equator).
        val phi = ((PI / 2.0) * lat / latSegments).toFloat()
        val ring = ArrayList<Vector3>(lonSegments + 1)
        for (lon in 0..lonSegments) {
            val theta = (2.0 * PI * lon / lonSegments).toFloat()
            val sp = sin(phi)
            ring.add(
                Vector3(
                    radius * sp * cos(theta),
                    radius * cos(phi),
                    radius * sp * sin(theta)
                )
            )
        }
        rings.add(ring)
    }
    for (lat in 0 until latSegments) {
        for (lon in 0 until lonSegments) {
            val a = rings[lat][lon]
            val c = rings[lat][lon + 1]
            val d = rings[lat + 1][lon + 1]
            val e = rings[lat + 1][lon]
            val cx = (a.x + c.x + d.x + e.x) / 4f
            val cy = (a.y + c.y + d.y + e.y) / 4f
            val cz = (a.z + c.z + d.z + e.z) / 4f
            val len = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-6f)
            b.quad(a, c, d, e, Vector3(cx / len, cy / len, cz / len))
        }
    }
    return b.build()
}

/** Flat disc in the XZ plane, facing +Y, centered on the origin. */
fun discMesh(radius: Float, segments: Int): MeshModel {
    val b = MeshBuilder()
    val center = Vector3(0f, 0f, 0f)
    val ring = ArrayList<Vector3>(segments + 1)
    for (i in 0..segments) {
        val a = (2.0 * PI * i / segments).toFloat()
        ring.add(Vector3(radius * cos(a), 0f, radius * sin(a)))
    }
    for (i in 0 until segments) {
        b.tri(center, ring[i], ring[i + 1], Vector3(0f, 1f, 0f))
    }
    return b.build()
}

/** Flat annulus in the XZ plane, facing +Y, centered on the origin. */
fun ringMesh(innerRadius: Float, outerRadius: Float, segments: Int): MeshModel {
    val b = MeshBuilder()
    val inner = ArrayList<Vector3>(segments + 1)
    val outer = ArrayList<Vector3>(segments + 1)
    for (i in 0..segments) {
        val a = (2.0 * PI * i / segments).toFloat()
        inner.add(Vector3(innerRadius * cos(a), 0f, innerRadius * sin(a)))
        outer.add(Vector3(outerRadius * cos(a), 0f, outerRadius * sin(a)))
    }
    for (i in 0 until segments) {
        b.quad(inner[i], outer[i], outer[i + 1], inner[i + 1], Vector3(0f, 1f, 0f))
    }
    return b.build()
}
