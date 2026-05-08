package com.cciworld.policy;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public record DistanceBand(String name, int minDistance, int maxDistance, Map<ResourceLocation, ResourceLocation> replacements) {

    public boolean contains(int distChunks) {
        return distChunks >= minDistance && distChunks <= maxDistance;
    }
}
