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
        val after = client.permissionController.getGrantedPermissions()
        for ((key, permission) in mapOf("steps" to STEPS, "sleep" to SLEEP, "heart" to HEART)) {
            if (permission !in after) snapshot[key] = mapOf("state" to "permission_required")
        }
        if (listOf("steps", "sleep", "heart").any {
                (snapshot[it] as? Map<*, *>)?.get("state") in listOf("available", "unavailable")
            }) snapshot["syncedAt"] = Instant.now().toEpochMilli()
        callback.onResult(snapshot, after)
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
        @JvmField val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        @JvmField val SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
        @JvmField val HEART = HealthPermission.getReadPermission(HeartRateRecord::class)
        @JvmStatic fun permissions(): Set<String> = setOf(STEPS, SLEEP, HEART)
        @JvmStatic fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)
        @JvmStatic fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = PermissionController.createRequestPermissionResultContract()
        @JvmStatic fun settings(context: Context): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)
    }
}
