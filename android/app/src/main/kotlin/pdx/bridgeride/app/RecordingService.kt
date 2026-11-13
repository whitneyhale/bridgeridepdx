package pdx.bridgeride.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pdx.bridgeride.core.Fix
import pdx.bridgeride.core.FixLine
import pdx.bridgeride.core.GapReport
import pdx.bridgeride.core.Odometer
import pdx.bridgeride.core.RideMeta
import pdx.bridgeride.core.RidePlan
import pdx.bridgeride.core.RideStats
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/** Live ride numbers, shared between the service and the screen. */
data class RecorderStatus(
    val recording: Boolean = false,
    val startMs: Long? = null,
    val lastFix: Fix? = null,
    val fixes: Int = 0,
    val distanceM: Double = 0.0,
    val gapsOver10s: Int = 0,
    val longestGapMs: Long = 0,
)

object Recorder {
    private val _status = MutableStateFlow(RecorderStatus())
    val status: StateFlow<RecorderStatus> = _status.asStateFlow()
    internal fun update(s: RecorderStatus) { _status.value = s }

    /** Starts a ride. With a plan the ride follows its line; with none it only records. */
    fun start(context: Context, plan: RidePlan? = null) {
        Nav.begin(plan)
        context.startForegroundService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START))
    }

    fun stop(context: Context) = context.startService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
    )
}

/**
 * Records GPS fixes while a ride runs, screen on or locked (N-06).
 * Every fix is written and flushed at once, so a crash loses at most one line.
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "pdx.bridgeride.START"
        const val ACTION_STOP = "pdx.bridgeride.STOP"
        private const val CHANNEL = "ride"
        private const val NOTE_ID = 1
    }

    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var writer: BufferedWriter? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var status = RecorderStatus()
    private var odometer = Odometer()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                onFix(
                    Fix(
                        timeMs = loc.time,
                        lat = loc.latitude,
                        lon = loc.longitude,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy else 999f,
                        speedMps = if (loc.hasSpeed()) loc.speed else null,
                        bearingDeg = if (loc.hasBearing()) loc.bearing else null,
                    ),
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRide()
            else -> startRide()
        }
        return START_NOT_STICKY
    }

    private fun startRide() {
        if (status.recording) return
        val start = System.currentTimeMillis()
        startForeground(NOTE_ID, notification("Recording", "Waiting for GPS"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        val file: File = RideStore.newRideFile(this, start)
        writer = BufferedWriter(FileWriter(file, true))
        // Remember what this ride set out to follow, so the log and the summary can name it.
        Nav.plan.value?.let { RideStore.writeMeta(file, RideMeta(name = it.name, planId = it.id)) }
        // Keeps the CPU awake between fixes so Samsung power saving does not batch or drop them.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bridgeride:ride")
            .apply { acquire(6 * 60 * 60 * 1000L) } // 6 h safety cap
        status = RecorderStatus(recording = true, startMs = start)
        odometer = Odometer()
        Recorder.update(status)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopRide() // permission was removed: keep what we have, stop cleanly
        }
    }

    private fun onFix(f: Fix) {
        val w = writer ?: return
        w.write(FixLine.encode(f)); w.newLine(); w.flush()
        Nav.onFix(f) // keeps the marker and the progress along the line current, screen on or off
        // Gaps: time since the previous fix of any quality (same rule as GapReport).
        var gaps = status.gapsOver10s
        var longest = status.longestGapMs
        status.lastFix?.let { prev ->
            val gap = f.timeMs - prev.timeMs
            // A silence with the rider in the same place is a stop (Android slows GPS then), not a gap.
            if (!GapReport.isStoppedPause(prev, f)) {
                if (gap > GapReport.LIMIT_MS) gaps++
                if (gap > longest) longest = gap
            }
        }
        // Distance: only real movement between good fixes (core Odometer, same rule as RideStats.distanceM).
        val dist = odometer.add(f)
        status = status.copy(lastFix = f, fixes = status.fixes + 1, distanceM = dist, gapsOver10s = gaps, longestGapMs = longest)
        Recorder.update(status)
        getSystemService(NotificationManager::class.java)
            .notify(NOTE_ID, notification("Recording", "%.2f mi, %d fixes".format(dist / 1609.344, status.fixes)))
    }

    private fun stopRide() {
        client.removeLocationUpdates(callback)
        writer?.close(); writer = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        status = status.copy(recording = false)
        Recorder.update(status)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (status.recording) stopRide()
        super.onDestroy()
    }

    private fun notification(title: String, text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Ride recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bike)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }
}
