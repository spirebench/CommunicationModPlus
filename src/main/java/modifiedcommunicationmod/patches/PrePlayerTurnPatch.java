/*
 * Capture a compact combat snapshot at the moment the enemy phase ends
 * and the player phase is about to begin — after the last monster's
 * takeTurn() has fully resolved, but before any start-of-player-turn
 * effect fires (block reset, energy refill, draw, start-of-turn relics
 * like Happy Flower / Brimstone / Mercury Hourglass, etc.).
 *
 * Inserted at the call site of AbstractPlayer.applyStartOfTurnRelics()
 * inside GameActionManager.getNextAction() — that call is the first
 * start-of-player-turn trigger, so capturing immediately before it
 * gives us the "enemy phase fully done" boundary.
 *
 * Together with post_player_turn (from EndOfTurnAction) and the
 * per-enemy pre_monster_turn snapshots, this lets the Python
 * consumer split the action log as:
 *
 *   post_player_turn → pre_monster_turn[0]  — end-of-player-turn effects
 *   pre_monster_turn[i] → pre_monster_turn[i+1]  — monster i's turn
 *   pre_monster_turn[N-1] → pre_player_turn  — last monster's turn (clean)
 *   pre_player_turn → game_snap  — start-of-player-turn effects
 */
package modifiedcommunicationmod.patches;

import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import modifiedcommunicationmod.GameStateListener;

@SpirePatch(clz=GameActionManager.class, method="getNextAction")
public class PrePlayerTurnPatch {
    @SpireInsertPatch(locator=Locator.class)
    public static void Insert(GameActionManager _instance) {
        GameStateListener.captureIntermediateState("pre_player_turn", -1, null);
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher.MethodCallMatcher matcher = new Matcher.MethodCallMatcher(AbstractPlayer.class, "applyStartOfTurnRelics");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList(), matcher);
        }
    }
}
