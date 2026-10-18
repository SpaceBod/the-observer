# The press: the AI newsroom

Back to the [README](../README.md).

Every edition of The Observer is written by a small language model that runs **on your own computer**. It reads what happened in your world, plans the paper, writes every story and checks every one against the facts before it goes to print. Nothing is sent anywhere.

## How a paper is written

1. **The editor** reads a dossier on every player in the news (what they did, how it compares with their usual week and with everyone else, what the paper said about them before) and plans each story: an angle, a few supporting facts, and now and then a quote from one of the townsfolk.
2. **The reporters** draft each story several times at once, from its facts and the editor's plan, in the paper's current voice.
3. **The sub-editor** checks every draft before it can be printed, and the best one that passes goes in:
   - **Structure:** valid JSON in the profile's schema.
   - **Sanitising:** no control characters, formatting codes, emoji or markdown.
   - **Form:** the right length, finished sentences, English, no looping.
   - **Grounding:** the player is named correctly; every number, player, notable thing and quote comes from the facts.
   - **Policy and copying:** no banned phrases, "they" for players, nothing lifted from the instructions.
4. **Letters and columns** from the townsfolk fill the space the news leaves, more of them in a quiet week. They name no players and invent no numbers.

A story with no passing draft is printed from the house template instead, so an edition always comes out whole.

## Installing a model

- **Singleplayer:** when a world opens without a model, The Observer offers to download one, recommends one for your computer and shows the size. You can also open the models folder, visit the model's page, or tell it never to ask again.
- **Dedicated server:** `/observer model download standard` (or `quality`), or put a `.gguf` file in `observer-models/` in the server folder.

| Model | Download | Extra memory while writing | For |
|---|---|---|---|
| Standard: Qwen3.5 0.8B (Q4_K_M) | 533 MB | about 0.8 GB | any computer that runs Minecraft |
| Quality: Qwen3.5 2B (Q4_K_M) | 1.3 GB | about 1.6 GB | 16 GB of RAM or more |

Both models are Apache-2.0 licensed, downloaded from Hugging Face ([0.8B](https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF), [2B](https://huggingface.co/unsloth/Qwen3.5-2B-GGUF)) and checked against the published SHA-256.

## Running it

- **GPU or CPU.** By default the model runs on the graphics card (Vulkan on Windows and Linux, Metal on Apple Silicon) and falls back to the CPU; the settings screen can force the CPU. The game itself keeps rendering as usual: the model only borrows the GPU while an edition is written. On a mid-range card an edition takes well under a minute.
- **When it runs:** only while an edition is being written, on a low-priority background thread (up to 8 CPU cores when on the CPU), then it unloads.
- **Platforms:** the engine is llama.cpp, bundled in the mod jar (most of the jar's size) for Windows (x64, ARM64), Linux (x64, ARM64) and Apple Silicon Macs. Elsewhere The Observer uses its templates.
- **Nothing is sent anywhere.** The only network access is the model download you ask for, and looking up the names of players who haven't been online, the same way the game does for player heads.

## Template mode

Rather not download a model, or playing somewhere it can't run? The Observer writes every edition from its built-in house templates instead: the same paper and the same news, in a fixed set of phrasings for each voice. Turn the model off with `usePress: false` in `config/observer.json`, or simply don't install one; on a computer the bundled engine doesn't support, template mode is automatic.

## Tuning

Everything that shapes the model is in **`config/observer-press.json`**: the persona and system prompt, the request template, a brief per kind of story, few-shot examples, each voice's style and examples, the editor's instructions, the letters page, sampling settings, the JSON Schema and every check. It is written with the defaults on first run and re-read for every edition; when the format changes, the new defaults replace it.

`/observer status` shows which model ran where, and how the last edition went: how many stories the model wrote and why any drafts were turned down.
