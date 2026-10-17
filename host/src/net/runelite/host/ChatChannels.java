package net.runelite.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.Nameable;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.client.util.Text;

/**
 * Friends chat and clan channels read from the game (Native.chatChannel, docs/offsets.md "Friends
 * chat / clan channels"). One object per channel state (see friendsChat()).
 * Verified 2026-10-17 gen 25 with OpenRune's ::fakeclan (friends chat name/owner/ranks/members, clan channel 0).
 */
final class ChatChannels {
    private ChatChannels() {}

    private static String clean(String s) { return s == null ? "" : Text.removeTags(s).replace(' ', ' '); }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && Text.toJagexName(a).equalsIgnoreCase(Text.toJagexName(b));
    }

    abstract static class Member implements Nameable {
        final String name;
        final int world;
        Member(String name, int world) { this.name = name; this.world = world; }
        public String getName() { return name; }
        public String getPrevName() { return null; }
        public int getWorld() { return world; }
        @Override public int compareTo(Nameable o) { return name.compareToIgnoreCase(o.getName()); }
        @Override public String toString() { return name + "@" + world; }
    }

    static final class FcMember extends Member implements FriendsChatMember {
        final int rank;
        FcMember(String name, int world, int rank) { super(name, world); this.rank = rank; }
        @Override public FriendsChatRank getRank() { return rank(rank); }
    }

    static FriendsChatRank rank(int v) {
        try {
            FriendsChatRank r = FriendsChatRank.valueOf(v);
            return r != null ? r : FriendsChatRank.UNRANKED;
        } catch (RuntimeException e) {
            return FriendsChatRank.UNRANKED;
        }
    }

    static final class Fc implements FriendsChatManager {
        final String name, owner;
        final int myRank, kickRank;
        final FcMember[] members;
        Fc(Object[] r) {
            name = clean((String) r[0]);
            owner = clean((String) r[1]);
            int[] info = (int[]) r[2];
            myRank = info[0];
            kickRank = info[1];
            String[] names = (String[]) r[3];
            int[] worlds = (int[]) r[4], ranks = (int[]) r[5];
            members = new FcMember[names.length];
            for (int i = 0; i < names.length; i++) members[i] = new FcMember(clean(names[i]), worlds[i], ranks[i]);
        }
        @Override public String getOwner() { return owner; }
        @Override public String getName() { return name; }
        @Override public FriendsChatRank getMyRank() { return rank(myRank); }
        @Override public FriendsChatRank getKickRank() { return rank(kickRank); }
        @Override public int getCount() { return members.length; }
        @Override public int getSize() { return members.length; }
        @Override public FriendsChatMember[] getMembers() { return members.clone(); }
        @Override public FriendsChatMember findByName(String n) {
            for (FcMember m : members) if (sameName(m.name, n)) return m;
            return null;
        }
    }

    static final class ClanMember extends Member implements ClanChannelMember {
        final int rank;
        ClanMember(String name, int world, int rank) { super(name, world); this.rank = rank; }
        @Override public ClanRank getRank() { return new ClanRank(rank); }
    }

    static final class Clan implements ClanChannel {
        final String name;
        final List<ClanChannelMember> members;
        Clan(Object[] r) {
            name = clean((String) r[0]);
            String[] names = (String[]) r[3];
            int[] worlds = (int[]) r[4], ranks = (int[]) r[5];
            List<ClanChannelMember> l = new ArrayList<>();
            for (int i = 0; i < names.length; i++) l.add(new ClanMember(clean(names[i]), worlds[i], ranks[i]));
            members = Collections.unmodifiableList(l);
        }
        @Override public String getName() { return name; }
        @Override public List<ClanChannelMember> getMembers() { return members; }
        @Override public ClanChannelMember findMember(String n) {
            for (ClanChannelMember m : members) if (sameName(m.getName(), n)) return m;
            return null;
        }
    }

    // Desktop's channel objects live until the game replaces them, and plugins compare them with ==
    // (Chat Channel: clanMemberJoined.getClanChannel() == client.getClanChannel()); the same object is
    // returned while the game's data is unchanged.
    private static String fcSig;
    private static Fc fcCached;
    private static final java.util.Map<Integer, Object[]> clanCached = new java.util.HashMap<>();  // which -> {sig, Clan}

    /** The friends chat the player is in, or null. Client thread only (null elsewhere). */
    static synchronized FriendsChatManager friendsChat() {
        Object[] r = Native.chatChannel(-2);
        if (r == null) { fcSig = null; fcCached = null; return null; }
        String sig = java.util.Arrays.deepToString(r);
        if (!sig.equals(fcSig)) { fcSig = sig; fcCached = new Fc(r); }
        return fcCached;
    }

    /** -1: the listened (guest) clan channel; 0..: the affined channel of that clan id (ClanID). */
    static synchronized ClanChannel clan(int which) {
        Object[] r = Native.chatChannel(which);
        if (r == null) { clanCached.remove(which); return null; }
        String sig = java.util.Arrays.deepToString(r);
        Object[] c = clanCached.get(which);
        if (c == null || !sig.equals(c[0])) clanCached.put(which, c = new Object[] {sig, new Clan(r)});
        return (Clan) c[1];
    }

    /**
     * Clan settings are not read from the game (CLANSETTINGS packets): this view is built from the
     * clan channel (online members only, no titles), so plugins that need the settings to exist (Chat
     * Channel's clan join/leave messages) work without rank titles. // unverified: on the device.
     */
    static net.runelite.api.clan.ClanSettings settings(int which) {
        ClanChannel ch = clan(which);
        if (ch == null) return null;
        return new net.runelite.api.clan.ClanSettings() {
            @Override public String getName() { return ch.getName(); }
            @Override public List<net.runelite.api.clan.ClanMember> getMembers() {
                List<net.runelite.api.clan.ClanMember> l = new ArrayList<>();
                for (ClanChannelMember m : ch.getMembers()) l.add(asMember(m));
                return l;
            }
            @Override public net.runelite.api.clan.ClanMember findMember(String n) {
                ClanChannelMember m = ch.findMember(n);
                return m != null ? asMember(m) : null;
            }
            @Override public net.runelite.api.clan.ClanTitle titleForRank(ClanRank rank) { return null; }
        };
    }

    private static net.runelite.api.clan.ClanMember asMember(ClanChannelMember m) {
        return new net.runelite.api.clan.ClanMember() {
            @Override public String getName() { return m.getName(); }
            @Override public ClanRank getRank() { return m.getRank(); }
            @Override public java.time.LocalDate getJoinDate() { return null; }  // not in the channel data
        };
    }

    // --- events: desktop posts them from the channel packets; here the channels are diffed once per
    // game tick (before GameTick). Joining posts FriendsChatChanged(true) / ClanChannelChanged, then a
    // joined event per member already there, as the full channel arrives in one update on desktop.
    // Verified 2026-10-17 gen 25 (Chat Channel's join/leave messages for both).
    private static final int[] CLAN_SLOTS = {-1, 0, 1};  // guest, ClanID.CLAN, ClanID.GROUP_IRONMAN
    private static Fc lastFc;
    private static final Clan[] lastClan = new Clan[CLAN_SLOTS.length];

    private static final Class<?>[] EVENTS = {
        net.runelite.api.events.FriendsChatChanged.class, net.runelite.api.events.FriendsChatMemberJoined.class,
        net.runelite.api.events.FriendsChatMemberLeft.class, net.runelite.api.events.ClanChannelChanged.class,
        net.runelite.api.events.ClanMemberJoined.class, net.runelite.api.events.ClanMemberLeft.class,
    };

    /** Game thread, once per game tick while logged in. */
    static void postChanges(net.runelite.client.eventbus.EventBus bus) {
        boolean any = false;
        for (Class<?> c : EVENTS) any |= bus.hasSubscribers(c);
        if (!any) return;
        Fc fc = (Fc) friendsChat();
        Fc was = lastFc;
        lastFc = fc;
        if ((was == null) != (fc == null) || (was != null && !was.name.equals(fc.name))) {
            if (was != null) for (FcMember m : was.members) bus.post(new net.runelite.api.events.FriendsChatMemberLeft(m));
            if (was != null) bus.post(new net.runelite.api.events.FriendsChatChanged(false));
            if (fc != null) bus.post(new net.runelite.api.events.FriendsChatChanged(true));
            was = null;
        }
        if (fc != null) {
            for (FcMember m : fc.members) if (was == null || was.findByName(m.name) == null)
                bus.post(new net.runelite.api.events.FriendsChatMemberJoined(m));
            if (was != null) for (FcMember m : was.members) if (fc.findByName(m.name) == null)
                bus.post(new net.runelite.api.events.FriendsChatMemberLeft(m));
        }
        for (int k = 0; k < CLAN_SLOTS.length; k++) {
            int which = CLAN_SLOTS[k];
            Clan ch = (Clan) clan(which);
            Clan old = lastClan[k];
            lastClan[k] = ch;
            if ((old == null) != (ch == null) || (old != null && !old.name.equals(ch.name))) {
                bus.post(new net.runelite.api.events.ClanChannelChanged(ch, which < 0 ? -1 : which, which < 0));
                old = null;
            }
            if (ch == null) continue;
            for (ClanChannelMember m : ch.members) if (old == null || old.findMember(m.getName()) == null)
                bus.post(new net.runelite.api.events.ClanMemberJoined(ch, m));
            if (old != null) for (ClanChannelMember m : old.members) if (ch.findMember(m.getName()) == null)
                bus.post(new net.runelite.api.events.ClanMemberLeft(ch, m));
        }
    }

    /** Logged out: forget the channels (desktop clears them; no events). */
    static void reset() {
        lastFc = null;
        java.util.Arrays.fill(lastClan, null);
        synchronized (ChatChannels.class) { fcSig = null; fcCached = null; clanCached.clear(); }
    }
}
