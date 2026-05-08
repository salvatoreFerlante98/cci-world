package com.cciworld.generator;

import com.cciworld.config.CCIWorldConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.8 — biome rules per ring.
 *
 * <p>Pure deterministic evaluation: given a biome {@link Holder} and a ring
 * alias, decide whether the cluster is allowed in that biome. The result is
 * captured in {@link Result} for diagnostic purposes.</p>
 *
 * <p>Tag strings can be specified as {@code #namespace:path} or
 * {@code namespace:path}. Invalid tag IDs are ignored (returned as
 * non-matching) and a warn-once is emitted via {@link #warnInvalidTag}.</p>
 */
public final class BiomeRules {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> WARNED_TAGS =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    public enum Mode { PERMISSIVE, STRICT }

    public record Result(
        boolean evaluated,
        boolean allowed,
        String  reason,           // human-readable: biome_allowed_no_match | biome_allowed_match | biome_denied:<tag> | biome_strict_no_match | biome_no_holder
        List<String> matchedAllowed,
        List<String> matchedDenied,
        Mode    mode
    ) {
        public static Result notEvaluated() {
            return new Result(false, true, "biome_not_evaluated",
                List.of(), List.of(), Mode.PERMISSIVE);
        }
    }

    private BiomeRules() {}

    /** Evaluate biome rules for the given ring alias. */
    public static Result evaluate(Holder<Biome> biome, String alias) {
        if (biome == null) {
            return new Result(false, true, "biome_no_holder",
                List.of(), List.of(), modeFor(alias));
        }
        List<? extends String> allowed = allowedTagsFor(alias);
        List<? extends String> denied  = deniedTagsFor(alias);
        Mode mode = modeFor(alias);

        // Check denied first — denied always wins.
        List<String> matchedDenied = new ArrayList<>();
        for (String raw : denied) {
            TagKey<Biome> key = parseTag(raw);
            if (key == null) continue;
            if (biome.is(key)) matchedDenied.add(raw);
        }
        if (!matchedDenied.isEmpty()) {
            return new Result(true, false, "biome_denied:" + matchedDenied.get(0),
                List.of(), matchedDenied, mode);
        }

        List<String> matchedAllowed = new ArrayList<>();
        for (String raw : allowed) {
            TagKey<Biome> key = parseTag(raw);
            if (key == null) continue;
            if (biome.is(key)) matchedAllowed.add(raw);
        }

        if (mode == Mode.STRICT) {
            if (matchedAllowed.isEmpty()) {
                return new Result(true, false, "biome_strict_no_match",
                    List.of(), List.of(), mode);
            }
            return new Result(true, true, "biome_allowed_match",
                matchedAllowed, List.of(), mode);
        }

        // Permissive: allow if not denied; report match for diagnostics.
        String reason = matchedAllowed.isEmpty() ? "biome_allowed_no_match" : "biome_allowed_match";
        return new Result(true, true, reason, matchedAllowed, List.of(), mode);
    }

    /** Parse a tag string ("#ns:path" or "ns:path") into a {@link TagKey}; null if invalid. */
    public static TagKey<Biome> parseTag(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("#")) s = s.substring(1);
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) {
            warnInvalidTag(raw);
            return null;
        }
        try {
            return TagKey.create(net.minecraft.core.registries.Registries.BIOME, rl);
        } catch (Throwable t) {
            warnInvalidTag(raw);
            return null;
        }
    }

    public static void warnInvalidTag(String raw) {
        if (raw == null) return;
        if (WARNED_TAGS.add(raw)) {
            LOGGER.warn("[CCI World] invalid biome tag in v0.8 biome rules (ignored): {}", raw);
        }
    }

    public static int warnedTagCount() {
        return WARNED_TAGS.size();
    }

    public static Mode modeFor(String alias) {
        String s = switch (alias) {
            case "coal"     -> CCIWorldConfig.GEN_COAL_BIOME_MODE.get();
            case "iron"     -> CCIWorldConfig.GEN_IRON_BIOME_MODE.get();
            case "copper"   -> CCIWorldConfig.GEN_COPPER_BIOME_MODE.get();
            case "zinc"     -> CCIWorldConfig.GEN_ZINC_BIOME_MODE.get();
            case "redstone" -> CCIWorldConfig.GEN_REDSTONE_BIOME_MODE.get();
            case "gold"     -> CCIWorldConfig.GEN_GOLD_BIOME_MODE.get();
            default -> "permissive";
        };
        return "strict".equalsIgnoreCase(s) ? Mode.STRICT : Mode.PERMISSIVE;
    }

    public static List<? extends String> allowedTagsFor(String alias) {
        return switch (alias) {
            case "coal"     -> CCIWorldConfig.GEN_COAL_ALLOWED_TAGS.get();
            case "iron"     -> CCIWorldConfig.GEN_IRON_ALLOWED_TAGS.get();
            case "copper"   -> CCIWorldConfig.GEN_COPPER_ALLOWED_TAGS.get();
            case "zinc"     -> CCIWorldConfig.GEN_ZINC_ALLOWED_TAGS.get();
            case "redstone" -> CCIWorldConfig.GEN_REDSTONE_ALLOWED_TAGS.get();
            case "gold"     -> CCIWorldConfig.GEN_GOLD_ALLOWED_TAGS.get();
            default -> List.of();
        };
    }

    public static List<? extends String> deniedTagsFor(String alias) {
        return switch (alias) {
            case "coal"     -> CCIWorldConfig.GEN_COAL_DENIED_TAGS.get();
            case "iron"     -> CCIWorldConfig.GEN_IRON_DENIED_TAGS.get();
            case "copper"   -> CCIWorldConfig.GEN_COPPER_DENIED_TAGS.get();
            case "zinc"     -> CCIWorldConfig.GEN_ZINC_DENIED_TAGS.get();
            case "redstone" -> CCIWorldConfig.GEN_REDSTONE_DENIED_TAGS.get();
            case "gold"     -> CCIWorldConfig.GEN_GOLD_DENIED_TAGS.get();
            default -> List.of();
        };
    }
}
