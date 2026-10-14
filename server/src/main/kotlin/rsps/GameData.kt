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
class ObjWear(val wearpos: Int, val wearpos2: Int, val wearpos3: Int, val name: String, val iops: Array<String?>) {
    val wearable get() = wearpos >= 0

    companion object {
        fun decode(data: ByteArray): ObjWear {
            val buf = java.nio.ByteBuffer.wrap(data)
            var wearpos = -1
            var wearpos2 = -1
            var wearpos3 = -1
            var name = "null"
            val iops = arrayOfNulls<String>(5).also { it[4] = "Drop" }
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
                        1, 4, 5, 6, 7, 8, 24, 26, 75, 78, 79, 90, 91, 92, 93, 94, 95, 97, 98, 99,
                        110, 111, 112, 139, 140, 148, 149 -> skip(2)
                        2 -> name = readString()
                        3, 9 -> skipString()
                        11, 15, 16, 65, 160, 251 -> Unit
                        12, 44, 46, 47, 49, 50, 51, 52, 53, 54 -> skip(4)
                        13 -> wearpos = buf.get().toInt()
                        14 -> wearpos2 = buf.get().toInt()
                        23, 25 -> skip(3)
                        27 -> wearpos3 = buf.get().toInt()
                        in 30..34 -> skipString()
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
            return ObjWear(wearpos, wearpos2, wearpos3, name, iops)
        }
    }
}
