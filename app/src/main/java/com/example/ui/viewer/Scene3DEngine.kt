package com.example.ui.viewer

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 1f, 0f)
    }

    fun rotateY(angleRad: Float): Vec3 {
        val c = cos(angleRad)
        val s = sin(angleRad)
        return Vec3(x * c + z * s, y, -x * s + z * c)
    }

    fun rotateX(angleRad: Float): Vec3 {
        val c = cos(angleRad)
        val s = sin(angleRad)
        return Vec3(x, y * c - z * s, y * s + z * c)
    }

    fun rotateZ(angleRad: Float): Vec3 {
        val c = cos(angleRad)
        val s = sin(angleRad)
        return Vec3(x * c - y * s, x * s + y * c, z)
    }
}

data class Face3D(
    val v0: Int,
    val v1: Int,
    val v2: Int,
    val color: Color,
    val emissive: Float = 0f
)

data class Point3D(
    val pos: Vec3,
    val color: Color
)

data class Mesh3D(
    val vertices: List<Vec3>,
    val faces: List<Face3D>,
    val points: List<Point3D>,
    val vertexCount: Int,
    val polygonCount: Int,
    val defaultCameraPos: Vec3 = Vec3(7f, 4f, 10f),
    val defaultCameraTarget: Vec3 = Vec3(0f, 0f, 0f)
)

object Scene3DEngine {

    /**
     * Generates the procedural Space Station Core Hub ("station" sample)
     */
    fun generateStationScene(): Mesh3D {
        val vertices = ArrayList<Vec3>()
        val faces = ArrayList<Face3D>()
        val points = ArrayList<Point3D>()

        val cyan = Color(0xFF00BFFF)
        val neonGreen = Color(0xFF00FFCC)
        val slateMetal = Color(0xFF283747)
        val solarBlue = Color(0xFF0B4F6C)
        val coreGlow = Color(0xFF00E5FF)
        val accentMagenta = Color(0xFFE040FB)

        // 1. Central Geodesic Octahedron / UV Sphere Core (radius 1.45)
        addUvSphere(
            vertices = vertices,
            faces = faces,
            center = Vec3(0f, 0f, 0f),
            radius = 1.45f,
            latSegments = 12,
            lonSegments = 20,
            color = neonGreen,
            emissive = 0.2f
        )

        // Inner reactor ring
        addTorus(
            vertices = vertices,
            faces = faces,
            radius = 2.8f,
            tubeRadius = 0.12f,
            radialSegments = 10,
            tubularSegments = 36,
            color = cyan,
            emissive = 0.15f,
            tiltX = 0f,
            tiltZ = 0f
        )

        // Outer inclined gyroscopic tech ring
        addTorus(
            vertices = vertices,
            faces = faces,
            radius = 4.0f,
            tubeRadius = 0.09f,
            radialSegments = 8,
            tubularSegments = 40,
            color = coreGlow,
            emissive = 0.1f,
            tiltX = 0.55f,
            tiltZ = 0.35f
        )

        // Secondary counter-tilted sensor ring
        addTorus(
            vertices = vertices,
            faces = faces,
            radius = 4.3f,
            tubeRadius = 0.06f,
            radialSegments = 6,
            tubularSegments = 36,
            color = accentMagenta,
            emissive = 0.25f,
            tiltX = -0.5f,
            tiltZ = 0.4f
        )

        // Central vertical command spire
        addBox(
            vertices = vertices,
            faces = faces,
            center = Vec3(0f, 0f, 0f),
            size = Vec3(0.35f, 4.4f, 0.35f),
            yaw = 0f,
            color = slateMetal
        )

        // 4 Outer Solar Array Wings & Connecting Struts
        for (i in 0 until 4) {
            val angle = (i / 4f) * (PI.toFloat() * 2f)
            val cosA = cos(angle)
            val sinA = sin(angle)

            // Strut bar
            val strutCenter = Vec3(cosA * 4.2f, 0f, sinA * 4.2f)
            addBox(
                vertices = vertices,
                faces = faces,
                center = strutCenter,
                size = Vec3(2.8f, 0.12f, 0.12f),
                yaw = -angle,
                color = cyan,
                emissive = 0.1f
            )

            // Solar Wing Panel
            val panelCenter = Vec3(cosA * 5.8f, 0f, sinA * 5.8f)
            addBox(
                vertices = vertices,
                faces = faces,
                center = panelCenter,
                size = Vec3(2.4f, 0.08f, 1.1f),
                yaw = -angle,
                color = solarBlue,
                emissive = 0.05f
            )

            // Wing tip beacon node
            val beaconCenter = Vec3(cosA * 7.1f, 0f, sinA * 7.1f)
            addBox(
                vertices = vertices,
                faces = faces,
                center = beaconCenter,
                size = Vec3(0.22f, 0.22f, 0.22f),
                yaw = -angle,
                color = neonGreen,
                emissive = 0.4f
            )
        }

        // Lower Orbital Scan Base Platform
        addCylinderDisc(
            vertices = vertices,
            faces = faces,
            center = Vec3(0f, -2.2f, 0f),
            radius = 5.5f,
            height = 0.14f,
            segments = 24,
            color = Color(0xFF16202C)
        )

        // Populate point cloud representation from vertices so "points" mode also looks rich
        for (v in vertices) {
            val dist = sqrt(v.x * v.x + v.z * v.z)
            val ptColor = when {
                dist < 1.8f -> neonGreen
                dist < 3.5f -> cyan
                else -> coreGlow
            }
            points.add(Point3D(v, ptColor))
        }

        return Mesh3D(
            vertices = vertices,
            faces = faces,
            points = points,
            vertexCount = vertices.size,
            polygonCount = faces.size,
            defaultCameraPos = Vec3(7f, 4f, 10f),
            defaultCameraTarget = Vec3(0f, 0f, 0f)
        )
    }

    /**
     * Generates the procedural LIDAR Corridor Scan ("scan" PLY sample)
     */
    fun generateLidarCorridorScene(): Mesh3D {
        val vertices = ArrayList<Vec3>()
        val faces = ArrayList<Face3D>()
        val points = ArrayList<Point3D>()

        val palette = listOf(
            Color(0xFF00BFFF), // Cyan
            Color(0xFF00FFCC), // Neon Green
            Color(0xFFE040FB), // Magenta
            Color(0xFF7C4DFF)  // Deep Violet
        )

        // Build structured tunnel mesh rings + dense LIDAR scatter points
        val ringCount = 48
        val ptsPerRing = 36
        for (r in 0 until ringCount) {
            val t = r.toFloat() / (ringCount - 1)
            val z = t * 26f - 13f
            val baseColor = lerpPalette(palette, t)

            for (s in 0 until ptsPerRing) {
                val angle = (s.toFloat() / ptsPerRing) * (PI.toFloat() * 2f)
                // Arch-shaped subterranean corridor with organic rock undulation
                val wave = sin(z * 0.45f + angle * 2f) * 0.35f + cos(z * 0.8f - angle * 3f) * 0.18f
                val radiusX = 2.8f + wave
                val radiusY = 2.3f + sin(z * 0.35f) * 0.3f + wave * 0.6f
                val x = cos(angle) * radiusX
                val rawY = sin(angle) * radiusY + 1.0f
                // Flatten floor at y = -0.6f for walkway look
                val y = if (rawY < -0.6f) -0.6f + (rawY + 0.6f) * 0.15f else rawY

                val pos = Vec3(x, y, z)
                vertices.add(pos)
                points.add(Point3D(pos, baseColor))

                // Connect triangles between adjacent rings so Solid & Wireframe modes also work on LIDAR scan
                if (r < ringCount - 1) {
                    val nextRingOffset = (r + 1) * ptsPerRing
                    val curRingOffset = r * ptsPerRing
                    val nextS = (s + 1) % ptsPerRing

                    val i0 = curRingOffset + s
                    val i1 = curRingOffset + nextS
                    val i2 = nextRingOffset + nextS
                    val i3 = nextRingOffset + s

                    faces.add(Face3D(i0, i1, i2, baseColor, emissive = 0.15f))
                    faces.add(Face3D(i0, i2, i3, baseColor, emissive = 0.15f))
                }
            }
        }

        // Add extra high-density LIDAR scatter points along corridor walls & pillars
        var seed = 1337L
        fun nextRand(): Float {
            seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return (seed.toDouble() / 0xFFFFFFFFL.toDouble()).toFloat()
        }

        val extraPoints = 2200
        for (i in 0 until extraPoints) {
            val u = nextRand()
            val z = u * 26f - 13f
            val angle = nextRand() * PI.toFloat() * 2f
            val radius = 2.5f + sin(z * 0.5f) * 0.45f + (nextRand() - 0.5f) * 0.35f
            val x = cos(angle) * radius
            val rawY = sin(angle) * radius + 1.0f
            val y = max(-0.65f, rawY)
            val color = lerpPalette(palette, u)
            points.add(Point3D(Vec3(x, y, z), color))
        }

        return Mesh3D(
            vertices = vertices,
            faces = faces,
            points = points,
            vertexCount = points.size,
            polygonCount = faces.size,
            defaultCameraPos = Vec3(0f, 3f, 15f),
            defaultCameraTarget = Vec3(0f, 1f, 0f)
        )
    }

    /**
     * Parses a .ply file (supports both ASCII and binary_little_endian / binary_big_endian)
     */
    fun parsePlyBytes(bytes: ByteArray): Mesh3D {
        val headerEndIndex = findPlyHeaderEnd(bytes)
        val headerText = String(bytes, 0, headerEndIndex, Charsets.US_ASCII)
        val lines = headerText.lines().map { it.trim() }

        var format = "ascii"
        var vertexCount = 0
        var faceCount = 0
        val vertexProps = ArrayList<Pair<String, String>>() // (type, name)
        var currentElement = ""

        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.isEmpty()) continue
            when (parts[0]) {
                "format" -> if (parts.size >= 2) format = parts[1].lowercase()
                "element" -> {
                    if (parts.size >= 3) {
                        currentElement = parts[1]
                        val count = parts[2].toIntOrNull() ?: 0
                        if (currentElement == "vertex") vertexCount = count
                        else if (currentElement == "face") faceCount = count
                    }
                }
                "property" -> {
                    if (currentElement == "vertex" && parts.size >= 3 && parts[1] != "list") {
                        vertexProps.add(parts[1] to parts[2])
                    }
                }
            }
        }

        val rawVertices = ArrayList<Vec3>(max(vertexCount, 16))
        val rawColors = ArrayList<Color>(max(vertexCount, 16))
        val rawFaces = ArrayList<Face3D>(max(faceCount, 16))

        if (format == "ascii") {
            val bodyReader = BufferedReader(
                InputStreamReader(ByteArrayInputStream(bytes, headerEndIndex, bytes.size - headerEndIndex), Charsets.US_ASCII)
            )
            val xIdx = vertexProps.indexOfFirst { it.second == "x" }.takeIf { it >= 0 } ?: 0
            val yIdx = vertexProps.indexOfFirst { it.second == "y" }.takeIf { it >= 0 } ?: 1
            val zIdx = vertexProps.indexOfFirst { it.second == "z" }.takeIf { it >= 0 } ?: 2
            val rIdx = vertexProps.indexOfFirst { it.second == "red" || it.second == "r" }
            val gIdx = vertexProps.indexOfFirst { it.second == "green" || it.second == "g" }
            val bIdx = vertexProps.indexOfFirst { it.second == "blue" || it.second == "b" }

            var vRead = 0
            while (vRead < vertexCount) {
                val line = bodyReader.readLine()?.trim() ?: break
                if (line.isEmpty() || line.startsWith("comment")) continue
                val tokens = line.split(Regex("\\s+"))
                if (tokens.size >= 3) {
                    val x = tokens.getOrNull(xIdx)?.toFloatOrNull() ?: 0f
                    val y = tokens.getOrNull(yIdx)?.toFloatOrNull() ?: 0f
                    val z = tokens.getOrNull(zIdx)?.toFloatOrNull() ?: 0f
                    rawVertices.add(Vec3(x, y, z))

                    val col = if (rIdx >= 0 && gIdx >= 0 && bIdx >= 0 && tokens.size > maxOf(rIdx, gIdx, bIdx)) {
                        val rVal = tokens[rIdx].toFloatOrNull() ?: 0f
                        val gVal = tokens[gIdx].toFloatOrNull() ?: 191f
                        val bVal = tokens[bIdx].toFloatOrNull() ?: 255f
                        if (rVal <= 1f && gVal <= 1f && bVal <= 1f && (rVal > 0f || gVal > 0f || bVal > 0f)) {
                            Color(rVal.coerceIn(0f, 1f), gVal.coerceIn(0f, 1f), bVal.coerceIn(0f, 1f))
                        } else {
                            Color(
                                (rVal / 255f).coerceIn(0f, 1f),
                                (gVal / 255f).coerceIn(0f, 1f),
                                (bVal / 255f).coerceIn(0f, 1f)
                            )
                        }
                    } else {
                        Color(0xFF00BFFF)
                    }
                    rawColors.add(col)
                    vRead++
                }
            }

            var fRead = 0
            while (fRead < faceCount) {
                val line = bodyReader.readLine()?.trim() ?: break
                if (line.isEmpty() || line.startsWith("comment")) continue
                val tokens = line.split(Regex("\\s+"))
                val n = tokens.firstOrNull()?.toIntOrNull() ?: 0
                if (n >= 3 && tokens.size >= n + 1) {
                    val i0 = tokens[1].toIntOrNull() ?: 0
                    for (k in 1 until n - 1) {
                        val i1 = tokens[1 + k].toIntOrNull() ?: 0
                        val i2 = tokens[2 + k].toIntOrNull() ?: 0
                        if (i0 in rawVertices.indices && i1 in rawVertices.indices && i2 in rawVertices.indices) {
                            rawFaces.add(Face3D(i0, i1, i2, rawColors[i0]))
                        }
                    }
                }
                fRead++
            }
        } else {
            // Binary PLY parsing
            val order = if (format.contains("big")) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            val buf = ByteBuffer.wrap(bytes, headerEndIndex, bytes.size - headerEndIndex).order(order)
            for (v in 0 until vertexCount) {
                if (!buf.hasRemaining()) break
                var x = 0f
                var y = 0f
                var z = 0f
                var r = 0f
                var g = 0.75f
                var b = 1f
                for ((type, name) in vertexProps) {
                    val value = readBinaryPlyProp(buf, type)
                    when (name) {
                        "x" -> x = value
                        "y" -> y = value
                        "z" -> z = value
                        "red", "r" -> r = if (type.contains("char") || type.contains("uchar") || type.contains("uint8")) value / 255f else value
                        "green", "g" -> g = if (type.contains("char") || type.contains("uchar") || type.contains("uint8")) value / 255f else value
                        "blue", "b" -> b = if (type.contains("char") || type.contains("uchar") || type.contains("uint8")) value / 255f else value
                    }
                }
                rawVertices.add(Vec3(x, y, z))
                rawColors.add(Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f)))
            }
        }

        return normalizeAndBuildMesh(rawVertices, rawColors, rawFaces)
    }

    /**
     * Parses a binary .glb or .gltf file into a normalized 3D Mesh
     */
    fun parseGlbBytes(bytes: ByteArray): Mesh3D {
        if (bytes.size < 20) {
            throw IllegalArgumentException("File is too small to be a valid GLB/GLTF model.")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        val jsonStr: String
        var binChunk: ByteBuffer? = null

        if (magic == 0x46546C67) { // "glTF" binary magic
            buf.int // version
            val totalLen = buf.int
            var offset = 12
            var foundJson = ""
            while (offset + 8 <= min(bytes.size, totalLen)) {
                buf.position(offset)
                val chunkLength = buf.int
                val chunkType = buf.int
                val dataStart = offset + 8
                if (dataStart + chunkLength > bytes.size) break
                if (chunkType == 0x4E4F534A) { // "JSON"
                    foundJson = String(bytes, dataStart, chunkLength, Charsets.UTF_8)
                } else if (chunkType == 0x004E4942) { // "BIN\0"
                    binChunk = ByteBuffer.wrap(bytes, dataStart, chunkLength).order(ByteOrder.LITTLE_ENDIAN)
                }
                offset = dataStart + chunkLength
            }
            jsonStr = foundJson
        } else {
            // Try parsing as JSON glTF
            jsonStr = String(bytes, Charsets.UTF_8)
        }

        if (jsonStr.isBlank()) {
            throw IllegalArgumentException("Invalid GLB/GLTF structure: missing JSON descriptor.")
        }

        val root = JSONObject(jsonStr)
        val accessors = root.optJSONArray("accessors")
        val bufferViews = root.optJSONArray("bufferViews")
        val meshes = root.optJSONArray("meshes")

        val vertices = ArrayList<Vec3>()
        val colors = ArrayList<Color>()
        val faces = ArrayList<Face3D>()

        if (meshes != null && accessors != null && bufferViews != null && binChunk != null) {
            for (m in 0 until meshes.length()) {
                val meshObj = meshes.getJSONObject(m)
                val primitives = meshObj.optJSONArray("primitives") ?: continue
                for (p in 0 until primitives.length()) {
                    val prim = primitives.getJSONObject(p)
                    val attrs = prim.optJSONObject("attributes") ?: continue
                    val posAccIdx = attrs.optInt("POSITION", -1)
                    if (posAccIdx < 0) continue

                    val baseVertexOffset = vertices.size
                    val primPositions = readAccessorVec3(posAccIdx, accessors, bufferViews, binChunk)
                    val primColor = Color(0xFF00FFCC)
                    for (pos in primPositions) {
                        vertices.add(pos)
                        colors.add(primColor)
                    }

                    val indicesAccIdx = prim.optInt("indices", -1)
                    if (indicesAccIdx >= 0) {
                        val idxList = readAccessorScalarIndices(indicesAccIdx, accessors, bufferViews, binChunk)
                        var k = 0
                        while (k + 2 < idxList.size) {
                            val i0 = baseVertexOffset + idxList[k]
                            val i1 = baseVertexOffset + idxList[k + 1]
                            val i2 = baseVertexOffset + idxList[k + 2]
                            if (i0 in vertices.indices && i1 in vertices.indices && i2 in vertices.indices) {
                                faces.add(Face3D(i0, i1, i2, primColor))
                            }
                            k += 3
                        }
                    } else {
                        var k = 0
                        while (k + 2 < primPositions.size) {
                            faces.add(
                                Face3D(
                                    baseVertexOffset + k,
                                    baseVertexOffset + k + 1,
                                    baseVertexOffset + k + 2,
                                    primColor
                                )
                            )
                            k += 3
                        }
                    }
                }
            }
        }

        if (vertices.isEmpty()) {
            throw IllegalArgumentException("No 3D mesh vertices found in model file.")
        }

        return normalizeAndBuildMesh(vertices, colors, faces)
    }

    private fun readAccessorVec3(
        accIdx: Int,
        accessors: org.json.JSONArray,
        bufferViews: org.json.JSONArray,
        bin: ByteBuffer
    ): List<Vec3> {
        val acc = accessors.getJSONObject(accIdx)
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return emptyList()
        val count = acc.optInt("count", 0)
        val accByteOffset = acc.optInt("byteOffset", 0)

        val bv = bufferViews.getJSONObject(bvIdx)
        val bvOffset = bv.optInt("byteOffset", 0)
        val byteStride = bv.optInt("byteStride", 12)

        val result = ArrayList<Vec3>(count)
        val startOffset = bvOffset + accByteOffset
        for (i in 0 until count) {
            val pos = startOffset + i * byteStride
            if (pos + 12 > bin.limit()) break
            val x = bin.getFloat(pos)
            val y = bin.getFloat(pos + 4)
            val z = bin.getFloat(pos + 8)
            result.add(Vec3(x, y, z))
        }
        return result
    }

    private fun readAccessorScalarIndices(
        accIdx: Int,
        accessors: org.json.JSONArray,
        bufferViews: org.json.JSONArray,
        bin: ByteBuffer
    ): List<Int> {
        val acc = accessors.getJSONObject(accIdx)
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0) return emptyList()
        val count = acc.optInt("count", 0)
        val componentType = acc.optInt("componentType", 5123) // 5121=UBYTE, 5123=USHORT, 5125=UINT
        val accByteOffset = acc.optInt("byteOffset", 0)

        val bv = bufferViews.getJSONObject(bvIdx)
        val bvOffset = bv.optInt("byteOffset", 0)
        val startOffset = bvOffset + accByteOffset

        val result = ArrayList<Int>(count)
        for (i in 0 until count) {
            when (componentType) {
                5121 -> {
                    val pos = startOffset + i
                    if (pos + 1 > bin.limit()) break
                    result.add(bin.get(pos).toInt() and 0xFF)
                }
                5123 -> {
                    val pos = startOffset + i * 2
                    if (pos + 2 > bin.limit()) break
                    result.add(bin.getShort(pos).toInt() and 0xFFFF)
                }
                5125 -> {
                    val pos = startOffset + i * 4
                    if (pos + 4 > bin.limit()) break
                    result.add(bin.getInt(pos))
                }
            }
        }
        return result
    }

    private fun normalizeAndBuildMesh(
        rawVertices: List<Vec3>,
        rawColors: List<Color>,
        rawFaces: List<Face3D>
    ): Mesh3D {
        if (rawVertices.isEmpty()) {
            throw IllegalArgumentException("Model contains 0 vertices.")
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (v in rawVertices) {
            if (v.x < minX) minX = v.x
            if (v.y < minY) minY = v.y
            if (v.z < minZ) minZ = v.z
            if (v.x > maxX) maxX = v.x
            if (v.y > maxY) maxY = v.y
            if (v.z > maxZ) maxZ = v.z
        }

        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val maxDim = maxOf(maxX - minX, maxY - minY, maxZ - minZ)
        val scale = if (maxDim > 1e-5f) 5.5f / maxDim else 1f

        val normVertices = rawVertices.map { v ->
            Vec3((v.x - cx) * scale, (v.y - cy) * scale, (v.z - cz) * scale)
        }

        val points = normVertices.mapIndexed { idx, v ->
            Point3D(v, rawColors.getOrElse(idx) { Color(0xFF00BFFF) })
        }

        return Mesh3D(
            vertices = normVertices,
            faces = rawFaces,
            points = points,
            vertexCount = normVertices.size,
            polygonCount = rawFaces.size,
            defaultCameraPos = Vec3(6f, 4f, 8f),
            defaultCameraTarget = Vec3(0f, 0f, 0f)
        )
    }

    private fun findPlyHeaderEnd(bytes: ByteArray): Int {
        val marker = "end_header".toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..bytes.size - marker.size) {
            for (j in marker.indices) {
                if (bytes[i + j] != marker[j]) continue@outer
            }
            var end = i + marker.size
            while (end < bytes.size && (bytes[end] == '\r'.code.toByte() || bytes[end] == '\n'.code.toByte())) {
                end++
            }
            return end
        }
        throw IllegalArgumentException("Invalid PLY file: missing end_header.")
    }

    private fun readBinaryPlyProp(buf: ByteBuffer, type: String): Float {
        return when (type.lowercase()) {
            "char", "int8" -> if (buf.remaining() >= 1) buf.get().toFloat() else 0f
            "uchar", "uint8" -> if (buf.remaining() >= 1) (buf.get().toInt() and 0xFF).toFloat() else 0f
            "short", "int16" -> if (buf.remaining() >= 2) buf.short.toFloat() else 0f
            "ushort", "uint16" -> if (buf.remaining() >= 2) (buf.short.toInt() and 0xFFFF).toFloat() else 0f
            "int", "int32", "uint", "uint32" -> if (buf.remaining() >= 4) buf.int.toFloat() else 0f
            "double", "float64" -> if (buf.remaining() >= 8) buf.double.toFloat() else 0f
            else -> if (buf.remaining() >= 4) buf.float else 0f
        }
    }

    private fun lerpPalette(palette: List<Color>, t: Float): Color {
        val scaled = (t.coerceIn(0f, 0.999f)) * (palette.size - 1)
        val idx = scaled.toInt()
        val frac = scaled - idx
        val c0 = palette[idx]
        val c1 = palette[min(idx + 1, palette.size - 1)]
        return Color(
            red = c0.red + (c1.red - c0.red) * frac,
            green = c0.green + (c1.green - c0.green) * frac,
            blue = c0.blue + (c1.blue - c0.blue) * frac,
            alpha = 1f
        )
    }

    private fun addUvSphere(
        vertices: MutableList<Vec3>,
        faces: MutableList<Face3D>,
        center: Vec3,
        radius: Float,
        latSegments: Int,
        lonSegments: Int,
        color: Color,
        emissive: Float = 0f
    ) {
        val base = vertices.size
        for (lat in 0..latSegments) {
            val theta = lat * PI.toFloat() / latSegments
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            for (lon in 0..lonSegments) {
                val phi = lon * 2f * PI.toFloat() / lonSegments
                val x = cos(phi) * sinTheta
                val y = cosTheta
                val z = sin(phi) * sinTheta
                vertices.add(center + Vec3(x * radius, y * radius, z * radius))
            }
        }
        val stride = lonSegments + 1
        for (lat in 0 until latSegments) {
            for (lon in 0 until lonSegments) {
                val first = base + lat * stride + lon
                val second = first + stride
                faces.add(Face3D(first, second, first + 1, color, emissive))
                faces.add(Face3D(second, second + 1, first + 1, color, emissive))
            }
        }
    }

    private fun addTorus(
        vertices: MutableList<Vec3>,
        faces: MutableList<Face3D>,
        radius: Float,
        tubeRadius: Float,
        radialSegments: Int,
        tubularSegments: Int,
        color: Color,
        emissive: Float = 0f,
        tiltX: Float = 0f,
        tiltZ: Float = 0f
    ) {
        val base = vertices.size
        for (j in 0..radialSegments) {
            val v = j.toFloat() / radialSegments * PI.toFloat() * 2f
            val cosV = cos(v)
            val sinV = sin(v)
            for (i in 0..tubularSegments) {
                val u = i.toFloat() / tubularSegments * PI.toFloat() * 2f
                val x = (radius + tubeRadius * cosV) * cos(u)
                val y = tubeRadius * sinV
                val z = (radius + tubeRadius * cosV) * sin(u)
                var pt = Vec3(x, y, z)
                if (tiltX != 0f) pt = pt.rotateX(tiltX)
                if (tiltZ != 0f) pt = pt.rotateZ(tiltZ)
                vertices.add(pt)
            }
        }
        val stride = tubularSegments + 1
        for (j in 0 until radialSegments) {
            for (i in 0 until tubularSegments) {
                val a = base + stride * j + i
                val b = base + stride * (j + 1) + i
                val c = base + stride * (j + 1) + i + 1
                val d = base + stride * j + i + 1
                faces.add(Face3D(a, b, d, color, emissive))
                faces.add(Face3D(b, c, d, color, emissive))
            }
        }
    }

    private fun addBox(
        vertices: MutableList<Vec3>,
        faces: MutableList<Face3D>,
        center: Vec3,
        size: Vec3,
        yaw: Float,
        color: Color,
        emissive: Float = 0f
    ) {
        val hx = size.x * 0.5f
        val hy = size.y * 0.5f
        val hz = size.z * 0.5f
        val localCorners = listOf(
            Vec3(-hx, -hy, -hz),
            Vec3(hx, -hy, -hz),
            Vec3(hx, hy, -hz),
            Vec3(-hx, hy, -hz),
            Vec3(-hx, -hy, hz),
            Vec3(hx, -hy, hz),
            Vec3(hx, hy, hz),
            Vec3(-hx, hy, hz)
        )
        val base = vertices.size
        for (c in localCorners) {
            vertices.add(center + c.rotateY(yaw))
        }
        val boxIndices = arrayOf(
            intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), // Back
            intArrayOf(4, 5, 6), intArrayOf(4, 6, 7), // Front
            intArrayOf(0, 4, 7), intArrayOf(0, 7, 3), // Left
            intArrayOf(1, 2, 6), intArrayOf(1, 6, 5), // Right
            intArrayOf(3, 7, 6), intArrayOf(3, 6, 2), // Top
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4)  // Bottom
        )
        for (tri in boxIndices) {
            faces.add(Face3D(base + tri[0], base + tri[1], base + tri[2], color, emissive))
        }
    }

    private fun addCylinderDisc(
        vertices: MutableList<Vec3>,
        faces: MutableList<Face3D>,
        center: Vec3,
        radius: Float,
        height: Float,
        segments: Int,
        color: Color
    ) {
        val base = vertices.size
        val topCenterIdx = base
        val botCenterIdx = base + 1
        val hh = height * 0.5f
        vertices.add(center + Vec3(0f, hh, 0f))
        vertices.add(center + Vec3(0f, -hh, 0f))

        for (i in 0..segments) {
            val angle = i.toFloat() / segments * PI.toFloat() * 2f
            val x = cos(angle) * radius
            val z = sin(angle) * radius
            vertices.add(center + Vec3(x, hh, z))
            vertices.add(center + Vec3(x, -hh, z))
        }

        for (i in 0 until segments) {
            val topCurr = base + 2 + i * 2
            val botCurr = topCurr + 1
            val topNext = topCurr + 2
            val botNext = topCurr + 3

            faces.add(Face3D(topCenterIdx, topNext, topCurr, color))
            faces.add(Face3D(botCenterIdx, botCurr, botNext, color))
            faces.add(Face3D(topCurr, topNext, botNext, color))
            faces.add(Face3D(topCurr, botNext, botCurr, color))
        }
    }
}
