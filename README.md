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

### Why it happens (decompilation findings)

`off_the_grid` v1.6.0 is an MCreator-generated mod. The jar ships:

- A texture: `assets/off_the_grid/textures/item/swordon_scales.png` ✅
- A model: `assets/off_the_grid/models/item/swordonscales.json` ✅
- An item class: `net.mcreator.offthegrid.item.SwordonscalesItem` ✅
- A brewing recipe: `net.mcreator.offthegrid.recipes.brewing.SwordonScalesrecipeBrewingRecipe` ✅
- **The actual item registration on `OffTheGridModItems.SWORDONSCALES`: missing ❌**

The recipe class was compiled assuming the registration would be there. It wasn't. So at runtime, every call to `isIngredient()` does:

```java
new ItemStack(OffTheGridModItems.SWORDONSCALES.get())   // ← SWORDONSCALES doesn't exist
```

→ JVM throws `NoSuchFieldError` → brewing stand crashes → world fails to tick.

**This isn't the only broken recipe.** Decompilation shows off_the_grid 1.6.0 ships **22 brewing recipe classes**, and several of them reference fields that don't exist on the published `OffTheGridModItems`:

| Recipe class | References field | Field actually exists? |
|---|---|---|
| `SwordonScalesrecipeBrewingRecipe` | `SWORDONSCALES` | ❌ |
| `SailingscourgescalesrecipeBrewingRecipe` | `SAILINGSCOURGESCALES` | ❌ |
| `MagmasmelterscalesrecipeBrewingRecipe` | `MAGMASMELTERSCALES` | ❌ |
| `HoarderwyvernscalesrecipeBrewingRecipe` | `HOARDERWYVERNSCALES` | ❌ |
| `FrostravengerfurrecipeBrewingRecipe` | `FROSTRAVENGERFUR` (typo'd; registered name is `FROSTRAVENGAR`fur) | ❌ |
| `NadderdonscalerecipeBrewingRecipe` | `NADDERDONSCALES` | ✅ |
| `SpearrowscalerecipeBrewingRecipe` | `SPEARROWSCALES` | ✅ |
| _…and others_ | | |

Today only the `SwordonScales` crash has been reported because most players haven't tried to brew with the other broken recipes yet. Without this patch, every one of those is a future crash report.

## What this mod does

At `FMLCommonSetupEvent` (after `off_the_grid` registers its recipes, before any world ticks), this patch:

1. Reflectively reads `BrewingRecipeRegistry.recipes`.
2. For every recipe whose class is under `net.mcreator.offthegrid.*`, dry-runs `isIngredient(ItemStack.EMPTY)` inside a `try/catch`.
3. Recipes that throw `NoSuchFieldError`, `NoSuchMethodError`, `NoClassDefFoundError`, or `NullPointerException` (= wiring problems) are removed from the registry.
4. Recipes that complete cleanly stay registered and work normally.
5. Logs every removal AND every keep at startup, so the server operator can audit which off_the_grid potions are functional.

### Net effects

| What | Effect |
|---|---|
| Brewing stands | ✅ Work normally for vanilla + every other mod's recipes |
| Existing crash-loop worlds | ✅ Load and tick without crashing — no NBT editing required |
| Working off_the_grid brewing recipes (Nadderdon, Spearrow, etc.) | ✅ Untouched, fully functional |
| Broken off_the_grid brewing recipes (SwordOnScales etc.) | ❌ Removed — these never worked (they crashed the game) |
| Other off_the_grid features (blocks, items, world gen, non-brewing recipes) | ✅ Completely untouched |
| Server performance | ✅ One reflection call + N method probes at startup, zero runtime overhead |
| Save compatibility | ✅ 100% — drop in, remove later, no migration |

### Why we don't "register the missing items ourselves"

It would be technically possible to register a stand-in `SWORDONSCALES` item under our own modid (`offthegridfix:swordonscales`) and synthesise a new brewing recipe. We considered this and rejected it because:

- The item registry ID would be `offthegridfix:swordonscales`, not `off_the_grid:swordonscales` — different namespace, different NBT, would show up as a separate item in JEI.
- Players who think they're brewing "the off_the_grid SwordOnScales" would actually be getting a Vonix-Network counterfeit. That's content invention, not a fix.
- We'd be guessing at the recipe's intended input/output/potion-type, since the bytecode only tells us the ingredient — the output potion type was never defined either.

The right fix for the mod itself is for the **off_the_grid author** to push a 1.6.1 with the missing registrations. Until they do, removing the broken recipes is the safest user-side action.

### What this mod does **not** do

- Does not modify `off_the_grid` bytecode.
- Does not modify the player's save file.
- Does not depend on Mixin, MixinExtras, or any coremod loader. Pure vanilla Forge.
- Does not change any other mod's behavior.
- Does not invent items, potions, or recipes to fill the gaps.

## Installation

1. Download `offthegridfix-1.1.0.jar` from the [latest release](https://github.com/Vonix-Network/OffTheGrid-BrewingFix/releases/latest).
2. Drop it into the `mods/` folder of every client AND server running `off_the_grid`.
3. Start the game. Look for lines like:
   ```
   [OffTheGridBrewingFix] Removed N broken off_the_grid brewing recipe(s) (registry size: X -> Y).
   [OffTheGridBrewingFix]   - REMOVED: net.mcreator.offthegrid.recipes.brewing.SwordonScalesrecipeBrewingRecipe  [NoSuchFieldError: SWORDONSCALES]
   [OffTheGridBrewingFix]   - kept:    net.mcreator.offthegrid.recipes.brewing.NadderdonscalerecipeBrewingRecipe
   ```
4. Your existing crash-loop world will load. Brewing stands will work.

### Safe to leave installed

If `off_the_grid` is later updated and the missing registrations are restored, this patch logs:

```
[OffTheGridBrewingFix] Scanned N off_the_grid brewing recipe(s), all healthy — this fix mod is currently a no-op.
```

…and does nothing else. You can remove it at your convenience.

If `off_the_grid` is removed entirely:

```
[OffTheGridBrewingFix] off_the_grid not detected — this patch is a no-op for this server.
```

## Building from source

```bash
git clone https://github.com/Vonix-Network/OffTheGrid-BrewingFix.git
cd OffTheGrid-BrewingFix
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew build
# jar is at build/libs/offthegridfix-1.1.0.jar
```

Requires JDK 17.

## Credits

- **Crash report:** Reported by the Isle of Berk modpack creator on 2026-06-24, mclo.gs/nE79sr7
- **Diagnosis & patch:** [Hermes Agent](https://hermes-agent.nousresearch.com/) operating on the [Vonix Network](https://vonix.network) infrastructure, in collaboration with WeedMeister
- **Upstream mod:** `off_the_grid` ("Off the grid dragons") — the broken mod itself is not modified. The mod author should be contacted about the incomplete v1.6.0 build.

## License

MIT — see [LICENSE](LICENSE).
