package net.runelite.host;

/**
 * An actor's posed model as desktop's NPC/Player.getModel() returns it: vertices in model space
 * (unrotated, local units, y negative = up), face vertex indices and the lit face colours
 * (colours3 -1 = flat, -2 = hidden), read from the game's Model (Native.actorMesh, layout in
 * game.cpp read_mesh). A snapshot: the game builds a fresh posed model per call.
 */
final class AndroidModel extends ModelBag {
    private final int verts, faces;
    private final float[] x, y, z;
    private final int[] a, b, c, c1, c2, c3;

    private AndroidModel(int[] m) {
        _modelHeight = m[0];
        verts = m[1];
        faces = m[2];
        boolean colours = m[3] == 1;
        int o = 4;
        x = floats(m, o, verts); o += verts;
        y = floats(m, o, verts); o += verts;
        z = floats(m, o, verts); o += verts;
        a = ints(m, o, faces); o += faces;
        b = ints(m, o, faces); o += faces;
        c = ints(m, o, faces); o += faces;
        c1 = colours ? ints(m, o, faces) : new int[faces]; o += colours ? faces : 0;
        c2 = colours ? ints(m, o, faces) : new int[faces]; o += colours ? faces : 0;
        c3 = colours ? ints(m, o, faces) : filled(faces, -1);
    }

    private static int cacheStamp = -1;
    private static final java.util.HashMap<Integer, AndroidModel> cache = new java.util.HashMap<>();
    private static final AndroidModel NONE = new AndroidModel(new int[] {0, 0, 0, 0});

    /**
     * The posed model of an NPC (player=false) or player, or null (off the game thread, no model).
     * Read once per game cycle per actor (overlays ask every frame, several times per actor).
     */
    static AndroidModel of(boolean player, int index) {
        if (!Native.isClientThread()) return null;
        if (cacheStamp != Host.cycleStamp) { cache.clear(); cacheStamp = Host.cycleStamp; }
        Integer key = player ? ~index : index;
        AndroidModel r = cache.get(key);
        if (r == null) {
            int[] m = Native.actorMesh(player, index);
            r = m == null || m.length < 4 ? NONE : new AndroidModel(m);
            cache.put(key, r);
        }
        return r == NONE ? null : r;
    }

    private static float[] floats(int[] m, int off, int n) {
        float[] r = new float[n];
        for (int i = 0; i < n; i++) r[i] = m[off + i];
        return r;
    }

    private static int[] ints(int[] m, int off, int n) {
        int[] r = new int[n];
        System.arraycopy(m, off, r, 0, n);
        return r;
    }

    private static int[] filled(int n, int v) {
        int[] r = new int[n];
        java.util.Arrays.fill(r, v);
        return r;
    }

    @Override public int getVerticesCount() { return verts; }
    @Override public float[] getVerticesX() { return x; }
    @Override public float[] getVerticesY() { return y; }
    @Override public float[] getVerticesZ() { return z; }
    @Override public int getFaceCount() { return faces; }
    @Override public int[] getFaceIndices1() { return a; }
    @Override public int[] getFaceIndices2() { return b; }
    @Override public int[] getFaceIndices3() { return c; }
    @Override public int[] getFaceColors1() { return c1; }
    @Override public int[] getFaceColors2() { return c2; }
    @Override public int[] getFaceColors3() { return c3; }
    // unverified: default. Where the game's Model keeps per-face alpha is not found (the ModelData's is at
    // +0xe8, a byte vector, consumed by the light pass). Desktop returns null for models without
    // alpha, and callers null-check it (Improved Tile Indicators), so every face reads as opaque.
    @Override public byte[] getFaceTransparencies() { return null; }
    @Override public short[] getFaceTextures() { return null; }  // unverified: default (textures not read)
}
