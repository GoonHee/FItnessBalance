# Exercise module — current master integration

**Activity Log update:** Manual and guided exercise logging is now integrated. See [ACTIVITY_LOG.md](ACTIVITY_LOG.md) for the new daily calorie accounting, history, compatibility and QA notes.

This implementation starts from `master` commit `a7ca20f08e3eadcda9c5060aeb12dee5c8a99f60`. It does not merge or depend on the earlier exercise branch. The updated food log, serving calculations, barcode scanner, product creation and saved barcode-food code remain unchanged.

## Open and run in Android Studio

1. Download the ZIP for the new `gamified-exercise-current` branch, extract it, and open the directory containing `settings.gradle.kts`. Alternatively, fetch and switch to that branch in your existing clone after committing or stashing local edits.
2. Let Gradle sync finish. Use JDK 21 and Android SDK 36. The existing Google code scanner dependency remains installed.
3. Keep your Firebase development project's `app/google-services.json`. Authentication and Firestore must be enabled for that project. Do not replace it with an unrelated example configuration.
4. Import the exercise catalogue below. Without it, Exercise Home displays an empty-content message; Quick Exercise Log and the other navigation buttons stay accessible.
5. Review the supplied Firestore rules against your actual deployed rules, then deploy them explicitly to the correct development project. New exercise collections need permission rules; the file also preserves the current food and barcode paths.
6. Run the app on a phone or emulator. Sign in with a verified account, open the dashboard Exercise card, and follow the manual QA checklist below.

**No remote seed import, Firebase rules deployment or main-branch merge was performed by this implementation.** The debug APK uses your existing Firebase client configuration. The release APK is unsigned.

## Audit and integration scope

The new working tree was clean before editing. Reviewed Gradle/settings/catalog, manifest, authentication/application classes, MainActivity, ExerciseActivity, ProfileActivity, food repositories, saved barcode paths and related XML/styles. The baseline debug build and existing test passed after resolving the scanner dependency added on current master.

Reusable components: Java activities, XML Views, Material 3 theme, existing Firebase Authentication, dashboard card, profile fields, Quick Log and the new food screens' theme-aware panel style.

Existing edits are deliberately limited:

- Dashboard Exercise card opens `ExerciseHomeActivity`.
- Profile writes merge, preserving email, XP and fields maintained by other modules.
- Existing ExerciseActivity is now Activity Log, combining manual entries and guided records. Existing `exerciseLogs` arrays remain the manual source; `dailyData` contains the combined exercise estimate.
- App Gradle explicitly declares RecyclerView 1.2.1, already used transitively by the available dependencies. The barcode scanner dependency is retained.
- Eight private activities are added to the existing manifest without replacing the food activities or scanner metadata.
- New module strings/layouts/styles are separate resources. Draft preferences are excluded from backup and device transfer.

New files cover `model`, `repository`, `service`, `ui.exercise`, `adapter`, `util`, eight activity layouts, a reusable row, seed tooling, rules, tests and this guide. There is no Kotlin/Compose migration or unrelated food rewrite.

### Milestone validation

| Milestone | Changes | Validation |
| --- | --- | --- |
| 1 | Firestore models, constants, pure rewards/Momentum/recommendation/session-state services, catalogue seed | Debug build and service tests passed |
| 2 | Details, active workout, timed/repetition targets, rest, pause/resume/skip/end, durable plan snapshot, feedback, result and atomic save | Debug build and tests passed |
| 3 | RecyclerView path, prerequisite locking, repeats and retained best stars | Debug build and tests passed |
| 4 | Hub, progress history, daily quests, six achievements, weekly target/bonus | Debug build and tests passed |
| 5 | Profile/feedback/recovery-aware recommendations and explanations | Debug build and tests passed |
| 6 | Rules, barcode/food regression access, duplicate-concurrency recovery, error states, accessibility/resource review and docs | Debug/release builds, JVM tests, lint and local Firestore tests passed |

Final automated results: **70 JVM tests passed, 32 local Firestore tests passed, debug and unsigned release builds passed, and lint passed with 0 errors.** Nonblocking lint warnings include existing app code and layout background overdraw.

## Catalogue import — development only

`tools/exercise-seed.json` contains 63 deterministic documents:

- Eight reusable exercises.
- Beginner Foundation Path and seven sequential lessons: Basic Movement; Lower Body Basics; Upper Body Basics; Five-Minute Workout; Strength and Mobility; Ten-Minute Full Body; Foundation Challenge.
- Separate Gentle Recovery and Comeback Movement lessons, excluded from sequential path completion.
- Four ordered exercise items for each of the nine lessons, three quest definitions and six achievement definitions.

Lesson 1 uses March in Place (30 seconds, 20 seconds rest), Chair Squat (8 repetitions, 30 seconds rest), Wall Push-Up (8 repetitions, 30 seconds rest) and Standing Stretch (30 seconds, no rest). Recovery/comeback alternatives require no equipment. Times displayed in the catalogue are approximate, self-paced session estimates.

The Android app cannot write shared content and never seeds on startup. Import is create-only: reruns preserve edited existing records and cannot duplicate document IDs. Interrupted imports can safely be resumed. Catalogue revisions need explicit administrator updates, not an overwrite import. Exercise instructions are MVP content and should be reviewed before public distribution.

### Windows / PowerShell

Run from the project root:

```powershell
py -m venv work\seed-venv
work\seed-venv\Scripts\python.exe -m pip install -r tools\requirements-seed.txt
# Dry run; does not need Firebase credentials:
work\seed-venv\Scripts\python.exe tools\seed_exercise.py --project YOUR_DEVELOPMENT_PROJECT
```

For a remote development import, configure Firebase Admin Application Default Credentials outside the repository. If you use a downloaded service-account JSON, keep it outside the project and set the path only in your local shell:

```powershell
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\private\development-service-account.json"
work\seed-venv\Scripts\python.exe tools\seed_exercise.py --project YOUR_DEVELOPMENT_PROJECT --apply --allow-remote-development-project
```

Never commit that key or put it in the Android app. The project's `.gitignore` excludes the local `work/` directory, but credentials should still live outside the project.

### macOS / Linux or local Firestore emulator

```sh
python3 -m venv work/seed-venv
work/seed-venv/bin/pip install -r tools/requirements-seed.txt
work/seed-venv/bin/python tools/seed_exercise.py --project YOUR_DEVELOPMENT_PROJECT
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 work/seed-venv/bin/python tools/seed_exercise.py --project demo-fitnessbalance --apply
# Remote DEVELOPMENT project, using separately configured Admin credentials:
work/seed-venv/bin/python tools/seed_exercise.py --project YOUR_DEVELOPMENT_PROJECT --apply --allow-remote-development-project
```

## Firestore rules and tests

The repository did not contain an existing rules file. Review `firestore.rules` against the rules currently deployed in Firebase; merge any unrelated app collections before deployment. Unknown paths are denied by default.

The supplied rules preserve:

- Authenticated read-only shared `foods` and exercise/path/quest/achievement catalogues.
- Owner-only `users/{uid}`, `foodLogs`, `exerciseLogs`, `dailyData` and **`barcodeFoods`**, including the current food serving/nutrient writes.
- Immutable server-timestamped session starts and workout records.
- Prerequisite checks when a session is started, private progress/week documents, create-only daily quest and achievement unlocks.
- Bounded record counts/XP/stars, matching session times and IDs, atomic linkage to the user's XP update, and rejection of standalone XP changes/password fields.

Development test dependencies do not become Android dependencies. Node 20/22 and Java 21 are recommended:

```sh
npm --prefix tools/security-tests install
# From the project root; local demo project only:
tools/security-tests/node_modules/.bin/firebase emulators:exec --project demo-fitnessbalance --only firestore 'node --test tools/security-tests/rules.test.cjs'
```

On Windows, use `tools\security-tests\node_modules\.bin\firebase.cmd` for the executable. There are 32 tests for shared and private access, food/nutrient and barcode regression paths, first-lesson/locked/recovery starts, immutable sessions/records, atomic progress, replay/concurrent submissions, repeat reductions, one-time daily/weekly bonuses, missing profiles and maximum achievement-write access limits. The JavaScript harness exercises the Firestore contract; it does not execute Android activities or the Android repository binary.

When two clients submit the same session, Firestore's immutable-record rules can reject the losing write before an SDK retry. The repository checks the authoritative saved record after an uncertain failure and returns that record if present. Otherwise it reports failure and retains the draft for a same-ID retry. It never creates a second reward record.

Rules are not deployed automatically. After review, explicitly deploy only to your selected project:

```sh
tools/security-tests/node_modules/.bin/firebase deploy --only firestore:rules --project YOUR_DEVELOPMENT_PROJECT
```

This is a personal, client-authoritative habit tracker. Rules isolate accounts and bound writes; they cannot prove physical exercise or eliminate every modified-client self-reward attack. A trusted economy would move calculation/aggregate validation to an authenticated backend. There is no leaderboard or social comparison.

## Data and reward behavior

Required shared collections use the specified paths. User records use:

```text
users/{uid}/workoutSessions/{sessionUuid}  # server-acknowledged start
users/{uid}/workoutRecords/{sessionUuid}  # immutable, same UUID for idempotency
users/{uid}/pathProgress/{pathId}
users/{uid}/exerciseWeeks/{localMondayDate}
users/{uid}/questProgress/{localDate}_{questId}
users/{uid}/unlockedAchievements/{achievementId}
```

An atomic transaction creates the record and updates user XP/level, path attempts/bests, current week, eligible quests and achievements. All reads happen before writes. The result/history screens only read data. Reopening a result cannot award XP.

Base XP comes from the catalogue: five-minute 10, ten-minute 20, fifteen-minute service support 30, challenge 35, comeback 15. Completion multipliers are 1.0 at 80%+, 0.6 at 50%+, otherwise 0.25. First rewarded attempt receives full workout XP, first repeat 50%, later attempts 25%. Attempts below 50% still receive the specified low XP/one star but do not unlock prerequisites or count toward Momentum. Counting all rewarded attempts for repeat reduction prevents repeated partial attempts from farming full XP. `firstCompletion` records whether this was the first rewarded attempt.

Feedback adds 2 XP once per record. Daily planned-workout and five-active-minute quests each add 5 XP once per local date. The feedback quest adds no second daily bonus on top of the per-record 2 XP. Five-minute quest time excludes rest and pause. Duration includes rest, excludes pause/background time. Stars: below 50% one; 50%+ two; 80%+ with Suitable three. Best completion/stars never decrease.

Weekly target defaults to 3 and is bounded to 1–7. At least 50% completion qualifies. The primary measure is `min(count / target × 100, 100)` with the requested four Momentum bands. Weekly bonus is 30 XP once per week. Week documents avoid storing every week in the main user document; `metGoalWeeks` tracks distinct achieved weeks. No daily streak or missed-day XP penalty is used. Recovery days do not remove earned progress.

If prior `weeklyCounts`/`metGoalWeeks` fields exist, their current-week count/awarded status are used as a fallback when the week document does not exist. Existing XP, records, quest and achievement documents are preserved rather than reset. There is no automatic migration of other modules or deletion of old data.

Six achievements: First Step (qualifying workout); Routine Builder (target met in two distinct weeks); Path Explorer (all seven path lessons at least 50%); Welcome Back (qualifying return after seven inactive days); Honest Reflection (five feedback records); Balanced Week (strength, mobility and recovery lesson categories in one week). Each is stored once, with no extra achievement XP.

## Recommendations, preferences and recovery

Recommendations use rules, not machine learning. Safety overrides difficulty progression: recent discomfort first, planned recovery day next, seven-day inactivity/comeback next, then excessive difficulty, recent strength (48 hours), two easy ratings, duration and category/equipment/fitness preferences. With insufficient data, choose the next unlocked beginner lesson. Discomfort looks back seven local calendar dates and never results in an automatic difficulty increase.

Existing `fitnessLevel`, `preferredExercises`, and profile weight are reused. Optional user fields:

- `weeklyWorkoutTarget`: integer 1–7, default 3.
- `preferredWorkoutMinutes`: numeric minutes, default 10.
- `preferredWorkoutDays`: ISO weekday numbers, Monday=1 through Sunday=7. Unscheduled days are planned recovery days.
- `preferredExerciseCategories`, `equipment`: string lists; `fitnessGoal`: string.

There is no new preferences editor in this MVP; optional fields can be configured on the user's document. An absent equipment list means unknown availability; details show the required equipment. An explicit `["none"]` filters out chair workouts. All seeded path lessons are beginner difficulty. Two easy ratings suggest later available beginner content, not an unseeded advanced workout. Recovery/comeback sessions are standalone and cannot unlock sequential lessons.

Device local timezone consistently sets day/week boundaries, stored on the record. Weeks start Monday. Changing device timezone can move date buckets; a fixed home-timezone policy can be added later.

Calories are estimates from active time, MET and profile weight; absent/invalid weight uses 70 kg. They never influence XP. Structured estimates appear in results and Activity Log. `dailyData.caloriesBurned` combines completed manual entries with guided records; food fields are preserved. Manual activities do not unlock path lessons or award exercise-module XP.

## Lifecycle, errors and limits

The pure `SessionState` handles exercise, rest, feedback, skip and pause transitions. Timers use monotonic deltas. Background/recreation automatically pauses; resumption requires an explicit Resume. A per-UID local draft stores the exercise plan/instructions, timers, counts and feedback choices. This preserves the session when the screen/process restarts and avoids reloading a different exercise list mid-session. Draft writes happen on transitions and approximately each second while active. They are excluded from backup/transfer.

Home offers Resume and Discard Unfinished Workout. Discard affects the local draft only, not saved records/rewards. Abandoned server starts can be cleaned up administratively in a future retention job. A draft saved before an uncertain start is retried using the same UUID. Content removal/deactivation before final submission can still prevent saving; the error is retryable and the user may return Home and discard/restart.

Content reads may use Firestore's cache, but session creation/resumption checks and reward transactions require network. There is no offline reward queue. Network, missing content/reference, signed-out, missing profile, denied permission and transaction errors show guidance/retry. Diagnostics do not log tokens, passwords or health values. Quick Log navigation remains available on the hub even if shared content is unavailable.

History displays the latest 30 records; pagination is future work. Quests/achievement rules are deterministic and can later be versioned or moved server-side. Very long-lived per-lesson maps/goal-week lists may eventually need archival. Conflicting submissions of *different* sessions can still require a same-ID user retry; no partial rewards are committed.

## Build and verification

```sh
bash gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

In the provided cloud environment, JDK and SDK paths are `/workspace/jdk-21` and `/workspace/android-sdk`, and Gradle cache is `/workspace/.gradle`. The initial offline baseline lacked your new scanner dependency; fetching that dependency allowed the unchanged baseline to pass. Subsequent checks ran offline.

The manifest's existing `firebase_auth_emulator_host` metadata does not itself route the Firebase SDK to emulators. For Android emulator development, configure both Auth and Firestore `useEmulator` calls in debug-only initialization before first SDK use, using `10.0.2.2:9099` and `10.0.2.2:8080` and a dedicated development configuration. No emulator routing or live credentials were changed automatically.

### Manual QA — pending a device

No Android device/emulator was attached during implementation. Automated checks do not establish that these interactive steps were run:

- [ ] Registration, email verification, login, password reset, profile edits and logout work.
- [ ] Profile save preserves XP, email and unrelated fields.
- [ ] Current food search, serving/nutrient calculations, barcode scan, custom product creation, saved products and food history still work.
- [ ] Quick Log reads/writes existing exercise logs and dashboard calories; no path unlock.
- [ ] New user opens Exercise Home; Lesson 1 ready, Lesson 2 locked and untappable.
- [ ] Details displays four ordered exercises, targets, rest and equipment.
- [ ] Timed targets count down, reps display correctly, rest advances automatically; pause/skip/end work.
- [ ] Background, rotation, process restart and return from feedback preserve draft state and feedback choices.
- [ ] Discomfort shows safety guidance; next recommendation avoids difficulty increase.
- [ ] Lesson 1 at least 50% saves once, awards expected XP/stars, updates level/Momentum and unlocks Lesson 2.
- [ ] Reopening results or retrying after network uncertainty does not award again.
- [ ] Repeats award reduced workout XP and retain the best stars.
- [ ] Quests reset by local date, active-minute quest excludes rest, weekly bonus occurs once.
- [ ] Recovery days preserve earned XP/achievements.
- [ ] App restart retains remote progress; two accounts remain isolated.
- [ ] Missing profile/content/reference, offline and denied-permission cases show safe errors and retry.
- [ ] Large text, TalkBack, light/dark mode, portrait/landscape and system insets are usable.
