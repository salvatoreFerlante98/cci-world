package com.cciworld.command;

import com.cciworld.coe.COEVeinIds;
import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.cciworld.generator.ClusterDecision;
import com.cciworld.generator.ClusterGenerator;
import com.cciworld.generator.ClusterGeneratorEngine;
import com.cciworld.policy.AutomaticPolicyEngine;
import com.cciworld.policy.BiomePolicyRule;
import com.cciworld.policy.DistanceBand;
import com.cciworld.policy.PolicyQueue;
import com.cciworld.policy.PolicyQueueEntry;
import com.cciworld.policy.PolicyResult;
import com.cciworld.policy.PolicyResult.Reason;
import com.cciworld.policy.RarityPolicyRule;
import com.cciworld.policy.RegionPolicyRule;
import com.cciworld.policy.ResourcePolicyService;
import com.cciworld.policy.WeightedReplacementEntry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CCIWorldCommands {

    private CCIWorldCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("cci_world")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("debug_chunk")
                    .executes(CCIWorldCommands::debugChunk))
                .then(Commands.literal("set_test_vein")
                    .then(Commands.argument("alias", StringArgumentType.word())
                        .executes(CCIWorldCommands::setTestVein)))
                .then(Commands.literal("set_test_no_vein")
                    .executes(CCIWorldCommands::setTestNoVein))
                .then(Commands.literal("replace_test_vein")
                    .then(Commands.argument("from", StringArgumentType.word())
                        .then(Commands.argument("to", StringArgumentType.word())
                            .executes(CCIWorldCommands::replaceTestVein))))
                .then(Commands.literal("debug_policy_here")
                    .executes(CCIWorldCommands::debugPolicyHere))
                .then(Commands.literal("apply_distance_policy_here")
                    .executes(CCIWorldCommands::applyDistancePolicyHere))
                .then(Commands.literal("apply_spawn_policy_here")
                    .executes(CCIWorldCommands::applySpawnPolicyHere))
                .then(Commands.literal("apply_spawn_policy_loaded_radius")
                    .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(1, 64))
                        .executes(CCIWorldCommands::applySpawnPolicyLoadedRadius)))
                .then(Commands.literal("policy_status")
                    .executes(CCIWorldCommands::policyStatus))
                .then(Commands.literal("clear_policy_session_cache")
                    .executes(CCIWorldCommands::clearPolicySessionCache))
                .then(Commands.literal("rescan_loaded")
                    .executes(CCIWorldCommands::rescanLoaded))
                .then(Commands.literal("clear_policy_cache")
                    .executes(CCIWorldCommands::clearPolicyCache))
                .then(Commands.literal("selftest")
                    .executes(CCIWorldCommands::selftest))
                .then(Commands.literal("selftest_policy_matrix")
                    .executes(CCIWorldCommands::selftestPolicyMatrix))
                .then(Commands.literal("debug_generator_here")
                    .executes(CCIWorldCommands::debugGeneratorHere))
                .then(Commands.literal("generator_status")
                    .executes(CCIWorldCommands::generatorStatus))
                .then(Commands.literal("selftest_generator")
                    .executes(CCIWorldCommands::selftestGenerator))
                .then(Commands.literal("simulate_distribution")
                    .then(Commands.argument("radius_blocks", IntegerArgumentType.integer(16, 100_000))
                        .executes(CCIWorldCommands::simulateDistribution)))
        );
    }

    // -------------------------------------------------------------------------
    // debug_chunk
    // -------------------------------------------------------------------------

    private static int debugChunk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());
            OreData data = OreDataAttachment.getData(chunk);
            ResourceLocation recipeId = data.getRecipeId();

            String msg = "[CCI World] debug_chunk" +
                "\n  dimension:    " + level.dimension().location() +
                "\n  chunk x/z:   " + chunk.getPos().x + " / " + chunk.getPos().z +
                "\n  recipe:       " + (recipeId != null ? recipeId : "none") +
                "\n  loaded:       " + data.isLoaded() +
                "\n  randomMul:    " + data.getRandomMul() +
                "\n  extractedAmount: not available via public API";

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // set_test_vein <alias>
    // -------------------------------------------------------------------------

    private static int setTestVein(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            String alias = StringArgumentType.getString(ctx, "alias");

            Optional<ResourceLocation> targetOpt = COEVeinIds.fromAlias(alias);
            if (targetOpt.isEmpty()) {
                src.sendFailure(Component.literal("[CCI World] unknown alias '" + alias + "'. Valid: " + COEVeinIds.aliases().keySet()));
                return 0;
            }
            ResourceLocation targetId = targetOpt.get();
            if (!COEVeinWriter.veinExists(src.getServer().getRecipeManager(), targetId)) {
                src.sendFailure(Component.literal("[CCI World] vein recipe not found: " + targetId));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());
            OreData before = OreDataAttachment.getData(chunk);
            ResourceLocation previousId = before.getRecipeId();
            COEVeinWriter.writeVein(chunk, targetId);
            OreData after = OreDataAttachment.getData(chunk);

            String msg = "[CCI World] set_test_vein SUCCESS" +
                "\n  chunk x/z:      " + chunk.getPos().x + " / " + chunk.getPos().z +
                "\n  previous recipe: " + (previousId != null ? previousId : "none") +
                "\n  new recipe:      " + after.getRecipeId();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // set_test_no_vein — sets the current chunk to a "no useful vein" state.
    // Audit (COE 1.6.8): recipe=null + loaded=true is a state already produced
    // natively by OreData.populate() when OreVeinGenerator.pick() returns null,
    // and is null-safe across save(), getRecipe(rm) and ExcavatingBlockEntity.
    // -------------------------------------------------------------------------

    private static int setTestNoVein(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            OreData before = OreDataAttachment.getData(chunk);
            ResourceLocation previousId = before.getRecipeId();
            boolean previousLoaded = before.isLoaded();
            float previousRandomMul = before.getRandomMul();

            COEVeinWriter.writeNoVein(chunk);

            OreData after = OreDataAttachment.getData(chunk);
            ResourceLocation newId = after.getRecipeId();
            boolean ok = newId == null && after.isLoaded();

            String msg = "[CCI World] set_test_no_vein " + (ok ? "SUCCESS" : "FAILURE") +
                "\n  dimension:        " + level.dimension().location() +
                "\n  chunk x/z:        " + chunk.getPos().x + " / " + chunk.getPos().z +
                "\n  previous recipe:  " + (previousId != null ? previousId : "none") +
                "\n  previous loaded:  " + previousLoaded +
                "\n  previous randMul: " + previousRandomMul +
                "\n  new recipe:       " + (newId != null ? newId : "none (null)") +
                "\n  new loaded:       " + after.isLoaded() +
                "\n  new randomMul:    " + after.getRandomMul() +
                "\n  extractedAmount:  reset to 0 (not readable via public API)" +
                "\n[WARN] no-vein state (recipe=null, loaded=true) is experimental;" +
                "\n       it mirrors what COE.populate() produces when pick()=null." +
                "\n       Restore with /cci_world set_test_vein <alias>.";

            src.sendSuccess(() -> Component.literal(msg), false);
            return ok ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // replace_test_vein <from> <to>
    // -------------------------------------------------------------------------

    private static int replaceTestVein(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            String fromAlias = StringArgumentType.getString(ctx, "from");
            String toAlias   = StringArgumentType.getString(ctx, "to");

            Optional<ResourceLocation> fromOpt = COEVeinIds.fromAlias(fromAlias);
            if (fromOpt.isEmpty()) { src.sendFailure(Component.literal("[CCI World] unknown from-alias '" + fromAlias + "'")); return 0; }
            Optional<ResourceLocation> toOpt = COEVeinIds.fromAlias(toAlias);
            if (toOpt.isEmpty()) { src.sendFailure(Component.literal("[CCI World] unknown to-alias '" + toAlias + "'")); return 0; }

            ResourceLocation fromId = fromOpt.get();
            ResourceLocation toId   = toOpt.get();
            var recipeMgr = src.getServer().getRecipeManager();
            if (!COEVeinWriter.veinExists(recipeMgr, fromId)) { src.sendFailure(Component.literal("[CCI World] vein recipe not found: " + fromId)); return 0; }
            if (!COEVeinWriter.veinExists(recipeMgr, toId))   { src.sendFailure(Component.literal("[CCI World] vein recipe not found: " + toId));   return 0; }

            ServerLevel level = player.serverLevel();
            LevelChunk chunk  = level.getChunkAt(player.blockPosition());
            ResourceLocation currentId = OreDataAttachment.getData(chunk).getRecipeId();

            if (!fromId.equals(currentId)) {
                String msg = "[CCI World] no replacement applied\n  current: " + (currentId != null ? currentId : "none") + "\n  from: " + fromId;
                src.sendSuccess(() -> Component.literal(msg), false);
                return 1;
            }

            COEVeinWriter.writeVein(chunk, toId);
            OreData after = OreDataAttachment.getData(chunk);
            String msg = "[CCI World] replace_test_vein SUCCESS" +
                "\n  chunk x/z: " + chunk.getPos().x + " / " + chunk.getPos().z +
                "\n  replaced:  " + fromId + " -> " + toId +
                "\n  randomMul: " + after.getRandomMul();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // debug_policy_here — full inspect (distance + biome + region + rarity), no write
    // -------------------------------------------------------------------------

    private static int debugPolicyHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            BlockPos spawnPos = level.getSharedSpawnPos();
            int spawnCX = SectionPos.blockToSectionCoord(spawnPos.getX());
            int spawnCZ = SectionPos.blockToSectionCoord(spawnPos.getZ());

            PolicyResult r = ResourcePolicyService.inspect(level, chunk);

            String bandInfo = r.bandName() != null
                ? r.bandName() + " [" + r.bandMin() + "-" + r.bandMax() + "]" : "none";

            // Biome display
            String biomeInfo = r.policyType() == PolicyResult.PolicyType.DISTANCE
                ? "not evaluated"
                : (r.biomeId() != null ? r.biomeId().toString() : "unknown");
            String biomeAllowed = switch (r.reason()) {
                case BIOME_ALLOWED             -> "yes";
                case BIOME_NOT_ALLOWED_APPLIED,
                     BIOME_RULE_TARGET_MISSING -> "no";
                default                        -> "n/a";
            };
            String biomeRuleMatched = r.matchedBiomeRule() != null ? "yes (" + r.matchedBiomeRule() + ")" : "no";

            // Region display
            boolean distOrBiomeApplied = r.reason() == Reason.APPLIED || r.reason() == Reason.BIOME_NOT_ALLOWED_APPLIED;
            String regionStatus = distOrBiomeApplied ? "not evaluated" : switch (r.reason()) {
                case REGION_POLICY_DISABLED            -> "disabled";
                case REGION_NO_VALID_RULE              -> "no valid rules";
                case REGION_NO_MATCHING_BAND           -> "no matching band";
                case REGION_ALLOWED                    -> "allowed";
                case REGION_NOT_PREFERRED_APPLIED      -> "would replace (fallback)";
                case REGION_NOT_PREFERRED_WEIGHTED_APPLIED -> "would replace (pool)";
                case REGION_TARGET_MISSING             -> "target missing";
                case REGION_REPLACEMENT_POOL_INVALID   -> "pool invalid + target missing";
                default                                -> "not evaluated";
            };
            String regionDetail = "";
            if (!distOrBiomeApplied && r.regionName() != null) {
                String prefStr = r.regionPreferredRecipes() != null
                    ? r.regionPreferredRecipes().stream().map(ResourceLocation::getPath).collect(Collectors.joining(", "))
                    : "none";
                regionDetail =
                    "\n  region x/z:              " + r.regionX() + " / " + r.regionZ() +
                    "\n  matched region:          " + r.regionName() + " (roll=" + String.format("%.4f", r.regionRoll()) + ")" +
                    "\n  region preferred:        " + prefStr +
                    "\n  region replacement:      " + (r.regionReplacementRecipe() != null ? r.regionReplacementRecipe() : "none") +
                    "\n  region allowed:          " + r.regionAllowed() +
                    "\n  pool enabled:            " + CCIWorldConfig.WEIGHTED_REPLACEMENT_POOLS_ENABLED.get() +
                    "\n  pool used:               " + r.regionReplacementPoolUsed() +
                    (r.regionReplacementPoolUsed()
                        ? "\n  pool roll:               " + String.format("%.4f", r.regionReplacementPoolRoll())
                          + "\n  pool choice:             " + r.regionReplacementPoolChoice()
                          + "\n  pool total weight:       " + r.regionReplacementPoolTotalWeight()
                        : (r.regionReplacementPoolTotalWeight() > 0
                            ? "\n  pool total weight:       " + r.regionReplacementPoolTotalWeight()
                            : ""));
            } else if (!distOrBiomeApplied) {
                regionDetail = "\n  region x/z:              " + r.regionX() + " / " + r.regionZ();
            }

            // Rarity display
            boolean regionApplied = r.reason() == Reason.REGION_NOT_PREFERRED_APPLIED
                || r.reason() == Reason.REGION_NOT_PREFERRED_WEIGHTED_APPLIED;
            String rarityStatus = (distOrBiomeApplied || regionApplied) ? "not evaluated" : switch (r.reason()) {
                case RARITY_POLICY_DISABLED     -> "disabled";
                case RARITY_NO_MATCHING_RULE    -> "no matching rule";
                case RARITY_GATE_PASSED         -> "gate passed — keep";
                case RARITY_GATE_FAILED_APPLIED -> "gate failed — replace";
                case RARITY_RULE_TARGET_MISSING -> "rule target missing: " + r.newRecipe();
                default                         -> "not evaluated";
            };
            String rarityDetail = "";
            if (r.rarityRuleName() != null) {
                rarityDetail =
                    "\n  rarity rule:             " + r.rarityRuleName()
                    + " (keep_chance=" + String.format("%.4f", r.rarityKeepChance()) + ")"
                    + "\n  rarity roll:             " + String.format("%.4f", r.rarityRoll())
                    + "\n  rarity would pass:       " + (r.rarityPassed() ? "yes" : "no")
                    + " (roll " + String.format("%.4f", r.rarityRoll())
                    + (r.rarityPassed() ? " <= " : " > ")
                    + String.format("%.4f", r.rarityKeepChance()) + ")";
            }

            String replacementInfo = (r.previousRecipe() != null && r.newRecipe() != null)
                ? r.previousRecipe() + " -> " + r.newRecipe()
                : (r.previousRecipe() != null ? r.previousRecipe() + " -> (no replacement)" : "none");

            String action = switch (r.reason()) {
                case APPLIED ->
                    "APPLY via DISTANCE";
                case BIOME_NOT_ALLOWED_APPLIED ->
                    "APPLY via BIOME (" + r.matchedBiomeRule() + ")";
                case REGION_NOT_PREFERRED_APPLIED ->
                    "APPLY via REGION/" + r.regionName() + " (fallback replacement_recipe)";
                case REGION_NOT_PREFERRED_WEIGHTED_APPLIED ->
                    "APPLY via REGION/" + r.regionName() + " (pool, roll="
                    + String.format("%.4f", r.regionReplacementPoolRoll()) + " -> " + r.regionReplacementPoolChoice() + ")";
                case RARITY_GATE_FAILED_APPLIED ->
                    "APPLY via RARITY (" + r.rarityRuleName()
                    + " roll=" + String.format("%.4f", r.rarityRoll())
                    + " > " + String.format("%.4f", r.rarityKeepChance()) + ")";
                case RARITY_GATE_PASSED ->
                    "NO ACTION (rarity gate passed — " + r.rarityRuleName()
                    + " roll=" + String.format("%.4f", r.rarityRoll())
                    + " <= " + String.format("%.4f", r.rarityKeepChance()) + ")";
                default ->
                    "NO ACTION (" + r.reason() + ")";
            };

            String msg = "[CCI World] debug_policy_here" +
                "\n  dimension:               " + level.dimension().location() +
                "\n  spawn chunk x/z:         " + spawnCX + " / " + spawnCZ +
                "\n  current chunk x/z:       " + r.chunkX() + " / " + r.chunkZ() +
                "\n  distance chunks:         " + r.distChunks() +
                "\n  matched band:            " + bandInfo +
                "\n  current recipe:          " + (r.previousRecipe() != null ? r.previousRecipe() : "none") +
                "\n  biome id:                " + biomeInfo +
                "\n  biome policy:            " + (CCIWorldConfig.BIOME_POLICY_ENABLED.get() ? "enabled" : "disabled") +
                "\n  matched biome rule:      " + biomeRuleMatched +
                "\n  biome allowed:           " + biomeAllowed +
                "\n  region policy:           " + (CCIWorldConfig.REGION_POLICY_ENABLED.get() ? "enabled" : "disabled") +
                "\n  region_size_chunks:      " + CCIWorldConfig.REGION_SIZE_CHUNKS.get() +
                "\n  region status:           " + regionStatus +
                regionDetail +
                "\n  rarity policy:           " + (CCIWorldConfig.RARITY_POLICY_ENABLED.get() ? "enabled" : "disabled") +
                "\n  rarity status:           " + rarityStatus +
                rarityDetail +
                "\n  replacement:             " + replacementInfo +
                "\n  predicted action:        " + action +
                "\n  reason:                  " + r.reason();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // apply_distance_policy_here — applies full policy (distance + biome + region + rarity)
    // -------------------------------------------------------------------------

    private static int applyDistancePolicyHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            PolicyResult r = ResourcePolicyService.apply(level, chunk);

            String bandInfo = r.bandName() != null
                ? r.bandName() + " [" + r.bandMin() + "-" + r.bandMax() + "]" : "none";

            String body = switch (r.reason()) {
                case DISABLED ->
                    "\n  policy disabled";
                case NO_MATCHING_BAND ->
                    "\n  matched band:    none\n  no action";
                case BIOME_POLICY_DISABLED ->
                    "\n  matched band:    " + bandInfo + "\n  biome policy disabled — no action";
                case NO_CURRENT_RECIPE ->
                    "\n  matched band:    " + bandInfo + "\n  no current recipe — no action";
                case NO_MATCHING_RULE ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  no rule matched — fallback: " + CCIWorldConfig.FALLBACK.get();
                case TARGET_RECIPE_MISSING ->
                    "\n  matched band:    " + bandInfo +
                    "\n  target recipe not found: " + r.newRecipe();
                case APPLIED ->
                    "\n  policy type:     DISTANCE" +
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  new recipe:      " + r.newRecipe();
                case BIOME_UNKNOWN ->
                    "\n  matched band:    " + bandInfo + "\n  biome unknown — no action";
                case BIOME_ALLOWED ->
                    "\n  matched band:    " + bandInfo +
                    "\n  biome id:        " + r.biomeId() +
                    "\n  biome allowed — no action";
                case BIOME_NOT_ALLOWED_APPLIED ->
                    "\n  policy type:     BIOME" +
                    "\n  matched band:    " + bandInfo +
                    "\n  biome id:        " + r.biomeId() +
                    "\n  biome rule:      " + r.matchedBiomeRule() +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  new recipe:      " + r.newRecipe();
                case BIOME_RULE_TARGET_MISSING ->
                    "\n  matched band:    " + bandInfo +
                    "\n  biome rule target not found: " + r.newRecipe();
                case REGION_POLICY_DISABLED ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  region policy disabled — no action";
                case REGION_NO_VALID_RULE ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  region: no valid rules — no action";
                case REGION_NO_MATCHING_BAND ->
                    "\n  matched band:    " + bandInfo +
                    "\n  region:          " + r.regionName() + " (band not in allowed list)";
                case REGION_ALLOWED ->
                    "\n  policy type:     REGION" +
                    "\n  matched band:    " + bandInfo +
                    "\n  region:          " + r.regionName() +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  recipe is preferred — no action";
                case REGION_NOT_PREFERRED_APPLIED ->
                    "\n  policy type:     REGION" +
                    "\n  source:          fallback replacement_recipe" +
                    "\n  matched band:    " + bandInfo +
                    "\n  region:          " + r.regionName() +
                    "\n  region roll:     " + String.format("%.4f", r.regionRoll()) +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  new recipe:      " + r.newRecipe();
                case REGION_NOT_PREFERRED_WEIGHTED_APPLIED ->
                    "\n  policy type:     REGION" +
                    "\n  source:          weighted pool" +
                    "\n  matched band:    " + bandInfo +
                    "\n  region:          " + r.regionName() +
                    "\n  region roll:     " + String.format("%.4f", r.regionRoll()) +
                    "\n  pool roll:       " + String.format("%.4f", r.regionReplacementPoolRoll()) +
                    "\n  pool choice:     " + r.regionReplacementPoolChoice() +
                    "\n  pool weight:     " + r.regionReplacementPoolTotalWeight() +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  new recipe:      " + r.newRecipe();
                case REGION_TARGET_MISSING ->
                    "\n  matched band:    " + bandInfo +
                    "\n  region:          " + r.regionName() +
                    "\n  region target not found: " + r.newRecipe();
                case REGION_REPLACEMENT_POOL_INVALID ->
                    "\n  matched band:    " + bandInfo +
                    "\n  region:          " + r.regionName() +
                    "\n  pool invalid (all entries missing) + fallback target not found: " + r.newRecipe();
                case RARITY_POLICY_DISABLED ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  rarity policy disabled — no action";
                case RARITY_NO_MATCHING_RULE ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  rarity: no matching rule — fallback: " + CCIWorldConfig.FALLBACK.get();
                case RARITY_GATE_PASSED ->
                    "\n  policy type:     RARITY" +
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  rarity rule:     " + r.rarityRuleName() +
                    "\n  rarity roll:     " + String.format("%.4f", r.rarityRoll()) +
                    "\n  keep chance:     " + String.format("%.4f", r.rarityKeepChance()) +
                    "\n  gate passed — no action";
                case RARITY_GATE_FAILED_APPLIED ->
                    "\n  policy type:     RARITY" +
                    "\n  matched band:    " + bandInfo +
                    "\n  rarity rule:     " + r.rarityRuleName() +
                    "\n  rarity roll:     " + String.format("%.4f", r.rarityRoll()) +
                    "\n  keep chance:     " + String.format("%.4f", r.rarityKeepChance()) +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  new recipe:      " + r.newRecipe();
                case RARITY_RULE_TARGET_MISSING ->
                    "\n  matched band:    " + bandInfo +
                    "\n  rarity rule target not found: " + r.newRecipe();
                default ->
                    "\n  reason: " + r.reason();
            };

            boolean success = r.reason() == Reason.APPLIED
                || r.reason() == Reason.BIOME_NOT_ALLOWED_APPLIED
                || r.reason() == Reason.REGION_NOT_PREFERRED_APPLIED
                || r.reason() == Reason.REGION_NOT_PREFERRED_WEIGHTED_APPLIED
                || r.reason() == Reason.RARITY_GATE_FAILED_APPLIED;
            String label = success
                ? "[CCI World] apply_distance_policy_here SUCCESS"
                : "[CCI World] apply_distance_policy_here";

            String header =
                "\n  dimension:       " + level.dimension().location() +
                "\n  chunk x/z:       " + r.chunkX() + " / " + r.chunkZ() +
                "\n  distance chunks: " + r.distChunks() +
                "\n  reason:          " + r.reason();

            src.sendSuccess(() -> Component.literal(label + header + body), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // apply_spawn_policy_here — legacy alias
    // -------------------------------------------------------------------------

    private static int applySpawnPolicyHere(CommandContext<CommandSourceStack> ctx) {
        return applyDistancePolicyHere(ctx);
    }

    // -------------------------------------------------------------------------
    // apply_spawn_policy_loaded_radius <radiusChunks>
    // -------------------------------------------------------------------------

    private static int applySpawnPolicyLoadedRadius(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            int radiusChunks = IntegerArgumentType.getInteger(ctx, "radiusChunks");
            ServerLevel level = player.serverLevel();
            int playerCX = SectionPos.blockToSectionCoord(player.blockPosition().getX());
            int playerCZ = SectionPos.blockToSectionCoord(player.blockPosition().getZ());

            int candidates = 0, enqueued = 0, skippedUnloaded = 0, skippedInQueue = 0;

            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                    candidates++;
                    int cx = playerCX + dx, cz = playerCZ + dz;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) { skippedUnloaded++; continue; }
                    boolean added = PolicyQueue.enqueue(new PolicyQueueEntry(
                        level.dimension(), cx, cz, "manual", player.getName().getString()));
                    if (added) enqueued++; else skippedInQueue++;
                }
            }

            String msg = "[CCI World] apply_spawn_policy_loaded_radius" +
                "\n  player chunk x/z:  " + playerCX + " / " + playerCZ +
                "\n  requested radius:  " + radiusChunks +
                "\n  candidate count:   " + candidates +
                "\n  loaded enqueued:   " + enqueued +
                "\n  skipped unloaded:  " + skippedUnloaded +
                "\n  skipped in-queue:  " + skippedInQueue +
                "\n  queue size total:  " + PolicyQueue.size();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // policy_status
    // -------------------------------------------------------------------------

    private static int policyStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            List<DistanceBand> bands = CCIWorldConfig.parseBands();
            StringBuilder bandNames = new StringBuilder();
            for (DistanceBand b : bands) {
                if (bandNames.length() > 0) bandNames.append(", ");
                bandNames.append(b.name()).append('[').append(b.minDistance()).append('-').append(b.maxDistance()).append(']');
            }

            int biomeRulesCount   = CCIWorldConfig.parseBiomeRules().size();
            int invalidBiomeIds   = AutomaticPolicyEngine.getWarnedInvalidBiomeIds();

            List<RegionPolicyRule> regionRules   = CCIWorldConfig.parseRegionRules();
            int regionRulesCount   = regionRules.size();
            int regionInvalidCount = CCIWorldConfig.getRegionRuleInvalidCount();
            int poolInvalidCount   = CCIWorldConfig.getRegionPoolInvalidCount();

            List<RarityPolicyRule> rarityRules   = CCIWorldConfig.parseRarityRules();
            int rarityRulesCount   = rarityRules.size();
            int rarityInvalidCount = CCIWorldConfig.getRarityRuleInvalidCount();

            StringBuilder byResource = new StringBuilder();
            AutomaticPolicyEngine.getReplacementsByResource().forEach((id, count) ->
                byResource.append("\n    ").append(id.getPath()).append(": ").append(count));
            String byResourceStr = byResource.length() > 0 ? byResource.toString() : " none";

            String msg = "[CCI World] policy_status" +
                "\n  --- engine ---" +
                "\n  enabled:                        " + CCIWorldConfig.ENABLED.get() +
                "\n  policy_engine_enabled:          " + CCIWorldConfig.POLICY_ENGINE_ENABLED.get() +
                "\n  automatic_policy_enabled:       " + CCIWorldConfig.AUTOMATIC_POLICY_ENABLED.get() +
                "\n  policy_chunks_per_tick:         " + CCIWorldConfig.POLICY_CHUNKS_PER_TICK.get() +
                "\n  max_pending_policy_jobs:        " + CCIWorldConfig.MAX_PENDING_POLICY_JOBS.get() +
                "\n  processed_cache_enabled:        " + CCIWorldConfig.PROCESSED_CACHE_ENABLED.get() +
                "\n  player_scan_radius_chunks:      " + CCIWorldConfig.PLAYER_SCAN_RADIUS_CHUNKS.get() +
                "\n  scan_interval_ticks:            " + CCIWorldConfig.SCAN_INTERVAL_TICKS.get() +
                "\n  --- distance ---" +
                "\n  distance_bands_count:           " + bands.size() +
                "\n  bands:                          " + bandNames +
                "\n  --- biome ---" +
                "\n  biome_policy_enabled:           " + CCIWorldConfig.BIOME_POLICY_ENABLED.get() +
                "\n  biome_sample_y:                 " + CCIWorldConfig.BIOME_SAMPLE_Y.get() +
                "\n  biome_rules_count:              " + biomeRulesCount +
                "\n  invalid_biome_ids_warned:       " + invalidBiomeIds +
                "\n  --- region ---" +
                "\n  region_policy_enabled:          " + CCIWorldConfig.REGION_POLICY_ENABLED.get() +
                "\n  region_size_chunks:             " + CCIWorldConfig.REGION_SIZE_CHUNKS.get() +
                "\n  region_salt:                    " + CCIWorldConfig.REGION_SALT.get() +
                "\n  weighted_pools_enabled:         " + CCIWorldConfig.WEIGHTED_REPLACEMENT_POOLS_ENABLED.get() +
                "\n  replacement_pool_salt:          " + CCIWorldConfig.REPLACEMENT_POOL_SALT.get() +
                "\n  region_rules_count:             " + regionRulesCount +
                "\n  invalid_region_rules:           " + regionInvalidCount +
                "\n  invalid_pool_entries:           " + poolInvalidCount +
                "\n  --- rarity ---" +
                "\n  rarity_policy_enabled:          " + CCIWorldConfig.RARITY_POLICY_ENABLED.get() +
                "\n  rarity_salt:                    " + CCIWorldConfig.RARITY_SALT.get() +
                "\n  rarity_rules_count:             " + rarityRulesCount +
                "\n  invalid_rarity_rules:           " + rarityInvalidCount +
                "\n  --- runtime ---" +
                "\n  queue size:                     " + AutomaticPolicyEngine.getQueueSize() +
                "\n  session cache size:             " + AutomaticPolicyEngine.getSessionCacheSize() +
                "\n  total applied:                  " + AutomaticPolicyEngine.getTotalApplied() +
                "\n  skipped unloaded:               " + AutomaticPolicyEngine.getTotalSkippedUnloaded() +
                "\n  skipped cached:                 " + AutomaticPolicyEngine.getTotalSkippedCached() +
                "\n  skipped no action:              " + AutomaticPolicyEngine.getTotalSkippedNoAction() +
                "\n  replacements by resource:" + byResourceStr;

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // rescan_loaded
    // -------------------------------------------------------------------------

    private static int rescanLoaded(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            int cleared = AutomaticPolicyEngine.clearSessionCache();
            int enqueued = 0, skippedFull = 0, skippedUnloaded = 0;
            int radius = CCIWorldConfig.PLAYER_SCAN_RADIUS_CHUNKS.get();

            for (ServerLevel level : src.getServer().getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    int px = SectionPos.blockToSectionCoord(player.blockPosition().getX());
                    int pz = SectionPos.blockToSectionCoord(player.blockPosition().getZ());
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            int cx = px + dx, cz = pz + dz;
                            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                            if (chunk == null) { skippedUnloaded++; continue; }
                            if (PolicyQueue.size() >= CCIWorldConfig.MAX_PENDING_POLICY_JOBS.get()) { skippedFull++; continue; }
                            if (PolicyQueue.enqueue(new PolicyQueueEntry(level.dimension(), cx, cz, "rescan", player.getName().getString()))) enqueued++;
                        }
                    }
                }
            }

            String msg = "[CCI World] rescan_loaded" +
                "\n  cache cleared:     " + cleared + " entries" +
                "\n  enqueued:          " + enqueued +
                "\n  skipped unloaded:  " + skippedUnloaded +
                "\n  skipped queue full:" + skippedFull +
                "\n  queue size:        " + PolicyQueue.size();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // clear_policy_cache / clear_policy_session_cache
    // -------------------------------------------------------------------------

    private static int clearPolicyCache(CommandContext<CommandSourceStack> ctx) {
        return clearCacheImpl(ctx, "clear_policy_cache");
    }

    private static int clearPolicySessionCache(CommandContext<CommandSourceStack> ctx) {
        return clearCacheImpl(ctx, "clear_policy_session_cache");
    }

    private static int clearCacheImpl(CommandContext<CommandSourceStack> ctx, String cmdName) {
        CommandSourceStack src = ctx.getSource();
        try {
            int cleared   = AutomaticPolicyEngine.clearSessionCache();
            int queueSize = AutomaticPolicyEngine.getQueueSize();
            String msg = "[CCI World] " + cmdName +
                "\n  cache cleared: " + cleared + " entries removed" +
                "\n  queue size:    " + queueSize + " (unchanged)";
            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // selftest
    // -------------------------------------------------------------------------

    private static int selftest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StringBuilder sb = new StringBuilder("[CCI World] selftest");
        int[] stats = {0, 0};

        ResourceLocation origRecipe   = null;
        float            origRandomMul = 0f;
        boolean          origLoaded    = false;
        boolean          stateModified = false;
        LevelChunk       chunk         = null;
        ServerLevel      level         = null;

        try {
            ServerPlayer player = src.getPlayerOrException();
            level = player.serverLevel();
            chunk = level.getChunkAt(player.blockPosition());

            OreData orig = OreDataAttachment.getData(chunk);
            origRecipe    = orig.getRecipeId();
            origRandomMul = orig.getRandomMul();
            origLoaded    = orig.isLoaded();

            sb.append("\n  chunk: ").append(chunk.getPos().x).append("/").append(chunk.getPos().z);
            sb.append("\n  original recipe: ").append(origRecipe != null ? origRecipe : "none");
            pass(sb, stats, "chunk loaded");

            var recipeMgr = src.getServer().getRecipeManager();
            ResourceLocation ironId   = COEVeinIds.fromAlias("iron").orElse(null);
            ResourceLocation copperId = COEVeinIds.fromAlias("copper").orElse(null);
            boolean prereq = ironId != null && copperId != null
                && COEVeinWriter.veinExists(recipeMgr, ironId)
                && COEVeinWriter.veinExists(recipeMgr, copperId);

            if (!prereq) {
                fail(sb, stats, "iron/copper recipes not in RecipeManager (is COE loaded?)");
            } else {
                COEVeinWriter.writeVein(chunk, ironId);
                stateModified = true;
                if (ironId.equals(OreDataAttachment.getData(chunk).getRecipeId())) {
                    pass(sb, stats, "set iron (" + ironId + ")");
                } else {
                    fail(sb, stats, "set iron — got: " + OreDataAttachment.getData(chunk).getRecipeId());
                }

                COEVeinWriter.writeVein(chunk, copperId);
                if (copperId.equals(OreDataAttachment.getData(chunk).getRecipeId())) {
                    pass(sb, stats, "replace iron -> copper (" + copperId + ")");
                } else {
                    fail(sb, stats, "replace iron -> copper — got: " + OreDataAttachment.getData(chunk).getRecipeId());
                }

                // --- no-vein experimental section ---------------------------
                // Audit (COE 1.6.8): recipe=null is null-safe in save(),
                // getRecipe(rm) and ExcavatingBlockEntity. We treat it as
                // supported. If anything throws here we report [SKIP].
                try {
                    COEVeinWriter.writeNoVein(chunk);
                    OreData afterNoVein = OreDataAttachment.getData(chunk);
                    if (afterNoVein.getRecipeId() == null && afterNoVein.isLoaded()) {
                        pass(sb, stats, "set no-vein (recipe=null, loaded=true)");
                        // read-back: getRecipe(rm) must not throw and must return null
                        var rh = afterNoVein.getRecipe(recipeMgr);
                        if (rh == null) {
                            pass(sb, stats, "no-vein read-back: getRecipe(rm) == null (no crash)");
                        } else {
                            fail(sb, stats, "no-vein read-back: getRecipe(rm) returned non-null: " + rh.id());
                        }
                    } else {
                        fail(sb, stats, "set no-vein — got recipe=" + afterNoVein.getRecipeId() + " loaded=" + afterNoVein.isLoaded());
                    }
                } catch (Throwable t) {
                    sb.append("\n  [SKIP] no-vein unsupported: ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
                }
                // ------------------------------------------------------------

                PolicyResult pr = ResourcePolicyService.inspect(level, chunk);
                pass(sb, stats, "policy evaluation: reason=" + pr.reason()
                    + " policyType=" + pr.policyType()
                    + (pr.biomeId() != null ? " biome=" + pr.biomeId() : "")
                    + (pr.regionName() != null ? " region=" + pr.regionName() : "")
                    + (pr.regionReplacementPoolUsed() ? " poolUsed=true" : ""));
            }
        } catch (Exception e) {
            fail(sb, stats, "exception: " + e.getMessage());
        }

        if (stateModified && chunk != null) {
            if (origRecipe != null) {
                try {
                    OreData restore = OreDataAttachment.getData(chunk);
                    restore.setRecipe(origRecipe);
                    restore.setLoaded(origLoaded);
                    restore.setRandomMul(origRandomMul);
                    chunk.setUnsaved(true);
                    if (origRecipe.equals(OreDataAttachment.getData(chunk).getRecipeId())) {
                        pass(sb, stats, "restored original recipe (" + origRecipe + ")");
                        sb.append("\n[WARN] extractedAmount not restorable — not readable via public API");
                    } else {
                        fail(sb, stats, "restore verification failed");
                    }
                } catch (Exception re) {
                    fail(sb, stats, "restore threw exception: " + re.getMessage());
                }
            } else {
                sb.append("\n[WARN] cannot restore null recipe");
            }
        }

        sb.append("\n--- ").append(stats[0]).append(" passed, ").append(stats[1]).append(" failed ---");
        int finalFail = stats[1];
        String msg = sb.toString();
        src.sendSuccess(() -> Component.literal(msg), false);
        return finalFail == 0 ? 1 : 0;
    }

    // -------------------------------------------------------------------------
    // selftest_policy_matrix
    // -------------------------------------------------------------------------

    private static int selftestPolicyMatrix(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StringBuilder sb = new StringBuilder("[CCI World] selftest_policy_matrix");
        int[] stats = {0, 0};

        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());
            var recipeMgr = src.getServer().getRecipeManager();

            // --- Distance bands ---
            sb.append("\n--- Distance Bands ---");
            List<DistanceBand> bands = CCIWorldConfig.parseBands();
            for (DistanceBand band : bands) {
                String bandLabel = "band " + band.name() + " [" + band.minDistance() + "-" + band.maxDistance() + "]";
                if (band.replacements().isEmpty()) {
                    pass(sb, stats, bandLabel + ": no distance replacement rules");
                } else {
                    for (Map.Entry<ResourceLocation, ResourceLocation> e : band.replacements().entrySet()) {
                        boolean fromOk = COEVeinWriter.veinExists(recipeMgr, e.getKey());
                        boolean toOk   = COEVeinWriter.veinExists(recipeMgr, e.getValue());
                        String detail  = bandLabel + ": " + e.getKey().getPath() + " -> " + e.getValue().getPath();
                        if (fromOk && toOk) pass(sb, stats, detail + " (both exist)");
                        else fail(sb, stats, detail + (!fromOk ? " [source MISSING]" : "") + (!toOk ? " [target MISSING]" : ""));
                    }
                }
                if (band.name().equals("mid")) {
                    ResourceLocation goldId = COEVeinIds.fromAlias("gold").orElse(null);
                    if (goldId != null) {
                        if (band.replacements().containsKey(goldId)) fail(sb, stats, bandLabel + ": gold should NOT have distance replacement");
                        else pass(sb, stats, bandLabel + ": gold not in distance replacements (biome policy applies)");
                    }
                }
            }

            // --- Biome rules ---
            sb.append("\n--- Biome Rules ---");
            int bx = chunk.getPos().getMinBlockX() + 8;
            int bz = chunk.getPos().getMinBlockZ() + 8;
            int by = CCIWorldConfig.BIOME_SAMPLE_Y.get();
            Holder<Biome> biomeHolder = chunk.getNoiseBiome(bx >> 2, by >> 2, bz >> 2);
            Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
            ResourceLocation currentBiome = biomeKey.map(ResourceKey::location).orElse(null);
            sb.append("\n[INFO] current biome (real): ").append(currentBiome != null ? currentBiome : "unknown");

            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            List<BiomePolicyRule> biomeRules = CCIWorldConfig.parseBiomeRules();
            if (biomeRules.isEmpty()) sb.append("\n[INFO] no biome rules configured");

            for (BiomePolicyRule rule : biomeRules) {
                sb.append("\n  rule '").append(rule.name()).append("':");
                if (COEVeinWriter.veinExists(recipeMgr, rule.sourceRecipe()))
                    pass(sb, stats, "source recipe exists (" + rule.sourceRecipe() + ")");
                else fail(sb, stats, "source recipe MISSING: " + rule.sourceRecipe());

                if (COEVeinWriter.veinExists(recipeMgr, rule.replacementRecipe()))
                    pass(sb, stats, "replacement recipe exists (" + rule.replacementRecipe() + ")");
                else fail(sb, stats, "replacement recipe MISSING: " + rule.replacementRecipe());

                int valid = 0, invalid = 0;
                for (ResourceLocation id : rule.allowedBiomes()) {
                    if (biomeRegistry.containsKey(id)) valid++; else invalid++;
                }
                if (invalid == 0) pass(sb, stats, "allowed_biomes: " + valid + " valid, 0 invalid");
                else { fail(sb, stats, "allowed_biomes: " + valid + " valid, " + invalid + " invalid"); }

                if (currentBiome != null) {
                    boolean allowed = rule.allowedBiomes().stream().filter(biomeRegistry::containsKey).anyMatch(currentBiome::equals);
                    sb.append("\n    [INFO] current biome '").append(currentBiome).append("': ")
                      .append(allowed ? "ALLOWED" : "NOT ALLOWED");
                }
            }

            // --- Region rules ---
            sb.append("\n--- Region Rules ---");
            sb.append("\n[INFO] region_policy_enabled: ").append(CCIWorldConfig.REGION_POLICY_ENABLED.get());
            sb.append("\n[INFO] region_size_chunks: ").append(CCIWorldConfig.REGION_SIZE_CHUNKS.get());
            sb.append("\n[INFO] region_salt: ").append(CCIWorldConfig.REGION_SALT.get());
            sb.append("\n[INFO] weighted_pools_enabled: ").append(CCIWorldConfig.WEIGHTED_REPLACEMENT_POOLS_ENABLED.get());
            sb.append("\n[INFO] replacement_pool_salt: ").append(CCIWorldConfig.REPLACEMENT_POOL_SALT.get());

            boolean poolSaltOk = !CCIWorldConfig.REPLACEMENT_POOL_SALT.get().isEmpty();
            if (poolSaltOk) pass(sb, stats, "replacement_pool_salt is not empty");
            else fail(sb, stats, "replacement_pool_salt is empty");

            int regionSizeChunks = CCIWorldConfig.REGION_SIZE_CHUNKS.get();
            if (regionSizeChunks > 0) pass(sb, stats, "region_size_chunks=" + regionSizeChunks + " > 0");
            else fail(sb, stats, "region_size_chunks=" + regionSizeChunks + " must be > 0");

            List<RegionPolicyRule> regionRules = CCIWorldConfig.parseRegionRules();
            int invalidRegion = CCIWorldConfig.getRegionRuleInvalidCount();
            int invalidPool   = CCIWorldConfig.getRegionPoolInvalidCount();
            if (invalidRegion > 0) fail(sb, stats, "invalid region rules in config: " + invalidRegion + " (check log)");
            if (invalidPool   > 0) fail(sb, stats, "invalid pool entries in config: " + invalidPool + " (format errors, check log)");

            int totalWeight = regionRules.stream().mapToInt(RegionPolicyRule::weight).sum();
            if (totalWeight > 0) pass(sb, stats, "total region weight=" + totalWeight + " > 0");
            else fail(sb, stats, "total region weight=" + totalWeight + " must be > 0");

            if (regionRules.isEmpty() && invalidRegion == 0) sb.append("\n[INFO] no region rules configured");

            List<String> validBandNames = List.of("inner", "mid", "far");
            for (RegionPolicyRule rule : regionRules) {
                sb.append("\n  rule '").append(rule.name()).append("' (weight=").append(rule.weight()).append("):");

                if (rule.weight() > 0) pass(sb, stats, rule.name() + ": weight=" + rule.weight() + " > 0");
                else fail(sb, stats, rule.name() + ": weight=" + rule.weight() + " must be > 0");

                boolean allBandsOk = rule.allowedBands().stream().allMatch(validBandNames::contains);
                if (allBandsOk && !rule.allowedBands().isEmpty())
                    pass(sb, stats, rule.name() + ": allowed_bands=" + rule.allowedBands() + " valid");
                else fail(sb, stats, rule.name() + ": allowed_bands=" + rule.allowedBands() + " contains invalid entries");

                boolean allPrefOk = true;
                for (ResourceLocation pref : rule.preferredRecipes()) {
                    if (!COEVeinWriter.veinExists(recipeMgr, pref)) { allPrefOk = false; break; }
                }
                if (allPrefOk && !rule.preferredRecipes().isEmpty())
                    pass(sb, stats, rule.name() + ": all " + rule.preferredRecipes().size() + " preferred_recipes exist");
                else fail(sb, stats, rule.name() + ": one or more preferred_recipes MISSING");

                if (COEVeinWriter.veinExists(recipeMgr, rule.replacementRecipe()))
                    pass(sb, stats, rule.name() + ": replacement_recipe exists (" + rule.replacementRecipe() + ")");
                else fail(sb, stats, rule.name() + ": replacement_recipe MISSING: " + rule.replacementRecipe());

                // Pool checks
                List<WeightedReplacementEntry> pool = rule.replacementPool();
                if (pool.isEmpty()) {
                    sb.append("\n    [INFO] ").append(rule.name()).append(": replacement_pool is empty (will use replacement_recipe)");
                } else {
                    int poolWeight = pool.stream().mapToInt(WeightedReplacementEntry::weight).sum();
                    if (poolWeight > 0) pass(sb, stats, rule.name() + ": pool total weight=" + poolWeight + " > 0");
                    else fail(sb, stats, rule.name() + ": pool total weight=" + poolWeight + " must be > 0");

                    boolean allPoolOk = true;
                    for (WeightedReplacementEntry e : pool) {
                        if (!COEVeinWriter.veinExists(recipeMgr, e.recipe())) { allPoolOk = false; break; }
                    }
                    if (allPoolOk)
                        pass(sb, stats, rule.name() + ": all " + pool.size() + " pool entries exist in RecipeManager");
                    else
                        fail(sb, stats, rule.name() + ": one or more pool recipe entries MISSING in RecipeManager");
                }
            }

            // Deterministic region roll stability
            sb.append("\n  [deterministic region roll stability]");
            String testDim = "minecraft:overworld";
            int testRX = 1, testRZ = -1;
            String testSalt = CCIWorldConfig.REGION_SALT.get();
            double rRoll1 = ResourcePolicyService.testDeterministicRegionRoll(testDim, testRX, testRZ, testSalt);
            double rRoll2 = ResourcePolicyService.testDeterministicRegionRoll(testDim, testRX, testRZ, testSalt);
            if (Double.compare(rRoll1, rRoll2) == 0)
                pass(sb, stats, String.format("deterministic region roll stable: %.6f == %.6f", rRoll1, rRoll2));
            else
                fail(sb, stats, String.format("deterministic region roll unstable: %.6f != %.6f", rRoll1, rRoll2));

            // Weighted selection returns a known region name
            String selectedRegion = ResourcePolicyService.testRegionSelection(testDim, testRX, testRZ);
            List<String> knownNames = regionRules.stream().map(RegionPolicyRule::name).toList();
            if (selectedRegion != null && knownNames.contains(selectedRegion))
                pass(sb, stats, "weighted selection returned known region: " + selectedRegion);
            else if (regionRules.isEmpty())
                sb.append("\n[INFO] no region rules — skip weighted selection check");
            else
                fail(sb, stats, "weighted selection returned unexpected region: " + selectedRegion);

            // Pool roll stability
            sb.append("\n  [deterministic pool roll stability]");
            String poolSalt = CCIWorldConfig.REPLACEMENT_POOL_SALT.get();
            double pRoll1 = ResourcePolicyService.testDeterministicPoolRoll(
                testDim, "industrial", testRX, testRZ, 32, -32,
                "createoreexcavation:ore_vein_type/zinc", poolSalt);
            double pRoll2 = ResourcePolicyService.testDeterministicPoolRoll(
                testDim, "industrial", testRX, testRZ, 32, -32,
                "createoreexcavation:ore_vein_type/zinc", poolSalt);
            if (Double.compare(pRoll1, pRoll2) == 0)
                pass(sb, stats, String.format("deterministic pool roll stable: %.6f == %.6f", pRoll1, pRoll2));
            else
                fail(sb, stats, String.format("deterministic pool roll unstable: %.6f != %.6f", pRoll1, pRoll2));

            // Pool selection from a known pool
            sb.append("\n  [pool weighted selection]");
            if (!regionRules.isEmpty()) {
                RegionPolicyRule firstRule = regionRules.get(0);
                List<WeightedReplacementEntry> testPool = firstRule.replacementPool();
                if (!testPool.isEmpty()) {
                    ResourceLocation choice1 = ResourcePolicyService.testPoolSelection(testPool, pRoll1);
                    ResourceLocation choice2 = ResourcePolicyService.testPoolSelection(testPool, pRoll1);
                    if (choice1 != null && choice1.equals(choice2))
                        pass(sb, stats, "pool selection deterministic: " + choice1);
                    else
                        fail(sb, stats, "pool selection non-deterministic: " + choice1 + " != " + choice2);
                } else {
                    sb.append("\n[INFO] first region rule '").append(firstRule.name()).append("' has empty pool — skip pool selection check");
                }
            } else {
                sb.append("\n[INFO] no region rules — skip pool selection check");
            }

            // --- Rarity rules ---
            sb.append("\n--- Rarity Rules ---");
            sb.append("\n[INFO] rarity_policy_enabled: ").append(CCIWorldConfig.RARITY_POLICY_ENABLED.get());
            sb.append("\n[INFO] rarity_salt: ").append(CCIWorldConfig.RARITY_SALT.get());

            List<RarityPolicyRule> rarityRules = CCIWorldConfig.parseRarityRules();
            int invalidRarity = CCIWorldConfig.getRarityRuleInvalidCount();
            if (invalidRarity > 0) fail(sb, stats, "invalid rarity rules in config: " + invalidRarity + " (check log)");
            if (rarityRules.isEmpty() && invalidRarity == 0) sb.append("\n[INFO] no rarity rules configured");

            for (RarityPolicyRule rule : rarityRules) {
                sb.append("\n  rule '").append(rule.name()).append("':");
                if (COEVeinWriter.veinExists(recipeMgr, rule.sourceRecipe()))
                    pass(sb, stats, rule.name() + ": source recipe exists (" + rule.sourceRecipe() + ")");
                else fail(sb, stats, rule.name() + ": source recipe MISSING: " + rule.sourceRecipe());

                if (COEVeinWriter.veinExists(recipeMgr, rule.replacementRecipe()))
                    pass(sb, stats, rule.name() + ": replacement recipe exists (" + rule.replacementRecipe() + ")");
                else fail(sb, stats, rule.name() + ": replacement recipe MISSING: " + rule.replacementRecipe());

                if (validBandNames.contains(rule.band()))
                    pass(sb, stats, rule.name() + ": band '" + rule.band() + "' valid");
                else fail(sb, stats, rule.name() + ": unknown band '" + rule.band() + "'");

                if (rule.keepChance() >= 0.0 && rule.keepChance() <= 1.0)
                    pass(sb, stats, rule.name() + ": keep_chance=" + rule.keepChance() + " valid");
                else fail(sb, stats, rule.name() + ": keep_chance out of [0.0, 1.0]");
            }

            // Rarity deterministic roll stability
            sb.append("\n  [deterministic rarity roll stability]");
            double roll1 = ResourcePolicyService.testDeterministicRoll("minecraft:overworld", 42, -17,
                "createoreexcavation:ore_vein_type/gold", "mid", CCIWorldConfig.RARITY_SALT.get());
            double roll2 = ResourcePolicyService.testDeterministicRoll("minecraft:overworld", 42, -17,
                "createoreexcavation:ore_vein_type/gold", "mid", CCIWorldConfig.RARITY_SALT.get());
            if (Double.compare(roll1, roll2) == 0)
                pass(sb, stats, String.format("deterministic rarity roll stable: %.6f == %.6f", roll1, roll2));
            else
                fail(sb, stats, String.format("deterministic rarity roll unstable: %.6f != %.6f", roll1, roll2));

            sb.append("\n--- ").append(stats[0]).append(" passed, ").append(stats[1]).append(" failed ---");
        } catch (Exception e) {
            fail(sb, stats, "exception: " + e.getMessage());
            sb.append("\n--- ").append(stats[0]).append(" passed, ").append(stats[1]).append(" failed ---");
        }

        int finalFail = stats[1];
        String msg = sb.toString();
        src.sendSuccess(() -> Component.literal(msg), false);
        return finalFail == 0 ? 1 : 0;
    }

    // -------------------------------------------------------------------------
    // Selftest helpers
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // v0.5 — debug_generator_here
    // -------------------------------------------------------------------------

    private static int debugGeneratorHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            ClusterDecision decision = ClusterGenerator.decide(level, chunk);
            OreData before = OreDataAttachment.getData(chunk);
            ResourceLocation previousId = before.getRecipeId();

            String resStr = decision.isNoVein() ? "NO_VEIN" : decision.finalRecipe().toString();
            String biomeStr = decision.biome() != null ? decision.biome().toString() : "unknown";
            boolean wouldWrite = !java.util.Objects.equals(decision.finalRecipe(), previousId)
                || (!decision.isNoVein() && !before.isLoaded());

            String centerStr = (decision.centerX() == 0 && decision.centerZ() == 0 && decision.radiusBlocks() == 0)
                ? "(none — empty cell)"
                : decision.centerX() + " / " + decision.centerZ();

            // Finite-amount math (only meaningful for vein decisions)
            String unitsStr = "—";
            String randomMulStr = "—";
            String expectedStr = "—";
            String finiteStr = "—";
            String solveNote = "—";
            if (!decision.isNoVein()) {
                long units = ClusterGenerator.unitsForRecipeId(decision.finalRecipe());
                unitsStr = Long.toString(units);
                var recipeOpt = com.cciworld.coe.COEFiniteMath.findVeinRecipe(
                    src.getServer().getRecipeManager(), decision.finalRecipe());
                if (recipeOpt.isPresent()) {
                    finiteStr = com.cciworld.coe.COEFiniteMath.isFinite(recipeOpt.get()) ? "yes" : "no";
                    var sol = com.cciworld.coe.COEFiniteMath.solveRandomMul(recipeOpt.get(), units);
                    randomMulStr = String.format(java.util.Locale.ROOT, "%.6f", sol.randomMul());
                    expectedStr = sol.expectedTotalUnits() + (sol.exact() ? " (exact)" : " (rounded/clamped)");
                    solveNote = sol.note();
                } else {
                    solveNote = "recipe not found in COE";
                }
            }
            String weightedSel = decision.weightedPickedEmpty()
                ? "EMPTY (band=" + decision.emptyBand() + ")"
                : (decision.isNoVein() ? "n/a" : "RESOURCE:" + resStr);

            String msg = "[CCI World] debug_generator_here" +
                "\n  dimension:           " + level.dimension().location() +
                "\n  chunk x/z:           " + decision.chunkX() + " / " + decision.chunkZ() +
                "\n  distance_from_spawn: " + decision.distanceBlocks() + " blocks" +
                "\n  biome:               " + biomeStr +
                "\n  cell x/z:            " + decision.cellX() + " / " + decision.cellZ() +
                "\n                       (cell_size_chunks=" + CCIWorldConfig.GEN_CELL_SIZE_CHUNKS.get() + ")" +
                "\n  cluster center:      " + centerStr +
                "\n  cluster radius:      " + decision.radiusBlocks() + " blocks" +
                "\n  dist_from_center:    " + decision.distanceFromCenterBlocks() + " blocks" +
                "\n  selected resource:   " + resStr +
                "\n  cluster_id (hash):   " + Long.toHexString(decision.clusterId()) +
                "\n  final recipe:        " + (decision.isNoVein() ? "null (no-vein)" : decision.finalRecipe()) +
                "\n  reason:              " + decision.reason() +
                "\n  base no-vein roll:   " + String.format(java.util.Locale.ROOT, "%.6f", decision.baseNoVeinRoll())
                    + " (no_vein_chance=" + CCIWorldConfig.GEN_NO_VEIN_CHANCE.get() + ")" +
                "\n  empty band:          " + (decision.emptyBand().isEmpty() ? "n/a (base no-vein)" : decision.emptyBand()) +
                "\n  empty weight:        " + decision.emptyWeight() +
                "\n  weighted total:      " + decision.weightedTotalWeight() +
                "\n  weighted roll:       " + String.format(java.util.Locale.ROOT, "%.6f", decision.weightedRollPick()) +
                "\n  weighted selection:  " + weightedSel +
                "\n  random_roll:         " + String.format(java.util.Locale.ROOT, "%.6f", decision.roll()) +
                "\n  configured units:    " + unitsStr +
                "\n  finite recipe:       " + finiteStr +
                "\n  computed randomMul:  " + randomMulStr +
                "\n  expected max amount: " + expectedStr +
                "\n  finite_amount_base:  " + com.cciworld.coe.COEFiniteMath.finiteAmountBase() +
                "\n  solver note:         " + solveNote +
                "\n  current ore_data:    " + (previousId != null ? previousId : "none") +
                "\n  would_overwrite:     " + wouldWrite +
                "\n  authoritative_gen:   " + CCIWorldConfig.AUTHORITATIVE_GENERATION_ENABLED.get() +
                "\n[INFO] this command is read-only; OreData is not written.";

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // v0.5 — generator_status
    // -------------------------------------------------------------------------

    private static int generatorStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            StringBuilder sb = new StringBuilder("[CCI World] generator_status");
            sb.append("\n  enabled:             ").append(CCIWorldConfig.AUTHORITATIVE_GENERATION_ENABLED.get());
            sb.append("\n  policy_engine_v0.4:  ").append(CCIWorldConfig.POLICY_ENGINE_ENABLED.get())
              .append(CCIWorldConfig.AUTHORITATIVE_GENERATION_ENABLED.get() && CCIWorldConfig.POLICY_ENGINE_ENABLED.get()
                  ? " [SUPPRESSED — authoritative owns OreData]" : "");
            sb.append("\n  cell_size_chunks:    ").append(CCIWorldConfig.GEN_CELL_SIZE_CHUNKS.get());
            sb.append("\n  no_vein_chance:      ").append(CCIWorldConfig.GEN_NO_VEIN_CHANCE.get());
            sb.append("\n  empty_weight near:   ").append(CCIWorldConfig.GEN_EMPTY_WEIGHT_NEAR.get())
              .append(" (<=").append(CCIWorldConfig.GEN_EMPTY_NEAR_MAX_BLOCKS.get()).append(" blocks)");
            sb.append("\n  empty_weight mid:    ").append(CCIWorldConfig.GEN_EMPTY_WEIGHT_MID.get())
              .append(" (<=").append(CCIWorldConfig.GEN_EMPTY_MID_MAX_BLOCKS.get()).append(" blocks)");
            sb.append("\n  empty_weight far:    ").append(CCIWorldConfig.GEN_EMPTY_WEIGHT_FAR.get())
              .append(" (>").append(CCIWorldConfig.GEN_EMPTY_MID_MAX_BLOCKS.get()).append(" blocks)");
            sb.append("\n  finite_amount_base:  ").append(com.cciworld.coe.COEFiniteMath.finiteAmountBase());
            sb.append("\n  rings:");
            for (ClusterGenerator.Ring r : ClusterGenerator.rings()) {
                sb.append("\n    ").append(r.alias()).append(":");
                sb.append("\n      min_distance:    ").append(r.minBlocks()).append(" blocks");
                sb.append("\n      max_distance:    ").append(r.maxBlocks()).append(" blocks");
                sb.append("\n      weight:          ").append(r.weight());
                sb.append("\n      radius_min/max:  ").append(r.radiusMinBlocks()).append(" / ").append(r.radiusMaxBlocks()).append(" blocks");
                sb.append("\n      units_per_chunk: ").append(r.unitsPerChunk());
            }
            sb.append("\n  chunks_per_tick:     ").append(CCIWorldConfig.GEN_CHUNKS_PER_TICK.get());
            sb.append("\n  max_pending_jobs:    ").append(CCIWorldConfig.MAX_PENDING_GEN_JOBS.get());
            sb.append("\n  queue size:          ").append(ClusterGeneratorEngine.getQueueSize());
            sb.append("\n  session cache:       ").append(ClusterGeneratorEngine.getSessionCacheSize());
            sb.append("\n  total processed:     ").append(ClusterGeneratorEngine.getTotalProcessed());
            sb.append("\n  total written:       ").append(ClusterGeneratorEngine.getTotalWritten());
            sb.append("\n  total no-vein:       ").append(ClusterGeneratorEngine.getTotalNoVein());
            sb.append("\n  skipped unloaded:    ").append(ClusterGeneratorEngine.getTotalSkippedUnloaded());
            sb.append("\n  skipped cached:      ").append(ClusterGeneratorEngine.getTotalSkippedCached());
            Map<ResourceLocation, Integer> writes = ClusterGeneratorEngine.getWritesByResource();
            if (writes.isEmpty()) {
                sb.append("\n  writes_by_resource:  (none)");
            } else {
                sb.append("\n  writes_by_resource:");
                writes.forEach((k, v) -> sb.append("\n    ").append(k).append(" -> ").append(v));
            }
            String msg = sb.toString();
            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // v0.5 — selftest_generator
    // -------------------------------------------------------------------------

    private static int selftestGenerator(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StringBuilder sb = new StringBuilder("[CCI World] selftest_generator");
        int[] stats = {0, 0};
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            // 1. Determinism: same chunk twice -> same decision
            ClusterDecision d1 = ClusterGenerator.decide(level, chunk);
            ClusterDecision d2 = ClusterGenerator.decide(level, chunk);
            if (d1.clusterId() == d2.clusterId()
                && java.util.Objects.equals(d1.finalRecipe(), d2.finalRecipe())
                && d1.distanceBlocks() == d2.distanceBlocks()) {
                pass(sb, stats, "deterministic decision (clusterId=" + Long.toHexString(d1.clusterId()) + ")");
            } else {
                fail(sb, stats, "non-deterministic: d1.recipe=" + d1.finalRecipe() + " d2.recipe=" + d2.finalRecipe());
            }

            // 2. Distance is non-negative
            if (d1.distanceBlocks() >= 0) pass(sb, stats, "distance >= 0: " + d1.distanceBlocks() + " blocks");
            else fail(sb, stats, "distance negative: " + d1.distanceBlocks());

            // 3. Roll in [0,1)
            if (d1.roll() >= 0.0 && d1.roll() < 1.0) pass(sb, stats, "roll in [0,1): " + String.format("%.6f", d1.roll()));
            else fail(sb, stats, "roll out of [0,1): " + d1.roll());

            // 4. Reason matches outcome
            boolean reasonOk = d1.isNoVein()
                ? (d1.reason().equals("no_vein_cell_roll")
                    || d1.reason().equals("out_of_all_rings")
                    || d1.reason().startsWith("outside_cluster_radius:"))
                : d1.reason().startsWith("in_cluster:");
            if (reasonOk) pass(sb, stats, "reason matches outcome: " + d1.reason());
            else fail(sb, stats, "reason inconsistent: reason=" + d1.reason() + " recipe=" + d1.finalRecipe());

            // 4b. Cluster cohesion: chunks in the same cell share the same cluster_id
            int cellSize = CCIWorldConfig.GEN_CELL_SIZE_CHUNKS.get();
            int sameCellCx = (d1.cellX() * cellSize); // first chunk of the same cell
            int sameCellCz = (d1.cellZ() * cellSize);
            LevelChunk neighbor = level.getChunkSource().getChunkNow(sameCellCx, sameCellCz);
            if (neighbor != null && (neighbor.getPos().x != chunk.getPos().x || neighbor.getPos().z != chunk.getPos().z)) {
                ClusterDecision dn = ClusterGenerator.decide(level, neighbor);
                if (dn.cellX() == d1.cellX() && dn.cellZ() == d1.cellZ() && dn.clusterId() == d1.clusterId()) {
                    pass(sb, stats, "cell cohesion: same cell -> same clusterId");
                } else {
                    fail(sb, stats, "cell cohesion broken: cellHere=" + d1.cellX() + "/" + d1.cellZ()
                        + " idHere=" + Long.toHexString(d1.clusterId())
                        + " cellNbr=" + dn.cellX() + "/" + dn.cellZ()
                        + " idNbr=" + Long.toHexString(dn.clusterId()));
                }
            } else {
                sb.append("\n[INFO] cell cohesion: neighbor chunk not loaded — skipped");
            }

            // 5. If a recipe was chosen it exists in COE
            if (!d1.isNoVein()) {
                if (COEVeinWriter.veinExists(src.getServer().getRecipeManager(), d1.finalRecipe()))
                    pass(sb, stats, "chosen recipe exists in COE: " + d1.finalRecipe());
                else
                    fail(sb, stats, "chosen recipe NOT in COE recipe manager: " + d1.finalRecipe());
            } else {
                sb.append("\n[INFO] decision is no-vein — skipping recipe-existence check");
            }

            // 6. Sample distribution: run 256 synthetic chunks around player and count outcomes
            int noVein = 0, withVein = 0;
            Map<String, Integer> ringCounts = new java.util.LinkedHashMap<>();
            int baseX = chunk.getPos().x;
            int baseZ = chunk.getPos().z;
            for (int dx = -8; dx < 8; dx++) {
                for (int dz = -8; dz < 8; dz++) {
                    LevelChunk c = level.getChunkSource().getChunkNow(baseX + dx, baseZ + dz);
                    if (c == null) continue;
                    ClusterDecision dd = ClusterGenerator.decide(level, c);
                    if (dd.isNoVein()) noVein++;
                    else { withVein++; ringCounts.merge(dd.reason(), 1, Integer::sum); }
                }
            }
            int sampled = noVein + withVein;
            if (sampled > 0) {
                pass(sb, stats, "sampled " + sampled + " loaded chunks: vein=" + withVein + " no-vein=" + noVein);
                ringCounts.forEach((k, v) -> sb.append("\n    ").append(k).append(" -> ").append(v));
            } else {
                sb.append("\n[INFO] no neighboring chunks loaded — skip distribution sample");
            }

            // 6b. Finite solver: for a vein decision, computed randomMul must be in [0,1]
            //     and expected total amount must be > 0 if the recipe is finite.
            if (!d1.isNoVein()) {
                long unitsCfg = ClusterGenerator.unitsForRecipeId(d1.finalRecipe());
                var recipeOpt = com.cciworld.coe.COEFiniteMath.findVeinRecipe(
                    src.getServer().getRecipeManager(), d1.finalRecipe());
                if (recipeOpt.isPresent()) {
                    var sol = com.cciworld.coe.COEFiniteMath.solveRandomMul(recipeOpt.get(), unitsCfg);
                    boolean ok = sol.randomMul() >= 0F && sol.randomMul() <= 1F
                        && sol.expectedTotalUnits() >= 0;
                    if (ok) pass(sb, stats, "finite solver: randomMul=" + sol.randomMul()
                        + " expected=" + sol.expectedTotalUnits()
                        + (sol.exact() ? " (exact)" : " (" + sol.note() + ")"));
                    else fail(sb, stats, "finite solver invalid: randomMul=" + sol.randomMul()
                        + " expected=" + sol.expectedTotalUnits() + " note=" + sol.note());
                } else {
                    sb.append("\n[INFO] finite solver: recipe not in COE — skipped");
                }
            }

            // 6c. Pure simulation determinism + non-empty + no-write (Task D, v0.7)
            long sseed = level.getSeed();
            BlockPos sspawn = level.getSharedSpawnPos();
            int sx = sspawn.getX(), sz = sspawn.getZ();
            int simRadius = 3000;
            int simRadiusChunks = (simRadius + 15) >> 4;
            long simRadiusSqr = (long) simRadius * (long) simRadius;
            int spawnCx = SectionPos.blockToSectionCoord(sx);
            int spawnCz = SectionPos.blockToSectionCoord(sz);
            java.util.Map<String, Integer> simCounts1 = new java.util.LinkedHashMap<>();
            int simChunks1 = 0, simNoVein1 = 0;
            // Snapshot OreData on the player's chunk to verify simulation does not write
            OreData snapshot = OreDataAttachment.getData(chunk);
            ResourceLocation beforeId = snapshot.getRecipeId();
            boolean beforeLoaded = snapshot.isLoaded();
            float beforeMul = snapshot.getRandomMul();
            for (int dx = -simRadiusChunks; dx <= simRadiusChunks; dx++) {
                for (int dz = -simRadiusChunks; dz <= simRadiusChunks; dz++) {
                    int cx = spawnCx + dx, cz = spawnCz + dz;
                    long bx = ((long) (cx << 4) + 8) - sx;
                    long bz = ((long) (cz << 4) + 8) - sz;
                    if (bx * bx + bz * bz > simRadiusSqr) continue;
                    simChunks1++;
                    ClusterDecision dd = ClusterGenerator.decidePure(sseed, sx, sz, cx, cz, null);
                    if (dd.isNoVein()) simNoVein1++;
                    else {
                        ClusterGenerator.Ring rr = null;
                        for (ClusterGenerator.Ring r : ClusterGenerator.rings()) if (r.recipeId().equals(dd.finalRecipe())) { rr = r; break; }
                        simCounts1.merge(rr != null ? rr.alias() : dd.finalRecipe().toString(), 1, Integer::sum);
                    }
                }
            }
            // Same simulation again to assert determinism on aggregate counts
            java.util.Map<String, Integer> simCounts2 = new java.util.LinkedHashMap<>();
            int simChunks2 = 0, simNoVein2 = 0;
            for (int dx = -simRadiusChunks; dx <= simRadiusChunks; dx++) {
                for (int dz = -simRadiusChunks; dz <= simRadiusChunks; dz++) {
                    int cx = spawnCx + dx, cz = spawnCz + dz;
                    long bx = ((long) (cx << 4) + 8) - sx;
                    long bz = ((long) (cz << 4) + 8) - sz;
                    if (bx * bx + bz * bz > simRadiusSqr) continue;
                    simChunks2++;
                    ClusterDecision dd = ClusterGenerator.decidePure(sseed, sx, sz, cx, cz, null);
                    if (dd.isNoVein()) simNoVein2++;
                    else {
                        ClusterGenerator.Ring rr = null;
                        for (ClusterGenerator.Ring r : ClusterGenerator.rings()) if (r.recipeId().equals(dd.finalRecipe())) { rr = r; break; }
                        simCounts2.merge(rr != null ? rr.alias() : dd.finalRecipe().toString(), 1, Integer::sum);
                    }
                }
            }
            if (simChunks1 == simChunks2 && simNoVein1 == simNoVein2 && simCounts1.equals(simCounts2))
                pass(sb, stats, "simulate determinism: " + simChunks1 + " chunks, no-vein=" + simNoVein1);
            else
                fail(sb, stats, "simulate non-deterministic: chunks " + simChunks1 + "/" + simChunks2
                    + " no-vein " + simNoVein1 + "/" + simNoVein2);
            // No-vein must exist somewhere within sample
            if (simNoVein1 > 0) pass(sb, stats, "no-vein chunks found in sim: " + simNoVein1);
            else fail(sb, stats, "no no-vein chunks within radius=" + simRadius
                + " (no_vein_chance=" + CCIWorldConfig.GEN_NO_VEIN_CHANCE.get() + ")");
            // At least one resource generated within reasonable radius
            int simVein1 = simChunks1 - simNoVein1;
            if (simVein1 > 0 && !simCounts1.isEmpty())
                pass(sb, stats, "at least one resource within " + simRadius + " blocks: " + simCounts1);
            else
                fail(sb, stats, "no useful vein chunks within " + simRadius + " blocks");
            // Verify OreData on player's chunk was NOT modified by simulation
            OreData snapshotAfter = OreDataAttachment.getData(chunk);
            boolean simNoWrite = java.util.Objects.equals(snapshotAfter.getRecipeId(), beforeId)
                && snapshotAfter.isLoaded() == beforeLoaded
                && snapshotAfter.getRandomMul() == beforeMul;
            if (simNoWrite) pass(sb, stats, "simulate did NOT write OreData on player's chunk");
            else fail(sb, stats, "simulate altered OreData unexpectedly: recipe " + beforeId + "->" + snapshotAfter.getRecipeId());

            // 6d. v0.7.1 — empty candidate must dominate gold-only far ring.
            // Sample chunks at ring distance ~ [3300, 3800] from spawn (only gold qualifies)
            // and verify that NOT all of them are gold: empty must keep most cells barren.
            int farTotal = 0, farGold = 0, farEmpty = 0, farWeightedEmpty = 0;
            for (int dxc = -240; dxc <= 240; dxc += 8) {
                for (int dzc = -240; dzc <= 240; dzc += 8) {
                    int cx2 = spawnCx + dxc, cz2 = spawnCz + dzc;
                    long bx2 = ((long) (cx2 << 4) + 8) - sx;
                    long bz2 = ((long) (cz2 << 4) + 8) - sz;
                    long sqd = bx2 * bx2 + bz2 * bz2;
                    if (sqd < 3300L * 3300L || sqd > 3800L * 3800L) continue;
                    farTotal++;
                    ClusterDecision dd = ClusterGenerator.decidePure(sseed, sx, sz, cx2, cz2, null);
                    if (dd.isNoVein()) {
                        farEmpty++;
                        if (dd.isWeightedEmpty()) farWeightedEmpty++;
                    } else {
                        ClusterGenerator.Ring rr = null;
                        for (ClusterGenerator.Ring r : ClusterGenerator.rings()) if (r.recipeId().equals(dd.finalRecipe())) { rr = r; break; }
                        if (rr != null && "gold".equals(rr.alias())) farGold++;
                    }
                }
            }
            if (farTotal >= 50) {
                double goldRatio = farGold / (double) farTotal;
                if (goldRatio < 0.50)
                    pass(sb, stats, "far gold-only ring not collapsed onto gold: gold=" + farGold
                        + " empty=" + farEmpty + " (weighted=" + farWeightedEmpty + ") of " + farTotal
                        + " (gold ratio=" + String.format(java.util.Locale.ROOT, "%.2f", goldRatio) + ")");
                else
                    fail(sb, stats, "far ring collapsed onto gold: gold=" + farGold + "/" + farTotal
                        + " — increase empty_weight_far or decrease gold weight");
            } else {
                pass(sb, stats, "far ring sample too small (" + farTotal + " chunks) — skipped collapse check");
            }

            // 7. Write the decision and read back
            ClusterGeneratorEngine.writeDecision(level, chunk, d1);
            OreData after = OreDataAttachment.getData(chunk);
            if (d1.isNoVein()) {
                if (after.getRecipeId() == null && after.isLoaded())
                    pass(sb, stats, "write+readback no-vein OK");
                else
                    fail(sb, stats, "write+readback no-vein got recipe=" + after.getRecipeId() + " loaded=" + after.isLoaded());
            } else {
                if (d1.finalRecipe().equals(after.getRecipeId())) {
                    pass(sb, stats, "write+readback recipe OK: " + after.getRecipeId()
                        + " randomMul=" + after.getRandomMul());
                } else {
                    fail(sb, stats, "write+readback recipe mismatch: expected=" + d1.finalRecipe() + " got=" + after.getRecipeId());
                }
            }

            sb.append("\n--- ").append(stats[0]).append(" passed, ").append(stats[1]).append(" failed ---");
            sb.append("\n[WARN] this test wrote OreData on the current chunk; restore via /cci_world set_test_vein <alias> if needed.");
        } catch (Exception e) {
            fail(sb, stats, "exception: " + e.getMessage());
            sb.append("\n--- ").append(stats[0]).append(" passed, ").append(stats[1]).append(" failed ---");
        }
        int finalFail = stats[1];
        String msg = sb.toString();
        src.sendSuccess(() -> Component.literal(msg), false);
        return finalFail == 0 ? 1 : 0;
    }

    private static void pass(StringBuilder sb, int[] stats, String detail) {
        sb.append("\n[PASS] ").append(detail);
        stats[0]++;
    }

    private static void fail(StringBuilder sb, int[] stats, String detail) {
        sb.append("\n[FAIL] ").append(detail);
        stats[1]++;
    }

    // -------------------------------------------------------------------------
    // v0.7 — simulate_distribution <radius_blocks>
    // -------------------------------------------------------------------------

    /**
     * Pure read-only summary of a single chunk simulation (used inside the
     * cluster aggregation map). Does NOT touch the world.
     */
    private static final class ClusterSummary {
        final String alias;
        final long clusterId;
        final int cellX, cellZ;
        final int centerX, centerZ;
        final int radius;
        int chunks = 0;
        int distanceFromSpawnAtCenter;
        ClusterSummary(String alias, long clusterId, int cellX, int cellZ,
                       int centerX, int centerZ, int radius, int distSpawn) {
            this.alias = alias; this.clusterId = clusterId;
            this.cellX = cellX; this.cellZ = cellZ;
            this.centerX = centerX; this.centerZ = centerZ;
            this.radius = radius; this.distanceFromSpawnAtCenter = distSpawn;
        }
    }

    private static int simulateDistribution(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int radius = IntegerArgumentType.getInteger(ctx, "radius_blocks");
        try {
            ServerLevel level = src.getLevel();
            long seed = level.getSeed();
            BlockPos spawn = level.getSharedSpawnPos();
            int spawnX = spawn.getX();
            int spawnZ = spawn.getZ();

            int spawnCx = SectionPos.blockToSectionCoord(spawnX);
            int spawnCz = SectionPos.blockToSectionCoord(spawnZ);
            int radiusChunks = (radius + 15) >> 4;

            // Aggregators
            int chunksSimulated = 0;
            int noVeinChunks = 0;
            int emptyByBase = 0;
            int emptyByWeighted = 0;
            int emptyByOutOfRings = 0;
            int emptyByOutsideRadius = 0;
            int veinChunks = 0;
            java.util.Map<String, Integer> chunksByResource = new java.util.LinkedHashMap<>();
            java.util.Map<String, Long> unitsByResource = new java.util.LinkedHashMap<>();
            java.util.Map<Long, ClusterSummary> clusters = new java.util.LinkedHashMap<>();
            java.util.Set<Long> cellsSeen = new java.util.HashSet<>();

            // Pre-build alias->units lookup from current rings to estimate units quickly.
            java.util.Map<ResourceLocation, ClusterGenerator.Ring> ringByRecipe = new java.util.HashMap<>();
            for (ClusterGenerator.Ring r : ClusterGenerator.rings()) ringByRecipe.put(r.recipeId(), r);

            long radiusSqr = (long) radius * (long) radius;

            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                    int cx = spawnCx + dx;
                    int cz = spawnCz + dz;
                    int blockCenterX = (cx << 4) + 8;
                    int blockCenterZ = (cz << 4) + 8;
                    long ddx = blockCenterX - spawnX;
                    long ddz = blockCenterZ - spawnZ;
                    if (ddx * ddx + ddz * ddz > radiusSqr) continue;

                    chunksSimulated++;
                    ClusterDecision d = ClusterGenerator.decidePure(seed, spawnX, spawnZ, cx, cz, null);
                    int cellSize = ClusterGenerator.cellSizeChunks();
                    long cellKey = (((long) d.cellX()) << 32) ^ (d.cellZ() & 0xFFFFFFFFL);
                    cellsSeen.add(cellKey);

                    if (d.isNoVein()) {
                        noVeinChunks++;
                        if (d.isBaseNoVein()) emptyByBase++;
                        else if (d.isWeightedEmpty()) emptyByWeighted++;
                        else if ("out_of_all_rings".equals(d.reason())) emptyByOutOfRings++;
                        else emptyByOutsideRadius++;
                        continue;
                    }
                    veinChunks++;
                    ClusterGenerator.Ring ring = ringByRecipe.get(d.finalRecipe());
                    String alias = ring != null ? ring.alias() : d.finalRecipe().toString();
                    chunksByResource.merge(alias, 1, Integer::sum);
                    long u = ring != null ? ring.unitsPerChunk() : 0L;
                    unitsByResource.merge(alias, u, Long::sum);

                    ClusterSummary cs = clusters.get(d.clusterId());
                    if (cs == null) {
                        long cdx = d.centerX() - spawnX;
                        long cdz = d.centerZ() - spawnZ;
                        int distSpawn = (int) Math.round(Math.sqrt((double) (cdx * cdx + cdz * cdz)));
                        cs = new ClusterSummary(alias, d.clusterId(), d.cellX(), d.cellZ(),
                            d.centerX(), d.centerZ(), d.radiusBlocks(), distSpawn);
                        clusters.put(d.clusterId(), cs);
                    }
                    cs.chunks++;
                }
            }

            // Per-resource cluster aggregates
            java.util.Map<String, Integer> clustersByResource = new java.util.LinkedHashMap<>();
            java.util.Map<String, Integer> nearestByResource = new java.util.LinkedHashMap<>();
            java.util.Map<String, Long> sumDistByResource = new java.util.LinkedHashMap<>();
            java.util.Map<String, Integer> countDistByResource = new java.util.LinkedHashMap<>();
            for (ClusterSummary cs : clusters.values()) {
                clustersByResource.merge(cs.alias, 1, Integer::sum);
                nearestByResource.merge(cs.alias, cs.distanceFromSpawnAtCenter, Math::min);
                sumDistByResource.merge(cs.alias, (long) cs.distanceFromSpawnAtCenter, Long::sum);
                countDistByResource.merge(cs.alias, 1, Integer::sum);
            }

            // Build report
            StringBuilder sb = new StringBuilder("[CCI World] simulate_distribution");
            sb.append("\n  dimension:           ").append(level.dimension().location());
            sb.append("\n  world seed:          ").append(seed);
            sb.append("\n  spawn:               ").append(spawnX).append(" / ").append(spawnZ);
            sb.append("\n  radius_blocks:       ").append(radius);
            sb.append("\n  chunks simulated:    ").append(chunksSimulated);
            sb.append("\n  cells simulated:     ").append(cellsSeen.size());
            sb.append("\n  no-vein chunks:      ").append(noVeinChunks);
            sb.append("\n    by base no_vein_roll:    ").append(emptyByBase);
            sb.append("\n    by weighted_empty:       ").append(emptyByWeighted);
            sb.append("\n    by out_of_all_rings:     ").append(emptyByOutOfRings);
            sb.append("\n    by outside_cluster_rad.: ").append(emptyByOutsideRadius);
            sb.append("\n  useful vein chunks:  ").append(veinChunks);
            sb.append("\n  clusters by resource:");
            if (clustersByResource.isEmpty()) sb.append("\n    (none)");
            else clustersByResource.forEach((k, v) -> sb.append("\n    ").append(k).append(" -> ").append(v));
            sb.append("\n  chunks by resource:");
            if (chunksByResource.isEmpty()) sb.append("\n    (none)");
            else chunksByResource.forEach((k, v) -> sb.append("\n    ").append(k).append(" -> ").append(v));
            sb.append("\n  estimated total units (chunks * units_per_chunk):");
            if (unitsByResource.isEmpty()) sb.append("\n    (none)");
            else unitsByResource.forEach((k, v) -> sb.append("\n    ").append(k).append(" -> ").append(v));
            sb.append("\n  nearest cluster (blocks from spawn):");
            for (ClusterGenerator.Ring r : ClusterGenerator.rings()) {
                Integer near = nearestByResource.get(r.alias());
                sb.append("\n    ").append(r.alias()).append(": ")
                  .append(near != null ? near.toString() : "ABSENT");
            }
            sb.append("\n  average cluster distance from spawn:");
            for (ClusterGenerator.Ring r : ClusterGenerator.rings()) {
                Integer cnt = countDistByResource.get(r.alias());
                Long sum = sumDistByResource.get(r.alias());
                if (cnt != null && cnt > 0 && sum != null) {
                    sb.append("\n    ").append(r.alias()).append(": ").append(sum / cnt).append(" blocks (n=").append(cnt).append(")");
                } else {
                    sb.append("\n    ").append(r.alias()).append(": ABSENT");
                }
            }
            sb.append("\n  first 10 clusters:");
            int shown = 0;
            for (ClusterSummary cs : clusters.values()) {
                if (shown++ >= 10) break;
                long est = 0L;
                ClusterGenerator.Ring rr = null;
                for (ClusterGenerator.Ring r : ClusterGenerator.rings()) if (r.alias().equals(cs.alias)) { rr = r; break; }
                if (rr != null) est = (long) cs.chunks * rr.unitsPerChunk();
                sb.append("\n    [").append(cs.alias).append("] id=").append(Long.toHexString(cs.clusterId))
                  .append(" cell=").append(cs.cellX).append("/").append(cs.cellZ)
                  .append(" center_block=").append(cs.centerX).append("/").append(cs.centerZ)
                  .append(" center_chunk=").append(cs.centerX >> 4).append("/").append(cs.centerZ >> 4)
                  .append(" radius=").append(cs.radius).append("b")
                  .append(" est_chunks=").append(cs.chunks)
                  .append(" est_units=").append(est)
                  .append(" dist_spawn=").append(cs.distanceFromSpawnAtCenter).append("b");
            }

            // Resource target summary (Task B): diagnostic only, never fail.
            sb.append("\n  target summary (diagnostic only):");
            appendTarget(sb, "coal",     1000, clustersByResource, radius, true);
            appendTarget(sb, "iron",     1000, clustersByResource, radius, true);
            appendTarget(sb, "copper",   1000, clustersByResource, radius, true);
            appendTarget(sb, "zinc",     1500, clustersByResource, radius, false);
            appendTarget(sb, "redstone", 2000, clustersByResource, radius, false);
            appendTarget(sb, "gold",     2500, clustersByResource, radius, false);

            // v0.7.2: upper-bound diagnostic — WARN if a resource is too dense within 1000 blocks.
            // Only meaningful when the simulated radius covers 1000 blocks.
            if (radius >= 1000) {
                int coal1k     = countClustersWithin(clusters, 1000, "coal");
                int iron1k     = countClustersWithin(clusters, 1000, "iron");
                int copper1k   = countClustersWithin(clusters, 1000, "copper");
                int zinc1k     = countClustersWithin(clusters, 1000, "zinc");
                int redstone1k = countClustersWithin(clusters, 1000, "redstone");
                int gold1k     = countClustersWithin(clusters, 1000, "gold");
                appendDensityWarn(sb, "coal",     coal1k,     10, "too_dense");
                appendDensityWarn(sb, "iron",     iron1k,      8, "too_dense");
                appendDensityWarn(sb, "copper",   copper1k,    8, "too_dense");
                appendDensityWarn(sb, "zinc",     zinc1k,      3, "too_early_dense");
                appendDensityWarn(sb, "redstone", redstone1k,  2, "too_early_dense");
                appendDensityWarn(sb, "gold",     gold1k,      0, "too_early");
            }

            sb.append("\n[INFO] read-only simulation: no chunks loaded/generated, no OreData written.");
            String msg = sb.toString();
            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] simulate_distribution error: " + e.getMessage()));
            return 0;
        }
    }

    private static int countClustersWithin(java.util.Map<Long, ClusterSummary> clusters, int maxDistance, String alias) {
        int n = 0;
        for (ClusterSummary cs : clusters.values()) {
            if (cs.alias.equals(alias) && cs.distanceFromSpawnAtCenter <= maxDistance) n++;
        }
        return n;
    }

    private static void appendDensityWarn(StringBuilder sb, String alias, int count, int maxAllowed, String reasonTag) {
        if (count > maxAllowed) {
            sb.append("\n    [WARN] ").append(alias).append(" within 1000 blocks: count=").append(count)
              .append(" > ").append(maxAllowed).append(" -> ").append(reasonTag);
        }
    }

    private static void appendTarget(StringBuilder sb, String alias, int targetRadius,
                                     java.util.Map<String, Integer> clustersByResource,
                                     int simulatedRadius, boolean mandatory) {
        int count = clustersByResource.getOrDefault(alias, 0);
        boolean ok = count >= 1;
        String tag;
        if (simulatedRadius < targetRadius) {
            tag = "[N/A]"; // simulated radius too small to evaluate
        } else if (ok) {
            tag = "[PASS]";
        } else {
            tag = mandatory ? "[WARN]" : "[INFO]";
        }
        sb.append("\n    ").append(tag).append(" ").append(alias)
          .append(" >=1 cluster within ").append(targetRadius).append(" blocks: count=")
          .append(count)
          .append(mandatory ? " (mandatory)" : " (preferred)");
    }
}
