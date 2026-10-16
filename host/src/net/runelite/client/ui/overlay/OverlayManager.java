package net.runelite.client.ui.overlay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.runelite.api.widgets.WidgetItem;

/**
 * Replaces RuneLite's OverlayManager (same name and plugin-facing API). Positions are not
 * persisted yet; there is no config manager on Android.
 */
public class OverlayManager {
    public static final String OPTION_CONFIGURE = "Configure";

    private final List<Overlay> overlays = new CopyOnWriteArrayList<>();
    private volatile Collection<WidgetItem> widgetItems = Collections.emptyList();

    public void init() {}

    public synchronized boolean add(Overlay overlay) {
        if (overlays.contains(overlay)) return false;
        overlays.add(overlay);
        if (overlay instanceof WidgetItemOverlay) ((WidgetItemOverlay) overlay).setOverlayManager(this);
        return true;
    }

    public synchronized boolean remove(Overlay overlay) {
        return overlays.remove(overlay);
    }

    public synchronized boolean removeIf(Predicate<Overlay> filter) {
        return overlays.removeIf(filter);
    }

    public synchronized boolean anyMatch(Predicate<Overlay> filter) {
        return overlays.stream().anyMatch(filter);
    }

    public synchronized void clear() {
        overlays.clear();
    }

    public synchronized void saveOverlay(Overlay overlay) {}

    public synchronized void resetOverlay(Overlay overlay) {
        overlay.setPreferredLocation(null);
        overlay.setPreferredSize(null);
        overlay.setPreferredPosition(null);
    }

    public Collection<WidgetItem> getWidgetItems() { return widgetItems; }
    public void setWidgetItems(Collection<WidgetItem> items) { widgetItems = items; }
    public synchronized boolean hasWidgetItemOverlays() {
        for (Overlay o : overlays) if (o instanceof WidgetItemOverlay) return true;
        return false;
    }

    /** Host: overlays to draw on `layer`, highest priority last (drawn on top). */
    public List<Overlay> getLayer(OverlayLayer layer) {
        List<Overlay> out = new ArrayList<>();
        for (Overlay o : overlays) {
            OverlayLayer l = o.getLayer() == OverlayLayer.MANUAL ? OverlayLayer.ABOVE_WIDGETS : o.getLayer();
            // Tooltips (TooltipOverlay) go last, after every overlay that may add one this frame.
            if (o.getPosition() == OverlayPosition.TOOLTIP) l = OverlayLayer.ALWAYS_ON_TOP;
            if (l == layer) out.add(o);
        }
        out.sort(Comparator.comparingDouble((Overlay o) -> o.getPosition() == OverlayPosition.TOOLTIP ? Float.MAX_VALUE : o.getPriority()));
        return out;
    }

    /** Host: true if any overlay is registered. */
    public boolean isEmpty() { return overlays.isEmpty(); }
}
