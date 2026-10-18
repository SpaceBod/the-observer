# Configuration and commands

Back to the [README](../README.md).

Most settings can be changed in game: operators use the **Editor's Desk** on any newsstand, and in singleplayer the settings screen (Mod Menu on Fabric, the Mods screen on NeoForge) covers worlds hosted on your computer.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/observer` | Everyone | Lists the commands |
| `/observer publish` | Operators | Print an edition now (closes the current period) |
| `/observer reprint` | Operators | A free copy of the latest edition |
| `/observer status` | Operators | Latest issue, when the next is due, and how the model did last time |
| `/observer tone <broadsheet\|roast\|chronicle>` | Operators | The paper's voice |
| `/observer frequency <daily\|weekly\|fortnightly>` | Operators | How often the paper comes out |
| `/observer autopublish <on\|off>` | Operators | Publish on schedule, or only when an operator publishes |
| `/observer price <emeralds>` | Operators | What each reader's copy costs (0: free) |
| `/observer firstcopy <free\|paid>` | Operators | Whether each player's first copy is on the house |
| `/observer rewards <low\|balanced\|high>` | Operators | The Exchange's rewards, from the next edition |
| `/observer dogs <on\|off>` | Operators | Put the Pawstman on or off duty (off: everyone collects at a newsstand) |
| `/observer model download <standard\|quality>` | Admins | Download a writing model on a dedicated server |

"Operators" includes the owner of a singleplayer world, cheats on or off.

## `config/observer.json`

| Option | Default | |
|---|---|---|
| `frequency` | `"weekly"` | `daily`, `weekly` or `fortnightly`. An edition comes out at sunrise on its day and covers the time since the last one. |
| `autoPublish` | `true` | Off: no edition comes out on its own; operators publish when they like. |
| `storiesPerEdition` | `8` | Stories the news desk sends to press. |
| `tone` | `"broadsheet"` | The paper's voice: `broadsheet`, `roast` or `chronicle`. |
| `paperPrice` | `1` | Emeralds for a copy, paid to the Pawstman or at a newsstand; spare copies cost the same. 0: free. |
| `firstCopyFree` | `true` | Each player's first copy is on the house. |
| `deliveryDog` | `true` | Players may choose the Pawstman. Off: everyone collects at a newsstand. |
| `exchangeRewards` | `"balanced"` | `low`, `balanced` or `high`: how generous the Exchange is, from the next edition. |
| `usePress` | `true` | Write stories with the installed model. Off: house templates only. |
| `model` | `""` | Model file to use from `observer-models/`. Empty: the newest one. |
| `pressDevice` | `"gpu"` | Where the model runs: `gpu` (falling back to the CPU) or `cpu`. |
| `pressThreads` | `0` | CPU threads for the model. 0: half your cores, at most 8. |
| `offerModelDownload` | `true` | Singleplayer: offer to download a model when none is installed. |

`config/observer-press.json` is the writing model's profile; see [The press](press.md).

## Files

- **Each world's records** live in `<world>/spacebod_observer/`: plain JSON with who lives there, their statistics as of the last edition, their recent history, the Exchange hand-ins and the latest edition.
- **Models** live in `observer-models/` next to `mods/` (or in the server folder).
