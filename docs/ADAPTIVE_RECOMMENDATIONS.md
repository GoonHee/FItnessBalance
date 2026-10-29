# Explainable adaptive workout suggestions

Open **Exercise → Use sleep summaries for workout suggestions**. The switch is off by default. Enabling displays a consent/explanation dialog. Health Connect permissions are checked separately; the switch does not grant them. Manually sync from **Profile → Connect health data**. No background health reads or sync jobs are added.

## Rules: sleep-rules-v1 / workout-rules-v1

1. Compute the existing usual recommendation from profile preferences, recent workout categories/difficulty feedback, inactivity and discomfort/recovery rules.
2. If sleep use is disabled or permission cannot be verified, keep that usual recommendation.
3. Require a fresh local sleep summary and source timestamp, matching current local day/time zone, representing an explicit preceding-24-hour window. Missing, old, future or unusable values do not become zero.
4. Build a comparison from at least 3 **previous distinct source days**, within 14 days and the same time zone, synced at comparable clock times (within 2 hours). Duplicate same-day observations do not inflate the baseline. Use their median recorded duration. A source summary outside 1–16 hours is excluded as an input-quality guard, not classified medically.
5. A recorded shortfall of at least 1 hour can suggest a shorter existing, unlocked, equipment-compatible lesson. Choose the nearest shorter estimate, of the same stated difficulty or beginner difficulty. Only regular lessons and the recovery alternative can be selected, not an unrelated comeback alternative. Existing discomfort, scheduled recovery, comeback, easier-workout, activity-balance and equipment fallback reasons keep priority.
6. If there is no shorter eligible lesson, keep the usual recommendation. Existing exercise targets and timers are not shortened or generated.
7. Users can keep their usual recommendation. Before opening details, show the selected workout, its estimated duration and explanation for confirmation. Actual session start still takes place in the existing workout-details flow. Resuming an existing draft does not replan its workout.

The threshold is a transparent prototype rule, not a clinically validated recovery model or CMAB learning engine. Recorded sleep may be incomplete; comparable sync windows are only a quality check and cannot establish full sensor coverage. The app does not diagnose fatigue or certify readiness. Steps and heart-rate averages do not influence this first rule. No calorie/nutrient targets or exercise calorie accounting changes are made.

With no prior history, health suggestions will not trigger immediately: at least 3 previous comparable observed days are required before evaluating a new day's record. An existing short usual workout can remain unchanged even with a shortfall.

## Input and decision records

Account-private files use the same Android no-backup storage mechanism as health summaries. Baselines retain up to 14 observations; eligibility checks restrict comparisons to the prior 14 days. The last 20 decisions are bounded further to fit the cache size. No health input or decision is uploaded to Firestore/analytics.

Decisions store:
- Rule and base-rule versions, creation time, unique local decision ID.
- Exact relevant preference/history context, including equipment, duration, fitness level, goal, recent feedback/categories, inactivity, discomfort and recovery flags.
- Available lesson IDs, estimated durations, difficulty, equipment, categories and sequence.
- Allowed sleep summary, sync/window/source metadata, comparison observations and resulting median.
- Usual reason/lesson, health-rule reason, selected lesson/duration, whether health changed the selection.
- `shown` or `accepted_for_review` confirmation. This deliberately does not claim a workout was started or completed.

**Why this suggestion?** shows the user-facing explanation, preferred duration, comparison count and rule version. Keeping the usual plan records that choice. If permission verification fails, the usual recommendation remains available. If local audit saving fails, the screen reports it without claiming that an explanation was saved.

Disabling sleep use removes baseline/decision history before reverting to usual suggestions; subsequent usual recommendations may create new records containing no sleep inputs. Revoking sleep permission clears stored baseline/decision inputs when access is next checked. Removing saved summaries on the health screen removes the adaptive cache and opt-in setting as well. Device/account lifecycle guards suppress obsolete health callbacks. No new Firestore rules or migration is required.

## Device acceptance

- Enable with no history: usual recommendation and baseline-building explanation, no fabricated sleep.
- Confirm privacy text describes optional workout adaptation; decline opt-in and verify no sleep-based adjustment.
- Sync on separate days at similar times; verify median and comparison count. Use unit fixtures for repeatable deficit scenarios rather than injecting fake data into real user profiles.
- With fresh usable lower sleep and a shorter unlocked lesson, verify name, duration, explanation and normal-plan alternative.
- Choose usual, cancel confirmation, or continue to details. Only the normal details Start creates a session.
- Verify discomfort and scheduled recovery retain priority, missing equipment is respected, and no locked lesson is selected.
- Revoke permission, remove cache, change day/time zone, sign out, rotate/background during checks: use normal fallback; never display another account's recommendation.
- Compare food goals, exerciseLogs/dailyData calories, XP and draft targets before/after generating or accepting suggestions: they must remain unchanged.

The local suite tests selection, safety priority, equipment, missing/stale/future/zero data, minimum baseline, duplicates, time-window comparability, catalogue absence and audit choices. Real Health Connect, cache retention and confirmation UI need device acceptance; no such device was available in the cloud environment.
