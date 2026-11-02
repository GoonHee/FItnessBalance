# Wearable workouts through Health Connect

## Phone setup

The supported path is band → Mi Fitness (or another companion app) → Health Connect → FitnessBalance. This module reads Health Connect; it does not pair with Bluetooth hardware or require a Xiaomi SDK.

1. Pull master, sync Gradle and run on your compatible phone. Publish the updated Firestore rules before confirming a workout. No seed changes are needed. See [Workout confirmation](WEARABLE_WORKOUT_LINKING.md).
2. Confirm Mi Fitness has exported a recent exercise session into Health Connect. A session visible only in Mi Fitness cannot be imported by this module.
3. In FitnessBalance open **Exercise → Workout with wearable**, or **Profile → Connect health data → Wearable workouts**.
4. Tap **Connect / request read permissions** and allow exercise-session reading and optional active-calorie reading. Existing steps/sleep/heart permissions do not automatically include the new permission.
5. Tap **Sync wearable workouts**. The Connect health data screen's **Sync health data now** also imports workouts alongside its existing summaries.

The workout screen requests `READ_EXERCISE` and `READ_ACTIVE_CALORIES_BURNED`; the general health screen offers all supported read types. No total-energy, route, background, extended-history or Health Connect write permission is requested.

## Display and scope

A rolling snapshot contains ended sessions whose start is within the previous seven actual 24-hour periods, as of the successful sync. Sessions starting before that boundary and sessions ending after the sync cutoff are omitted. The source app may update records later; this is not independent verification that an exercise was physically completed. A stale snapshot remains visibly cached until another successful read; it is not described as a current live feed.

The screen shows activity type, optional source title, start/end, elapsed duration, source app/package, source modification time, last successful workout sync and its window. Times use the phone's current time zone. Known activity codes have readable names; unknown future codes retain their numeric type. Elapsed duration includes any pauses inside the interval and is not active exercise time. Source app labels may be unavailable under Android package visibility rules, in which case the exact package is shown.

Optional active calories are matched conservatively using complete contiguous intervals from the same source. Active duration, distance, route/location, exercise segments/laps and raw heart-rate samples are not imported. Missing calories remain **unavailable**, never an invented estimate. See the confirmation guide for the exact energy matching policy.

## Reconciliation and local storage

`HealthConnectBridge` reads `ExerciseSessionRecord` through the official SDK, with pagination. Reads are limited to 20 pages of 200 records and 1000 relevant rows. An exceeded limit, failed page or invalid payload is a failed read, never a truncated successful import. The SDK's permission list is checked again before returning data.

`WearableWorkoutService` validates IDs, package, type, timestamps and the exact rolling window. Repeated IDs are deduplicated by latest modification time; distinct IDs remain separate even if their times match. This avoids guessing that two overlapping source records must be duplicates.

`WearableWorkoutStore` is a SQLite cache in Android's private no-backup directory, with primary key `(uid, Health Connect ID)`. Sync does not upload this cache. A selected record can subsequently be explicitly confirmed into the Firebase activity log, as described below. A complete validated response atomically replaces that account's rolling snapshot and sync metadata. A complete empty response removes the old rows. Edited/deleted source records are therefore reconciled without appending duplicates. An older concurrent snapshot cannot overwrite a newer successful window.

Read failures preserve the previous rows/window/success time and record an error status. Permission-required results clear the account's cache; unavailable/unchecked access hides it. Permission checks occur when opening/resuming the health/workout screens, not as a background service. If checking itself fails, cached workout contents are hidden until access can be checked. Revocation is not claimed to delete original Mi Fitness/Health Connect records.

**Remove imported workouts from this device** clears this account's workout cache and pending choice. Confirmed Firebase logs are separate and are not deleted. The general **Remove saved health data from this device** action clears health summaries, the sleep-recommendation cache and imported workouts. Other accounts' private caches are isolated. Uninstall/app-data removal clears the local database. Merely granting access or opening a screen does not read new health records; sync must be user initiated.

The transient workout payload returned during general health sync is removed before writing `HealthSummaryStore`, so raw session metadata is not duplicated inside the step/sleep/heart cache or sleep-recommendation context.

## Duplicate review and accounting

Sync displays imports separately and does not change counted totals. **Workout with wearable** can prepare an activity, match its exported session, and explicitly confirm duration and available active calories into `exerciseLogs` and `dailyData`. Confirmation does not create guided `workoutRecords` or award XP, quests, weekly guided-workout credit or CMAB learning. See [Workout confirmation](WEARABLE_WORKOUT_LINKING.md) for duplicate receipts, overlap guards, account storage consent and limitations. There is no Health Connect export from FitnessBalance to create a feedback loop.

The screen reads existing owner-scoped Firestore logs for comparison; sync alone does not upload imported session IDs or source records. Possible overlapping guided-session intervals are flagged. Imported records overlapping other imported IDs are also flagged. Manual logs lack exact activity intervals, so a counted manual record on a matching local date produces a weaker **same-day, not confirmed duplicate** warning. Overnight sessions check both local dates; an end exactly at midnight does not add the next day.

Comparison errors, missing timing data or read limits cannot justify automatic deduplication. Network/rules failures show that comparison is unavailable but do not block local import display. The comparison reads at most 500 guided records and fewer than 10 dated manual documents in the cached window; reaching those guards is reported as unavailable, not as no overlaps. Use **View counted activity logs for this date** to inspect existing entries. No automatic merge or replacement of previously completed guided sessions is performed. Explicit confirmation has the overlap and transaction safeguards documented in the confirmation guide. Keep an existing counted log unless you deliberately edit/remove it; the wearable row itself does not replace it.

Importing remains separate from explicit confirmation. Exercise-session presence alone does not establish calorie availability.

## Validation

The JVM suite tests valid metadata, ID updates/deduplication, source identity conflicts, empty success, window bounds, failed/permission reads, overflow limits, ordering, overlapping/adjacent intervals, overnight local dates and missing calorie/reward fields. The debug APK and Android test APK assemble, and lint runs.

Nine `WearableWorkoutStoreInstrumentedTest` tests are supplied for a device: account isolation and repeat imports; deleted/empty reconciliation; failed-read retention; edited records; permission-revocation clearing; invalid-read preservation; protection against older concurrent snapshots; pending-plan persistence; and calorie revocation. They use random test account namespaces and clean up only those namespaces. They were compiled, not executed in the cloud because no Android device/provider was attached.

Phone acceptance: import your Mi Fitness walk; compare times and elapsed duration; repeat sync and check the same ID does not duplicate; edit/delete a source session and resync; try an empty seven-day window; revoke read access; clear cache; switch accounts; test large font/dark mode; interrupt a read and verify previous success time remains; confirm sync alone leaves all totals unchanged, then test explicit confirmation according to the linked guide. Mi Fitness export and a real Health Connect provider must be tested on your phone.
