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
        int index = -1;
        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().monsters != null) {
            ArrayList monsters = AbstractDungeon.getCurrRoom().monsters.monsters;
            for (int i = 0; i < monsters.size(); i++) {
                if (monsters.get(i) == m) {
                    index = i;
                    break;
                }
            }
        }
        GameStateListener.captureIntermediateState("pre_monster_turn", index, m.name);
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            Matcher.MethodCallMatcher matcher = new Matcher.MethodCallMatcher(AbstractMonster.class, "takeTurn");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList(), matcher);
        }
    }
}
