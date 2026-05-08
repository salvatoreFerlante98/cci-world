package com.cciworld.coe;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

public final class COEVeinIds {

    private static final String NS = "createoreexcavation";
    private static final String PREFIX = "ore_vein_type/";

    private static final Map<String, ResourceLocation> ALIASES = Map.of(
        "coal",     rl("coal"),
        "iron",     rl("iron"),
        "copper",   rl("copper"),
        "zinc",     rl("zinc"),
        "redstone", rl("redstone"),
        "gold",     rl("gold")
    );

    private COEVeinIds() {}

    private static ResourceLocation rl(String name) {
        return ResourceLocation.fromNamespaceAndPath(NS, PREFIX + name);
    }

    public static Optional<ResourceLocation> fromAlias(String alias) {
        return Optional.ofNullable(ALIASES.get(alias.toLowerCase()));
    }

    public static Map<String, ResourceLocation> aliases() {
        return ALIASES;
    }
}
