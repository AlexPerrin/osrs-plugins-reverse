package net.runelite.client.game;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Replaces RuneLite's ItemManager (same name; the original needs OkHttp and the price API).
 * Definitions come from the client; prices are 0 and images blank until item sprites exist.
 */
public class ItemManager {
    private final Client client;

    @Inject
    public ItemManager(Client client) {
        this.client = client;
    }

    public ItemComposition getItemComposition(int id) { return client.getItemDefinition(id); }
    public int canonicalize(int id) {
        ItemComposition c = client.getItemDefinition(id);
        if (c == null) return id;
        if (c.getNote() != -1) return c.getLinkedNoteId();
        if (c.getPlaceholderTemplateId() != -1) return c.getPlaceholderId();
        return id;
    }
    public long getItemPrice(int id) { return 0; }
    public long getItemPriceWithSource(int id, boolean useWiki) { return 0; }
    public List<?> search(String query) { return Collections.emptyList(); }
    public ItemStats getItemStats(int id) { return null; }

    public AsyncBufferedImage getImage(int id) { return getImage(id, 1, false); }
    public AsyncBufferedImage getImage(int id, int quantity, boolean stackable) {
        return new AsyncBufferedImage(null, 36, 32, BufferedImage.TYPE_INT_ARGB);
    }
    public BufferedImage getItemOutline(int id, int quantity, Color color) {
        return new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
    }
}
