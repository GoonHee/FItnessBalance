# Contextual bandit workout recommendations

## Enable and test

Pull master, sync Gradle and run. On Exercise enable **Learn workout suggestions from my feedback**. It is off by default and independent of the existing optional sleep adjustment. No new Firestore collection, rules deployment, seed or API key is required.

The standard recommendation determines a duration/difficulty ceiling. The bandit ranks unlocked, equipment-compatible, non-standalone lessons of that same difficulty and no longer than that recommendation. Completed unlocked lessons may be repeated, enabling learning without unlocking harder content. With fewer than two candidates it uses the normal rules. Discomfort, recovery day, comeback, easier-session and balance rules take priority. A sleep-shortfall adjustment also bypasses the bandit. Missing health data still permits ordinary recommendations; this model does not read health records.

Tap **Why?** for the learning explanation and version. **Keep usual recommendation** bypasses learning for this choice. Review and explicitly start the recommended session. Complete it and submit difficulty and feeling feedback. Return to Exercise: eligible saved outcomes update the local model. Refreshing or reopening screens does not invent new outcomes. Workouts started from the path, manual exercise and ordinary recommendations are not training samples.

## Algorithm

Disjoint LinUCB, version `workout-linucb-v1`, one ridge-linear model per lesson, ridge lambda=1, exploration alpha=0.35:

- context x: intercept, preferred minutes/180, inactive days capped at 7 divided by 7, presence of recent difficulty feedback, any recent too-easy feedback, and recent strength category;
- for each arm, A=I+sum(x*x^T), b=sum(reward*x), theta=A^-1*b;
- score=theta^T*x+alpha*sqrt(x^T*A^-1*x);
- highest score wins; exact ties prefer the normal-rule lesson, then stable lesson ID order.

All features are taken before selection. Models are replayed from at most the latest 80 attributed outcomes. This is a bounded rolling-history CMAB rather than an indefinitely accumulated model. Context-dependent regression and uncertainty-driven exploration distinguish it from the previous fixed rules. It is not a deep-learning model or evidence of improved health outcomes.

### Reward (`completion-fit-v1`)

Reward = 0.60*(completionPercentage/100) + 0.25*difficultyFit + 0.15*feelingFit.

- difficultyFit: suitable=1, too_easy=0.5, too_difficult=0;
- feelingFit: energized=1, normal=0.5, tired=0;
- discomfort overrides total reward to 0 and existing recovery rules retain priority;
- invalid/missing feedback produces no observation, not a zero reward.

Weights are explicit engineering choices, not clinically validated measurements. Completion and feedback are self-reported. Calories burned and XP are deliberately excluded from reward to avoid incentivising higher expenditure or points.

## Attribution, persistence and privacy

A confirmed review gets an account-local UUID with exact feature vector, algorithm/reward version, baseline, eligible candidate IDs, predicted scores, uncertainty bonuses and timestamp. Starting that exact lesson within 24 hours binds the decision once to the actual session UUID. After the immutable Firestore workout record is visible in the latest 30 sessions, the Exercise screen matches both session and lesson ID and records reward. The pending binding is removed atomically with local learning-state persistence. Reprocessing the same saved record cannot reward it twice. Repeated starts through the same review intent cannot bind the consumed decision again.

Data is stored in an account-hashed JSON file in Android's no-backup private directory, using AtomicFile and a 64KiB size guard. No photos, raw health records or model state are uploaded. At most 8 review decisions, 8 pending starts and 80 observations are retained. Old review decisions may be cleared; when pending capacity is reached the oldest binding is evicted. Completed observations retain their original context/reward/version/baseline/session; detailed candidate scores remain with pending/review decisions only. Turning learning off deletes its local history and pending decisions. Reinstalling, clearing app data, or switching devices does not transfer learning.

Learning occurs only after completed sessions have persisted, on the next Exercise load. Unstarted or abandoned sessions are not failures; very old completions outside the latest-30 query or evicted pending bindings are not learned. This introduces completed-session selection bias. There is no propensity-based off-policy evaluation, cross-device model, automated experiment dashboard, or claim of superiority over rules. The small seeded catalogue can limit the number of comparable arms.

## Validation and evaluation

JVM tests cover cold-start ties, preference-dependent contexts, reward learning, exploration, protected-rule bypass, equipment/difficulty/duration limits, standalone exclusion, corrupt observations, version mismatches, reward bounds and audit contents. Build and lint check Android integration; no physical-device test was performed in the cloud.

On a device verify opt-in/out, no-health fallback, eligible alternatives, Keep usual, resume/retry, outcome arrival after network recovery, account switching and existing recovery/sleep behavior. Compare completion and suitable-feedback rates against rule-only recommendations across enough real participants; record candidate availability and exposure, not only successful sessions. Any future controlled study needs a predeclared evaluation design. A working learning algorithm alone does not establish recommendation quality.
