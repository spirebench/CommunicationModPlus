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
            case "sethp":
                executeSetHpCommand(tokens);
                return true;
            case "setgold":
                executeSetGoldCommand(tokens);
                return true;
            case "setplaytime":
                executeSetPlaytimeCommand(tokens);
                return true;
            case "win":
                executeWinCommand();
                return true;
            case "deck":
                executeDeckCommand(tokens);
                return true;
            case "abandon":
                executeAbandonCommand();
                return true;
            case "shopcard":
                executeShopCardCommand(tokens);
                return true;
            case "shoppotion":
                executeShopPotionCommand(tokens);
                return true;
            case "rewardpotion":
                executeRewardPotionCommand(tokens);
                return true;
            case "rng":
                executeRngCommand(tokens);
                return false;
            case "leaveroom":
                executeLeaveRoomCommand(tokens);
                return true;
            case "neowmini":
                executeNeowMiniCommand(tokens);
                return true;
            case "bossseen":
                // No state change: returning true would clear the listener's
                // waiting flag, which at the main menu nothing sets again, so
                // every later state said ready_for_command false.
                executeBossSeenCommand(tokens);
                return false;

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
        if (command.equals("relic") || command.equals("card") || command.equals("maxhp") || command.equals("keys") || command.equals("act") || command.equals("floor")
                || command.equals("sethp") || command.equals("setgold") || command.equals("setplaytime") || command.equals("deck") || command.equals("abandon") || command.equals("shopcard")
                || command.equals("shoppotion") || command.equals("rewardpotion") || command.equals("neowmini")
                || command.equals("rng") || command.equals("leaveroom")) {
            // A test hook, not a move: deliberately left out of
            // available_commands so nothing that reads that list offers it.
            return isInDungeon();
        }
        if (command.equals("bossseen")) {
            // A test hook too, and set before a run starts, so not tied to a dungeon.
            return true;
        }
        if(command.equals("confirm") || command.equalsIgnoreCase("proceed")) {
            return isConfirmCommandAvailable();
        } else if (command.equals("skip") || command.equals("cancel") || command.equals("return") || command.equals("leave")) {
            return isCancelCommandAvailable();
        } else if (command.equals("win")) {
            // A test hook too: only inside a fight, and only once it is the
            // player's to act in (the room is in COMBAT).
            return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT;
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
     * (FZ39XPEUT9FTI, FZ67NWYX70I56, bug #230).
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
        // alone, with no floor jump after it (JZ352JZ6IM4PQ, bug #296). The
        // list only: Neow's room stays current, and is rendered, until the
        // transition to the next dungeon, so its event is not disposed here
        // (disposed, NeowEvent.render threw a NullPointerException and every
        // later-act start crashed the game, round67 fuzz1096-1098).
        com.megacrit.cardcrawl.events.RoomEventDialog.optionList.clear();
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

    /*
     * The streamer benchmark's hooks (docs/streamer_bench.md). Its builder
     * replays a recorded human run and skips the fights, so it has to put
     * the run's own HP, gold and deck back where the record says they were.
     * Like the gifts, none of these is a move and none is listed in
     * available_commands.
     *
     * sethp <hp> [<max hp>]: set the player's HP, and max HP when given;
     * HP is clamped to max HP. No relic or power hears of it (no heal, no
     * HP loss), which is the point: the record already includes them.
     */
    private static void executeSetHpCommand(String[] tokens) throws InvalidCommandException {
        int hp;
        int maxHp = AbstractDungeon.player.maxHealth;
        try {
            hp = Integer.parseInt(tokens.length > 1 ? tokens[1] : "");
            if (tokens.length > 2) {
                maxHp = Integer.parseInt(tokens[2]);
            }
        }
        catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, String.join(" ", tokens));
        }
        if (hp < 1 || maxHp < 1) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, String.join(" ", tokens));
        }
        AbstractDungeon.player.maxHealth = maxHp;
        AbstractDungeon.player.currentHealth = Math.min(hp, maxHp);
        AbstractDungeon.player.healthBarUpdatedEvent();
        GameStateListener.setTimeout(30);
    }

    /* setgold <n>: set the player's gold, silently (no Ectoplasm, no Bloody Idol). */
    private static void executeSetGoldCommand(String[] tokens) throws InvalidCommandException {
        int gold;
        try {
            gold = Integer.parseInt(tokens.length > 1 ? tokens[1] : "");
        }
        catch (NumberFormatException e) {
            gold = -1;
        }
        if (gold < 0) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens.length > 1 ? tokens[1] : "");
        }
        AbstractDungeon.player.gold = gold;
        AbstractDungeon.player.displayGold = gold;
        GameStateListener.setTimeout(30);
    }

    /*
     * setplaytime <seconds>: set the run's clock. Not cosmetic: Secret Portal
     * joins Act 3's event pool only once CardCrawlGame.playtime reaches 800 s
     * (AbstractDungeon.getShrine), so a replay that reaches Act 3 in minutes
     * draws from a different pool than the player it replays, and lands on a
     * different event.
     */
    private static void executeSetPlaytimeCommand(String[] tokens) throws InvalidCommandException {
        float seconds;
        try {
            seconds = Float.parseFloat(tokens.length > 1 ? tokens[1] : "");
        }
        catch (NumberFormatException e) {
            seconds = -1.0f;
        }
        if (seconds < 0.0f) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens.length > 1 ? tokens[1] : "");
        }
        CardCrawlGame.playtime = seconds;
        GameStateListener.setTimeout(30);
    }

    /*
     * win: end the current fight as won, as if the last blow had landed.
     * Every monster still standing dies through its own die(), so death
     * triggers run and the room's own end-of-battle code builds the reward
     * screen from the run's persistent generators, exactly as a played fight
     * would. cannotLose is cleared first: while it is set (Awakened One's
     * first form, the Darklings) die() does nothing. A monster whose die()
     * still leaves it standing is marked dying outright, since the room ends
     * only once every monster isDying or isEscaping.
     */
    private static void executeWinCommand() {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        room.cannotLose = false;
        for (AbstractMonster m : room.monsters.monsters) {
            if (m.isDying || m.isDead || m.isEscaping || m.escaped) continue;
            m.halfDead = false;
            m.currentHealth = 0;
            try {
                m.die();
            } catch (RuntimeException e) {
                logger.info("win: " + m.id + ".die() threw " + e);
            }
            if (!m.isDying && !m.isDead) {
                m.isDying = true;
            }
        }
        GameStateListener.setTimeout(30);
    }

    /*
     * deck remove|upgrade|add <card id>[+<upgrades>]: change the master deck
     * directly, in the .run file's card notation ("Bash+1", "Searing
     * Blow+3"). remove and upgrade act on one copy with exactly that many
     * upgrades; add puts a fresh copy in without ShowCardAndObtainEffect, so
     * no egg relic upgrades it on the way in. For the record's deck changes
     * the builder cannot replay through the game itself (a fight's Lesson
     * Learned, a Parasite from Writhing Mass).
     */
    private static void executeDeckCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        String op = tokens[1];
        int level = -1;
        int first = 2;
        if (op.equals("level")) {
            if (tokens.length < 4) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
            }
            try {
                level = Integer.parseInt(tokens[2]);
            }
            catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
            first = 3;
        }
        String spec = String.join(" ", java.util.Arrays.copyOfRange(tokens, first, tokens.length)).trim();
        int upgrades = 0;
        int plus = spec.lastIndexOf('+');
        if (plus > 0) {
            try {
                upgrades = Integer.parseInt(spec.substring(plus + 1));
                spec = spec.substring(0, plus);
            }
            catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
            }
        }
        String id = CommandExecutor.findCardId(spec);
        if (id == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
        }
        if (op.equals("add")) {
            AbstractCard card = CardLibrary.getCard(id).makeCopy();
            for (int i = 0; i < upgrades && card.canUpgrade(); ++i) {
                card.upgrade();
            }
            AbstractDungeon.player.masterDeck.addToTop(card);
            GameStateListener.setTimeout(30);
            return;
        }
        AbstractCard found = null;
        for (AbstractCard c : AbstractDungeon.player.masterDeck.group) {
            if (!c.cardID.equals(id) || c.timesUpgraded != upgrades) continue;
            found = c;
            break;
        }
        if (found == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec + "+" + upgrades);
        }
        if (op.equals("remove")) {
            AbstractDungeon.player.masterDeck.removeCard(found);
        } else if (op.equals("level")) {
            // In place, keeping the card's position and uuid: later picks by
            // deck order (Falling, War Paint) must see the deck the streamer
            // had. A bottled card is refused, since its relic holds it.
            if (found.inBottleFlame || found.inBottleLightning || found.inBottleTornado) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec + " is bottled");
            }
            AbstractCard card = CardLibrary.getCard(id).makeCopy();
            for (int i = 0; i < level && card.canUpgrade(); ++i) {
                card.upgrade();
            }
            card.uuid = found.uuid;
            ArrayList<AbstractCard> group = AbstractDungeon.player.masterDeck.group;
            group.set(group.indexOf(found), card);
        } else if (op.equals("upgrade")) {
            found.upgrade();
            AbstractDungeon.player.bottledCardUpgradeCheck(found);
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, op);
        }
        GameStateListener.setTimeout(30);
    }

    /*
     * shopcard <slot> <card id>[+N]: put this card in the shop's coloured-card
     * slot <slot>, at the slot's position and price. For the streamer
     * benchmark's replay: with The Courier, a bought coloured card is restocked
     * by ShopScreen.purchaseCard from getCardFromPool(rollRarity(), type,
     * false), whose rarity (cardRng) and price (merchantRng) are seeded but
     * whose card is drawn with MathUtils.random, so no replay reproduces it.
     * The replacement must share the slot card's type and rarity, which are
     * the seeded part; anything else is refused.
     */
    private static void executeShopCardCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.SHOP || AbstractDungeon.shopScreen == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "not in a shop");
        }
        int slot;
        try {
            slot = Integer.parseInt(tokens[1]);
        }
        catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        ArrayList<AbstractCard> colored = AbstractDungeon.shopScreen.coloredCards;
        if (slot < 0 || slot >= colored.size()) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
        }
        String spec = String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length)).trim();
        int upgrades = 0;
        int plus = spec.lastIndexOf('+');
        if (plus > 0) {
            try {
                upgrades = Integer.parseInt(spec.substring(plus + 1));
                spec = spec.substring(0, plus);
            }
            catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
            }
        }
        String id = CommandExecutor.findCardId(spec);
        if (id == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
        }
        AbstractCard old = colored.get(slot);
        AbstractCard card = CardLibrary.getCard(id).makeCopy();
        if (card.type != old.type || card.rarity != old.rarity || card.color == AbstractCard.CardColor.COLORLESS) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT,
                    id + " is " + card.rarity + " " + card.type + ", the slot " + old.rarity + " " + old.type);
        }
        for (int i = 0; i < upgrades && card.canUpgrade(); ++i) {
            card.upgrade();
        }
        card.current_x = old.current_x;
        card.current_y = old.current_y;
        card.target_x = old.target_x;
        card.target_y = old.target_y;
        card.price = old.price;
        colored.set(slot, card);
        GameStateListener.setTimeout(30);
    }

    private static String findPotionId(String name) {
        for (String id : PotionHelper.getPotions(null, true)) {
            if (id.equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    /*
     * shoppotion <slot> <potion id>: put this potion in the shop's potion slot
     * <slot>, at the slot's price. For the streamer benchmark's replay: shop
     * potions are drawn on potionRng, which the replay's skipped fights can
     * leave out of step; the record names the stock, and this sets it.
     */
    private static void executeShopPotionCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.SHOP || AbstractDungeon.shopScreen == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "not in a shop");
        }
        int slot;
        try {
            slot = Integer.parseInt(tokens[1]);
        }
        catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        ArrayList<com.megacrit.cardcrawl.shop.StorePotion> potions = ReflectionHacks.getPrivate(
                AbstractDungeon.shopScreen, com.megacrit.cardcrawl.shop.ShopScreen.class, "potions");
        if (slot < 0 || slot >= potions.size() || potions.get(slot).isPurchased) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
        }
        String spec = String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length)).trim();
        String id = CommandExecutor.findPotionId(spec);
        if (id == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
        }
        com.megacrit.cardcrawl.shop.StorePotion old = potions.get(slot);
        AbstractPotion potion = PotionHelper.getPotion(id);
        potion.posX = old.potion.posX;
        potion.posY = old.potion.posY;
        potion.hb.move(old.potion.hb.cX, old.potion.hb.cY);
        old.potion = potion;
        GameStateListener.setTimeout(30);
    }

    /*
     * rewardpotion <index> <potion id|none>, rewardpotion add <potion id>: set
     * the potion of the combat reward at <index> (an index into the reward
     * screen's list), remove it, or add one. For the streamer benchmark's
     * replay: a fight's drop roll and its potion are drawn on potionRng, and
     * where the replay's generator is out of step the record says what the
     * fight gave.
     */
    private static void executeRewardPotionCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        if (AbstractDungeon.screen != AbstractDungeon.CurrentScreen.COMBAT_REWARD) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "not on a reward screen");
        }
        ArrayList<com.megacrit.cardcrawl.rewards.RewardItem> rewards = AbstractDungeon.combatRewardScreen.rewards;
        String spec = String.join(" ", java.util.Arrays.copyOfRange(tokens, 2, tokens.length)).trim();
        String id = spec.equals("none") ? null : CommandExecutor.findPotionId(spec);
        if (id == null && !spec.equals("none")) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
        }
        // The screen's list is a copy of the room's (reopen() copies it back
        // after a card reward), so an item is changed in place, and an item
        // added or removed is added to or removed from both.
        ArrayList<com.megacrit.cardcrawl.rewards.RewardItem> roomRewards = AbstractDungeon.getCurrRoom().rewards;
        if (tokens[1].equals("add")) {
            if (id == null) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, spec);
            }
            com.megacrit.cardcrawl.rewards.RewardItem item = new com.megacrit.cardcrawl.rewards.RewardItem(PotionHelper.getPotion(id));
            rewards.add(item);
            if (roomRewards != rewards) {
                roomRewards.add(item);
            }
        } else {
            int index;
            try {
                index = Integer.parseInt(tokens[1]);
            }
            catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
            }
            if (index < 0 || index >= rewards.size() || rewards.get(index).type != com.megacrit.cardcrawl.rewards.RewardItem.RewardType.POTION) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
            }
            com.megacrit.cardcrawl.rewards.RewardItem item = rewards.get(index);
            if (id == null) {
                rewards.remove(index);
                roomRewards.remove(item);
            } else {
                AbstractPotion potion = PotionHelper.getPotion(id);
                item.potion = potion;
                item.text = potion.name;
            }
        }
        AbstractDungeon.combatRewardScreen.positionRewards();
        GameStateListener.setTimeout(30);
    }

    private static final java.util.HashMap<String, Random> savedRngs = new java.util.HashMap<String, Random>();

    /*
     * rng save|restore <name>, rng advance <name> <n>: keep a copy of one of
     * the run's generators (AbstractDungeon.<name>Rng: monster, event,
     * merchant, card, treasure, relic, potion, misc, cardRandom, ...), put
     * the copy back, or draw from it n times. For the streamer benchmark's
     * replay, where its skipped fights draw a generator a different number of
     * times than the streamer's did (a Looter killed rolls gold the escaped
     * one did not). A generator's state cannot be stepped back, so a draw to
     * undo is undone by saving before it and restoring after.
     */
    private static void executeRngCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        String op = tokens[1];
        java.lang.reflect.Field field = null;
        for (java.lang.reflect.Field f : AbstractDungeon.class.getFields()) {
            if (f.getType() == Random.class && f.getName().equalsIgnoreCase(tokens[2] + "Rng")) field = f;
        }
        if (field == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        try {
            Random rng = (Random) field.get(null);
            if (op.equals("save")) {
                savedRngs.put(field.getName(), rng.copy());
            } else if (op.equals("restore")) {
                Random saved = savedRngs.remove(field.getName());
                if (saved == null) {
                    throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "nothing saved for " + tokens[2]);
                }
                field.set(null, saved);
            } else if (op.equals("advance") && tokens.length >= 4) {
                int n = Integer.parseInt(tokens[3]);
                for (int i = 0; i < n; ++i) {
                    rng.random(99);
                }
            } else {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, op);
            }
        }
        catch (IllegalAccessException | NumberFormatException e) {
            throw new InvalidCommandException("rng: " + e);
        }
        CommunicationMod.mustSendGameState = true;
    }

    /*
     * leaveroom: leave the current room for the map, from whatever screen it
     * is on (a shop, an event, a reward screen), as if it were finished: the
     * screen is closed, the room's rewards dropped, the room marked complete
     * and the map opened on the current node. Not in a fight. For the
     * streamer benchmark's repair of a floor whose screens are not the
     * record's: the floor is dropped and its end state set from the record.
     */
    private static void executeLeaveRoomCommand(String[] tokens) throws InvalidCommandException {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room == null || room.phase == AbstractRoom.RoomPhase.COMBAT) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "in a fight");
        }
        if (AbstractDungeon.isScreenUp) {
            AbstractDungeon.closeCurrentScreen();
        }
        leaveCurrentEvent("leaveroom");
        room.rewards.clear();
        AbstractDungeon.combatRewardScreen.rewards.clear();
        room.phase = AbstractRoom.RoomPhase.COMPLETE;
        AbstractDungeon.dungeonMapScreen.open(false);
        GameStateListener.setTimeout(30);
    }

    /*
     * neowmini: on Neow's first screen, offer the two-option mini blessing in
     * place of the talk click. NeowEvent offers it when the profile's
     * <CLASS>_SPIRITS count is 0 (the previous run ended before the Act 1
     * boss), but only to an unseeded run: a seeded start sets bossCount to 1,
     * so a replay on a streamer's seed always got the four options. Setting
     * bossCount to 0 before the click did not take (the replay still got
     * four), so this calls the event's own miniBlessing(), which the click
     * calls when bossCount is 0: the same rewards, options and screenNum (3).
     */
    private static void executeNeowMiniCommand(String[] tokens) throws InvalidCommandException {
        AbstractRoom room = AbstractDungeon.getCurrRoom();
        if (room == null || !(room.event instanceof com.megacrit.cardcrawl.neow.NeowEvent)) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "not at Neow");
        }
        int screen = (Integer) ReflectionHacks.getPrivate(room.event, com.megacrit.cardcrawl.neow.NeowEvent.class, "screenNum");
        // The first screen: 0 the first time Neow is met on a profile, 1 after
        // (the intro tip seen), 10 with the dialog skipped. Each one's click
        // opens the blessing, or with bossCount 0 the mini blessing.
        if (screen != 0 && screen != 1 && screen != 10) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, "Neow is past its first screen");
        }
        try {
            java.lang.reflect.Method mini = com.megacrit.cardcrawl.neow.NeowEvent.class.getDeclaredMethod("miniBlessing");
            mini.setAccessible(true);
            mini.invoke(room.event);
        }
        catch (ReflectiveOperationException e) {
            throw new InvalidCommandException("neowmini: " + e);
        }
        GameStateListener.setTimeout(30);
    }

    /*
     * bossseen <KEY> <0|1>: set whether the profile has seen a boss, in memory
     * (UnlockTracker.bossSeenPref; not flushed). initializeBoss forces the
     * first unseen boss of an act's fixed order and shuffles with monsterRng
     * only when all three are seen, so a replay of a run played on a fresh
     * profile needs the streamer's flags before `start`.
     */
    private static void executeBossSeenCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        String key = tokens[1].toUpperCase();
        if (!java.util.Arrays.asList("GUARDIAN", "GHOST", "SLIME", "CHAMP", "AUTOMATON", "COLLECTOR", "CROW", "DONUT", "WIZARD").contains(key)
                || !(tokens[2].equals("0") || tokens[2].equals("1"))) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1] + " " + tokens[2]);
        }
        com.megacrit.cardcrawl.unlock.UnlockTracker.bossSeenPref.putInteger(key, Integer.parseInt(tokens[2]));
        CommunicationMod.mustSendGameState = true;  // nothing on screen changes, so answer at once
    }

    /*
     * abandon: give up the run the way the pause menu's Abandon Run does
     * (ConfirmPopup ABANDON_MID_RUN): the death screen opens, and its
     * proceed returns to the main menu, ready for the next start.
     */
    private static void executeAbandonCommand() {
        AbstractDungeon.closeCurrentScreen();
        AbstractDungeon.player.isDead = true;
        AbstractDungeon.deathScreen = new com.megacrit.cardcrawl.screens.DeathScreen(AbstractDungeon.getMonsters());
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
