/*
 * Snapshots before and after each hit on a monster, before its reaction to the hit.
 *
 * AbstractMonster.damage(info) takes the hit synchronously: Block absorbs
 * what it can (decrementBlock) and the rest comes off HP. Its powers' and
 * relics' reactions to being hit are queued as actions, not applied inside
 * it: Malleable's GainBlockAction (addToTop), the Guardian's mode-shift
 * Block, Curl Up. So the state at the end of damage() is the hit alone.
 *
 * A snapshot before each hit too ("pre_damage"): Malleable's Block from
 * one hit, absorbed by the next, is seen only between them.
 *
 * Without them, a play's result was one net change per monster from the
 * state before the command to the state after it, and Block that absorbed
 * the hit and then grew back read as a small gain: "HP -5 (127→122); Block
 * +1 (3→4)" for 3 absorbed and 4 from Malleable (bug #46). The snapshots
 * ride on the next ready state as "action_snapshots"; the harness splits a
 * result at them only where the net hides something.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=AbstractMonster.class, method="damage")
public class PostDamageSnapshotPatch {
    public static void Prefix(AbstractMonster _instance, DamageInfo info) {
        capture("pre_damage", _instance);
    }

    public static void Postfix(AbstractMonster _instance, DamageInfo info) {
        capture("post_damage", _instance);
    }

    private static void capture(String label, AbstractMonster monster) {
        if (AbstractDungeon.getCurrRoom() == null || AbstractDungeon.getCurrRoom().monsters == null) {
            return;
        }
        int index = AbstractDungeon.getCurrRoom().monsters.monsters.indexOf(monster);
        GameStateListener.captureActionSnapshot(label, index, monster.name);
    }
}
