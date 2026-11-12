package com.tk.carelog.demo

import com.tk.carelog.data.LiveDrive
import com.tk.carelog.data.Repository
import com.tk.carelog.data.ServiceRecord
import com.tk.carelog.data.State
import com.tk.carelog.data.Trust
import com.tk.carelog.engine.Condition
import com.tk.carelog.engine.MaintItem
import com.tk.carelog.engine.TripAccumulator
import com.tk.carelog.engine.TripSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * 시연용 주행.
 * 실제 기록과 같은 TripAccumulator 를 통과하고, 결과 트립에는 simulated=true 가 붙는다.
 * 첫 트립은 화면에서 실시간으로 보여주고(시간 압축), 그 주의 나머지 트립은 한 번에 반영한다.
 */
class DemoScenarios(private val repo: Repository) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    private val _running = MutableStateFlow<String?>(null)
    val running: StateFlow<String?> = _running.asStateFlow()

    enum class Script(val title: String, val desc: String) {
        CITY("도심 출퇴근 1주", "정체·정지출발이 많은 출퇴근"),
        HIGHWAY("고속도로 출퇴근 1주", "고속도로 위주 장거리 출퇴근"),
    }

    // ---------------------------------------------------------------- 속도 패턴

    /** 정지·출발이 반복되는 도심 구간. 약 1분 주기로 신호 대기 */
    private fun citySpeed(i: Int): Double {
        val phase = i % 60
        return when {
            phase < 8 -> 0.0
            phase < 20 -> (phase - 8) * 2.0
            phase < 45 -> 24.0 + 4 * sin(i / 5.0)
            else -> (60 - phase) * 1.6
        }
    }

    private fun highwaySpeed(i: Int): Double = when {
        i < 120 -> i * 0.85
        else -> 102.0 + 6 * sin(i / 40.0)
    }

    private fun speedOf(script: Script, i: Int) =
        if (script == Script.CITY) citySpeed(i) else highwaySpeed(i)

    /** 화면 없이 트립 하나를 통째로 만든다 */
    private fun buildTrip(script: Script, end: Long, km: Double): TripSummary {
        val acc = TripAccumulator(simulated = true)
        var t = end - 4 * 3_600_000L
        var dist = 0.0
        var i = 0
        while (dist < km) {
            val v = speedOf(script, i)
            acc.add(t, v, 30.0)
            dist += v / 3600.0
            t += 1000; i++
        }
        return acc.finish()
    }

    /** 신호 몇 번 받고 간선도로로 빠지는 평소 출퇴근 */
    private fun mixedSpeed(i: Int): Double = when {
        i < 300 -> citySpeed(i)
        i < 360 -> 24.0 + (i - 300) * 0.6
        else -> 58.0 + 8 * sin(i / 30.0)
    }

    private fun buildMixedTrip(end: Long, km: Double): TripSummary {
        val acc = TripAccumulator(simulated = true)
        var t = end - 2 * 3_600_000L
        var dist = 0.0
        var i = 0
        while (dist < km) {
            val v = mixedSpeed(i)
            acc.add(t, v, 30.0)
            dist += v / 3600.0
            t += 1000; i++
        }
        return acc.finish()
    }

    /** 최근 30일 기록: 평소 출퇴근 위주, 가끔 장거리와 동네 단거리 */
    private fun seedTrips(now: Long): List<TripSummary> {
        val day = 24L * 3600 * 1000
        val hour = 3_600_000L
        val out = mutableListOf<TripSummary>()
        for (d in 29 downTo 1) {
            val base = now - d * day
            val weekday = d % 7 !in listOf(0, 1)
            if (weekday) {
                out += buildMixedTrip(base - 15 * hour, 13.5 + (d % 3))
                if (d % 2 == 0) out += buildMixedTrip(base - 4 * hour, 13.0)
            } else if (d % 7 == 0) {
                out += buildTrip(Script.HIGHWAY, base - 8 * hour, 44.0)
            } else {
                out += buildTrip(Script.CITY, base - 6 * hour, 4.0)
            }
        }
        return out.sortedBy { it.endTime }
    }

    // ---------------------------------------------------------------- 실시간 시연

    fun play(script: Script) {
        if (job?.isActive == true) return
        job = scope.launch {
            _running.value = script.title
            val liveKm = if (script == Script.CITY) 7.0 else 30.0
            val simSecPerFrame = if (script == Script.CITY) 5 else 9
            val acc = TripAccumulator(simulated = true)
            val now = System.currentTimeMillis()
            var t = now
            var i = 0
            var km = 0.0
            var stops = 0
            var moving = false

            repo.setLive(LiveDrive(gear = "D", label = script.desc, simulated = true))
            delay(600)
            while (km < liveKm) {
                var v = 0.0
                repeat(simSecPerFrame) {
                    v = speedOf(script, i)
                    acc.add(t, v, 30.0)
                    km += v / 3600.0
                    if (v > 10) moving = true
                    if (moving && v < 3) { stops++; moving = false }
                    t += 1000; i++
                }
                repo.setLive(
                    LiveDrive(
                        speedKmh = v, gear = "D", tripKm = km, elapsedSec = i.toDouble(),
                        congestedKm = acc.finishPreviewCongestedKm(), stops = stops,
                        idleSec = acc.idleSoFarSec, label = script.desc, simulated = true,
                    )
                )
                delay(33)
            }
            // 주차
            repo.setLive(repo.live.value.copy(speedKmh = 0.0, gear = "P"))
            delay(700)

            // 첫 트립 + 그 주의 나머지 트립 반영 (판정은 마지막에 한 번)
            val day = 24L * 3600 * 1000
            val first = acc.finish()
            val perTrip = if (script == Script.CITY) 16.0 else 58.0
            val count = if (script == Script.CITY) 13 else 7
            // 1초 단위 시뮬레이션은 무거우므로 메인 스레드 밖에서 만든다
            val week = withContext(Dispatchers.Default) {
                (1..count).map { k -> buildTrip(script, now - (k * 7 * day) / (count + 1), perTrip) }
            }
            repo.addTrip(first, advanceOdometer = true, evaluate = false)
            week.forEach { repo.addTrip(it, advanceOdometer = true, evaluate = false) }
            repo.reevaluate()
            _running.value = null
        }
    }

    // ---------------------------------------------------------------- 시연 초기 상태

    /**
     * 2023년 9월에 신차로 받아 3년 2개월째 타는 그랜저 가솔린.
     * 엔진오일은 대부분 정비소 확인 기록, 최근 30일 운행 기록은 비어 있는 상태에서 시작.
     */
    fun reset() {
        job?.cancel()
        _running.value = null
        repo.clearBriefings()
        repo.setLive(LiveDrive())
        val now = System.currentTimeMillis()
        val month = 30L * 24 * 3600 * 1000
        fun ago(m: Double) = now - (m * month).toLong()

        repo.update { s ->
            State(
                settings = s.settings.copy(setupOdo = 0.0, setupTime = ago(38.0), habitOilKm = 5_000, lastReceiptWon = 80_000),
                odometerKm = 42_180.0,
                unrecordedKm = 1_240.0,
                lifetimeRecordedKm = 40_940.0,
                // 보유 기간 운행 프로필: 모든 조건이 '자주' 기준 아래
                lifetimeConditionKm = mapOf(
                    Condition.CONGESTION to 40_940.0 * 0.21,
                    Condition.SHORT_TRIP to 40_940.0 * 0.09,
                    Condition.HIGH_SPEED to 40_940.0 * 0.06,
                    Condition.HILLS to 40_940.0 * 0.03,
                    Condition.HARSH to 40_940.0 * 0.02,
                ),
                lifetimeDurationSec = 3_200_000.0,
                lifetimeIdleSec = 3_200_000.0 * 0.08,
                records = listOf(
                    ServiceRecord(MaintItem.ENGINE_OIL, 7_400.0, ago(32.0), Trust.SHOP, "1회차"),
                    ServiceRecord(MaintItem.ENGINE_OIL, 14_850.0, ago(26.0), Trust.SHOP),
                    ServiceRecord(MaintItem.CABIN_FILTER, 15_100.0, ago(25.5), Trust.OWNER),
                    ServiceRecord(MaintItem.ENGINE_OIL, 22_300.0, ago(19.0), Trust.CAR),
                    ServiceRecord(MaintItem.ENGINE_OIL, 29_750.0, ago(12.0), Trust.SHOP),
                    ServiceRecord(MaintItem.CABIN_FILTER, 30_050.0, ago(10.0), Trust.CAR),
                    ServiceRecord(MaintItem.AIR_CLEANER, 39_500.0, ago(4.0), Trust.SHOP),
                    ServiceRecord(MaintItem.ENGINE_OIL, 37_200.0, ago(5.0), Trust.SHOP),
                ),
                trips = seedTrips(now),
                warningCount = 0,
                ownerSince = ago(38.0),
                ownerCount = 1,
            )
        }
        // 지금 판정(일반)을 기준점으로 저장 → 시연 주행 후 바뀌는 순간에만 브리핑
        repo.update { s -> s.copy(lastVerdicts = MaintItem.entries.associateWith { false }) }
    }
}
