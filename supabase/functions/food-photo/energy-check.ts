// TypeScript twin of the SQL `food_energy_check` and the Kotlin `FoodEnergyCheck` (rule: planning/07-coach-design.md
// section 4 "Food energy check"). Same factors and thresholds; keep the three in step.
// Nulls count as 0; polyols are capped at carbs; `computed` and `gap` are returned unrounded.

export type EnergyCheckResult = "ok" | "mismatch" | "clearly_wrong" | "skipped";

export type EnergyCheck = {
    result: EnergyCheckResult;
    computed: number;
    /** computed minus the declared kcal. */
    gap: number;
};

export type EnergyCheckInput = {
    kcal: number;
    protein: number | null;
    carbs: number | null;
    fat: number | null;
    fibre?: number | null;
    polyols?: number | null;
    alcohol?: number | null;
};

export function foodEnergyCheck(i: EnergyCheckInput): EnergyCheck {
    const protein = i.protein ?? 0;
    const carbs = i.carbs ?? 0;
    const fat = i.fat ?? 0;
    const polyols = Math.min(i.polyols ?? 0, carbs);
    const computed = 4 * protein + 4 * (carbs - polyols) + 2.4 * polyols +
        9 * fat + 2 * (i.fibre ?? 0) + 7 * (i.alcohol ?? 0);
    const gap = computed - i.kcal;
    const limit = Math.abs(gap);
    let result: EnergyCheckResult;
    if (i.kcal <= 13 && computed <= 13) result = "skipped";
    else if (limit > 0.30 * i.kcal + 5) result = "clearly_wrong";
    else if (limit > 0.15 * i.kcal + 5) result = "mismatch";
    else result = "ok";
    return { result, computed, gap };
}

export const isFailing = (r: EnergyCheckResult): r is
    | "mismatch"
    | "clearly_wrong" => r === "mismatch" || r === "clearly_wrong";
