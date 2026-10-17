package net.runelite.host;

import java.util.EnumSet;
import net.runelite.api.World;
import net.runelite.api.WorldType;

/** Client.createWorld: a plain World record (World Hopper / Default World fill it in from the world list). */
final class AndroidWorld implements World {
    private EnumSet<WorldType> types = EnumSet.noneOf(WorldType.class);
    private int playerCount, location, index, id;
    private String activity = "", address = "";

    @Override public EnumSet<WorldType> getTypes() { return types; }
    @Override public void setTypes(EnumSet<WorldType> t) { types = t != null ? t : EnumSet.noneOf(WorldType.class); }
    @Override public int getPlayerCount() { return playerCount; }
    @Override public void setPlayerCount(int n) { playerCount = n; }
    @Override public int getLocation() { return location; }
    @Override public void setLocation(int l) { location = l; }
    @Override public int getIndex() { return index; }
    @Override public void setIndex(int i) { index = i; }
    @Override public int getId() { return id; }
    @Override public void setId(int i) { id = i; }
    @Override public String getActivity() { return activity; }
    @Override public void setActivity(String a) { activity = a; }
    @Override public String getAddress() { return address; }
    @Override public void setAddress(String a) { address = a; }
    @Override public String toString() { return "World " + id + " (" + address + ")"; }
}
