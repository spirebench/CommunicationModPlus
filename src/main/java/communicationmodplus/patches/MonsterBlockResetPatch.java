/*
 * Snapshots either side of MonsterGroup.applyPreTurnLogic().
 *
 * That method runs at the start of the enemy turn (MonsterStartTurnAction):
 * each monster loses its Block (unless it has Barricade) and its
 * start-of-turn powers fire, Poison among them, which queues its HP loss.
 *
 * Prefix: "pre_monster_block_reset". post_player_turn is taken by a queued
 * action, and what the player's end of turn queued behind it (Dead Branch's
 * card, for an Ethereal card exhausted at end of turn) resolves after it and
 * before this method runs (bug #96). Nothing else happens in between:
 * EndTurnAction only resets the player's per-turn bookkeeping. So
 * post_player_turn → here is the rest of the player's end of turn.
 *
 * Postfix: "monster_block_reset". Taken synchronously, it has every
 * monster's Block reset and none of the queued start-of-turn effects, so
 * pre_monster_block_reset → here is the reset (housekeeping) and here →
 * the first pre_monster_turn is the monsters' start of turn.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=MonsterGroup.class, method="applyPreTurnLogic")
public class MonsterBlockResetPatch {
    public static void Prefix(MonsterGroup _instance) {
        GameStateListener.captureIntermediateState("pre_monster_block_reset", -1, null);
    }

    public static void Postfix(MonsterGroup _instance) {
        GameStateListener.captureIntermediateState("monster_block_reset", -1, null);
    }
}
