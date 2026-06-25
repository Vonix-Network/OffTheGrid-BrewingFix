package network.vonix.offthegridfix;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Off The Grid Brewing Fix
 * ------------------------
 * Reported via mclo.gs/nE79sr7 on 2026-06-24 (Isle of Berk modpack creator).
 *
 * The off_the_grid 1.6.0 jar (off_the_grid-1.6.0-forge-1.18.2.jar) ships 22+
 * brewing recipe classes whose static field references on
 *   net.mcreator.offthegrid.init.OffTheGridModItems
 * point at items the author forgot to register. The first known offender is
 *   SwordonScalesrecipeBrewingRecipe -> OffTheGridModItems.SWORDONSCALES
 * Decompilation confirms SWORDONSCALES is never declared on OffTheGridModItems
 * (verified field list: STINGERZILLAPLATE, STINGERZILLASCALES, STINGERZILLA_AXE,
 * SPEARROWSCALES, TRIPLESTINGERSCALES, DISTORTUSSTINGERSCALES, GULLETGULPERSCALES,
 * TRIPLEBOLTSCALES, INDO_STINGERSCALES, HOOTWINGFUR, TUNDRAGUTTERFUR,
 * NADDERDONSCALES, INDOMINUSSTINGERSCALES, SILVERKEEPERSCALES, TIMBERLUNGFUR,
 * FROSTRAVENGARFUR). Every brewing-stand tick iterates BrewingRecipeRegistry
 * and crashes the server with NoSuchFieldError.
 *
 * Other recipe classes in the jar are likely time-bombs:
 *   - SailingscourgescalesrecipeBrewingRecipe  (no SAILINGSCOURGE* field)
 *   - MagmasmelterscalesrecipeBrewingRecipe    (no MAGMASMELTER* field)
 *   - HoarderwyvernscalesrecipeBrewingRecipe   (no HOARDERWYVERN* field)
 *   - FrostravengerfurrecipeBrewingRecipe      (typo'd: FROSTRAVENGAR vs FROSTRAVENGER)
 *   - ... and others
 *
 * Strategy: at FMLCommonSetupEvent, dry-run every off_the_grid brewing recipe
 * with an empty stack. Any recipe that throws a LinkageError (NoSuchFieldError,
 * NoSuchMethodError, NoClassDefFoundError) is broken and gets unregistered.
 * Recipes that complete cleanly (or throw safe runtime errors like NPE on the
 * empty stack itself — which doesn't happen here because Forge's IBrewingRecipe
 * contract returns false on no-match) stay registered and continue to work.
 */
@Mod(OffTheGridBrewingFix.MODID)
public class OffTheGridBrewingFix {

    public static final String MODID = "offthegridfix";
    private static final Logger LOG = LogManager.getLogger(MODID);

    /** Target prefix for off_the_grid recipe classes. */
    private static final String TARGET_PACKAGE_PREFIX = "net.mcreator.offthegrid.";

    public OffTheGridBrewingFix() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
                .get().getModEventBus().addListener(this::onCommonSetup);
    }

    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::scrubBrokenRecipes);
    }

    @SuppressWarnings("unchecked")
    private void scrubBrokenRecipes() {
        try {
            Field recipesField = BrewingRecipeRegistry.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            List<IBrewingRecipe> recipes = (List<IBrewingRecipe>) recipesField.get(null);

            int before = recipes.size();
            int scanned = 0;
            List<String> removed = new ArrayList<>();
            List<String> kept = new ArrayList<>();

            // Probe with an empty stack — any off_the_grid recipe that throws a
            // LinkageError is wired to an unregistered item and will crash every
            // brewing-stand tick. Probing is the only reliable detection: the
            // bytecode-level field reference may resolve fine at class-load and
            // only fail when isIngredient() is invoked.
            ItemStack probe = ItemStack.EMPTY;

            Iterator<IBrewingRecipe> iter = recipes.iterator();
            while (iter.hasNext()) {
                IBrewingRecipe r = iter.next();
                String fqn = r.getClass().getName();
                if (!fqn.startsWith(TARGET_PACKAGE_PREFIX)) {
                    continue;
                }
                scanned++;
                try {
                    // The crash is in isIngredient (it eagerly constructs the
                    // ingredient ItemStack from the missing RegistryObject).
                    // We call it inside a Throwable catch so a buggy recipe
                    // can't poison our scrub pass.
                    r.isIngredient(probe);
                    kept.add(fqn);
                } catch (NoSuchFieldError | NoSuchMethodError | NoClassDefFoundError | NullPointerException e) {
                    // LinkageErrors = author forgot to register the item.
                    // NPE = RegistryObject.get() returned null (item registered
                    //       but the registry hasn't been populated — same effect:
                    //       broken recipe).
                    iter.remove();
                    removed.add(fqn + "  [" + e.getClass().getSimpleName()
                            + (e.getMessage() != null ? ": " + e.getMessage() : "") + "]");
                } catch (Throwable t) {
                    // Anything else (runtime error in author's logic that's not
                    // a wiring problem) — keep the recipe but warn. We don't
                    // want to remove recipes for unrelated reasons.
                    LOG.warn("[OffTheGridBrewingFix] Recipe {} threw {} during probe but is not a known wiring failure; keeping it.",
                            fqn, t.getClass().getSimpleName());
                    kept.add(fqn);
                }
            }

            if (scanned == 0) {
                LOG.info("[OffTheGridBrewingFix] off_the_grid not detected (no recipes under {}). " +
                        "This patch is a no-op for this server.", TARGET_PACKAGE_PREFIX);
                return;
            }

            if (removed.isEmpty()) {
                LOG.info("[OffTheGridBrewingFix] Scanned {} off_the_grid brewing recipe(s), all healthy. " +
                        "off_the_grid may have been patched upstream — this fix mod is currently a no-op.", scanned);
            } else {
                LOG.warn("[OffTheGridBrewingFix] Removed {} broken off_the_grid brewing recipe(s) " +
                        "(registry size: {} -> {}). Healthy off_the_grid recipes kept: {}.",
                        removed.size(), before, recipes.size(), kept.size());
                for (String r : removed) {
                    LOG.warn("[OffTheGridBrewingFix]   - REMOVED: {}", r);
                }
                for (String k : kept) {
                    LOG.info("[OffTheGridBrewingFix]   - kept:    {}", k);
                }
            }
        } catch (NoSuchFieldException e) {
            LOG.error("[OffTheGridBrewingFix] BrewingRecipeRegistry.recipes field not found — " +
                    "Forge internals have changed. This patch needs an update.", e);
        } catch (Throwable t) {
            LOG.error("[OffTheGridBrewingFix] Scrub pass failed — original crash will likely still occur.", t);
        }
    }
}
