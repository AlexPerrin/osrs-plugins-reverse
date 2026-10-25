package net.runelite.host;

import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Node;

/**
 * An unlit model as desktop's ModelData, decoded by the game's model store (Native.modelData; layout at
 * game.cpp pack_mesh): vertices, faces, HSL face colours and the optional per-face arrays. light() runs
 * the game's normal pass (0x72a0e0) and colour pass (0x72aea4) in Java and returns an AndroidModel.
 * Arrays may be shared with copies (shallowCopy / clone*), as desktop.
 */
final class AndroidModelData implements ModelData {
    int verts, faces, triangles;
    float[] x, y, z;
    int[] a, b, c;
    short[] colours, textures;
    byte[] renderTypes, priorities, alpha, coords, triTypes;
    int[] triP, triM, triN;
    byte priority;
    int id = -1;  // the model store id while the vertex and face counts are the store's (applyTransformations)
    private int height;

    /** Model id's ModelData, or null when the game hasn't the model loaded (or off the game thread). */
    static AndroidModelData load(int id) {
        int[] m = Native.modelData(id);
        if (m == null || m.length < 7) return null;
        AndroidModel.Packed p = new AndroidModel.Packed(m);
        AndroidModelData r = new AndroidModelData();
        r.id = id;
        r.verts = p.n;
        r.faces = p.f;
        r.x = p.floats(r.verts);
        r.y = p.floats(r.verts);
        r.z = p.floats(r.verts);
        r.a = p.ints(r.faces);
        r.b = p.ints(r.faces);
        r.c = p.ints(r.faces);
        if (p.has(1)) p.skip(3 * r.faces);
        r.colours = p.has(2) ? p.shorts(r.faces) : new short[r.faces];
        if (p.has(4)) r.renderTypes = p.bytes(r.faces);
        if (p.has(8)) r.priorities = p.bytes(r.faces);
        if (p.has(16)) r.alpha = p.bytes(r.faces);
        if (p.has(32)) r.textures = p.shorts(r.faces);
        if (p.has(64)) r.coords = p.bytes(r.faces);
        r.triangles = p.t;
        if (p.has(128)) r.triTypes = p.bytes(p.t);
        if (p.t > 0) {
            r.triP = p.ints(p.t);
            r.triM = p.ints(p.t);
            r.triN = p.ints(p.t);
        }
        r.priority = (byte) p.priority;
        return r;
    }

    private AndroidModelData copy() {
        AndroidModelData r = new AndroidModelData();
        r.verts = verts; r.faces = faces; r.triangles = triangles;
        r.x = x; r.y = y; r.z = z; r.a = a; r.b = b; r.c = c;
        r.colours = colours; r.textures = textures; r.renderTypes = renderTypes; r.priorities = priorities;
        r.alpha = alpha; r.coords = coords; r.triTypes = triTypes; r.triP = triP; r.triM = triM; r.triN = triN;
        r.priority = priority; r.height = height; r.id = id;
        return r;
    }

    // ---- ModelData ----

    @Override public short[] getFaceColors() { return colours; }
    @Override public Model light() { return light(DEFAULT_AMBIENT, DEFAULT_CONTRAST, DEFAULT_X, DEFAULT_Y, DEFAULT_Z); }

    @Override public ModelData recolor(short from, short to) {
        for (int i = 0; i < faces; i++) if (colours[i] == from) colours[i] = to;
        return this;
    }

    @Override public ModelData retexture(short from, short to) {
        if (textures != null) for (int i = 0; i < faces; i++) if (textures[i] == from) textures[i] = to;
        return this;
    }

    @Override public ModelData shallowCopy() { return copy(); }

    @Override public ModelData cloneVertices() {
        AndroidModelData r = copy();
        r.x = x.clone(); r.y = y.clone(); r.z = z.clone();
        return r;
    }

    @Override public ModelData cloneColors() {
        AndroidModelData r = copy();
        r.colours = colours.clone();
        return r;
    }

    @Override public ModelData cloneTextures() {
        AndroidModelData r = copy();
        if (textures != null) r.textures = textures.clone();
        return r;
    }

    @Override public ModelData cloneTransparencies() { return cloneTransparencies(true); }

    /** A copy whose alpha array is its own (clone) or shared; a model without alpha gets an opaque one to write. */
    @Override public ModelData cloneTransparencies(boolean clone) {
        AndroidModelData r = copy();
        r.alpha = alpha == null ? new byte[faces] : clone ? alpha.clone() : alpha;
        return r;
    }

    /** Desktop's ModelData.light: the game's light (0x72a520) with its normal and colour passes. */
    @Override public Model light(int ambient, int contrast, int lx, int ly, int lz) {
        int[][] n = normals(verts, faces, x, y, z, a, b, c, renderTypes);
        int[] vnx = n[0], vny = n[1], vnz = n[2], vmag = n[3], fnx = n[4], fny = n[5], fnz = n[6];
        AndroidModel m = new AndroidModel();
        m.id = id;
        m.verts = verts;
        m.faces = faces;
        m.x = x.clone(); m.y = y.clone(); m.z = z.clone();
        m.a = a; m.b = b; m.c = c;
        m.c1 = new int[faces]; m.c2 = new int[faces]; m.c3 = new int[faces];
        m.unlit = colours;
        m.priorities = priorities;
        m.priority = priority;
        m.alpha = alpha;
        m.textures = textures;
        m.normalsX = vnx; m.normalsY = vny; m.normalsZ = vnz;
        int l = (int) Math.sqrt(lx * lx + ly * ly + lz * lz) * contrast >> 8;
        int lf = l + l / 2;
        for (int i = 0; i < faces; i++) {
            int type = renderTypes == null ? 0 : renderTypes[i] & 0xff;
            boolean hidden = false;
            if (alpha != null) {
                if (alpha[i] == -2) type = 3;
                if (alpha[i] == -1) hidden = true;
            }
            int hsl = colours[i] & 0xffff;
            boolean textured = textures != null && textures[i] != -1;
            if (hidden || (textured ? type > 1 : type > 1 && type != 3)) {
                m.c3[i] = -2;
            } else if (type == 3) {
                m.c1[i] = 128;
                m.c3[i] = -1;
            } else if (type == 1) {
                int lit = lf != 0 ? (fnx[i] * lx + fny[i] * ly + fnz[i] * lz) / lf : 0;
                m.c1[i] = textured ? clamp(lit + ambient) : clamp((hsl & 0x7f) * (lit + ambient) >> 7) | (hsl & 0xff80);
                m.c3[i] = -1;
            } else {
                m.c1[i] = shade(a[i], textured, hsl, ambient, l, lx, ly, lz, vnx, vny, vnz, vmag);
                m.c2[i] = shade(b[i], textured, hsl, ambient, l, lx, ly, lz, vnx, vny, vnz, vmag);
                m.c3[i] = shade(c[i], textured, hsl, ambient, l, lx, ly, lz, vnx, vny, vnz, vmag);
            }
        }
        textureTriangles(m);
        return m;
    }

    private static int shade(int v, boolean textured, int hsl, int ambient, int l, int lx, int ly, int lz,
                             int[] nx, int[] ny, int[] nz, int[] mag) {
        int d = mag[v] * l;
        int lit = d != 0 ? (nz[v] * lz + ny[v] * ly + nx[v] * lx) / d : 0;
        return textured ? clamp(lit + ambient) : clamp((lit + ambient) * (hsl & 0x7f) >> 7) | (hsl & 0xff80);
    }

    private static int clamp(int v) { return v <= 1 ? 2 : Math.min(v, 126); }

    /** The game's light keeps only the type-0 texture triangles some face uses, and remaps the face coords. */
    private void textureTriangles(AndroidModel m) {
        if (triangles <= 0 || coords == null) return;
        int[] used = new int[triangles];
        for (int i = 0; i < faces; i++) if (coords[i] != -1 && (coords[i] & 0xff) < triangles) used[coords[i] & 0xff]++;
        int kept = 0;
        for (int t = 0; t < triangles; t++) if (used[t] > 0 && (triTypes == null || triTypes[t] == 0)) kept++;
        m.tex1 = new int[kept]; m.tex2 = new int[kept]; m.tex3 = new int[kept];
        int k = 0;
        for (int t = 0; t < triangles; t++) {
            if (used[t] > 0 && (triTypes == null || triTypes[t] == 0)) {
                m.tex1[k] = triP[t]; m.tex2[k] = triM[t]; m.tex3[k] = triN[t];
                used[t] = k++;
            } else {
                used[t] = -1;
            }
        }
        m.textureFaces = new byte[faces];
        for (int i = 0; i < faces; i++)
            m.textureFaces[i] = coords[i] != -1 && (coords[i] & 0xff) < triangles ? (byte) used[coords[i] & 0xff] : -1;
    }

    /**
     * The game's normal pass (0x72a0e0): per face the cross product of its edges, halved until each component is
     * within +-8192, scaled to length 256; smooth faces (type 0, or no types) add it to their vertices' normals and
     * magnitudes, flat faces (type 1) keep it as the face normal. {vx, vy, vz, magnitude, fx, fy, fz}.
     */
    static int[][] normals(int verts, int faces, float[] x, float[] y, float[] z, int[] a, int[] b, int[] c, byte[] types) {
        int[] vx = new int[verts], vy = new int[verts], vz = new int[verts], mag = new int[verts];
        int[] fx = new int[faces], fy = new int[faces], fz = new int[faces];
        for (int i = 0; i < faces; i++) {
            int ia = a[i], ib = b[i], ic = c[i];
            int dx1 = (int) x[ib] - (int) x[ia], dy1 = (int) y[ib] - (int) y[ia], dz1 = (int) z[ib] - (int) z[ia];
            int dx2 = (int) x[ic] - (int) x[ia], dy2 = (int) y[ic] - (int) y[ia], dz2 = (int) z[ic] - (int) z[ia];
            int nx = dy1 * dz2 - dz1 * dy2, ny = dz1 * dx2 - dx1 * dz2, nz = dx1 * dy2 - dy1 * dx2;
            while (nx > 8192 || ny > 8192 || nz > 8192 || nx < -8192 || ny < -8192 || nz < -8192) {
                nx >>= 1; ny >>= 1; nz >>= 1;
            }
            int len = Math.max(1, (int) Math.sqrt(nx * nx + ny * ny + nz * nz));
            nx = (nx << 8) / len; ny = (ny << 8) / len; nz = (nz << 8) / len;
            int type = types == null ? 0 : types[i] & 0xff;
            if (type == 0) {
                for (int v : new int[] {ia, ib, ic}) { vx[v] += nx; vy[v] += ny; vz[v] += nz; mag[v]++; }
            } else if (type == 1) {
                fx[i] = nx; fy[i] = ny; fz[i] = nz;
            }
        }
        return new int[][] {vx, vy, vz, mag, fx, fy, fz};
    }

    /** Desktop's Client.mergeModels: one ModelData of every part's vertices and faces, in order. */
    static AndroidModelData merge(ModelData[] parts, int count) {
        AndroidModelData r = new AndroidModelData();
        boolean types = false, prios = false, alphas = false, texs = false, coord = false;
        for (int i = 0; i < count; i++) {
            AndroidModelData p = (AndroidModelData) parts[i];
            if (p == null) continue;
            r.verts += p.verts; r.faces += p.faces; r.triangles += p.triangles;
            types |= p.renderTypes != null; alphas |= p.alpha != null; texs |= p.textures != null; coord |= p.coords != null;
            prios |= p.priorities != null || (i > 0 && parts[0] != null && p.priority != ((AndroidModelData) parts[0]).priority);
        }
        r.x = new float[r.verts]; r.y = new float[r.verts]; r.z = new float[r.verts];
        r.a = new int[r.faces]; r.b = new int[r.faces]; r.c = new int[r.faces];
        r.colours = new short[r.faces];
        if (types) r.renderTypes = new byte[r.faces];
        if (prios) r.priorities = new byte[r.faces];
        if (alphas) r.alpha = new byte[r.faces];
        if (texs) { r.textures = new short[r.faces]; java.util.Arrays.fill(r.textures, (short) -1); }
        if (coord) { r.coords = new byte[r.faces]; java.util.Arrays.fill(r.coords, (byte) -1); }
        r.triTypes = new byte[r.triangles];
        r.triP = new int[r.triangles]; r.triM = new int[r.triangles]; r.triN = new int[r.triangles];
        int v0 = 0, f0 = 0, t0 = 0;
        for (int i = 0; i < count; i++) {
            AndroidModelData p = (AndroidModelData) parts[i];
            if (p == null) continue;
            if (v0 == 0 && f0 == 0) r.priority = p.priority;
            System.arraycopy(p.x, 0, r.x, v0, p.verts);
            System.arraycopy(p.y, 0, r.y, v0, p.verts);
            System.arraycopy(p.z, 0, r.z, v0, p.verts);
            for (int f = 0; f < p.faces; f++) {
                r.a[f0 + f] = p.a[f] + v0; r.b[f0 + f] = p.b[f] + v0; r.c[f0 + f] = p.c[f] + v0;
                r.colours[f0 + f] = p.colours[f];
                if (types && p.renderTypes != null) r.renderTypes[f0 + f] = p.renderTypes[f];
                if (prios) r.priorities[f0 + f] = p.priorities != null ? p.priorities[f] : p.priority;
                if (alphas && p.alpha != null) r.alpha[f0 + f] = p.alpha[f];
                if (texs && p.textures != null) r.textures[f0 + f] = p.textures[f];
                if (coord && p.coords != null && p.coords[f] != -1) r.coords[f0 + f] = (byte) ((p.coords[f] & 0xff) + t0);
            }
            for (int t = 0; t < p.triangles; t++) {
                r.triTypes[t0 + t] = p.triTypes != null ? p.triTypes[t] : 0;
                r.triP[t0 + t] = p.triP[t] + v0; r.triM[t0 + t] = p.triM[t] + v0; r.triN[t0 + t] = p.triN[t] + v0;
            }
            v0 += p.verts; f0 += p.faces; t0 += p.triangles;
        }
        return r;
    }

    // ---- Mesh ----

    @Override public int getVerticesCount() { return verts; }
    @Override public float[] getVerticesX() { return x; }
    @Override public float[] getVerticesY() { return y; }
    @Override public float[] getVerticesZ() { return z; }
    @Override public int getFaceCount() { return faces; }
    @Override public int[] getFaceIndices1() { return a; }
    @Override public int[] getFaceIndices2() { return b; }
    @Override public int[] getFaceIndices3() { return c; }
    @Override public byte[] getFaceTransparencies() { return alpha; }
    @Override public short[] getFaceTextures() { return textures; }

    @Override public ModelData rotateY90Ccw() {
        for (int i = 0; i < verts; i++) { float t = x[i]; x[i] = z[i]; z[i] = -t; }
        return this;
    }

    @Override public ModelData rotateY180Ccw() {
        for (int i = 0; i < verts; i++) { x[i] = -x[i]; z[i] = -z[i]; }
        return this;
    }

    @Override public ModelData rotateY270Ccw() {
        for (int i = 0; i < verts; i++) { float t = z[i]; z[i] = x[i]; x[i] = -t; }
        return this;
    }

    @Override public ModelData translate(int dx, int dy, int dz) {
        for (int i = 0; i < verts; i++) { x[i] += dx; y[i] += dy; z[i] += dz; }
        return this;
    }

    /** The game's resize (0x729d8c): v * s / 128, truncated. */
    @Override public ModelData scale(int sx, int sy, int sz) {
        for (int i = 0; i < verts; i++) {
            x[i] = (int) x[i] * sx / 128;
            y[i] = (int) y[i] * sy / 128;
            z[i] = (int) z[i] * sz / 128;
        }
        return this;
    }

    // ---- Renderable / Node ----

    @Override public Model getModel() { return light(); }
    @Override public int getModelHeight() { return height; }
    @Override public void setModelHeight(int h) { height = h; }
    @Override public int getAnimationHeightOffset() { return 0; }
    @Override public int getRenderMode() { return RENDERMODE_DEFAULT; }
    @Override public Node getNext() { return null; }
    @Override public Node getPrevious() { return null; }
    @Override public long getHash() { return 0; }
}
