# FitnessBalance exercise module

## Audit and integration

The repository was clean before editing. Reviewed settings/project/app Gradle, the manifest, MainActivity, ExerciseActivity, ProfileActivity, authentication/application classes, food/calorie Firestore usage, and their XML screens. Baseline `assembleDebug` and `testDebugUnitTest` succeeded. The existing application is Java + XML Views, Material 3, Firebase Auth and Firestore, minSdk 23/targetSdk 36.

Reusable components: existing theme/colors, authenticated `users/{uid}` profiles, dashboard cards, Material buttons and existing manual exercise log. Existing data paths remain `users/{uid}/foodLogs/{date}`, `exerciseLogs/{date}`, `dailyData/{date}` and shared `foods`. No migrations or unrelated moves.

Existing files changed:

- `MainActivity.java`: exercise card opens Exercise Home.
- `ProfileActivity.java`: merge profile edits instead of replacing and erasing progress/email fields.
- `ExerciseActivity.java` / its XML: Quick Exercise Log label, optional custom activity, numeric reads accepting `Number`. Existing log/calorie writes retain their shapes.
- App Gradle: explicitly declare already available RecyclerView 1.2.1. No Kotlin/Compose migration or new Firebase dependency.
- Manifest: register eight private activities. Resource strings, module colors/dimensions and draft backup exclusions.

New files live in `model`, `repository`, `service`, `ui/exercise`, `adapter`, `util`, eight activity XML layouts, a list-row layout, module resources, service tests, `tools`, this guide, rules and Firebase emulator configuration. `work/` is ignored development scratch space.

## Milestones and validation

| Milestone | Delivered | Check performed |
| --- | --- | --- |
| 1 Foundation | Eight Firestore models with constructors/accessors, utilities, pure services, deterministic external seed | Debug build and service tests passed |
| 2 Workout flow | Home, details, active exercises/rest/reps, pause/skip/end, durable draft, feedback, read-only result, atomic record/rewards | Debug build and unit tests passed |
| 3 Progression | RecyclerView path, locked/completed/repeat states, prerequisite threshold, retained best stars, reduced repeat rewards | Debug build and unit tests passed |
| 4 Gamification | Home/progress XP and Momentum, three daily quests, six achievements, weekly bonus | Debug build and unit tests passed |
| 5 Adaptation | Rule-based explanations, preferences, previous difficulty, recent strength, comeback, discomfort and recovery | Debug build and unit tests passed |
| 6 Hardening | Rules, local emulator security checks, interruption recovery/discard, offline/error/retry states, API 23 compatibility, accessibility/resources, documentation | Debug/release builds, unit tests, lint and rules tests |

No device/emulator was attached for Android UI testing. Automated build/service/rules checks do not establish that all manual acceptance steps have been run. See the checklist below before release. Existing app lint warnings remain; the module's durable-draft synchronous saves and layout background overdraw also produce nonfatal warnings. Lint has no errors.

## Seed once in a development project

63 deterministic content documents: eight reusable exercises, one path, seven sequential lessons, two standalone recovery/comeback lessons, four ordered exercises per lesson, three quest templates and six achievement templates. Lesson 1 is Basic Movement and Lesson 2 is Lower Body Basics. The initial lesson uses March in Place, Chair Squat, Wall Push-Up and Standing Stretch with the requested targets/rests. Recovery and comeback alternatives require no equipment. Exercise instructions are MVP text content and need exercise-professional/content review before public distribution.

The Android client never writes shared content or runs the seed.

```sh
python3 -m venv work/seed-venv
work/seed-venv/bin/pip install -r tools/requirements-seed.txt
# Read-only dry run; no credentials required:
work/seed-venv/bin/python tools/seed_exercise.py --project YOUR_DEVELOPMENT_PROJECT
# Local Firestore emulator:
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 work/seed-venv/bin/python tools/seed_exercise.py --project demo-fitnessbalance --apply
# Remote DEVELOPMENT project, with Application Default Credentials configured outside this repo:
work/seed-venv/bin/python tools/seed_exercise.py --project YOUR_DEVELOPMENT_PROJECT --apply --allow-remote-development-project
```

Import uses create-only operations and skips existing IDs. Rerunning cannot overwrite edited content or duplicate IDs. An interrupted import can safely be resumed. To revise existing content, review and apply explicit document updates through an administrator rather than rerunning expecting overwrites. Never commit service-account keys. The script was dry-run validated; no live-project import was performed.

## Rules and emulator tests

Rules allow authenticated reads of exercise/path/quest/achievement content and foods, and deny ordinary shared writes. Users are isolated by Auth UID. Sessions have server creation timestamps and are immutable. Workout records, daily quest completions and achievement unlocks are create-only. Validated records are linked to the session and atomic user award. Independent total-XP edits and raw profile replacements that remove reward fields are rejected. Existing owner-scoped food/exercise/daily writes remain permitted.

```sh
npm --prefix tools/security-tests ci
# Launches only a LOCAL demo emulator and runs 12 tests:
tools/security-tests/node_modules/.bin/firebase emulators:exec --project demo-fitnessbalance --only firestore 'node --test tools/security-tests/rules.test.cjs'
```

Requires Node 20/22 and Java 21 on PATH. These are development tools, not Android app dependencies. Tests cover shared-content access, anonymous/cross-user denial, legacy data access, profile merges, password rejection, bounded immutable sessions, atomic reward writes, replay rejection, immutable records and first completion when the profile is absent. The test batch exercises rule compatibility; it does not execute the Android repository or UI.

Review the supplied rules against any other deployed collections before deployment. No prior rules file was in this repository. Unknown collections are denied by default. Deployment was not performed.

```sh
# Only after choosing the correct development project and reviewing changes:
tools/security-tests/node_modules/.bin/firebase deploy --only firestore:rules --project YOUR_DEVELOPMENT_PROJECT
```

These rules bound and isolate a client-authoritative personal habit tracker. They cannot prove that a person physically performed an exercise or prevent every form of self-reward fabrication by a modified client. For a trusted reward economy, move reward computation and aggregate validation to an authenticated backend callable endpoint. There is no leaderboard or competitive economy here.

## Runtime behavior and choices

- All new screens require Auth. Stable path/lesson/session IDs pass between activities.
- A server-acknowledged `workoutSessions/{uuid}` starts each session. One immutable `workoutRecords/{same uuid}` is the transaction's idempotency guard. Repeating a lesson intentionally starts a new UUID.
- One completion transaction reads existing state, checks the prerequisite again, then writes record, user XP/level, path bests/counts, daily quests, achievements and week state. A retry after an uncertain network response returns the existing record without awarding again. Results only read.
- Below-50% attempts receive the specified reduced XP and one star, but do not unlock a prerequisite or count toward Momentum. Repeat reduction counts every rewarded attempt, including incomplete attempts, so partial retries cannot repeatedly claim full XP. First attempt is 100%, next 50%, all later attempts 25% of workout XP. Feedback adds 2 XP once per record.
- Daily planned-workout and five-active-minute quests each add 5 XP once per local date. Feedback quest has no extra daily bonus, avoiding a second award on top of per-record feedback XP. Five active minutes exclude rest and pause time. Workout duration includes rest but excludes pause/background time. Calories use actual active time, exercise MET and profile weight; missing weight falls back to 70 kg, so values are estimates. Calories never affect rewards.
- Weekly targets default to 3, clamped to 1–7. A qualifying workout is at least 50% complete. Weekly bonus is 30 XP once per local Monday-based week. `weeklyCounts`, `weeklyCategories` and `metGoalWeeks` on the user hold history, with no destructive weekly resets. Application totals and achievements never decrease.
- `preferredWorkoutMinutes` defaults to 10. Optional `preferredWorkoutDays` uses ISO numbers 1=Monday through 7=Sunday. Other days are planned recovery days. Optional `equipment` and `preferredExerciseCategories` are string lists; `fitnessGoal` is a string. The existing profile's `fitnessLevel` and `preferredExercises` are reused. These additional schedule/preferences can be set on the user's document; a preferences editor is future UI work. With no equipment data, availability is unknown and requirements are shown in details; an explicit list such as `["none"]` filters out chair workouts.
- Recommendations prioritize discomfort within seven local dates, then scheduled recovery, inactivity of at least seven days, previous excessive difficulty, recent strength (48 hours), two easy workouts, duration/categories/goal/equipment and next unlocked beginner content. Safety deliberately overrides the specification's inactivity-first ordering. All seeded path workouts remain beginner level; two easy ratings suggest available later beginner content rather than inventing an advanced workout. No suitable safe content yields a recovery message.
- Recovery and comeback are standalone lessons in the same path collection. They are excluded from sequential path completion; doing them cannot unlock path lessons. They can count toward Momentum if completed at least 50%.
- First Step requires a qualifying workout; Routine Builder requires two distinct weeks meeting target; Path Explorer requires all seven path lessons at least 50%; Welcome Back requires a qualifying return after seven inactive days; Honest Reflection requires five feedback records; Balanced Week requires strength, mobility and recovery lesson categories in one local week. Achievements have no extra XP bonus.
- Device local timezone consistently determines day/week boundaries and is captured on records. Changing timezone can change a date/week bucket; fixed home-timezone policies are future work. There is no daily streak.
- Timers use monotonic elapsed time and automatically pause on background/recreation. Local drafts are per authenticated UID, saved on transitions/pause/recreation and excluded from backups/transfers. Home offers Resume and Discard Unfinished Workout. A discarded local session's server start is retained without a record/rewards; future administrative cleanup may remove abandoned starts.
- Content/progress reads and transactions currently require network. Offline or permission failures show retry; the draft is retained. There is no offline reward queue. Content changes mid-session are not versioned; changed exercise counts produce a recoverable error and the user may discard/restart the draft.
- Quick logs remain independent and do not grant structured rewards or unlock lessons. Their existing `exerciseLogs`/`dailyData` behavior is retained. Custom entries use a clearly labeled general 3-MET estimate.
- History display is the latest 30 records; pagination is future work. Week aggregates and per-lesson counts are stored in maps for the MVP. At larger scale, move long-lived week history to separate documents to avoid document growth/contention. Quest templates and achievement conditions are intentionally deterministic; future versions can introduce versioned templates or server evaluation.

## Build

```sh
# Standard Android Studio environment:
bash gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

In the provided workspace, use `JAVA_HOME=/workspace/jdk-21 ANDROID_HOME=/workspace/android-sdk GRADLE_USER_HOME=/workspace/.gradle` and `--offline`. Gradle needs permission to open its local lock socket. Release builds produce an unsigned APK; configure your own release signing outside source control. The debug APK uses the existing application's Firebase client configuration. Seed shared data and review/deploy rules to the matching development project before testing live workouts. No credentials, production data or Firebase settings were changed.

The manifest's pre-existing `firebase_auth_emulator_host` metadata does not itself configure Firebase SDK emulator routing. For Android emulator testing, point Auth and Firestore to `10.0.2.2:9099` and `10.0.2.2:8080` via `useEmulator` in a debug-only initialization before first use, with a dedicated development Firebase config. This was not enabled automatically against your existing project.

## Manual QA checklist — pending a device

- [ ] Existing registration, verification, login, reset and logout still work.
- [ ] Profile edits preserve email, XP, level and all existing fields.
- [ ] Food search/log and dashboard daily calories still work.
- [ ] Quick Log preset/custom entries save/read and update daily calories as before; no lesson unlock.
- [ ] New authenticated user: Exercise Home opens; Lesson 1 unlocked; Lesson 2 locked and untappable.
- [ ] Details shows ordered four exercises, targets, equipment and safety guidance.
- [ ] Timed exercises count down; rest advances; repetitions show targets; skip reduces completion.
- [ ] Pause/background/rotation preserve session state; resuming requires explicit Resume.
- [ ] End confirmation supports early attempts; Home can resume or discard a local draft.
- [ ] Feedback requires both answers; discomfort shows guidance and changes next recommendation to recovery.
- [ ] Complete Lesson 1 at least 50%: one record, expected XP/stars, updated level/Momentum and Lesson 2 unlocked.
- [ ] Open result repeatedly and retry completion after network uncertainty: no duplicate rewards.
- [ ] Repeat Lesson 1: workout XP scales to 50%, then 25%; best stars do not decrease.
- [ ] Five-minute quest excludes rests; quest rewards occur once per local date; weekly bonus once per week.
- [ ] Recovery days do not reduce XP or achievements; weekly percentage reflects completed/target only.
- [ ] Restart application: saved records/progress persist; incomplete draft resumes correctly.
- [ ] Signed-out, missing profile/content/reference, denied permission and offline cases show safe guidance/retry.
- [ ] Two accounts have isolated records/progress; emulator rules suite also checks this automatically.
- [ ] Large font, TalkBack, dark mode, portrait/landscape and system insets remain usable.
