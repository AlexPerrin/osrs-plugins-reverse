package rsps

import java.nio.ByteBuffer

/** Varbit config (archive 2, group 14): bits [lsb, msb] of a varp. */
class VarbitType(val varp: Int, val lsb: Int, val msb: Int) {
    val mask get() = if (msb - lsb >= 31) -1 else (1 shl (msb - lsb + 1)) - 1

    companion object {
        fun decode(data: ByteArray): VarbitType? {
            val buf = ByteBuffer.wrap(data)
            var result: VarbitType? = null
            while (buf.hasRemaining()) {
                when (buf.get().toInt() and 0xFF) {
                    0 -> break
                    1 -> result = VarbitType(buf.short.toInt() and 0xFFFF, buf.get().toInt() and 0xFF, buf.get().toInt() and 0xFF)
                    else -> return result
                }
            }
            return result
        }
    }
}

/**
 * A quest and the var holding its progress. The quest list and Quest Helper read progress with
 * clientscript 4029 (quest_status_get), which compares the value from script 4024 (a switch from
 * the quest's dbrow to one varp or varbit) with the dbrow's unstarted/end columns.
 */
class QuestType(
    val dbrow: Int,
    val name: String,
    /** True for a varbit, false for a varp. */
    val varbit: Boolean,
    val varId: Int,
    val unstarted: Int,
    val end: Int,
    val questPoints: Int,
    val kind: Kind = Kind.QUEST,
) {
    enum class Kind { QUEST, MINIQUEST, SUBQUEST }
}

/** Journal totals: quest count (without subquests), miniquest count, quest points available. */
class QuestTotals(val quests: Int, val miniquests: Int, val questPoints: Int)

object Quests {
    /** clientscript returning a quest's progress var, keyed by the quest's dbrow. */
    private const val SCRIPT_PROGRESS = 4024
    private const val DBTABLE_QUEST = 0
    private const val COL_DISPLAYNAME = 2
    private const val COL_TYPE = 4
    private const val COL_PARENT = 21
    private const val COL_QUESTPOINTS = 17
    private const val COL_UNSTARTEDSTATE = 18
    private const val COL_ENDSTATE = 19
    private const val OP_SWITCH = 60
    private const val OP_GET_VARP = 1
    private const val OP_GET_VARBIT = 25

    fun load(cache: Cache): List<QuestType> {
        val script = cache.files(12, SCRIPT_PROGRESS)[0] ?: return emptyList()
        val (instructions, switches) = disassemble(script)
        val switchIndex = instructions.indexOfFirst { it.first == OP_SWITCH }
        if (switchIndex < 0) return emptyList()
        val table = switches.getOrNull(instructions[switchIndex].second) ?: return emptyList()
        val rows = cache.files(2, 38)
        return table.mapNotNull { (row, offset) ->
            val (op, varId) = instructions.getOrNull(switchIndex + 1 + offset) ?: return@mapNotNull null
            if (op != OP_GET_VARP && op != OP_GET_VARBIT) return@mapNotNull null
            val data = rows[row] ?: return@mapNotNull null
            val (tableId, cols) = decodeDbRow(data)
            if (tableId != DBTABLE_QUEST) return@mapNotNull null
            QuestType(
                dbrow = row,
                name = cols[COL_DISPLAYNAME]?.firstOrNull() as? String ?: "quest $row",
                varbit = op == OP_GET_VARBIT,
                varId = varId,
                unstarted = cols[COL_UNSTARTEDSTATE]?.firstOrNull() as? Int ?: 0,
                end = cols[COL_ENDSTATE]?.firstOrNull() as? Int ?: 1,
                questPoints = cols[COL_QUESTPOINTS]?.firstOrNull() as? Int ?: 0,
                kind = kindOf(cols),
            )
        }.sortedBy { it.name }
    }

    private fun kindOf(cols: Map<Int, List<Any>>) = when {
        cols.containsKey(COL_PARENT) -> QuestType.Kind.SUBQUEST
        cols[COL_TYPE]?.firstOrNull() == 1 -> QuestType.Kind.MINIQUEST
        else -> QuestType.Kind.QUEST
    }

    /**
     * Totals over every row of the quest dbtable (also quests without a progress var here). The
     * journal summary shows them from varbits quests_total_count / qp_max, which the server sets.
     */
    fun totals(cache: Cache): QuestTotals {
        val kinds = cache.files(2, 38).values.mapNotNull { data ->
            val (tableId, cols) = runCatching { decodeDbRow(data) }.getOrNull() ?: return@mapNotNull null
            if (tableId != DBTABLE_QUEST) null else kindOf(cols) to (cols[COL_QUESTPOINTS]?.firstOrNull() as? Int ?: 0)
        }
        return QuestTotals(
            quests = kinds.count { it.first == QuestType.Kind.QUEST },
            miniquests = kinds.count { it.first == QuestType.Kind.MINIQUEST },
            questPoints = kinds.sumOf { it.second },
        )
    }

    /** Clientscript bytecode: (opcode, operand) per instruction, and the switch tables. */
    private fun disassemble(data: ByteArray): Pair<List<Pair<Int, Int>>, List<Map<Int, Int>>> {
        val buf = ByteBuffer.wrap(data)
        val switchLength = buf.getShort(data.size - 2).toInt() and 0xFFFF
        val end = data.size - 2 - switchLength - 12
        val switches = ArrayList<Map<Int, Int>>()
        buf.position(end + 12)
        repeat(buf.get().toInt() and 0xFF) {
            val map = HashMap<Int, Int>()
            repeat(buf.short.toInt() and 0xFFFF) { map[buf.int] = buf.int }
            switches += map
        }
        buf.position(0)
        while (buf.get().toInt() != 0) Unit // script name
        val instructions = ArrayList<Pair<Int, Int>>()
        while (buf.position() < end) {
            val op = buf.short.toInt() and 0xFFFF
            val operand = when {
                op == 3 -> { while (buf.get().toInt() != 0) Unit; 0 }
                op < 100 && op != 21 && op != 38 && op != 39 -> buf.int
                else -> buf.get().toInt() and 0xFF
            }
            instructions += op to operand
        }
        return instructions to switches
    }

    /** dbrow config: table id and the column values (strings or ints; longs are skipped). */
    private fun decodeDbRow(data: ByteArray): Pair<Int, Map<Int, List<Any>>> {
        val buf = ByteBuffer.wrap(data)
        var table = -1
        val cols = HashMap<Int, List<Any>>()
        loop@ while (buf.hasRemaining()) {
            when (buf.get().toInt() and 0xFF) {
                0 -> break@loop
                3 -> {
                    buf.get()
                    while (true) {
                        val col = buf.get().toInt() and 0xFF
                        if (col == 255) break
                        val types = IntArray(buf.get().toInt() and 0xFF) { buf.get().toInt() and 0xFF }
                        val values = ArrayList<Any>()
                        val first = buf.get().toInt() and 0xFF
                        val count = if (first < 0x80) first else ((first and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
                        repeat(count) {
                            for (type in types) {
                                when (type) {
                                    TYPE_STRING -> {
                                        val start = buf.position()
                                        while (buf.get().toInt() != 0) Unit
                                        values += String(data, start, buf.position() - start - 1, Charsets.ISO_8859_1)
                                    }
                                    TYPE_LONG -> buf.long
                                    else -> values += buf.int
                                }
                            }
                        }
                        cols[col] = values
                    }
                }
                4 -> {
                    var v = 0
                    do {
                        val b = buf.get().toInt() and 0xFF
                        v = (v shl 7) or (b and 0x7F)
                    } while (b and 0x80 != 0)
                    table = v
                }
                else -> break@loop
            }
        }
        return table to cols
    }

    private const val TYPE_LONG = 35
    private const val TYPE_STRING = 36
}
