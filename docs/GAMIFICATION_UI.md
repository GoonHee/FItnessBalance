# Colourful workout rewards

## Use

Pull master, sync Gradle, build and run. Open Exercise to see the purple/gold XP card, teal weekly-momentum ring, coloured quest cards and clearer recommendation section. Completed quests and earned achievement cards have a check icon and text state; colour is not the only indicator. Result screens include a gold reward summary with XP and stars. Light and dark palettes are supplied.

Finish a guided workout and submit feedback. Once its immutable workout record is confirmed from Firestore, the result screen may show **one combined celebration** with earned XP/stars, a level change, new unlocks and weekly-target recognition. Continue dismisses it; the ordinary result screen remains available. Under-50% attempts use a recorded-effort message. Discomfort feedback keeps the saved reward information but uses a supportive message and no star animation.

Manual activity logs and ordinary history navigation do not trigger this guided-session celebration. No reward preview is shown as if a failed or pending save succeeded.

## Duplicate prevention and lifecycle

Only the feedback flow's confirmed save/retry path sets `exercise.celebrate_saved`. Result loading explicitly reads the record with `Source.SERVER`, rejects pending/cache-only metadata for celebration, and waits until the activity is resumed and the same account is signed in.

Before displaying, a local SQLite receipt with primary key `(uid, session)` is inserted using `CONFLICT_IGNORE`. The database is in Android private no-backup storage. A tiny indexed insert happens on the main thread immediately before showing the dialog, so rotation cannot interleave between eligibility and display. No workout/health values are stored in this receipt, only the account and session IDs. The unique constraint also protects concurrent claims. Receipts are not evicted during normal use.

A per-activity handled flag survives state restoration. The current popup is dismissed when leaving/stopping the screen, and it does not reappear when returning or rotating; its information remains on the result page. A retry/reopened save for that same record is also suppressed by the persistent local receipt. If receipt persistence fails, the app skips the popup and retains the result summary. A crash immediately after claiming may skip a celebration rather than replay it. This is local at-most-once presentation, not a cross-device Firebase event ledger; clearing app data/reinstalling resets local receipts.

## Reward meaning and motion

Presentation reads existing immutable values. Level transitions compare the stored total XP after the workout with `totalXpAfter - xpEarned`. Weekly recognition requires a qualifying completion and the saved count exactly reaching the saved target. New unlocks use the existing translated unlock names; these may include lessons and achievements.

The displayed XP is the already-awarded total, including applicable bonuses. The UI does not invent a per-bonus breakdown because the current record does not store every component separately. XP calculation, repeat reductions, weekly targets, rewards, calories, CMAB and progression rules are unchanged.

XP and ring indicators animate only when Android reports that system animators are enabled. The pop-up uses one short star scale animation, with no looping confetti, sound or forced dismissal timer. It is static with system animation scale disabled or discomfort feedback. Its content scrolls and has a visible Continue action. No new notification, camera, storage or health permission is requested.

## Setup and validation

No Firestore schema/rules changes, deployment, catalogue import, seed or extra dependency is needed for this UI iteration. Existing rules must already support the exercise module.

JVM tests check level thresholds, actual bonus-inclusive transitions, qualifying weekly target recognition, under-target/above-target/incomplete attempts, history/pending/repeated presentation eligibility and reduced-motion decisions. Debug assembly and Android lint validate resources and integration. The existing unit suite is also run. No physical device was attached in the cloud, so actual rendering, animations, SQLite presentation receipts and lifecycle behavior still need device acceptance.

Device checklist:

1. Check Exercise and Achievements in light/dark themes, large font and small/landscape screens; verify XP/week counts match existing data.
2. Complete a new guided session, submit feedback and see one combined popup after save confirmation. Check it matches the result XP/stars.
3. Dismiss, rotate, reopen and retry the same saved result: no new celebration should appear. A separate newly saved session may show one.
4. Rotate or background during saving/loading. Verify no popup overlays a different account/screen and reward calculation remains once-only.
5. Disable animator duration scale; check a static presentation. Submit discomfort feedback and check recovery guidance/static icon.
6. Simulate a save/read failure: no success popup; use the existing retry path. Verify unchanged exercise totals, XP, quests and achievements.
