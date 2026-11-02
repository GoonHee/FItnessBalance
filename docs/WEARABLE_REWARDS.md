# Wearable exercise rewards

Confirmed wearable workouts now participate in the same daily quests, weekly workout target, total XP, level and general achievements as guided workouts. Merely syncing Health Connect still awards nothing. This is personal application gamification, not independent verification of physical activity.

## User flow

Publish the repository's updated `firestore.rules`, pull master, sync Gradle and rebuild. No seed update or additional Health Connect permissions are required for rewards.

Use Exercise → Workout with wearable → prepare an activity → track on the band → let Mi Fitness sync → sync in FitnessBalance → Record this workout. An eligible confirmation logs the workout and applies rewards in one transaction. A coloured result dialog reports committed XP, the event week's count/target and any new achievements. Home's level, weekly ring and daily quest rows use the same underlying profile/week/quest documents. Exercise progress history now mixes guided results and rewarded wearable sessions in event-time order.

For workouts confirmed before this update, open their dated **activity log → Check / apply workout rewards**. This reads the existing confirmed entry and receipt; it does not insert another activity or change calorie totals. It only applies to entries still present, at least five elapsed minutes long, and starting within the last seven actual days. An existing award returns an “already recorded” message and adds nothing. No historical bulk backfill runs automatically.

## Policy v1

| Rule | Behavior |
|---|---|
| Eligibility | Confirmed Health Connect activity, at least 300,000 elapsed milliseconds, at most 24 hours, source start in the preceding seven actual days, source end not in the future. Existing confirmation rules still defer overnight sessions. |
| Workout base XP | 20 XP for the first qualifying wearable session on a workout date; subsequent wearable sessions on that date get no base XP. |
| Weekly progress | One wearable workout credit per workout date, added to the shared guided-plus-wearable count for its event week. Additional distinct guided sessions retain their existing policy. |
| Daily quests | The existing `planned` (Complete a workout) and `minutes` (Complete five exercise minutes) quests, 5 XP each if not already completed by either source. Not cumulative short-session minutes. |
| Weekly bonus | Existing 30 XP bonus when the shared personalized weekly target is reached, once per week across both sources. |
| Level | Existing total-XP level formula, one level per 100 XP. |
| General achievements | First step, routine builder (two target weeks), welcome back (seven actual days since the prior recorded workout) and balanced week (existing strength/mobility/recovery mix), when their conditions are met. |
| Guided-only actions | Wearable sessions do not complete lesson paths, award lesson stars, imply difficulty/feeling feedback, complete the feedback quest or unlock path explorer/honest reflection. |
| Calories | Never used in reward calculation. Unavailable calories do not block rewards. Duration is explicitly elapsed, including pauses; active sensor time is not claimed. |

Examples: the first eligible wearable session normally earns **30 XP** (20 base + two unclaimed quests). If that session also reaches the weekly target, it earns **60 XP** including the weekly bonus. If guided exercise already completed both quests, it earns 20 base XP. Another wearable session on the same date generally earns 0 XP and no extra weekly count, while its duration/calories still log normally. It can complete a previously missing daily quest or contribute a legitimate activity category.

Credit uses the stored workout date/time zone and the Monday-based event week, not the date of synchronisation. Importing an older eligible session can update last week's progress; it does not fabricate a workout today or overwrite a more recent `lastWorkoutAt`. Late imports do not retroactively recompute every past comeback achievement.

Running/cycling/walking/swimming/etc. map to cardio; strength training/weight lifting to strength; yoga/Pilates to mobility; unknown activities to mixed. Walking is not automatically labelled recovery. A wearable import never invents a recovery or feedback signal to satisfy an achievement.

## Data and duplicate protection

- `users/{uid}/wearableWorkoutRewards/{stableEntryId}`: immutable versioned award, event times/date/week, activity/category, XP breakdown, target/count, total XP after the award and new unlocks. Stable ID is the existing hash of source package + Health Connect session ID.
- `users/{uid}/wearableRewardDays/{date}`: immutable date credit receipt, shared across devices, limiting wearable base XP and weekly credit to once on that date.
- Existing `questProgress`, `exerciseWeeks`, `unlockedAchievements` and protected user XP fields remain the shared sources of truth. A wearable award does not create a fictitious guided `workoutRecord`.
- Confirmation commits activity, daily calorie/time totals, import receipt and rewards atomically. The optional legacy claim changes rewards only. All transaction reads precede writes.
- Repeated requests, repeated sync, a second phone and parallel confirmations re-read the receipts. A losing immutable-rule race has one bounded retry with fresh transaction state. An uncertain confirmation checks its authoritative receipt before reporting failure or retrying; it does not blindly insert a second activity.
- Existing guided-overlap guards remain. If a guided completion overlaps an already counted wearable entry, its second workout XP/weekly/daily-exercise quest credit is suppressed; explicit guided feedback can still earn its normal feedback credit and lesson-specific progress remains separate. The daily calorie/time suppression/restoration policy remains unchanged.
- Deleting an activity removes it from calorie/duration totals but does not revoke earned XP, quests, achievements or weekly credit. Immutable receipts remain, preventing delete/re-import farming. The delete confirmation explains this policy. No clawback/recalculation of earned rewards is implemented.

Rules permit owner reads and valid immutable award/date-receipt creation, require bounded XP derived from the existing day/week/quest state, enforce a matching atomic profile/week update and restrict the achievement types/conditions. Ordinary profile edits cannot award XP. Existing guided reward rules remain supported. Health Connect provenance is supplied by the client import; rules do not cryptographically attest the band or certify calories. This policy is suitable for personal progress, not a competitive anti-cheat ledger.

## Validation and phone checks

The JVM policy tests cover thresholds, short/stale/future/unconfirmed sessions, calorie independence, per-date caps, shared quest/weekly bonuses, historical date/time-zone behavior, existing achievements, comeback/category rules, date mismatch, XP limits and guided overlap detection.

Firestore emulator tests cover first award, replay, distinct same-day sessions, simultaneous writers, weekly bonus, shared guided quest receipts, short-session denial, inflated XP/missing atomic writes, unauthorized profile changes, protected feedback/path achievements, unearned general achievements, legitimate general achievements, owner privacy/immutable ledgers, and claiming an existing confirmed workout without changing calories. The full existing rules suite is also run.

Debug app build, unit tests, lint and Android test APK compilation run in the cloud. No physical band/Health Connect/UI instrumentation acceptance is claimed. On your phone verify:

1. First 5+ minute confirmation updates the weekly ring, quests, total XP/level and the reward popup, even if calories are unavailable.
2. Repeat claim and repeat sync add nothing; two different workouts on the same date share the wearable credit cap.
3. Reach your weekly target using guided and wearable sessions together; the 30 XP bonus appears once.
4. Claim an eligible older confirmed activity; calories/duration stay unchanged, and credit belongs to the activity date/week.
5. Delete a rewarded activity and check the stated non-clawback policy; it cannot be re-imported for another award.
6. Try missing rules/offline confirmation: no partial activity/reward save or success popup. Test switching accounts/backgrounding during a claim.
