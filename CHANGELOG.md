# Changelog

All notable changes to The Observer will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Players whose names contain an underscore no longer have every model-written story about them rejected.
- On Minecraft 26.3, the buttons on the newsstand, the Pawstman's counter and the Exchange page now respond to clicks.
- The ends of the paper roll in the Pawstman's mouth no longer flicker.
- The sub-editor no longer turns down a story for saying "diamond" when the facts say "Diamonds!".
- Two games running the press at once no longer overwrite each other's copy of the writing engine (one could fall back to the CPU or fail).
- Emeralds can no longer be shift-clicked into a coin slot that isn't showing (before the first edition, or for a free copy); any left in a slot that closes go back to your inventory.
- The Pawstman no longer comes back with the same edition every time a player rejoins after turning him away; that copy waits at the newsstands.
- A model-written headline is never cut off part-way: it's printed whole, over as many lines as it needs, and the press asks for short, punchy headlines.

### Added

- Initial version.
- A weekly newspaper (configurable period), one edition for everyone, built from every player's vanilla statistics, deaths and advancements, with a Founding Edition covering everything before it. Players who are offline are read from the world's statistics files.
- News desk: scored candidate stories with a varied line-up, firsts (about 60 milestones), new habits and record weeks against each player's history, connections between a player's deaths and the rest of their week (crashing while gliding the week they found elytra, being killed by the creature they were hunting), opinion pieces and almanac items to fill the page. The news takes a second spread in a busy week. A boss defeat always leads the paper, and a big redstone build gets an engineering report.
- One masthead everywhere: The Observer. Players' names are printed in a dark blue so readers can find themselves.
- Reading screen styled as an open, stapled newspaper in three spreads: the news, the Exchange, and the Leaderboard with the Hall of Fame. No text is ever cut off.
- The Exchange: six notices per reader from named townsfolk of fourteen trades and the Watch, worked out from the edition and the reader's UUID, priced from vanilla trade rates, checked and paid by the server, one hand-in each until the next edition.
- Leaderboards: over 30 candidate boards plus one per creature killed, chosen each edition for activity, close races and variety, since the last edition and of all time.
- A delivery dog (red collar, the paper rolled up in its mouth) that appears out of sight, trots over, drops the paper and runs off; falls back to the inventory when there is no route. Offline players get the paper on their next join.
- The AI newsroom, an on-device writing model (llama.cpp, bundled): a singleplayer download offer, `/observer model download` for servers, a tunable press profile (`config/observer-press.json`: persona, prompt templates, examples, sampling, JSON Schema, checks) an editor pass that plans each story from per-player dossiers (angle, supporting facts, a quote from the townsfolk, earlier coverage only when it relates), several drafts per story with the best one kept, and a sub-editor on every story (schema, sanitising, grounding, policy, copying) with a fallback to the templates.
- The Pawstman, the delivery dog: always finds his reader (teleporting like a tamed wolf), takes the paper's price at his own counter (pay and take, or no thanks; free copies, the first included, are taken there too), and a turned-down or failed delivery is bought at the nearest newsstand.
- Exchange rewards: low, balanced or high, set by operators, from the next edition.
- The newsstand: a dyeable vending box, crafted from emeralds, iron, paper, any glass panes and any wooden chest, whose window shows a paper while your copy is waiting. Its counter shows the status and a live countdown to the next edition, hands out one free copy per edition, sells spares for an emerald through its coin slot, lets each player choose dog delivery or collection, and gives operators an editor's desk beside it: the paper's voice, the frequency and schedule, delivery dogs and an early publish.
- A survival flow: each player's first copy is free, players starting out always have an Exchange notice paying emeralds, the Pawstman doesn't pester empty pockets, and a newsstand is the mid-game upgrade.
- Papers can be kept on chiseled bookshelves, with their contents intact.
- Readers the Exchange didn't know about when an edition was printed (new players, or ones joining later) get their own notices when their copy reaches them.
- Daily, weekly or fortnightly editions: the wording, the "usual" comparisons, the Exchange's sizes and the schedule all follow, and issues are numbered in order, one per edition (a reprint keeps its number).
- Three voices for the paper, chosen by operators: the Broadsheet, the Roast and the Chronicle, each with its own model style, examples, house templates and masthead motto.
- GPU writing through Vulkan (Windows and Linux) or Metal (Apple Silicon), falling back to the CPU; chosen in the settings screen (Mod Menu on Fabric, the Mods screen on NeoForge).
- Letters to the editor and opinion columns written by the model, more of them in quiet weeks, and a "Did You Know?" column of over 400 facts about the game.
- `/observer`, `/observer publish`, `/observer reprint`, `/observer status`, `/observer tone`, `/observer frequency`, `/observer autopublish`, `/observer dogs`; all but the help are for operators and the owner of a singleplayer world, cheats or not.
- Scenario test suite and a readable gallery of generated editions; `/observer demo` (development runs only) moves a demonstration town into the world to try the paper out.
- Builds for Minecraft 26.1, 26.2 and 26.3, on Fabric and NeoForge.
