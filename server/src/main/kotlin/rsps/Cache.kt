package rsps

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.rsprot.protocol.api.js5.Js5Service
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.GZIPInputStream
import kotlin.io.path.exists

/**
 * Read-only reader for a Jagex disk cache (main_file_cache.dat2 + idx files), as distributed by
 * the OpenRS2 archive. Only what the test server needs: raw group reads, container decompression,
 * reference-table parsing and the pre-built JS5 responses.
 */
class Cache(dir: Path) : AutoCloseable {
    private val dat = RandomAccessFile(dir.resolve("main_file_cache.dat2").toFile(), "r")
    private val idx = HashMap<Int, RandomAccessFile>()
    val archives: List<Int>

    init {
        require(dir.resolve("main_file_cache.idx255").exists()) { "No cache found in $dir" }
        idx[MASTER] = RandomAccessFile(dir.resolve("main_file_cache.idx255").toFile(), "r")
        archives = (0 until idx.getValue(MASTER).length().toInt() / 6).filter { a ->
            val f = dir.resolve("main_file_cache.idx$a")
            if (f.exists()) idx[a] = RandomAccessFile(f.toFile(), "r")
            f.exists() && readRaw(MASTER, a) != null
        }
    }

    /** Reads a group as stored on disk (container + optional 2-byte version trailer). */
    fun readRaw(archive: Int, group: Int): ByteArray? {
        val index = idx[archive] ?: return null
        val pos = group.toLong() * 6
        if (pos + 6 > index.length()) return null
        val entry = ByteArray(6)
        index.seek(pos)
        index.readFully(entry)
        val size = u24(entry, 0)
        var sector = u24(entry, 3)
        if (size <= 0 || sector <= 0) return null

        val extended = group > 0xFFFF
        val headerSize = if (extended) 10 else 8
        val out = ByteArray(size)
        val buf = ByteArray(SECTOR_SIZE)
        var read = 0
        var chunk = 0
        while (read < size) {
            if (sector == 0) return null
            dat.seek(sector.toLong() * SECTOR_SIZE)
            val n = minOf(size - read, SECTOR_SIZE - headerSize)
            dat.readFully(buf, 0, headerSize + n)
            val bb = ByteBuffer.wrap(buf)
            val sGroup = if (extended) bb.int else bb.short.toInt() and 0xFFFF
            val sChunk = bb.short.toInt() and 0xFFFF
            val next = u24(buf, bb.position())
            val sArchive = buf[bb.position() + 3].toInt() and 0xFF
            if (sGroup != group || sChunk != chunk || sArchive != (archive and 0xFF)) return null
            System.arraycopy(buf, headerSize, out, read, n)
            read += n
            sector = next
            chunk++
        }
        return out
    }

    /** Reads a group and strips the version trailer, leaving exactly the JS5 container. */
    fun readContainer(archive: Int, group: Int): ByteArray? {
        val raw = readRaw(archive, group) ?: return null
        val len = containerLength(raw)
        return if (raw.size > len) raw.copyOf(len) else raw
    }

    fun readDecompressed(archive: Int, group: Int): ByteArray? = readContainer(archive, group)?.let(::decompress)

    fun index(archive: Int): ReferenceTable = ReferenceTable.decode(readDecompressed(MASTER, archive)!!)

    /** Returns the files of a group, split per the reference table. */
    fun files(archive: Int, group: Int): Map<Int, ByteArray> {
        val table = index(archive)
        val entry = table.groups[group] ?: return emptyMap()
        val data = readDecompressed(archive, group) ?: return emptyMap()
        return splitGroup(data, entry.fileIds)
    }

    fun fileByName(archive: Int, groupName: String, file: Int = 0): ByteArray? {
        val table = index(archive)
        val hash = djb2(groupName)
        val group = table.groups.values.firstOrNull { it.nameHash == hash } ?: return null
        return files(archive, group.id)[file]
    }

    override fun close() {
        dat.close()
        idx.values.forEach { it.close() }
    }

    class ReferenceTable(val format: Int, val version: Int, val groups: Map<Int, Group>) {
        class Group(val id: Int, var nameHash: Int = 0, var crc: Int = 0, var version: Int = 0) {
            var fileIds: IntArray = IntArray(0)
        }

        companion object {
            fun decode(data: ByteArray): ReferenceTable {
                val buf = ByteBuffer.wrap(data)
                val format = buf.get().toInt() and 0xFF
                val version = if (format >= 6) buf.int else 0
                val flags = buf.get().toInt() and 0xFF
                val count = if (format >= 7) smart(buf) else buf.short.toInt() and 0xFFFF
                val ids = IntArray(count)
                var acc = 0
                for (i in 0 until count) {
                    acc += if (format >= 7) smart(buf) else buf.short.toInt() and 0xFFFF
                    ids[i] = acc
                }
                val groups = ids.map { Group(it) }
                if (flags and FLAG_NAMES != 0) groups.forEach { it.nameHash = buf.int }
                groups.forEach { it.crc = buf.int }
                if (flags and FLAG_UNCOMPRESSED_CRC != 0) groups.forEach { buf.int }
                if (flags and FLAG_DIGESTS != 0) groups.forEach { buf.position(buf.position() + 64) }
                if (flags and FLAG_LENGTHS != 0) groups.forEach { buf.int; buf.int }
                groups.forEach { it.version = buf.int }
                val fileCounts = groups.map { if (format >= 7) smart(buf) else buf.short.toInt() and 0xFFFF }
                groups.forEachIndexed { i, g ->
                    var f = 0
                    g.fileIds = IntArray(fileCounts[i]) {
                        f += if (format >= 7) smart(buf) else buf.short.toInt() and 0xFFFF
                        f
                    }
                }
                return ReferenceTable(format, version, groups.associateBy { it.id })
            }

            private const val FLAG_NAMES = 0x1
            private const val FLAG_DIGESTS = 0x2
            private const val FLAG_LENGTHS = 0x4
            private const val FLAG_UNCOMPRESSED_CRC = 0x8
        }
    }

    companion object {
        const val MASTER = 255
        private const val SECTOR_SIZE = 520

        private fun u24(b: ByteArray, off: Int): Int =
            ((b[off].toInt() and 0xFF) shl 16) or ((b[off + 1].toInt() and 0xFF) shl 8) or (b[off + 2].toInt() and 0xFF)

        private fun smart(buf: ByteBuffer): Int =
            if (buf.get(buf.position()) < 0) buf.int and 0x7FFFFFFF else buf.short.toInt() and 0xFFFF

        fun djb2(s: String): Int = s.lowercase().fold(0) { h, c -> h * 31 + c.code }

        fun containerLength(c: ByteArray): Int {
            val type = c[0].toInt()
            val len = ByteBuffer.wrap(c, 1, 4).int
            return 5 + len + if (type == 0) 0 else 4
        }

        fun decompress(c: ByteArray): ByteArray {
            val bb = ByteBuffer.wrap(c)
            val type = bb.get().toInt()
            val len = bb.int
            if (type == 0) return c.copyOfRange(5, 5 + len)
            val uncompressedLen = bb.int
            val payload = c.copyOfRange(9, 9 + len)
            val stream = when (type) {
                1 -> BZip2CompressorInputStream(ByteArrayInputStream(byteArrayOf('B'.code.toByte(), 'Z'.code.toByte(), 'h'.code.toByte(), '1'.code.toByte()) + payload))
                2 -> GZIPInputStream(ByteArrayInputStream(payload))
                else -> error("Unsupported compression type $type")
            }
            val out = stream.use { it.readBytes() }
            check(out.size == uncompressedLen) { "Decompressed ${out.size} != $uncompressedLen" }
            return out
        }

        fun splitGroup(data: ByteArray, fileIds: IntArray): Map<Int, ByteArray> {
            if (fileIds.size == 1) return mapOf(fileIds[0] to data)
            val n = fileIds.size
            val stripes = data[data.size - 1].toInt() and 0xFF
            val table = ByteBuffer.wrap(data, data.size - 1 - stripes * n * 4, stripes * n * 4)
            val sizes = IntArray(n)
            val stripeSizes = Array(stripes) { IntArray(n) }
            for (s in 0 until stripes) {
                var size = 0
                for (f in 0 until n) {
                    size += table.int
                    stripeSizes[s][f] = size
                    sizes[f] += size
                }
            }
            val out = Array(n) { ByteArray(sizes[it]) }
            val written = IntArray(n)
            var pos = 0
            for (s in 0 until stripes) for (f in 0 until n) {
                val len = stripeSizes[s][f]
                System.arraycopy(data, pos, out[f], written[f], len)
                written[f] += len
                pos += len
            }
            return fileIds.indices.associate { fileIds[it] to out[it] }
        }
    }
}

/**
 * Pre-built JS5 responses for every group in the cache, plus the CRCs the client sends at login.
 */
class Js5Store(private val responses: Map<Int, ByteBuf>, val crcs: IntArray) {
    fun response(archive: Int, group: Int): ByteBuf? = responses[(archive shl 24) or group]

    companion object {
        fun build(cache: Cache): Js5Store {
            val responses = HashMap<Int, ByteBuf>(150_000)
            val maxArchive = cache.archives.max()
            val crcs = IntArray(maxArchive + 1)
            val master = Unpooled.buffer()
            for (archive in 0..maxArchive) {
                val table = if (archive in cache.archives) cache.readContainer(Cache.MASTER, archive) else null
                if (table == null) {
                    master.writeInt(0).writeInt(0)
                    continue
                }
                val crc = CRC32().apply { update(table) }.value.toInt()
                crcs[archive] = crc
                master.writeInt(crc).writeInt(Cache.ReferenceTable.decode(Cache.decompress(table)).version)
                responses.put(Cache.MASTER, archive, table)

                for (group in cache.index(archive).groups.keys) {
                    val container = cache.readContainer(archive, group) ?: continue
                    responses.put(archive, group, container)
                }
            }
            val masterContainer = Unpooled.buffer()
                .writeByte(0)
                .writeInt(master.readableBytes())
                .writeBytes(master)
            responses.put(Cache.MASTER, Cache.MASTER, ByteBufUtilBytes(masterContainer))
            return Js5Store(responses, crcs)
        }

        private fun HashMap<Int, ByteBuf>.put(archive: Int, group: Int, container: ByteArray) {
            val input = Unpooled.wrappedBuffer(container)
            val out = Unpooled.buffer(container.size + 8 + container.size / 512)
            Js5Service.prepareJs5Buffer(archive, group, input, out)
            this[(archive shl 24) or group] = Unpooled.unreleasableBuffer(out)
        }

        private fun ByteBufUtilBytes(buf: ByteBuf): ByteArray = ByteArray(buf.readableBytes()).also { buf.readBytes(it) }
    }
}

fun Path.isCache(): Boolean = Files.exists(resolve("main_file_cache.dat2"))
