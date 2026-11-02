Current gamification behavior and additional Firestore rule requirements are documented in [Wearable rewards](WEARABLE_REWARDS.md).

# Work out with a wearable

FitnessBalance can prepare a workout, match a completed Health Connect exercise session, and explicitly save its elapsed duration and available active calories into the existing activity log. Mi Fitness still starts/stops the workout on the Xiaomi band and exports its results. Health Connect is a synchronisation interface, not live band control.

## Install and use

1. Pull master, sync Gradle, and rebuild on your physical Android phone.
2. Publish the repository's updated `firestore.rules` in Firebase Console → Firestore → Rules. The new owner-only `users/{uid}/wearableWorkoutLinks/{id}` receipt collection is needed for confirmation; existing rules without it will reject the transaction. No service account or seed is required.
3. Exercise → **Workout with wearable** → Connect. Grant exercise sessions and optional active calories. Existing installations must grant the newly added active-calorie permission; granting exercise alone still works for duration.
4. Select the matching activity and tap **Prepare this workout**, then start that activity on the band. Supported selections include walking, running, treadmill running, cycling, stationary cycling, swimming, strength training, yoga, Pilates, hiking, HIIT and other workout.
5. Finish on the band and let Mi Fitness export to Health Connect. Return and tap **Sync wearable workouts**. This is user initiated; returning to the screen only checks permissions and reads its local cache.
6. A record with the same Health Connect activity type, a start from 10 minutes before to 30 minutes after preparation, and an end after preparation gets **Record this workout**. Check the source app and times, then confirm. The successful snapshot must be less than five minutes old. Different source apps can export the same activity; choose the actual band source.
7. View the result in Exercise → activity log for the session's start date. Home and progress calorie totals use that log's existing daily accounting. No separate calorie addition is made by sync itself.

The pending choice survives process restarts on this device, is isolated by account, and can be cancelled. It is not an active sensor session. The duration comes from Health Connect start/end, includes pauses, and is labelled elapsed, not measured active time.

## Calories and provenance

Only `ActiveCaloriesBurnedRecord` is read. Total energy, basal/resting energy, steps-derived calories and daily totals are not substituted. All contributing intervals must belong to the session's source package, fit entirely inside its start/end, and form exact contiguous coverage with no gaps or overlaps. Duplicate intervals, partial coverage, broad daily records, read limits/errors and overlapping same-source exercise sessions leave calories unavailable. This intentionally favours missing values over assigning another activity's energy. The SDK may not export Mi Fitness's displayed calories in this form; real-device verification is required.

A real recorded zero is accepted. Missing permission/data is saved as `calorieSource: unavailable`, excluded from the calorie sum, and displayed explicitly as unavailable, with a partial-total warning in the activity log. Duration still records. No MET fallback is silently used. Available values have `calorieSource: wearable_active`; these remain device/app estimates, not laboratory measurements.

Confirmed activity entries retain source package, Health Connect ID, original times, selected workout preparation time, duration source, calorie source, local date and time zone. They are stored in the existing `exerciseLogs/{date}.exercises` array and included in daily totals through `ExerciseLogRepository`. Storage uses the legacy manual-contribution bucket so all existing dashboards continue to work; the UI identifies them as wearable workouts and disables the manual editor for them. They can be deleted normally.

## Duplicate and race handling

A SHA-256 key of source package plus Health Connect record ID is used as the activity ID and immutable confirmation receipt. The activity entry, daily totals and receipt commit in one Firestore transaction. Retry, another device, or changed source timestamps cannot count that ID again. A deleted confirmed entry remains deleted; its receipt intentionally prevents re-import. Corrections to the source do not automatically modify confirmed entries.

The app blocks overlapping previously counted guided sessions and overlapping confirmed wearable entries. A same-day manual activity has no reliable exact time, so the confirmation asks the user to review it; automatic manual matching is not claimed. A guided completion updating the daily document during confirmation forces a transaction retry. If an import wins a race with a subsequent guided completion, the second daily calorie/time/count contribution is suppressed while that wearable entry exists; deleting it restores the original guided contribution. Guided records stay intact; a later overlapping guided completion does not award a second workout XP/weekly credit. Explicit feedback and lesson-specific progress remain separate. The daily marker conservatively blocks confirmation when a later guided completion occurred on that date, even if a new comparison might establish a non-overlap.

Confirmation currently supports sessions contained in one local day, up to 24 hours. Overnight sessions remain visible in wearable history but are not confirmed into totals yet, avoiding cross-date duplicate accounting. This release does not replace an already completed guided workout's estimates with wearable values. Use the dedicated wearable workout mode for running/cycling/etc.; guided lesson completion stays separate. Eligible wearable sessions now share daily quests, weekly counts, XP and general achievements under [Wearable rewards](WEARABLE_REWARDS.md).

## Privacy, permissions and deployment

Sync alone uses the private no-backup SQLite cache. Only explicit confirmation uploads the selected session metadata and chosen values to the signed-in Firebase account. No raw energy interval list, routes, heart-rate samples, sleep records or step records is uploaded by this feature. Removing local caches or revoking access does not remove explicitly confirmed account logs. The confirmation UI and Health Connect rationale describe this difference. Recheck of permissions before saving removes revoked energy values; revoked exercise access stops confirmation. Nothing writes back to Health Connect.

The database migrates version 1 to version 2 by adding optional calorie columns and an account-specific pending-workout table. Sync failure preserves old session records and the previous successful timestamp; it cannot enable confirmation without a recent successful snapshot.

## Validation

Cloud checks: debug app build, JVM suite, lint, Android test APK compilation, and Firestore emulator security tests. Pure policy tests cover exact/partial/duplicate/cross-source energy intervals, unknown versus zero, timing/type matching, stable identities, provenance, future and overnight rejection, and duplicate-contribution restoration. Device cache tests additionally cover pending-plan persistence/account isolation and energy revocation; these are compiled, not executed in the cloud.

Physical-device acceptance remains necessary:

- Check running/cycling and the activity type that Mi Fitness actually exports; compare displayed duration and calories against Health Connect records.
- Exercise-only permission, unavailable energy, active-calorie revocation, and genuine zero energy.
- Prepare → restart → finish on band → sync → confirm → open activity log/home/progress.
- Repeated confirmation/sync and a second device must not change totals twice.
- Review a manual duplicate, reject a guided overlap, delete a confirmed workout and verify totals.
- Test missing rules/network failure: no success message, partial write or reward must occur.
- Clear local data/revoke access and verify cloud logs remain available as explicitly saved entries.
