# Reviewed food suggestions — food-rules-v1

## User flow

1. Food log → **Suggested foods** uses the selected diary date. The prototype supports today and earlier dates, using current profile goals (not historical goal snapshots).
2. Select breakfast/lunch/dinner/snacks and enter a maximum calorie allowance for **one food portion**. No arbitrary meal percentage or 2000-kcal default is supplied.
3. Optionally limit to a remaining confirmed daily calorie goal. When the complete recorded calorie total is available, the effective limit is min(user allowance, remaining goal), clamped at zero. The effective limit is displayed. Reaching a goal does not prevent normal search or logging; users can choose an independent allowance by unchecking the option.
4. Get up to three alternatives, each with reviewed portion, energy/macros, explanation and an estimate label where applicable. These are individual alternatives, not a combined balanced meal or medical diet plan.
5. Review food and portion opens the existing details screen with the suggested amount, matching reference unit, one serving and selected meal. Values are reloaded. A changed/unknown unit prevents applying the suggested amount. Users still confirm before logging; viewing or generating suggestions writes no food logs, calories or XP.
6. Details → **Food recommendation preference** can set No preference / Prefer / Avoid for a shared food. Per-account profile arrays are updated transactionally, preserving other fields. Avoid wins over Prefer in ranking. Preferences do not override data-quality or dietary constraints and do not filter ordinary search. A maximum of 500 IDs per preference list is supported.
7. Returning to suggestions invalidates old cards. Regenerate after changes to logs/goals/preferences. Recommendations use a data snapshot rather than reserving a calorie budget across devices.

## Initial catalogue setup is required

Existing imported nutrient data does not establish dietary suitability, meal category or a practical serving size. **No live food metadata was changed or fabricated by this implementation.** The app only queries foods with recommendation_ready=true. Until some foods are reviewed, it will show that suggestions are not available; normal food search remains usable.

The provided export produced 495 template rows with known basis and positive energy. All template rows default to include=no. This is eligibility for review, not approval for recommendation. The 1267 Current/1997 records with unknown basis cannot be enabled until their basis is established separately. Missing-calorie incomplete records are not repaired by this module.

From your project root, with Python installed and the food export available locally:

```bash
python tools/food_recommendation_catalogue.py template --foods fitnessbalance_firestore_foods_basis_fixed.json --out food-recommendation-review.csv
```

Open the CSV in Excel. Start with 3–10 foods you can review against a source or package label. For each chosen row:

- Set include=yes. Leave other rows no (no change); use remove to disable an already enabled food.
- Enter meal_types using lowercase codes separated by |, for example breakfast|snacks. This is an explicit suitability annotation, not inferred from a food name.
- Enter a supported serving_label and measured/label-supported serving_amount. The serving_unit must match the existing g/ml reference basis. Do not invent bowl/spoon/packet conversions.
- Add a reviewer_id and review_notes describing the evidence for the portion and classification.
- dietary_tags may use halal, vegetarian, vegan, avoid_pork, avoid_alcohol. Each tag requires corresponding source evidence in dietary_evidence as JSON, such as {"vegetarian":"Ingredient/source evidence reviewed on DATE"}. Leave tags empty if unknown. Never use the example text as actual evidence; halal suitability needs appropriate evidence, not the food name or merely a nutrient label.

Generate a preview with no Firebase access:

```bash
python tools/food_recommendation_catalogue.py upload --foods fitnessbalance_firestore_foods_basis_fixed.json --review food-recommendation-review.csv --out food-recommendation-patches.json
```

Inspect the patch JSON. It changes recommendation metadata only, not nutrition, names, source basis or existing logs. An eligible metadata shape is:

```json
{
  "recommendation_ready": true,
  "meal_types": ["breakfast", "snacks"],
  "serving_options": [{"label": "YOUR VERIFIED PORTION", "amount": 30, "unit": "g"}],
  "dietary_tags": [],
  "dietary_tags_status": "unknown",
  "dietary_evidence": {}
}
```

The amount above is illustrative; do not assign it to foods without evidence. App-side metadata allows up to 5 options per food; the CSV importer creates one option per selected row. Keep the active reviewed catalogue at most 100 foods for this MVP. This is separate from normal search across the full food collection.

When the preview is correct, install firebase-admin on your own computer if needed, and explicitly apply using your existing LOCAL admin credential path:

```bash
python -m pip install firebase-admin
python tools/food_recommendation_catalogue.py upload --foods fitnessbalance_firestore_foods_basis_fixed.json --review food-recommendation-review.csv --out food-recommendation-patches.json --apply --project fitnessbalance-e477b --credentials "PATH_TO_YOUR_LOCAL_SERVICE_ACCOUNT.json"
```

Do not upload or commit that credential. It is not part of the Android app. The script checks the project, document existence and live food identity/basis/nutrients against the reviewed export; writes a metadata backup; uses one atomic update batch with update-time preconditions; and verifies changed fields afterward. If live data differs, obtain/review an up-to-date export rather than bypassing the check. Existing enabled foods omitted from the review are preserved. A timestamp in old backup metadata is represented by an explicit type/value object.

For a tiny catalogue, the same fields can be entered manually in Firebase Console after reviewing a food. Shared food metadata remains read-only to Android clients. No new Firestore collection, custom composite index or rules deployment is needed with the repository's existing foods-read/profile-owner rules.

## Filtering and scoring

Hard filters run before ranking:

- Explicit recommendation_ready and matching reviewed meal_types.
- Known matching per-100 g/ml basis, no outstanding package-basis confirmation.
- Supported positive labelled portion; known positive source energy or complete-macro 4/4/9 energy estimate.
- Avoided food IDs excluded.
- Every selected dietary preference must have the exact tag, reviewed status and nonempty evidence. Unknown suitability is excluded; no inference from names or automatic relaxation.

Source energy, including zero, is never replaced. Zero-energy foods are not ranked as energy-containing single-food suggestions. The general calorie estimate is labelled and has the existing fibre/alcohol/polyol limitations.

One best reviewed option per food is ranked by:

- 60 × portion calories/effective allowance.
- Up to 20 for average contribution to known positive remaining macro targets.
- +15 for an explicitly preferred ID.
- −10 if that food ID appears on the selected date or six preceding diary days.

The weights are transparent prototype product rules, not clinically validated dietary optimisation. They favour allowance fit, available goal contribution, explicit preference and modest variety. Missing logged nutrient coverage prevents treating that nutrient total as an exact remaining goal. Missing candidate macros receive no contribution credit; they are not invented as zero. Dietary restrictions are not allergy/certification guarantees. Equivalent foods with different database IDs are not automatically merged.

## Data and audit

Profile fields: preferredFoodIds/dislikedFoodIds plus existing dietaryPreferences and nutrition goals. Catalogue fields are those reviewed above plus reviewer, notes, review timestamp and metadata version. No source nutrition is overwritten.

The last recommendation snapshot is saved account-privately on-device, excluded from backup. It records rule version, date/meal, requested/effective allowances, goals, totals/coverage, preferences, recent IDs and best eligible portions/scores/reasons. It is not a complete catalogue archive. Oversized/cache-save failure is reported rather than claiming an audit was saved. **Remove saved recommendation explanation** clears it. Generating a new recommendation overwrites it. No audit is uploaded to Firestore or analytics.

## Acceptance

- Before setup: clear not-ready state, working normal food search.
- After review of a few foods: relevant meal/portion cards, correct arithmetic, distinct food IDs and explanatory reasons.
- Halal/vegan/other constraints with unknown metadata: no unsuitable fallback. Each selected tag/evidence must be satisfied.
- Prefer/Avoid/Neutral persist per user and change regenerated ranking; another account must not inherit them.
- Manual-calorie logs with missing macros: do not report precise remaining macros. Older unconfirmed 2000 goals must not silently constrain allowances.
- Reached goal: effective zero only when the user elects to respect the goal; independent allowance and normal logging remain possible.
- Detail handoff preserves date/meal/amount/unit; source unit changes are flagged; viewing does not log food automatically.
- Offline, missing profile, malformed metadata, more than 100 enabled foods and account changes: readable failure, no writes to food logs.
- Regression: food edit/delete, barcode fallback, workouts, Health Connect and XP remain unchanged.

JVM tests exercise ranking, constraints, budgets, missing coverage, portions and calorie fallback. Python tests exercise metadata-only changes, review/evidence requirements and live-export mismatch rejection. No live catalogue upload or device acceptance was performed in the cloud.
