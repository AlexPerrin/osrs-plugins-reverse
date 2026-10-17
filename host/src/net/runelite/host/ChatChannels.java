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
 * chat / clan channels"). Snapshots: a new object per call, as the game replaces its own.
 * // unverified: on the device (static RE of the CS2 ops; OpenRune's friends chat / clan support unknown).
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

    /** The friends chat the player is in, or null. Client thread only (null elsewhere). */
    static FriendsChatManager friendsChat() {
        Object[] r = Native.chatChannel(-2);
        return r != null ? new Fc(r) : null;
    }

    /** -1: the listened (guest) clan channel; 0..: the affined channel of that clan id (ClanID). */
    static ClanChannel clan(int which) {
        Object[] r = Native.chatChannel(which);
        return r != null ? new Clan(r) : null;
    }
}
