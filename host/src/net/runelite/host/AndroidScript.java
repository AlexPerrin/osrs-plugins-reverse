package net.runelite.host;

/**
 * A client script as the game's VM runs it (desktop Script): the opcodes and int operands of the game's decoded
 * script (Native.scriptCode, through its script cache), read once. getHash is the script id (desktop's cache key).
 */
final class AndroidScript implements net.runelite.api.Script {
    final int id;
    private final int[] instructions, intOperands;

    private AndroidScript(int id, int[] instructions, int[] intOperands) {
        this.id = id;
        this.instructions = instructions;
        this.intOperands = intOperands;
    }

    /** Game thread: script `id`, or null if the game can't load it. */
    static AndroidScript of(int id) {
        Object[] c = id < 0 ? null : Native.scriptCode(id);
        return c == null ? null : new AndroidScript(id, (int[]) c[0], (int[]) c[1]);
    }

    @Override public int[] getInstructions() { return instructions; }
    @Override public int[] getIntOperands() { return intOperands; }
    @Override public net.runelite.api.Node getNext() { return null; }
    @Override public net.runelite.api.Node getPrevious() { return null; }
    @Override public long getHash() { return id; }
    @Override public String toString() { return "Script[" + id + ", " + instructions.length + " instructions]"; }
}
