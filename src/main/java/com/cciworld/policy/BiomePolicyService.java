package com.cciworld.policy;

import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class BiomePolicyService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ResourceLocation> WARNED_INVALID = ConcurrentHashMap.newKeySet();

    private BiomePolicyService() {}

    static int getWarnedInvalidCount() {
        return WARNED_INVALID.size();
    }

    static PolicyResult evaluate(
        ServerLevel level,
        LevelChunk chunk,
        String bandName,
        int bandMin,
        int bandMax,
        ResourceLocation currentId,
        int distChunks,
        boolean doApply
    ) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        // Sample biome at the horizontal center of the chunk at configured Y
        int bx = chunk.getPos().getMinBlockX() + 8;
        int bz = chunk.getPos().getMinBlockZ() + 8;
        int by = CCIWorldConfig.BIOME_SAMPLE_Y.get();

        Holder<Biome> biomeHolder = chunk.getNoiseBiome(bx >> 2, by >> 2, bz >> 2);
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) {
            return new PolicyResult(PolicyResult.Reason.BIOME_UNKNOWN, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, null, null, currentId, null, distChunks, cx, cz);
        }
        ResourceLocation biomeId = biomeKey.get().location();

        // Find a biome rule whose source matches the current recipe
        List<BiomePolicyRule> rules = CCIWorldConfig.parseBiomeRules();
        BiomePolicyRule matched = null;
        for (BiomePolicyRule rule : rules) {
            if (rule.enabled() && rule.sourceRecipe().equals(currentId)) {
                matched = rule;
                break;
            }
        }

        if (matched == null) {
            PolicyResult.Reason reason = (bandName == null)
                ? PolicyResult.Reason.NO_MATCHING_BAND
                : PolicyResult.Reason.NO_MATCHING_RULE;
            return new PolicyResult(reason, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, biomeId, null, currentId, null, distChunks, cx, cz);
        }

        // Validate allowed biomes against the live biome registry
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Set<ResourceLocation> validAllowed = validateAllowedBiomes(matched, biomeRegistry);

        if (validAllowed.contains(biomeId)) {
            return new PolicyResult(PolicyResult.Reason.BIOME_ALLOWED, PolicyResult.PolicyType.BIOME,
                bandName, bandMin, bandMax, biomeId, matched.name(), currentId, null, distChunks, cx, cz);
        }

        // Biome not in allowed list — replacement applies
        ResourceLocation targetId = matched.replacementRecipe();
        if (!COEVeinWriter.veinExists(level.getServer().getRecipeManager(), targetId)) {
            return new PolicyResult(PolicyResult.Reason.BIOME_RULE_TARGET_MISSING, PolicyResult.PolicyType.BIOME,
                bandName, bandMin, bandMax, biomeId, matched.name(), currentId, targetId, distChunks, cx, cz);
        }

        if (doApply) {
            COEVeinWriter.writeVein(chunk, targetId, CCIWorldConfig.RANDOM_MULTIPLIER.get().floatValue());
        }

        return new PolicyResult(PolicyResult.Reason.BIOME_NOT_ALLOWED_APPLIED, PolicyResult.PolicyType.BIOME,
            bandName, bandMin, bandMax, biomeId, matched.name(), currentId, targetId, distChunks, cx, cz);
    }

    private static Set<ResourceLocation> validateAllowedBiomes(BiomePolicyRule rule, Registry<Biome> registry) {
        Set<ResourceLocation> valid = new HashSet<>();
        for (ResourceLocation id : rule.allowedBiomes()) {
            if (registry.containsKey(id)) {
                valid.add(id);
            } else if (WARNED_INVALID.add(id)) {
                LOGGER.warn("[CCI World] biome_policy rule '{}': biome '{}' not in registry — ignored", rule.name(), id);
            }
        }
        return valid;
    }
}
