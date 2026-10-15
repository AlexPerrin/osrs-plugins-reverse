package rsps

import java.nio.ByteBuffer

/** Enum configs (archive 2, group 8). Only int-valued entries are kept. */
class EnumType(val id: Int, val keyType: Char, val valueType: Char, val ints: Map<Int, Int>) {
    companion object {
        fun decode(id: Int, data: ByteArray): EnumType {
            val buf = ByteBuffer.wrap(data)
            var keyType = '?'
            var valueType = '?'
            val ints = HashMap<Int, Int>()
            while (true) {
                when (val op = buf.get().toInt() and 0xFF) {
                    0 -> break
                    1 -> keyType = (buf.get().toInt() and 0xFF).toChar()
                    2 -> valueType = (buf.get().toInt() and 0xFF).toChar()
                    3 -> while (buf.get().toInt() != 0) Unit
                    4 -> buf.int
                    5 -> repeat(buf.short.toInt() and 0xFFFF) {
                        buf.int
                        while (buf.get().toInt() != 0) Unit
                    }
                    6 -> repeat(buf.short.toInt() and 0xFFFF) { ints[buf.int] = buf.int }
                    else -> error("Unknown enum opcode $op in enum $id")
                }
            }
            return EnumType(id, keyType, valueType, ints)
        }
    }
}

/**
 * The interfaces opened when a player logs in. Content targets the resizable "osrs stretch"
 * toplevel (161); the cache's pane-redirect enum for the chosen toplevel translates each target
 * into the matching component of that toplevel, which is how the real gameframe scripts work.
 */
object Gameframe {
    const val TOPLEVEL_FIXED = 548
    const val TOPLEVEL_STRETCH = 161
    const val TOPLEVEL_MOBILE = 601
    /** toplevel_osrs_stretch:mainmodal and :sidemodal (RuneLite gameval ToplevelOsrsStretch). */
    const val STRETCH_MAINMODAL = 16
    const val STRETCH_SIDEMODAL = 74

    /** toplevel interface -> pane redirect enum id. */
    val redirectEnums = mapOf(TOPLEVEL_FIXED to 1129, TOPLEVEL_STRETCH to 1130, TOPLEVEL_MOBILE to 1745)

    /** interface id -> component of toplevel_osrs_stretch it targets. */
    val overlays = listOf(
        162 to 96, // chatbox -> chat_container
        651 to 6, // buff_bar
        708 to 5, // stat_boosts_hud
        163 to 93, // pm_chat -> pm_container
        303 to 2, // hpbar_hud
        90 to 3, // pvp_icons
        160 to 33, // orbs
        122 to 9, // xp_drops
        593 to 76, // combat_interface -> side0
        320 to 77, // stats -> side1
        629 to 78, // side_journal -> side2
        149 to 79, // inventory -> side3
        387 to 80, // wornitems -> side4
        541 to 81, // prayerbook -> side5
        218 to 82, // magic_spellbook -> side6
        707 to 83, // side_channels -> side7
        109 to 84, // account -> side8
        429 to 85, // friends -> side9
        182 to 86, // logout -> side10
        116 to 87, // settings_side -> side11
        216 to 88, // emote -> side12
        239 to 89, // music -> side13
    )

    const val LOGOUT_INTERFACE = 182
}

/**
 * Name, inventory options and wear slots of an obj config (archive 2, group 10). Opcodes are not written in ascending order,
 * so the whole config is walked using RuneLite's ItemLoader opcode list; decoding stops (keeping
 * what was read) only at an opcode it does not know.
 */
class ObjWear(
    val wearpos: Int,
    val wearpos2: Int,
    val wearpos3: Int,
    val name: String,
    val iops: Array<String?>,
    /** Ground ops 1-5 (op 3 defaults to Take). */
    val ops: Array<String?> = arrayOfNulls(5),
    /** Stacks in one inventory slot (config opcode 11, or a noted obj: opcode 98 certtemplate). */
    val stackable: Boolean = false,
) {
    val wearable get() = wearpos >= 0

    companion object {
        fun decode(data: ByteArray): ObjWear {
            val buf = java.nio.ByteBuffer.wrap(data)
            var wearpos = -1
            var wearpos2 = -1
            var wearpos3 = -1
            var name = "null"
            val iops = arrayOfNulls<String>(5).also { it[4] = "Drop" }
            val ops = arrayOfNulls<String>(5).also { it[2] = "Take" }
            var stackable = false
            fun readString(): String {
                val start = buf.position()
                while (buf.get().toInt() != 0) Unit
                return String(data, start, buf.position() - start - 1, Charsets.ISO_8859_1)
            }
            fun u1() = buf.get().toInt() and 0xFF
            fun skip(n: Int) = buf.position(buf.position() + n)
            fun skipString() { while (buf.get().toInt() != 0) Unit }
            try {
                loop@ while (buf.hasRemaining()) {
                    when (val op = u1()) {
                        0 -> break@loop
                        1, 4, 5, 6, 7, 8, 24, 26, 75, 78, 79, 90, 91, 92, 93, 94, 95, 97, 99,
                        110, 111, 112, 139, 140, 148, 149 -> skip(2)
                        2 -> name = readString()
                        3, 9 -> skipString()
                        11 -> stackable = true
                        98 -> { skip(2); stackable = true }
                        15, 16, 65, 160, 251 -> Unit
                        12, 44, 46, 47, 49, 50, 51, 52, 53, 54 -> skip(4)
                        13 -> wearpos = buf.get().toInt()
                        14 -> wearpos2 = buf.get().toInt()
                        23, 25 -> skip(3)
                        27 -> wearpos3 = buf.get().toInt()
                        in 30..34 -> ops[op - 30] = readString().takeUnless { it.equals("hidden", true) }
                        in 35..39 -> iops[op - 35] = readString().takeUnless { it.equals("hidden", true) }
                        40, 41 -> skip(u1() * 4)
                        42, 113, 114, 115 -> skip(1)
                        43 -> {
                            skip(1)
                            while (u1() != 0) skipString()
                        }
                        45, 48 -> skip(5)
                        in 100..109 -> skip(4)
                        161 -> skip((buf.short.toInt() and 0xFFFF) * 2)
                        249 -> repeat(u1()) {
                            val isString = u1() == 1
                            skip(3)
                            if (isString) skipString() else skip(4)
                        }
                        else -> break@loop // e.g. 200-202 (entity ops), whose layout is not known here

                    }
                }
            } catch (e: java.nio.BufferUnderflowException) {
                // Malformed or unknown layout: keep whatever was decoded.
            }
            return ObjWear(wearpos, wearpos2, wearpos3, name, iops, ops, stackable)
        }
    }
}

/** Little reader for config records (cp1252/latin-1 strings, big-endian ints). */
private class ConfigReader(val data: ByteArray) {
    val buf: java.nio.ByteBuffer = java.nio.ByteBuffer.wrap(data)
    fun u1() = buf.get().toInt() and 0xFF
    fun u2() = buf.short.toInt() and 0xFFFF
    fun skip(n: Int) { buf.position(buf.position() + n) }
    fun string(): String {
        val start = buf.position()
        while (buf.get().toInt() != 0) Unit
        return String(data, start, buf.position() - start - 1, Charsets.ISO_8859_1)
    }
    fun params() = repeat(u1()) {
        val isString = u1() == 1
        skip(3)
        if (isString) string() else skip(4)
    }
}

/** Name and ops of an NPC or loc config; ops[i] is op i+1 (null = none/hidden). */
class EntityType(
    val name: String,
    val ops: Array<String?>,
    val sizeX: Int = 1,
    val sizeZ: Int = 1,
    /** Locs: 0 = walk through, 1 = blocks walking only, 2 = blocks walking and projectiles (default). */
    val interactType: Int = 2,
    /** NPCs: attack, defence, strength, hitpoints, ranged, magic levels (config opcodes 74-79). */
    val stats: IntArray = intArrayOf(1, 1, 1, 1, 1, 1),
    val combatLevel: Int = -1,
) {
    fun op(op: Int): String? = ops.getOrNull(op - 1)

    companion object {
        /** NPC config (archive 2, group 9), following RuneLite's NpcLoader; stops at unknown opcodes. */
        fun decodeNpc(data: ByteArray): EntityType {
            val r = ConfigReader(data)
            var name = "null"
            var size = 1
            val ops = arrayOfNulls<String>(5)
            val stats = intArrayOf(1, 1, 1, 1, 1, 1)
            var combatLevel = -1
            try {
                loop@ while (r.buf.hasRemaining()) {
                    when (val op = r.u1()) {
                        0 -> break@loop
                        1 -> r.skip(r.u1() * 2)
                        2 -> name = r.string()
                        12 -> size = r.u1()
                        in 74..79 -> stats[op - 74] = r.u2()
                        95 -> combatLevel = r.u2()
                        13, 14, 15, 16, 18, 97, 98, 103, 114, 124, 126, 146 -> r.skip(2)
                        17, 115, 117 -> r.skip(8)
                        in 30..34 -> ops[op - 30] = r.string().takeUnless { it.equals("hidden", true) }
                        40, 41 -> r.skip(r.u1() * 4)
                        60 -> r.skip(r.u1() * 2)
                        93, 99, 107, 109, 111, 122, 123, 145, 147 -> Unit
                        100, 101 -> r.skip(1)
                        106, 118 -> {
                            r.skip(4)
                            if (op == 118) r.skip(2)
                            r.skip((r.u1() + 1) * 2)
                        }
                        249 -> r.params()
                        else -> break@loop
                    }
                }
            } catch (e: java.nio.BufferUnderflowException) {
            }
            return EntityType(name, ops, size, size, stats = stats, combatLevel = combatLevel)
        }

        /** Loc config (archive 2, group 6), following RuneLite's ObjectLoader; stops at unknown opcodes. */
        fun decodeLoc(data: ByteArray): EntityType {
            val r = ConfigReader(data)
            var name = "null"
            var sizeX = 1
            var sizeZ = 1
            var interactType = 2
            val ops = arrayOfNulls<String>(5)
            try {
                loop@ while (r.buf.hasRemaining()) {
                    when (val op = r.u1()) {
                        0 -> break@loop
                        1 -> r.skip(r.u1() * 3)
                        17 -> interactType = 0
                        27 -> interactType = 1
                        2 -> name = r.string()
                        5 -> r.skip(r.u1() * 2)
                        // Rev 241 caches: models as int ids (6: model u4 + shape u1, 7: model u4).
                        6 -> r.skip(r.u1() * 5)
                        7 -> r.skip(r.u1() * 4)
                        14 -> sizeX = r.u1()
                        15 -> sizeZ = r.u1()
                        18, 21, 22, 23, 62, 64, 73, 74, 89, 90 -> Unit
                        19, 28, 29, 39, 69, 75, 81 -> r.skip(1)
                        24, 60, 61, 65, 66, 67, 68, 70, 71, 72, 82 -> r.skip(2)
                        in 30..34 -> ops[op - 30] = r.string().takeUnless { it.equals("hidden", true) }
                        40, 41 -> r.skip(r.u1() * 4)
                        77, 92 -> {
                            r.skip(4)
                            if (op == 92) r.skip(2)
                            r.skip((r.u1() + 1) * 2)
                        }
                        78 -> r.skip(4)
                        79 -> {
                            r.skip(6)
                            r.skip(r.u1() * 2)
                        }
                        249 -> r.params()
                        else -> break@loop
                    }
                }
            } catch (e: java.nio.BufferUnderflowException) {
            }
            return EntityType(name, ops, sizeX, sizeZ, interactType)
        }
    }
}
