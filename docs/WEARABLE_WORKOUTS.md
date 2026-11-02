# Wearable workouts through Health Connect

## Phone setup

The supported path is band → Mi Fitness (or another companion app) → Health Connect → FitnessBalance. This module reads Health Connect; it does not pair with Bluetooth hardware or require a Xiaomi SDK.

1. Pull master, sync Gradle and run on your compatible phone. No Firestore rules or seed changes are needed.
2. Confirm Mi Fitness has exported a recent exercise session into Health Connect. A session visible only in Mi Fitness cannot be imported by this module.
3. In FitnessBalance open **Exercise → Wearable workouts**, or **Profile → Connect health data → Wearable workouts**.
4. Tap **Connect / request read permissions** and allow exercise-session reading. Existing steps/sleep/heart permissions do not automatically include the new permission.
5. Tap **Sync wearable workouts**. The Connect health data screen's **Sync health data now** also imports workouts alongside its existing summaries.

Only `android.permission.health.READ_EXERCISE` is added. The dedicated workout screen requests just that permission; the general health screen offers all supported read types. No calorie, route, background, extended-history or Health Connect write permission is requested.

## Display and scope

A rolling snapshot contains ended sessions whose start is within the previous seven actual 24-hour periods, as of the successful sync. Sessions starting before that boundary and sessions ending after the sync cutoff are omitted. The source app may update records later; this is not independent verification that an exercise was physically completed. A stale snapshot remains visibly cached until another successful read; it is not described as a current live feed.

The screen shows activity type, optional source title, start/end, elapsed duration, source app/package, source modification time, last successful workout sync and its window. Times use the phone's current time zone. Known activity codes have readable names; unknown future codes retain their numeric type. Elapsed duration includes any pauses inside the interval and is not active exercise time. Source app labels may be unavailable under Android package visibility rules, in which case the exact package is shown.

Calories, active time, distance, route/location, exercise segments/laps and raw heart-rate samples are not imported. Missing calories are labelled **not imported**, never zero.

## Reconciliation and local storage

`HealthConnectBridge` reads `ExerciseSessionRecord` through the official SDK, with pagination. Reads are limited to 20 pages of 200 records and 1000 relevant rows. An exceeded limit, failed page or invalid payload is a failed read, never a truncated successful import. The SDK's permission list is checked again before returning data.

`WearableWorkoutService` validates IDs, package, type, timestamps and the exact rolling window. Repeated IDs are deduplicated by latest modification time; distinct IDs remain separate even if their times match. This avoids guessing that two overlapping source records must be duplicates.

`WearableWorkoutStore` is a SQLite cache in Android's private no-backup directory, with primary key `(uid, Health Connect ID)`. It is never uploaded to Firestore, analytics or another server. A complete validated response atomically replaces that account's rolling snapshot and sync metadata. A complete empty response removes the old rows. Edited/deleted source records are therefore reconciled without appending duplicates. An older concurrent snapshot cannot overwrite a newer successful window.

Read failures preserve the previous rows/window/success time and record an error status. Permission-required results clear the account's cache; unavailable/unchecked access hides it. Permission checks occur when opening/resuming the health/workout screens, not as a background service. If checking itself fails, cached workout contents are hidden until access can be checked. Revocation is not claimed to delete original Mi Fitness/Health Connect records.

**Remove imported workouts from this device** clears this account's workout cache. The general **Remove saved health data from this device** action clears health summaries, the sleep-recommendation cache and imported workouts. Other accounts' private caches are isolated. Uninstall/app-data removal clears the local database. Merely granting access or opening a screen does not read new health records; sync must be user initiated.

The transient workout payload returned during general health sync is removed before writing `HealthSummaryStore`, so raw session metadata is not duplicated inside the step/sleep/heart cache or sleep-recommendation context.

## Duplicate review and accounting

Imports are view-only evidence in a separate **Wearable workouts** screen. They do not enter `exerciseLogs`, `workoutRecords`, `dailyData`, XP, quests, weekly workout counts, progress calorie totals or CMAB learning. There is no Health Connect export from FitnessBalance to create a feedback loop.

The screen reads existing owner-scoped Firestore logs for comparison; it does not upload imported session IDs or source records. Possible overlapping guided-session intervals are flagged. Imported records overlapping other imported IDs are also flagged. Manual logs lack exact activity intervals, so a counted manual record on a matching local date produces a weaker **same-day, not confirmed duplicate** warning. Overnight sessions check both local dates; an end exactly at midnight does not add the next day.

Comparison errors, missing timing data or read limits cannot justify automatic deduplication. Network/rules failures show that comparison is unavailable but do not block local import display. The comparison reads at most 500 guided records and fewer than 10 dated manual documents in the cached window; reaching those guards is reported as unavailable, not as no overlaps. Use **View counted activity logs for this date** to inspect existing entries. No automatic merge, exclusion or calorie replacement is performed. Keep an existing counted log unless you deliberately edit/remove it; the wearable row itself does not replace it.

This first version intentionally separates importing from counting. Future wearable calorie/reward integration needs explicit source/overlap policies and supported calorie records; exercise-session presence does not establish calorie availability.

## Validation

The JVM suite tests valid metadata, ID updates/deduplication, source identity conflicts, empty success, window bounds, failed/permission reads, overflow limits, ordering, overlapping/adjacent intervals, overnight local dates and missing calorie/reward fields. The debug APK and Android test APK assemble, and lint runs.

Seven `WearableWorkoutStoreInstrumentedTest` tests are supplied for a device: account isolation and repeat imports; deleted/empty reconciliation; failed-read retention; edited records; permission-revocation clearing; invalid-read preservation; and protection against older concurrent snapshots. They use random test account namespaces and clean up only those namespaces. They were compiled, not executed in the cloud because no Android device/provider was attached.

Phone acceptance: import your Mi Fitness walk; compare times and elapsed duration; repeat sync and check the same ID does not duplicate; edit/delete a source session and resync; try an empty seven-day window; revoke read access; clear cache; switch accounts; test large font/dark mode; interrupt a read and verify previous success time remains; confirm all calorie/XP/weekly totals are unchanged. Mi Fitness export and a real Health Connect provider must be tested on your phone.
