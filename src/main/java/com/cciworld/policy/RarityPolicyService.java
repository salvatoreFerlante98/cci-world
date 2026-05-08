package com.cciworld.policy;

import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

final class RarityPolicyService {

    private RarityPolicyService() {}

    /**
     * @param regionContext result from the region phase (null if region policy is disabled).
     *                      Region fields are carried forward into the returned PolicyResult.
     */
    static PolicyResult evaluate(
        ServerLevel level,
        LevelChunk chunk,
        String bandName,
        int bandMin,
        int bandMax,
        ResourceLocation currentId,
        ResourceLocation biomeId,
        String matchedBiomeRule,
        PolicyResult regionContext,
        int distChunks,
        boolean doApply
    ) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;

        // Extract region context to carry through all returns
        int regX    = regionContext != null ? regionContext.regionX()                         : 0;
        int regZ    = regionContext != null ? regionContext.regionZ()                         : 0;
        String regN = regionContext != null ? regionContext.regionName()                      : null;
        double regR = regionContext != null ? regionContext.regionRoll()                      : 0.0;
        boolean regA = regionContext != null && regionContext.regionAllowed();
        List<ResourceLocation> regP = regionContext != null ? regionContext.regionPreferredRecipes()  : null;
        ResourceLocation regRep     = regionContext != null ? regionContext.regionReplacementRecipe() : null;

        List<RarityPolicyRule> rules = CCIWorldConfig.parseRarityRules();
        RarityPolicyRule matched = null;
        for (RarityPolicyRule rule : rules) {
            if (rule.enabled()
                    && rule.sourceRecipe().equals(currentId)
                    && rule.band().equals(bandName)) {
                matched = rule;
                break;
            }
        }

        if (matched == null) {
            return new PolicyResult(
                PolicyResult.Reason.RARITY_NO_MATCHING_RULE, PolicyResult.PolicyType.NONE,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                null, 0.0, 0.0, false,
                regX, regZ, regN, regR, regA, regP, regRep,
                false, 0.0, null, 0);
        }

        String dimensionId = level.dimension().location().toString();
        String salt = CCIWorldConfig.RARITY_SALT.get();
        double roll = deterministicRoll(dimensionId, cx, cz, currentId.toString(), bandName, salt);
        boolean passed = roll <= matched.keepChance();

        if (passed) {
            return new PolicyResult(
                PolicyResult.Reason.RARITY_GATE_PASSED, PolicyResult.PolicyType.RARITY,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, null, distChunks, cx, cz,
                matched.name(), matched.keepChance(), roll, true,
                regX, regZ, regN, regR, regA, regP, regRep,
                false, 0.0, null, 0);
        }

        ResourceLocation target = matched.replacementRecipe();
        if (!COEVeinWriter.veinExists(level.getServer().getRecipeManager(), target)) {
            return new PolicyResult(
                PolicyResult.Reason.RARITY_RULE_TARGET_MISSING, PolicyResult.PolicyType.RARITY,
                bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
                currentId, target, distChunks, cx, cz,
                matched.name(), matched.keepChance(), roll, false,
                regX, regZ, regN, regR, regA, regP, regRep,
                false, 0.0, null, 0);
        }

        if (doApply) {
            COEVeinWriter.writeVein(chunk, target, CCIWorldConfig.RANDOM_MULTIPLIER.get().floatValue());
        }

        return new PolicyResult(
            PolicyResult.Reason.RARITY_GATE_FAILED_APPLIED, PolicyResult.PolicyType.RARITY,
            bandName, bandMin, bandMax, biomeId, matchedBiomeRule,
            currentId, target, distChunks, cx, cz,
            matched.name(), matched.keepChance(), roll, false,
            regX, regZ, regN, regR, regA, regP, regRep,
            false, 0.0, null, 0);
    }

    /**
     * SHA-256 deterministic roll: same inputs always produce the same double in [0.0, 1.0).
     * Uses top 53 bits of the hash to populate the double mantissa.
     */
    static double deterministicRoll(String dimensionId, int chunkX, int chunkZ,
                                     String sourceRecipe, String bandName, String salt) {
        String input = dimensionId + "|" + chunkX + "|" + chunkZ
            + "|" + sourceRecipe + "|" + bandName + "|" + salt;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(input.getBytes(StandardCharsets.UTF_8));
            long raw = 0;
            for (int i = 0; i < 8; i++) {
                raw = (raw << 8) | (hash[i] & 0xFF);
            }
            return (raw >>> 11) / (double)(1L << 53);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
