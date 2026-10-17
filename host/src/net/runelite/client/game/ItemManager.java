package net.runelite.client.game;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Replaces RuneLite's ItemManager (same name; the original needs OkHttp and the price API).
 * Definitions come from the client; prices are store values (no price API); images are the
 * game's own item icons.
 */
public class ItemManager {
    private final Client client;
    private final net.runelite.client.callback.ClientThread clientThread;
    private final java.util.Map<String, AsyncBufferedImage> images = java.util.Collections.synchronizedMap(new LRU<String, AsyncBufferedImage>(512));
    private final java.util.Map<String, BufferedImage> outlines = java.util.Collections.synchronizedMap(new LRU<String, BufferedImage>(128));

    @Inject
    public ItemManager(Client client, net.runelite.client.callback.ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
        startIndexBuild();
    }

    private static final class LRU<K, V> extends java.util.LinkedHashMap<K, V> {
        private final int max;
        LRU(int max) { super(16, 0.75f, true); this.max = max; }
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<K, V> e) { return size() > max; }
    }

    public ItemComposition getItemComposition(int id) { return client.getItemDefinition(id); }
    public int canonicalize(int id) {
        ItemComposition c = client.getItemDefinition(id);
        if (c == null) return id;
        if (c.getNote() != -1) return c.getLinkedNoteId();
        if (c.getPlaceholderTemplateId() != -1) return c.getPlaceholderId();
        return id;
    }
    /**
     * No price API on the device: coins and platinum tokens have their fixed value, everything else
     * its store value (ItemComposition.getPrice), so value thresholds still order items sensibly.
     */
    public long getItemPrice(int id) {
        if (id == 995) return 1;          // ItemID.COINS_995
        if (id == 13204) return 1000;     // ItemID.PLATINUM_TOKEN
        ItemComposition c = client.getItemDefinition(canonicalize(id));
        return c != null ? c.getPrice() : 0;
    }
    public long getItemPriceWithSource(int id, boolean useWiki) { return getItemPrice(id); }
    /**
     * Desktop searches its price list (tradeable items) by name. Here the list is built from the
     * game's item definitions: a pass over all ids after login, 4096 ids per client cycle (~180 ms
     * in all on BlueStacks, so no single long frame); a search on the client thread before it is
     * done finishes the pass at once. Off the client thread before then, the result is empty.
     */
    public List<net.runelite.http.api.item.ItemPrice> search(String query) {
        List<net.runelite.http.api.item.ItemPrice> idx = priceIndex();
        String q = query.toLowerCase();
        List<net.runelite.http.api.item.ItemPrice> out = new java.util.ArrayList<>();
        for (net.runelite.http.api.item.ItemPrice p : idx)
            if (p.getName().toLowerCase().contains(q)) out.add(p);
        return out;
    }

    /** Desktop prefers the wiki price; here both are the store value. */
    public long getWikiPrice(net.runelite.http.api.item.ItemPrice p) {
        return p.getWikiPrice() > 0 ? p.getWikiPrice() : p.getPrice();
    }

    private volatile List<net.runelite.http.api.item.ItemPrice> priceIndex;
    private final List<net.runelite.http.api.item.ItemPrice> building = new java.util.ArrayList<>();
    private int buildNext;
    private long buildNanos;

    private void startIndexBuild() {
        clientThread.invoke(() -> {
            if (priceIndex != null) return true;
            if (client.getGameState() != GameState.LOGGED_IN) return false;
            indexStep(4096);
            return priceIndex != null;
        });
    }

    /** Client thread: indexes up to `max` more ids; publishes the index when all are done. */
    private void indexStep(int max) {
        long t0 = System.nanoTime();
        int n = client.getItemCount();
        int end = (int) Math.min((long) n, (long) buildNext + max);
        for (int id = buildNext; id < end; id++) {
            ItemComposition c = client.getItemDefinition(id);
            if (c == null || !c.isTradeable() || c.getNote() != -1 || c.getPlaceholderTemplateId() != -1) continue;
            String name = c.getName();
            if (name == null || name.isEmpty() || name.equals("null")) continue;
            net.runelite.http.api.item.ItemPrice p = new net.runelite.http.api.item.ItemPrice();
            p.setId(id);
            p.setName(name);
            p.setPrice(getItemPrice(id));
            p.setWikiPrice(p.getPrice());
            building.add(p);
        }
        buildNext = end;
        buildNanos += System.nanoTime() - t0;
        if (buildNext >= n) {
            priceIndex = Collections.unmodifiableList(new java.util.ArrayList<>(building));
            building.clear();
            android.util.Log.i("osrsrl-host", "item search index: " + priceIndex.size() + " of " + n + " items in "
                + buildNanos / 1000000 + " ms");
        }
    }

    private List<net.runelite.http.api.item.ItemPrice> priceIndex() {
        List<net.runelite.http.api.item.ItemPrice> idx = priceIndex;
        if (idx != null) return idx;
        if (!client.isClientThread() || client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
            return Collections.emptyList();
        indexStep(Integer.MAX_VALUE);
        return priceIndex != null ? priceIndex : Collections.<net.runelite.http.api.item.ItemPrice>emptyList();
    }

    public ItemStats getItemStats(int id) { return null; }
    public net.runelite.http.api.item.ItemStats getItemStats(int id, boolean allowNote) { return null; }

    public AsyncBufferedImage getImage(int id) { return getImage(id, 1, false); }
    /**
     * The game's inventory icon (Client.createItemSprite, rendered on the game thread like
     * RuneLite's loader); blank until loaded, then onLoaded listeners run. Gives up after ~10 s
     * (unknown ids).
     */
    public AsyncBufferedImage getImage(int id, int quantity, boolean stackable) {
        String key = id + ":" + quantity + ":" + stackable;
        AsyncBufferedImage img = images.get(key);
        if (img != null) return img;
        AsyncBufferedImage fresh = new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB);
        images.put(key, fresh);
        final int[] tries = {0};
        clientThread.invoke(() -> {
            if (client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal()) return false;
            SpritePixels sprite = client.createItemSprite(id, quantity, 1, 3153952, stackable ? 1 : 0, false, 512);
            if (sprite == null) return ++tries[0] > 500;
            sprite.toBufferedImage(fresh);
            fresh.loaded();
            return true;
        });
        return fresh;
    }

    /**
     * Outline of the item's icon (Inventory Tags, Quest Helper). Rendered on the game thread; until
     * then (or off it) a blank image is returned and not cached.
     */
    public BufferedImage getItemOutline(int id, int quantity, Color color) {
        String key = id + ":" + quantity + ":" + (color != null ? color.getRGB() : 0);
        BufferedImage cached = outlines.get(key);
        if (cached != null) return cached;
        SpritePixels sprite = client.createItemSprite(id, quantity, 1, 0, 0, false, 512);
        if (sprite == null) {
            if (!clientThread.isClientThread()) clientThread.invokeLater(() -> { getItemOutline(id, quantity, color); });
            return new BufferedImage(38, 34, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage out = sprite.toBufferedOutline(color);
        outlines.put(key, out);
        return out;
    }
}
