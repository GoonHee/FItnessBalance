# Daily food and activity logs

The dashboard, Food Log, Activity Log and exercise home refresh at local midnight while visible and when returning after a day change. Device date/time and timezone changes are checked too. The dashboard clears the previous day's totals immediately before loading the new date; stale requests cannot restore an earlier day's totals.

Each day uses its own existing `yyyy-MM-dd` Firestore document under foodLogs, exerciseLogs and dailyData. Guided workout records retain their saved localDay. No historical records are deleted, moved or copied at midnight. Choose an earlier date in Food Log or Activity Log to review it. Explicit history browsing survives rotation on the same day; a calendar-day change returns the log screen to today, including after process restoration.

Already-open food entry forms and manual activity drafts keep the date displayed when they were opened, so an in-flight save is not silently moved to another day. Close that form and reopen from today's log to enter a new day's activity. Workouts in progress keep their existing session/completion rules. XP, achievements, workout progression and weekly Momentum retain their normal history.

No Firebase rules update, migration or seed run is needed.

## Device checks in Android Studio

1. Add food and manual exercise today; complete a guided workout and confirm today's totals.
2. Leave each daily screen open across midnight (a test device clock may be used). Check the date, empty entries/zero totals for a day without records, and fresh daily quests.
3. Background the app overnight, reopen it, and repeat after process recreation. No previous day's entries should be presented as today's.
4. Select yesterday using either date picker: the original food/exercise entries and totals should still be there. Rotate while browsing history on the same day; the selected date should remain.
5. Check month/year boundaries, timezone changes and slow network responses. Old callbacks should not repaint today's screen.

Automated JVM tests cover day transitions, backward calendar changes, history restoration and midnight scheduling including DST. Device UI and lifecycle checks require an attached Android device/emulator.
