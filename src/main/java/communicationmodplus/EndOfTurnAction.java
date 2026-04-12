package communicationmodplus;

import com.megacrit.cardcrawl.actions.AbstractGameAction;

public class EndOfTurnAction extends AbstractGameAction {
    public void update() {
        // Capture a snapshot AFTER all end-of-turn effects have resolved
        // (Metallicize, Combust, orb passives, hand discard/exhaust) but
        // BEFORE monsters start acting. This action was added to the
        // bottom of the queue by AbstractRoomEndTurnPatch, so by the
        // time it runs all the end-of-turn triggers queued by
        // AbstractRoom.endTurn() have already completed.
        GameStateListener.captureIntermediateState("post_player_turn", -1, null);
        GameStateListener.signalTurnEnd();
        this.isDone = true;
    }
}
