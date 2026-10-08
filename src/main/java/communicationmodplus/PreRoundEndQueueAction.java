/*
 * Queued action that captures the "pre_round_end_queue" snapshot.
 *
 * Put on top of the action queue by PlayerTurnStartPatch, just before the
 * player's start-of-turn relics and powers run. What they queue to the top
 * (Brimstone's Strength) goes on above it and resolves first; the round's
 * end that MonsterGroup.applyEndOfTurnPowers queued (debuff decay, Ritual)
 * is below it and resolves after. So this snapshot ends the start of turn's
 * queue-jumping effects and begins the round's queued end, which runs to
 * pre_player_turn.
 */
package communicationmodplus;

import com.megacrit.cardcrawl.actions.AbstractGameAction;
import communicationmodplus.GameStateListener;

public class PreRoundEndQueueAction
extends AbstractGameAction {
    public void update() {
        GameStateListener.captureIntermediateState("pre_round_end_queue", -1, null);
        this.isDone = true;
    }
}
