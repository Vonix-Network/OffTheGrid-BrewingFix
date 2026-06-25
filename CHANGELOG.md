# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-06-25

### Changed
- **Broadened scope from one recipe to all unregistered off_the_grid brewing recipes.** Decompilation of `off_the_grid-1.6.0-forge-1.18.2.jar` revealed that `SwordonScales` is not the only orphaned recipe — the jar ships 22 brewing recipe classes and several reference items the author forgot to register on `OffTheGridModItems` (e.g. `Sailingscourgescales`, `Magmasmelterscales`, `Hoarderwyvernscales`, and the typo'd `Frostravenger` vs registered `FROSTRAVENGAR`). Without v1.1, these would have crashed the server the moment any player tried brewing with the corresponding ingredient.
- **Detection switched from FQN substring match to runtime probe.** The patch now dry-runs `isIngredient(ItemStack.EMPTY)` on every `net.mcreator.offthegrid.*` brewing recipe at `FMLCommonSetupEvent` and removes any that throw `NoSuchFieldError`, `NoSuchMethodError`, `NoClassDefFoundError`, or `NullPointerException` (= author forgot to register the referenced item). This means: working off_the_grid brewing recipes stay working, and any future broken recipe in a hypothetical off_the_grid 1.7+ build is caught automatically.

### Fixed
- Prevented the next class of crashes that would have surfaced once a player tried to use one of the other ~5–10 broken brewing recipes shipped in off_the_grid 1.6.0.

### Notes
- Recipe class FQNs that get kept vs removed are logged at startup so server operators can audit which off_the_grid potions work and which were scrubbed.

## [1.0.0] - 2026-06-25

### Added
- Initial release.
- `FMLCommonSetupEvent` listener that reflectively removes any brewing recipe whose class FQN contains `SwordonScales` from `net.minecraftforge.common.brewing.BrewingRecipeRegistry.recipes`.
- Defensive no-op behavior when `off_the_grid` is absent or has been updated.
- Error-isolated reflection: any failure during the removal logs at ERROR level but does not crash the server.

### Fixed
- Hard crash on every brewing-stand tick caused by `off_the_grid-1.6.0-forge-1.18.2.jar`'s `SwordonScalesrecipeBrewingRecipe` referencing a non-existent static field `SWORDONSCALES`.

[1.1.0]: https://github.com/Vonix-Network/OffTheGrid-BrewingFix/releases/tag/v1.1.0
[1.0.0]: https://github.com/Vonix-Network/OffTheGrid-BrewingFix/releases/tag/v1.0.0
