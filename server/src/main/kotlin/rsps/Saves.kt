package rsps

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Per-account player saves: one JSON file per lowercase username in [dir] with position,
 * hitpoints, experience, varps and the inventory/worn/bank contents. Under Docker the directory is
 * a named volume (`rsps-saves:/saves`), so saves survive container rebuilds (the `./data` bind
 * mount can come up empty under Docker Desktop).
 */
class Saves(private val dir: Path) {
    /** The file layout; inventories are `[slot, obj, count]` triples (empty slots left out). */
    class Save(
        val x: Int,
        val z: Int,
        val level: Int,
        val hitpoints: Int,
        val experience: IntArray,
        val varps: Map<Int, Int>,
        val inv: List<IntArray>,
        val worn: List<IntArray>,
        val bank: List<IntArray>,
    ) {
        val coord get() = Coord(x, z, level)
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        Files.createDirectories(dir)
    }

    private fun file(username: String) = dir.resolve(username.lowercase().replace(Regex("[^a-z0-9_-]"), "_") + ".json")

    fun load(username: String): Save? {
        val f = file(username)
        if (!Files.exists(f)) return null
        return gson.fromJson(Files.readString(f), Save::class.java)
    }

    fun save(player: Player) {
        fun slots(inv: Inventory) = inv.objs.indices.filter { inv.objs[it] != -1 }.map { intArrayOf(it, inv.objs[it], inv.counts[it]) }
        val c = player.coord
        val save = Save(c.x, c.z, c.level, player.hitpoints, player.experience.copyOf(), player.varps.toSortedMap(),
            slots(player.inventory), slots(player.worn), slots(player.bank))
        val f = file(player.username)
        val tmp = f.resolveSibling(f.fileName.toString() + ".tmp")
        Files.writeString(tmp, gson.toJson(save))
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Copies a save's state into a fresh [player] (nothing is sent; login sends it). */
    fun apply(save: Save, player: Player) {
        player.hitpoints = save.hitpoints
        if (save.experience.size == player.experience.size) save.experience.copyInto(player.experience)
        player.varps.putAll(save.varps.orEmpty())
        for ((slots, inv) in listOf(save.inv to player.inventory, save.worn to player.worn, save.bank to player.bank)) {
            for (s in slots.orEmpty()) {
                if (s.size != 3 || s[0] !in 0 until inv.capacity) continue
                inv.objs[s[0]] = s[1]
                inv.counts[s[0]] = s[2]
            }
        }
    }
}
