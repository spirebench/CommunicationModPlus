/*
 * Capture "monster_block_reset" right after MonsterGroup.applyPreTurnLogic().
 *
 * That method runs at the start of the enemy turn (MonsterStartTurnAction):
 * each monster loses its Block (unless it has Barricade) and its
 * start-of-turn powers fire, Poison among them, which queues its HP loss.
 * Taken synchronously, this snapshot has every monster's Block reset and
 * none of the queued start-of-turn effects, so post_player_turn → here is
 * the reset (housekeeping) and here → the first pre_monster_turn is the
 * monsters' start of turn.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=MonsterGroup.class, method="applyPreTurnLogic")
public class MonsterBlockResetPatch {
    public static void Postfix(MonsterGroup _instance) {
        GameStateListener.captureIntermediateState("monster_block_reset", -1, null);
    }
}
