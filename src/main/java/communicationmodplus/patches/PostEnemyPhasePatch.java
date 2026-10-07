/*
 * Snapshots around the end of the round.
 *
 * MonsterGroup.applyEndOfTurnPowers() runs once every monster has acted and
 * the action queue has drained. It runs every monster's end-of-turn
 * triggers (Plated Armor, Metallicize, Regenerate), then every creature's
 * atEndOfRound (Ritual, Vulnerable/Weak/Frail decay, Slow, Shackled), most
 * of which queue actions to the bottom of the queue.
 *
 * Prefix: "post_monsters", synchronously, before any of that. The last
 * monster's move ends here, so nothing of the round's end reads as its move
 * (bug #47 and the decay rules the harness used to need).
 *
 * Postfix: queue PrePlayerTurnAction after those triggers, so
 * "pre_player_turn" is taken once they have resolved.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import communicationmodplus.GameStateListener;
import communicationmodplus.PrePlayerTurnAction;

@SpirePatch(clz=MonsterGroup.class, method="applyEndOfTurnPowers")
public class PostEnemyPhasePatch {
    public static void Prefix(MonsterGroup _instance) {
        GameStateListener.captureIntermediateState("post_monsters", -1, null);
    }

    public static void Postfix(MonsterGroup _instance) {
        AbstractDungeon.actionManager.addToBottom((AbstractGameAction)new PrePlayerTurnAction());
    }
}
