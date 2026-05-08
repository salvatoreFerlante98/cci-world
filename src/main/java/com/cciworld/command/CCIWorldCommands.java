package com.cciworld.command;

import com.cciworld.coe.COEVeinIds;
import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
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

    private static void pass(StringBuilder sb, int[] stats, String detail) {
        sb.append("\n[PASS] ").append(detail);
        stats[0]++;
    }

    private static void fail(StringBuilder sb, int[] stats, String detail) {
        sb.append("\n[FAIL] ").append(detail);
        stats[1]++;
    }
}
