package com.cciworld.config;

import com.cciworld.policy.BiomePolicyRule;
import com.cciworld.policy.DistanceBand;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("deprecation")
public final class CCIWorldConfig {

    public static final ModConfigSpec SPEC;

    // --- global ---
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLED;
    public static final ModConfigSpec.DoubleValue RANDOM_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK;
    public static final ModConfigSpec.ConfigValue<Boolean> AUTOMATIC_POLICY_ENABLED;
    public static final ModConfigSpec.IntValue PLAYER_SCAN_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_CHUNKS_PER_TICK;

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
    public static final ModConfigSpec.ConfigValue<String> BIOME_GOLD_SOURCE;
    public static final ModConfigSpec.ConfigValue<String> BIOME_GOLD_REPLACEMENT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_GOLD_ALLOWED_BIOMES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        ENABLED = builder
            .comment("Enable the distance band + biome vein replacement policy.")
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

        // Distance bands
        builder.push("band_inner");
        BAND_INNER_MIN = builder
            .comment("Chebyshev chunk distance from spawn (inclusive) — minimum.")
            .defineInRange("min_distance_chunks", 0, 0, Integer.MAX_VALUE);
        BAND_INNER_MAX = builder
            .comment("Chebyshev chunk distance from spawn (inclusive) — maximum.")
            .defineInRange("max_distance_chunks", 12, 0, Integer.MAX_VALUE);
        BAND_INNER_REPLACEMENTS = builder
            .comment("Replacement rules for this band. Format: \"from_recipe_id -> to_recipe_id\"")
            .defineList("replacements",
                () -> List.of(
                    "createoreexcavation:ore_vein_type/zinc -> createoreexcavation:ore_vein_type/copper",
                    "createoreexcavation:ore_vein_type/redstone -> createoreexcavation:ore_vein_type/iron",
                    "createoreexcavation:ore_vein_type/gold -> createoreexcavation:ore_vein_type/coal"
                ),
                o -> o instanceof String);
        builder.pop();

        builder.push("band_mid");
        BAND_MID_MIN = builder
            .comment("Chebyshev chunk distance from spawn (inclusive) — minimum.")
            .defineInRange("min_distance_chunks", 13, 0, Integer.MAX_VALUE);
        BAND_MID_MAX = builder
            .comment("Chebyshev chunk distance from spawn (inclusive) — maximum.")
            .defineInRange("max_distance_chunks", 32, 0, Integer.MAX_VALUE);
        BAND_MID_REPLACEMENTS = builder
            .comment("Replacement rules for this band. Format: \"from_recipe_id -> to_recipe_id\"")
            .defineList("replacements",
                () -> List.of(
                    "createoreexcavation:ore_vein_type/redstone -> createoreexcavation:ore_vein_type/iron"
                ),
                o -> o instanceof String);
        builder.pop();

        builder.push("band_far");
        BAND_FAR_MIN = builder
            .comment("Chebyshev chunk distance from spawn (inclusive) — minimum.")
            .defineInRange("min_distance_chunks", 33, 0, Integer.MAX_VALUE);
        BAND_FAR_MAX = builder
            .comment("Chebyshev chunk distance from spawn (inclusive) — maximum.")
            .defineInRange("max_distance_chunks", Integer.MAX_VALUE, 0, Integer.MAX_VALUE);
        BAND_FAR_REPLACEMENTS = builder
            .comment("Replacement rules for this band. Format: \"from_recipe_id -> to_recipe_id\"")
            .defineList("replacements",
                List::of,
                o -> o instanceof String);
        builder.pop();

        // Biome policy
        BIOME_POLICY_ENABLED = builder
            .comment("Enable biome-based vein replacement (runs after distance band policy when distance does not apply).")
            .define("biome_policy_enabled", true);

        BIOME_SAMPLE_Y = builder
            .comment("Block Y coordinate used when sampling the biome for a chunk.")
            .defineInRange("biome_sample_y", 64, -64, 320);

        builder.push("biome_policy");
        builder.push("gold");
        BIOME_GOLD_ENABLED = builder
            .comment("Enable this biome rule.")
            .define("enabled", true);
        BIOME_GOLD_SOURCE = builder
            .comment("Source vein recipe to which this rule applies.")
            .define("source_recipe", "createoreexcavation:ore_vein_type/gold");
        BIOME_GOLD_REPLACEMENT = builder
            .comment("Replacement vein recipe applied when the chunk biome is not in allowed_biomes.")
            .define("replacement_recipe", "createoreexcavation:ore_vein_type/coal");
        BIOME_GOLD_ALLOWED_BIOMES = builder
            .comment("Biome IDs where gold veins are permitted. Invalid IDs are logged and ignored.")
            .defineList("allowed_biomes",
                () -> List.of(
                    "minecraft:badlands",
                    "minecraft:eroded_badlands",
                    "minecraft:wooded_badlands"
                ),
                o -> o instanceof String);
        builder.pop(); // gold
        builder.pop(); // biome_policy

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

    private static Map<ResourceLocation, ResourceLocation> parseReplacementList(List<? extends String> entries) {
        Map<ResourceLocation, ResourceLocation> map = new HashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split("\\s*->\\s*", 2);
            if (parts.length == 2) {
                ResourceLocation from = ResourceLocation.tryParse(parts[0].trim());
                ResourceLocation to   = ResourceLocation.tryParse(parts[1].trim());
                if (from != null && to != null) {
                    map.put(from, to);
                }
            }
        }
        return map;
    }
}
