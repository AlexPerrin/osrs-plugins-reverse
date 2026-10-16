package net.runelite.host;

import java.awt.Shape;
import java.util.Arrays;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.model.Jarvis;

/**
 * Stand-ins for model hulls: models aren't read on Android, so actors and scene objects use the
 * convex hull of a box over their footprint, projected with the client's camera.
 */
final class Hulls {
    private Hulls() {}

    /**
     * @param centerX,centerY box centre in local units
     * @param halfX,halfY     half extents in local units
     * @param height          box height in local units above the ground at the centre
     */
    static Shape box(int centerX, int centerY, int halfX, int halfY, int plane, int height) {
        Client client = Host.client();
        if (client == null) return null;
        int ground = Perspective.getTileHeight(client, new LocalPoint(centerX, centerY, WorldView.TOPLEVEL), plane);
        int[] xs = new int[8], ys = new int[8];
        int n = 0;
        for (int dz = 0; dz < 2; dz++)
            for (int dx = -1; dx <= 1; dx += 2)
                for (int dy = -1; dy <= 1; dy += 2) {
                    net.runelite.api.Point p = Perspective.localToCanvas(client, centerX + dx * halfX, centerY + dy * halfY,
                            dz == 0 ? ground : ground - height);
                    if (p == null) continue;
                    xs[n] = p.getX();
                    ys[n] = p.getY();
                    n++;
                }
        if (n < 3) return null;
        return Jarvis.convexHull(Arrays.copyOf(xs, n), Arrays.copyOf(ys, n));
    }

    /**
     * Convex hull of a posed model (Native.actorModel: {height, x0, y0, z0, ...}), rotated by the
     * orientation (0..2047, Jagex direction) and placed at the local point on the ground, as
     * RuneLite's Model.getConvexHull. Null when nothing projects.
     */
    static Shape model(int[] m, int localX, int localY, int orientation, int plane) {
        Client client = Host.client();
        if (client == null || m == null || m.length < 4) return null;
        int ground = Perspective.getTileHeight(client, new LocalPoint(localX, localY, WorldView.TOPLEVEL), plane);
        int sin = Perspective.SINE[orientation & 2047], cos = Perspective.COSINE[orientation & 2047];
        int n = (m.length - 1) / 3;
        int[] xs = new int[n], ys = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int vx = m[1 + 3 * i], vy = m[2 + 3 * i], vz = m[3 + 3 * i];
            int rx = (vx * cos + vz * sin) >> 16, rz = (vz * cos - vx * sin) >> 16;
            net.runelite.api.Point p = Perspective.localToCanvas(client, localX + rx, localY + rz, ground + vy);
            if (p == null) continue;
            xs[k] = p.getX();
            ys[k] = p.getY();
            k++;
        }
        if (k < 3) return null;
        return Jarvis.convexHull(Arrays.copyOf(xs, k), Arrays.copyOf(ys, k));
    }

    /** Model hulls are on unless `setprop debug.osrsrl.modelhull 0`. */
    static boolean modelHulls() { return !"0".equals(Native.property("debug.osrsrl.modelhull")); }
}
