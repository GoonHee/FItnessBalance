# Health Connect: foreground summary module

Open **Profile → Connect health data**. Install/update Health Connect if prompted, grant the read permissions you choose, then tap **Sync health summaries now**. Returning from settings refreshes connection/permission state but does not read new health records until Sync is tapped.

## Scope and compatibility

- AndroidX Health Connect client 1.1.0, read-only. The library requires minSdk 26, so the app minimum increases from 23 to 26. Compatible Android 9+ devices can use Health Connect; Android 8 can run the app but receives the unavailable screen. Android 14+ normally provides Health Connect through the system; compatible older versions need the provider app.
- Java screen, cache and freshness policy; one small Kotlin bridge calls the SDK's official suspend APIs. The repository already uses the Kotlin Android plugin. This does not convert the existing application screens to Kotlin.
- Declares READ_STEPS, READ_SLEEP and READ_HEART_RATE only. No background/history access, exercise-session access, calorie access or health writes.
- Android 13 rationale intent and Android 14+ permission-usage alias open the same public explanation screen; that activity never displays user data.

## Windows and data meaning

Steps and recorded heart-rate average/minimum/maximum cover local midnight through sync time. Sleep duration covers the preceding 24 actual hours. Windows, time zone, latest contributing record/sample and last successful sync are displayed. Sleep is not a diagnostic quality score, and the heart-rate summary is not resting heart rate.

The SDK aggregate API supplies totals and applies its supported provider aggregation/priority behaviour. Do not sum this result with raw records again. Separate paginated reads identify the latest contributing source timestamp; their record values are not independently added to totals. The page limit fails a metric rather than presenting a silently truncated result.

Missing aggregates remain unavailable; no missing metric is filled with zero. A recorded zero is distinct. Individual metrics distinguish missing permission, no records, and read errors. Partial permissions and missing wearable/source data are supported. Successful reads with no records still confirm that a sync was attempted successfully; they do not imply any new measurement.

A summary is stale after 24 hours or a day/time-zone change. A metric is also marked stale/unknown if its source timestamp is missing, in the future or at least 24 hours old. This is an explicit cache freshness rule, not medical readiness assessment.

## Storage and isolation

Only aggregate summaries, status and window/source timestamps are saved. Cache files are account-specific (SHA-256 UID filename), Android-private and inside getNoBackupFilesDir. They are excluded from Android backup. No health data is uploaded to Firestore or analytics by this module. Raw source records are not retained.

Permission checks mask and remove revoked metric values from the local cache. Clear saved summaries removes this account's cache; it does not delete records from Health Connect or their source apps. Manage Health Connect access/data opens provider controls. Uninstall removes the local cache. Switching accounts uses separate cache files and stale callbacks cannot display the previous account's results. Backgrounding cancels pending work; there is no background sync job.

Nothing writes exerciseLogs, workoutRecords, dailyData, caloriesBurned, goals, XP or achievements. Fresh sleep summaries can influence recommendations only after separate opt-in on Exercise; see ADAPTIVE_RECOMMENDATIONS.md. This separation prevents exercise calorie double counting and provides a foundation for later adaptive logic without implying it already exists.

No Firebase schema, rule deployment, service account or seed is needed. First-time library downloads require Gradle network access. This change does not establish production Health Connect/Play distribution approval.

## Device acceptance checklist

1. Pull latest master on a clean branch, sync Gradle and build. Run a compatible Android 9+ device or a supported emulator with Health Connect and a source/toolbox providing test records.
2. Open Profile → Connect health data. Test available, provider-update-required, and unsupported-device states. Food/exercise logs must remain usable in all cases.
3. Grant steps only. Sync: sleep/heart show permission not granted. Grant remaining types and retry.
4. Use known test records from Health Connect Toolbox or a source app: verify daily steps, prior-24-hour sleep, heart average/min/max and displayed time windows. Compare against Health Connect's provider data and its source-priority settings.
5. Remove all sleep/heart records: those metrics must show unavailable, not zero. Permission denial and read errors must have distinct explanations.
6. Revoke permissions in Health Connect and return: hidden values must disappear and be removed from cache. Clear cache and reopen: no prior values may reappear without a new sync.
7. Reopen without syncing: cached timestamps stay unchanged. Test day/time-zone changes and older source records; stale data must be labelled.
8. Background/rotate during a sync; then return and retry. Never show a success from a cancelled request or from another signed-in account.
9. Sync repeatedly and compare dailyData/exerciseLogs/workoutRecords before/after: no change to calorie totals or XP and no duplicate exercise entries.
10. Check both Android 13 rationale and Android 14+ permission usage links. They must show the privacy explanation without private health values.

Local JVM tests cover permission masking, missing-value preservation and freshness boundaries. SDK calls compile against the actual library. No live health provider/device acceptance was performed in the cloud environment.

API references: https://developer.android.com/health-and-fitness/guides/health-connect/develop/get-started and https://developer.android.com/health-and-fitness/guides/health-connect/develop/aggregate-data . API signatures were checked against the published connect-client 1.1.0 sources.
