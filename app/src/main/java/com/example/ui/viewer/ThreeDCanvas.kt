package com.example.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

private data class ProjectedTriangle(
    val x0: Float, val y0: Float,
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val depth: Float,
    val shadedColor: Color
)

private data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float,
    val radius: Float,
    val color: Color
)

@Composable
fun ThreeDCanvas(
    viewModel: ModelViewModel,
    modifier: Modifier = Modifier
) {
    val activeModel by viewModel.activeModel.collectAsState()
    val renderMode by viewModel.renderMode.collectAsState()
    val pointSize by viewModel.pointSize.collectAsState()
    val lightIntensity by viewModel.lightIntensity.collectAsState()
    val autoRotate by viewModel.autoRotate.collectAsState()
    val gridVisible by viewModel.gridVisible.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()

    var currentMesh by remember { mutableStateOf<Mesh3D?>(null) }

    // Spherical orbit camera coordinates around target
    var camTarget by remember { mutableStateOf(Vec3(0f, 0f, 0f)) }
    var camYaw by remember { mutableFloatStateOf(0.6f) }     // radians around Y
    var camPitch by remember { mutableFloatStateOf(0.35f) }  // radians elevation
    var camDistance by remember { mutableFloatStateOf(12.5f) }
    var modelRotationY by remember { mutableFloatStateOf(0f) }

    // Helper to compute Cartesian camera position from spherical orbit parameters
    fun computeCameraPos(target: Vec3, yaw: Float, pitch: Float, dist: Float): Vec3 {
        val clampedPitch = pitch.coerceIn(-1.45f, 1.45f)
        val clampedDist = dist.coerceIn(0.8f, 80f)
        val x = target.x + clampedDist * cos(clampedPitch) * sin(yaw)
        val y = target.y + clampedDist * sin(clampedPitch)
        val z = target.z + clampedDist * cos(clampedPitch) * cos(yaw)
        return Vec3(x, y, z)
    }

    fun syncCameraToViewModel(target: Vec3 = camTarget, yaw: Float = camYaw, pitch: Float = camPitch, dist: Float = camDistance) {
        val pos = computeCameraPos(target, yaw, pitch, dist)
        viewModel.updateCameraPosition(pos.x, pos.y, pos.z, target.x, target.y, target.z)
    }

    fun setCameraFromCartesian(pos: Vec3, target: Vec3) {
        val diff = pos - target
        val dist = diff.length().coerceIn(0.8f, 80f)
        val pitch = asin((diff.y / dist).coerceIn(-0.99f, 0.99f))
        val yaw = atan2(diff.x, diff.z)
        camTarget = target
        camDistance = dist
        camPitch = pitch
        camYaw = yaw
        viewModel.updateCameraPosition(pos.x, pos.y, pos.z, target.x, target.y, target.z)
    }

    // Load active model on change
    LaunchedEffect(activeModel) {
        val model = activeModel ?: return@LaunchedEffect
        viewModel.setModelLoading(true)
        viewModel.setError(null)
        try {
            val loadedMesh = withContext(Dispatchers.Default) {
                if (model.isSample) {
                    if (model.sampleAssetPath == "scan" || model.type == "ply") {
                        Scene3DEngine.generateLidarCorridorScene()
                    } else {
                        Scene3DEngine.generateStationScene()
                    }
                } else {
                    val file = File(model.fileUri)
                    if (!file.exists()) {
                        throw IllegalArgumentException("Model file not found on device.")
                    }
                    val bytes = file.readBytes()
                    if (model.type.lowercase() == "ply") {
                        Scene3DEngine.parsePlyBytes(bytes)
                    } else {
                        Scene3DEngine.parseGlbBytes(bytes)
                    }
                }
            }
            currentMesh = loadedMesh
            modelRotationY = 0f
            setCameraFromCartesian(loadedMesh.defaultCameraPos, loadedMesh.defaultCameraTarget)
            viewModel.updateStats(loadedMesh.vertexCount, loadedMesh.polygonCount)
            viewModel.setModelLoading(false)
        } catch (e: Exception) {
            viewModel.setError(e.message ?: "Failed to parse 3D model.")
            viewModel.setModelLoading(false)
        }
    }

    // Smooth camera animation when user selects a SavedViewpoint
    LaunchedEffect(Unit) {
        viewModel.navigateEvent.collectLatest { vp ->
            val startPos = computeCameraPos(camTarget, camYaw, camPitch, camDistance)
            val startTarget = camTarget
            val endPos = Vec3(vp.posX, vp.posY, vp.posZ)
            val endTarget = Vec3(vp.targetX, vp.targetY, vp.targetZ)

            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            ) {
                val t = value
                val curPos = Vec3(
                    startPos.x + (endPos.x - startPos.x) * t,
                    startPos.y + (endPos.y - startPos.y) * t,
                    startPos.z + (endPos.z - startPos.z) * t
                )
                val curTarget = Vec3(
                    startTarget.x + (endTarget.x - startTarget.x) * t,
                    startTarget.y + (endTarget.y - startTarget.y) * t,
                    startTarget.z + (endTarget.z - startTarget.z) * t
                )
                setCameraFromCartesian(curPos, curTarget)
            }
        }
    }

    // Continuous 60 FPS rotation loop when autoRotate is enabled
    LaunchedEffect(autoRotate) {
        if (!autoRotate) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && autoRotate) {
            val frameNanos = withFrameNanos { it }
            val dtSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            lastFrameNanos = frameNanos
            modelRotationY = (modelRotationY + dtSeconds * 0.65f) % (PI.toFloat() * 2f)
        }
    }

    val bgColor = remember(backgroundColor) {
        try {
            Color(android.graphics.Color.parseColor(backgroundColor))
        } catch (_: Exception) {
            Color(0xFF0D0F14)
        }
    }

    val pathScratch = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (abs(zoom - 1f) > 0.005f) {
                        // Pinch zoom
                        camDistance = (camDistance / zoom).coerceIn(1.0f, 60f)
                    }
                    // Orbit camera with drag
                    camYaw -= pan.x * 0.0075f
                    camPitch = (camPitch + pan.y * 0.0075f).coerceIn(-1.45f, 1.45f)
                    syncCameraToViewModel(camTarget, camYaw, camPitch, camDistance)
                }
            }
            .testTag("three_d_viewport_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 1f || height <= 1f) return@Canvas

            val camPos = computeCameraPos(camTarget, camYaw, camPitch, camDistance)
            val forward = (camTarget - camPos).normalized()
            val worldUp = Vec3(0f, 1f, 0f)
            val right = forward.cross(worldUp).let {
                if (it.length() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
            }
            val up = right.cross(forward).normalized()

            val fovScale = (min(width, height) * 0.92f)
            val centerX = width * 0.5f
            val centerY = height * 0.5f

            fun projectPoint(worldPt: Vec3): Vec3? {
                val rel = worldPt - camPos
                val vx = rel.dot(right)
                val vy = rel.dot(up)
                val vz = rel.dot(forward)
                if (vz <= 0.15f) return null
                val invZ = fovScale / vz
                val sx = centerX + vx * invZ
                val sy = centerY - vy * invZ
                return Vec3(sx, sy, vz)
            }

            // 1. Render 3D Coordinate & Polar Floor Grids if enabled
            if (gridVisible) {
                drawFloorGrids(::projectPoint)
            }

            // 2. Render Active 3D Mesh / Point Cloud
            val mesh = currentMesh
            if (mesh != null) {
                val effectiveMode = if (mesh.faces.isEmpty()) "points" else renderMode
                if (effectiveMode == "points") {
                    drawPointCloud(
                        mesh = mesh,
                        modelRotationY = modelRotationY,
                        pointSize = pointSize,
                        fovScale = fovScale,
                        lightIntensity = lightIntensity,
                        project = ::projectPoint
                    )
                } else {
                    drawMeshFaces(
                        mesh = mesh,
                        modelRotationY = modelRotationY,
                        wireframe = (effectiveMode == "wireframe"),
                        lightIntensity = lightIntensity,
                        pathScratch = pathScratch,
                        project = ::projectPoint
                    )
                }
            }

            // 3. Draw 3D Orientation Axis Gizmo in top-right (below Target Focus card)
            drawAxisGizmo(
                right = right,
                up = up,
                forward = forward,
                gizmoCenter = Offset(width - 56.dp.toPx(), 110.dp.toPx()),
                axisRadius = 26.dp.toPx()
            )
        }

        // Bottom-Right Quick Camera Navigation Controls (Zoom In / Zoom Out / Reset View)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SpaceCardBg)
                .border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    camDistance = (camDistance * 0.82f).coerceIn(1.0f, 60f)
                    syncCameraToViewModel()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = NeonCyan
                )
            }

            IconButton(
                onClick = {
                    camDistance = (camDistance * 1.22f).coerceIn(1.0f, 60f)
                    syncCameraToViewModel()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = NeonCyan
                )
            }

            IconButton(
                onClick = {
                    val mesh = currentMesh
                    if (mesh != null) {
                        modelRotationY = 0f
                        setCameraFromCartesian(mesh.defaultCameraPos, mesh.defaultCameraTarget)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("reset_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Reset Camera View",
                    tint = NeonGreen
                )
            }
        }

        // Bottom-Left Viewport Status Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpaceCardBg)
                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val modeLabel = renderMode.uppercase()
            Text(
                text = "ENGINE: NATIVE 3D • MODE: $modeLabel",
                color = NeonCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun DrawScope.drawFloorGrids(
    projectPoint: (Vec3) -> Vec3?
) {
    val gridY = -2.25f
    val gridExtent = 14
    val step = 2
    val gridLineColor = Color(0xFF00BFFF).copy(alpha = 0.14f)
    val axisLineColor = Color(0xFF00FFCC).copy(alpha = 0.28f)

    var i = -gridExtent
    while (i <= gridExtent) {
        val isCenter = (i == 0)
        val col = if (isCenter) axisLineColor else gridLineColor

        // Line along Z
        drawSegmented3DLine(
            a = Vec3(i.toFloat(), gridY, -gridExtent.toFloat()),
            b = Vec3(i.toFloat(), gridY, gridExtent.toFloat()),
            segments = 8,
            color = col,
            strokeWidth = if (isCenter) 2f else 1f,
            projectPoint = projectPoint
        )

        // Line along X
        drawSegmented3DLine(
            a = Vec3(-gridExtent.toFloat(), gridY, i.toFloat()),
            b = Vec3(gridExtent.toFloat(), gridY, i.toFloat()),
            segments = 8,
            color = col,
            strokeWidth = if (isCenter) 2f else 1f,
            projectPoint = projectPoint
        )
        i += step
    }

    // Polar radar rings on the floor
    val ringRadii = floatArrayOf(4f, 8f, 12f)
    val ringSegments = 36
    val polarColor = Color(0xFF00FFCC).copy(alpha = 0.16f)
    for (r in ringRadii) {
        var prevProj: Vec3? = null
        for (s in 0..ringSegments) {
            val angle = (s.toFloat() / ringSegments) * (PI.toFloat() * 2f)
            val pt = Vec3(cos(angle) * r, gridY, sin(angle) * r)
            val proj = projectPoint(pt)
            if (prevProj != null && proj != null) {
                drawLine(
                    color = polarColor,
                    start = Offset(prevProj.x, prevProj.y),
                    end = Offset(proj.x, proj.y),
                    strokeWidth = 1.2f
                )
            }
            prevProj = proj
        }
    }
}

private fun DrawScope.drawSegmented3DLine(
    a: Vec3,
    b: Vec3,
    segments: Int,
    color: Color,
    strokeWidth: Float,
    projectPoint: (Vec3) -> Vec3?
) {
    var prev: Vec3? = projectPoint(a)
    for (s in 1..segments) {
        val t = s.toFloat() / segments
        val pt = Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        )
        val curr = projectPoint(pt)
        if (prev != null && curr != null) {
            drawLine(
                color = color,
                start = Offset(prev.x, prev.y),
                end = Offset(curr.x, curr.y),
                strokeWidth = strokeWidth
            )
        }
        prev = curr
    }
}

private fun DrawScope.drawMeshFaces(
    mesh: Mesh3D,
    modelRotationY: Float,
    wireframe: Boolean,
    lightIntensity: Float,
    pathScratch: Path,
    project: (Vec3) -> Vec3?
) {
    val transformedVertices = if (abs(modelRotationY) > 1e-4f) {
        mesh.vertices.map { it.rotateY(modelRotationY) }
    } else {
        mesh.vertices
    }

    val projectedVerts = transformedVertices.map { project(it) }
    val lightDir = Vec3(0.45f, 0.78f, 0.43f).normalized()
    val fillLightDir = Vec3(-0.5f, -0.3f, -0.8f).normalized()

    val drawList = ArrayList<ProjectedTriangle>(mesh.faces.size)

    for (face in mesh.faces) {
        val p0 = projectedVerts.getOrNull(face.v0) ?: continue
        val p1 = projectedVerts.getOrNull(face.v1) ?: continue
        val p2 = projectedVerts.getOrNull(face.v2) ?: continue

        val w0 = transformedVertices[face.v0]
        val w1 = transformedVertices[face.v1]
        val w2 = transformedVertices[face.v2]

        val edge1 = w1 - w0
        val edge2 = w2 - w0
        val normal = edge1.cross(edge2).normalized()

        val ndotl = abs(normal.dot(lightDir))
        val fillDot = max(0f, normal.dot(fillLightDir)) * 0.25f
        val ambient = 0.28f * lightIntensity
        val diffuse = (ndotl * 0.72f + fillDot) * lightIntensity
        val shade = (ambient + diffuse + face.emissive).coerceIn(0.12f, 1.35f)

        val base = face.color
        val shaded = Color(
            red = (base.red * shade).coerceIn(0f, 1f),
            green = (base.green * shade).coerceIn(0f, 1f),
            blue = (base.blue * shade).coerceIn(0f, 1f),
            alpha = if (wireframe) 0.85f else 0.96f
        )

        val avgDepth = (p0.z + p1.z + p2.z) / 3f
        drawList.add(
            ProjectedTriangle(
                x0 = p0.x, y0 = p0.y,
                x1 = p1.x, y1 = p1.y,
                x2 = p2.x, y2 = p2.y,
                depth = avgDepth,
                shadedColor = shaded
            )
        )
    }

    // Painter's algorithm: sort back-to-front (largest depth first)
    drawList.sortByDescending { it.depth }

    if (wireframe) {
        for (tri in drawList) {
            pathScratch.reset()
            pathScratch.moveTo(tri.x0, tri.y0)
            pathScratch.lineTo(tri.x1, tri.y1)
            pathScratch.lineTo(tri.x2, tri.y2)
            pathScratch.close()
            drawPath(
                path = pathScratch,
                color = tri.shadedColor,
                style = Stroke(width = 1.35f)
            )
        }
    } else {
        val edgeOverlay = Color(0x2800BFFF)
        for (tri in drawList) {
            pathScratch.reset()
            pathScratch.moveTo(tri.x0, tri.y0)
            pathScratch.lineTo(tri.x1, tri.y1)
            pathScratch.lineTo(tri.x2, tri.y2)
            pathScratch.close()
            drawPath(
                path = pathScratch,
                color = tri.shadedColor,
                style = Fill
            )
            drawPath(
                path = pathScratch,
                color = edgeOverlay,
                style = Stroke(width = 0.65f)
            )
        }
    }
}

private fun DrawScope.drawPointCloud(
    mesh: Mesh3D,
    modelRotationY: Float,
    pointSize: Float,
    fovScale: Float,
    lightIntensity: Float,
    project: (Vec3) -> Vec3?
) {
    val sourcePoints = mesh.points
    val projectedPoints = ArrayList<ProjectedPoint>(sourcePoints.size)
    val brightness = (0.55f + 0.45f * lightIntensity).coerceIn(0.3f, 1.4f)

    for (pt in sourcePoints) {
        val worldPos = if (abs(modelRotationY) > 1e-4f) pt.pos.rotateY(modelRotationY) else pt.pos
        val proj = project(worldPos) ?: continue
        val radiusPx = ((pointSize * fovScale) / max(1.2f, proj.z)).coerceIn(1.5f, 14f)
        val col = Color(
            red = (pt.color.red * brightness).coerceIn(0f, 1f),
            green = (pt.color.green * brightness).coerceIn(0f, 1f),
            blue = (pt.color.blue * brightness).coerceIn(0f, 1f),
            alpha = 0.88f
        )
        projectedPoints.add(
            ProjectedPoint(
                x = proj.x,
                y = proj.y,
                depth = proj.z,
                radius = radiusPx,
                color = col
            )
        )
    }

    projectedPoints.sortByDescending { it.depth }
    for (p in projectedPoints) {
        drawCircle(
            color = p.color,
            radius = p.radius,
            center = Offset(p.x, p.y)
        )
    }
}

private fun DrawScope.drawAxisGizmo(
    right: Vec3,
    up: Vec3,
    forward: Vec3,
    gizmoCenter: Offset,
    axisRadius: Float
) {
    drawCircle(
        color = Color(0x99131722),
        radius = axisRadius * 1.25f,
        center = gizmoCenter
    )
    drawCircle(
        color = Color(0x3300BFFF),
        radius = axisRadius * 1.25f,
        center = gizmoCenter,
        style = Stroke(width = 1.5f)
    )

    val axes = listOf(
        Triple(Vec3(1f, 0f, 0f), Color(0xFFFF4D4D), forward.x), // X Red
        Triple(Vec3(0f, 1f, 0f), Color(0xFF00E676), forward.y), // Y Green
        Triple(Vec3(0f, 0f, 1f), Color(0xFF29B6F6), forward.z)  // Z Blue
    ).sortedByDescending { it.third }

    for ((axisVec, axisColor, _) in axes) {
        val sx = axisVec.dot(right) * axisRadius
        val sy = -axisVec.dot(up) * axisRadius
        val end = Offset(gizmoCenter.x + sx, gizmoCenter.y + sy)
        drawLine(
            color = axisColor,
            start = gizmoCenter,
            end = end,
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = axisColor,
            radius = 4f,
            center = end
        )
    }
}
