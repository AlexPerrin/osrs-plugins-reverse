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
 *  - skillTabTotalLevel (SkillTabTotalLevel, script 396): before if_settext (2112), object stack
 *    [..., "Total level: N"], int stack [..., component] (Virtual Levels' virtual total).
 *  - friendsChatSetText (FriendUpdate, script 125): before cc_settext (1112), object stack
 *    [..., name] (Friend Notes appends its note icon).
 *  - friendsChatSetPosition (script 125): before cc_setposition (1000), int stack
 *    [..., x, y, xmode, ymode] (Friend Notes moves the name right of the icon).
 * Callbacks raised before an instruction see the top of the live stacks; what the plugin changes
 * there is written back (ints with setScriptIntStack, strings with setScriptString).
 *  - fairyringFilterFavorite / fairyringFilterDbrow (fairyrings_sort_update, script 8080): the
 *    patched script raises them after its own "does the code/name contain the filter" test
 *    (string_indexof_string 4121 != -1), with int stack [widget or dbrow, match]; a plugin's 1
 *    keeps the row. Here the test's result is rewritten after string_indexof_string: the
 *    favourite row's code widget is the result of `enum 1298` earlier in the loop, the travel log
 *    row is the dbrow whose db_getfield 364608 (its code widget) preceded the test.
 *    // unverified: on the device (the travel log filter needs the server's fairy ring interface).
 *  - spellbookSort (script 2611, the spellbook builder): the patched script raises it right
 *    after `gosub 2621` (sorting array 2 of 2611's frame) with int stack [enum = local 14, 2,
 *    count = local 22] and stores the answer in local 22; the plugin rewrites array 2 in place
 *    (getArray) and returns the visible count. Here it is raised when proc 2621 returns to 2611,
 *    on 2611's saved frame (Native.scriptArray/scriptIntLocal with caller = true).
 *    Verified on the device 2026-10-17 gen 24 (Spellbook hides Wind Strike).
 *  - musicTrackFilter (proc 9291, music_track_shown(dbrow, ..., unlocked)): the patched script starts
 *    with `iconst -1; iload 0; iload 4; runelite_callback`, and a 0 or 1 left in the -1 slot is
 *    returned at once (the track is hidden or shown). Here the callback is raised before each of
 *    9291's returns with int stack [-1, local 0, local 4], and an answer other than -1 replaces
 *    the returned value. // unverified: that mobile's music list runs proc 9291 with these locals.
 * Not emulated: skillTabMaxLevel (next to an iconst, which the opcode hook can't see).
 * // unverified: that the mobile skills tab runs scripts 395/396 (the desktop ids).
 */
final class ScriptCallbacks {
    private static final String TAG = "osrsrl-host";

    static final int SKILL_TAB_BUILDER = 395, SKILL_TAB_TOTAL = 396, FRIEND_UPDATE = 125;
    static final int FAIRY_RINGS_SORT = 8080, ENUM = 3408, DB_GETFIELD = 7502, STRING_INDEXOF_STRING = 4121;
    static final int STAT_BASE = 3306, IF_SETTEXT = 2112, CC_SETTEXT = 1112, CC_SETPOSITION = 1000;
    static final int MUSIC_TRACK_SHOWN = 9291, RETURN = 21;

    static final int ANY = 0xffff;  // reported script id of a wildcard watch
    static final String TOTAL_LEVEL = "Total level: ";

    private static final int[] WATCHES = {
        SKILL_TAB_BUILDER, STAT_BASE,
        SKILL_TAB_TOTAL, IF_SETTEXT,
        FRIEND_UPDATE, CC_SETTEXT,
        FRIEND_UPDATE, CC_SETPOSITION,
        // Mobile sets the skills tab total from another script (396 never runs): any settext of
        // "Total level: ..." raises skillTabTotalLevel.
        -1, IF_SETTEXT,
        -1, CC_SETTEXT,
        FAIRY_RINGS_SORT, ENUM,
        FAIRY_RINGS_SORT, DB_GETFIELD,
        FAIRY_RINGS_SORT, STRING_INDEXOF_STRING,
        MUSIC_TRACK_SHOWN, RETURN,
    };
    private static final String[] PREFIXES = {null, null, null, null, TOTAL_LEVEL, TOTAL_LEVEL, null, null, null, null};

    private static boolean installed;
    private static final java.util.ArrayDeque<Integer> skills = new java.util.ArrayDeque<>();

    private ScriptCallbacks() {}

    /** Watches the pairs once the event bus has ScriptCallbackEvent subscribers (game thread). */
    static void update(EventBus bus) {
        boolean want = bus != null && bus.hasSubscribers(ScriptCallbackEvent.class);
        if (want == installed) return;
        installed = want;
        Native.setOpWatches(want ? WATCHES : null, PREFIXES);
        Log.i(TAG, "opcode callbacks " + (want ? "on" : "off"));
    }

    /** From Host.onScript: phase 3 = before the instruction, 4 = after it. */
    static void onOp(EventBus bus, AndroidClient ac, int phase, int script, int opcode) {
        if (script == SKILL_TAB_BUILDER && opcode == STAT_BASE) skillTabBaseLevel(bus, ac, phase);
        else if (script == FAIRY_RINGS_SORT) fairyRings(bus, ac, phase, opcode);
        else if (phase != 3) return;
        else if (script == MUSIC_TRACK_SHOWN && opcode == RETURN) musicTrackFilter(bus, ac);
        else if (script == SKILL_TAB_TOTAL && opcode == IF_SETTEXT) raiseOnLive(bus, ac, "skillTabTotalLevel", 1, 1);
        else if (script == ANY && (opcode == IF_SETTEXT || opcode == CC_SETTEXT)) raiseOnLive(bus, ac, "skillTabTotalLevel", 1, 1);
        else if (script == FRIEND_UPDATE && opcode == CC_SETTEXT) raiseOnLive(bus, ac, "friendsChatSetText", 0, 1);
        else if (script == FRIEND_UPDATE && opcode == CC_SETPOSITION) raiseOnLive(bus, ac, "friendsChatSetPosition", 4, 0);
    }

    /**
     * getSearchingTagTab (BankmainBuild clientscript 276): RuneLite's copy starts with `iconst 1;
     * iconst 0; runelite_callback "getSearchingTagTab"; if_icmpne`, and when the plugin answers 1
     * (a Bank Tags tag tab is open) and varbit 4150 (the open bank tab) isn't 0 it sets 4150 to 0
     * (log "Server attempted to reset bank tab."). Raised here as 276 starts, with the same varbit write
     * (client-side, as the script's set_varbit). The game's 276 (LIVE cache, 22 instructions) is the
     * rest of RuneLite's copy.
     * Not emulated: bankBuildTab (proc 277). RuneLite's 277 is restructured, not just patched: when the
     * callback answers 1 it jumps to a block the game's 277 does not have (sums tab sizes 4171-4179 and
     * lays the tag tab out as one list without tab separators); with no such code in the game's
     * script there is no instruction to redirect to.
     */
    static final int BANKMAIN_BUILD = 276, BANK_TAB_VARBIT = 4150;

    /** From Host.onScript as a root script starts (phase 2), before ScriptPreFired. */
    static void onRootStart(EventBus bus, AndroidClient ac, int id) {
        if (id != BANKMAIN_BUILD || !installed) return;
        int answer = raise(bus, ac, "getSearchingTagTab", new int[] {1, 0}, new Object[0], 1);
        if (answer != 1) return;
        int tab = ac.getVarbitValue(BANK_TAB_VARBIT);
        if (tab == 0) return;
        ac.setVarbit(BANK_TAB_VARBIT, 0);
        Log.i(TAG, "getSearchingTagTab: bank tab " + tab + " -> 0 (tag tab open)");
    }

    static final int SPELLBOOK_BUILD = 2611, SPELLBOOK_SORT_PROC = 2621;
    private static int sortLogs;

    /** From Host.onScript on a ScriptPostFired (proc return), before the plugins see it. */
    static void onReturn(EventBus bus, AndroidClient ac, int id) {
        if (id != SPELLBOOK_SORT_PROC || !installed || Native.currentScript() != SPELLBOOK_BUILD) return;
        int en = Native.scriptIntLocal(true, 14, false, 0), count = Native.scriptIntLocal(true, 22, false, 0);
        if (en == Integer.MIN_VALUE || count == Integer.MIN_VALUE) {
            Log.w(TAG, "spellbookSort: 2611 locals unavailable");
            return;
        }
        ac.arrayFrameCaller = true;
        try {
            int answer = raise(bus, ac, "spellbookSort", new int[] {en, 2, count}, new Object[0], 2);
            ac.flushArrays();
            if (answer != count) Native.scriptIntLocal(true, 22, true, answer);
            if (sortLogs++ < 3) Log.i(TAG, "spellbookSort: enum " + en + " count " + count + " -> " + answer);
        } finally {
            ac.arrayFrameCaller = false;
        }
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

    // fairyrings_sort_update state: which row the next string_indexof_string tests.
    private static final int NONE = 0, FAVOURITE = 1, DBROW = 2;
    private static int fairyMode = NONE, fairyKey = -1, pendingEnum = -1, pendingRow = -1;

    private static void fairyRings(EventBus bus, AndroidClient ac, int phase, int opcode) {
        int n = ac.getIntStackSize();
        int[] st = ac.getIntStack();
        if (opcode == ENUM) {
            // enum(type, type, enum id, key): the favourites loop's code widget is enum 1298.
            if (phase == 3) pendingEnum = n >= 2 ? st[n - 2] : -1;
            else if (pendingEnum == 1298 && n >= 1) { fairyMode = FAVOURITE; fairyKey = st[n - 1]; }
            return;
        }
        if (opcode == DB_GETFIELD) {
            // db_getfield(row, column, tuple): column 364608 is the travel log row's code widget.
            if (phase == 3) {
                pendingRow = n >= 3 && st[n - 2] == 364608 ? st[n - 3] : -1;
                if (pendingRow >= 0) ac.wantDbField(pendingRow, 3, 0);  // the plugin reads column 3
            } else if (pendingRow >= 0) {
                boolean shown = n >= 1 && st[n - 1] != -1;
                fairyMode = shown ? DBROW : NONE;
                fairyKey = pendingRow;
            }
            return;
        }
        if (opcode != STRING_INDEXOF_STRING || phase != 4 || fairyMode == NONE || n < 1) return;
        String name = fairyMode == FAVOURITE ? "fairyringFilterFavorite" : "fairyringFilterDbrow";
        int key = fairyKey;
        fairyMode = NONE;
        int[] live = java.util.Arrays.copyOf(st, n);
        int found = live[n - 1];
        int match = found != -1 ? 1 : 0;
        int answer = raise(bus, ac, name, new int[] {key, match}, new Object[0], 1);
        if (answer == match) return;
        live[n - 1] = answer == 1 ? 0 : -1;  // the script keeps the row when the index isn't -1
        Native.setScriptIntStack(live, n);
    }

    private static int musicLogs;

    private static void musicTrackFilter(EventBus bus, AndroidClient ac) {
        int n = ac.getIntStackSize();
        int row = Native.scriptIntLocal(false, 0, false, 0), unlocked = Native.scriptIntLocal(false, 4, false, 0);
        if (n < 1 || row == Integer.MIN_VALUE || unlocked == Integer.MIN_VALUE) return;
        ac.wantDbField(row, 1, 0);  // the plugin's search reads the track's display name (column 1)
        int answer = raise(bus, ac, "musicTrackFilter", new int[] {-1, row, unlocked}, new Object[0], 0);
        if (musicLogs++ < 3) Log.i(TAG, "musicTrackFilter: row " + row + " unlocked " + unlocked + " -> " + answer);
        if (answer == -1) return;
        int[] live = java.util.Arrays.copyOf(ac.getIntStack(), n);
        live[n - 1] = answer;
        Native.setScriptIntStack(live, n);
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
