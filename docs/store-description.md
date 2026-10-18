![The Observer](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/banner.jpg)

![Minecraft 26.1 to 26.3](https://img.shields.io/badge/Minecraft-26.1%20to%2026.3-62B47A?style=for-the-badge)
![Fabric and NeoForge](https://img.shields.io/badge/Fabric%20%26%20NeoForge-supported-DBD0B4?style=for-the-badge)
![Written by a local AI](https://img.shields.io/badge/Written%20by-a%20local%20AI-1F3A8A?style=for-the-badge)

**The AI newspaper for your Minecraft world.** Every week, a language model running on your own computer reads what
happened in your world and writes it up as a newspaper: who beat the Ender Dragon, who struck diamonds and then lava,
who built half a castle, who died six times and still won't talk about it. The Pawstman, the paper's delivery dog,
brings it to your door.

![The front page: the Ender Dragon story leading, columns of news, obituaries and the town's numbers](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/articles-page-1.jpg)

![Newsstands in every dye colour, with the Pawstman in front](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/multicolor-options-dog.jpg)

![The Pawstman arriving with the paper rolled in his mouth](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/pawstman-approach.jpg)

## Why The Observer

A good week on a server is full of moments: the night someone finally killed the Wither, the raid you barely
survived, the mega-build that went up while you were at work. By next week they're gone, buried in chat and
half-remembered.

The Observer keeps them. Each edition is written fresh by an AI from what actually happened, so every story is about
your world and your friends. Log in after a few days away and catch up on everything you missed. Keep every edition
on a bookshelf, and your world builds its own history: a shelf of front pages to take down and reread, from the first
night to the hundredth dragon.

It needs no setup and works in singleplayer and on servers. The model runs on your computer, so nothing about your
world ever leaves it.

## Highlights

- **A newsroom on your computer.** An AI editor plans each edition from a file on every player, reporters draft every
  story several times, and a sub-editor checks each draft against the facts before it's printed. Runs on your
  graphics card (Vulkan or Metal) or the CPU; an edition takes well under a minute on a mid-range card.
- **Three voices.** The Broadsheet's proud small-town dry wit, The Roast's no-resident-is-safe teasing, or The
  Chronicle's epic saga.
- **A real newspaper to read.** Front-page lead, columns, obituaries, the town's numbers, weather, small ads,
  letters from the townsfolk and a "Did You Know?" column of real Minecraft trivia. Your name is printed in blue.
- **News worth reading.** The biggest moment always leads (a slain dragon takes the front page), with firsts, record
  weeks, redstone builds and connected stories: the elytra found and crashed the same week.
- **The Exchange.** Six jobs of your own from named townsfolk each edition, paid in emeralds, goods and enchanted
  books.
- **Leaderboards and a Hall of Fame,** picked fresh each edition.
- **Your world's history.** Old editions keep on chiseled bookshelves, every story intact.
- **The newsstand.** A dyeable vending box whose window and lamps show whether your copy is waiting, with a live
  countdown to the next edition.
- **The Pawstman.** A delivery dog who brings every copy and takes the price at his own counter. Like a tamed wolf,
  he always finds you.
- **Daily, weekly or fortnightly,** set by operators from the Editor's Desk.

![The Exchange: notices from the townsfolk](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/exchange-page.jpg)

![The Pawstman's counter: pay and take, or no thanks](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/pawstman-gui.jpg)

![The newsstand's counter, with the Editor's Desk open beside it](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/newsstand-gui-op.jpg)

## Crafting the newsstand

Emeralds, iron ingots, glass panes (any colour), paper and a wooden chest:

![The newsstand's crafting recipe](https://raw.githubusercontent.com/SpaceBod/the-observer/main/docs/images/crafting-recipe.jpg)

## Requirements

- Minecraft 26.1, 26.2 or 26.3
- Fabric Loader 0.19.5+ with Fabric API, or NeoForge
- On the server and on every player's game: the server writes and delivers the paper, players need the mod to read it
- A writing model, downloaded in game: 533 MB (standard) or 1.3 GB (quality)

Rather not download a model? The Observer writes every edition from its built-in house templates instead.

## Getting started

1. Put The Observer (plus Fabric API on Fabric) into your `mods` folder, on the server too, and start the game.
2. Open a world. The Observer offers to download a writing model and recommends one for your computer. On a
   dedicated server, run `/observer model download standard`.
3. The first edition comes out at sunrise, and the Pawstman brings everyone their first copy free. Right-click him to
   take it, then right-click the paper to read it.

Operators run the paper from the Editor's Desk beside any newsstand's counter, or with `/observer`.

## Links

- [Documentation](https://github.com/SpaceBod/the-observer/tree/main/docs)
- [Features in detail](https://github.com/SpaceBod/the-observer/blob/main/docs/features.md)
- [The AI newsroom](https://github.com/SpaceBod/the-observer/blob/main/docs/press.md)
- [Commands and settings](https://github.com/SpaceBod/the-observer/blob/main/docs/configuration.md)
- [Report a problem](https://github.com/SpaceBod/the-observer/issues)

## Credits

Written with [llama.cpp](https://github.com/ggml-org/llama.cpp), through the
[java-llama.cpp](https://github.com/bernardladenthin/java-llama.cpp) binding, and the
[Qwen3.5](https://huggingface.co/Qwen) models by the Qwen team (Apache-2.0), in the GGUF builds published by
[Unsloth](https://huggingface.co/unsloth).

The Observer is free software under the MIT licence.
