package communicationmodplus;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.neow.NeowRoom;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.EventRoom;
import com.megacrit.cardcrawl.rooms.VictoryRoom;
import com.megacrit.cardcrawl.vfx.FastCardObtainEffect;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;

import java.util.ArrayList;
import java.util.HashMap;

public class GameStateListener {
    private static AbstractDungeon.CurrentScreen previousScreen = null;
    private static boolean previousScreenUp = false;
    private static AbstractRoom.RoomPhase previousPhase = null;
    private static boolean previousGridSelectConfirmUp = false;
    private static int previousGold = 99;
    // The cards on the card reward or grid screen when a state was last
    // published: an open of either assigns a new list or group, so a
    // replacement shows as a new object (see screenContentChanged).
    private static Object previousScreenContent = null;
    private static boolean externalChange = false;
    private static boolean myTurn = false;
    private static boolean blocked = false;
    private static boolean waitingForCommand = false;
    private static boolean hasPresentedOutOfGameState = false;
    private static boolean waitOneUpdate = false;
    private static int timeout = 0;
    // Snapshots captured since the last ready state.
    private static ArrayList<HashMap<String, Object>> intermediateStates = new ArrayList<HashMap<String, Object>>();
    // The batch handed out with the last ready state, sent again with every
    // ready state until the next command that acts on the game (bug #72).
    // The harness reads only the newest output file, so a ready state the
    // mod republished straight away (answering a `state` poll that crossed
    // the turn start) used to replace the one that carried the batch, and
    // that turn arrived with no snapshots and no note.
    private static ArrayList<HashMap<String, Object>> deliveredStates = new ArrayList<HashMap<String, Object>>();
    // Captures skipped since the last delivery, with the reason, so a turn
    // that arrives with no intermediate snapshots says why (bug #48).
    private static ArrayList<String> skippedCaptures = new ArrayList<String>();
    // The note for a turn that arrived without snapshots; like the batch,
    // repeated on every ready state until the next acting command.
    private static HashMap<String, Object> missingNote = null;
    // Card obtains started since the last snapshot, by card uuid
    // (CardObtainEffectPatch): reported by the next snapshot even if the card
    // has landed by then, so the obtain is placed in the segment it began in.
    private static java.util.LinkedHashMap<String, HashMap<String, Object>> startedObtains = new java.util.LinkedHashMap<String, HashMap<String, Object>>();
    // GameActionManager.turn at the last delivery of intermediate snapshots.
    private static int lastDeliveredTurn = -1;
    // The last turn accounted for: delivered, or noted as missing.
    private static int lastAccountedTurn = -1;
    // The fight (its MonsterGroup) whose post_player_turn the buffer holds.
    // A ready state on turn 1 clears the buffer as the fight's first, unless
    // a turn of this same fight has ended: a fight lost on turn 1 ends in a
    // GAME_OVER that keeps the turn number, and its chain (the end of turn,
    // the enemy turn, player_death) was cleared and never sent
    // (LM95R05KPIIVD c184).
    private static Object turnEndedInFight = null;

    /**
     * Used to indicate that something (in game logic, not external command) has been done that will change the game state,
     * and hasStateChanged() should indicate a state change when the state next becomes stable.
     */
    public static void registerStateChange() {
        externalChange = true;
        waitingForCommand = false;
    }

    /**
     * Used to tell hasStateChanged() to indicate a state change after a specified number of frames.
     * @param newTimeout The number of frames to wait
     */
    public static void setTimeout(int newTimeout) {
        timeout = newTimeout;
    }

    /**
     * Used to indicate that an external command has been executed
     */
    public static void registerCommandExecution() {
        waitingForCommand = false;
        // The agent has acted on a state that carried them.
        deliveredStates.clear();
        missingNote = null;
    }

    /**
     * Prevents hasStateChanged() from indicating a state change until resumeStateUpdate() is called.
     */
    public static void blockStateUpdate() {
        blocked = true;
    }

    /**
     * Removes the block instantiated by blockStateChanged()
     */
    public static void resumeStateUpdate() {
        blocked = false;
    }

    /**
     * Used by a patch in the game to signal the start of your turn. We do not care about state changes
     * when it is not our turn in combat, as we cannot take action until then.
     */
    public static void signalTurnStart() {
        myTurn = true;
    }

    /**
     * Used by patches in the game to signal the end of your turn (or the end of combat).
     */
    public static void signalTurnEnd() {
        myTurn = false;
    }

    /**
     * Resets all state detection variables for the start of a new run.
     */
    /**
     * Capture a compact combat-state snapshot and append it to the
     * intermediate buffer. Called from EndOfTurnAction and
     * MonsterTakeTurnPatch so the agent can see per-enemy granularity
     * for what happened during the enemy turn. The buffer is shipped
     * with the next regular game-state message (on turn start).
     */
    public static void captureIntermediateState(String label, int monsterIndex, String monsterName) {
        if ("post_player_turn".equals(label)) {
            pastPlayerTurn = true;
        }
        if (!CommandExecutor.isInDungeon()) {
            skippedCaptures.add(label + ": not in dungeon");
            return;
        }
        if (AbstractDungeon.getCurrRoom() == null || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
            skippedCaptures.add(label + ": room phase " + (AbstractDungeon.getCurrRoom() == null ? "null" : String.valueOf(AbstractDungeon.getCurrRoom().phase)));
            return;
        }
        HashMap<String, Object> snapshot = GameStateConverter.captureCompactCombatSnapshot(label, monsterIndex, monsterName);
        if (snapshot != null) {
            intermediateStates.add(snapshot);
            if ("post_player_turn".equals(label)) {
                turnEndedInFight = AbstractDungeon.getCurrRoom().monsters;
            }
        } else {
            skippedCaptures.add(label + ": snapshot null");
        }
    }

    // Snapshots taken after each hit on a monster during the player's own
    // command, before its reaction (PostDamageSnapshotPatch, bug #46). They
    // ride on every ready state until the next command clears them.
    private static final ArrayList<HashMap<String, Object>> actionSnapshots = new ArrayList<HashMap<String, Object>>();
    private static final int MAX_ACTION_SNAPSHOTS = 64;
    // Set once post_player_turn is taken, until the next command clears the
    // snapshots. A hit snapshot taken after it is from the enemy turn or the
    // next turn's start (Fire Breathing on a draw, Mayhem's card), which
    // myTurn lets through once the next turn has begun: it is tagged
    // "after_player_turn", and the harness leaves it out of the end of turn,
    // where its later HP read as a hit and then a heal that never happened
    // (LM7HH0TU4ZEF8 c18 "HP -15 (380→365), then HP +15 (365→380)").
    private static boolean pastPlayerTurn = false;

    public static void captureActionSnapshot(String label, int monsterIndex, String monsterName) {
        if (!myTurn || !CommandExecutor.isInDungeon() || AbstractDungeon.getCurrRoom() == null
            || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT
            || actionSnapshots.size() >= MAX_ACTION_SNAPSHOTS) {
            return;
        }
        HashMap<String, Object> snapshot = GameStateConverter.captureHitSnapshot(label, monsterIndex, monsterName);
        if (snapshot != null) {
            snapshot.put("after_player_turn", pastPlayerTurn);
            actionSnapshots.add(snapshot);
        }
    }

    // Set by capturePlayerDeath when it took the death into the chain, so the
    // end of that damage() call takes the revive there too (bug #442).
    private static boolean deathInChain = false;

    /**
     * The player's HP has reached 0 inside AbstractPlayer.damage, before a
     * revive (PlayerDeathSnapshotPatch). In the player's turn it is a hit
     * snapshot, between pre_player_damage and post_player_damage; otherwise
     * a chain snapshot, which capturePostPlayerDeath follows once the revive
     * is done.
     */
    public static void capturePlayerDeath() {
        if (myTurn) {
            captureActionSnapshot("player_death", -1, null);
        } else {
            captureIntermediateState("player_death", -1, null);
            deathInChain = true;
        }
    }

    public static void capturePostPlayerDeath() {
        if (deathInChain) {
            deathInChain = false;
            captureIntermediateState("post_player_death", -1, null);
        }
    }

    public static void clearActionSnapshots() {
        actionSnapshots.clear();
        pastPlayerTurn = false;
    }

    public static ArrayList<HashMap<String, Object>> getActionSnapshots() {
        return new ArrayList<HashMap<String, Object>>(actionSnapshots);
    }

    public static void noteCardObtainStarted(AbstractCard card, String destination) {
        if (card == null || AbstractDungeon.getCurrRoom() == null || AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) {
            return;
        }
        startedObtains.put(card.uuid.toString(), cardInFlight(card, destination));
    }

    private static HashMap<String, Object> cardInFlight(AbstractCard card, String destination) {
        HashMap<String, Object> entry = new HashMap<String, Object>();
        entry.put("name", card.name);
        entry.put("uuid", card.uuid.toString());
        entry.put("destination", destination);
        return entry;
    }

    /**
     * The cards on their way to a pile for a snapshot: every obtain started
     * since the previous snapshot, landed or not, and every obtain still
     * running. Each is {name, uuid, destination}; the destination is "deck",
     * the one move the game defers (see CardObtainEffectPatch). A card still
     * in flight is listed by every snapshot until it lands, so a reader takes
     * the first snapshot that lists a uuid as the one it was obtained in.
     */
    public static ArrayList<HashMap<String, Object>> takeCardsInFlight() {
        java.util.LinkedHashMap<String, HashMap<String, Object>> out = new java.util.LinkedHashMap<String, HashMap<String, Object>>(startedObtains);
        startedObtains.clear();
        for (java.util.ArrayList<?> list : new java.util.ArrayList<?>[]{AbstractDungeon.effectList, AbstractDungeon.effectsQueue, AbstractDungeon.topLevelEffects, AbstractDungeon.topLevelEffectsQueue}) {
            if (list == null) {
                continue;
            }
            for (Object e : list) {
                AbstractCard card = null;
                if (e instanceof ShowCardAndObtainEffect && !((ShowCardAndObtainEffect)e).isDone) {
                    card = (AbstractCard)ReflectionHacks.getPrivate(e, ShowCardAndObtainEffect.class, "card");
                } else if (e instanceof FastCardObtainEffect && !((FastCardObtainEffect)e).isDone) {
                    card = (AbstractCard)ReflectionHacks.getPrivate(e, FastCardObtainEffect.class, "card");
                }
                if (card != null && !out.containsKey(card.uuid.toString())) {
                    out.put(card.uuid.toString(), cardInFlight(card, "deck"));
                }
            }
        }
        return new ArrayList<HashMap<String, Object>>(out.values());
    }

    /**
     * Settle what a ready state carries about the enemy phase before it.
     *
     * New snapshots join the batch being delivered. A turn past the first
     * that has none, and has not been accounted for, gets a note saying
     * why (bug #48). Both are repeated on every ready state until the next
     * acting command, so a republished state carries them too (bug #72).
     */
    public static void settleIntermediates(int turn) {
        // A ready state carries the deck itself; an obtain started before it
        // is not the next enemy phase's.
        startedObtains.clear();
        if (turn <= 1 && (turnEndedInFight == null || turnEndedInFight != currentFight())) {
            // The first turn of a combat: nothing captured before it belongs
            // to it, and an earlier combat's turn numbers say nothing here.
            intermediateStates.clear();
            deliveredStates.clear();
            skippedCaptures.clear();
            missingNote = null;
            lastDeliveredTurn = -1;
            lastAccountedTurn = turn;
            return;
        }
        if (turn < lastAccountedTurn) {
            // A new combat whose first turn was never published ready.
            deliveredStates.clear();
            missingNote = null;
            lastDeliveredTurn = -1;
            lastAccountedTurn = -1;
        }
        if (!intermediateStates.isEmpty()) {
            deliveredStates.addAll(intermediateStates);
            intermediateStates.clear();
            skippedCaptures.clear();
            missingNote = null;
            lastDeliveredTurn = turn;
            lastAccountedTurn = turn;
        } else if (deliveredStates.isEmpty() && missingNote == null && turn != lastAccountedTurn) {
            missingNote = new HashMap<String, Object>();
            missingNote.put("turn", turn);
            missingNote.put("last_delivered_turn", lastDeliveredTurn);
            missingNote.put("skipped", new ArrayList<String>(skippedCaptures));
            CommunicationMod.logIntermediatesMissing(turn, lastDeliveredTurn, skippedCaptures);
            skippedCaptures.clear();
            lastAccountedTurn = turn;
        }
    }

    private static Object currentFight() {
        return AbstractDungeon.getCurrRoom() == null ? null : AbstractDungeon.getCurrRoom().monsters;
    }

    /**
     * A fight begins (BaseMod's battle-start hook, before its first draw):
     * nothing captured before it belongs to it.
     */
    public static void signalBattleStart() {
        intermediateStates.clear();
        deliveredStates.clear();
        skippedCaptures.clear();
        missingNote = null;
        lastDeliveredTurn = -1;
        lastAccountedTurn = -1;
        turnEndedInFight = null;
        deathInChain = false;
    }

    /** The snapshots captured since the last ready state, not yet delivered. */
    public static ArrayList<HashMap<String, Object>> getPendingIntermediates() {
        return new ArrayList<HashMap<String, Object>>(intermediateStates);
    }

    public static ArrayList<HashMap<String, Object>> getDeliveredIntermediates() {
        return new ArrayList<HashMap<String, Object>>(deliveredStates);
    }

    public static HashMap<String, Object> getMissingIntermediatesNote() {
        return missingNote;
    }

    public static void resetStateVariables() {
        previousScreen = null;
        previousScreenUp = false;
        previousPhase = null;
        previousGridSelectConfirmUp = false;
        previousGold = 99;
        previousScreenContent = null;
        externalChange = false;
        myTurn = false;
        blocked = false;
        waitingForCommand = false;
        waitOneUpdate = false;
        intermediateStates.clear();
        deliveredStates.clear();
        skippedCaptures.clear();
        missingNote = null;
        lastDeliveredTurn = -1;
        lastAccountedTurn = -1;
        turnEndedInFight = null;
        startedObtains.clear();
        actionSnapshots.clear();
        pastPlayerTurn = false;
        deathInChain = false;
    }

    /**
     * Detects whether the game state is stable and we are ready to receive a command from the user.
     *
     * @return whether the state is stable
     */
    private static boolean hasDungeonStateChanged() {
        if (blocked) {
            return false;
        }
        hasPresentedOutOfGameState = false;
        AbstractDungeon.CurrentScreen newScreen = AbstractDungeon.screen;
        boolean newScreenUp = AbstractDungeon.isScreenUp;
        AbstractRoom.RoomPhase newPhase = AbstractDungeon.getCurrRoom().phase;
        boolean inCombat = (newPhase == AbstractRoom.RoomPhase.COMBAT);
        // Lots of stuff can happen while the dungeon is fading out, but nothing that requires input from the user.
        if (AbstractDungeon.isFadingOut || AbstractDungeon.isFadingIn) {
            return false;
        }
        // This check happens before the rest since dying can happen in combat and messes with the other cases.
        if (newScreen == AbstractDungeon.CurrentScreen.DEATH && newScreen != previousScreen) {
            return true;
        }
        // These screens have no interaction available.
        if (newScreen == AbstractDungeon.CurrentScreen.DOOR_UNLOCK || newScreen == AbstractDungeon.CurrentScreen.NO_INTERACT) {
            return false;
        }
        // We are not ready to receive commands when it is not our turn, except for some pesky screens
        if (inCombat && (!myTurn || AbstractDungeon.getMonsters().areMonstersBasicallyDead())) {
            if (!newScreenUp) {
                return false;
            }
        }
        // Out of combat, a card obtained through a ShowCardAnd*/FastCardObtain
        // effect (an event's card, Cursed Key's curse, Neow's transform,
        // Astrolabe, Dolly's Mirror) joins the deck only when the effect's
        // animation ends. Published before that, the state shows the deck a
        // card short and the harness reported the card one action late or
        // never (bugs #63, #64, #98, #99, #124, #136). Hold every path until
        // no such effect is pending. Ported from 12e4b50 (enh_create-video),
        // which the 2026-10-04 merge b5ab7ea dropped.
        if (!inCombat && hasPendingObtainEffect()) {
            return false;
        }
        // In combat only the deck-obtain effect defers its change (Writhing
        // Mass's Implant adds to the master deck when the effect ends); the
        // hand/draw/discard ShowCardAnd* effects act synchronously and are
        // not held, which would slow every card that creates one.
        if (inCombat && hasPendingDeckObtainEffect()) {
            return false;
        }
        // A Smoke Bomb escape keeps the room in COMBAT, the hand dealt and
        // the enemies alive until the player's escape animation ends and the
        // room completes. Published before that, the state reads as a fresh
        // turn the player cannot take: a play was refused with "Possible
        // commands: [potion, proceed, ...]" and the next state was an empty
        // COMBAT_REWARD (#159, GFT3N9GD9RR9D F24, GFT48BEKAKF9M F14). Hold
        // until the escape has finished.
        if (inCombat && (AbstractDungeon.player.isEscaping || AbstractDungeon.getCurrRoom().smoked)) {
            return false;
        }
        // In-combat HAND_SELECT picker stability gate (49dac73, May; deferred
        // by the 2026-10-04 merge b5ab7ea and not ported until now). A hand
        // picker (Warcry, Headbutt, Gambling Chip, Discovery) must not be
        // reported in its OUT phase -- just confirmed, its action not yet
        // drained into the hand -- or the next play_card resolves against a
        // hand that is about to change and lands on the wrong card or is
        // silently dropped (LC0001 F44, Warcry+ put-on-deck).
        if (inCombat) {
            com.megacrit.cardcrawl.screens.select.HandCardSelectScreen handSel = AbstractDungeon.handCardSelectScreen;
            // Only when the picker closed back onto the combat itself: if
            // it closed into another screen awaiting a pick (a potion's or
            // card's card choice, a grid), the pending actions cannot drain
            // until that pick is made, so holding here never lets go
            // (FZ3SVFXGJE4PA: a CARD_REWARD held not-ready for 600 s).
            if (previousScreen == AbstractDungeon.CurrentScreen.HAND_SELECT
                && newScreen != AbstractDungeon.CurrentScreen.HAND_SELECT
                && !newScreenUp) {
                if (!AbstractDungeon.actionManager.phase.equals((Object)GameActionManager.Phase.WAITING_ON_USER)
                    || !AbstractDungeon.actionManager.actions.isEmpty()
                    || !AbstractDungeon.actionManager.cardQueue.isEmpty()
                    || !AbstractDungeon.actionManager.preTurnActions.isEmpty()) {
                    return false;
                }
            }
            if (newScreen == AbstractDungeon.CurrentScreen.HAND_SELECT
                && handSel != null && handSel.wereCardsRetrieved) {
                return false;
            }
        }
        // In event rooms, we need to wait for the event wait timer to reach 0 before we can accurately assess its state.
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();
        if ((currentRoom instanceof EventRoom
                || currentRoom instanceof NeowRoom
                || (currentRoom instanceof VictoryRoom && ((VictoryRoom) currentRoom).eType == VictoryRoom.EventType.HEART))
                && AbstractDungeon.getCurrRoom().event != null
                && AbstractDungeon.getCurrRoom().event.waitTimer != 0.0F) {
            return false;
        }
        // The state has always changed in some way when one of these variables is different.
        // However, the state may not be finished changing, so we need to do some additional checks.
        if (newScreen != previousScreen || newScreenUp != previousScreenUp || newPhase != previousPhase
            || screenContentChanged(newScreen, newScreenUp)) {
            if (inCombat) {
                // In combat, newScreenUp being true indicates an action that requires our immediate attention.
                if (newScreenUp) {
                    return true;
                }
                // In combat, if no screen is up, we should wait for all actions to complete before indicating a state change.
                else if (AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER)
                        && AbstractDungeon.actionManager.cardQueue.isEmpty()
                        && AbstractDungeon.actionManager.actions.isEmpty()) {
                    return true;
                }

            // Out of combat, we want to wait one update cycle, as some screen transitions trigger further updates.
            } else {
                waitOneUpdate = true;
                previousScreenUp = newScreenUp;
                previousScreen = newScreen;
                previousPhase = newPhase;
                previousScreenContent = currentScreenContent();
                return false;
            }
        } else if (waitOneUpdate) {
            if (hasPendingObtainEffect()) {
                return false;
            }
            waitOneUpdate = false;
            return true;
        }
        // We are assuming that commands are only being submitted through our interface. Some actions that require
        // our attention, like retaining a card, occur after the end turn is queued, but the previous cases
        // cover those actions. We would like to avoid registering other state changes after the end turn
        // command but before the game actually ends your turn.
        // Not while a screen is up: the turn cannot end until its pick is made.
        // The Time Eater's twelfth card ends the turn early (endTurnQueued) with
        // a Scry grid from the same play still open; the grid was published as
        // it opened, but each pick on it changes no screen and was held here,
        // so the game was never ready again (FZ800CNYVBIHF, bug #286).
        if (inCombat && AbstractDungeon.player.endTurnQueued && !newScreenUp) {
            return false;
        }
        // If some other code registered a state change through registerStateChange(), or if we notice a state
        // change through the gold amount changing, we still need to wait until all actions are finished
        // resolving to claim a stable state and ask for a new command.
        if ((externalChange || previousGold != AbstractDungeon.player.gold)
                && AbstractDungeon.actionManager.phase.equals(GameActionManager.Phase.WAITING_ON_USER)
                && AbstractDungeon.actionManager.preTurnActions.isEmpty()
                && AbstractDungeon.actionManager.actions.isEmpty()
                && AbstractDungeon.actionManager.cardQueue.isEmpty()) {
            return true;
        }
        // In a grid select screen, if a confirm screen comes up or goes away, it doesn't change any other state.
        if (newScreen == AbstractDungeon.CurrentScreen.GRID) {
            boolean newGridSelectConfirmUp = AbstractDungeon.gridSelectScreen.confirmScreenUp;
            if (previousScreen == AbstractDungeon.CurrentScreen.GRID && newGridSelectConfirmUp != previousGridSelectConfirmUp) {
                return true;
            }
        }
        // Sometimes, we need to register an external change in combat while an action is resolving which brings
        // the screen up. Because the screen did not change, this is not covered by other cases.
        if (externalChange && inCombat && newScreenUp) {
            return true;
        }
        if (timeout > 0) {
            timeout -= 1;
            if(timeout == 0) {
                return true;
            }
        }
        return false;
    }

    /*
     * A card reward or grid screen that is not the one last published.
     * Screen type, screen-up and room phase alone cannot tell two of them
     * apart when the one in between closed while nothing was published:
     * Nilry's Codex's pick at the end of a turn, then -- Mayhem playing Time
     * Eater's twelfth card at the start of the next, which ends that turn
     * early -- a second Codex pick (FZ260FLNIM6RQ, bug #222); two Scry grids
     * in a row (FZ92IMVB9MCA0, bug #232). The second matched the last
     * published state field for field, was never reported ready, and the run
     * was given up as wedged. Every open of the card reward screen assigns it
     * a new list; ScryAction opens the grid on a new group. A grid opened on a
     * pile the game reuses (the master deck) keeps its group and is not told
     * apart, as before.
     */
    private static boolean screenContentChanged(AbstractDungeon.CurrentScreen newScreen, boolean newScreenUp) {
        if (!newScreenUp) {
            return false;
        }
        if (newScreen != AbstractDungeon.CurrentScreen.CARD_REWARD && newScreen != AbstractDungeon.CurrentScreen.GRID) {
            return false;
        }
        return currentScreenContent() != previousScreenContent;
    }

    private static Object currentScreenContent() {
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.CARD_REWARD) {
            return AbstractDungeon.cardRewardScreen == null ? null : AbstractDungeon.cardRewardScreen.rewardGroup;
        }
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.GRID) {
            return AbstractDungeon.gridSelectScreen == null ? null : AbstractDungeon.gridSelectScreen.targetGroup;
        }
        return null;
    }

    // Any pending card-obtain effect on the four effect lists AbstractDungeon
    // keeps: effectList, effectsQueue (promoted to effectList next tick),
    // topLevelEffects (Neow's transform) and topLevelEffectsQueue. Matched by
    // class-name prefix so no VFX class has to be imported.
    private static boolean hasPendingObtainEffect() {
        return listHasObtainEffect(AbstractDungeon.effectList)
            || listHasObtainEffect(AbstractDungeon.effectsQueue)
            || listHasObtainEffect(AbstractDungeon.topLevelEffects)
            || listHasObtainEffect(AbstractDungeon.topLevelEffectsQueue);
    }

    private static boolean hasPendingDeckObtainEffect() {
        return listHasClass(AbstractDungeon.effectList, "ShowCardAndObtainEffect")
            || listHasClass(AbstractDungeon.effectsQueue, "ShowCardAndObtainEffect")
            || listHasClass(AbstractDungeon.topLevelEffects, "ShowCardAndObtainEffect")
            || listHasClass(AbstractDungeon.topLevelEffectsQueue, "ShowCardAndObtainEffect")
            || listHasClass(AbstractDungeon.effectList, "FastCardObtainEffect")
            || listHasClass(AbstractDungeon.effectsQueue, "FastCardObtainEffect");
    }

    private static boolean listHasClass(java.util.ArrayList<?> list, String simpleName) {
        if (list == null) {
            return false;
        }
        for (Object e : list) {
            if (e != null && e.getClass().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean listHasObtainEffect(java.util.ArrayList<?> list) {
        if (list == null) {
            return false;
        }
        for (Object e : list) {
            if (e == null) {
                continue;
            }
            String name = e.getClass().getSimpleName();
            // Keys (ObtainKeyEffect, Sapphire/Emerald from a reward) and
            // potions (ObtainPotionEffect, Entropic Brew out of combat) also
            // land when their effect runs, after the claim is published.
            if (name.startsWith("ShowCardAnd") || name.startsWith("FastCardObtain")
                || name.equals("ObtainKeyEffect") || name.equals("ObtainPotionEffect")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects whether the state of the game menu has changed. Right now, this only occurs when you first enter the
     * menu, either after starting Slay the Spire for the first time, or after ending a game and returning to the menu.
     *
     * @return Whether the main menu has just been entered.
     */
    public static boolean checkForMenuStateChange() {
        boolean stateChange = false;
        if (!hasPresentedOutOfGameState && CardCrawlGame.mode == CardCrawlGame.GameMode.CHAR_SELECT && CardCrawlGame.mainMenuScreen != null) {
            stateChange = true;
            hasPresentedOutOfGameState = true;
        }
        if (stateChange) {
            externalChange = false;
            waitingForCommand = true;
        }
        return stateChange;
    }

    /**
     * Detects a state change in AbstractDungeon, and updates all of the local variables used to detect
     * changes in the dungeon state. Sets waitingForCommand = true if a state change was registered since
     * the last command was sent.
     *
     * @return Whether a dungeon state change was detected
     */
    public static boolean checkForDungeonStateChange() {
        boolean stateChange = false;
        if (CommandExecutor.isInDungeon()) {
            stateChange = hasDungeonStateChanged();
            if (stateChange) {
                externalChange = false;
                waitingForCommand = true;
                previousPhase = AbstractDungeon.getCurrRoom().phase;
                previousScreen = AbstractDungeon.screen;
                previousScreenUp = AbstractDungeon.isScreenUp;
                previousGold = AbstractDungeon.player.gold;
                previousScreenContent = currentScreenContent();
                previousGridSelectConfirmUp = AbstractDungeon.gridSelectScreen.confirmScreenUp;
                timeout = 0;
            }
        } else {
            myTurn = false;
        }
        return stateChange;
    }

    public static boolean isWaitingForCommand() {
        return waitingForCommand;
    }
}