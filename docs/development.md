# Development

Back to the [README](../README.md).

```
the-observer/
├─ build-logic/   shared Fabric/NeoForge Gradle conventions (spacebod.common, .fabric, .neoforge)
├─ common/        loader-agnostic code: src/main, src/client, src/versions/<series>/ overlays, src/test
├─ fabric/        Fabric entrypoints and fabric.mod.json
├─ neoforge/      NeoForge @Mod classes and neoforge.mods.toml
├─ llama.gradle   bundles the llama.cpp binding (CPU natives for five platforms, Vulkan for Windows and Linux x64)
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

## A demonstration town

In a development run, `/observer demo` moves a demonstration town into the world: five residents with a few ordinary weeks behind them and an eventful week just gone (a record harvest, diamonds and lava, a first trip to the Nether, a redstone build, an elytra crash, the Wither and the Ender Dragon, a raid, a terrible week for one unlucky resident), then prints an edition about them. It's how the screenshots were made. The command doesn't exist outside development.

## Tests

`./gradlew :common:test` runs the newsroom against a bootstrapped vanilla game with no loader and no world:

- **Scenarios** (`common/src/test/.../sim/`): simulated residents whose activities leave the statistics the game would really record (a redstone build places wire, repeaters and pistons, crafts them and mines redstone ore; a monster hunt adds kills, damage both ways and the drops) over several editions, published through the real ledger, news desk, insights, connections, leaderboards and Exchange.
- **The Exchange:** the same notices on client and server, sized to the frequency, no Nether work for newcomers, rewards within vanilla's price range, an emerald notice for everyone starting out.
- **The press:** every sub-editor rule, sanitising, the prompt templates, and every voice's examples passing the same checks the model's drafts do.
- **The gallery:** every scenario's edition (the demonstration town included) is written as Markdown to `common/build/scenarios/`, to read what the paper would actually say. With `OBSERVER_MODEL` set to a GGUF file, some are also printed through the model (`*.press.md`):

```bash
OBSERVER_MODEL=/path/to/Qwen3.5-2B-Q4_K_M.gguf ./gradlew :common:test --tests '*GalleryTest*'
```
