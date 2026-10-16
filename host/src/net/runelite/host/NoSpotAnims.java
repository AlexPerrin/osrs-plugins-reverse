package net.runelite.host;

import java.util.Collections;
import java.util.Iterator;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.IterableHashTable;

/** Actor spot animations aren't read from the game yet (Actor +0x6f0 vector): an empty table. */
final class NoSpotAnims implements IterableHashTable<ActorSpotAnim> {
    static final NoSpotAnims INSTANCE = new NoSpotAnims();
    @Override public ActorSpotAnim get(long key) { return null; }
    @Override public void put(ActorSpotAnim node, long key) {}
    @Override public Iterator<ActorSpotAnim> iterator() { return Collections.emptyIterator(); }
}
