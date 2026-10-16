package rsps

import java.nio.ByteBuffer

/**
 * Tile collision flags built from the map (archive 5: file 0 = terrain, file 1 = locs), loaded
 * per map square on first use, and a BFS pathfinder over them. Flags and direction masks follow
 * the usual OSRS server layout (wall bits per side, 0x100 = loc, 0x200000 = blocked floor).
 */
class Collision(private val cache: Cache, private val locTypes: Map<Int, EntityType>) {
    private val squares = HashMap<Int, Array<IntArray>?>()

    fun flags(x: Int, z: Int, level: Int): Int {
        val square = square(x shr 6, z shr 6) ?: return 0
        return square[level][((x and 63) shl 6) or (z and 63)]
    }

    private fun add(x: Int, z: Int, level: Int, flag: Int) {
        val square = square(x shr 6, z shr 6) ?: return
        val i = ((x and 63) shl 6) or (z and 63)
        square[level][i] = square[level][i] or flag
    }

    private fun remove(x: Int, z: Int, level: Int, flag: Int) {
        val square = square(x shr 6, z shr 6) ?: return
        val i = ((x and 63) shl 6) or (z and 63)
        square[level][i] = square[level][i] and flag.inv()
    }

    private fun square(mx: Int, mz: Int): Array<IntArray>? {
        val id = (mx shl 8) or mz
        if (id in squares) return squares[id]
        val flags = Array(4) { IntArray(64 * 64) }
        squares[id] = flags
        val files = runCatching { cache.files(ARCHIVE_MAPS, id) }.getOrNull()
        if (files == null || files[0] == null) {
            squares[id] = null
            return null
        }
        val settings = runCatching { decodeTerrain(files[0]!!) }.getOrNull() ?: Array(4) { ByteArray(64 * 64) }
        for (level in 0 until 4) for (i in 0 until 64 * 64) {
            val l = if (settings[1][i].toInt() and 2 != 0) level - 1 else level
            if (l < 0) continue
            if (settings[level][i].toInt() and HAS_SURFACE != 0) flags[l][i] = flags[l][i] or SURFACE
            if (settings[level][i].toInt() and 1 != 0) flags[l][i] = flags[l][i] or FLOOR
        }
        val locs = files[1] ?: return flags
        val baseX = mx shl 6
        val baseZ = mz shl 6
        runCatching {
            Maps.decodeLocs(locs) { locId, lx, lz, level, shape, rot ->
                val l = if (settings[1][(lx shl 6) or lz].toInt() and 2 != 0) level - 1 else level
                if (l >= 0) addLoc(locId, baseX + lx, baseZ + lz, l, shape, rot, add = true)
            }
        }
        return flags
    }

    /** Adds or removes the collision of a loc (walls, solid objects, blocking floor decoration). */
    fun addLoc(id: Int, x: Int, z: Int, level: Int, shape: Int, rot: Int, add: Boolean) {
        val type = locTypes[id] ?: return
        if (type.interactType == 0) return
        val set: (Int, Int, Int) -> Unit = if (add) { dx, dz, f -> add(x + dx, z + dz, level, f) } else { dx, dz, f -> remove(x + dx, z + dz, level, f) }
        when (shape) {
            0 -> when (rot) { // straight wall on one side of the tile
                0 -> { set(0, 0, W); set(-1, 0, E) }
                1 -> { set(0, 0, N); set(0, 1, S) }
                2 -> { set(0, 0, E); set(1, 0, W) }
                else -> { set(0, 0, S); set(0, -1, N) }
            }
            1, 3 -> when (rot) { // corner pieces
                0 -> { set(0, 0, NW); set(-1, 1, SE) }
                1 -> { set(0, 0, NE); set(1, 1, SW) }
                2 -> { set(0, 0, SE); set(1, -1, NW) }
                else -> { set(0, 0, SW); set(-1, -1, NE) }
            }
            2 -> when (rot) { // L-shaped wall
                0 -> { set(0, 0, W or N); set(-1, 0, E); set(0, 1, S) }
                1 -> { set(0, 0, N or E); set(0, 1, S); set(1, 0, W) }
                2 -> { set(0, 0, E or S); set(1, 0, W); set(0, -1, N) }
                else -> { set(0, 0, S or W); set(0, -1, N); set(-1, 0, E) }
            }
            9, 10, 11 -> {
                val sx = if (rot and 1 == 1) type.sizeZ else type.sizeX
                val sz = if (rot and 1 == 1) type.sizeX else type.sizeZ
                for (dx in 0 until sx) for (dz in 0 until sz) set(dx, dz, LOC)
            }
            22 -> if (type.interactType == 1) set(0, 0, FLOOR_DECO)
        }
    }

    fun canStep(x: Int, z: Int, level: Int, dx: Int, dz: Int): Boolean {
        val tx = x + dx
        val tz = z + dz
        return when {
            dx == -1 && dz == 0 -> flags(tx, tz, level) and BLOCK_W == 0
            dx == 1 && dz == 0 -> flags(tx, tz, level) and BLOCK_E == 0
            dx == 0 && dz == -1 -> flags(tx, tz, level) and BLOCK_S == 0
            dx == 0 && dz == 1 -> flags(tx, tz, level) and BLOCK_N == 0
            dx == -1 && dz == -1 -> flags(tx, tz, level) and BLOCK_SW == 0 &&
                flags(x - 1, z, level) and BLOCK_W == 0 && flags(x, z - 1, level) and BLOCK_S == 0
            dx == 1 && dz == -1 -> flags(tx, tz, level) and BLOCK_SE == 0 &&
                flags(x + 1, z, level) and BLOCK_E == 0 && flags(x, z - 1, level) and BLOCK_S == 0
            dx == -1 && dz == 1 -> flags(tx, tz, level) and BLOCK_NW == 0 &&
                flags(x - 1, z, level) and BLOCK_W == 0 && flags(x, z + 1, level) and BLOCK_N == 0
            dx == 1 && dz == 1 -> flags(tx, tz, level) and BLOCK_NE == 0 &&
                flags(x + 1, z, level) and BLOCK_E == 0 && flags(x, z + 1, level) and BLOCK_N == 0
            else -> true
        }
    }

    /**
     * The closest tile to (x, z) on [level] that has a floor surface (underlay or overlay, so not the
     * void around upper floors) and no solid loc or blocked floor, searching square rings out to
     * [radius]; null if none. Used to land after Climb-up/down: there is no stairs destination table,
     * and the tile in place is often the staircase itself or outside the room on the other level.
     */
    fun nearestOpen(x: Int, z: Int, level: Int, radius: Int = 4): Coord? {
        for (r in 0..radius) {
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                if (flags(x + dx, z + dz, level) and (LOC or FLOOR or SURFACE) == SURFACE) return Coord(x + dx, z + dz, level)
            }
        }
        return null
    }

    /**
     * Whether a player at (px, pz) can interact with a solid loc covering x..x+sx-1, z..z+sz-1: on a
     * tile along one of its sides (not a corner) with no wall in between, or inside it. Stops ops
     * through walls, e.g. the Lumbridge castle stairs from outside their tower.
     */
    fun reachesRect(px: Int, pz: Int, level: Int, x: Int, z: Int, sx: Int, sz: Int): Boolean {
        val inX = px in x until x + sx
        val inZ = pz in z until z + sz
        return when {
            inX && inZ -> true
            inX && pz == z - 1 -> flags(px, z, level) and S == 0
            inX && pz == z + sz -> flags(px, z + sz - 1, level) and N == 0
            inZ && px == x - 1 -> flags(x, pz, level) and W == 0
            inZ && px == x + sx -> flags(x + sx - 1, pz, level) and E == 0
            else -> false
        }
    }

    /**
     * Breadth-first search in a 128x128 window around [start] until [goal] holds. If no tile
     * satisfies it, walks to the reachable tile closest to [target] (as OSRS does). Returns the
     * tiles to walk, excluding the start.
     */
    fun findPath(start: Coord, target: Coord, goal: (Int, Int) -> Boolean): List<Coord> {
        val size = 128
        val baseX = start.x - size / 2
        val baseZ = start.z - size / 2
        val via = IntArray(size * size) { -1 }
        val dist = IntArray(size * size) { Int.MAX_VALUE }
        val queue = IntArray(size * size)
        var head = 0
        var tail = 0
        val startIndex = (size / 2) * size + size / 2
        dist[startIndex] = 0
        queue[tail++] = startIndex
        var found = -1
        while (head < tail) {
            val cur = queue[head++]
            val cx = baseX + cur / size
            val cz = baseZ + cur % size
            if (goal(cx, cz)) {
                found = cur
                break
            }
            for ((dx, dz) in DIRECTIONS) {
                val nx = cur / size + dx
                val nz = cur % size + dz
                if (nx !in 0 until size || nz !in 0 until size) continue
                val next = nx * size + nz
                if (dist[next] != Int.MAX_VALUE) continue
                if (!canStep(cx, cz, start.level, dx, dz)) continue
                dist[next] = dist[cur] + 1
                via[next] = cur
                queue[tail++] = next
            }
        }
        if (found < 0) {
            // Closest reachable tile to the target (within 10 tiles), then the shortest walk.
            var best = Int.MAX_VALUE
            for (i in 0 until size * size) {
                if (dist[i] == Int.MAX_VALUE) continue
                val dx = baseX + i / size - target.x
                val dz = baseZ + i % size - target.z
                if (abs(dx) > 10 || abs(dz) > 10) continue
                val score = (dx * dx + dz * dz) * 1000 + dist[i]
                if (score < best) {
                    best = score
                    found = i
                }
            }
        }
        if (found < 0 || found == startIndex) return emptyList()
        val path = ArrayList<Coord>()
        var cur = found
        while (cur != startIndex) {
            path += Coord(baseX + cur / size, baseZ + cur % size, start.level)
            cur = via[cur]
        }
        path.reverse()
        return path
    }

    private fun abs(v: Int) = if (v < 0) -v else v

    private fun decodeTerrain(data: ByteArray): Array<ByteArray> {
        val buf = ByteBuffer.wrap(data)
        val settings = Array(4) { ByteArray(64 * 64) }
        for (level in 0 until 4) for (x in 0 until 64) for (z in 0 until 64) {
            while (true) {
                val op = buf.short.toInt() and 0xFFFF
                if (op == 0) break
                if (op == 1) {
                    buf.get()
                    break
                }
                val i = (x shl 6) or z
                if (op <= 49) {
                    if (op >= 2 && buf.short.toInt() != 0) settings[level][i] = (settings[level][i].toInt() or HAS_SURFACE).toByte()
                    else if (op < 2) buf.short
                } else if (op <= 81) settings[level][i] = ((settings[level][i].toInt() and HAS_SURFACE) or (op - 49)).toByte()
                else settings[level][i] = (settings[level][i].toInt() or HAS_SURFACE).toByte()
            }
        }
        return settings
    }

    companion object {
        private const val ARCHIVE_MAPS = 5
        const val NW = 0x1
        const val N = 0x2
        const val NE = 0x4
        const val E = 0x8
        const val SE = 0x10
        const val S = 0x20
        const val SW = 0x40
        const val W = 0x80
        const val LOC = 0x100
        const val FLOOR_DECO = 0x40000
        const val FLOOR = 0x200000
        /** Not a collision flag: the tile has an underlay or overlay (upper levels are void elsewhere). */
        const val SURFACE = 0x4000000
        private const val HAS_SURFACE = 0x40

        private const val BLOCK_W = 0x1240108
        private const val BLOCK_E = 0x1240180
        private const val BLOCK_S = 0x1240102
        private const val BLOCK_N = 0x1240120
        private const val BLOCK_SW = 0x124010E
        private const val BLOCK_SE = 0x1240183
        private const val BLOCK_NW = 0x1240138
        private const val BLOCK_NE = 0x12401E0

        /** OSRS BFS order: straight directions first, then diagonals. */
        private val DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
    }
}
