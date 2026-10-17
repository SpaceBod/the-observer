# Herald

A weekly newspaper about the players in your world. Every seven in-game days, the Herald writes up what actually happened (who died and how, who found diamonds, who took up farming, who finally beat the Ender Dragon), posts work from the townsfolk, ranks everyone on the leaderboards, and a delivery dog brings a copy to every player.

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Overview

- **Side:** Both. The server writes and delivers the paper; clients need the mod for the newspaper item, the delivery dog and the reading screen.
- **Minecraft:** 26.1, 26.2, 26.3
- **Loader:** Fabric (Loader ≥ 0.19.5 + Fabric API) or NeoForge
- **Optional:** a small AI model that writes the stories on your own computer, offline (see [The press](#the-press-optional-ai-writing)).

## The paper

Use the paper to open it: three spreads of a stapled newspaper.

1. **The news (pages 1-2).** A masthead named after your world, the lead story across the front page, more stories in columns, an "In Brief" column, then opinion pieces and almanac items to fill the page. Page 2's sidebar has the obituaries, the town's numbers, the weather and the small ads. Nothing is ever cut off: a story only goes in if it fits whole.
2. **The Exchange (pages 3-4).** Six notices for whoever is reading: four deliveries for townsfolk of different trades ("WANTED: 128 wheat") and two jobs for the Watch ("Deal with 12 zombies"). Each reader sees their own, worked out from the edition and their player ID. Progress shows live; "Hand in" or "Claim" when it's done; one each, until the next edition.
3. **The Leaderboard and the Hall of Fame (pages 5-6).** Eight rankings of who led the town at what since the last edition, and eight all-time records. Picked fresh each edition from over 30 boards, plus a "Top Hunter" board for every creature anyone killed: busy boards and close races first, and boards that ran last time get a rest.

## What makes the news

Everything comes from the statistics the game keeps anyway, compared edition by edition, plus deaths and advancements as they happen:

- **Stories** for the big things: deaths (in the game's own words), advancements, boss kills, mining hauls (diamonds lead), building, travel, farming, trading, raids, monster hunts, and a few sillier ones for slow weeks.
- **Firsts.** About 60 milestones, spotted the edition they happen: the first diamond pickaxe, first steps in the Nether, the first enchantment, a portal lit, an elytra picked up.
- **Habits and record weeks.** Twenty-odd pursuits (farming, forestry, redstone, cooking, fishing, sailing, music...) measured against each player's own usual week: "took up redstone", or "a record week: 420 crops planted, usually about 40".
- **Every player who has ever played**, online or not, read from the world's statistics files, so the first paper on an existing world covers everyone.
- **A varied line-up.** Every candidate story is scored; the biggest leads, and no one player or kind of story takes over the paper.

## The Exchange

- **Townsfolk with names:** each notice is posted by someone with a trade (Farmer, Mason, Fisherman, Cleric, Librarian, Armourer, Shepherd, Fletcher, Leatherworker, Butcher, Cartographer, Toolsmith, Forester, Baker) from a town that ranges from Little Ashcombe to Gloamwood. Now and then a famous face turns up in a job that suits them.
- **Fair pay:** priced from vanilla villager trade rates, at the generous end, in week-sized amounts. Some pay in goods instead of emeralds.
- **Suited to you:** nobody is asked for blaze rods before they've been to the Nether.
- **Checked by the server:** deliveries are taken from your inventory, Watch jobs count kills since the paper was printed. Finished work makes the next edition's news.

## Delivered by hand

A delivery dog in a red collar appears somewhere you can't see it, trots over with the paper rolled up in its mouth, drops it at your feet with a bark and runs off. It can't be tamed, isn't saved with the world, and drops the paper if anyone hits it. If it can't find a way to you, the paper goes straight into your inventory. Players who were offline get theirs when they next join.

## The press (optional AI writing)

Out of the box, the Herald writes every story from its own house templates, in a consistent, deadpan small-town-paper style.

It can also write each story with a small language model that runs **on your computer, offline**. The model only sees the facts the newsroom collected for that story ("Steve mined 412 blocks", "the block mined most was stone: 380"), replies in a fixed JSON shape, and every reply goes through the sub-editor before it can be printed:

- **Structure:** valid JSON matching the profile's schema
- **Sanitising:** Unicode normalisation; no control characters, formatting codes, emoji or markdown
- **Form:** the right length, a finished sentence, English, no looping
- **Grounding:** the player is named correctly; every number, player, notable thing and quote comes from the facts
- **Policy and copying:** no banned phrases, "they" for players, nothing lifted from the instructions

A reply that fails is redrafted; after three failures the house template is printed instead. The paper never depends on the model.

Everything that shapes the model is in **`config/herald-press.json`**: the persona and system prompt, the request template, a brief per kind of story, few-shot examples, sampling settings, the JSON Schema and every check. It is written with the defaults on first run and re-read for every edition.

**Installing a model**

- **Singleplayer:** when a world opens without a model, the Herald offers to download one, recommends one for your computer and shows the size. You can also open the models folder, visit the model's page, or tell it never to ask again.
- **Dedicated server:** `/herald model download standard` (or `quality`), or put a `.gguf` file in `herald-models/` in the server folder.

| Model | Download | Extra memory while writing | For |
|---|---|---|---|
| Standard: Qwen3.5 0.8B (Q4_K_M) | 533 MB | about 0.8 GB | any computer that runs Minecraft |
| Quality: Qwen3.5 2B (Q4_K_M) | 1.3 GB | about 1.6 GB | 16 GB of RAM or more |

- **Where it comes from:** both models are Apache-2.0 licensed, downloaded from Hugging Face ([0.8B](https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF), [2B](https://huggingface.co/unsloth/Qwen3.5-2B-GGUF)) and checked against the published SHA-256.
- **When it runs:** only while an edition is being written, on a low-priority background thread using up to 4 cores, then it unloads.
- **Size and platforms:** the engine is llama.cpp, bundled in the mod jar (most of the jar's size), for Windows (x64, ARM64), Linux (x64, ARM64) and Apple Silicon Macs. Elsewhere the Herald uses its templates.
- **Nothing is sent anywhere:** the only network access is the model download you ask for, and looking up the names of players who haven't been online, the same way the game does for player heads.

## Usage

| Command | Who | What it does |
|---|---|---|
| `/herald` | Everyone | Lists the commands |
| `/herald reprint` | Everyone | A fresh copy of the latest edition |
| `/herald publish` | Operators | Print an edition now (closes the current period) |
| `/herald status` | Operators | Latest issue, when the next is due, and how the model did last time |
| `/herald model download <standard\|quality>` | Admins | Download a writing model on a dedicated server |

## Configuration

`config/herald.json`:

| Option | Default | |
|---|---|---|
| `daysPerEdition` | `7` | In-game days between editions. An edition comes out at sunrise on its day. |
| `paperName` | `""` | Masthead. Empty: "The *world name* Herald". |
| `storiesPerEdition` | `8` | Stories the news desk sends to press; those that fit pages 1-2 are printed. |
| `deliveryDog` | `true` | Off: papers go straight into inventories. |
| `usePress` | `true` | Write stories with the installed model. Off: house templates only. |
| `model` | `""` | Model file to use from `herald-models/`. Empty: the newest one. |
| `pressThreads` | `0` | CPU threads for the model. 0: half your cores, at most 4. |
| `offerModelDownload` | `true` | Singleplayer: offer to download a model when none is installed. |

`config/herald-press.json`: the writing model's profile, see [The press](#the-press-optional-ai-writing).

Each world's records live in `<world>/spacebod_herald/`: plain JSON with who lives there, their statistics as of the last edition, their recent history, the Exchange hand-ins and the latest edition.

## Notes

- Editions follow the overworld's day count, so with the daylight cycle switched off no new editions come out on their own (`/herald publish` still works).
- The house content is data, so resource packs can restyle the paper: `copy.json` (story templates, opinion pieces, almanac, small ads), `insights.json` (firsts, pursuits, leaderboards), `exchange.json` (trades, requests, prices), `names.json` (people and towns), `press.json` (the default press profile), all in `assets/spacebod_herald/herald/`.

## Development

```
herald/
├─ build-logic/   shared Fabric/NeoForge Gradle conventions (spacebod.common, .fabric, .neoforge)
├─ common/        loader-agnostic code: src/main, src/client, src/versions/<series>/ overlays, src/test
├─ fabric/        Fabric entrypoints and fabric.mod.json
├─ neoforge/      NeoForge @Mod classes and neoforge.mods.toml
├─ llama.gradle   bundles the llama.cpp binding (CPU natives for the five platforms) into both jars
└─ release.ps1    builds every supported Minecraft series for both loaders into releases/
```

Requires JDK 25. Minecraft 26.x is unobfuscated, so both loaders compile the same `common/` sources; the few classes that differ between series live in `common/src/versions/<series>/`.

```bash
./gradlew :fabric:runClient      # Fabric dev client
./gradlew :neoforge:runClient    # NeoForge dev client
./gradlew build                  # both jars for the dev target (gradle.properties)
./gradlew :common:test           # the test suite
```

```powershell
.\release.ps1                          # every series, both loaders, into releases/
.\release.ps1 -Versions 26.2 -Loaders fabric
```

### Tests

`./gradlew :common:test` runs the newsroom against a bootstrapped vanilla game with no loader and no world:

- **Scenarios** (`common/src/test/.../sim/`): simulated players whose activities leave the statistics the game would really record (a redstone build places wire, repeaters and pistons, crafts them and mines redstone ore; a monster hunt adds kills, damage both ways and the drops) over several editions. They are published through the real ledger, news desk, insights, leaderboards and Exchange. Checks cover a founding edition, a redstone build, a mining expedition, a monster hunt with a race, TNT, a week of deaths, a record harvest, a Nether debut, a quiet week and a busy five-player server.
- **The Exchange:** the same notices on client and server, four trades and two Watch jobs, no Nether work for newcomers, rewards within vanilla's price range, famous names only in their own trade.
- **The press:** every sub-editor rule against the kind of drafts small models write, sanitising, and the prompt templates.
- **The gallery:** every scenario's edition is written as Markdown to `common/build/scenarios/`, to read what the paper would actually say. With `HERALD_MODEL` set to a GGUF file, some scenarios are also printed through the model (`*.press.md`):

```bash
HERALD_MODEL=/path/to/Qwen3.5-0.8B-Q4_K_M.gguf ./gradlew :common:test --tests '*GalleryTest*'
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT, see [LICENSE](LICENSE).
