# Saved meals

Saved meals are private reusable groups of existing diary portions. They are not recipes, serving-size conversions or certified nutrition records. This feature does not modify the shared `foods` collection.

## Android Studio and Firebase setup

1. Commit or preserve your local changes, then `git pull --ff-only origin master` on master.
2. Sync Gradle, assemble and run the app.
3. Review the repository `firestore.rules` and publish it to the intended Firebase project. If you have custom console-only rules, merge the `savedMeals` and `savedMealLogs` blocks inside `match /users/{uid}` instead of discarding your other policies. Example CLI command after authenticated setup:

   ```sh
   firebase deploy --only firestore:rules --project fitnessbalance-e477b
   ```

The new rules have been tested against a local emulator, not deployed to production by this change. Existing food/weight/exercise policies remain in place. No food import, extra index, service-account key in Android, or exercise seed is needed.

## Use

- Open **Food Log** and choose a date. Tap **Saved meals**, below the search panel.
- **Save foods from a diary date** opens a source-date picker. Select 1–25 entries from that date, then choose **Save meal**, enter a name and save. The original diary is unchanged.
- The saved library shows food count and nutrient totals with availability/coverage. Meals are paginated, 30 per page; **Load more saved meals** continues by document ID.
- **Log this saved meal** previews every food at its original saved portion and the nutrient summary. Choose Breakfast/Lunch/Dinner/Snacks and confirm. The destination date can be changed at the top of the screen before reviewing.
- Return to Food Log and select that destination date to see the new individual foods. Existing edit/remove actions still apply to them; changing those diary entries does not edit the saved template.
- **Rename** changes the reusable name. **Delete saved meal** removes only the template; existing diary entries remain.
- This first version reuses exact portions. To change a template's component foods or amounts, adjust diary entries and save a new meal, then delete the old template if desired. Recipes, ingredient yields and personal named servings are separate future work.

## Data model

```
users/{uid}/savedMeals/{mealId}
  name: 1–80 characters
  items: 1–25 historical portion/nutrient snapshots
  revision: integer, starts at 1
  createdAt, updatedAt: server timestamps

users/{uid}/savedMealLogs/{operationUUID}
  date, mealId, entryCount, createdAt
```

Snapshot fields retain food identity/name, portions, the six core nutrients when available, source/basis and manual/estimated-energy provenance. Diary IDs, old meal assignment and logging timestamps are excluded. Snapshots do not reread mutable catalogue values or infer missing nutrition. Legacy calorie-only entries retain energy but do not gain invented macros or serving amounts. Zero remains a known value. Additional catalogue nutrients were not part of the existing diary snapshot and are not newly inferred here.

Logging gives every component a new deterministic UUID derived from the request UUID plus its position, a selected meal, a new logging timestamp and saved-meal provenance. Duplicate ingredients remain separate entries. A new intentional logging action has a new request UUID.

`FoodLogRepository.saveMeal` reads the operation receipt and diary before any writes. In one transaction it appends all components, recalculates `FoodNutrition.totals`, merges food-related fields into `dailyData`, and creates the receipt. Exercise fields are preserved. An existing receipt makes the exact request a no-op, even if its logged foods were later edited/deleted. A saved-meal insertion is capped at 500 resulting diary entries and Firestore's document size limit still applies.

A transaction that fails does not partially insert components. An uncertain result can be retried with **Retry last log** using the same request. The pending request is stored in account-specific private no-backup storage before submission, survives screen recreation/reopening, and is cleared on acknowledgement. It is bounded to 64 KiB; a request that cannot be persisted is not submitted. App-data deletion or reinstall can remove this pending state; inspect the diary before issuing a new intentional log after losing it.

While pending, other save/log/rename/delete actions are blocked. **Discard pending request** stops retries, not a possibly committed write, and warns the user to check the destination diary before logging again. Successful operations remain recorded as immutable receipts in Firestore. They should not be removed as part of routine template deletion or retries.

Rename/delete transactions check the template revision. Template items are immutable under the initial rules; renames require the next revision and preserve creation timestamp and items. Rules enforce ownership, top-level shape, bounds and timestamps, but do not medically verify individual ingredient values. Existing diary writes remain owner-scoped as before.

## Verification

- Unit tests cover snapshot independence, identity stripping, missing/zero values, legacy/manual nutrition, stable IDs, distinct repeated components, new intentional operations, deduplication, totals and input limits.
- Firestore emulator tests cover owner read/list/create/rename/delete, cross-account/anonymous denial, invalid shapes and revisions, immutable receipts, concurrent/retried insertion, preserved exercise fields and retry after logged foods have been removed.
- Android build and lint validate integration. Physical-device acceptance and production rule publication are still required.

Device checklist: create a multi-food meal; log it to another date/meal; compare every portion and the energy/macronutrient totals; try an incomplete/manual entry; retry through a connection interruption; rotate/reopen with a pending request; rename/delete a template and verify old diary entries remain; switch accounts and verify libraries stay private. Creation and library/source loading require an online server response. This is not a new offline diary synchronisation implementation.
