package com.cciworld.command;

import com.cciworld.coe.COEVeinIds;
import com.cciworld.coe.COEVeinWriter;
import com.cciworld.config.CCIWorldConfig;
import com.cciworld.policy.AutomaticPolicyEngine;
import com.cciworld.policy.DistanceBand;
import com.cciworld.policy.PolicyQueue;
import com.cciworld.policy.PolicyQueueEntry;
import com.cciworld.policy.PolicyResult;
import com.cciworld.policy.PolicyResult.Reason;
import com.cciworld.policy.ResourcePolicyService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Optional;

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
                src.sendFailure(Component.literal(
                    "[CCI World] unknown alias '" + alias + "'. Valid: " + COEVeinIds.aliases().keySet()
                ));
                return 0;
            }

            ResourceLocation targetId = targetOpt.get();
            if (!COEVeinWriter.veinExists(src.getServer().getRecipeManager(), targetId)) {
                src.sendFailure(Component.literal(
                    "[CCI World] vein recipe not found in RecipeManager: " + targetId +
                    ". Is createoreexcavation loaded?"
                ));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            OreData before = OreDataAttachment.getData(chunk);
            ResourceLocation previousId = before.getRecipeId();

            COEVeinWriter.writeVein(chunk, targetId);

            OreData after = OreDataAttachment.getData(chunk);
            String msg = "[CCI World] set_test_vein SUCCESS" +
                "\n  dimension:      " + level.dimension().location() +
                "\n  chunk x/z:     " + chunk.getPos().x + " / " + chunk.getPos().z +
                "\n  previous recipe: " + (previousId != null ? previousId : "none") +
                "\n  new recipe:      " + after.getRecipeId() +
                "\n  loaded:          " + after.isLoaded() +
                "\n  randomMul:       " + after.getRandomMul() +
                "\n  extractedAmount: not available via public API";

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
            if (fromOpt.isEmpty()) {
                src.sendFailure(Component.literal(
                    "[CCI World] unknown from-alias '" + fromAlias + "'. Valid: " + COEVeinIds.aliases().keySet()
                ));
                return 0;
            }

            Optional<ResourceLocation> toOpt = COEVeinIds.fromAlias(toAlias);
            if (toOpt.isEmpty()) {
                src.sendFailure(Component.literal(
                    "[CCI World] unknown to-alias '" + toAlias + "'. Valid: " + COEVeinIds.aliases().keySet()
                ));
                return 0;
            }

            ResourceLocation fromId = fromOpt.get();
            ResourceLocation toId   = toOpt.get();
            var recipeMgr = src.getServer().getRecipeManager();

            if (!COEVeinWriter.veinExists(recipeMgr, fromId)) {
                src.sendFailure(Component.literal("[CCI World] vein recipe not found: " + fromId));
                return 0;
            }
            if (!COEVeinWriter.veinExists(recipeMgr, toId)) {
                src.sendFailure(Component.literal("[CCI World] vein recipe not found: " + toId));
                return 0;
            }

            ServerLevel level = player.serverLevel();
            LevelChunk chunk  = level.getChunkAt(player.blockPosition());

            OreData data = OreDataAttachment.getData(chunk);
            ResourceLocation currentId = data.getRecipeId();

            if (!fromId.equals(currentId)) {
                String msg = "[CCI World] no replacement applied" +
                    "\n  current: " + (currentId != null ? currentId : "none") +
                    "\n  from:    " + fromId +
                    "\n  to:      " + toId;
                src.sendSuccess(() -> Component.literal(msg), false);
                return 1;
            }

            COEVeinWriter.writeVein(chunk, toId);

            OreData after = OreDataAttachment.getData(chunk);
            String msg = "[CCI World] replace_test_vein SUCCESS" +
                "\n  dimension:  " + level.dimension().location() +
                "\n  chunk x/z: " + chunk.getPos().x + " / " + chunk.getPos().z +
                "\n  replaced:  " + fromId + " -> " + toId +
                "\n  loaded:    " + after.isLoaded() +
                "\n  randomMul: " + after.getRandomMul();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // debug_policy_here — full inspect (distance + biome), no write
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
                ? r.bandName() + " [" + r.bandMin() + "-" + r.bandMax() + "]"
                : "none";

            String biomeInfo   = r.biomeId() != null ? r.biomeId().toString() : "unknown";
            String biomeAllowed = switch (r.reason()) {
                case BIOME_ALLOWED              -> "yes";
                case BIOME_NOT_ALLOWED_APPLIED,
                     BIOME_RULE_TARGET_MISSING  -> "no";
                default                         -> "n/a";
            };
            String biomeRuleMatched = (r.matchedBiomeRule() != null) ? "yes (" + r.matchedBiomeRule() + ")" : "no";

            String replacementInfo = (r.previousRecipe() != null && r.newRecipe() != null)
                ? r.previousRecipe() + " -> " + r.newRecipe()
                : (r.previousRecipe() != null ? r.previousRecipe() + " -> (no replacement)" : "none");

            String action = switch (r.reason()) {
                case APPLIED                   -> "APPLY via DISTANCE";
                case BIOME_NOT_ALLOWED_APPLIED -> "APPLY via BIOME";
                default                        -> "NO ACTION (" + r.reason() + ")";
            };

            String msg = "[CCI World] debug_policy_here" +
                "\n  dimension:           " + level.dimension().location() +
                "\n  spawn chunk x/z:     " + spawnCX + " / " + spawnCZ +
                "\n  current chunk x/z:   " + r.chunkX() + " / " + r.chunkZ() +
                "\n  distance chunks:     " + r.distChunks() +
                "\n  matched band:        " + bandInfo +
                "\n  current recipe:      " + (r.previousRecipe() != null ? r.previousRecipe() : "none") +
                "\n  biome id:            " + biomeInfo +
                "\n  biome policy:        " + (CCIWorldConfig.BIOME_POLICY_ENABLED.get() ? "enabled" : "disabled") +
                "\n  matched biome rule:  " + biomeRuleMatched +
                "\n  biome allowed:       " + biomeAllowed +
                "\n  replacement:         " + replacementInfo +
                "\n  fallback:            " + CCIWorldConfig.FALLBACK.get() +
                "\n  predicted action:    " + action +
                "\n  reason:              " + r.reason();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // apply_distance_policy_here — applies full policy (distance + biome)
    // -------------------------------------------------------------------------

    private static int applyDistancePolicyHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            ServerPlayer player = src.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            LevelChunk chunk = level.getChunkAt(player.blockPosition());

            PolicyResult r = ResourcePolicyService.apply(level, chunk);

            String bandInfo = r.bandName() != null
                ? r.bandName() + " [" + r.bandMin() + "-" + r.bandMax() + "]"
                : "none";

            String body = switch (r.reason()) {
                case DISABLED               -> "\n  policy disabled";
                case NO_MATCHING_BAND       -> "\n  matched band:    none\n  no action";
                case BIOME_POLICY_DISABLED  ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  biome policy disabled — no action";
                case NO_CURRENT_RECIPE      ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: none\n  no action";
                case NO_MATCHING_RULE       ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  no replacement rule — fallback: " + CCIWorldConfig.FALLBACK.get();
                case TARGET_RECIPE_MISSING  ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  target recipe not found: " + r.newRecipe();
                case APPLIED                ->
                    "\n  policy type:     DISTANCE" +
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
                    "\n  new recipe:      " + r.newRecipe();
                case BIOME_UNKNOWN          ->
                    "\n  matched band:    " + bandInfo +
                    "\n  biome id:        unknown\n  no action";
                case BIOME_ALLOWED          ->
                    "\n  matched band:    " + bandInfo +
                    "\n  previous recipe: " + r.previousRecipe() +
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
                    "\n  biome id:        " + r.biomeId() +
                    "\n  biome rule target not found: " + r.newRecipe();
                default                     -> "\n  reason: " + r.reason();
            };

            boolean success = r.reason() == Reason.APPLIED || r.reason() == Reason.BIOME_NOT_ALLOWED_APPLIED;
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

            int candidates       = 0;
            int enqueued         = 0;
            int skippedUnloaded  = 0;
            int skippedInQueue   = 0;

            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                    candidates++;
                    int cx = playerCX + dx;
                    int cz = playerCZ + dz;

                    // getChunkNow — never generates a chunk; returns null if not loaded
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        skippedUnloaded++;
                        continue;
                    }

                    boolean added = PolicyQueue.enqueue(new PolicyQueueEntry(
                        level.dimension(), cx, cz, "manual", player.getName().getString()
                    ));
                    if (added) enqueued++; else skippedInQueue++;
                }
            }

            int queueSize = PolicyQueue.size();
            String msg = "[CCI World] apply_spawn_policy_loaded_radius" +
                "\n  dimension:         " + level.dimension().location() +
                "\n  player chunk x/z:  " + playerCX + " / " + playerCZ +
                "\n  requested radius:  " + radiusChunks +
                "\n  candidate count:   " + candidates +
                "\n  loaded enqueued:   " + enqueued +
                "\n  skipped unloaded:  " + skippedUnloaded +
                "\n  skipped in-queue:  " + skippedInQueue +
                "\n  queue size total:  " + queueSize;

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

            int biomeRulesCount  = CCIWorldConfig.parseBiomeRules().size();
            int invalidBiomeIds  = AutomaticPolicyEngine.getWarnedInvalidBiomeIds();

            String msg = "[CCI World] policy_status" +
                "\n  enabled:                   " + CCIWorldConfig.ENABLED.get() +
                "\n  automatic_policy_enabled:  " + CCIWorldConfig.AUTOMATIC_POLICY_ENABLED.get() +
                "\n  distance_bands_count:      " + bands.size() +
                "\n  bands:                     " + bandNames +
                "\n  biome_policy_enabled:      " + CCIWorldConfig.BIOME_POLICY_ENABLED.get() +
                "\n  biome_sample_y:            " + CCIWorldConfig.BIOME_SAMPLE_Y.get() +
                "\n  biome_rules_count:         " + biomeRulesCount +
                "\n  invalid_biome_ids_warned:  " + invalidBiomeIds +
                "\n  player_scan_radius_chunks: " + CCIWorldConfig.PLAYER_SCAN_RADIUS_CHUNKS.get() +
                "\n  scan_interval_ticks:       " + CCIWorldConfig.SCAN_INTERVAL_TICKS.get() +
                "\n  max_chunks_per_tick:       " + CCIWorldConfig.MAX_CHUNKS_PER_TICK.get() +
                "\n  queue size:                " + AutomaticPolicyEngine.getQueueSize() +
                "\n  session cache size:        " + AutomaticPolicyEngine.getSessionCacheSize() +
                "\n  total applied this session:" + AutomaticPolicyEngine.getTotalApplied() +
                "\n  total skipped this session:" + AutomaticPolicyEngine.getTotalSkipped();

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }

    // -------------------------------------------------------------------------
    // clear_policy_session_cache
    // -------------------------------------------------------------------------

    private static int clearPolicySessionCache(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        try {
            int cleared   = AutomaticPolicyEngine.clearSessionCache();
            int queueSize = AutomaticPolicyEngine.getQueueSize();

            String msg = "[CCI World] clear_policy_session_cache" +
                "\n  cache cleared: " + cleared + " entries removed" +
                "\n  queue size:    " + queueSize + " (unchanged)";

            src.sendSuccess(() -> Component.literal(msg), false);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[CCI World] error: " + e.getMessage()));
            return 0;
        }
    }
}
