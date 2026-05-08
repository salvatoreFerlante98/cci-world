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
}
