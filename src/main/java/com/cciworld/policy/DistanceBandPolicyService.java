package com.cciworld.policy;

import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public final class DistanceBandPolicyService {

    private DistanceBandPolicyService() {}

    public enum Reason {
        APPLIED,
        DISABLED,
        NO_CURRENT_RECIPE,
        NO_MATCHING_BAND,
        NO_MATCHING_RULE,
        TARGET_RECIPE_MISSING,
        CHUNK_NOT_LOADED
    }

    public record Result(
        Reason reason,
        String bandName,
        int bandMin,
        int bandMax,
        ResourceLocation previousRecipe,
        ResourceLocation newRecipe,
        int distChunks,
        int chunkX,
        int chunkZ
    ) {
        public boolean applied() { return reason == Reason.APPLIED; }
    }

    /**
     * Inspects what the distance band policy would do without writing anything.
     * Caller must guarantee chunk is non-null and already loaded.
     */
    public static Result inspect(ServerLevel level, LevelChunk chunk) {
        return execute(level, chunk, false);
    }

    /**
     * Applies the distance band policy to an already-loaded chunk.
     * Caller must guarantee chunk is non-null and already loaded.
     * This method never calls getChunk/getChunkAt.
     */
    public static Result apply(ServerLevel level, LevelChunk chunk) {
        return execute(level, chunk, true);
    }

    private static Result execute(ServerLevel level, LevelChunk chunk, boolean doApply) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        if (!CCIWorldConfig.ENABLED.get()) {
            return new Result(Reason.DISABLED, null, -1, -1, null, null, 0, cx, cz);
        }

        BlockPos spawnPos = level.getSharedSpawnPos();
        int spawnCX = SectionPos.blockToSectionCoord(spawnPos.getX());
        int spawnCZ = SectionPos.blockToSectionCoord(spawnPos.getZ());
        int distChunks = Math.max(Math.abs(cx - spawnCX), Math.abs(cz - spawnCZ));

        List<DistanceBand> bands = CCIWorldConfig.parseBands();
        DistanceBand band = null;
        for (DistanceBand b : bands) {
            if (b.contains(distChunks)) {
                band = b;
                break;
            }
        }

        if (band == null) {
            return new Result(Reason.NO_MATCHING_BAND, null, -1, -1, null, null, distChunks, cx, cz);
        }

        OreData data = OreDataAttachment.getData(chunk);
        ResourceLocation currentId = data.getRecipeId();

        if (currentId == null) {
            return new Result(Reason.NO_CURRENT_RECIPE, band.name(), band.minDistance(), band.maxDistance(), null, null, distChunks, cx, cz);
        }

        ResourceLocation targetId = band.replacements().get(currentId);

        if (targetId == null) {
            return new Result(Reason.NO_MATCHING_RULE, band.name(), band.minDistance(), band.maxDistance(), currentId, null, distChunks, cx, cz);
        }

        if (!COEVeinWriter.veinExists(level.getServer().getRecipeManager(), targetId)) {
            return new Result(Reason.TARGET_RECIPE_MISSING, band.name(), band.minDistance(), band.maxDistance(), currentId, targetId, distChunks, cx, cz);
        }

        if (doApply) {
            float randomMul = CCIWorldConfig.RANDOM_MULTIPLIER.get().floatValue();
            COEVeinWriter.writeVein(chunk, targetId, randomMul);
        }

        return new Result(Reason.APPLIED, band.name(), band.minDistance(), band.maxDistance(), currentId, targetId, distChunks, cx, cz);
    }
}
