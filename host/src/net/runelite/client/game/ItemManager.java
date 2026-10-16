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
    public List<?> search(String query) { return Collections.emptyList(); }
    public ItemStats getItemStats(int id) { return null; }

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
