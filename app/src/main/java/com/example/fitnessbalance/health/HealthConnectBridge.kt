package com.example.fitnessbalance.health

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import java.time.*
import kotlin.reflect.KClass

/** Coroutine boundary for the Java UI. Reads only; no cloud uploads or exercise calorie writes. */
class HealthConnectBridge(context: Context) {
    interface Callback {
        fun onResult(snapshot: Map<String, Any?>, granted: Set<String>)
        fun onError()
    }
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client by lazy { HealthConnectClient.getOrCreate(this.context) }

    fun check(callback: Callback) = launch(callback) {
        callback.onResult(emptyMap(), client.permissionController.getGrantedPermissions())
    }

    fun sync(callback: Callback) = launch(callback) {
        val granted = client.permissionController.getGrantedPermissions()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant()
        val sleepStart = now.minus(Duration.ofHours(24))
        val daily = TimeRangeFilter.between(start, now)
        val sleepWindow = TimeRangeFilter.between(sleepStart, now)
        suspend fun metric(permission: String, read: suspend () -> Map<String, Any?>): Map<String, Any?> {
            if (permission !in granted) return mapOf("state" to "permission_required")
            return try { read() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: SecurityException) { mapOf("state" to "permission_required") }
            catch (_: Exception) { mapOf("state" to "error") }
        }
        val snapshot = mutableMapOf<String, Any?>(
            "day" to today.toString(), "zone" to zone.id,
            "attemptedAt" to now.toEpochMilli(), "windowStart" to start.toEpochMilli(),
            "windowEnd" to now.toEpochMilli(), "sleepWindowStart" to sleepStart.toEpochMilli())
        snapshot["steps"] = metric(STEPS) {
            val result = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), daily))
            val count = result[StepsRecord.COUNT_TOTAL]
            val latest = if (count != null) latest(StepsRecord::class, daily) { it.endTime.coerceAtMost(now) } else null
            mapOf("state" to if (count == null) "unavailable" else "available", "value" to count, "observedAt" to latest)
        }
        snapshot["sleep"] = metric(SLEEP) {
            val result = client.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), sleepWindow))
            val duration = result[SleepSessionRecord.SLEEP_DURATION_TOTAL]
            val latest = if (duration != null) latest(SleepSessionRecord::class, sleepWindow) { it.endTime.coerceAtMost(now) } else null
            mapOf("state" to if (duration == null) "unavailable" else "available", "value" to duration?.seconds, "observedAt" to latest)
        }
        snapshot["heart"] = metric(HEART) {
            val result = client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX), daily))
            val avg = result[HeartRateRecord.BPM_AVG]
            val latest = if (avg != null) latest(HeartRateRecord::class, daily) { r ->
                r.samples.filter { !it.time.isBefore(start) && !it.time.isAfter(now) }.maxOfOrNull { it.time }
            } else null
            mapOf("state" to if (avg == null) "unavailable" else "available", "value" to avg,
                "min" to result[HeartRateRecord.BPM_MIN], "max" to result[HeartRateRecord.BPM_MAX], "observedAt" to latest)
        }
        snapshot["workouts"] = workouts(granted, now)
        val after = client.permissionController.getGrantedPermissions()
        if (EXERCISE !in after) snapshot["workouts"] = mapOf("state" to "permission_required")
        for ((key, permission) in mapOf("steps" to STEPS, "sleep" to SLEEP, "heart" to HEART)) {
            if (permission !in after) snapshot[key] = mapOf("state" to "permission_required")
        }
        if (listOf("steps", "sleep", "heart").any {
                (snapshot[it] as? Map<*, *>)?.get("state") in listOf("available", "unavailable")
            }) snapshot["syncedAt"] = Instant.now().toEpochMilli()
        callback.onResult(snapshot, after)
    }

    fun syncWorkouts(callback: Callback) = launch(callback) {
        val granted = client.permissionController.getGrantedPermissions()
        var workouts = workouts(granted, Instant.now())
        val after = client.permissionController.getGrantedPermissions()
        if (EXERCISE !in after) workouts = mapOf("state" to "permission_required")
        callback.onResult(mapOf("workouts" to workouts), after)
    }

    private suspend fun workouts(granted: Set<String>, now: Instant): Map<String, Any?> {
        if (EXERCISE !in granted) return mapOf("state" to "permission_required")
        return try {
            val start = now.minus(Duration.ofDays(7))
            val rows = mutableListOf<Map<String, Any?>>()
            var token: String? = null
            var pages = 0
            do {
                if (++pages > 20) error("Incomplete exercise read")
                val response = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,
                    TimeRangeFilter.between(start, now), pageSize = 200, pageToken = token))
                for (record in response.records) {
                    // This rolling cache contains completed sessions starting inside the window.
                    if (record.startTime.isBefore(start) || !record.startTime.isBefore(now) || record.endTime.isAfter(now)) continue
                    rows.add(mapOf("id" to record.metadata.id, "source" to record.metadata.dataOrigin.packageName,
                        "type" to record.exerciseType, "activity" to activityName(record.exerciseType),
                        "title" to (record.title ?: "").take(200), "start" to record.startTime.toEpochMilli(),
                        "end" to record.endTime.toEpochMilli(), "modified" to record.metadata.lastModifiedTime.toEpochMilli()))
                    if (rows.size > 1000) error("Exercise read limit exceeded")
                }
                token = response.pageToken
            } while (token != null)
            mapOf("state" to "available", "windowStart" to start.toEpochMilli(), "windowEnd" to now.toEpochMilli(),
                "syncedAt" to Instant.now().toEpochMilli(), "records" to rows)
        } catch (cancel: CancellationException) { throw cancel }
          catch (_: SecurityException) { mapOf("state" to "permission_required") }
          catch (_: Exception) { mapOf("state" to "error") }
    }

    private suspend fun <T : Record> latest(type: KClass<T>, range: TimeRangeFilter, event: (T) -> Instant?): Long? {
        var token: String? = null
        var latest: Instant? = null
        var pages = 0
        do {
            if (++pages > 100) error("Too many records; do not report a partial summary")
            val page = client.readRecords(ReadRecordsRequest(type, range, pageSize = 1000, pageToken = token))
            for (record in page.records) {
                val time = event(record)
                if (time != null && (latest == null || time.isAfter(latest))) latest = time
            }
            token = page.pageToken
        } while (token != null)
        return latest?.toEpochMilli()
    }
    private fun launch(callback: Callback, block: suspend () -> Unit) {
        scope.launch { try { block() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { callback.onError() } }
    }
    fun cancelPending() { scope.coroutineContext.cancelChildren() }
    fun close() { scope.cancel() }

    companion object {
        @JvmStatic fun activityName(type: Int): String = when(type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Treadmill running"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Stationary cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Pool swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Open-water swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength training"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weight lifting"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dancing"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing"
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT -> "Other workout"
            else -> "Workout (type $type)"
        }
        @JvmField val EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        @JvmField val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        @JvmField val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
        @JvmField val HEART = HealthPermission.getReadPermission(HeartRateRecord::class)
        @JvmStatic fun permissions(): Set<String> = setOf(STEPS, SLEEP, HEART, EXERCISE)
        @JvmStatic fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)
        @JvmStatic fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()
        @JvmStatic fun settings(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
    }
}
