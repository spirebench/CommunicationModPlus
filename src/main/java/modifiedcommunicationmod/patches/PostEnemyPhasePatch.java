/*
 * Queue a pre_player_turn snapshot action after all enemy end-of-round
 * triggers have been queued.
 *
 * MonsterGroup.applyEndOfTurnPowers() iterates every alive monster and
 * calls applyEndOfTurnTriggers() — which queues actions like Ritual's
 * ApplyPowerAction(Strength +3) and debuff decay (Vulnerable -1) to
 * the bottom of the action queue. This PostFix queues our
 * PrePlayerTurnAction AFTER all of those, so by the time it runs the
 * end-of-round effects have fully resolved and the snapshot captures
 * the clean post-enemy-phase state.
 *
 * Replaces PrePlayerTurnPatch (SpireInsertPatch at applyStartOfTurnRelics
 * in getNextAction), which fired too early — the queued end-of-round
 * actions hadn't drained yet, so Ritual Strength gains and Vulnerable
 * decay leaked into the start-of-turn section instead of being
 * attributed to the enemy phase.
 */
package modifiedcommunicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import modifiedcommunicationmod.PrePlayerTurnAction;

@SpirePatch(clz=MonsterGroup.class, method="applyEndOfTurnPowers")
public class PostEnemyPhasePatch {
    public static void Postfix(MonsterGroup _instance) {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction)new PrePlayerTurnAction());
    }
}
