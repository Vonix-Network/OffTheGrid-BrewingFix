# Off The Grid Brewing Fix

A tiny, surgical Forge 1.18.2 patch mod that stops the `off_the_grid` mod from crashing brewing stands.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2-blue)]()
[![Forge](https://img.shields.io/badge/Forge-40.x-orange)]()
[![License](https://img.shields.io/badge/License-MIT-green)]()

---

## What this fixes

Players on the **Isle of Berk** modpack (and any other 1.18.2 pack shipping `off_the_grid-1.6.0-forge-1.18.2.jar`) experience a hard crash every time a brewing stand ticks:

```
java.lang.NoSuchFieldError: SWORDONSCALES
  at net.mcreator.offthegrid.recipes.brewing.SwordonScalesrecipeBrewingRecipe.isIngredient(...)
  at net.minecraftforge.common.brewing.BrewingRecipeRegistry.canBrew(...)
  at net.minecraft.world.level.block.entity.BrewingStandBlockEntity.serverTick(...)
```

Once a brewing stand exists in a loaded chunk, the world is unloadable — every server tick re-throws the same crash. The player gets `Ticking block entity` and is locked out of their save.

### Why it happens

`off_the_grid` v1.6.0 is an MCreator-generated mod. Its custom brewing recipe class `SwordonScalesrecipeBrewingRecipe` references a static field `SWORDONSCALES` that does **not** exist in the loaded class — the jar is an incomplete update (the file is labeled `1.6.0` but its `mods.toml` reports version `1.0.0`, indicating partial class replacement during build).

Forge's `BrewingRecipeRegistry.canBrew()` iterates **every** registered brewing recipe on every brewing stand tick. The broken recipe gets called → JVM throws `NoSuchFieldError` → block entity crashes → world fails to tick.

## What this mod does

At `FMLCommonSetupEvent` (after `off_the_grid` registers its recipes, before any world ticks), this patch reflectively reads `BrewingRecipeRegistry.recipes` and removes any entry whose class FQN contains `SwordonScales`. The broken recipe is never called again, so brewing stands tick normally for the rest of the world's life.

### Net effects

| What | Effect |
|---|---|
| Brewing stands | ✅ Work normally for vanilla + every other mod's recipes |
| Existing saves with brewing stands at the crash location | ✅ Load and tick without crashing — no NBT editing required |
| Other `off_the_grid` features (blocks, items, world gen, non-brewing recipes) | ✅ Untouched, fully functional |
| The "SwordonScales" potion itself | ❌ Cannot be brewed (but it never worked anyway — it crashed the game) |
| Server performance | ✅ No measurable impact — one reflection call at startup, zero runtime overhead |
| Save compatibility | ✅ Fully save-compatible. Add the jar, load the world. Remove the jar later if `off_the_grid` ships a real fix. |

### What this mod does **not** do

- Does not modify `off_the_grid` bytecode.
- Does not modify the player's save file.
- Does not depend on Mixin, MixinExtras, or any coremod loader. Pure vanilla Forge.
- Does not change any other mod's behavior.
- Does not log spam — one INFO line at startup, that's it.

## Installation

1. Download `offthegridfix-1.0.0.jar` from the [latest release](https://github.com/Vonix-Network/OffTheGrid-BrewingFix/releases/latest).
2. Drop it into the `mods/` folder of every client AND server running `off_the_grid`.
3. Start the game. Look for this line in the log:
   ```
   [OffTheGridBrewingFix] Removed 1 broken brewing recipe(s). Registry size: N -> N-1.
   ```
4. Your existing crash-loop world will load. Brewing stands will work.

### What if `off_the_grid` is updated and the bug gets fixed?

This patch is **safe to leave installed**. If the broken recipe class is no longer present at startup (because off_the_grid was updated or removed), the patch logs:

```
[OffTheGridBrewingFix] No SwordonScales-class brewing recipe found in registry — this patch is now a no-op.
```

…and does nothing else. You can remove the patch jar at your convenience.

## Building from source

```bash
git clone https://github.com/Vonix-Network/OffTheGrid-BrewingFix.git
cd OffTheGrid-BrewingFix
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew build
# jar is at build/libs/offthegridfix-1.0.0.jar
```

Requires JDK 17.

## Credits

- **Crash report:** Reported by the Isle of Berk modpack creator on 2026-06-24, mclo.gs/nE79sr7
- **Diagnosis & patch:** [Hermes Agent](https://hermes-agent.nousresearch.com/) operating on the [Vonix Network](https://vonix.network) infrastructure, in collaboration with WeedMeister
- **Upstream mod:** `off_the_grid` (the broken mod itself is not modified — its author should be contacted about the corrupted v1.6.0 build)

## License

MIT — see [LICENSE](LICENSE).
