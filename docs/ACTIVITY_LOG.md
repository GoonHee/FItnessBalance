# Integrated Activity Log

Based on current master `317fda05f5e6bb664746cd5aac8a3507d45c4932`. This update replaces the prototype exercise screen with a theme-aware Activity Log while keeping the existing Java/XML application, food/barcode features, workout records and gamification rules.

## Use it

Open **Exercise → Activity Log**. Choose a date to see manual activities and guided workouts together, plus a combined estimated-calorie total and source breakdown. The Exercise Home card and main dashboard use the same accounting.

Select **Add Manual Activity**, choose an exercise type or Other/Custom, enter minutes, review the estimate and save. Profile preferences select an initial familiar activity where available. The form uses profile weight, with a clearly documented 70 kg reference when weight is missing/invalid. Common activity MET values are retained from the prototype; estimates now use the same conventional formula as guided sessions:

```text
Estimated kcal = MET × 3.5 × weight in kg / 200 × duration in minutes
```

These are gross MET estimates, not measurements of net active energy or medical assessments. Wearable imports will require a separate energy-normalization/matching policy later. No wearable SDK, permissions, OAuth connection or import was added here.

New manual entries default to completed. Existing planned entries stay planned and are excluded from daily totals until explicitly edited and marked completed. Manual entries have Edit/Delete controls. Edits preview a fresh estimate before the user confirms. Historical estimates are never recalculated merely by loading the log. Guided sessions are read-only and link to their existing result screen, preserving immutable records and rewards.

Guided workouts are logged automatically. A manual entry should describe a separate activity; there is no automatic matching of two separately recorded physical workouts. The duplicate protection applies to repeated saves of the same entry/session, not to two intentionally created entries with different IDs.

## Storage and compatibility

No new Firestore collection or rule deployment is required. The existing rules already permit owner-only `exerciseLogs`, `dailyData` and reads of `workoutRecords`. No catalogue reseed or live-data migration script is required. No Firebase writes were performed by development tools against your live project.

- `users/{uid}/exerciseLogs/{yyyy-MM-dd}` remains the manual source. Existing `exercises`, `date`, `totalCaloriesBurned`, and document metadata remain compatible. `totalCaloriesBurned` in this document continues to mean **manual-only** calories.
- Old entries retain their original name, duration, completion state, calories and unknown fields. Stable legacy IDs are derived in memory; a subsequent manual mutation persists IDs alongside the original data. Identical old entries at separate positions remain separate activities.
- New/edited entries add `entryId`, `revision`, `activityType`, `metValue`, `weightKg`, `source`, `calorieMethod`, `lastMutationId` and `legacySummary` metadata. Duration can be fractional minutes. Existing numeric fields are read through `Number`.
- Aggregate-only historical manual days are shown as a historical total with unknown duration. A pre-integration dashboard-only calorie total is preserved as a historical manual total when no source log exists. Corrupt/non-numeric data is reported rather than silently erased.
- `users/{uid}/workoutRecords/{sessionUuid}` stays the immutable guided source. No duplicate manual row is created for a guided session.
- `users/{uid}/dailyData/{day}.caloriesBurned` is now the combined exercise estimate. Additional fields are `manualCaloriesBurned`, `structuredCaloriesBurned`, `exerciseActiveSeconds`, `exerciseEntryCount`, `exerciseTotalsVersion` (2), and `structuredExerciseContributions` keyed by workout-record ID.
- Daily fields such as `caloriesConsumed`, `nutrientTotals`, `nutrientCoverage`, `foodEntryCount` and unknown unrelated fields are preserved using selected-field merges.

Guided rest time is excluded from active seconds. A historical guided record with no active-duration field contributes its recorded calories but does not invent active minutes. Records without a `localDay` can be found by their server completion timestamp within the selected local day. Records with a stored local date keep that date instead of being reassigned after a timezone change.

## Atomicity and retries

`ExerciseLogRepository` manages all manual mutations and daily reconciliation. `ExerciseLogService` provides pure, unit-tested legacy mapping and accounting. `ExerciseCalorieService` provides the estimate formula and input validation.

Adding/editing/deleting a manual entry reads the current manual day and daily contribution map inside a transaction, then writes the manual log and recalculated daily fields together. Concurrent changes to another entry are preserved. Stable entry IDs and mutation IDs prevent duplicate saves after network uncertainty or screen recreation. Reusing an ID with different data produces a conflict instead of silently discarding the change. Stale revisions fail safely. Retrying a deletion of an already removed entry is harmless.

`GamificationRepository.complete()` reads the manual day and daily document before its first write. A guided calorie contribution is keyed by the same immutable session/record ID and commits atomically with the workout record and existing rewards. Reopening results or retrying a session does not add the calories again.

When Activity Log, Exercise Home or the dashboard refreshes, older guided records are fetched and merged by ID into the selected day's map. This brings pre-integration workouts into calorie totals without re-awarding XP, rewriting their records or deleting prior contributions. Contributions from a newer transaction are preserved if they arrived after the history query started. An empty day creates no artificial workout or quest credit. Reading an existing day may update its derived totals and `lastUpdated` timestamp.

Manual activity does not change total XP, path progress, quests, achievements or Weekly Momentum in this increment. Guided workout reward eligibility and repeat reductions are unchanged. Calories never determine rewards.

## UI and lifecycle

The new screen uses the existing Material theme, theme-aware colors, accessible text sizes, labelled controls, RecyclerView rows, loading/empty/error/retry states and system insets. A date picker restricts manual entry to today or earlier. The manual form is a DialogFragment with stable operation IDs and saved view state so recreation can retry an uncertain save safely. Per-entry revision conflicts ask the user to reload rather than overwrite another edit.

The Activity Log and dashboard reconciliation require network. There is no offline save queue. A failed save keeps the form open for retry; the dashboard can still show its previously saved daily totals if refresh fails. If an older APK continues writing the prototype's manual-only dashboard total, the new app's next successful reconciliation restores the combined total. Use the updated app on all active devices to avoid that older writer behavior.

Normal form validation accepts positive duration up to 1,440 minutes and activity names of 1–100 characters. The manual day has a defensive 1,000-entry cap. Workout and manual record IDs prevent write replay; they do not detect real-world overlap when a person manually logs a guided workout a second time. Guided records cannot be deleted through Activity Log. Administrator deletion/editing of immutable records and very large daily contribution maps require a separate repair/retention policy.

## Validation

- Debug and unsigned release APK builds.
- 70 JVM tests: original services plus calorie estimates, legacy mapping, planned/completed handling, mixed totals, stable IDs, repeat-save behavior, edit conflicts, idempotent delete, partial legacy records and invalid data preservation.
- 32 Firestore emulator tests: existing security/reward suite plus concurrent manual/guided writes, one-time guided credits, manual edit/delete, food/nutrient field preservation, historical backfill and account isolation.
- Lint: no errors; nonblocking warnings remain in the app.

The Firestore JavaScript harness verifies transaction/security contracts; it does not execute Android activity code. No Android device/emulator is attached here, so interactive verification remains pending.

```sh
bash gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
npm --prefix tools/security-tests install
tools/security-tests/node_modules/.bin/firebase emulators:exec --project demo-fitnessbalance --only firestore 'node --test tools/security-tests/rules.test.cjs'
```

### On-device checklist

- [ ] Existing completed and planned manual entries are displayed with their original calories.
- [ ] Adding a completed manual activity updates Activity Log, Exercise Home and dashboard totals.
- [ ] Planned manual entries are excluded; editing and marking completed includes them.
- [ ] Editing duration recalculates the preview and updates only that manual entry.
- [ ] Deleting a manual entry updates totals without changing guided records or XP.
- [ ] A guided workout contributes calories immediately when its feedback save succeeds.
- [ ] Reopening its result or retrying a save does not add calories or XP twice.
- [ ] Older guided records appear when their date is opened, without extra rewards.
- [ ] Different dates stay isolated; future manual dates cannot be selected.
- [ ] Rotation/background/restart during an open form or uncertain save can recover safely.
- [ ] Large fonts, TalkBack, dark mode and landscape remain usable.
- [ ] Login, profile, food history, barcode scan and nutrient dashboard behavior remain intact.
- [ ] A second account cannot read or modify the first account's entries or totals.
