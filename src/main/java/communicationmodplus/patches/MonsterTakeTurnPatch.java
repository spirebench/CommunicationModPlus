/*
 * Capture a compact combat snapshot before each monster's takeTurn() runs.
 *
 * AbstractMonster.takeTurn() is declared abstract on the base class, so
 * ModTheSpire can't patch it directly. Instead we insert at the call
 * site inside GameActionManager.getNextAction(), which is the orchestrator
 * that drives the monster phase. The local variable `m` in that method
 * is the AbstractMonster whose turn is about to begin.
 *
 * The Insert fires once per monster turn, in order, at the moment just
 * before the game invokes that monster's takeTurn(). By capturing here
 * we get the state "after previous monster (or end-of-turn effects)
 * completely finished, before this monster starts" — exactly the
 * per-enemy boundary the agent wants.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=GameActionManager.class, method="getNextAction")
public class MonsterTakeTurnPatch {
    @SpireInsertPatch(locator=Locator.class, localvars={"m"})
    public static void Insert(GameActionManager _instance, AbstractMonster m) {
        // Disabled: the Insert fires at the wrong bytecode offset when other
        // mods (BaseMod, StSLib) inject code with takeTurn references into
        // getNextAction(). The pre_monster_turn snapshot is now captured via
        // BaseMod's PreMonsterTurnSubscriber in CommunicationMod instead.
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            // Match the takeTurn() → applyTurnPowers() sequence — this pair
            // only exists at the original monster-phase call site in
            // getNextAction(). Other mods (BaseMod, StSLib) inject code
            // containing takeTurn references elsewhere in the method; a
            // single-matcher Locator can't distinguish them. By requiring
            // takeTurn as an "expected" predecessor and targeting
            // applyTurnPowers, findInOrder anchors to the unique sequence.
            // The Insert fires before the returned target (applyTurnPowers),
            // but since our Insert is before takeTurn (the expected match),
            // we use the expected list approach: the returned offset is for
            // the first match in the expected list that precedes the target.
            //
            // Actually, findInOrder(ctMethod, expectedList, finalMatcher)
            // returns the line of the FINAL matcher. We want to insert
            // before takeTurn, not before applyTurnPowers. So we put
            // takeTurn as the sole expected matcher and use
            // findAllInOrder(ctMethod, expected, applyTurnPowers) to find
            // the takeTurn that is followed by applyTurnPowers.
            ArrayList expected = new ArrayList();
            expected.add(new Matcher.MethodCallMatcher(AbstractMonster.class, "takeTurn"));
            Matcher finalMatcher = new Matcher.MethodCallMatcher(AbstractMonster.class, "applyTurnPowers");
            return LineFinder.findInOrder(ctMethodToPatch, expected, finalMatcher);
        }
    }
}
