# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-06-25

### Added
- Initial release.
- `FMLCommonSetupEvent` listener that reflectively removes any brewing recipe whose class FQN contains `SwordonScales` from `net.minecraftforge.common.brewing.BrewingRecipeRegistry.recipes`.
- Defensive no-op behavior when `off_the_grid` is absent or has been updated — the patch logs and does nothing.
- Error-isolated reflection: any failure during the removal logs at ERROR level but does not crash the server.

### Fixed
- Hard crash on every brewing-stand tick caused by `off_the_grid-1.6.0-forge-1.18.2.jar`'s `SwordonScalesrecipeBrewingRecipe` referencing a non-existent static field `SWORDONSCALES`.

[1.0.0]: https://github.com/Vonix-Network/OffTheGrid-BrewingFix/releases/tag/v1.0.0
