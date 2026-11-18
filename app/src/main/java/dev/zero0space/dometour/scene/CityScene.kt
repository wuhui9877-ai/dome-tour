package dev.zero0space.dometour.scene

import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import dev.zero0space.dometour.ui.DAY_PHASES
import dev.zero0space.dometour.ui.DISTRICTS
import dev.zero0space.dometour.ui.DayPhase
import dev.zero0space.dometour.ui.District
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAG = "DomeTourScene"

/**
 * Scene layout, in scene units (roughly meters). The whole diorama fits under
 * a dome of radius [DOME_RADIUS] centered on the origin. Exact framing inside
 * the SpatialView is unverified — see README; the orbit/zoom controls and the
 * [ROOT_Y_OFFSET] exist to tune it on the emulator.
 */
private const val DOME_RADIUS = 0.36f
private const val DISTRICT_RING_RADIUS = 0.20f
private const val PLATFORM_TOP = 0.014f
private const val ROOT_Y_OFFSET = 0f

/** Courier-drone patrol circle: radius above the ring road, flight height. */
private const val DRONE_ROUTE_RADIUS = 0.27f
private const val DRONE_ROUTE_HEIGHT = 0.17f

/** Gentle airship bob: vertical amplitude in scene units. */
private const val AIRSHIP_BOB_AMPLITUDE = 0.004f
private const val AIRSHIP_BOB_SPEED = 0.6f

/** One moored airship: its root transform, rest position, and bob phase. */
class AirshipHandle(
    val transform: TransformComponent,
    val basePosition: Vector3,
    val phaseOffset: Float
)

/** Handles to the live scene, for per-frame state updates and disposal. */
class CitySceneHandles(
    private val root: Entity,
    private val rootTransform: TransformComponent,
    private val droneTransform: TransformComponent,
    private val rotorTransforms: List<TransformComponent>,
    private val airships: List<AirshipHandle>,
    private val domeMaterial: UnlitMaterial?,
    private val latticeMaterial: UnlitMaterial?,
    private val glowMaterials: List<UnlitMaterial?>,
    private val highlightMaterial: UnlitMaterial?,
    private val highlightTransform: TransformComponent,
    private val meshes: List<MeshResource>,
    private val materials: List<Material>
) {
    /**
     * Applies UI state to the scene. Called from the SpatialView `update`
     * lambda on every recomposition. Null materials (creation failed) are
     * skipped: the affected parts simply stay as they were built.
     */
    fun applyState(
        yawDeg: Float,
        pitchDeg: Float,
        scale: Float,
        selectedDistrict: Int,
        phase: DayPhase
    ) {
        rootTransform.setEulerAngles(EulerAngles(pitchDeg, yawDeg, 0f))
        rootTransform.setScaleVector(Vector3(scale))

        domeMaterial?.setBaseColor(phase.domeColor)
        domeMaterial?.setOpacity(phase.domeOpacity)
        latticeMaterial?.setBaseColor(phase.domeColor)
        latticeMaterial?.setOpacity((phase.domeOpacity + 0.12f).coerceAtMost(0.5f))

        glowMaterials.forEachIndexed { index, material ->
            material?.setOpacity(
                if (index == selectedDistrict) 0.95f
                else 0.15f + 0.45f * phase.glow
            )
        }

        val district = DISTRICTS[selectedDistrict]
        highlightMaterial?.setBaseColor(district.accent)
        val center = districtCenter(district)
        highlightTransform.setPosition(Vector3(center.x, 0.016f, center.z))
    }

    /**
     * Applies per-frame animation, driven from Compose frame callbacks (see
     * DomeTourPage). The drone follows its circular patrol, the rotors spin,
     * and the moored airships bob gently. The caller freezes
     * [droneAngleDeg]/[rotorAngleDeg] while the tour is paused, which parks
     * the drone in place; [timeSec] keeps advancing so the airships stay
     * ambient. Same transform math the ECS patrol system used to run.
     */
    fun applyAnimation(droneAngleDeg: Float, rotorAngleDeg: Float, timeSec: Float) {
        val rad = Math.toRadians(droneAngleDeg.toDouble())
        droneTransform.setPosition(
            Vector3(
                (DRONE_ROUTE_RADIUS * cos(rad)).toFloat(),
                DRONE_ROUTE_HEIGHT,
                (DRONE_ROUTE_RADIUS * sin(rad)).toFloat()
            )
        )
        droneTransform.setEulerAngles(EulerAngles(0f, -droneAngleDeg + 90f, 0f))
        rotorTransforms.forEach { rotor ->
            rotor.setEulerAngles(EulerAngles(0f, rotorAngleDeg, 0f))
        }
        airships.forEach { ship ->
            ship.transform.setPosition(
                Vector3(
                    ship.basePosition.x,
                    ship.basePosition.y +
                        sin(timeSec * AIRSHIP_BOB_SPEED + ship.phaseOffset) *
                        AIRSHIP_BOB_AMPLITUDE,
                    ship.basePosition.z
                )
            )
        }
    }

    fun dispose() {
        try {
            if (root.valid) root.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Entity destroy failed: ${e.message}")
        }
        meshes.forEach { mesh ->
            try {
                if (mesh.valid) mesh.close()
            } catch (e: Exception) {
                Log.w(TAG, "Mesh close failed: ${e.message}")
            }
        }
        materials.forEach { material ->
            try {
                if (material.valid) material.close()
            } catch (e: Exception) {
                Log.w(TAG, "Material close failed: ${e.message}")
            }
        }
    }
}

fun districtCenter(district: District): Vector3 {
    val rad = Math.toRadians(district.angleDeg.toDouble())
    return Vector3(
        (DISTRICT_RING_RADIUS * cos(rad)).toFloat(),
        0f,
        (DISTRICT_RING_RADIUS * sin(rad)).toFloat()
    )
}

/**
 * Builds the whole dome city and adds it to [content]. Returns handles, or
 * null if the scene could not be built at all (individual failed meshes only
 * remove their own parts).
 */
fun buildCity(content: SpatialViewContent): CitySceneHandles? {
    val meshes = ArrayList<MeshResource>()
    val materials = ArrayList<Material>()

    fun mesh(model: com.pico.spatial.core.ecs.resource.MeshModel): MeshResource? {
        val resource = meshResource(model)
        if (resource == null) {
            Log.w(TAG, "A shared mesh failed to build; some parts will be missing")
        } else {
            meshes.add(resource)
        }
        return resource
    }

    // Shared unit-size meshes, instanced through entity scale.
    val unitBox = mesh(boxMesh(Vector3(1f, 1f, 1f)))
    val unitCylinder = mesh(cylinderMesh(1f, 1f, 1f, 12))
    val unitCone = mesh(cylinderMesh(0f, 1f, 1f, 12))
    val unitSphere = mesh(sphereMesh(1f, 10, 16))
    // Final-size meshes.
    val domeMesh = mesh(hemisphereMesh(DOME_RADIUS, 8, 24))
    val roadRingMesh = mesh(ringMesh(0.155f, 0.245f, 48))
    val highlightRingMesh = mesh(ringMesh(0.082f, 0.094f, 40))
    val waterDiscMesh = mesh(discMesh(0.03f, 24))

    // Materials. Null results (creation failed) are tracked as null and the
    // affected parts are skipped when building entities.
    fun <T : Material> track(material: T?): T? {
        if (material != null) materials.add(material)
        return material
    }

    val groundMat = track(opaqueMaterial(rgb(0x2A3140)))
    val plinthMat = track(opaqueMaterial(rgb(0x1C2230)))
    val roadMat = track(opaqueMaterial(rgb(0x232A38)))
    val plazaMat = track(opaqueMaterial(rgb(0x3A4356)))
    val platformMat = track(opaqueMaterial(rgb(0x333C4E)))
    val stationMat = track(opaqueMaterial(rgb(0x4A5468)))
    val spireMat = track(opaqueMaterial(rgb(0x6A7690)))
    val craneMat = track(opaqueMaterial(rgb(0x54606E)))
    val airshipMat = track(opaqueMaterial(rgb(0x9AA6B8)))
    val treeMat = track(opaqueMaterial(rgb(0x3E6E4A)))
    val waterMat = track(transparentMaterial(rgb(0x9FE8C8), 0.55f))
    val spireTipMat = track(glowMaterial(rgb(0x6AF2FF), 0.9f))
    val droneBodyMat = track(opaqueMaterial(rgb(0x8A94A8)))
    val droneGlowMat = track(glowMaterial(rgb(0x6AF2FF), 0.9f))

    val night = DAY_PHASES[3]
    val domeMat = track(transparentMaterial(night.domeColor, night.domeOpacity))
    val latticeMat = track(latticeMaterial(night.domeColor, night.domeOpacity + 0.12f))
    val highlightMat = track(glowMaterial(DISTRICTS[0].accent, 0.9f))

    val bodyMats = DISTRICTS.map { track(opaqueMaterial(it.body)) }
    val bodyDarkMats = DISTRICTS.map { track(opaqueMaterial(it.bodyDark)) }
    val glowMats = DISTRICTS.map { track(glowMaterial(it.accent, 0.6f)) }

    // Entity helpers.
    fun modelEntity(
        meshResource: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ): Entity? {
        if (meshResource == null || material == null) return null
        return Entity().apply {
            components.set(ModelComponent(meshResource, material))
            components.set(TransformComponent(position, euler, scale))
        }
    }

    // Like modelEntity, but also returns the transform so per-frame animation
    // (rotor spin) can drive it. Null pair entries mean the part was skipped.
    fun trackedEntity(
        meshResource: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ): Pair<Entity?, TransformComponent?> {
        if (meshResource == null || material == null) return null to null
        val transform = TransformComponent(position, euler, scale)
        val entity = Entity().apply {
            components.set(ModelComponent(meshResource, material))
            components.set(transform)
        }
        return entity to transform
    }

    // Moored-airship handles, filled in by the Fog Harbor builder.
    val airshipHandles = ArrayList<AirshipHandle>()

    // Root: everything hangs off this entity so orbit/zoom apply to the whole
    // diorama with one transform.
    val rootTransform = TransformComponent(
        Vector3(0f, ROOT_Y_OFFSET, 0f),
        EulerAngles(),
        Vector3(1f)
    )
    val root = Entity().apply { components.set(rootTransform) }

    // Diorama base: plinth + ground + ring road + center plaza + spire.
    root.addChildOrSkip(
        modelEntity(
            unitCylinder, plinthMat,
            Vector3(0f, -0.03f, 0f), scale = Vector3(0.36f, 0.02f, 0.36f)
        )
    )
    root.addChildOrSkip(
        modelEntity(
            unitCylinder, groundMat,
            Vector3(0f, -0.01f, 0f), scale = Vector3(0.34f, 0.02f, 0.34f)
        )
    )
    root.addChildOrSkip(
        modelEntity(roadRingMesh, roadMat, Vector3(0f, 0.002f, 0f))
    )
    root.addChildOrSkip(
        modelEntity(
            unitCylinder, plazaMat,
            Vector3(0f, 0.007f, 0f), scale = Vector3(0.085f, 0.014f, 0.085f)
        )
    )
    root.addChildOrSkip(
        modelEntity(
            unitCylinder, spireMat,
            Vector3(0f, 0.114f, 0f), scale = Vector3(0.01f, 0.20f, 0.01f)
        )
    )
    root.addChildOrSkip(
        modelEntity(
            unitSphere, spireTipMat,
            Vector3(0f, 0.224f, 0f), scale = Vector3(0.018f)
        )
    )
    // Plaza pylons.
    for (i in 0 until 4) {
        val a = Math.toRadians((90.0 * i)).toFloat()
        val px = 0.06f * cos(a)
        val pz = 0.06f * sin(a)
        root.addChildOrSkip(
            modelEntity(
                unitBox, stationMat,
                Vector3(px, 0.029f, pz), scale = Vector3(0.008f, 0.03f, 0.008f)
            )
        )
        root.addChildOrSkip(
            modelEntity(
                unitSphere, spireTipMat,
                Vector3(px, 0.048f, pz), scale = Vector3(0.005f)
            )
        )
    }
    // Ring-road stations between districts.
    for (i in 0 until 4) {
        val a = Math.toRadians((90.0 * i)).toFloat()
        val px = 0.20f * cos(a)
        val pz = 0.20f * sin(a)
        root.addChildOrSkip(
            modelEntity(
                unitBox, stationMat,
                Vector3(px, 0.008f, pz),
                EulerAngles(0f, Math.toDegrees(a.toDouble()).toFloat(), 0f),
                Vector3(0.03f, 0.012f, 0.02f)
            )
        )
        root.addChildOrSkip(
            modelEntity(
                unitBox, spireTipMat,
                Vector3(px, 0.015f, pz),
                EulerAngles(0f, Math.toDegrees(a.toDouble()).toFloat(), 0f),
                Vector3(0.026f, 0.002f, 0.016f)
            )
        )
    }

    // Districts.
    DISTRICTS.forEach { district ->
        buildDistrict(
            root = root,
            district = district,
            modelEntity = ::modelEntity,
            airshipHandles = airshipHandles,
            unitBox = unitBox,
            unitCylinder = unitCylinder,
            unitCone = unitCone,
            unitSphere = unitSphere,
            waterDiscMesh = waterDiscMesh,
            platformMat = platformMat,
            bodyMat = bodyMats[district.index],
            bodyDarkMat = bodyDarkMats[district.index],
            glowMat = glowMats[district.index],
            craneMat = craneMat,
            airshipMat = airshipMat,
            treeMat = treeMat,
            waterMat = waterMat
        )
    }

    // Dome shell + lattice overlay.
    root.addChildOrSkip(modelEntity(domeMesh, domeMat, Vector3(0f, 0f, 0f)))
    root.addChildOrSkip(modelEntity(domeMesh, latticeMat, Vector3(0f, 0f, 0f)))

    // Selection highlight ring (repositioned by applyState).
    val highlightTransform = TransformComponent(
        Vector3(districtCenter(DISTRICTS[0]).x, 0.016f, districtCenter(DISTRICTS[0]).z),
        EulerAngles(),
        Vector3(1f)
    )
    val highlightRing = if (highlightRingMesh != null && highlightMat != null) {
        Entity().apply {
            components.set(ModelComponent(highlightRingMesh, highlightMat))
            components.set(highlightTransform)
        }
    } else {
        null
    }
    root.addChildOrSkip(highlightRing)

    // Courier drone. Driven per-frame from Compose frame callbacks (see
    // DomeTourPage), not by an ECS system: the drone transform, the four
    // rotor transforms, and the airship handles below are all plain handles.
    val droneTransform = TransformComponent(
        Vector3(DRONE_ROUTE_RADIUS, DRONE_ROUTE_HEIGHT, 0f),
        EulerAngles(),
        Vector3(1f)
    )
    val drone = Entity().apply { components.set(droneTransform) }
    drone.addChildOrSkip(
        modelEntity(unitBox, droneBodyMat, Vector3(0f, 0f, 0f), scale = Vector3(0.03f, 0.012f, 0.018f))
    )
    drone.addChildOrSkip(
        modelEntity(unitBox, droneBodyMat, Vector3(0f, 0.004f, 0f), scale = Vector3(0.044f, 0.002f, 0.004f))
    )
    drone.addChildOrSkip(
        modelEntity(unitBox, droneBodyMat, Vector3(0f, 0.004f, 0f), scale = Vector3(0.004f, 0.002f, 0.028f))
    )
    val rotorTransforms = ArrayList<TransformComponent>()
    for (sx in listOf(-1f, 1f)) {
        for (sz in listOf(-1f, 1f)) {
            val (rotor, rotorTransform) = trackedEntity(
                unitCylinder, droneBodyMat,
                Vector3(0.02f * sx, 0.008f, 0.012f * sz),
                scale = Vector3(0.011f, 0.0015f, 0.011f)
            )
            drone.addChildOrSkip(rotor)
            if (rotorTransform != null) rotorTransforms.add(rotorTransform)
        }
    }
    drone.addChildOrSkip(
        modelEntity(unitSphere, droneGlowMat, Vector3(0f, -0.008f, 0f), scale = Vector3(0.006f))
    )
    root.addChild(drone)

    content.addEntity(root)

    return CitySceneHandles(
        root = root,
        rootTransform = rootTransform,
        droneTransform = droneTransform,
        rotorTransforms = rotorTransforms,
        airships = airshipHandles,
        domeMaterial = domeMat,
        latticeMaterial = latticeMat,
        glowMaterials = glowMats,
        highlightMaterial = highlightMat,
        highlightTransform = highlightTransform,
        meshes = meshes,
        materials = materials
    )
}

private fun Entity.addChildOrSkip(child: Entity?) {
    if (child != null) addChild(child)
}

private fun buildDistrict(
    root: Entity,
    district: District,
    modelEntity: (
        MeshResource?, Material?, Vector3, EulerAngles, Vector3
    ) -> Entity?,
    airshipHandles: MutableList<AirshipHandle>,
    unitBox: MeshResource?,
    unitCylinder: MeshResource?,
    unitCone: MeshResource?,
    unitSphere: MeshResource?,
    waterDiscMesh: MeshResource?,
    platformMat: Material?,
    bodyMat: Material?,
    bodyDarkMat: Material?,
    glowMat: Material?,
    craneMat: Material?,
    airshipMat: Material?,
    treeMat: Material?,
    waterMat: Material?
) {
    val center = districtCenter(district)
    val random = Random(1000 + district.index * 7919)

    fun place(
        mesh: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ) {
        root.addChildOrSkip(modelEntity(mesh, material, position, euler, scale))
    }

    // District platform.
    place(
        unitCylinder, platformMat,
        Vector3(center.x, 0.007f, center.z),
        scale = Vector3(0.075f, 0.014f, 0.075f)
    )

    // Building cluster. 弦塔 (index 2) gets taller, thinner towers.
    val tall = district.index == 2
    data class Building(val x: Float, val z: Float, val w: Float, val d: Float, val h: Float)
    val buildings = ArrayList<Building>()
    repeat(7) { i ->
        val a = random.nextFloat() * 2f * PI.toFloat()
        val r = 0.015f + random.nextFloat() * 0.032f
        val w = if (tall) 0.014f + random.nextFloat() * 0.008f else 0.018f + random.nextFloat() * 0.014f
        val d = if (tall) 0.014f + random.nextFloat() * 0.008f else 0.018f + random.nextFloat() * 0.014f
        val h = if (tall) 0.05f + random.nextFloat() * 0.11f else 0.03f + random.nextFloat() * 0.07f
        val bx = center.x + r * cos(a)
        val bz = center.z + r * sin(a)
        buildings.add(Building(bx, bz, w, d, h))
        place(
            unitBox, if (i % 2 == 0) bodyMat else bodyDarkMat,
            Vector3(bx, PLATFORM_TOP + h / 2f, bz),
            EulerAngles(0f, random.nextFloat() * 30f - 15f, 0f),
            Vector3(w, h, d)
        )
    }

    // Window lights: small glow boxes on random building faces.
    buildings.forEach { b ->
        repeat(1 + random.nextInt(2)) {
            val onXFace = random.nextBoolean()
            val sign = if (random.nextBoolean()) 1f else -1f
            val y = PLATFORM_TOP + b.h * (0.3f + random.nextFloat() * 0.6f)
            if (onXFace) {
                place(
                    unitBox, glowMat,
                    Vector3(b.x + sign * (b.w / 2f + 0.001f), y, b.z),
                    scale = Vector3(0.002f, 0.004f, 0.006f)
                )
            } else {
                place(
                    unitBox, glowMat,
                    Vector3(b.x, y, b.z + sign * (b.d / 2f + 0.001f)),
                    scale = Vector3(0.006f, 0.004f, 0.002f)
                )
            }
        }
    }

    // Signature structures per district.
    when (district.index) {
        0 -> buildFogHarbor(
            center, random, ::place, unitBox, unitCylinder, unitSphere,
            craneMat, airshipMat, glowMat, modelEntity, root, airshipHandles
        )
        1 -> buildLanternMarket(center, random, ::place, unitBox, unitCylinder, unitSphere, bodyMat, bodyDarkMat, glowMat)
        2 -> buildStringTowers(center, ::place, unitBox, unitCylinder, bodyMat, bodyDarkMat, glowMat)
        3 -> buildQuietCourt(center, random, ::place, unitBox, unitCylinder, unitCone, unitSphere, waterDiscMesh, bodyMat, bodyDarkMat, treeMat, waterMat, glowMat)
    }
}

private fun buildFogHarbor(
    center: Vector3,
    random: Random,
    placeFn: (MeshResource?, Material?, Vector3, EulerAngles, Vector3) -> Unit,
    unitBox: MeshResource?,
    unitCylinder: MeshResource?,
    unitSphere: MeshResource?,
    craneMat: Material?,
    airshipMat: Material?,
    glowMat: Material?,
    modelEntity: (MeshResource?, Material?, Vector3, EulerAngles, Vector3) -> Entity?,
    root: Entity,
    airshipHandles: MutableList<AirshipHandle>
) {
    // Local wrapper restoring default arguments (lost on function references).
    fun place(
        mesh: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ) = placeFn(mesh, material, position, euler, scale)
    // Two dock cranes: mast + arm + hanging cable + hook.
    val craneSpecs = listOf(
        Triple(0.035f, 200f, 30f),
        Triple(0.04f, 320f, -40f)
    )
    craneSpecs.forEach { (dist, angleDeg, yawDeg) ->
        val a = Math.toRadians(angleDeg.toDouble())
        val mx = center.x + dist * cos(a).toFloat()
        val mz = center.z + dist * sin(a).toFloat()
        val mastH = 0.09f
        place(unitBox, craneMat, Vector3(mx, PLATFORM_TOP + mastH / 2f, mz), scale = Vector3(0.006f, mastH, 0.006f))
        val topY = PLATFORM_TOP + mastH
        place(
            unitBox, craneMat, Vector3(mx, topY, mz),
            EulerAngles(0f, yawDeg, 0f), Vector3(0.05f, 0.005f, 0.005f)
        )
        // Cable hangs from the arm's +X end. Rotation about +Y by yaw maps
        // +X to (cos yaw, 0, -sin yaw).
        val yawRad = Math.toRadians(yawDeg.toDouble())
        val ex = mx + 0.022f * cos(yawRad).toFloat()
        val ez = mz - 0.022f * sin(yawRad).toFloat()
        place(unitBox, craneMat, Vector3(ex, topY - 0.015f, ez), scale = Vector3(0.0015f, 0.03f, 0.0015f))
        place(unitBox, glowMat, Vector3(ex, topY - 0.032f, ez), scale = Vector3(0.005f, 0.005f, 0.005f))
    }
    // Two moored airships. Each ship hangs off its own parent entity so the
    // frame-callback animation can bob the whole ship by moving one
    // transform; children keep the same local offsets as before.
    val shipSpecs = listOf(
        Triple(0.02f, 0.155f, 1f),
        Triple(-0.025f, 0.185f, 0.7f)
    )
    shipSpecs.forEachIndexed { shipIndex, (dx, y, s) ->
        val sx = center.x + dx
        val sz = center.z - 0.01f + random.nextFloat() * 0.02f
        val shipTransform = TransformComponent(
            Vector3(sx, y, sz),
            EulerAngles(),
            Vector3(1f)
        )
        val ship = Entity().apply { components.set(shipTransform) }
        ship.addChildOrSkip(
            modelEntity(
                unitSphere, airshipMat, Vector3(0f, 0f, 0f), EulerAngles(),
                Vector3(0.035f * s, 0.013f * s, 0.013f * s)
            )
        )
        ship.addChildOrSkip(
            modelEntity(
                unitBox, craneMat, Vector3(0f, -0.013f * s, 0f), EulerAngles(),
                Vector3(0.014f * s, 0.006f, 0.006f)
            )
        )
        ship.addChildOrSkip(
            modelEntity(
                unitBox, glowMat, Vector3(0f, -0.017f * s, 0f), EulerAngles(),
                Vector3(0.004f, 0.003f, 0.004f)
            )
        )
        root.addChild(ship)
        airshipHandles.add(AirshipHandle(shipTransform, Vector3(sx, y, sz), shipIndex * 2.1f))
    }
}

private fun buildLanternMarket(
    center: Vector3,
    random: Random,
    placeFn: (MeshResource?, Material?, Vector3, EulerAngles, Vector3) -> Unit,
    unitBox: MeshResource?,
    unitCylinder: MeshResource?,
    unitSphere: MeshResource?,
    bodyMat: Material?,
    bodyDarkMat: Material?,
    glowMat: Material?
) {
    fun place(
        mesh: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ) = placeFn(mesh, material, position, euler, scale)
    // Lantern poles in a loose ring.
    repeat(8) { i ->
        val a = (2.0 * PI * i / 8 + random.nextFloat() * 0.3).toFloat()
        val r = 0.03f + random.nextFloat() * 0.02f
        val px = center.x + r * cos(a)
        val pz = center.z + r * sin(a)
        place(
            unitCylinder, bodyDarkMat,
            Vector3(px, PLATFORM_TOP + 0.025f, pz),
            scale = Vector3(0.0018f, 0.05f, 0.0018f)
        )
        place(unitSphere, glowMat, Vector3(px, PLATFORM_TOP + 0.055f, pz), scale = Vector3(0.008f))
    }
    // Market stalls with glowing awning strips.
    repeat(4) { i ->
        val a = (2.0 * PI * i / 4 + 0.4).toFloat()
        val px = center.x + 0.045f * cos(a)
        val pz = center.z + 0.045f * sin(a)
        place(unitBox, bodyMat, Vector3(px, PLATFORM_TOP + 0.005f, pz), scale = Vector3(0.014f, 0.01f, 0.014f))
        place(unitBox, glowMat, Vector3(px, PLATFORM_TOP + 0.011f, pz), scale = Vector3(0.018f, 0.002f, 0.018f))
    }
}

private fun buildStringTowers(
    center: Vector3,
    placeFn: (MeshResource?, Material?, Vector3, EulerAngles, Vector3) -> Unit,
    unitBox: MeshResource?,
    unitCylinder: MeshResource?,
    bodyMat: Material?,
    bodyDarkMat: Material?,
    glowMat: Material?
) {
    fun place(
        mesh: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ) = placeFn(mesh, material, position, euler, scale)
    // Three resonant towers; glowing strings join their tops.
    val heights = listOf(0.13f, 0.16f, 0.19f)
    val tops = ArrayList<Vector3>()
    heights.forEachIndexed { i, h ->
        val a = Math.toRadians(90.0 + 120.0 * i)
        val tx = center.x + 0.025f * cos(a).toFloat()
        val tz = center.z + 0.025f * sin(a).toFloat()
        place(
            unitCylinder, if (i % 2 == 0) bodyMat else bodyDarkMat,
            Vector3(tx, PLATFORM_TOP + h / 2f, tz),
            scale = Vector3(0.007f, h, 0.007f)
        )
        val top = Vector3(tx, PLATFORM_TOP + h, tz)
        tops.add(top)
        // Vertical drop-string from the tower top.
        place(
            unitBox, glowMat,
            Vector3(tx, PLATFORM_TOP + h - 0.025f, tz),
            scale = Vector3(0.0015f, 0.05f, 0.0015f)
        )
        place(unitBox, glowMat, top, scale = Vector3(0.006f, 0.004f, 0.006f))
    }
    // Horizontal strings between tower tops.
    for (i in tops.indices) {
        for (j in i + 1 until tops.size) {
            val a = tops[i]
            val b = tops[j]
            val dx = b.x - a.x
            val dz = b.z - a.z
            val len = kotlin.math.sqrt(dx * dx + dz * dz)
            if (len < 1e-4f) continue
            // Yaw that rotates +X onto (dx, dz): rotation about +Y by yaw
            // maps +X to (cos yaw, 0, -sin yaw).
            val yawDeg = Math.toDegrees(atan2(-dz.toDouble(), dx.toDouble())).toFloat()
            place(
                unitBox, glowMat,
                Vector3((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f),
                EulerAngles(0f, yawDeg, 0f),
                Vector3(len, 0.002f, 0.002f)
            )
        }
    }
}

private fun buildQuietCourt(
    center: Vector3,
    random: Random,
    placeFn: (MeshResource?, Material?, Vector3, EulerAngles, Vector3) -> Unit,
    unitBox: MeshResource?,
    unitCylinder: MeshResource?,
    unitCone: MeshResource?,
    unitSphere: MeshResource?,
    waterDiscMesh: MeshResource?,
    bodyMat: Material?,
    bodyDarkMat: Material?,
    treeMat: Material?,
    waterMat: Material?,
    glowMat: Material?
) {
    fun place(
        mesh: MeshResource?,
        material: Material?,
        position: Vector3,
        euler: EulerAngles = EulerAngles(),
        scale: Vector3 = Vector3(1f)
    ) = placeFn(mesh, material, position, euler, scale)
    // Still-water disc.
    place(waterDiscMesh, waterMat, Vector3(center.x + 0.02f, PLATFORM_TOP + 0.001f, center.z + 0.015f))
    // Garden trees: trunk + cone canopy.
    repeat(3) { i ->
        val a = Math.toRadians(30.0 + 120.0 * i + random.nextFloat() * 20f)
        val tx = center.x + 0.04f * cos(a).toFloat()
        val tz = center.z + 0.04f * sin(a).toFloat()
        place(
            unitCylinder, bodyDarkMat,
            Vector3(tx, PLATFORM_TOP + 0.01f, tz),
            scale = Vector3(0.003f, 0.02f, 0.003f)
        )
        place(
            unitCone, treeMat,
            Vector3(tx, PLATFORM_TOP + 0.037f, tz),
            scale = Vector3(0.012f, 0.035f, 0.012f)
        )
    }
    // Two low pavilions with cone roofs.
    repeat(2) { i ->
        val a = Math.toRadians(150.0 + 180.0 * i)
        val px = center.x + 0.035f * cos(a).toFloat()
        val pz = center.z + 0.035f * sin(a).toFloat()
        place(unitBox, bodyMat, Vector3(px, PLATFORM_TOP + 0.007f, pz), scale = Vector3(0.02f, 0.014f, 0.02f))
        place(
            unitCone, bodyDarkMat,
            Vector3(px, PLATFORM_TOP + 0.02f, pz),
            scale = Vector3(0.016f, 0.012f, 0.016f)
        )
    }
    // Stone lanterns with a soft glow.
    repeat(2) { i ->
        val a = Math.toRadians(250.0 + 100.0 * i)
        val px = center.x + 0.05f * cos(a).toFloat()
        val pz = center.z + 0.05f * sin(a).toFloat()
        place(unitBox, bodyDarkMat, Vector3(px, PLATFORM_TOP + 0.006f, pz), scale = Vector3(0.006f, 0.012f, 0.006f))
        place(unitSphere, glowMat, Vector3(px, PLATFORM_TOP + 0.015f, pz), scale = Vector3(0.004f))
    }
}
