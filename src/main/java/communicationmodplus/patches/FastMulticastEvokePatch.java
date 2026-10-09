package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * Keeps Fast Multi-Cast's evoke from reading past the end of the action queue (bug #182).
 *
 * The third-party Fast Multi-Cast mod replaces each of Multi-Cast's
 * EvokeWithoutRemovingOrbActions with its own TimedEvokeWithoutRemovingOrbAction, whose
 * update() evokes the front orb and then runs the actions that evoke queued, in place, by
 * index: first {@code actions.get(j)} for j below the count the evoke added, then for j below
 * the count it and those actions added together. The indices assume the queue only grows while
 * they run. A Lightning evoke breaks that: run in place, the damage it queued can kill the last
 * monster, and DamageAction then calls clearPostCombatActions(), which removes every queued
 * action that is not damage, block, heal or card use -- the SFX and VFX the evoke queued among
 * them. The loop's next index is then past the end, the IndexOutOfBoundsException escapes into
 * CardCrawlGame.render, and the game shuts down (DEFECT, FZ5I3I21CA4FD: "Index: 3, Size: 3").
 *
 * The patch reroutes the action queue's {@code get} inside that update() through
 * {@link #getOrInert}: an index still inside the queue returns exactly what {@code get} returned,
 * so every play the original survived runs unchanged; an index past the end returns an action
 * that does nothing. Nothing in the loop changes the queue once it has run off the end, so every
 * later index is past it too -- the same as stopping the loop there. What the evoke queued and
 * the loop had not reached stays in the queue and resolves normally.
 *
 * Optional, and keyed on the mod's id: without Fast Multi-Cast there is no class to patch and
 * ModTheSpire skips this patch.
 */
@SpirePatch(cls="fastmulticast.TimedEvokeWithoutRemovingOrbAction", method="update",
        requiredModId="fast-multicast", optional=true)
public class FastMulticastEvokePatch {
    @SpireInstrumentPatch
    public static ExprEditor guardQueueIndex() {
        return new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getClassName().equals("java.util.ArrayList") && m.getMethodName().equals("get")) {
                    m.replace("{ $_ = communicationmodplus.patches.FastMulticastEvokePatch.getOrInert($0, $1); }");
                }
            }
        };
    }

    /** {@code list.get(i)}, except that an index past the end of the action queue yields an inert action. */
    public static Object getOrInert(ArrayList list, int i) {
        if (i >= list.size() && AbstractDungeon.actionManager != null && list == AbstractDungeon.actionManager.actions) {
            return new InertAction();
        }
        return list.get(i);
    }

    /** Stands in for a queue entry that is no longer there: never queued, and does nothing. */
    public static class InertAction extends AbstractGameAction {
        public InertAction() {
            this.isDone = true;
        }

        @Override
        public void update() {
            this.isDone = true;
        }
    }
}
