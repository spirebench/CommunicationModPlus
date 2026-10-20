/*
 * Snapshots before and after each hit on the player during the player's own
 * turn and its end of turn (bug #395, the player side of #46).
 *
 * AbstractPlayer.damage(info) takes the hit synchronously: Block absorbs what
 * it can and the rest comes off HP. A hit that lands inside a command (Beat
 * of Death's 1 damage after each card, a Burn at the end of the turn) used to
 * be folded into the command's net: "Played Defend. Block +4" for 5 Block
 * less the 1 that Beat of Death took, and a model concluded its cards were
 * weaker than their text (LM65M7BZF2FJK c255, c281, c298). With a snapshot on
 * each side of the hit the harness reports the segments as they stand.
 *
 * The snapshots join the monster hits' (PostDamageSnapshotPatch) in
 * GameStateListener's action snapshots, which are taken only while it is the
 * player's turn -- up to post_player_turn, so the end of turn's hits are
 * included and the monsters' hits on the player (already split per monster
 * by pre_monster_turn) are not.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.cards.DamageInfo;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import communicationmodplus.GameStateListener;

@SpirePatch(clz=AbstractPlayer.class, method="damage")
public class PlayerDamageSnapshotPatch {
    public static void Prefix(AbstractPlayer _instance, DamageInfo info) {
        GameStateListener.captureActionSnapshot("pre_player_damage", -1, null);
    }

    public static void Postfix(AbstractPlayer _instance, DamageInfo info) {
        GameStateListener.captureActionSnapshot("post_player_damage", -1, null);
    }
}
