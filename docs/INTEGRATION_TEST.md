# CCI World — Integration Test Guide

Tests cci_world vein replacement in combination with cci_radar + JourneyMap.
cci_world compiles and runs standalone; this setup is for manual integration testing only.

---

## Prerequisites

Both repos must sit as siblings under the same parent directory:

```
IdeaProjects/
  cci-world/   ← this repo
  cci-radar/   ← sibling repo
```

Required runtime jars (from the local modpack install):

```
journeymap-neoforge-1.21.1-6.0.0-beta.66.jar
jmi-neoforge-1.21.1-1.9.jar
```

These are resolved from the CurseForge modpack mods folder automatically.

---

## Step 1 — Build cci_radar

```
cd ../cci-radar
./gradlew clean build
```

Output: `../cci-radar/build/libs/cci_radar-1.0.0.jar`

> Alternatively, drop `cci_radar-*.jar` manually into `libs/integration/`.
> That folder is gitignored — the jar will never be committed.

---

## Step 2 — Run integration client

```
cd ../cci-world
./gradlew runClient -PcciIntegration=true
```

Gradle will log which cci_radar jar was picked up:

```
[CCI World] integration: loading cci_radar from .../cci-radar/build/libs/cci_radar-1.0.0.jar
```

If the jar is missing, Gradle logs a warning and launches without cci_radar (game still starts).

---

## Expected loaded mods

| Mod                 | Source                                 |
|---------------------|----------------------------------------|
| cci_world           | this project (sourceSets.main)         |
| cci_radar           | ../cci-radar/build/libs/ or libs/integration/ |
| journeymap          | modpack mods folder (localRuntime)     |
| jmi (JMI)           | modpack mods folder (localRuntime)     |
| create              | modpack mods folder (localRuntime)     |
| createoreexcavation | modpack mods folder (localRuntime)     |

Normal `./gradlew runClient` (no property) loads cci_world + create + COE only.

---

## Integration test checklist

Run these commands in-game as an OP player:

### Phase 1 — baseline

```
/cci_world set_test_vein zinc
/cci_radar unlock_tier 1
/cci_radar debug_scan_real 8
/cci_radar debug_resource_distribution
```

**Expected:** Radar reports zinc vein at current chunk.

### Phase 2 — apply replacement

```
/cci_world replace_test_vein zinc copper
/cci_radar debug_scan_real 8
/cci_radar resync
/cci_radar debug_resource_distribution
```

**Expected:**
- Radar no longer reports zinc at current chunk
- Radar reports copper at current chunk
- No stale zinc marker on JourneyMap
- No crash, no duplicate pebbles/markers
- `/cci_world debug_chunk` confirms `recipe: createoreexcavation:ore_vein_type/copper`

### Phase 3 — automatic policy smoke test

Walk to a fresh chunk (or use `/cci_world rescan_loaded`), then:

```
/cci_world policy_status
/cci_radar debug_resource_distribution
```

**Expected:** `total applied` counter increments; Radar distribution matches policy replacements.

---

## Troubleshooting

**`cci_radar jar not found` warning at Gradle startup:**
Build cci_radar first (`cd ../cci-radar && ./gradlew clean build`) or drop the jar in `libs/integration/`.

**Configuration cache stale after cci_radar rebuild:**
If Gradle serves a cached config that misses an updated jar, force re-configuration once:
```
./gradlew runClient -PcciIntegration=true --no-configuration-cache
```

**JourneyMap fails to load:**
Verify `journeymap-neoforge-1.21.1-6.0.0-beta.66.jar` and `jmi-neoforge-1.21.1-1.9.jar` exist in the CurseForge modpack mods folder. Update the paths in `build.gradle` if the modpack install location differs.
