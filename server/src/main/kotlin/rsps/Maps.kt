package rsps

import java.nio.ByteBuffer

/** A loc placed in the map: config id, shape (0 = straight wall) and rotation (0 = west, 1 = north, 2 = east, 3 = south). */
data class MapLoc(val id: Int, val shape: Int, val rot: Int)

/**
 * Loc placements from the map archive (5). In rev 241 each map square is group `mx << 8 | mz`
 * with file 1 holding the locs (unencrypted, no XTEA keys). Only wall-layer locs (shape 0) with an
 * Open/Close op are kept: that is what doors and gates need.
 */
object Maps {
    private const val ARCHIVE_MAPS = 5
    private const val FILE_LOCS = 1

    fun key(x: Int, z: Int, level: Int): Long = (level.toLong() shl 32) or (x.toLong() shl 16) or z.toLong()

    fun loadDoors(cache: Cache, locTypes: Map<Int, EntityType>): Map<Long, MapLoc> {
        val doors = HashMap<Long, MapLoc>()
        var failed = 0
        val openable = locTypes.filterValues { t -> t.ops.any { it.equals("Open", true) || it.equals("Close", true) } }.keys
        for (group in cache.index(ARCHIVE_MAPS).groups.keys) {
            val data = runCatching { cache.files(ARCHIVE_MAPS, group)[FILE_LOCS] }.getOrNull() ?: continue
            val baseX = (group shr 8) shl 6
            val baseZ = (group and 0xFF) shl 6
            try {
                decodeLocs(data) { id, lx, lz, level, shape, rot ->
                    if (shape == 0 && id in openable) doors[key(baseX + lx, baseZ + lz, level)] = MapLoc(id, shape, rot)
                }
            } catch (e: RuntimeException) {
                failed++ // a few squares are empty or differently formatted; keep what was read
            }
        }
        if (failed > 0) System.err.println("Maps: $failed loc files could not be fully decoded")
        return doors
    }

    fun decodeLocs(data: ByteArray, consumer: (id: Int, lx: Int, lz: Int, level: Int, shape: Int, rot: Int) -> Unit) {
        val buf = ByteBuffer.wrap(data)
        fun uSmart(): Int {
            val peek = buf.get(buf.position()).toInt() and 0xFF
            return if (peek < 128) buf.get().toInt() and 0xFF else (buf.short.toInt() and 0xFFFF) - 32768
        }
        fun bigSmart(): Int {
            var acc = 0
            var v = uSmart()
            while (v == 32767) {
                acc += 32767
                v = uSmart()
            }
            return acc + v
        }
        var id = -1
        while (buf.hasRemaining()) {
            val idOffset = bigSmart()
            if (idOffset == 0) break
            id += idOffset
            var pos = 0
            while (true) {
                val posOffset = uSmart()
                if (posOffset == 0) break
                pos += posOffset - 1
                val attributes = buf.get().toInt() and 0xFF
                consumer(id, (pos shr 6) and 0x3F, pos and 0x3F, (pos shr 12) and 0x3, attributes shr 2, attributes and 3)
            }
        }
    }

    /** Tile offset a door moves to when opened, by its closed rotation. */
    fun openOffset(rot: Int): Pair<Int, Int> = when (rot and 3) {
        0 -> -1 to 0
        1 -> 0 to 1
        2 -> 1 to 0
        else -> 0 to -1
    }
}
