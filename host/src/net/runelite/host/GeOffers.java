package net.runelite.host;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.eventbus.EventBus;

/**
 * Grand Exchange offers from the game's slot objects (Native.geOffers, docs/offsets.md "GE offers").
 * Mobile keeps the state (0 empty, 1 adding, 2 stable, 5 finished) and the type (0 buy, 1 sell)
 * in separate bytes; desktop packs them into one (type = bit 3). GrandExchangeOfferChanged is
 * posted when a slot differs from the last cycle's, and for every slot on the first read after a
 * login (desktop gets UPDATE_STOCKMARKET_SLOT for each slot at login).
 * Verified 2026-10-17 on OpenRune's GE (server dev's device run: SELLING -> SOLD, BUYING -> CANCELLED_BUY).
 */
final class GeOffers {
    private GeOffers() {}

    static final class Offer implements GrandExchangeOffer {
        final int state, type, item, total, done;
        final long price, spent;

        Offer(long[] r, int i) {
            state = (int) r[7 * i];
            type = (int) r[7 * i + 1];
            item = (int) r[7 * i + 2];
            price = r[7 * i + 3];
            total = (int) r[7 * i + 4];
            done = (int) r[7 * i + 5];
            spent = r[7 * i + 6];
        }

        Offer() {
            state = 0; type = 0; item = 0; total = 0; done = 0; price = 0; spent = 0;
        }

        @Override public int getQuantitySold() { return done; }
        @Override public int getItemId() { return item; }
        @Override public int getTotalQuantity() { return total; }
        @Override public long getPrice() { return price; }
        @Override public long getSpent() { return spent; }

        @Override
        public GrandExchangeOfferState getState() {
            boolean buy = type == 0;
            switch (state) {
                case 1:
                case 2:
                    return buy ? GrandExchangeOfferState.BUYING : GrandExchangeOfferState.SELLING;
                case 5:
                    if (done < total) return buy ? GrandExchangeOfferState.CANCELLED_BUY : GrandExchangeOfferState.CANCELLED_SELL;
                    return buy ? GrandExchangeOfferState.BOUGHT : GrandExchangeOfferState.SOLD;
                default:
                    return GrandExchangeOfferState.EMPTY;
            }
        }

        boolean same(Offer o) {
            return o != null && o.state == state && o.type == type && o.item == item && o.price == price
                && o.total == total && o.done == done && o.spent == spent;
        }

        @Override
        public String toString() {
            return getState() + " item " + item + " " + done + "/" + total + " @" + price + " spent " + spent;
        }
    }

    private static final Offer EMPTY = new Offer();
    private static Offer[] last;

    static GrandExchangeOffer[] read() {
        long[] r = Native.geOffers();
        GrandExchangeOffer[] out = new GrandExchangeOffer[8];
        for (int i = 0; i < 8; i++) out[i] = r != null && r.length >= 56 && r[7 * i] >= 0 ? new Offer(r, i) : EMPTY;
        return out;
    }

    /** Once per cycle while logged in. */
    static void poll(EventBus bus) {
        if (!bus.hasSubscribers(GrandExchangeOfferChanged.class)) { last = null; return; }
        GrandExchangeOffer[] now = read();
        Offer[] cur = new Offer[8];
        for (int i = 0; i < 8; i++) cur[i] = (Offer) now[i];
        Offer[] prev = last;
        last = cur;
        for (int i = 0; i < 8; i++) {
            if (prev != null && cur[i].same(prev[i])) continue;
            GrandExchangeOfferChanged e = new GrandExchangeOfferChanged();
            e.setSlot(i);
            e.setOffer(cur[i]);
            bus.post(e);
        }
    }

    /** A logout: the next login posts every slot again. */
    static void reset() { last = null; }
}
