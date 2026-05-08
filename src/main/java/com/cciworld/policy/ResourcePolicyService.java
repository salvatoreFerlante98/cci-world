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

public final class ResourcePolicyService {

    private ResourcePolicyService() {}

    /**
     * Inspects what the combined distance + biome policy would do without writing anything.
     * Caller must guarantee chunk is non-null and already loaded.
     */
    public static PolicyResult inspect(ServerLevel level, LevelChunk chunk) {
        return execute(level, chunk, false);
    }

    /**
     * Applies the combined distance + biome policy to an already-loaded chunk.
     * Distance policy takes priority; biome policy runs only if distance does not apply.
     * Caller must guarantee chunk is non-null and already loaded.
     * This method never calls getChunk/getChunkAt.
     */
    public static PolicyResult apply(ServerLevel level, LevelChunk chunk) {
        return execute(level, chunk, true);
    }

    private static PolicyResult execute(ServerLevel level, LevelChunk chunk, boolean doApply) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        if (!CCIWorldConfig.ENABLED.get()) {
            return new PolicyResult(PolicyResult.Reason.DISABLED, PolicyResult.PolicyType.NONE,
                null, -1, -1, null, null, null, null, 0, cx, cz);
        }

        // Compute Chebyshev distance from spawn
        BlockPos spawnPos = level.getSharedSpawnPos();
        int spawnCX = SectionPos.blockToSectionCoord(spawnPos.getX());
        int spawnCZ = SectionPos.blockToSectionCoord(spawnPos.getZ());
        int distChunks = Math.max(Math.abs(cx - spawnCX), Math.abs(cz - spawnCZ));

        // Find matching distance band
        List<DistanceBand> bands = CCIWorldConfig.parseBands();
        DistanceBand band = null;
        for (DistanceBand b : bands) {
            if (b.contains(distChunks)) { band = b; break; }
        }
        String bandName = band != null ? band.name() : null;
        int bandMin    = band != null ? band.minDistance() : -1;
        int bandMax    = band != null ? band.maxDistance() : -1;

        // Read current recipe
        OreData data = OreDataAttachment.getData(chunk);
        ResourceLocation currentId = data.getRecipeId();

        if (currentId == null) {
            return new PolicyResult(PolicyResult.Reason.NO_CURRENT_RECIPE, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, null, null, null, null, distChunks, cx, cz);
        }

        // --- Phase 1: distance band policy ---
        if (band != null) {
            ResourceLocation bandTarget = band.replacements().get(currentId);
            if (bandTarget != null) {
                if (!COEVeinWriter.veinExists(level.getServer().getRecipeManager(), bandTarget)) {
                    return new PolicyResult(PolicyResult.Reason.TARGET_RECIPE_MISSING, PolicyResult.PolicyType.DISTANCE,
                        bandName, bandMin, bandMax, null, null, currentId, bandTarget, distChunks, cx, cz);
                }
                if (doApply) {
                    COEVeinWriter.writeVein(chunk, bandTarget, CCIWorldConfig.RANDOM_MULTIPLIER.get().floatValue());
                }
                return new PolicyResult(PolicyResult.Reason.APPLIED, PolicyResult.PolicyType.DISTANCE,
                    bandName, bandMin, bandMax, null, null, currentId, bandTarget, distChunks, cx, cz);
            }
        }

        // --- Phase 2: biome policy ---
        if (!CCIWorldConfig.BIOME_POLICY_ENABLED.get()) {
            PolicyResult.Reason r = (band == null)
                ? PolicyResult.Reason.NO_MATCHING_BAND
                : PolicyResult.Reason.BIOME_POLICY_DISABLED;
            return new PolicyResult(r, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, null, null, currentId, null, distChunks, cx, cz);
        }

        return BiomePolicyService.evaluate(level, chunk, bandName, bandMin, bandMax, currentId, distChunks, doApply);
    }
}
