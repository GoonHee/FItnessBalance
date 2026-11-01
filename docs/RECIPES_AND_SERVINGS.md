# Recipes and personal servings

## Setup

Pull master, sync Gradle, assemble and run. Review and publish the current `firestore.rules`, preserving any custom console-only policies. The new owner-scoped `recipes` and `personalServings` blocks belong inside `/users/{uid}`. Shared food permissions, saved-meal receipts and existing diary rules remain in place. These rules were tested in the local emulator, not deployed to production by this change.

Example after local Firebase CLI authentication:

```sh
firebase deploy --only firestore:rules --project fitnessbalance-e477b
```

No food database reimport, new exercise seed, recognition API or service account inside Android is required.

## Combined library

Food Log's button is now **Meals and recipes**. It opens the existing private saved-meal library with Saved meals and Recipes tabs. Existing saved meals require no migration and still log at their saved portions. The new **Create recipe** action is available here. On a saved-meal card, **Use as recipe ingredients** copies the saved foods into a new recipe draft; it does not alter the saved meal or log ingredients again.

### Build a recipe

1. Enter a recipe name and yield (the total number of equal servings the finished recipe makes).
2. **Add ingredient from food search** uses the existing lower-case prefix search, with pagination. Select a food and use its normal Food Details portion controls, including personal servings. The primary button becomes **Add to recipe**. It returns the nutrient snapshot without writing to the diary.
3. **Add ingredient manually** supports foods without a usable database match. Enter a measured amount and nutrients for that entire amount, not per 100 g. Any nutrient may be blank. Blank is unavailable, including energy; an actual entered zero is a known zero. The ingredient is marked user-entered, not source-verified.
4. **Multiply ingredient amount by** adjusts an ingredient and its known nutrients proportionally. Remove incorrect ingredients and add replacements through search/manual entry.
5. **Update nutrition preview** shows whole-recipe and per-serving summaries with coverage. Save, then return to the Recipes tab.

Food Details can return missing-energy ingredients if the reference basis is known. An unknown reference basis is not silently assigned per-100 units: the existing manual form still requires entered calories, or the recipe editor's explicit manual-ingredient form can record a measured ingredient with unavailable nutrition. No serving measurement repairs missing source metadata.

The editor retains draft inputs, IDs, revision and mutation UUID through Android state restoration. It confirms navigation away from the draft. This is not a cloud draft system. An interrupted source load can be retried using the editor's retry button. After an uncertain save, refresh the library before creating another recipe.

### Log, edit or delete

Choose the destination date at the top of the library, then **Log recipe servings**. Enter the number of recipe servings, choose Breakfast/Lunch/Dinner/Snacks, update the preview and confirm. For each ingredient:

```
logged nutrient = ingredient nutrient × requested recipe servings ÷ recipe yield
```

Example: 390 + 330 + 90 = 810 kcal in the whole recipe. Yield=3 gives 270 kcal per serving; 1.5 recipe servings gives 405 kcal.

The diary receives individual scaled ingredient entries in one transaction. This preserves partial nutrient coverage: a known subtotal is not transformed into a complete-looking aggregate recipe entry. Every ingredient carries recipe ID, name, revision, yield and requested servings. The diary displays recipe provenance beside its ingredients. Component entries remain independently editable/removable using existing diary behavior; they are not a synchronized group after logging.

Recipe logging reuses saved-meal request UUIDs, private durable pending requests and immutable `savedMealLogs` receipts, with `mealId=recipe:<recipeId>`. Retry has the same no-duplicate behavior as saved meals. A new intentional log has a new operation UUID.

**Edit recipe** changes ingredients/name/yield for future logs, using revision checks to reject stale edits. Rename and Delete also remain available. Editing/deleting a recipe or a serving does not recalculate or remove historical diary entries or old saved-meal snapshots.

## Personal servings

Food Details now has **Save this portion** and **My serving options**.

- Enter the measured amount in one serving (for example 150 g), then save a name such as “My rice bowl.” It saves the amount in one serving, not amount multiplied by the current number of servings.
- Select a named serving and choose **Use this serving**. The amount is applied and the number of servings resets to 1. Change that count as usual, e.g. 1.5 bowls becomes 225 g.
- Edit a serving's name/amount, or delete it. Units are fixed for that definition; create another definition to use another unit.
- Options belong to one exact catalogue food or barcode, under the signed-in account. They are paginated 30 at a time and never transferred to another food by name similarity.
- Only g and ml measurements are supported. No density assumption or g/ml conversion is made. Automatic nutrient calculation requires a matching known reference unit. Manual mode clears its calorie input when a named serving is applied, so calories for an earlier, differently sized serving are not reused accidentally.
- A named serving stores a measurement, not a nutrition snapshot or household-unit standard. Future direct logs still use the currently loaded food reference data and existing manual/estimated/source indicators. Saved recipes and diary entries retain their historical nutrition.

## Storage and constraints

```
users/{uid}/recipes/{recipeId}
  name, items[ingredient snapshots], yieldServings,
  revision, mutationId, createdAt, updatedAt

users/{uid}/personalServings/{servingId}
  name, foodKey, amount, unit, revision, createdAt, updatedAt
```

Recipe yield, requested servings, scaling factors and measured amounts must be finite, positive and at most 10,000. Scaled component portions must also satisfy existing diary limits. Recipes support 1–25 ingredients. Cooking yield is explicitly entered; ingredient weights are not summed to invent finished cooked weight. Raw/cooked ingredient state and values remain the user's responsibility.

Rules enforce account isolation, top-level schema/bounds, server timestamps and revision progression. Personal serving updates cannot change their food identity or unit. They do not verify the truth of self-entered ingredient nutrient measurements. Recipe saves use stable mutation UUIDs for acknowledgements and revision-checked transactions; existing snapshots are canonically normalized across Android JSON and Firestore numeric types.

## Validation and device checklist

Automated JVM tests cover recipe arithmetic, fractional servings, ingredient amount scaling, missing/zero nutrient coverage, manual-calorie consistency, stable retry IDs, recipe provenance, snapshot independence, numeric round trips, invalid yields/amounts/overflow and personal serving identity/unit matching. Emulator tests cover private CRUD, revisions, invalid inputs, immutable food/unit identity and historical diary preservation; the existing saved-meal retry/concurrency tests also run.

On the phone test: convert a saved meal; build a recipe through search without adding foods to the diary; add a manual incomplete ingredient; log fractional servings to another date; edit the recipe and verify an old log is unchanged; save/use/edit/delete a named portion; confirm g/ml mismatch is rejected; rotate the editor during a source load or draft; switch accounts; retry an uncertain recipe log. Physical-device acceptance and live rule publication are still required.
