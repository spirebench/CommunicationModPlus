/*
 * Capture a "post_block_reset" snapshot synchronously right after the
 * player's start-of-turn Block reset in GameActionManager.getNextAction().
 *
 * The reset (loseBlock(), or loseBlock(15) with Calipers, skipped under
 * Barricade and Blur) runs synchronously in the same call that queues the
 * end-of-round triggers, so no queued snapshot can sit between it and the
 * queued effects. This one is inserted before the start-of-turn
 * DrawCardAction is queued, which is after the reset in every branch: its
 * player Block is what the player kept, whatever kept it. The start of
 * turn's in-place part (relic counters) runs before the reset, after
 * player_turn_start (PlayerTurnStartPatch). The end-of-round effects queued
 * just before have not resolved yet; they land between pre_round_end_queue
 * and pre_player_turn.
 *
 * Replaces PreBlockResetPatch, which overwrote pre_player_turn's Block with
 * the pre-reset value and never fired under Barricade or Blur (bug #44).
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.common.DrawCardAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=GameActionManager.class, method="getNextAction")
public class PostBlockResetPatch {
    @SpireInsertPatch(locator=Locator.class)
    public static void Insert(GameActionManager _instance) {
        GameStateListener.captureIntermediateState("post_block_reset", -1, null);
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            // The start-of-turn DrawCardAction that follows the Block reset.
            ArrayList expected = new ArrayList();
            expected.add(new Matcher.MethodCallMatcher(AbstractPlayer.class, "loseBlock"));
            Matcher finalMatcher = new Matcher.NewExprMatcher(DrawCardAction.class);
            return LineFinder.findInOrder(ctMethodToPatch, expected, finalMatcher);
        }
    }
}
