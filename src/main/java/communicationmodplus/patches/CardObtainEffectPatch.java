/*
 * Record a card obtain the moment it starts.
 *
 * A card obtained into the master deck (Writhing Mass's Implant, through
 * AddCardToDeckAction) travels as a ShowCardAndObtainEffect, or a
 * FastCardObtainEffect, and is only added to the deck when the effect ends,
 * 0.5-2 s later. The obtain is the action's effect, so it belongs in the
 * segment the action ran in; by the time the next snapshot is taken the card
 * may still be in flight, or may already have landed. Recording the start
 * here is what lets a snapshot list it either way
 * (GameStateListener.takeCardsInFlight, bug #207).
 *
 * The cards that go to the hand, draw pile or discard pile
 * (ShowCardAndAddTo*Effect) need nothing: those constructors add the card to
 * its pile at once, so the pile captures already see it.
 *
 * Only the four-argument ShowCardAndObtainEffect constructor is patched: the
 * three-argument one delegates to it, so patching both would record each
 * obtain twice. An obtain Omamori cancels is marked done in the constructor
 * and never lands, so it is not recorded.
 */
package communicationmodplus.patches;

import com.evacipated.cardcrawl.modthespire.lib.SpirePatch;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.vfx.FastCardObtainEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import communicationmodplus.GameStateListener;

public class CardObtainEffectPatch {
    @SpirePatch(clz=ShowCardAndObtainEffect.class, method=SpirePatch.CONSTRUCTOR, paramtypez={AbstractCard.class, float.class, float.class, boolean.class})
    public static class ShowCardAndObtain {
        public static void Postfix(ShowCardAndObtainEffect _instance, AbstractCard card, float x, float y, boolean converge) {
            if (!_instance.isDone) {
                GameStateListener.noteCardObtainStarted(card, "deck");
            }
        }
    }

    @SpirePatch(clz=FastCardObtainEffect.class, method=SpirePatch.CONSTRUCTOR)
    public static class FastCardObtain {
        public static void Postfix(FastCardObtainEffect _instance, AbstractCard card, float x, float y) {
            if (!_instance.isDone) {
                GameStateListener.noteCardObtainStarted(card, "deck");
            }
        }
    }
}
