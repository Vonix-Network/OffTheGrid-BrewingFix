package network.vonix.offthegridfix;

import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Off The Grid Brewing Fix
 * ------------------------
 * The off_the_grid 1.6.0 mod (file: off_the_grid-1.6.0-forge-1.18.2.jar) ships a
 * custom brewing recipe class:
 *
 *     net.mcreator.offthegrid.recipes.brewing.SwordonScalesrecipeBrewingRecipe
 *
 * whose isIngredient() / getOutput() methods reference a static field named
 * SWORDONSCALES that does not exist in the loaded class. The first brewing-stand
 * tick anywhere in the world iterates BrewingRecipeRegistry.recipes, calls into
 * this recipe, and the JVM throws:
 *
 *     java.lang.NoSuchFieldError: SWORDONSCALES
 *
 * which surfaces as a Forge "Ticking block entity" crash and locks the player
 * out of their world. Reported by the Isle of Berk modpack creator on 2026-06-24.
 *
 * This patch mod runs at FMLCommonSetupEvent (after off_the_grid has registered
 * its recipes, before any world ticks) and removes the broken recipe instance
 * from BrewingRecipeRegistry.recipes via reflection. Net effect: brewing stands
 * work normally for every other recipe; the SwordonScales potion just stops
 * working — which it never worked anyway, since it crashed the game.
 */
@Mod(OffTheGridBrewingFix.MODID)
public class OffTheGridBrewingFix {

    public static final String MODID = "offthegridfix";
    private static final Logger LOG = LogManager.getLogger(MODID);

    // Substring match on FQN — defensive against off_the_grid renaming/relocating
    // the class internally. The pattern "SwordonScales" is distinctive enough
    // that we won't false-positive on any vanilla or third-party brewing recipe.
    private static final String TARGET_CLASS_HINT = "SwordonScales";

    public OffTheGridBrewingFix() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
                .get().getModEventBus().addListener(this::onCommonSetup);
    }

    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::removeBrokenRecipe);
    }

    @SuppressWarnings("unchecked")
    private void removeBrokenRecipe() {
        try {
            Field recipesField = BrewingRecipeRegistry.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            List<IBrewingRecipe> recipes = (List<IBrewingRecipe>) recipesField.get(null);

            int before = recipes.size();
            int removed = 0;
            var iter = recipes.iterator();
            while (iter.hasNext()) {
                IBrewingRecipe r = iter.next();
                String fqn = r.getClass().getName();
                if (fqn.contains(TARGET_CLASS_HINT)) {
                    LOG.warn("[OffTheGridBrewingFix] Unregistering broken brewing recipe: {}", fqn);
                    iter.remove();
                    removed++;
                }
            }
            if (removed == 0) {
                LOG.info("[OffTheGridBrewingFix] No SwordonScales-class brewing recipe found in registry ({} recipes total). " +
                        "Either off_the_grid is absent, already patched, or has been updated upstream — this patch is now a no-op.", before);
            } else {
                LOG.info("[OffTheGridBrewingFix] Removed {} broken brewing recipe(s). Registry size: {} -> {}.",
                        removed, before, recipes.size());
            }
        } catch (NoSuchFieldException e) {
            LOG.error("[OffTheGridBrewingFix] BrewingRecipeRegistry.recipes field not found — Forge internals have changed. " +
                    "This patch needs an update.", e);
        } catch (Throwable t) {
            // Never let the patch crash the server. If we can't remove the recipe,
            // log loudly and let the original bug surface — the operator at least
            // knows the patch failed.
            LOG.error("[OffTheGridBrewingFix] Failed to remove broken brewing recipe — original crash will likely still occur.", t);
        }
    }
}
