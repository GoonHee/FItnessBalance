# Missing calories and personal nutrition sources

This change keeps food logging usable while MyFCD is unavailable. It does not fill missing shared database calories with zero, infer unknown reference amounts, certify substitutions, or rewrite the original `foods` collection.

## Setup

Pull master and sync Gradle. Review/publish the repository Firestore rules to your intended project, preserving any console-only custom rules. The new `foodNutrition` block goes inside `match /users/{uid}`. Without it, private source lookup/save will be denied; unknown-calorie diary logging still uses the existing owner-scoped diary rules.

Example after local Firebase CLI authentication:

```sh
firebase deploy --only firestore:rules --project fitnessbalance-e477b
```

Rules were tested in the local demo emulator; production deployment was not performed by the code change. No service-account JSON is needed in Android. No catalogue reimport or new external provider credentials are required.

## Log without calories

In Food Details enable **Log without calories**. The numeric amount is cleared so an untouched default of 100 g is not mistaken for a measured portion. You may enter a measured g/ml amount and number of servings, or leave the amount blank and optionally describe a portion, e.g. “one plate, weight unknown.” Select a meal and log normally.

- The entry has no `calories` or `nutrients.energy_kcal` value; it is not a zero-calorie food.
- An unknown reference basis prevents all source nutrient scaling. If a measured amount has a matching known reference unit, other available source nutrients may be recorded, while calories remain intentionally unavailable.
- An unmeasured portion records no invented gram/ml amount or nutrient totals.
- The entry has `calculationMode=unknown`, `nutritionStatus=calories_unavailable` and `energyMethod=unavailable`.
- The same option can return an ingredient to the recipe editor without logging it to the diary.
- Named personal servings can supply a measurement in this mode, but do not establish a nutrition reference amount or g/ml conversion.

Measured unknown entries can be resized with their known nutrients retained. Unmeasured entries cannot use the portion-resize editor, since there is no measured quantity to scale. Both can be deleted normally.

## Add known calories later

For a diary entry with unavailable calories, **Add known calories** accepts kcal for that entire recorded entry. The confirmation explicitly says not per 100 g or per serving. Enter zero only when it is a real known zero. This updates the same entry in a stale-edit-checked transaction and recomputes totals; it does not append a second food, alter the shared catalogue or guess optional nutrients. Existing measured portions and any known nutrients remain as saved. Historical saved meals/recipes are independent snapshots and are not retroactively changed.

## Incomplete totals

The existing total maps are extended with `knownCalorieCount`, `missingCalorieCount` and `caloriesComplete`, atomically on the diary and daily dashboard. `totalCalories`/`caloriesConsumed` retain a numeric sum of available values for compatibility; this number is a subtotal if coverage is incomplete. All-unknown days have no energy value inside `nutrientTotals`, even though the compatibility subtotal is zero.

- **Food Log:** available calorie subtotal plus an explicit missing-food count, or calories unavailable when none are known.
- **Homepage:** known subtotal and missing count, with net calories withheld for incomplete or unknown coverage. Read failures show unavailable, not zero. Canonical food items override stale daily totals, including an explicitly empty diary. Exercise reconciliation can update displayed logged exercise without replacing nutrition coverage.
- **Progress:** partial calorie bars remain identifiable; all-unknown days have no calorie bar. Net is withheld for those incomplete days, and goal-difference comparisons are suppressed when the selected week has incomplete calories. Known-value averages are labelled partial. Legacy aggregate-only days retain their saved subtotal but have unknown coverage, withheld net and partial indicators. CSV retains blank unavailable cells and the partial/coverage fields.
- **Recommendations:** the existing energy-coverage gate continues to withhold a remaining-goal budget when food calories are incomplete. It does not treat a missing entry as zero allowance consumed.

“Complete” means all recorded entries have calorie values, not that all actual eating has been logged or that source values are verified. The app still distinguishes food minus logged exercise from total daily energy balance.

## My nutrition source

For a non-barcode catalogue food, open **My nutrition source** from Food Details:

### Package label

Enter the label's reference amount and g/ml unit, values for that amount, a source description, and confirm that the food/preparation/unit match. Optional nutrients remain blank. Calories or all three macros are required for a usable energy calculation. Values are normalized to per 100 g/ml. Macro-derived energy uses the existing clearly labelled 4/4/9 estimate; a true recorded zero is preserved.

The source is private and attached to this exact catalogue document ID. Future Food Details views load it automatically and display its source description. Use the same screen to replace it or remove it. Revisions reject stale concurrent changes; stable mutation IDs acknowledge an identical retry. A different payload after an uncertain committed save requires reopening the screen. Removal restores the original source for future logs only.

### Find alternative nutrition

Search the beginning of a food name in the **existing Firestore catalogue**. Results are paginated; only candidates with a known per-100 reference unit and usable calories/macros are offered. Review the candidate name, source, reference unit and nutrients, then explicitly confirm raw/cooked state, ingredients, sweetness and suitability before saving an approximate substitution.

This does not automatically match names or call USDA/another paid API. The catalogue may have no appropriate alternative; label entry, recipes, manual calories or unknown logging remain available. An alternative is always labelled approximate, not verified nutrition for the original food. The original food identity is retained with `alternativeFoodId`/`alternativeName` and the private source revision in the log. No density conversion is attempted.

Barcode foods retain the existing barcode-product label editor; the new attached private-source screen is for catalogue foods. Unknown-calorie logging also works after entering the existing unmatched-barcode manual fallback.

## Storage and history

```
users/{uid}/foodNutrition/{originalFoodId}
  originalFoodId, food_name, nutrition, nutrition_basis,
  source_kind (label|alternative), source_database, source_note,
  nutrition_verification, revision, mutationId, confirmedAt, updatedAt
  label_amount/label_unit/label_nutrition (label)
  alternativeFoodId/alternativeName (alternative)
```

Only the owner can read/write/delete. Rules enforce top-level shape, reference metadata, revisions and server timestamps; they do not certify the truth of self-entered nutrition. Private source lookup requires a server response. If it fails, Food Details explicitly says it could not check the private source and is showing the original record; retry if a saved source is expected.

Snapshots retain source kind/revision, substitution identity and unavailable-calorie status through saved meals and recipes. Editing a source never rewrites older logs, meal templates or recipe ingredients. No UI migrates old unknown values into new estimates automatically.

## Validation

JVM tests cover unknown basis/unit behavior, unmeasured portions, partial/all-unknown/real-zero totals, net withholding, dashboard metadata, legacy aggregates, later calorie resolution, saved-meal/recipe preservation, measured unknown resizing, label normalization, macro fallback, source identity, invalid reference values, and progress with missing calories. Firestore emulator tests cover owner isolation, private source CRUD/revisions, invalid metadata, unchanged shared foods and unknown-entry metadata, in addition to all existing rules tests.

Device acceptance is still required: log an unmeasured food with unknown calories; verify all three screens and recommendation budgets; add a true known zero and a later known calorie value; save a label; reopen Details and check its unit/portion calculation; confirm a generic alternative; remove a private source; test recipe/saved-meal snapshots, rotation and account switching. Live rule deployment and a real-device test were not performed in the cloud.

## Existing source values with an unknown reference amount

Food Details now shows **Confirm source reference amount** when usable source calories/macros are already present but the stored reference basis is unknown. This avoids retyping existing nutrient values into the package-label editor. It opens My nutrition source and retrieves the original record from Firestore; it does not need access to the MyFCD website.

Review the displayed source values, enter the quantity/unit they actually describe, add where you confirmed that reference and check the explicit confirmation. Unknown reference quantities are left blank, not defaulted to 100 g. Your eaten portion is a separate quantity and must not be used as the source reference unless that is what the source actually states. If the reference cannot be established, use the existing unavailable/manual/alternative flows instead of guessing.

For example, **if** the displayed rice values (130 kcal, 2.3 g protein, 30 g carbohydrate, 0.1 g fat, 0 g fibre, 6 mg sodium) are confirmed per 100 g, a 150 g portion calculates as 195 kcal, 3.45 g protein, 45 g carbohydrate, 0.15 g fat and 9 mg sodium. Confirming the same values per 200 g instead produces 65 kcal per 100 g; the app does not infer the reference from the calorie magnitude.

The normalized definition is stored privately using the existing `foodNutrition` user-entered reference schema (`source_kind=label` for compatibility), with `source_database=User-confirmed source reference`. The UI identifies it as a confirmed source reference rather than a package label. Logs retain `basisConfirmationSource=user_reference_confirmation` and `energyMethod=user_confirmed_reference` (or the existing labelled macro estimate). The shared food record and previous diary/recipe/meal snapshots remain unchanged. No new rules deployment is needed beyond the existing `foodNutrition` rules.

After a successful save, Food Details reloads the private reference and turns off the old manual/unavailable mode so the normal portion calculator can operate. Check the eaten amount and number of servings before logging. Device verification remains required.
