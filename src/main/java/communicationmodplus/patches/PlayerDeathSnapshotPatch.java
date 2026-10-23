/*
 * A snapshot when the player's HP reaches 0, before a revive, and one when
 * the hit is over (bug #442).
 *
 * AbstractPlayer.damage(info) takes the HP to 0 and then, in the same call,
 * revives: Fairy in a Bottle heals and is destroyed, Lizard Tail heals and
 * is spent. No snapshot fell between the two, so a death and revive read as
 * a heal by the enemy that killed the player: "[0] Book of Stabbing — Player
 * HP +78 (16→94)" (LM8Y63DNS9N44 c226, c232).
 *
 * Inserted before the Fairy in a Bottle check, which runs once the HP has
 * been set to 0 and only without Mark of the Bloom, this takes
 * "player_death". In the player's turn and its end of turn it joins the hit
 * snapshots, between pre_player_damage and post_player_damage
 * (PlayerDamageSnapshotPatch). Otherwise (the monsters' turn, the round's
 * end, the start of turn) it joins the turn-boundary chain, followed by
 * "post_player_death" from the Postfix, once the revive is done.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.LineFinder;
import com.evacipated.cardcrawl.modthespire.lib.Matcher;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertLocator;
import com.evacipated.cardcrawl.modthespire.lib.SpireInsertPatch;
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.evacipated.cardcrawl.modthespire.patcher.PatchingException;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import java.util.ArrayList;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=AbstractPlayer.class, method="damage")
public class PlayerDeathSnapshotPatch {
    @SpireInsertPatch(locator=Locator.class)
    public static void Insert(AbstractPlayer _instance, DamageInfo info) {
        GameStateListener.capturePlayerDeath();
    }

    public static void Postfix(AbstractPlayer _instance, DamageInfo info) {
        GameStateListener.capturePostPlayerDeath();
    }

    private static class Locator
    extends SpireInsertLocator {
        private Locator() {
        }

        public int[] Locate(CtBehavior ctMethodToPatch) throws CannotCompileException, PatchingException {
            // hasPotion("FairyPotion"), the one hasPotion call in damage().
            Matcher finalMatcher = new Matcher.MethodCallMatcher(AbstractPlayer.class, "hasPotion");
            return LineFinder.findInOrder(ctMethodToPatch, new ArrayList(), finalMatcher);
        }
    }
}
