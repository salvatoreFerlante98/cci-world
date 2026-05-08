package com.cciworld.coe;

import com.tom.createores.Config;
import com.tom.createores.CreateOreExcavation;
import com.tom.createores.recipe.VeinRecipe;
import com.tom.createores.util.ThreeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Optional;

/**
 * Helper to compute COE finite-amount math without mixin/reflection.
 *
 * <p><b>Audit (COE 1.6.8) — formula reconstructed from OreData bytecode:</b></p>
 * <pre>
 *   total_units(chunk) = round( ( minA + (maxA - minA) * randomMul ) * Config.finiteAmountBase )
 *   remaining(chunk)   = total_units(chunk) - extractedAmount
 * </pre>
 * where:
 *  - {@code minA = VeinRecipe.amountMultiplierMin} (alias of {@code getMinAmount()}),
 *  - {@code maxA = VeinRecipe.amountMultiplierMax} (alias of {@code getMaxAmount()}),
 *  - {@code Config.finiteAmountBase} is a global int (server config),
 *  - {@code randomMul ∈ [0,1]} is OreData's {@code randomMul}.
 *
 * <p>So to obtain a target {@code units_per_chunk} we invert:</p>
 * <pre>
 *   randomMul = (units / base - minA) / (maxA - minA)        // when maxA > minA
 * </pre>
 * which is then clamped to [0,1]. The result is exact when
 * {@code minA*base ≤ units ≤ maxA*base} and {@code maxA > minA}; otherwise it is
 * the closest reachable amount given the recipe's bounds (see
 * {@link FiniteSolution#exact}).
 */
public final class COEFiniteMath {

    private COEFiniteMath() {}

    public record FiniteSolution(
        float randomMul,
        long expectedTotalUnits,
        boolean exact,
        boolean finiteSupported,
        String note
    ) {}

    /** @return the {@link VeinRecipe} for the given recipe id, or empty. */
    public static Optional<VeinRecipe> findVeinRecipe(RecipeManager mgr, ResourceLocation id) {
        if (mgr == null || id == null) return Optional.empty();
        for (RecipeHolder<?> h : mgr.getAllRecipesFor(CreateOreExcavation.VEIN_RECIPES.getRecipeType())) {
            if (h.id().equals(id) && h.value() instanceof VeinRecipe vr) {
                return Optional.of(vr);
            }
        }
        return Optional.empty();
    }

    /** Returns whether the given recipe is finite (NEVER means infinite). */
    public static boolean isFinite(VeinRecipe recipe) {
        if (recipe == null) return false;
        ThreeState s = recipe.isFinite();
        if (s == ThreeState.NEVER) return false;
        if (s == ThreeState.ALWAYS) return true;
        // DEFAULT: defer to global config
        return !Config.defaultInfinite;
    }

    public static int finiteAmountBase() {
        return Config.finiteAmountBase;
    }

    /**
     * Computes the {@code randomMul} that yields approximately {@code unitsPerChunk}
     * given the recipe's amount bounds and the global finite base.
     *
     * <p>Returns {@link FiniteSolution#finiteSupported}=false when the recipe is
     * infinite — in that case the caller should fall back to a sensible default
     * (e.g. {@code randomMul = 1.0}).</p>
     */
    public static FiniteSolution solveRandomMul(VeinRecipe recipe, long unitsPerChunk) {
        if (recipe == null) {
            return new FiniteSolution(1.0F, 0L, false, false, "recipe_null");
        }
        if (!isFinite(recipe)) {
            // Infinite vein — randomMul still scales drop weights; keep 1.0
            return new FiniteSolution(1.0F, Long.MAX_VALUE, false, false, "recipe_infinite");
        }
        int base = finiteAmountBase();
        float minA = recipe.getMinAmount();
        float maxA = recipe.getMaxAmount();
        if (base <= 0) {
            return new FiniteSolution(0F, 0L, false, true, "finiteAmountBase_le_0");
        }
        long minTotal = Math.round((double) minA * base);
        long maxTotal = Math.round((double) maxA * base);

        // Degenerate recipe: min == max → totale fisso, randomMul ignorato dalla formula.
        if (Float.compare(maxA, minA) == 0) {
            boolean exact = (unitsPerChunk == minTotal);
            return new FiniteSolution(0F, minTotal, exact, true,
                exact ? "fixed_amount_recipe" : "fixed_amount_recipe_clamped");
        }

        // Fuori range → clamp e ritorna closest endpoint
        if (unitsPerChunk <= minTotal) {
            return new FiniteSolution(0F, minTotal, unitsPerChunk == minTotal, true,
                unitsPerChunk == minTotal ? "ok_at_min" : "below_min_clamped");
        }
        if (unitsPerChunk >= maxTotal) {
            return new FiniteSolution(1F, maxTotal, unitsPerChunk == maxTotal, true,
                unitsPerChunk == maxTotal ? "ok_at_max" : "above_max_clamped");
        }

        // Caso normale: invertiamo la formula
        double mul = ((double) unitsPerChunk / (double) base - (double) minA) / ((double) maxA - (double) minA);
        if (mul < 0.0) mul = 0.0;
        if (mul > 1.0) mul = 1.0;
        float randomMul = (float) mul;

        long achieved = Math.round(((double) minA + ((double) maxA - (double) minA) * randomMul) * base);
        boolean exact = (achieved == unitsPerChunk);
        return new FiniteSolution(randomMul, achieved, exact, true,
            exact ? "ok_exact" : "ok_rounded");
    }
}
