package com.cciworld.config;

import com.cciworld.policy.BiomePolicyRule;
import com.cciworld.policy.DistanceBand;
import com.cciworld.policy.RarityPolicyRule;
import com.cciworld.policy.RegionPolicyRule;
import com.cciworld.policy.WeightedReplacementEntry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public final class CCIWorldConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ModConfigSpec SPEC;

    // --- global ---
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLED;
    public static final ModConfigSpec.DoubleValue RANDOM_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK;
    public static final ModConfigSpec.ConfigValue<Boolean> AUTOMATIC_POLICY_ENABLED;
    public static final ModConfigSpec.IntValue PLAYER_SCAN_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_TICK;

    // --- v0.4 policy engine ---
    public static final ModConfigSpec.ConfigValue<Boolean> POLICY_ENGINE_ENABLED;
    public static final ModConfigSpec.IntValue POLICY_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_PENDING_POLICY_JOBS;
    public static final ModConfigSpec.ConfigValue<Boolean> PROCESSED_CACHE_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> REPLACEMENT_DEFAULT;

    // --- v0.5 authoritative cluster generator ---
    public static final ModConfigSpec.ConfigValue<Boolean> AUTHORITATIVE_GENERATION_ENABLED;
    public static final ModConfigSpec.IntValue GEN_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_PENDING_GEN_JOBS;
    public static final ModConfigSpec.IntValue GEN_CELL_SIZE_CHUNKS;
    public static final ModConfigSpec.DoubleValue GEN_NO_VEIN_CHANCE;

    // v0.7.1 — weighted EMPTY candidate (per spawn-distance band)
    public static final ModConfigSpec.IntValue GEN_EMPTY_WEIGHT_NEAR;
    public static final ModConfigSpec.IntValue GEN_EMPTY_WEIGHT_MID;
    public static final ModConfigSpec.IntValue GEN_EMPTY_WEIGHT_FAR;
    public static final ModConfigSpec.IntValue GEN_EMPTY_NEAR_MAX_BLOCKS;
    public static final ModConfigSpec.IntValue GEN_EMPTY_MID_MAX_BLOCKS;

    // Per-resource ring config (min/max distance in blocks, weight, radius range in blocks)
    public static final ModConfigSpec.IntValue GEN_COAL_MIN, GEN_COAL_MAX, GEN_COAL_WEIGHT, GEN_COAL_RMIN, GEN_COAL_RMAX;
    public static final ModConfigSpec.LongValue GEN_COAL_UNITS;
    public static final ModConfigSpec.IntValue GEN_IRON_MIN, GEN_IRON_MAX, GEN_IRON_WEIGHT, GEN_IRON_RMIN, GEN_IRON_RMAX;
    public static final ModConfigSpec.LongValue GEN_IRON_UNITS;
    public static final ModConfigSpec.IntValue GEN_COPPER_MIN, GEN_COPPER_MAX, GEN_COPPER_WEIGHT, GEN_COPPER_RMIN, GEN_COPPER_RMAX;
    public static final ModConfigSpec.LongValue GEN_COPPER_UNITS;
    public static final ModConfigSpec.IntValue GEN_ZINC_MIN, GEN_ZINC_MAX, GEN_ZINC_WEIGHT, GEN_ZINC_RMIN, GEN_ZINC_RMAX;
    public static final ModConfigSpec.LongValue GEN_ZINC_UNITS;
    public static final ModConfigSpec.IntValue GEN_REDSTONE_MIN, GEN_REDSTONE_MAX, GEN_REDSTONE_WEIGHT, GEN_REDSTONE_RMIN, GEN_REDSTONE_RMAX;
    public static final ModConfigSpec.LongValue GEN_REDSTONE_UNITS;
    public static final ModConfigSpec.IntValue GEN_GOLD_MIN, GEN_GOLD_MAX, GEN_GOLD_WEIGHT, GEN_GOLD_RMIN, GEN_GOLD_RMAX;
    public static final ModConfigSpec.LongValue GEN_GOLD_UNITS;

    // --- distance bands ---
    public static final ModConfigSpec.IntValue BAND_INNER_MIN;
    public static final ModConfigSpec.IntValue BAND_INNER_MAX;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BAND_INNER_REPLACEMENTS;

    public static final ModConfigSpec.IntValue BAND_MID_MIN;
    public static final ModConfigSpec.IntValue BAND_MID_MAX;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BAND_MID_REPLACEMENTS;

    public static final ModConfigSpec.IntValue BAND_FAR_MIN;
    public static final ModConfigSpec.IntValue BAND_FAR_MAX;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BAND_FAR_REPLACEMENTS;

    // --- biome policy ---
    public static final ModConfigSpec.ConfigValue<Boolean> BIOME_POLICY_ENABLED;
    public static final ModConfigSpec.IntValue BIOME_SAMPLE_Y;

    // biome_policy.gold
    public static final ModConfigSpec.ConfigValue<Boolean> BIOME_GOLD_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  BIOME_GOLD_SOURCE;
    public static final ModConfigSpec.ConfigValue<String>  BIOME_GOLD_REPLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_GOLD_ALLOWED_BIOMES;

    // --- region policy ---
    public static final ModConfigSpec.ConfigValue<Boolean> REGION_POLICY_ENABLED;
    public static final ModConfigSpec.IntValue             REGION_SIZE_CHUNKS;
    public static final ModConfigSpec.ConfigValue<String>  REGION_SALT;
    public static final ModConfigSpec.ConfigValue<Boolean> WEIGHTED_REPLACEMENT_POOLS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  REPLACEMENT_POOL_SALT;

    // region_policy.industrial
    public static final ModConfigSpec.ConfigValue<Boolean>             REGION_INDUSTRIAL_ENABLED;
    public static final ModConfigSpec.IntValue                         REGION_INDUSTRIAL_WEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_INDUSTRIAL_ALLOWED_BANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_INDUSTRIAL_PREFERRED;
    public static final ModConfigSpec.ConfigValue<String>              REGION_INDUSTRIAL_REPLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_INDUSTRIAL_POOL;

    // region_policy.energy
    public static final ModConfigSpec.ConfigValue<Boolean>             REGION_ENERGY_ENABLED;
    public static final ModConfigSpec.IntValue                         REGION_ENERGY_WEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_ENERGY_ALLOWED_BANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_ENERGY_PREFERRED;
    public static final ModConfigSpec.ConfigValue<String>              REGION_ENERGY_REPLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_ENERGY_POOL;

    // region_policy.precious
    public static final ModConfigSpec.ConfigValue<Boolean>             REGION_PRECIOUS_ENABLED;
    public static final ModConfigSpec.IntValue                         REGION_PRECIOUS_WEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_PRECIOUS_ALLOWED_BANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_PRECIOUS_PREFERRED;
    public static final ModConfigSpec.ConfigValue<String>              REGION_PRECIOUS_REPLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_PRECIOUS_POOL;

    // region_policy.barren
    public static final ModConfigSpec.ConfigValue<Boolean>             REGION_BARREN_ENABLED;
    public static final ModConfigSpec.IntValue                         REGION_BARREN_WEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_BARREN_ALLOWED_BANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_BARREN_PREFERRED;
    public static final ModConfigSpec.ConfigValue<String>              REGION_BARREN_REPLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REGION_BARREN_POOL;

    // --- rarity policy ---
    public static final ModConfigSpec.ConfigValue<Boolean> RARITY_POLICY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_SALT;

    // rarity_policy.zinc_mid
    public static final ModConfigSpec.ConfigValue<Boolean> RARITY_ZINC_MID_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_ZINC_MID_SOURCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_ZINC_MID_BAND;
    public static final ModConfigSpec.DoubleValue          RARITY_ZINC_MID_KEEP_CHANCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_ZINC_MID_REPLACEMENT;

    // rarity_policy.gold_mid
    public static final ModConfigSpec.ConfigValue<Boolean> RARITY_GOLD_MID_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_GOLD_MID_SOURCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_GOLD_MID_BAND;
    public static final ModConfigSpec.DoubleValue          RARITY_GOLD_MID_KEEP_CHANCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_GOLD_MID_REPLACEMENT;

    // rarity_policy.gold_far
    public static final ModConfigSpec.ConfigValue<Boolean> RARITY_GOLD_FAR_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_GOLD_FAR_SOURCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_GOLD_FAR_BAND;
    public static final ModConfigSpec.DoubleValue          RARITY_GOLD_FAR_KEEP_CHANCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_GOLD_FAR_REPLACEMENT;

    // rarity_policy.redstone_far
    public static final ModConfigSpec.ConfigValue<Boolean> RARITY_REDSTONE_FAR_ENABLED;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_REDSTONE_FAR_SOURCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_REDSTONE_FAR_BAND;
    public static final ModConfigSpec.DoubleValue          RARITY_REDSTONE_FAR_KEEP_CHANCE;
    public static final ModConfigSpec.ConfigValue<String>  RARITY_REDSTONE_FAR_REPLACEMENT;

    private static volatile int rarityRuleInvalidCount  = 0;
    private static volatile int regionRuleInvalidCount  = 0;
    private static volatile int regionPoolInvalidCount  = 0;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        ENABLED = builder
            .comment("Enable the distance band + biome + region + rarity vein replacement policy.")
            .define("enabled", true);

        RANDOM_MULTIPLIER = builder
            .comment("randomMul value written to the chunk OreData after replacement.")
            .defineInRange("random_multiplier", 1.0, 0.0, 100.0);

        FALLBACK = builder
            .comment("Behavior when no rule matches the current vein. Only \"leave_original\" is supported.")
            .define("fallback", "leave_original");

        AUTOMATIC_POLICY_ENABLED = builder
            .comment("Enable automatic scanning and replacement of loaded chunks around players.")
            .define("automatic_policy_enabled", true);

        PLAYER_SCAN_RADIUS_CHUNKS = builder
            .comment("Chunk radius around each player to scan for automatic policy application.")
            .defineInRange("player_scan_radius_chunks", 6, 0, 64);

        SCAN_INTERVAL_TICKS = builder
            .comment("How often (in server ticks) to scan player-surrounding chunks. 20 ticks = 1 second.")
            .defineInRange("scan_interval_ticks", 100, 1, 6000);

        MAX_CHUNKS_PER_TICK = builder
            .comment("Maximum number of queued chunks to process per server tick.")
            .defineInRange("max_chunks_per_tick", 4, 1, 64);

        POLICY_ENGINE_ENABLED = builder
            .comment("Enable ChunkLoadEvent-based automatic policy processing (v0.4 engine).")
            .define("policy_engine_enabled", true);

        POLICY_CHUNKS_PER_TICK = builder
            .comment("Maximum chunks the v0.4 engine processes per server tick.")
            .defineInRange("policy_chunks_per_tick", 6, 1, 64);

        MAX_PENDING_POLICY_JOBS = builder
            .comment("Maximum pending chunk jobs in the queue. Incoming chunk loads are silently dropped when the queue is full.")
            .defineInRange("max_pending_policy_jobs", 8192, 1, 65536);

        PROCESSED_CACHE_ENABLED = builder
            .comment("Cache processed chunk keys to skip reprocessing within a server session.")
            .define("processed_cache_enabled", true);

        REPLACEMENT_DEFAULT = builder
            .comment("Default behavior when no rule matches the current vein. Only \"leave_original\" is supported.")
            .define("replacement_default", "leave_original");

        // v0.5 authoritative cluster generator
        builder.push("authoritative_generator");
        AUTHORITATIVE_GENERATION_ENABLED = builder
            .comment("Enable the v0.5 authoritative cluster generator. When true, CCI World decides every chunk's final OreData (including no-vein).")
            .define("authoritative_generation_enabled", true);

        GEN_CHUNKS_PER_TICK = builder
            .comment("Maximum chunks the v0.5 cluster generator processes per server tick.")
            .defineInRange("policy_chunks_per_tick", 6, 1, 64);

        MAX_PENDING_GEN_JOBS = builder
            .comment("Maximum pending chunk jobs in the cluster generator queue. Incoming chunk loads are silently dropped when full.")
            .defineInRange("max_pending_policy_jobs", 8192, 1, 65536);

        GEN_CELL_SIZE_CHUNKS = builder
            .comment("Side length, in chunks, of each cluster cell. Each cell deterministically contains at most one cluster. v0.7.2 preset: 24 (larger cells -> rarer clusters near spawn).")
            .defineInRange("cell_size_chunks", 24, 1, 64);

        GEN_NO_VEIN_CHANCE = builder
            .comment("Probability [0..1] that a cell is forced barren BEFORE the weighted resource pick (base no-vein roll). v0.7.2 preset: 0.70.")
            .defineInRange("no_vein_chance", 0.70, 0.0, 1.0);

        GEN_EMPTY_WEIGHT_NEAR = builder
            .comment("Weight of the synthetic EMPTY candidate added to the cell weighted pick when the cluster center is in the NEAR distance band (<= empty_near_max_blocks). Higher = more no-vein. v0.7.2 preset: 350. Density near spawn is intentionally rare: the player must find Tier 0 nearby, but not dozens of clusters.")
            .defineInRange("empty_weight_near", 350, 0, Integer.MAX_VALUE);
        GEN_EMPTY_WEIGHT_MID = builder
            .comment("Weight of the synthetic EMPTY candidate when the cluster center is in the MID distance band (<= empty_mid_max_blocks). v0.7.2 preset: 550.")
            .defineInRange("empty_weight_mid", 550, 0, Integer.MAX_VALUE);
        GEN_EMPTY_WEIGHT_FAR = builder
            .comment("Weight of the synthetic EMPTY candidate when the cluster center is in the FAR distance band (> empty_mid_max_blocks). v0.7.2 preset: 900.")
            .defineInRange("empty_weight_far", 900, 0, Integer.MAX_VALUE);
        GEN_EMPTY_NEAR_MAX_BLOCKS = builder
            .comment("Upper bound (blocks from spawn) of the NEAR band used for empty_weight_near.")
            .defineInRange("empty_near_max_blocks", 1200, 0, Integer.MAX_VALUE);
        GEN_EMPTY_MID_MAX_BLOCKS = builder
            .comment("Upper bound (blocks from spawn) of the MID band used for empty_weight_mid. Beyond this, empty_weight_far applies.")
            .defineInRange("empty_mid_max_blocks", 2200, 0, Integer.MAX_VALUE);

        builder.push("rings");
        builder.push("coal");
        GEN_COAL_MIN    = builder.defineInRange("min_distance_blocks", 0,    0, Integer.MAX_VALUE);
        GEN_COAL_MAX    = builder.defineInRange("max_distance_blocks", 1200, 0, Integer.MAX_VALUE);
        GEN_COAL_WEIGHT = builder.defineInRange("weight",              50,   0, Integer.MAX_VALUE);
        GEN_COAL_RMIN   = builder.defineInRange("radius_min_blocks",   24,   1, 4096);
        GEN_COAL_RMAX   = builder.defineInRange("radius_max_blocks",   40,   1, 4096);
        GEN_COAL_UNITS  = builder.comment("Finite units per chunk inside a coal cluster (target).").defineInRange("units_per_chunk", 25000L, 0L, Long.MAX_VALUE);
        builder.pop();
        builder.push("iron");
        GEN_IRON_MIN    = builder.defineInRange("min_distance_blocks", 0,    0, Integer.MAX_VALUE);
        GEN_IRON_MAX    = builder.defineInRange("max_distance_blocks", 1200, 0, Integer.MAX_VALUE);
        GEN_IRON_WEIGHT = builder.defineInRange("weight",              40,   0, Integer.MAX_VALUE);
        GEN_IRON_RMIN   = builder.defineInRange("radius_min_blocks",   24,   1, 4096);
        GEN_IRON_RMAX   = builder.defineInRange("radius_max_blocks",   40,   1, 4096);
        GEN_IRON_UNITS  = builder.comment("Finite units per chunk inside an iron cluster (target).").defineInRange("units_per_chunk", 15000L, 0L, Long.MAX_VALUE);
        builder.pop();
        builder.push("copper");
        GEN_COPPER_MIN    = builder.defineInRange("min_distance_blocks", 0,    0, Integer.MAX_VALUE);
        GEN_COPPER_MAX    = builder.defineInRange("max_distance_blocks", 1200, 0, Integer.MAX_VALUE);
        GEN_COPPER_WEIGHT = builder.defineInRange("weight",              35,   0, Integer.MAX_VALUE);
        GEN_COPPER_RMIN   = builder.defineInRange("radius_min_blocks",   24,   1, 4096);
        GEN_COPPER_RMAX   = builder.defineInRange("radius_max_blocks",   40,   1, 4096);
        GEN_COPPER_UNITS  = builder.comment("Finite units per chunk inside a copper cluster (target).").defineInRange("units_per_chunk", 15000L, 0L, Long.MAX_VALUE);
        builder.pop();
        builder.push("zinc");
        GEN_ZINC_MIN    = builder.defineInRange("min_distance_blocks", 400,  0, Integer.MAX_VALUE);
        GEN_ZINC_MAX    = builder.defineInRange("max_distance_blocks", 2200, 0, Integer.MAX_VALUE);
        GEN_ZINC_WEIGHT = builder.defineInRange("weight",              20,   0, Integer.MAX_VALUE);
        GEN_ZINC_RMIN   = builder.defineInRange("radius_min_blocks",   18,   1, 4096);
        GEN_ZINC_RMAX   = builder.defineInRange("radius_max_blocks",   32,   1, 4096);
        GEN_ZINC_UNITS  = builder.comment("Finite units per chunk inside a zinc cluster (target).").defineInRange("units_per_chunk", 10000L, 0L, Long.MAX_VALUE);
        builder.pop();
        builder.push("redstone");
        GEN_REDSTONE_MIN    = builder.defineInRange("min_distance_blocks", 700,  0, Integer.MAX_VALUE);
        GEN_REDSTONE_MAX    = builder.defineInRange("max_distance_blocks", 3000, 0, Integer.MAX_VALUE);
        GEN_REDSTONE_WEIGHT = builder.defineInRange("weight",              10,   0, Integer.MAX_VALUE);
        GEN_REDSTONE_RMIN   = builder.defineInRange("radius_min_blocks",   18,   1, 4096);
        GEN_REDSTONE_RMAX   = builder.defineInRange("radius_max_blocks",   32,   1, 4096);
        GEN_REDSTONE_UNITS  = builder.comment("Finite units per chunk inside a redstone cluster (target). NOTE: units_per_chunk must lie inside the reachable COE finite range [amountMultiplierMin*finiteAmountBase, amountMultiplierMax*finiteAmountBase] of the recipe, otherwise the solver will clamp. Redstone reachable range with default base=1000 is 10000..30000.").defineInRange("units_per_chunk", 10000L, 0L, Long.MAX_VALUE);
        builder.pop();
        builder.push("gold");
        GEN_GOLD_MIN    = builder.defineInRange("min_distance_blocks", 1200, 0, Integer.MAX_VALUE);
        GEN_GOLD_MAX    = builder.defineInRange("max_distance_blocks", 4000, 0, Integer.MAX_VALUE);
        GEN_GOLD_WEIGHT = builder.defineInRange("weight",              6,    0, Integer.MAX_VALUE);
        GEN_GOLD_RMIN   = builder.defineInRange("radius_min_blocks",   12,   1, 4096);
        GEN_GOLD_RMAX   = builder.defineInRange("radius_max_blocks",   22,   1, 4096);
        GEN_GOLD_UNITS  = builder.comment("Finite units per chunk inside a gold cluster (target).").defineInRange("units_per_chunk", 4000L, 0L, Long.MAX_VALUE);
        builder.pop();
        builder.pop(); // rings

        builder.pop(); // authoritative_generator

        // Distance bands
        builder.push("band_inner");
        BAND_INNER_MIN = builder.comment("Chebyshev chunk distance from spawn — minimum.")
            .defineInRange("min_distance_chunks", 0, 0, Integer.MAX_VALUE);
        BAND_INNER_MAX = builder.comment("Chebyshev chunk distance from spawn — maximum.")
            .defineInRange("max_distance_chunks", 12, 0, Integer.MAX_VALUE);
        BAND_INNER_REPLACEMENTS = builder.comment("Format: \"from_recipe_id -> to_recipe_id\"")
            .defineList("replacements",
                () -> List.of(
                    "createoreexcavation:ore_vein_type/zinc -> createoreexcavation:ore_vein_type/copper",
                    "createoreexcavation:ore_vein_type/redstone -> createoreexcavation:ore_vein_type/iron",
                    "createoreexcavation:ore_vein_type/gold -> createoreexcavation:ore_vein_type/coal"
                ), o -> o instanceof String);
        builder.pop();

        builder.push("band_mid");
        BAND_MID_MIN = builder.comment("Chebyshev chunk distance from spawn — minimum.")
            .defineInRange("min_distance_chunks", 13, 0, Integer.MAX_VALUE);
        BAND_MID_MAX = builder.comment("Chebyshev chunk distance from spawn — maximum.")
            .defineInRange("max_distance_chunks", 32, 0, Integer.MAX_VALUE);
        BAND_MID_REPLACEMENTS = builder.comment("Format: \"from_recipe_id -> to_recipe_id\"")
            .defineList("replacements",
                () -> List.of(
                    "createoreexcavation:ore_vein_type/redstone -> createoreexcavation:ore_vein_type/iron"
                ), o -> o instanceof String);
        builder.pop();

        builder.push("band_far");
        BAND_FAR_MIN = builder.comment("Chebyshev chunk distance from spawn — minimum.")
            .defineInRange("min_distance_chunks", 33, 0, Integer.MAX_VALUE);
        BAND_FAR_MAX = builder.comment("Chebyshev chunk distance from spawn — maximum.")
            .defineInRange("max_distance_chunks", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
        BAND_FAR_REPLACEMENTS = builder.comment("Format: \"from_recipe_id -> to_recipe_id\"")
            .defineList("replacements", List::of, o -> o instanceof String);
        builder.pop();

        // Biome policy
        BIOME_POLICY_ENABLED = builder
            .comment("Enable biome-based vein replacement (runs after distance band).")
            .define("biome_policy_enabled", true);
        BIOME_SAMPLE_Y = builder
            .comment("Block Y coordinate used when sampling the biome for a chunk.")
            .defineInRange("biome_sample_y", 64, -64, 320);

        builder.push("biome_policy");
        builder.push("gold");
        BIOME_GOLD_ENABLED = builder.comment("Enable this biome rule.").define("enabled", true);
        BIOME_GOLD_SOURCE   = builder.comment("Source vein recipe.").define("source_recipe", "createoreexcavation:ore_vein_type/gold");
        BIOME_GOLD_REPLACEMENT = builder.comment("Replacement when chunk biome is not in allowed_biomes.").define("replacement_recipe", "createoreexcavation:ore_vein_type/coal");
        BIOME_GOLD_ALLOWED_BIOMES = builder.comment("Biome IDs where gold is permitted.")
            .defineList("allowed_biomes",
                () -> List.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"),
                o -> o instanceof String);
        builder.pop(); // gold
        builder.pop(); // biome_policy

        // Region policy
        REGION_POLICY_ENABLED = builder
            .comment("Enable deterministic resource regions (runs after biome policy, before rarity).")
            .define("region_policy_enabled", true);
        REGION_SIZE_CHUNKS = builder
            .comment("Side length in chunks of each region cell. All chunks in the same cell share the same region type.")
            .defineInRange("region_size_chunks", 32, 1, 1024);
        REGION_SALT = builder
            .comment("Salt string mixed into the region hash. Change to reshuffle region layouts.")
            .define("region_salt", "cci_world_regions_v1");
        WEIGHTED_REPLACEMENT_POOLS_ENABLED = builder
            .comment("Enable weighted replacement pools for region policy. If false, replacement_recipe is always used.")
            .define("weighted_replacement_pools_enabled", true);
        REPLACEMENT_POOL_SALT = builder
            .comment("Salt mixed into the per-chunk pool roll. Change to reshuffle pool choices without changing region assignments.")
            .define("replacement_pool_salt", "cci_world_replacement_pools_v1");

        builder.push("region_policy");

        builder.push("industrial");
        REGION_INDUSTRIAL_ENABLED  = builder.comment("Enable this region type.").define("enabled", true);
        REGION_INDUSTRIAL_WEIGHT   = builder.comment("Relative weight in the weighted selection.").defineInRange("weight", 45, 1, Integer.MAX_VALUE);
        REGION_INDUSTRIAL_ALLOWED_BANDS = builder.comment("Bands where this region rule applies.").defineList("allowed_bands", () -> List.of("mid", "far"), o -> o instanceof String);
        REGION_INDUSTRIAL_PREFERRED = builder.comment("Recipes that are permitted in this region. Others are replaced.").defineList("preferred_recipes",
            () -> List.of(
                "createoreexcavation:ore_vein_type/iron",
                "createoreexcavation:ore_vein_type/copper",
                "createoreexcavation:ore_vein_type/zinc"
            ), o -> o instanceof String);
        REGION_INDUSTRIAL_REPLACEMENT = builder.comment("Fallback replacement when recipe is not preferred and pool is disabled/invalid.").define("replacement_recipe", "createoreexcavation:ore_vein_type/iron");
        REGION_INDUSTRIAL_POOL = builder.comment("Weighted replacement pool. Format: \"recipeId@weight\". Used when weighted_replacement_pools_enabled=true.").defineList("replacement_pool",
            () -> List.of(
                "createoreexcavation:ore_vein_type/iron@45",
                "createoreexcavation:ore_vein_type/copper@35",
                "createoreexcavation:ore_vein_type/zinc@20"
            ), o -> o instanceof String);
        builder.pop(); // industrial

        builder.push("energy");
        REGION_ENERGY_ENABLED  = builder.comment("Enable this region type.").define("enabled", true);
        REGION_ENERGY_WEIGHT   = builder.comment("Relative weight in the weighted selection.").defineInRange("weight", 25, 1, Integer.MAX_VALUE);
        REGION_ENERGY_ALLOWED_BANDS = builder.comment("Bands where this region rule applies.").defineList("allowed_bands", () -> List.of("mid", "far"), o -> o instanceof String);
        REGION_ENERGY_PREFERRED = builder.comment("Recipes that are permitted in this region.").defineList("preferred_recipes",
            () -> List.of(
                "createoreexcavation:ore_vein_type/coal",
                "createoreexcavation:ore_vein_type/redstone"
            ), o -> o instanceof String);
        REGION_ENERGY_REPLACEMENT = builder.comment("Fallback replacement when recipe is not preferred and pool is disabled/invalid.").define("replacement_recipe", "createoreexcavation:ore_vein_type/coal");
        REGION_ENERGY_POOL = builder.comment("Weighted replacement pool. Format: \"recipeId@weight\".").defineList("replacement_pool",
            () -> List.of(
                "createoreexcavation:ore_vein_type/coal@70",
                "createoreexcavation:ore_vein_type/redstone@30"
            ), o -> o instanceof String);
        builder.pop(); // energy

        builder.push("precious");
        REGION_PRECIOUS_ENABLED  = builder.comment("Enable this region type.").define("enabled", true);
        REGION_PRECIOUS_WEIGHT   = builder.comment("Relative weight in the weighted selection.").defineInRange("weight", 20, 1, Integer.MAX_VALUE);
        REGION_PRECIOUS_ALLOWED_BANDS = builder.comment("Bands where this region rule applies.").defineList("allowed_bands", () -> List.of("far"), o -> o instanceof String);
        REGION_PRECIOUS_PREFERRED = builder.comment("Recipes that are permitted in this region.").defineList("preferred_recipes",
            () -> List.of(
                "createoreexcavation:ore_vein_type/gold",
                "createoreexcavation:ore_vein_type/redstone"
            ), o -> o instanceof String);
        REGION_PRECIOUS_REPLACEMENT = builder.comment("Fallback replacement when recipe is not preferred and pool is disabled/invalid.").define("replacement_recipe", "createoreexcavation:ore_vein_type/gold");
        REGION_PRECIOUS_POOL = builder.comment("Weighted replacement pool. Format: \"recipeId@weight\".").defineList("replacement_pool",
            () -> List.of(
                "createoreexcavation:ore_vein_type/gold@60",
                "createoreexcavation:ore_vein_type/redstone@40"
            ), o -> o instanceof String);
        builder.pop(); // precious

        builder.push("barren");
        REGION_BARREN_ENABLED  = builder.comment("Enable this region type.").define("enabled", true);
        REGION_BARREN_WEIGHT   = builder.comment("Relative weight in the weighted selection.").defineInRange("weight", 10, 1, Integer.MAX_VALUE);
        REGION_BARREN_ALLOWED_BANDS = builder.comment("Bands where this region rule applies.").defineList("allowed_bands", () -> List.of("mid", "far"), o -> o instanceof String);
        REGION_BARREN_PREFERRED = builder.comment("Recipes that are permitted in this region.").defineList("preferred_recipes",
            () -> List.of(
                "createoreexcavation:ore_vein_type/coal",
                "createoreexcavation:ore_vein_type/iron"
            ), o -> o instanceof String);
        REGION_BARREN_REPLACEMENT = builder.comment("Fallback replacement when recipe is not preferred and pool is disabled/invalid.").define("replacement_recipe", "createoreexcavation:ore_vein_type/coal");
        REGION_BARREN_POOL = builder.comment("Weighted replacement pool. Format: \"recipeId@weight\".").defineList("replacement_pool",
            () -> List.of(
                "createoreexcavation:ore_vein_type/coal@75",
                "createoreexcavation:ore_vein_type/iron@25"
            ), o -> o instanceof String);
        builder.pop(); // barren

        builder.pop(); // region_policy

        // Rarity policy
        RARITY_POLICY_ENABLED = builder
            .comment("Enable deterministic rarity gate (runs after region policy).")
            .define("rarity_policy_enabled", true);
        RARITY_SALT = builder
            .comment("Salt string mixed into the rarity roll. Change to reshuffle all rarity outcomes.")
            .define("rarity_salt", "cci_world_v1");

        builder.push("rarity_policy");

        builder.push("zinc_mid");
        RARITY_ZINC_MID_ENABLED     = builder.comment("Enable this rarity rule.").define("enabled", true);
        RARITY_ZINC_MID_SOURCE      = builder.comment("Source vein recipe.").define("source_recipe", "createoreexcavation:ore_vein_type/zinc");
        RARITY_ZINC_MID_BAND        = builder.comment("Distance band name (inner / mid / far).").define("band", "mid");
        RARITY_ZINC_MID_KEEP_CHANCE = builder.comment("Probability [0.0, 1.0] that the vein stays.").defineInRange("keep_chance", 0.75, 0.0, 1.0);
        RARITY_ZINC_MID_REPLACEMENT = builder.comment("Replacement recipe when the gate fails.").define("replacement_recipe", "createoreexcavation:ore_vein_type/copper");
        builder.pop();

        builder.push("gold_mid");
        RARITY_GOLD_MID_ENABLED     = builder.comment("Enable this rarity rule.").define("enabled", true);
        RARITY_GOLD_MID_SOURCE      = builder.comment("Source vein recipe.").define("source_recipe", "createoreexcavation:ore_vein_type/gold");
        RARITY_GOLD_MID_BAND        = builder.comment("Distance band name (inner / mid / far).").define("band", "mid");
        RARITY_GOLD_MID_KEEP_CHANCE = builder.comment("Probability [0.0, 1.0] that the vein stays.").defineInRange("keep_chance", 0.35, 0.0, 1.0);
        RARITY_GOLD_MID_REPLACEMENT = builder.comment("Replacement recipe when the gate fails.").define("replacement_recipe", "createoreexcavation:ore_vein_type/coal");
        builder.pop();

        builder.push("gold_far");
        RARITY_GOLD_FAR_ENABLED     = builder.comment("Enable this rarity rule.").define("enabled", true);
        RARITY_GOLD_FAR_SOURCE      = builder.comment("Source vein recipe.").define("source_recipe", "createoreexcavation:ore_vein_type/gold");
        RARITY_GOLD_FAR_BAND        = builder.comment("Distance band name (inner / mid / far).").define("band", "far");
        RARITY_GOLD_FAR_KEEP_CHANCE = builder.comment("Probability [0.0, 1.0] that the vein stays.").defineInRange("keep_chance", 0.65, 0.0, 1.0);
        RARITY_GOLD_FAR_REPLACEMENT = builder.comment("Replacement recipe when the gate fails.").define("replacement_recipe", "createoreexcavation:ore_vein_type/coal");
        builder.pop();

        builder.push("redstone_far");
        RARITY_REDSTONE_FAR_ENABLED     = builder.comment("Enable this rarity rule.").define("enabled", true);
        RARITY_REDSTONE_FAR_SOURCE      = builder.comment("Source vein recipe.").define("source_recipe", "createoreexcavation:ore_vein_type/redstone");
        RARITY_REDSTONE_FAR_BAND        = builder.comment("Distance band name (inner / mid / far).").define("band", "far");
        RARITY_REDSTONE_FAR_KEEP_CHANCE = builder.comment("Probability [0.0, 1.0] that the vein stays.").defineInRange("keep_chance", 0.60, 0.0, 1.0);
        RARITY_REDSTONE_FAR_REPLACEMENT = builder.comment("Replacement recipe when the gate fails.").define("replacement_recipe", "createoreexcavation:ore_vein_type/iron");
        builder.pop();

        builder.pop(); // rarity_policy

        SPEC = builder.build();
    }

    private CCIWorldConfig() {}

    public static List<DistanceBand> parseBands() {
        List<DistanceBand> bands = new ArrayList<>();
        bands.add(new DistanceBand("inner", BAND_INNER_MIN.get(), BAND_INNER_MAX.get(), parseReplacementList(BAND_INNER_REPLACEMENTS.get())));
        bands.add(new DistanceBand("mid",   BAND_MID_MIN.get(),   BAND_MID_MAX.get(),   parseReplacementList(BAND_MID_REPLACEMENTS.get())));
        bands.add(new DistanceBand("far",   BAND_FAR_MIN.get(),   BAND_FAR_MAX.get(),   parseReplacementList(BAND_FAR_REPLACEMENTS.get())));
        return bands;
    }

    public static List<BiomePolicyRule> parseBiomeRules() {
        List<BiomePolicyRule> rules = new ArrayList<>();
        if (BIOME_GOLD_ENABLED.get()) {
            ResourceLocation src = ResourceLocation.tryParse(BIOME_GOLD_SOURCE.get());
            ResourceLocation rep = ResourceLocation.tryParse(BIOME_GOLD_REPLACEMENT.get());
            if (src != null && rep != null) {
                Set<ResourceLocation> allowed = new HashSet<>();
                for (String s : BIOME_GOLD_ALLOWED_BIOMES.get()) {
                    ResourceLocation id = ResourceLocation.tryParse(s);
                    if (id != null) allowed.add(id);
                }
                rules.add(new BiomePolicyRule("gold", true, src, rep, allowed));
            }
        }
        return rules;
    }

    public static List<RegionPolicyRule> parseRegionRules() {
        int invalid = 0;
        regionPoolInvalidCount = 0;
        List<RegionPolicyRule> rules = new ArrayList<>();

        invalid += addRegionRule(rules, "industrial",
            REGION_INDUSTRIAL_ENABLED, REGION_INDUSTRIAL_WEIGHT,
            REGION_INDUSTRIAL_ALLOWED_BANDS, REGION_INDUSTRIAL_PREFERRED,
            REGION_INDUSTRIAL_REPLACEMENT, REGION_INDUSTRIAL_POOL);

        invalid += addRegionRule(rules, "energy",
            REGION_ENERGY_ENABLED, REGION_ENERGY_WEIGHT,
            REGION_ENERGY_ALLOWED_BANDS, REGION_ENERGY_PREFERRED,
            REGION_ENERGY_REPLACEMENT, REGION_ENERGY_POOL);

        invalid += addRegionRule(rules, "precious",
            REGION_PRECIOUS_ENABLED, REGION_PRECIOUS_WEIGHT,
            REGION_PRECIOUS_ALLOWED_BANDS, REGION_PRECIOUS_PREFERRED,
            REGION_PRECIOUS_REPLACEMENT, REGION_PRECIOUS_POOL);

        invalid += addRegionRule(rules, "barren",
            REGION_BARREN_ENABLED, REGION_BARREN_WEIGHT,
            REGION_BARREN_ALLOWED_BANDS, REGION_BARREN_PREFERRED,
            REGION_BARREN_REPLACEMENT, REGION_BARREN_POOL);

        regionRuleInvalidCount = invalid;
        return rules;
    }

    public static int getRegionRuleInvalidCount() {
        return regionRuleInvalidCount;
    }

    public static int getRegionPoolInvalidCount() {
        return regionPoolInvalidCount;
    }

    private static int addRegionRule(
        List<RegionPolicyRule> rules,
        String name,
        ModConfigSpec.ConfigValue<Boolean>             enabled,
        ModConfigSpec.IntValue                         weight,
        ModConfigSpec.ConfigValue<List<? extends String>> allowedBands,
        ModConfigSpec.ConfigValue<List<? extends String>> preferredRecipes,
        ModConfigSpec.ConfigValue<String>              replacement,
        ModConfigSpec.ConfigValue<List<? extends String>> poolEntries
    ) {
        if (!enabled.get()) return 0;

        ResourceLocation rep = ResourceLocation.tryParse(replacement.get());
        if (rep == null) {
            LOGGER.warn("[CCI World] region rule '{}': invalid replacement_recipe '{}' — ignored", name, replacement.get());
            return 1;
        }

        List<String> validBandNames = List.of("inner", "mid", "far");
        List<String> bands = allowedBands.get().stream()
            .filter(b -> {
                boolean ok = validBandNames.contains(b);
                if (!ok) LOGGER.warn("[CCI World] region rule '{}': unknown band '{}' — ignored", name, b);
                return ok;
            })
            .collect(Collectors.toList());

        if (bands.isEmpty()) {
            LOGGER.warn("[CCI World] region rule '{}': no valid allowed_bands — ignored", name);
            return 1;
        }

        List<ResourceLocation> preferred = new ArrayList<>();
        for (String s : preferredRecipes.get()) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) {
                preferred.add(id);
            } else {
                LOGGER.warn("[CCI World] region rule '{}': invalid preferred_recipe '{}' — skipped", name, s);
            }
        }

        if (preferred.isEmpty()) {
            LOGGER.warn("[CCI World] region rule '{}': no valid preferred_recipes — ignored", name);
            return 1;
        }

        // Parse replacement pool entries (format: "recipeId@weight")
        List<WeightedReplacementEntry> pool = new ArrayList<>();
        int poolInvalid = 0;
        for (String entry : poolEntries.get()) {
            String[] parts = entry.split("@", 2);
            if (parts.length != 2) {
                LOGGER.warn("[CCI World] region rule '{}': pool entry '{}': bad format (expected 'recipeId@weight') — ignored", name, entry);
                poolInvalid++;
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(parts[0].trim());
            if (rl == null) {
                LOGGER.warn("[CCI World] region rule '{}': pool entry invalid recipe ID '{}' — ignored", name, parts[0].trim());
                poolInvalid++;
                continue;
            }
            int w;
            try {
                w = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                LOGGER.warn("[CCI World] region rule '{}': pool entry '{}': non-integer weight '{}' — ignored", name, rl, parts[1].trim());
                poolInvalid++;
                continue;
            }
            if (w <= 0) {
                LOGGER.warn("[CCI World] region rule '{}': pool entry '{}': weight {} <= 0 — ignored", name, rl, w);
                poolInvalid++;
                continue;
            }
            pool.add(new WeightedReplacementEntry(rl, w));
        }
        regionPoolInvalidCount += poolInvalid;

        rules.add(new RegionPolicyRule(name, true, weight.get(), bands, preferred, rep, pool));
        return 0;
    }

    public static List<RarityPolicyRule> parseRarityRules() {
        int invalid = 0;
        List<RarityPolicyRule> rules = new ArrayList<>();

        invalid += addRarityRule(rules, "zinc_mid",
            RARITY_ZINC_MID_ENABLED, RARITY_ZINC_MID_SOURCE,
            RARITY_ZINC_MID_BAND, RARITY_ZINC_MID_KEEP_CHANCE, RARITY_ZINC_MID_REPLACEMENT);

        invalid += addRarityRule(rules, "gold_mid",
            RARITY_GOLD_MID_ENABLED, RARITY_GOLD_MID_SOURCE,
            RARITY_GOLD_MID_BAND, RARITY_GOLD_MID_KEEP_CHANCE, RARITY_GOLD_MID_REPLACEMENT);

        invalid += addRarityRule(rules, "gold_far",
            RARITY_GOLD_FAR_ENABLED, RARITY_GOLD_FAR_SOURCE,
            RARITY_GOLD_FAR_BAND, RARITY_GOLD_FAR_KEEP_CHANCE, RARITY_GOLD_FAR_REPLACEMENT);

        invalid += addRarityRule(rules, "redstone_far",
            RARITY_REDSTONE_FAR_ENABLED, RARITY_REDSTONE_FAR_SOURCE,
            RARITY_REDSTONE_FAR_BAND, RARITY_REDSTONE_FAR_KEEP_CHANCE, RARITY_REDSTONE_FAR_REPLACEMENT);

        rarityRuleInvalidCount = invalid;
        return rules;
    }

    public static int getRarityRuleInvalidCount() {
        return rarityRuleInvalidCount;
    }

    private static int addRarityRule(
        List<RarityPolicyRule> rules,
        String name,
        ModConfigSpec.ConfigValue<Boolean> enabled,
        ModConfigSpec.ConfigValue<String>  source,
        ModConfigSpec.ConfigValue<String>  band,
        ModConfigSpec.DoubleValue          keepChance,
        ModConfigSpec.ConfigValue<String>  replacement
    ) {
        if (!enabled.get()) return 0;

        ResourceLocation src = ResourceLocation.tryParse(source.get());
        ResourceLocation rep = ResourceLocation.tryParse(replacement.get());

        if (src == null) {
            LOGGER.warn("[CCI World] rarity rule '{}': invalid source_recipe '{}' — ignored", name, source.get());
            return 1;
        }
        if (rep == null) {
            LOGGER.warn("[CCI World] rarity rule '{}': invalid replacement_recipe '{}' — ignored", name, replacement.get());
            return 1;
        }

        String bandName = band.get().trim();
        if (!List.of("inner", "mid", "far").contains(bandName)) {
            LOGGER.warn("[CCI World] rarity rule '{}': unknown band '{}' — ignored", name, bandName);
            return 1;
        }

        double kc = Math.max(0.0, Math.min(1.0, keepChance.get()));
        rules.add(new RarityPolicyRule(name, true, src, bandName, kc, rep));
        return 0;
    }

    private static Map<ResourceLocation, ResourceLocation> parseReplacementList(List<? extends String> entries) {
        Map<ResourceLocation, ResourceLocation> map = new HashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split("\\s*->\\s*", 2);
            if (parts.length == 2) {
                ResourceLocation from = ResourceLocation.tryParse(parts[0].trim());
                ResourceLocation to   = ResourceLocation.tryParse(parts[1].trim());
                if (from != null && to != null) map.put(from, to);
            }
        }
        return map;
    }
}
