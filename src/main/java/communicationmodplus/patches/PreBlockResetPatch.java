/*
 * Capture the player's block synchronously RIGHT BEFORE the start-of-
 * player-turn block reset inside GameActionManager.getNextAction().
 *
 * The reset is two adjacent invokevirtual calls — loseBlock() (no Calipers)
 * or loseBlock(15) (with Calipers) — gated by hasRelic("Calipers"). Both
 * sit in the synchronous start-of-turn block of getNextAction, after
 * applyEndOfTurnPowers queues end-of-round triggers. Only one branch
 * executes per turn, so we patch both call sites and rely on JVM
 * branching to capture exactly the value that would have been reset.
 *
 * The value is stashed on a static field in GameStateListener; the next
 * pre_player_turn intermediate snapshot reads and consumes it, so the
 * snapshot's player.block reflects the post-attack pre-reset value.
 * Without this, the snapshot always sees 0 (or 15 for Calipers) and
 * the last enemy's bullet conflates attack absorption with the reset.
 *
 * Skipped automatically when the player has Barricade or Blur (loseBlock
 * is never invoked, so neither patched site fires, so the field stays
 * unset and the snapshot uses the live block — which is correct since
 * no reset happened).
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=GameActionManager.class, method="getNextAction")
public class PreBlockResetPatch {
    @SpireInsertPatch(locator=Locator.class)
    public static void Insert(GameActionManager _instance) {
        if (AbstractDungeon.player == null) {
            return;
        }
        GameStateListener.setPreBlockResetPlayerBlock(AbstractDungeon.player.currentBlock);
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            // Match BOTH loseBlock() and loseBlock(int) — only one fires
            // at runtime depending on Calipers, but both need patching.
            Matcher.MethodCallMatcher matcher = new Matcher.MethodCallMatcher(AbstractPlayer.class, "loseBlock");
            return LineFinder.findAllInOrder(ctMethodToPatch, matcher);
        }
    }
}
