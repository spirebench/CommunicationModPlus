/*
 * Mark the start of the player's turn in GameActionManager.getNextAction().
 *
 * Once every monster has acted, one getNextAction() call runs:
 *
 *   monsters.applyEndOfTurnPowers()   the round's end (post_monsters is its
 *                                     Prefix); most of it is queued
 *   player.applyStartOfTurnRelics()   the player's start of turn; relic
 *   ...PreDrawCards/Cards/Powers/Orbs counters move here in place (Happy
 *                                     Flower, Incense Burner, Letter
 *                                     Opener), their effects are queued
 *   player.loseBlock()                (post_block_reset follows it)
 *   addToBottom(DrawCardAction)
 *
 * so the start of turn's in-place part runs before the round's queued end
 * resolves. With no snapshot here the relic counters read as part of the
 * round's end (bug #95).
 *
 * Inserted before applyStartOfTurnRelics, this takes "player_turn_start":
 * post_monsters → here is the round's end done in place, here →
 * post_block_reset the start of turn done in place, and the Block reset.
 *
 * It also puts PreRoundEndQueueAction on top of the queue. A start-of-turn
 * effect queued to the top (Brimstone's Strength) goes on above it, and the
 * round's queued end (debuff decay, Ritual) is below it, so its
 * "pre_round_end_queue" snapshot separates the two: post_block_reset → it
 * is the start of turn, it → pre_player_turn the round's queued end.
 *
 * A turn whose monster turn is skipped (Vault's extra turn) skips
 * applyEndOfTurnPowers and with it post_monsters and pre_player_turn, and
 * has no round's end to queue, so PreRoundEndQueueAction is not queued.
 * "player_turn_start" is still taken: it is the only line between the end
 * of the turn Vault ended and the extra turn's start, and without it the
 * extra turn's Block reset, relics and draw read as the end of the turn
 * (LM8UWFHYQXUN4 c155, LM8NYCQXJU7HA c87, bug #443).
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import communicationmodplus.GameStateListener;
import communicationmodplus.PreRoundEndQueueAction;

@SpirePatch(clz=GameActionManager.class, method="getNextAction")
public class PlayerTurnStartPatch {
    @SpireInsertPatch(locator=Locator.class)
    public static void Insert(GameActionManager _instance) {
        if (AbstractDungeon.getCurrRoom() == null) {
            return;
        }
        GameStateListener.captureIntermediateState("player_turn_start", -1, null);
        if (AbstractDungeon.getCurrRoom().skipMonsterTurn) {
            return;
        }
        _instance.addToTop((AbstractGameAction)new PreRoundEndQueueAction());
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher finalMatcher = new Matcher.MethodCallMatcher(AbstractPlayer.class, "applyStartOfTurnRelics");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList(), finalMatcher);
        }
    }
}
