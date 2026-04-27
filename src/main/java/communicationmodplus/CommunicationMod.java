package communicationmodplus;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreMonsterTurnSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.google.gson.Gson;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.helpers.TipTracker;
import communicationmodplus.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpireInitializer
public class CommunicationMod implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber, PreMonsterTurnSubscriber, OnStateChangeSubscriber {

    private static Process listener;
    private static StringBuilder inputBuffer = new StringBuilder();
    public static boolean messageReceived = false;
    private static final Logger logger = LogManager.getLogger(CommunicationMod.class.getName());
    private static Thread writeThread;
    private static BlockingQueue<String> writeQueue;
    private static Thread readThread;
    private static BlockingQueue<String> readQueue;
    private static final String MODNAME = "Communication Mod";
    private static final String AUTHOR = "Forgotten Arbiter";
    private static final String DESCRIPTION = "This mod communicates with an external program to play Slay the Spire.";
    public static boolean mustSendGameState = false;
    private static ArrayList<OnStateChangeSubscriber> onStateChangeSubscribers;

    private static SpireConfig communicationConfig;
    private static final String COMMAND_OPTION = "command";
    private static final String GAME_START_OPTION = "runAtGameStart";
    private static final String VERBOSE_OPTION = "verbose";
    private static final String INITIALIZATION_TIMEOUT_OPTION = "maxInitializationTimeout";
    private static final String DEFAULT_COMMAND = "";
    private static final long DEFAULT_TIMEOUT = 600L;
    private static final boolean DEFAULT_VERBOSITY = true;
    private static final String INPUT_FILE_OPTION = "input_file_path";
    private static final String OUTPUT_FILE_OPTION = "output_file_path";
    private static final String DEFAULT_INPUT_FILE = "communication_mod_input.txt";
    private static final String DEFAULT_OUTPUT_FILE = "communication_mod_output.txt";

    private static String getInputFilePath() {
        if (communicationConfig == null) {
            return DEFAULT_INPUT_FILE;
        }
        return communicationConfig.getString(INPUT_FILE_OPTION);
    }

    private static String getOutputFilePath() {
        if (communicationConfig == null) {
            return DEFAULT_OUTPUT_FILE;
        }
        return communicationConfig.getString(OUTPUT_FILE_OPTION);
    }

    public CommunicationMod(){
        BaseMod.subscribe(this);
        onStateChangeSubscribers = new ArrayList<>();
        CommunicationMod.subscribe(this);
        readQueue = new LinkedBlockingQueue<>();
        try {
            Properties defaults = new Properties();
            defaults.put(GAME_START_OPTION, Boolean.toString(false));
            defaults.put(INITIALIZATION_TIMEOUT_OPTION, Long.toString(DEFAULT_TIMEOUT));
            defaults.put(VERBOSE_OPTION, Boolean.toString(DEFAULT_VERBOSITY));
            communicationConfig = new SpireConfig("CommunicationModPlus", "config", defaults);
            // One-time migration from the upstream CommunicationMod config dir.
            // Earlier builds of this fork (and vanilla CommunicationMod) wrote
            // ~/.config/ModTheSpire/CommunicationMod/config.properties; on
            // first launch with the new dir we copy any user-set keys forward
            // so paths and runAtGameStart don't silently revert to defaults.
            try {
                SpireConfig oldConfig = new SpireConfig("CommunicationMod", "config");
                boolean migrated = false;
                for (String key : new String[]{INPUT_FILE_OPTION, OUTPUT_FILE_OPTION, COMMAND_OPTION}) {
                    if (oldConfig.has(key) && !communicationConfig.has(key)) {
                        communicationConfig.setString(key, oldConfig.getString(key));
                        migrated = true;
                    }
                }
                if (oldConfig.has(GAME_START_OPTION) && !communicationConfig.has(GAME_START_OPTION)) {
                    communicationConfig.setBool(GAME_START_OPTION, oldConfig.getBool(GAME_START_OPTION));
                    migrated = true;
                }
                if (oldConfig.has(VERBOSE_OPTION) && !communicationConfig.has(VERBOSE_OPTION)) {
                    communicationConfig.setBool(VERBOSE_OPTION, oldConfig.getBool(VERBOSE_OPTION));
                    migrated = true;
                }
                if (oldConfig.has(INITIALIZATION_TIMEOUT_OPTION) && !communicationConfig.has(INITIALIZATION_TIMEOUT_OPTION)) {
                    communicationConfig.setInt(INITIALIZATION_TIMEOUT_OPTION, oldConfig.getInt(INITIALIZATION_TIMEOUT_OPTION));
                    migrated = true;
                }
                if (migrated) {
                    logger.info("Migrated config from CommunicationMod/ to CommunicationModPlus/.");
                }
            }
            catch (IOException e) {
                // No legacy config to migrate from - that's fine.
            }
            String command = communicationConfig.getString(COMMAND_OPTION);
            // I want this to always be saved to the file so people can set it more easily.
            if (command == null) {
                communicationConfig.setString(COMMAND_OPTION, DEFAULT_COMMAND);
                communicationConfig.save();
            }
            communicationConfig.save();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(getRunOnGameStartOption()) {
            boolean success = startExternalProcess();
        }
    }

    public static void initialize() {
        CommunicationMod mod = new CommunicationMod();
    }

    public void receivePreUpdate() {
        if(listener != null && !listener.isAlive() && writeThread != null && writeThread.isAlive()) {
            logger.info("Child process has died...");
            writeThread.interrupt();
            readThread.interrupt();
        }
        if(messageAvailable()) {
            try {
                boolean stateChanged = CommandExecutor.executeCommand(readMessage());
                if(stateChanged) {
                    GameStateListener.registerCommandExecution();
                }
            } catch (InvalidCommandException e) {
                HashMap<String, Object> jsonError = new HashMap<>();
                jsonError.put("error", e.getMessage());
                jsonError.put("ready_for_command", GameStateListener.isWaitingForCommand());
                Gson gson = new Gson();
                sendMessage(gson.toJson(jsonError));
            }
        }
    }

    public static void subscribe(OnStateChangeSubscriber sub) {
        onStateChangeSubscribers.add(sub);
    }

    public static void publishOnGameStateChange() {
        for(OnStateChangeSubscriber sub : onStateChangeSubscribers) {
            sub.receiveOnStateChange();
        }
    }

    public void receiveOnStateChange() {
        sendGameState();
    }

    public static void queueCommand(String command) {
        readQueue.add(command);
    }

    public void receivePostInitialize() {
        setUpOptionsMenu();
        // Suppress first-time-user tutorial popups. Fresh profiles (no
        // preferences/STSTips) otherwise stall on the first-combat tip:
        // AbstractDungeon.screen becomes FTUE with isScreenUp=true, no
        // game commands are exposed, and the external agent polls state
        // forever. Writing a pre-dismissed STSTips file doesn't work
        // reliably because the game's Prefs layer writes back to the
        // same file during startup; calling disableAllFtues() here sets
        // every tip key to true in-memory at mod load, before the first
        // dungeon is entered.
        TipTracker.disableAllFtues();
        // disableAllFtues() does NOT cover NEOW_SKIP or NEOW_INTRO, and
        // both default to false on fresh profiles. With NEOW_SKIP=false,
        // Exordium's constructor places an EmptyRoom (not NeowRoom) at
        // currMapNode and CardCrawlGame.update() calls
        // dungeonMapScreen.open(true) immediately on dungeon entry —
        // skipping Neow's blessing dialog entirely and dropping the
        // player onto Act 1's map with neow_bonus='' and
        // chose_neow_reward=false. This matters here because every
        // experiment run wipes its ephemeral worker dir (and thus the
        // STSTips prefs file), so without this every run is effectively
        // a "first-ever" profile that skips Neow.
        TipTracker.neverShowAgain("NEOW_SKIP");
        TipTracker.neverShowAgain("NEOW_INTRO");
        logger.info("Disabled all FTUE tips and Neow skip/intro markers (fresh-profile workaround).");
    }

    public void receivePostUpdate() {
        if(!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if(mustSendGameState) {
            publishOnGameStateChange();
            mustSendGameState = false;
        }
        InputActionPatch.doKeypress = false;
    }

    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
        }
        if(AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    public boolean receivePreMonsterTurn(AbstractMonster m) {
        // Capture a per-monster intermediate snapshot for the agent's
        // per-enemy action attribution. BaseMod fires this callback
        // once per monster, just before each monster's takeTurn().
        // This replaces the fragile @SpireInsertPatch on getNextAction()
        // which broke when other mods injected code with takeTurn references.
        int index = -1;
        if (AbstractDungeon.getCurrRoom() != null && AbstractDungeon.getCurrRoom().monsters != null) {
            java.util.ArrayList monsters = AbstractDungeon.getCurrRoom().monsters.monsters;
            for (int i = 0; i < monsters.size(); i++) {
                if (monsters.get(i) == m) {
                    index = i;
                    break;
                }
            }
        }
        GameStateListener.captureIntermediateState("pre_monster_turn", index, m.name);
        return true;
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();
        ModLabeledToggleButton gameStartOptionButton = new ModLabeledToggleButton(
                "Start external process at game launch",
                350, 550, Settings.CREAM_COLOR, FontHelper.charDescFont,
                getRunOnGameStartOption(), settingsPanel, modLabel -> {},
                modToggleButton -> {
                    if (communicationConfig != null) {
                        communicationConfig.setBool(GAME_START_OPTION, modToggleButton.enabled);
                        try {
                            communicationConfig.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
        settingsPanel.addUIElement(gameStartOptionButton);

        ModLabel externalCommandLabel = new ModLabel(
                "", 350, 600, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    modLabel.text = String.format("External Process Command: %s", getSubprocessCommandString());
                });
        settingsPanel.addUIElement(externalCommandLabel);

        ModButton startProcessButton = new ModButton(
                350, 650, settingsPanel, modButton -> {
                    BaseMod.modSettingsUp = false;
                    startExternalProcess();
                });
        settingsPanel.addUIElement(startProcessButton);

        ModLabel startProcessLabel = new ModLabel(
                "(Re)start external process",
                475, 700, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {
                    if(listener != null && listener.isAlive()) {
                        modLabel.text = "Restart external process";
                    } else {
                        modLabel.text = "Start external process";
                    }
                });
        settingsPanel.addUIElement(startProcessLabel);

        ModButton editProcessButton = new ModButton(
                850, 650, settingsPanel, modButton -> {});
        settingsPanel.addUIElement(editProcessButton);

        ModLabel editProcessLabel = new ModLabel(
                "Set command (not implemented)",
                975, 700, Settings.CREAM_COLOR, FontHelper.charDescFont,
                settingsPanel, modLabel -> {});
        settingsPanel.addUIElement(editProcessLabel);

        ModLabeledToggleButton verbosityOption = new ModLabeledToggleButton(
                "Suppress verbose log output",
                350, 500, Settings.CREAM_COLOR, FontHelper.charDescFont,
                getVerbosityOption(), settingsPanel, modLabel -> {},
                modToggleButton -> {
                    if (communicationConfig != null) {
                        communicationConfig.setBool(VERBOSE_OPTION, modToggleButton.enabled);
                        try {
                            communicationConfig.save();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
        settingsPanel.addUIElement(verbosityOption);
        BaseMod.registerModBadge(ImageMaster.loadImage("Icon.png"),"Communication Mod", "Forgotten Arbiter", null, settingsPanel);
    }

    private void startCommunicationThreads() {
        writeQueue = new LinkedBlockingQueue<>();
        readQueue = new LinkedBlockingQueue<>();
        writeThread = new Thread(new FileDataWriter(writeQueue, getOutputFilePath(), getVerbosityOption()));
        writeThread.start();
        readThread = new Thread(new FileDataReader(readQueue, getInputFilePath(), getVerbosityOption()));
        readThread.start();
    }

    private static void sendGameState() {
        String state = GameStateConverter.getCommunicationState();
        sendMessage(state);
    }

    public static void dispose() {
        logger.info("Shutting down communication threads...");
        if(readThread != null) {
            readThread.interrupt();
        }
        if(writeThread != null) {
            writeThread.interrupt();
        }
    }

    private static void sendMessage(String message) {
        if(writeQueue != null && writeThread.isAlive()) {
            writeQueue.add(message);
        }
    }

    private static boolean messageAvailable() {
        return readQueue != null && !readQueue.isEmpty();
    }

    private static String readMessage() {
        if(messageAvailable()) {
            return readQueue.remove();
        } else {
            return null;
        }
    }

    private static String readMessageBlocking() {
        try {
            return readQueue.poll(getInitializationTimeoutOption(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to read message from subprocess.");
        }
    }

    private static String[] getSubprocessCommand() {
        if (communicationConfig == null) {
            return new String[0];
        }
        return communicationConfig.getString(COMMAND_OPTION).trim().split("\\s+");
    }

    private static String getSubprocessCommandString() {
        if (communicationConfig == null) {
            return "";
        }
        return communicationConfig.getString(COMMAND_OPTION).trim();
    }

    private static boolean getRunOnGameStartOption() {
        if (communicationConfig == null) {
            return false;
        }
        return communicationConfig.getBool(GAME_START_OPTION);
    }

    private static long getInitializationTimeoutOption() {
        if (communicationConfig == null) {
            return DEFAULT_TIMEOUT;
        }
        return (long)communicationConfig.getInt(INITIALIZATION_TIMEOUT_OPTION);
    }

    private static boolean getVerbosityOption() {
        if (communicationConfig == null) {
            return DEFAULT_VERBOSITY;
        }
        return communicationConfig.getBool(VERBOSE_OPTION);
    }

    private boolean startExternalProcess() {
        if(readThread != null) {
            readThread.interrupt();
        }
        if(writeThread != null) {
            writeThread.interrupt();
        }
        startCommunicationThreads();
        if (GameStateListener.isWaitingForCommand()) {
            mustSendGameState = true;
        }
        // There is no external process to wait for: the client reads the output file when it is ready.
        sendMessage("READY");
        return true;
    }

}
