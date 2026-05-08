package com.cciworld.coe;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.chunk.LevelChunk;

public final class COEVeinWriter {

    private COEVeinWriter() {}

    public static boolean veinExists(RecipeManager mgr, ResourceLocation id) {
        return mgr.getAllRecipesFor(CreateOreExcavation.VEIN_RECIPES.getRecipeType())
                  .stream()
                  .anyMatch(h -> h.id().equals(id));
    }

    public static void writeVein(LevelChunk chunk, ResourceLocation recipeId) {
        writeVein(chunk, recipeId, 1.0F);
    }

    public static void writeVein(LevelChunk chunk, ResourceLocation recipeId, float randomMul) {
        OreData data = OreDataAttachment.getData(chunk);
        data.setRecipe(recipeId);
        data.setLoaded(true);
        data.setRandomMul(randomMul);
        data.setExtractedAmount(0L);
        chunk.setUnsaved(true);
    }

    /**
     * Writes a "no useful vein" state on the chunk.
     *
     * Audit (COE 1.6.8):
     *  - OreData.recipe field is nullable.
     *  - OreData.save() uses Optional.ofNullable(recipe), so null recipe is
     *    serialized safely.
     *  - OreData.getRecipe(RecipeManager) has an explicit null-check on recipe
     *    and returns null when absent.
     *  - OreData.populate(chunk) natively produces this exact state when
     *    OreVeinGenerator.pick(chunk) returns null: recipe stays null while
     *    loaded=true is still set.
     *  - ExcavatingBlockEntity skips its mining logic when getRecipe(rm) is null.
     *
     * Therefore the safest no-vein state is:
     *   recipe=null, loaded=true, randomMul=0, extractedAmount=0.
     */
    public static void writeNoVein(LevelChunk chunk) {
        OreData data = OreDataAttachment.getData(chunk);
        data.setRecipe(null);
        data.setLoaded(true);
        data.setRandomMul(0F);
        data.setExtractedAmount(0L);
        chunk.setUnsaved(true);
    }
}
