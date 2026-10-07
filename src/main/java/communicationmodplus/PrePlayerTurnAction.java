/*
 * Queued action that captures a pre_player_turn intermediate snapshot.
 *
 * Added to the bottom of the action queue by PostEnemyPhasePatch
 * (PostFix on MonsterGroup.applyEndOfTurnPowers). By the time this
 * action runs, all end-of-round queued effects (Ritual Strength gains,
 * Vulnerable/Weak decay, etc.) have resolved — so the snapshot
 * captures the clean post-enemy-phase state.
 *
 * The player's Block reset and energy refill happen synchronously in the
 * same getNextAction() call (before the queue drains), so the snapshot
 * includes those changes; post_block_reset (PostBlockResetPatch) holds
 * the Block the player kept, before the queued end-of-round effects.
 */
package communicationmodplus;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import communicationmodplus.GameStateListener;

public class PrePlayerTurnAction
extends AbstractGameAction {
    public void update() {
        GameStateListener.captureIntermediateState("pre_player_turn", -1, null);
        this.isDone = true;
    }
}
