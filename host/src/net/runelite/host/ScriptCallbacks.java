package net.runelite.host;

import android.util.Log;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.eventbus.EventBus;

/**
 * runelite_callback emulation at instruction level. RuneLite ships patched copies of some game
 * scripts with `sconst "name"; runelite_callback` inserted next to an instruction; the game's own
 * scripts have no callback. The native opcode hook (script_hooks, Native.setOpWatches) reports
 * when a watched (script, opcode) pair is about to run and when it has run; the callback is raised
 * there with the stacks the patched script would have, and the plugin's answer is applied.
 *
 * Callbacks (runelite-client/src/main/scripts):
 *  - skillTabBaseLevel (SkillTabBuilder, script 395): after stat_base (3306) pushes a skill's
 *    base level, int stack [skill, level]; the plugin's level replaces the pushed one
 *    (Virtual Levels: levels above 99).
 *  - skillTabTotalLevel (SkillTabTotalLevel, script 396): before if_settext (2100), object stack
 *    [..., "Total level: N"], int stack [..., component] (Virtual Levels' virtual total).
 *  - friendsChatSetText (FriendUpdate, script 125): before cc_settext (1100), object stack
 *    [..., name] (Friend Notes appends its note icon).
 *  - friendsChatSetPosition (script 125): before cc_setposition (1000), int stack
 *    [..., x, y, xmode, ymode] (Friend Notes moves the name right of the icon).
 * Callbacks raised before an instruction see the top of the live stacks; what the plugin changes
 * there is written back (ints with setScriptIntStack, strings with setScriptString).
 * Not emulated: skillTabMaxLevel (next to an iconst, which the opcode hook can't see).
 * // unverified: that the mobile skills tab runs scripts 395/396 (the desktop ids).
 */
final class ScriptCallbacks {
    private static final String TAG = "osrsrl-host";

    static final int SKILL_TAB_BUILDER = 395, SKILL_TAB_TOTAL = 396, FRIEND_UPDATE = 125;
    static final int STAT_BASE = 3306, IF_SETTEXT = 2100, CC_SETTEXT = 1100, CC_SETPOSITION = 1000;

    private static final int[] WATCHES = {
        SKILL_TAB_BUILDER, STAT_BASE,
        SKILL_TAB_TOTAL, IF_SETTEXT,
        FRIEND_UPDATE, CC_SETTEXT,
        FRIEND_UPDATE, CC_SETPOSITION,
    };

    private static boolean installed;
    private static final java.util.ArrayDeque<Integer> skills = new java.util.ArrayDeque<>();

    private ScriptCallbacks() {}

    /** Watches the pairs once the event bus has ScriptCallbackEvent subscribers (game thread). */
    static void update(EventBus bus) {
        boolean want = bus != null && bus.hasSubscribers(ScriptCallbackEvent.class);
        if (want == installed) return;
        installed = want;
        Native.setOpWatches(want ? WATCHES : null);
        Log.i(TAG, "opcode callbacks " + (want ? "on" : "off"));
    }

    /** From Host.onScript: phase 3 = before the instruction, 4 = after it. */
    static void onOp(EventBus bus, AndroidClient ac, int phase, int script, int opcode) {
        if (script == SKILL_TAB_BUILDER && opcode == STAT_BASE) skillTabBaseLevel(bus, ac, phase);
        else if (phase != 3) return;
        else if (script == SKILL_TAB_TOTAL && opcode == IF_SETTEXT) raiseOnLive(bus, ac, "skillTabTotalLevel", 1, 1);
        else if (script == FRIEND_UPDATE && opcode == CC_SETTEXT) raiseOnLive(bus, ac, "friendsChatSetText", 0, 1);
        else if (script == FRIEND_UPDATE && opcode == CC_SETPOSITION) raiseOnLive(bus, ac, "friendsChatSetPosition", 4, 0);
    }

    private static void skillTabBaseLevel(EventBus bus, AndroidClient ac, int phase) {
        int n = ac.getIntStackSize();
        if (phase == 3) {
            skills.push(n >= 1 ? ac.getIntStack()[n - 1] : -1);
            return;
        }
        if (skills.isEmpty()) return;
        int skill = skills.pop();
        if (skill < 0 || n < 1) return;
        int[] live = java.util.Arrays.copyOf(ac.getIntStack(), n);
        int level = live[n - 1];
        int answer = raise(bus, ac, "skillTabBaseLevel", new int[] {skill, level}, new Object[0], 1);
        if (answer != level) {
            live[n - 1] = answer;
            Native.setScriptIntStack(live, n);
        }
    }

    /**
     * Raises a callback with the top `ints` ints and `objs` objects of the live stacks (as the
     * patched script would see them right before the watched instruction) and writes back what
     * the plugin changed.
     */
    private static void raiseOnLive(EventBus bus, AndroidClient ac, String name, int ints, int objs) {
        int n = ac.getIntStackSize(), m = ac.getObjectStackSize();
        if (n < ints || m < objs) return;
        int[] live = java.util.Arrays.copyOf(ac.getIntStack(), n);
        Object[] liveObjs = java.util.Arrays.copyOf(ac.getObjectStack(), m);
        int[] fi = java.util.Arrays.copyOfRange(live, n - ints, n);
        Object[] fo = java.util.Arrays.copyOfRange(liveObjs, m - objs, m);
        ac.fakeStacks(fi, fo);
        post(bus, name);
        int[] outInts = java.util.Arrays.copyOf(ac.getIntStack(), ints);
        Object[] outObjs = java.util.Arrays.copyOf(ac.getObjectStack(), objs);
        ac.beginScriptEvent();  // back to the live stacks
        boolean intsChanged = false;
        for (int i = 0; i < ints; i++) {
            if (outInts[i] != fi[i]) { live[n - ints + i] = outInts[i]; intsChanged = true; }
        }
        if (intsChanged) Native.setScriptIntStack(live, n);
        for (int i = 0; i < objs; i++) {
            Object o = outObjs[i];
            if (o instanceof String && !o.equals(fo[i]) && !Native.setScriptString(m - objs + i, (String) o)) {
                Log.w(TAG, name + ": object stack slot " + (m - objs + i) + " is not a string");
            }
        }
    }

    /** Raises a callback on host-made stacks; returns int stack slot `slot` afterwards. Live stacks are restored. */
    private static int raise(EventBus bus, AndroidClient ac, String name, int[] ints, Object[] objs, int slot) {
        ac.fakeStacks(ints, objs);
        post(bus, name);
        int answer = ac.getIntStack()[slot];
        ac.beginScriptEvent();  // back to the live stacks; the fake ones are not written back
        return answer;
    }

    private static void post(EventBus bus, String name) {
        ScriptCallbackEvent e = new ScriptCallbackEvent();
        e.setEventName(name);
        bus.post(e);
    }
}
