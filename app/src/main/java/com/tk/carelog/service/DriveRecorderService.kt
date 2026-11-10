package com.tk.carelog.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.LiveDrive
import com.tk.carelog.engine.TripAccumulator
import com.tk.carelog.vehicle.CarSignals

/**
 * 시동(부팅)과 함께 시작해 1초마다 차속·고도를 트립 요약에 누적합니다.
 * 트립 시작: 차속 > 5km/h / 트립 종료: P단 또는 120초 이상 정차.
 * 화면 표시는 하지 않습니다 (주행 중 조작 없음).
 */
class DriveRecorderService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var signals: CarSignals
    private var trip: TripAccumulator? = null
    private var tripStartOdo: Double? = null
    private var stillSec = 0
    private var tick = 0

    private val loop = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        signals = CarSignals(this).also { it.start() }
        handler.post(loop)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun step() {
        val app = application as CareLogApp
        val now = System.currentTimeMillis()

        // 5초마다 주행거리계와 엔진오일 레벨 확인
        if (tick++ % 5 == 0) {
            // 시연 모드에서는 시연용 주행거리를 유지 (에뮬레이터 값으로 덮어쓰지 않음)
            if (!app.repo.state.settings.demoMode) {
                app.vehicle.readOdometer { km -> if (km != null) app.repo.setOdometer(km) }
            }
            app.vehicle.readEngineOilLevel { st ->
                if (st != null && st != app.repo.state.oilLevelState) {
                    // 부족 상태로 바뀐 순간만 경고 이력으로 센다 (상수 이름은 SDK 확인 후 맞출 것)
                    val low = st.contains("LOW", ignoreCase = true)
                    app.repo.update { it.copy(oilLevelState = st, warningCount = it.warningCount + if (low) 1 else 0) }
                }
            }
        }

        val v = signals.speedKmh ?: return
        // 시연 주행 중에는 실시간 화면을 시연 쪽이 쓰므로 건드리지 않는다
        if (app.demo.running.value == null) {
            val cur = trip
            app.repo.setLive(
                LiveDrive(
                    speedKmh = v,
                    gear = if (signals.gearPark == true) "P" else if (v > 0.5) "D" else "N",
                    tripKm = cur?.distanceSoFarKm ?: 0.0,
                    idleSec = cur?.idleSoFarSec ?: 0.0,
                    congestedKm = cur?.finishPreviewCongestedKm() ?: 0.0,
                    label = "실시간 주행",
                )
            )
        }
        val t = trip
        if (t == null) {
            if (v > 5.0) {
                trip = TripAccumulator().also { it.add(now, v, signals.altitudeM) }
                tripStartOdo = app.repo.state.odometerKm.takeIf { it > 0 }
                stillSec = 0
            }
            return
        }
        t.add(now, v, signals.altitudeM)
        stillSec = if (v < 1.0) stillSec + 1 else 0
        val parked = signals.gearPark == true && v < 1.0
        if (parked || stillSec >= 120) finishTrip()
    }

    private fun finishTrip() {
        val app = application as CareLogApp
        val t = trip ?: return
        trip = null
        if (t.distanceSoFarKm < 0.2) return
        val odoDelta = tripStartOdo?.let { start -> app.repo.state.odometerKm - start }?.takeIf { it > 0.05 }
        val summary = t.finish(odoDelta)
        // 주행거리계를 읽을 수 있으면 그 값이 이미 반영되어 있으므로 다시 더하지 않음
        app.repo.addTrip(summary, advanceOdometer = odoDelta == null)
        Log.i(TAG, "트립 저장: ${"%.1f".format(summary.distanceKm)}km, 정체 ${"%.1f".format(summary.congestedKm)}km")
    }

    override fun onDestroy() {
        finishTrip()
        handler.removeCallbacks(loop)
        signals.stop()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "주행 기록", NotificationManager.IMPORTANCE_MIN))
        val n: Notification = Notification.Builder(this, CH)
            .setContentTitle("CareLog")
            .setContentText("주행 조건을 기록하고 있어요 (경로는 저장하지 않음)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val TAG = "CareLog/Recorder"
        private const val CH = "carelog_recording"
        @Volatile var running = false

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, DriveRecorderService::class.java))
            } catch (t: Throwable) {
                Log.w(TAG, "기록 서비스 시작 실패", t)
            }
        }
    }
}

/** 부팅(=시동) 시 자동으로 기록 시작 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DriveRecorderService.start(context)
    }
}
