# Progress charts and dated weight history

## Open the feature

Home → **Progress · charts and weight history** opens Overview, Calories, Nutrients and Macros tabs. Weeks run Monday–Sunday. Previous/next controls and the date selector browse weeks; future weeks cannot be selected. Refresh is in the toolbar menu. Changing tabs reuses the loaded snapshot rather than querying again.

Overview contains weekly food-calorie bars, a weight card and trend, estimated exercise calories and three macro charts. The Calories tab switches between stacked meal calories and food minus logged exercise, includes available-day averages, daily values and the highest recorded food calorie totals. Nutrients shows saved totals, available-day averages/coverage and current explicit goal references. Macros shows grams over the week and, only with complete recorded macro coverage, a 4/9/4 energy-share chart.

The UI follows the supplied examples' card/tab/chart organisation, using FitnessBalance's own implementation and colours. No MyFitnessPal code, assets or external chart dependency was copied. Custom Java Canvas charts have text descriptions/exact-value context; weight entries are available as text.

## What the charts mean

- Missing food-log days and future days are blank, not zero calories. A saved zero is distinct. Empty food logs do not revive obsolete daily calorie aggregates.
- Averages divide by days containing that nutrient/value, not blindly by seven. Partial entry coverage is labelled; complete coverage of saved entries does not prove that the person logged their whole intake. No completed-diary flag currently exists.
- Food/nutrient data comes from saved food entry snapshots. Compatible older calorie-only totals are displayed without inventing macros or named food rankings. Meal values without a recognised category are retained as Unassigned.
- Exercise estimates are read from manual logs, immutable guided records and existing daily guided contributions. Guided records are merged by record ID so they are not added twice. No query rewrites stored exercise totals and current profile weight never recalculates previous calories here.
- No recorded exercise contributes zero *logged exercise* when calculating a day's food-minus-exercise value. It does not mean no physical activity occurred. This arithmetic excludes resting metabolism and other activity and is not labelled a full surplus/deficit. The old homepage wording is corrected accordingly.
- Health Connect steps are not added as calorie expenditure.
- Goal lines/comparisons use current confirmed user-entered goals, not historical targets or a default 2000. Food comparisons apply only to days with complete saved calorie-entry coverage; the UI labels these as current-goal references. Net calories do not automatically change the food allowance.
- Macro percentages are withheld if any saved food entry lacks a macro. Available gram totals may still be displayed as partial. The percentage denominator is energy from recorded carbohydrate ×4, fat ×9 and protein ×4, not necessarily source-reported calories.
- Highest-food values are cumulative saved kcal over the selected week, grouped by food ID (or saved name for older entries), not newly fetched nutritional reference values.

## Weight measurements

Progress → **Weight history · log my weight** opens Measurements. Add or update one kg value per local date, view first/latest measurements and signed change, browse all/30/90/365-day charts, and edit or delete dated records. The entry list initially shows the newest 100 with a Show more control. Chart/export ranges include all loaded measurements in the chosen range. First/latest/change summaries use the full recorded history and are labelled accordingly.

Only actual dated measurements are graphed. Existing profile weight is shown separately as potentially undated; registration/profile edits do not fabricate old measurements or a starting date. A single measurement is a point, not an invented flat history. Connecting lines link measured dates and do not add measurements between them.

At save, **Also use this measurement as my current profile weight** is an explicit choice, checked by default for today's date and unchecked for backdated dates. Saving with it checked updates only profile.weight alongside the measurement, so future estimates can use it. Deleting a historical record leaves profile weight and all previously logged calories unchanged. Editing an existing date changes its value; correcting a date requires delete/re-add. Allowed numeric input is finite, >0 and <=300 kg, consistent with the exercise-estimate implementation; this is an input bound, not a recommended weight.

## Firebase schema and atomic writes

New owner-private paths:

```
users/{uid}/weightLogs/{yyyy-MM-dd}
  day, weightKg, revision, source="manual", mutationId,
  updateProfile, updatedAt=serverTimestamp

users/{uid}/weightHistory/state
  entries: {date: {weightKg, revision}, ...}
  updatedAt=serverTimestamp
```

A transaction reads state, the target record and user before writing. It checks revisions and primary/index agreement, then updates the dated record and complete index together. Optional profile update is a merge that preserves name, goals, XP and unrelated data. Stable mutation IDs acknowledge an already-committed identical retry; deletes do not recreate missing records. Different-date concurrent writes retry against the latest index. The index is bounded to 5000 measurements; it avoids re-reading thousands of dated documents for every chart. If the index is absent, the repository bootstraps from compatible dated records without inventing data.

Do not edit just one of the two weight paths manually in Console: that can create an index mismatch. Use the app or a deliberately coordinated migration.

## Required: deploy the new rules

The application code alone does not deploy Firebase rules. **Weight reads/writes require the new weightLogs/weightHistory blocks in this repository's firestore.rules.** Existing nutrition/exercise chart paths are unchanged, and weight-load failure is shown separately on Overview.

Review the updated file. If you maintain extra Console rules not present in the repository, merge them rather than discarding unrelated access policies. You can publish the complete reviewed file through Firebase Console → Firestore Database → Rules, or use your own authenticated local Firebase CLI:

```bash
firebase deploy --only firestore:rules --project fitnessbalance-e477b
```

No service-account upload, food-data reimport or weight-history seed is required. Rules isolate accounts, validate weight fields/revisions/server timestamps and require dated-record writes/deletes to match the same operation's fresh index. Owner-only index validation is bounded and structural; measurements remain self-reported, not medically or physically verified. This implementation did not deploy rules to production.

## Export

Export the displayed week or displayed weight range through Android's document picker. No broad storage permission, background upload or automatic sharing is used. CSV uses ISO dates, locale-independent numbers and blank unavailable nutrient cells. Weekly exports include entry/coverage counts so partial values can be recognised. Weight exports contain only explicitly recorded dates/values. Files omit account IDs and names.

## Android Studio checks

1. Pull current master on a clean branch, sync Gradle, assemble and run. Deploy the reviewed weight rules as above.
2. Home → Progress: browse previous/current week, change tabs and date. No previous week's values may remain under a new date header after a failed load.
3. Log a known food and a manual-calorie food. Verify saved calories, meal stacks, averages, missing-macro labels and withheld incomplete macro percentages.
4. Compare manual/guided calorie totals with Activity Log. Reopening/refreshing charts must not change logs or XP or count a guided session twice. Net can be negative and is labelled as arithmetic only.
5. Check empty/missing/future days and genuine zero entries. Verify that current goals are references, with no invented historic targets.
6. Weight history initially has no dated entries unless previously recorded. Save a known measurement today, optionally update profile; leave/reopen and verify. Add an older date and verify it does not silently become a current profile weight.
7. Edit the same date (no duplicate), delete first/latest/last measurements and verify first/latest/change statistics. Deletion preserves profile weight. Try conflicting edits on two devices; stale writes must fail rather than overwrite unnoticed.
8. Change range and export; no measurements may be inserted for gaps. Confirm single-point and equal-weight charts render sensibly. Check CSV missing values and coverage columns.
9. Test permission-denied/offline, cancelled/uncertain saves, account switching, background/rotation during saving, day/time-zone changes and export cancellation. Refresh/reopen after an interrupted editor; unsaved dialog text is not guaranteed to survive recreation.
10. Regression-check barcode food logging, diary edits, preferences, Health Connect and workout progress.

JVM tests cover aggregation, missing/partial/zero values, averages, meal totals, legacy compatibility, duplicate exercise credits, negative net, future days, goals, weight revisions/dates/bounds and CSVs. Local Firestore-emulator tests cover owner isolation, atomic index matching, invalid writes, deletion, profile preservation and concurrent weight transactions. Device/UI/production-rule acceptance is still required; no Android device was attached to the cloud environment.
