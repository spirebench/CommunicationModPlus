package communicationmodplus;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.screens.DungeonTransitionScreen;
import com.megacrit.cardcrawl.vfx.ObtainKeyEffect;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.vfx.cardManip.ShowCardAndObtainEffect;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.helpers.TrialHelper;
import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.helpers.input.InputAction;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.*;
import communicationmodplus.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.megacrit.cardcrawl.rooms.ShopRoom;
import com.megacrit.cardcrawl.rooms.RestRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import java.util.ArrayList;

public class CommandExecutor {

    private static final Logger logger = LogManager.getLogger(CommandExecutor.class.getName());

    public static boolean executeCommand(String command) throws InvalidCommandException {
        command = command.toLowerCase();
        String [] tokens = command.split("\\s+");
        if(tokens.length == 0) {
            return false;
        }
        if (CommandExecutor.isPotionGift(tokens) && !CommandExecutor.isInDungeon()) {
            throw new InvalidCommandException("Invalid command: potion. Possible commands: " + CommandExecutor.getAvailableCommands());
        }
        if (!CommandExecutor.isPotionGift(tokens) && !isCommandAvailable(tokens[0])) {
            throw new InvalidCommandException("Invalid command: " + tokens[0] + ". Possible commands: " + getAvailableCommands());
        }
        String command_tail = command.substring(tokens[0].length());
        // The post-damage snapshots belong to one command: an acting one
        // starts a new set (bug #46). A poll leaves them for the harness.
        if (!tokens[0].equals("state") && !tokens[0].equals("wait")) {
            GameStateListener.clearActionSnapshots();
        }
        if (CommandExecutor.isPotionGift(tokens)) {
            // potion <id>: the hidden gift form, told apart from
            // "potion use|discard <slot>" by its second word.
            CommandExecutor.executePotionGiftCommand(tokens, command_tail.trim());
            return true;
        }
        switch(tokens[0]) {
            case "play":
                executePlayCommand(tokens);
                return true;
            case "end":
                executeEndCommand();
                return true;
            case "choose":
                executeChooseCommand(tokens);
                return true;
            case "potion":
                executePotionCommand(tokens);
                return true;
            case "confirm":
            case "proceed":
                executeConfirmCommand();
                return true;
            case "skip":
            case "cancel":
            case "return":
            case "leave":
                executeCancelCommand();
                return true;
            case "start":
                executeStartCommand(tokens);
                return true;
            case "continue":
                executeContinueCommand(tokens);
                return true;
            case "state":
                executeStateCommand();
                return false;
            case "key":
                executeKeyCommand(tokens);
                return true;
            case "click":
                executeClickCommand(tokens);
                return true;
            case "wait":
                executeWaitCommand(tokens);
                return true;
            case "maxhp":
                executeMaxHpGiftCommand(tokens);
                return true;
            case "keys":
                executeKeyGiftCommand(tokens);
                return true;
            case "act":
                executeActJumpCommand(tokens);
                return true;
            case "floor":
                executeFloorJumpCommand(tokens);
                return true;
            case "relic":
                executeRelicCommand(tokens, command_tail.trim());
                return true;
            case "card":
                executeCardGiftCommand(tokens, command_tail.trim());
                return true;

            default:
                logger.info("This should never happen.");
                throw new InvalidCommandException("Command not recognized.");
        }
    }

    public static ArrayList<String> getAvailableCommands() {
        ArrayList<String> availableCommands = new ArrayList<>();
        if (isPlayCommandAvailable()) {
            availableCommands.add("play");
        }
        if (isChooseCommandAvailable()) {
            availableCommands.add("choose");
        }
        if (isEndCommandAvailable()) {
            availableCommands.add("end");
        }
        if (isPotionCommandAvailable()) {
            availableCommands.add("potion");
        }
        if (isConfirmCommandAvailable()) {
            availableCommands.add(ChoiceScreenUtils.getConfirmButtonText());
        }
        if (isCancelCommandAvailable()) {
            availableCommands.add(ChoiceScreenUtils.getCancelButtonText());
        }
        if (isStartCommandAvailable()) {
            availableCommands.add("start");
            availableCommands.add("continue");
        }
        if (isInDungeon()) {
            availableCommands.add("key");
            availableCommands.add("click");
            availableCommands.add("wait");
        }
        availableCommands.add("state");
        return availableCommands;
    }

    public static boolean isCommandAvailable(String command) {
        if (command.equals("relic") || command.equals("card") || command.equals("maxhp") || command.equals("keys") || command.equals("act") || command.equals("floor")) {
            // A test hook, not a move: deliberately left out of
            // available_commands so nothing that reads that list offers it.
            return isInDungeon();
        }
        if(command.equals("confirm") || command.equalsIgnoreCase("proceed")) {
            return isConfirmCommandAvailable();
        } else if (command.equals("skip") || command.equals("cancel") || command.equals("return") || command.equals("leave")) {
            return isCancelCommandAvailable();
        } else {
            return getAvailableCommands().contains(command);
        }
    }

    public static boolean isInDungeon() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon.isPlayerInDungeon() && AbstractDungeon.currMapNode != null;
    }

    private static boolean isPlayCommandAvailable() {
        if(isInDungeon()) {
            if(AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.isScreenUp) {
                // Play command is not available if none of the cards are playable.
                // TODO: this does not check the case where there is no legal target for a target card.
                for (AbstractCard card : AbstractDungeon.player.hand.group) {
                    if (card.canUse(AbstractDungeon.player, null)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isEndCommandAvailable() {
        return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.isScreenUp;
    }

    public static boolean isChooseCommandAvailable() {
        if(isInDungeon()) {
            return !isPlayCommandAvailable() && !ChoiceScreenUtils.getCurrentChoiceList().isEmpty();
        } else {
            return false;
        }
    }

    public static boolean isPotionCommandAvailable() {
        if(isInDungeon()) {
            for(AbstractPotion potion : AbstractDungeon.player.potions) {
                if(!(potion instanceof PotionSlot)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isConfirmCommandAvailable() {
        if(isInDungeon()) {
            return ChoiceScreenUtils.isConfirmButtonAvailable();
        } else {
            return false;
        }
    }

    public static boolean isCancelCommandAvailable() {
        if(isInDungeon()) {
            return ChoiceScreenUtils.isCancelButtonAvailable();
        } else {
            return false;
        }
    }

    private static void executeContinueCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        AbstractPlayer.PlayerClass selectedClass = null;
        for (AbstractPlayer.PlayerClass playerClass : AbstractPlayer.PlayerClass.values()) {
            if (!playerClass.name().equalsIgnoreCase(tokens[1])) continue;
            selectedClass = playerClass;
        }
        if (tokens[1].equalsIgnoreCase("silent")) {
            selectedClass = AbstractPlayer.PlayerClass.THE_SILENT;
        }
        if (selectedClass == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        SaveFile saveFile = SaveAndContinue.loadSaveFile(selectedClass);
        if (saveFile == null) {
            throw new InvalidCommandException("No save file found for " + selectedClass.name());
        }
        CardCrawlGame.chosenCharacter = selectedClass;
        CardCrawlGame.saveFile = saveFile;
        CardCrawlGame.loadingSave = true;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        CharacterManager manager = new CharacterManager();
        manager.setChosenCharacter(selectedClass);
        AbstractDungeon.ascensionLevel = saveFile.ascension_level;
        AbstractDungeon.isAscensionMode = saveFile.ascension_level > 0;
        GameStateListener.resetStateVariables();
    }

    public static boolean isStartCommandAvailable() {
        return !isInDungeon() && CardCrawlGame.mainMenuScreen != null;
    }

    private static void executeStateCommand() {
        CommunicationMod.mustSendGameState = true;
    }

    private static void executePlayCommand(String[] tokens) throws InvalidCommandException {
        if(tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int card_index;
        try {
            card_index = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(card_index == 0) {
            card_index = 10;
        }
        if((card_index < 1) || (card_index > AbstractDungeon.player.hand.size())) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(card_index));
        }
        int monster_index = -1;
        if(tokens.length == 3) {
            try {
                monster_index = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
        }
        AbstractMonster target_monster = null;
        if (monster_index != -1) {
            if (monster_index < 0 || monster_index >= AbstractDungeon.getCurrRoom().monsters.monsters.size()) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(monster_index));
            } else {
                target_monster = AbstractDungeon.getCurrRoom().monsters.monsters.get(monster_index);
            }
        }
        if((card_index < 1) || (card_index > AbstractDungeon.player.hand.size()) || !(AbstractDungeon.player.hand.group.get(card_index - 1).canUse(AbstractDungeon.player, target_monster))) {
            throw new InvalidCommandException("Selected card cannot be played with the selected target.");
        }
        AbstractCard card = AbstractDungeon.player.hand.group.get(card_index - 1);
        if(card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
            if(target_monster == null) {
                throw new InvalidCommandException("Selected card requires an enemy target.");
            }
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, target_monster));
        } else {
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, null));
        }
    }

    private static void executeEndCommand() throws InvalidCommandException {
        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
    }

    private static void executeChooseCommand(String[] tokens) throws InvalidCommandException {
        ArrayList<String> validChoices = ChoiceScreenUtils.getCurrentChoiceList();
        if(validChoices.size() == 0) {
            throw new InvalidCommandException("The choice command is not implemented on this screen.");
        }
        int choice_index = getValidChoiceIndex(tokens, validChoices);
        ChoiceScreenUtils.executeChoice(choice_index);
    }

    private static void executePotionCommand(String[] tokens) throws  InvalidCommandException {
        int potion_index;
        boolean use;
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        if(tokens[1].equals("use")) {
            use = true;
        } else if (tokens[1].equals("discard")) {
            use = false;
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        try {
            potion_index = Integer.parseInt(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        if(potion_index < 0 || potion_index >= AbstractDungeon.player.potionSlots) {
            throw new InvalidCommandException("Potion index out of bounds.");
        }
        AbstractPotion selectedPotion = AbstractDungeon.player.potions.get(potion_index);
        if(selectedPotion instanceof PotionSlot) {
            throw new InvalidCommandException("No potion in the selected slot.");
        }
        if(use && !selectedPotion.canUse()) {
            throw new InvalidCommandException("Selected potion cannot be used.");
        }
        // In combat, a screen that is up is a pick an action is waiting on
        // (a hand picker, a grid, a card choice). A potion used over it
        // queues its actions behind that one -- a Colorless Potion drunk
        // over Well-Laid Plans' retain picker opened its card choice behind
        // the picker and the state never came ready (FZ3SVFXGJE4PA).
        if (use && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && AbstractDungeon.isScreenUp) {
            throw new InvalidCommandException("Potions cannot be used while a selection screen is open in combat.");
        }
        if(!use && !selectedPotion.canDiscard()) {
            throw new InvalidCommandException("Selected potion cannot be discarded.");
        }
        int monster_index = -1;
        if (use) {
            if (selectedPotion.targetRequired) {
                if (tokens.length < 4) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, " Selected potion requires a target.");
                }
                AbstractMonster target_monster;
                try {
                    monster_index = Integer.parseInt(tokens[3]);
                } catch (NumberFormatException e) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[3]);
                }
                if (monster_index < 0 || monster_index >= AbstractDungeon.getCurrRoom().monsters.monsters.size()) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(monster_index));
                } else {
                    target_monster = AbstractDungeon.getCurrRoom().monsters.monsters.get(monster_index);
                }
                selectedPotion.use(target_monster);
            } else {
                selectedPotion.use(AbstractDungeon.player);
            }
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onUsePotion();
            }
        }
        AbstractDungeon.topPanel.destroyPotion(selectedPotion.slot);
        GameStateListener.registerStateChange();
    }

    private static void executeConfirmCommand() {
        ChoiceScreenUtils.pressConfirmButton();
    }

    private static void executeCancelCommand() {
        // A reward screen a relic opens in a shop (Cauldron, Orrery) is left
        // with cancel. Whatever the player left on it stays in
        // combatRewardScreen.rewards, and AbstractDungeon.closeCurrentScreen()
        // sends any later GRID back to COMBAT_REWARD while that list is not
        // empty: the shop's purge then reopened the leftover screen, which
        // never came ready, and the run stalled for hours (bug #58). Leaving
        // the screen forfeits what is on it, as the player chose.
        if (AbstractDungeon.screen == AbstractDungeon.CurrentScreen.COMBAT_REWARD
            && AbstractDungeon.getCurrRoom() instanceof com.megacrit.cardcrawl.rooms.ShopRoom
            && AbstractDungeon.combatRewardScreen != null
            && AbstractDungeon.combatRewardScreen.rewards != null) {
            AbstractDungeon.combatRewardScreen.rewards.clear();
        }
        ChoiceScreenUtils.pressCancelButton();
    }

    private static void executeStartCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int ascensionLevel = 0;
        boolean seedSet = false;
        long seed = 0;
        AbstractPlayer.PlayerClass selectedClass = null;
        for(AbstractPlayer.PlayerClass playerClass : AbstractPlayer.PlayerClass.values()) {
            if(playerClass.name().equalsIgnoreCase(tokens[1])) {
                selectedClass = playerClass;
            }
        }
        // Better to allow people to specify the character as "silent" rather than requiring "the_silent"
        if(tokens[1].equalsIgnoreCase("silent")) {
            selectedClass = AbstractPlayer.PlayerClass.THE_SILENT;
        }
        if(selectedClass == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(tokens.length >= 3) {
            try {
                ascensionLevel = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
            if(ascensionLevel < 0 || ascensionLevel > 20) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[2]);
            }
        }
        if(tokens.length >= 4) {
            String seedString = tokens[3].toUpperCase();
            if(!seedString.matches("^[A-Z0-9]+$")) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, seedString);
            }
            seedSet = true;
            seed = SeedHelper.getLong(seedString);
            boolean isTrialSeed = TrialHelper.isTrialSeed(seedString);
            if (isTrialSeed) {
                Settings.specialSeed = seed;
                Settings.isTrial = true;
                seedSet = false;
            }
        }
        if(!seedSet) {
            seed = SeedHelper.generateUnoffensiveSeed(new Random(System.nanoTime()));
        }
        Settings.seed = seed;
        Settings.seedSet = seedSet;
        AbstractDungeon.generateSeeds();
        AbstractDungeon.ascensionLevel = ascensionLevel;
        AbstractDungeon.isAscensionMode = ascensionLevel > 0;
        CardCrawlGame.startOver = true;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        CharacterManager manager = new CharacterManager();
        manager.setChosenCharacter(selectedClass);
        CardCrawlGame.chosenCharacter = selectedClass;
        GameStateListener.resetStateVariables();
    }

    private static void executeKeyCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int keycode = getKeycode(tokens[1].toUpperCase());
        if (keycode == -1) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        int timeout = 100;
        if (tokens.length >= 3) {
            try {
                timeout = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
            if(timeout < 0) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[2]);
            }
        }
        InputActionPatch.doKeypress = true;
        InputActionPatch.key = keycode;
        InputHelper.updateFirst();
        GameStateListener.setTimeout(timeout);
    }

    private static void executeClickCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 4) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        float x = 0;
        float y = 0;
        int timeout = 100;
        try {
            x = Float.parseFloat(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        try {
            y = Float.parseFloat(tokens[3]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[3]);
        }
        x = x * Settings.scale;
        y = y * Settings.scale;
        Gdx.input.setCursorPosition((int)x, (int)y);
        InputHelper.updateFirst();
        String token1 = tokens[1].toUpperCase();
        if (token1.equals("LEFT")) {
            InputHelper.justClickedLeft = true;
            InputHelper.isMouseDown = true;
            ReflectionHacks.setPrivateStatic(InputHelper.class, "isPrevMouseDown", true);
        } else if (token1.equals("RIGHT")) {
            InputHelper.justClickedRight = true;
            InputHelper.isMouseDown_R = true;
            ReflectionHacks.setPrivateStatic(InputHelper.class, "isPrevMouseDown_R", true);
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if (tokens.length >= 5) {
            try {
                timeout = Integer.parseInt(tokens[4]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[4]);
            }
            if(timeout < 0) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[4]);
            }
        }
        GameStateListener.setTimeout(timeout);
    }

    private static void executeWaitCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int timeout = 0;
        try {
            timeout = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(timeout < 0) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
        }
        GameStateListener.setTimeout(timeout);
    }

    /*
     * maxhp <n>: raise max HP by n, healing the same amount (the game's own
     * increaseMaxHp, as BaseMod's "maxhp add"). For fuzz games that are to
     * live into the late acts (AASTS_MAXHP_GIFT).
     */
    private static void executeMaxHpGiftCommand(String[] tokens) throws InvalidCommandException {
        int amount;
        try {
            amount = Integer.parseInt(tokens.length > 1 ? tokens[1] : "");
        }
        catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens.length > 1 ? tokens[1] : "");
        }
        if (amount <= 0) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        AbstractDungeon.player.increaseMaxHp(amount, true);
        GameStateListener.setTimeout(30);
    }

    /*
     * keys <ruby|emerald|sapphire|all>: obtain the Act 4 keys through the
     * game's ObtainKeyEffect, as BaseMod's "key add" does, and make the final
     * act available (AASTS_KEY_GIFTS). Not "key": that word is the keyboard
     * command. The state listener holds the published state until the
     * effect has landed (ObtainKeyEffect is one of the obtain effects it
     * waits for).
     */
    private static void executeKeyGiftCommand(String[] tokens) throws InvalidCommandException {
        String which = tokens.length > 1 ? tokens[1] : "";
        boolean all = which.equals("all");
        if (!all && !which.equals("ruby") && !which.equals("emerald") && !which.equals("sapphire")) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, which);
        }
        Settings.isFinalActAvailable = true;
        if ((all || which.equals("ruby")) && !Settings.hasRubyKey) {
            AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.RED));
        }
        if ((all || which.equals("emerald")) && !Settings.hasEmeraldKey) {
            AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.GREEN));
        }
        if ((all || which.equals("sapphire")) && !Settings.hasSapphireKey) {
            AbstractDungeon.topLevelEffects.add(new ObtainKeyEffect(ObtainKeyEffect.KeyColor.BLUE));
        }
        GameStateListener.setTimeout(30);
    }

    /*
     * act <2|3|4>: leave this room for the start of that act, the way
     * BaseMod's "act" console command does: the room is marked complete and
     * the dungeon transition the game runs after a boss builds the new act
     * (dungeonTransitionSetup heals, resets the act's lists and counts the
     * act up). The floor counter is set to where it stands at that act's
     * start in a played run (17, 34, 51), so floor numbers read as they
     * would. For fuzz games that are to cover the late acts
     * (AASTS_START_ACT); sent last, after every other gift at Neow.
     */
    /*
     * Leave the current room's event as the game does when the player walks
     * out of it (AbstractEvent.dispose). The room-event dialog's option list
     * is static: skipped, Neow's "[Talk]" stayed at its head, the next room
     * event appended its own after it, and every click landed one option over
     * -- Masked Bandits' "Pay" fought, "Talk" paid and "Fight!" did nothing
     * (FZ39XPEUT9FTI, FZ67NWYX70I56, bug #230; after an act jump alone,
     * JZ352JZ6IM4PQ, bug #296).
     */
    private static void leaveCurrentEvent(String why) {
        AbstractRoom left = AbstractDungeon.getCurrRoom();
        if (left != null && left.event != null) {
            try {
                left.event.dispose();
            } catch (RuntimeException e) {
                logger.info(why + ": disposing the current event failed: " + e);
            }
        }
        com.megacrit.cardcrawl.events.RoomEventDialog.optionList.clear();
    }

    private static void executeActJumpCommand(String[] tokens) throws InvalidCommandException {
        String[] dungeons = {null, "Exordium", "TheCity", "TheBeyond", "TheEnding"};
        int act;
        try {
            act = Integer.parseInt(tokens.length > 1 ? tokens[1] : "");
        }
        catch (NumberFormatException e) {
            act = -1;
        }
        if (act < 2 || act > 4) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens.length > 1 ? tokens[1] : "");
        }
        AbstractDungeon.player.hand.group.clear();
        AbstractDungeon.actionManager.clear();
        AbstractDungeon.effectsQueue.clear();
        AbstractDungeon.effectList.clear();
        // An act-opening start floor (18, 35, 52) is reached by this jump
        // alone, with no floor jump after it (JZ352JZ6IM4PQ, bug #296).
        leaveCurrentEvent("act jump");
        AbstractDungeon.floorNum = (act - 1) * 17;
        CardCrawlGame.nextDungeon = dungeons[act];
        CardCrawlGame.dungeonTransitionScreen = new DungeonTransitionScreen(dungeons[act]);
        AbstractDungeon.actNum = act - 1;
        AbstractDungeon.getCurrRoom().phase = AbstractRoom.RoomPhase.COMPLETE;
        GameStateListener.setTimeout(30);
    }

    /*
     * floor <n>: on this act's map at its start, make floor n the next room.
     * The player is put on a node of the row before it, picked by a Random
     * seeded from the run seed and n (never one of the game's own RNGs, so
     * the run's draws are untouched), and that node's room is marked
     * complete, as a room left behind is; the floor counter is set to n - 1.
     * The node goes on the path, so a resume rebuilds the position, and
     * firstRoomChosen is set, so the next pick follows the node's edges. For
     * a boss floor the node is on the last row, from which the boss is the
     * next choice (AASTS_START_FLOOR, after "act" when n is in a later act).
     */
    private static void executeFloorJumpCommand(String[] tokens) throws InvalidCommandException {
        int floor;
        try {
            floor = Integer.parseInt(tokens.length > 1 ? tokens[1] : "");
        }
        catch (NumberFormatException e) {
            floor = -1;
        }
        int offset = (AbstractDungeon.actNum - 1) * 17;
        int row = floor - offset - 2;
        if (floor < 0 || row < 0 || row >= AbstractDungeon.map.size() || AbstractDungeon.screen != AbstractDungeon.CurrentScreen.MAP) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens.length > 1 ? tokens[1] : "");
        }
        // Only rooms that are whole before they are entered: an event room
        // has no event and a treasure room no chest until the player walks
        // in, and the game reads both while the room is current (an
        // EventRoom's null event crashed the state listener, fuzz18).
        ArrayList<MapRoomNode> candidates = new ArrayList<MapRoomNode>();
        ArrayList<MapRoomNode> fallback = new ArrayList<MapRoomNode>();
        for (MapRoomNode node : AbstractDungeon.map.get(row)) {
            if (node.room == null || !node.hasEdges()) continue;
            fallback.add(node);
            if (node.room instanceof MonsterRoom || node.room instanceof RestRoom || node.room instanceof ShopRoom) {
                candidates.add(node);
            }
        }
        if (candidates.isEmpty()) {
            candidates = fallback;
        }
        if (candidates.isEmpty()) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        java.util.Random pick = new java.util.Random(Settings.seed * 31L + floor);
        MapRoomNode node = candidates.get(pick.nextInt(candidates.size()));
        leaveCurrentEvent("floor jump");
        AbstractDungeon.setCurrMapNode(node);
        node.taken = true;
        node.room.phase = AbstractRoom.RoomPhase.COMPLETE;
        AbstractDungeon.firstRoomChosen = true;
        AbstractDungeon.pathX.add(node.x);
        AbstractDungeon.pathY.add(node.y);
        AbstractDungeon.floorNum = floor - 1;
        AbstractDungeon.dungeonMapScreen.open(false);
        GameStateListener.setTimeout(30);
    }

    /*
     * relic <relic id>: obtain a relic, as BaseMod's "relic add" console
     * command does, and take it out of the dungeon's relic pools so it is not
     * offered a second time. Used by the harness to test what particular
     * relics do to it (AASTS_RELIC_GIFTS). The id is matched ignoring case,
     * since every command is lowercased on arrival.
     */
    private static void executeRelicCommand(String[] tokens, String relicName) throws InvalidCommandException {
        if (relicName.isEmpty()) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        AbstractRelic found = null;
        ArrayList<ArrayList<AbstractRelic>> lists = new ArrayList<ArrayList<AbstractRelic>>();
        lists.add(RelicLibrary.starterList);
        lists.add(RelicLibrary.commonList);
        lists.add(RelicLibrary.uncommonList);
        lists.add(RelicLibrary.rareList);
        lists.add(RelicLibrary.bossList);
        lists.add(RelicLibrary.specialList);
        lists.add(RelicLibrary.shopList);
        lists.add(RelicLibrary.redList);
        lists.add(RelicLibrary.greenList);
        lists.add(RelicLibrary.blueList);
        lists.add(RelicLibrary.whiteList);
        for (ArrayList<AbstractRelic> list : lists) {
            for (AbstractRelic candidate : list) {
                if (!candidate.relicId.equalsIgnoreCase(relicName)) continue;
                found = candidate;
                break;
            }
            if (found != null) break;
        }
        if (found == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, relicName);
        }
        AbstractDungeon.commonRelicPool.remove(found.relicId);
        AbstractDungeon.uncommonRelicPool.remove(found.relicId);
        AbstractDungeon.rareRelicPool.remove(found.relicId);
        AbstractDungeon.bossRelicPool.remove(found.relicId);
        AbstractDungeon.shopRelicPool.remove(found.relicId);
        AbstractDungeon.getCurrRoom().spawnRelicAndObtain((float)(Settings.WIDTH / 2), (float)(Settings.HEIGHT / 2), found.makeCopy());
        // Report once the relic has settled, whether or not obtaining it
        // changed anything the state listener watches.
        GameStateListener.setTimeout(30);
    }

    /*
     * The hidden gift form of the potion command, "potion <potion id>": any
     * second word but use/discard. Available whenever a run is in progress,
     * unlike "potion use|discard", which needs a potion in the belt.
     */
    private static boolean isPotionGift(String[] tokens) {
        return tokens.length >= 2 && tokens[0].equals("potion") && !tokens[1].equals("use") && !tokens[1].equals("discard");
    }

    /*
     * potion <potion id>: put a potion in the first empty belt slot, through
     * the game's own AbstractPlayer.obtainPotion (so Sozu refuses it, as it
     * does any potion). Refused with an error when the belt is full or the id
     * is unknown. Used by the harness to test what particular potions do to
     * it (AASTS_POTION_GIFTS). Matched ignoring case against PotionHelper's
     * full list, since every command is lowercased on arrival.
     */
    private static void executePotionGiftCommand(String[] tokens, String potionName) throws InvalidCommandException {
        String found = null;
        for (String id : PotionHelper.getPotions(null, true)) {
            if (!id.equalsIgnoreCase(potionName)) continue;
            found = id;
            break;
        }
        if (found == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, potionName);
        }
        if (!AbstractDungeon.player.obtainPotion(PotionHelper.getPotion(found))) {
            throw new InvalidCommandException("Cannot obtain potion " + found + ": the potion belt is full, or a relic forbids potions.");
        }
        GameStateListener.setTimeout(30);
    }

    /*
     * card <card id> [upgrades]: add a card to the master deck through the
     * game's ordinary obtain path, ShowCardAndObtainEffect, so obtain hooks
     * fire (Ceramic Fish's gold, the Eggs' upgrade, Omamori against a curse)
     * and the state listener's out-of-combat obtain gate holds the published
     * state until the card has joined the deck. A trailing integer upgrades
     * the card that many times (Searing Blow takes any number; other cards
     * stop at one). The id is matched ignoring case against CardLibrary.
     * Used by the harness to test what particular cards do to it
     * (AASTS_CARD_GIFTS).
     */
    private static void executeCardGiftCommand(String[] tokens, String cardName) throws InvalidCommandException {
        if (cardName.isEmpty()) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int upgrades = 0;
        String id = CommandExecutor.findCardId(cardName);
        if (id == null) {
            int split = cardName.lastIndexOf(' ');
            if (split > 0) {
                try {
                    upgrades = Integer.parseInt(cardName.substring(split + 1));
                    id = CommandExecutor.findCardId(cardName.substring(0, split).trim());
                }
                catch (NumberFormatException e) {
                    id = null;
                }
            }
        }
        if (id == null || upgrades < 0) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, cardName);
        }
        AbstractCard card = CardLibrary.getCard(id).makeCopy();
        for (int i = 0; i < upgrades && card.canUpgrade(); ++i) {
            card.upgrade();
        }
        AbstractDungeon.effectList.add(new ShowCardAndObtainEffect(card, (float)Settings.WIDTH / 2.0f, (float)Settings.HEIGHT / 2.0f));
        GameStateListener.setTimeout(30);
    }

    private static String findCardId(String cardName) {
        for (String id : CardLibrary.cards.keySet()) {
            if (id.equalsIgnoreCase(cardName)) {
                return id;
            }
        }
        return null;
    }

    private static int getKeycode(String keyName) {
        InputAction action;
        switch(keyName) {
            case "CONFIRM":
                action = InputActionSet.confirm;
                break;
            case "CANCEL":
                action = InputActionSet.cancel;
                break;
            case "MAP":
                action = InputActionSet.map;
                break;
            case "DECK":
                action = InputActionSet.masterDeck;
                break;
            case "DRAW_PILE":
                action = InputActionSet.drawPile;
                break;
            case "DISCARD_PILE":
                action = InputActionSet.discardPile;
                break;
            case "EXHAUST_PILE":
                action = InputActionSet.exhaustPile;
                break;
            case "END_TURN":
                action = InputActionSet.endTurn;
                break;
            case "UP":
                action = InputActionSet.up;
                break;
            case "DOWN":
                action = InputActionSet.down;
                break;
            case "LEFT":
                action = InputActionSet.left;
                break;
            case "RIGHT":
                action = InputActionSet.right;
                break;
            case "DROP_CARD":
                action = InputActionSet.releaseCard;
                break;
            case "CARD_1":
                action = InputActionSet.selectCard_1;
                break;
            case "CARD_2":
                action = InputActionSet.selectCard_2;
                break;
            case "CARD_3":
                action = InputActionSet.selectCard_3;
                break;
            case "CARD_4":
                action = InputActionSet.selectCard_4;
                break;
            case "CARD_5":
                action = InputActionSet.selectCard_5;
                break;
            case "CARD_6":
                action = InputActionSet.selectCard_6;
                break;
            case "CARD_7":
                action = InputActionSet.selectCard_7;
                break;
            case "CARD_8":
                action = InputActionSet.selectCard_8;
                break;
            case "CARD_9":
                action = InputActionSet.selectCard_9;
                break;
            case "CARD_10":
                action = InputActionSet.selectCard_10;
                break;
            default:
                action = null;
        }
        if (action == null) {
            return -1;
        } else {
            return (int) ReflectionHacks.getPrivate(action, InputAction.class, "keycode");
        }
    }

    private static int getValidChoiceIndex(String[] tokens, ArrayList<String> validChoices) throws InvalidCommandException {
        if(tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, " A choice is required.");
        }
        String choice = merge_arguments(tokens);
        int choice_index = -1;
        if(validChoices.contains(choice)) {
            choice_index = validChoices.indexOf(choice);
        } else {
            try {
                choice_index = Integer.parseInt(choice);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, choice);
            }
            if(choice_index < 0 || choice_index >= validChoices.size()) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, choice);
            }
        }
        return choice_index;
    }

    private static String merge_arguments(String[] tokens) {
        StringBuilder builder = new StringBuilder();
        for(int i = 1; i < tokens.length; i++) {
            builder.append(tokens[i]);
            if(i != tokens.length - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }



}
