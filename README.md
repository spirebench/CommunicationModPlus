# CommunicationModPlus

This fork of [CommunicationMod](https://github.com/ForgottenArbiter/CommunicationMod)
is the game interface of SpireBench, an LLM agent harness for Slay the Spire.
It is read through [spirecommplus](https://github.com/spirebench/spirecommplus), our fork of spirecomm.

It builds on Orak's ModifiedCommunicationMod
([Orak](https://github.com/krafton-ai/Orak) ships it as a jar only; its
changes are reconstructed here as source, in commits authored by Orak's
committers), and adds what SpireBench needs to show an agent exactly what
happened in the game, to tell it reliably when the game is waiting for a
command, and to set up test games.

## What it adds

### Talking to the client

- **Files instead of a child process** (from Orak). Commands are read from
  `input_file_path` and each state is written to `output_file_path`, through a
  temporary file moved into place, so the client never reads half a state. The
  mod announces itself with `READY`, and waits up to 600 s for its client.
- **Crash reporting.** If the game crashes, the mod writes a crash marker to
  the output file, and publishes nothing after it, so the client knows at once
  instead of waiting on a frozen state.
- **Bounded logging.** Only the first 300 characters of each published state
  are logged, so long games no longer run out of heap.

#### Why files

Upstream launches the client as a child of the game and talks through its
stdin and stdout. Files decouple the two processes:

- Either side can start, stop or restart without the other. A harness can
  launch the game itself, survive a game crash and relaunch it, or be
  restarted and pick up the state the game is waiting on.
- The client's stdout is its own: under the stdio protocol, anything it prints
  is read as a command.
- There is no 10 s window for the client to say it is ready, and no pipe that
  stalls a writer while the other side is busy.
- The latest state is always on disk, complete, so it can be re-read,
  archived or inspected by hand, and the client can run in another container
  or on another machine with a shared filesystem.

The costs are a little latency, as both sides poll (the mod every 100 ms), and
that only the newest state is kept: a state overwritten before the client reads
it is gone. That is why the mod repeats the snapshots below on every ready state
until the next command.

### What happened between states

Upstream reports the game only when it is waiting for input, so everything the
enemies did, and everything that fired between turns, arrives as one net
change. CommunicationModPlus snapshots the combat at each boundary and sends
the snapshots with the next state, in `combat_state.intermediate_states`:

- after the player's end-of-turn effects (`post_player_turn`);
- before each monster acts (`pre_monster_turn`, with the monster's index and
  name);
- around the end of the round and the block resets (`post_monsters`,
  `pre_round_end_queue`, `pre_monster_block_reset`, `monster_block_reset`,
  `pre_player_turn`, `post_block_reset`);
- before the start-of-turn relics fire (`player_turn_start`), including at
  the start of the extra turn Vault gives;
- when the player's HP reaches 0 outside their own turn, before a revive
  (`player_death`, then `post_player_death` once the revive is done).

Each snapshot holds the player's HP, block, gold, powers, orbs and stance,
relic counters, the four card piles (names and ids), every monster's HP, block
and powers, and the cards being added to the deck at that moment
(`cards_in_flight`). A batch is repeated on every ready state until the next
command, so a state republished in between does not lose it. A turn that
arrives without snapshots carries `intermediate_states_missing`, saying why.
The buffer is cleared when each fight starts, so nothing from an earlier fight
is sent with a new one, and a fight lost on its first turn still sends its
chain.

A fight can end after the player's turn did: in the end of turn, the enemies'
turn or the next turn's start. No ready state follows those snapshots, so the
first state out of the fight carries them, as `combat_end_intermediates`,
together with how each monster left the fight, as `combat_end_monsters` (its
name, `uid`, HP, and whether it `escaped` or is `dead`): a thief that escaped
in the enemies' turn was otherwise last seen standing.

During the player's own commands, the mod also snapshots the monsters before
and after each hit (`action_snapshots`, `pre_damage`/`post_damage`), so a
play's damage, block absorbed and the monster's reaction can be told apart.
Hits on the player in their turn and its end are snapshotted the same way
(`pre_player_damage`/`post_player_damage`, and `player_death` before a
revive), and every action snapshot also carries the player's HP and block.
One taken after `post_player_turn` is tagged `after_player_turn`.

### More in each state

- **Descriptions** (from Orak) of cards, with their current numbers filled in,
  and of powers and relics. Power text is refreshed before it is read.
- **Cards:** `is_glowing` (the gold border of a card with a synergy right now),
  `bottled` (the Bottled relic holding it), and `free_to_play_once` on a card
  that Setup or Forethought made free until played, which its `cost` does not
  show. Hand cards' damage and block are recomputed against the player's
  current powers before they are sent.
- **Combat:** `cards_played_this_turn`, the game's own count of the turn's
  plays, copies included (a Double Tap copy that fizzled is not one).
- **Monsters:** a `uid` that stays the same for a monster's whole life, so two
  identical monsters can be told apart after a spawn shifts them. No monster
  is reported with the placeholder DEBUG intent (from Orak).
- **Player and map:** `stance`, and `has_emerald_key` on the map node of the
  burning elite.
- **Potions:** `requires_target` reports whether the potion asks for a target,
  rather than whether it is thrown (Explosive Potion is thrown but hits every
  enemy).
- **Events:** the event's body text when the dialog has not shown it yet, and
  in Match and Keep, the face-down cards by position and the last card
  flipped (`last_flip`).

### Telling when the game is ready

A state is published as ready only once it has settled. The mod holds it while:

- a card, key or potion being obtained has not yet landed;
- a hand picker has just closed and its action has not drained;
- a Smoke Bomb escape is playing out.

It also reports a new card reward screen or card grid that replaces another
with nothing published in between, and a pick on a grid while the turn is
ending early. Without these, the game could look unchanged and never come
ready.

### Commands

- `continue <class>` resumes the saved run of a character.
- A potion cannot be used while a selection screen is open in combat (the
  game would queue it behind the pick and never come ready).
- Leaving a reward screen opened in a shop forfeits what is left on it, so it
  does not reopen later.
- First-time-user tips, including the Calling Bell nag, are dismissed, and
  Neow's blessing is shown on a fresh profile.
- Test-only commands, not listed in `available_commands`, set up games to
  try particular situations: `relic <id>`, `potion <id>`, `card <id>
  [upgrades]`, `maxhp <n>`, `keys <ruby|emerald|sapphire|all>`,
  `act <2|3|4>` and `floor <n>`.

## Building

Put the game's and the mod loaders' jars in a `lib/` directory beside this
repository, as upstream does:

```
lib/desktop-1.0.jar     # from the Slay the Spire install
lib/ModTheSpire.jar     # Steam Workshop item 1605060445
lib/BaseMod.jar         # Steam Workshop item 1605833019
CommunicationMod/       # this repository
```

Then run `mvn package` (JDK 8 or later). The jar is written to
`target/CommunicationModPlus.jar` and copied to `../_ModTheSpire/mods/`, with
gson shaded in as `com.autoplay.gson`. Copy it into the game's `mods/`
directory. The mod needs BaseMod.

## Configuration

The config directory is `CommunicationModPlus` (settings from an old
`CommunicationMod` directory are copied over on first launch):

```
input_file_path=/path/to/comm_input.txt
output_file_path=/path/to/comm_output.txt
runAtGameStart=true
```

The client writes one command at a time to the input file, and reads each
published state from the output file. A state is written to a temporary file
and then moved into place, so the client never reads half of one. The `command`
setting and the stdin/stdout protocol below are no longer used.

## Reading the state from Python

[spirecommplus](https://github.com/spirebench/spirecommplus), the companion
fork of spirecomm, parses CommunicationModPlus's states into Python objects,
including every field listed above (`spirecommplus.spire.game.Game.from_json`).
Its `Coordinator` still uses the stdin/stdout protocol, so pair the parsers
with a client that reads and writes the two files.

The original README follows.

---

# CommunicationMod
Slay the Spire mod that provides a protocol for allowing another process to control the game

## Requirements

- Slay the Spire
- ModTheSpire (https://github.com/kiooeht/ModTheSpire)
- BaseMod (https://github.com/daviscook477/BaseMod)

## Setup

1. Copy CommunicationMod.jar to your ModTheSpire mods directory
2. Run ModTheSpire with CommunicationMod enabled
3. Edit your newly-created SpireConfig file with the command you want to use with CommunicationMod (see https://github.com/kiooeht/ModTheSpire/wiki/SpireConfig for the location of your config file). Your config file should look something like this (note that certain special characters must be escaped):
```
#Sat Apr 20 02:49:10 CDT 2019
command=python C\:\\Path\\To\\Script\\main.py
```

## What does this mod do?

CommunicationMod launches a specified process and communicates with this process through stdin and stdout, with the following protocol:

(Note: all messages are assumed to be ended by a new line '\n')

- After starting the external process, CommunicationMod waits for the process to send "ready" on stdout. If "ready" is not received before a specified timeout, the external process will be terminated.
- Whenever the state of the game is determined to be stable (no longer changing without external input), CommunicationMod sends a message containing the JSON representation of the current game state to the external process's stdin. For example:
```
{"available_commands":["play","end","key","click","wait","state"],"ready_for_command":true,"in_game":true,"game_state":{"screen_type":"NONE","screen_state":{},"seed":-3047511808784702860,"combat_state":{"draw_pile":[{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"0560233c-41e8-4620-a474-d0ed627354bd","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"f8adc2a6-4d1c-4524-9044-9e2bfacf4256","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"c6594538-debc-4085-81be-3b20a5d44062","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":false,"cost":-2,"name":"Ascender\u0027s Bane","id":"AscendersBane","type":"CURSE","ethereal":true,"uuid":"da41cd4b-6eda-4020-a031-ad870a52b0e1","upgrades":0,"rarity":"SPECIAL","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"b54d5d98-f074-4f71-b705-f071f1d44fff","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"9c10951d-0c08-46bd-bc5f-be2e1b9d53f2","upgrades":0,"rarity":"BASIC","has_target":false}],"discard_pile":[],"exhaust_pile":[],"cards_discarded_this_turn":0,"times_damaged":0,"monsters":[{"is_gone":false,"move_hits":1,"move_base_damage":12,"half_dead":false,"move_adjusted_damage":-1,"max_hp":46,"intent":"DEBUG","move_id":1,"name":"Jaw Worm","current_hp":1,"block":0,"id":"JawWorm","powers":[]}],"turn":1,"limbo":[],"hand":[{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"7b54caef-9c56-4134-82a2-be8f1d5c435f","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"5f9bba1a-4c54-4be7-b387-1992937c5717","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"0dbea551-c9ae-4228-8821-74e2ffd04889","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"612c16f7-f8f7-4253-88bb-ab4813d34b69","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":2,"name":"Bash","id":"Bash","type":"ATTACK","ethereal":false,"uuid":"41e3754b-d2e3-40b4-a83b-f165b1943ec3","upgrades":0,"rarity":"BASIC","has_target":true}],"player":{"orbs":[],"current_hp":68,"block":0,"max_hp":75,"powers":[],"energy":3}},"deck":[{"exhausts":false,"is_playable":false,"cost":-2,"name":"Ascender\u0027s Bane","id":"AscendersBane","type":"CURSE","ethereal":true,"uuid":"da41cd4b-6eda-4020-a031-ad870a52b0e1","upgrades":0,"rarity":"SPECIAL","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"7b54caef-9c56-4134-82a2-be8f1d5c435f","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"c6594538-debc-4085-81be-3b20a5d44062","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"5f9bba1a-4c54-4be7-b387-1992937c5717","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"0560233c-41e8-4620-a474-d0ed627354bd","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Strike","id":"Strike_R","type":"ATTACK","ethereal":false,"uuid":"b54d5d98-f074-4f71-b705-f071f1d44fff","upgrades":0,"rarity":"BASIC","has_target":true},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"9c10951d-0c08-46bd-bc5f-be2e1b9d53f2","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"0dbea551-c9ae-4228-8821-74e2ffd04889","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"612c16f7-f8f7-4253-88bb-ab4813d34b69","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":1,"name":"Defend","id":"Defend_R","type":"SKILL","ethereal":false,"uuid":"f8adc2a6-4d1c-4524-9044-9e2bfacf4256","upgrades":0,"rarity":"BASIC","has_target":false},{"exhausts":false,"is_playable":true,"cost":2,"name":"Bash","id":"Bash","type":"ATTACK","ethereal":false,"uuid":"41e3754b-d2e3-40b4-a83b-f165b1943ec3","upgrades":0,"rarity":"BASIC","has_target":true}],"relics":[{"name":"Burning Blood","id":"Burning Blood","counter":-1},{"name":"Neow\u0027s Lament","id":"NeowsBlessing","counter":2}],"max_hp":75,"act_boss":"The Guardian","gold":99,"action_phase":"WAITING_ON_USER","act":1,"screen_name":"NONE","room_phase":"COMBAT","is_screen_up":false,"potions":[{"requires_target":false,"can_use":false,"can_discard":false,"name":"Potion Slot","id":"Potion Slot"},{"requires_target":false,"can_use":false,"can_discard":false,"name":"Potion Slot","id":"Potion Slot"}],"current_hp":68,"floor":1,"ascension_level":20,"class":"IRONCLAD","map":[{"symbol":"M","children":[{"x":0,"y":1}],"x":1,"y":0,"parents":[]},{"symbol":"M","children":[{"x":2,"y":1}],"x":2,"y":0,"parents":[]},{"symbol":"M","children":[{"x":4,"y":1}],"x":3,"y":0,"parents":[]},{"symbol":"M","children":[{"x":5,"y":1}],"x":6,"y":0,"parents":[]},{"symbol":"M","children":[{"x":1,"y":2}],"x":0,"y":1,"parents":[]},{"symbol":"M","children":[{"x":1,"y":2},{"x":2,"y":2}],"x":2,"y":1,"parents":[]},{"symbol":"?","children":[{"x":3,"y":2}],"x":4,"y":1,"parents":[]},{"symbol":"$","children":[{"x":4,"y":2}],"x":5,"y":1,"parents":[]},{"symbol":"M","children":[{"x":1,"y":3},{"x":2,"y":3}],"x":1,"y":2,"parents":[]},{"symbol":"?","children":[{"x":2,"y":3},{"x":3,"y":3}],"x":2,"y":2,"parents":[]},{"symbol":"M","children":[{"x":3,"y":3}],"x":3,"y":2,"parents":[]},{"symbol":"M","children":[{"x":5,"y":3}],"x":4,"y":2,"parents":[]},{"symbol":"?","children":[{"x":1,"y":4}],"x":1,"y":3,"parents":[]},{"symbol":"M","children":[{"x":3,"y":4}],"x":2,"y":3,"parents":[]},{"symbol":"?","children":[{"x":3,"y":4}],"x":3,"y":3,"parents":[]},{"symbol":"M","children":[{"x":4,"y":4}],"x":5,"y":3,"parents":[]},{"symbol":"M","children":[{"x":1,"y":5}],"x":1,"y":4,"parents":[]},{"symbol":"?","children":[{"x":2,"y":5},{"x":3,"y":5}],"x":3,"y":4,"parents":[]},{"symbol":"M","children":[{"x":3,"y":5}],"x":4,"y":4,"parents":[]},{"symbol":"E","children":[{"x":1,"y":6}],"x":1,"y":5,"parents":[]},{"symbol":"E","children":[{"x":1,"y":6},{"x":2,"y":6}],"x":2,"y":5,"parents":[]},{"symbol":"R","children":[{"x":2,"y":6},{"x":3,"y":6}],"x":3,"y":5,"parents":[]},{"symbol":"R","children":[{"x":2,"y":7}],"x":1,"y":6,"parents":[]},{"symbol":"M","children":[{"x":2,"y":7},{"x":3,"y":7}],"x":2,"y":6,"parents":[]},{"symbol":"E","children":[{"x":3,"y":7}],"x":3,"y":6,"parents":[]},{"symbol":"E","children":[{"x":1,"y":8},{"x":2,"y":8},{"x":3,"y":8}],"x":2,"y":7,"parents":[]},{"symbol":"R","children":[{"x":3,"y":8}],"x":3,"y":7,"parents":[]},{"symbol":"T","children":[{"x":0,"y":9}],"x":1,"y":8,"parents":[]},{"symbol":"T","children":[{"x":1,"y":9}],"x":2,"y":8,"parents":[]},{"symbol":"T","children":[{"x":2,"y":9},{"x":3,"y":9},{"x":4,"y":9}],"x":3,"y":8,"parents":[]},{"symbol":"R","children":[{"x":1,"y":10}],"x":0,"y":9,"parents":[]},{"symbol":"M","children":[{"x":1,"y":10}],"x":1,"y":9,"parents":[]},{"symbol":"M","children":[{"x":1,"y":10},{"x":3,"y":10}],"x":2,"y":9,"parents":[]},{"symbol":"R","children":[{"x":4,"y":10}],"x":3,"y":9,"parents":[]},{"symbol":"?","children":[{"x":4,"y":10}],"x":4,"y":9,"parents":[]},{"symbol":"?","children":[{"x":0,"y":11},{"x":1,"y":11}],"x":1,"y":10,"parents":[]},{"symbol":"R","children":[{"x":3,"y":11}],"x":3,"y":10,"parents":[]},{"symbol":"E","children":[{"x":3,"y":11},{"x":4,"y":11}],"x":4,"y":10,"parents":[]},{"symbol":"$","children":[{"x":0,"y":12}],"x":0,"y":11,"parents":[]},{"symbol":"M","children":[{"x":1,"y":12}],"x":1,"y":11,"parents":[]},{"symbol":"M","children":[{"x":3,"y":12},{"x":4,"y":12}],"x":3,"y":11,"parents":[]},{"symbol":"?","children":[{"x":4,"y":12}],"x":4,"y":11,"parents":[]},{"symbol":"E","children":[{"x":0,"y":13}],"x":0,"y":12,"parents":[]},{"symbol":"M","children":[{"x":1,"y":13}],"x":1,"y":12,"parents":[]},{"symbol":"?","children":[{"x":3,"y":13}],"x":3,"y":12,"parents":[]},{"symbol":"M","children":[{"x":3,"y":13}],"x":4,"y":12,"parents":[]},{"symbol":"?","children":[{"x":1,"y":14}],"x":0,"y":13,"parents":[]},{"symbol":"M","children":[{"x":1,"y":14}],"x":1,"y":13,"parents":[]},{"symbol":"?","children":[{"x":2,"y":14},{"x":3,"y":14}],"x":3,"y":13,"parents":[]},{"symbol":"R","children":[{"x":3,"y":16}],"x":1,"y":14,"parents":[]},{"symbol":"R","children":[{"x":3,"y":16}],"x":2,"y":14,"parents":[]},{"symbol":"R","children":[{"x":3,"y":16}],"x":3,"y":14,"parents":[]}],"room_type":"MonsterRoom"}}
```
- CommunicationMod then waits for a message back from the external process, containing a command to be executed. Possible commands are:
  - START PlayerClass [AscensionLevel] [Seed]
    - Starts a new game with the selected class, on the selected Ascension level (default 0), with the selected seed (random seed if omitted).
    - Seeds are alphanumeric, as displayed in game.
    - This and all commands are case insensitive.
    - Only currently available in the main menu of the game.
  - POTION Use|Discard PotionSlot [TargetIndex]
    - Uses or discards the potion in the selected slot, on the selected target, if necessary.
    - TargetIndex is the index of the target monster in the game's monster array (0-indexed).
    - Only available when potions can be used or discarded.
  - PLAY CardIndex [TargetIndex]
    - Plays the selected card in your hand, with the selected target, if necessary.
    - Only available when cards can be played in combat.
    - Currently, CardIndex is 1-indexed to match up with the card numbers in game.
  - END
    - Ends your turn.
    - Only available when the end turn button is available, in combat.
  - CHOOSE ChoiceIndex|ChoiceName
    - Makes a choice relevant to the current screen.
    - A list of names for each choice is provided in the game state. If provided with a name, the first choice index with the matching name is selected.
    - Generally, available at any point when PLAY is not available.
  - PROCEED
    - Clicks the button on the right side of the screen, generally causing the game to proceed to a new screen.
    - Equivalent to CONFIRM.
    - Available whenever the proceed or confirm button is present on the right side of the screen.
  - RETURN
    - Clicks the button on the left side of the screen, generally causing you to return to the previous screen.
    - Equivalent to SKIP, CANCEL, and LEAVE.
    - Available whenever the return, cancel, or leave buttons are present on the left side of the screen. Also used for the skip button on card reward screens.
  - KEY Keyname [Timeout]
    - Presses the key corresponding to Keyname
    - Possible keynames are: Confirm, Cancel, Map, Deck, Draw_Pile, Discard_Pile, Exhaust_Pile, End_Turn, Up, Down, Left, Right, Drop_Card, Card_1, Card_2, ..., Card_10
    - The actual keys pressed depend on the corresponding mapping in the game options
    - If no state change is detected after [Timeout] frames (default 100), Communication Mod will then transmit the new state and accept input from the game. This is useful for keypresses that open menus or pick up cards, without affecting the state as detected by Communication Mod.
    - Only available in a run (not the main menus)
  - CLICK Left|Right X Y
    - Clicks the selected mouse button at the specified (X,Y) coordinates
    - (0,0) is the upper left corner of the screen, and (1920,1080) is the lower right corner, regardless of game resolution
    - Will move your cursor to the specified coordindates
    - Timeout works the same as the CLICK command
    - Only available in a run
  - WAIT Timeout
    - Waits for the specified number of frames or until a state change is detected, then transmits the current game state (same behavior as Timeout for the CLICK and KEY commands, but no input is sent to the game)
    - Possibly useful for KEY and CLICK commands which are expected to produce multiple state changes as detected by Communication Mod
    - Only available in a run
  - STATE
    - Causes CommunicationMod to immediately send a JSON representation of the current state to the external process, whether or not the game state is stable.
    - Always available.
- Upon receiving a command, CommunicationMod will execute it, and reply again with a JSON representation of the state of the game, when it is next stable.
- If there was an error in executing the command, CommunicationMod will instead send an error message of the form:
```
{"error":"Error message","ready_for_command":True}
```

## Known issues and limitations, to be hopefully fixed soon:
- The full state of the Match and Keep event is not transmitted.
- There is no feedback or state change if you attempt to take or buy a potion while your potion inventory is full. Beware!
- Unselecting cards in hand select screens is not supported.
- Several actions do not currently register a state change if they are performed manually in game.
- You must manually edit the mod's config file to set the command for your external process.
- Communication Mod has not been tested without fast mode on.

## What are some of the potential applications of this mod?

- Twitch plays Slay the Spire
- Slay the Spire AIs
- Streamers can display detailed information about the current run while in game

## Frequently asked questions

- How do I debug my process?

Communication Mod captures both the stderr and stdout of the external process. All messages sent to stdout are included in the game log, which is displayed in a window by ModTheSpire. All messages sent to the process by Communication Mod are also visible in the game log. The stdout of the external process is logged in a file named communication_mod_errors.log. Instead of printing debug information to stdout, try using a log file.

- When I start the external process, the game hangs for 10 seconds, and then the external process quits. What do I do?

Communication Mod is probably not receiving a ready signal. Make sure your process sends "Ready\n" to stdout when it is ready to receive commands from Communication Mod. If this is not the problem, there is likely some issue with the command used to start the process. Check communication_mod_errors.log to help debug these kinds of issues.

- Can I get some example code to help get started with the Communication Mod protocol?

Try looking at [spirecomm](https://github.com/ForgottenArbiter/spirecomm), the Python package I wrote to interface with Communication Mod.
