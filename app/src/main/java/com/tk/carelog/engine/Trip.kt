package com.tk.carelog.engine

import kotlin.math.abs

/**
 * 트립 요약. 경로·좌표는 저장하지 않고, 조건별 거리/시간만 남깁니다 (개인정보 원칙).
 */
data class TripSummary(
    val endTime: Long,
    val distanceKm: Double,
    val durationSec: Double,
    val congestedKm: Double,
    val idleSec: Double,
    val highSpeedKm: Double,
    val hillyKm: Double,
    val harshKm: Double,
    val stops: Int,
    val simulated: Boolean = false,
) {
    val shortTrip: Boolean get() = distanceKm <= Thresholds.SHORT_TRIP_KM
}

object Thresholds {
    /** 매뉴얼의 단거리 기준 (Hyundai 매뉴얼 통상 8km — 차종 매뉴얼로 확인) */
    const val SHORT_TRIP_KM = 8.0
    /** 1분 구간 평균 속도가 이 값 미만이면 정체 구간 (CareLog 자체 기준) */
    const val CONGESTION_AVG_KMH = 20.0
    /** 고속 주행 기준 속도. 취급설명서 가혹 조건: "고속주행(170km/h)의 빈도가 높은 경우" */
    const val HIGH_SPEED_KMH = 170.0
    /** km당 누적 상승 고도가 이 값 이상이면 산길·언덕길 트립 */
    const val HILLY_CLIMB_M_PER_KM = 20.0
    /** 초당 속도 변화가 이 값 이상이면 급가감속 1회 (≈0.23g) */
    const val HARSH_KMH_PER_SEC = 8.0
    /** 10km당 급가감속이 이 횟수 이상이면 급가감속 트립 */
    const val HARSH_PER_10KM = 2.0
    /** "자주"의 정의: 최근 30일 기록 주행거리 중 비율 */
    const val FREQUENT_SHARE = 0.30
    /** 기준 ±5%p 이내면 가혹 쪽으로 판정 */
    const val UNCERTAIN_BAND = 0.05
    /** 이 거리 미만으로 기록되면 판정 근거 부족 → 가혹 기본 */
    const val MIN_RECORDED_KM = 300.0
}

/**
 * 1초 단위 신호(차속, 고도)를 받아 트립 요약을 만듭니다.
 */
class TripAccumulator(private val simulated: Boolean = false) {
    private var lastT: Long? = null
    private var lastSpeed = 0.0
    private var lastAlt: Double? = null

    private var distKm = 0.0
    private var durSec = 0.0
    private var idleSec = 0.0
    private var highKm = 0.0
    private var climbM = 0.0
    private var harsh = 0
    private var stops = 0
    private var moving = false

    // 1분 창
    private var winKm = 0.0
    private var winSec = 0.0
    private var congestedKm = 0.0

    var endTime: Long = 0L
        private set

    val distanceSoFarKm: Double get() = distKm
    val idleSoFarSec: Double get() = idleSec

    /** 주행 중 화면용: 지금까지 닫힌 1분 구간 기준 정체 거리 */
    fun finishPreviewCongestedKm(): Double = congestedKm

    fun add(tMs: Long, speedKmh: Double, altitudeM: Double? = null) {
        val prevT = lastT
        lastT = tMs
        endTime = tMs
        if (prevT == null) {
            lastSpeed = speedKmh; lastAlt = altitudeM; return
        }
        val dt = ((tMs - prevT) / 1000.0).coerceIn(0.0, 10.0)
        if (dt <= 0.0) return
        val v = (speedKmh + lastSpeed) / 2.0
        val dKm = v * dt / 3600.0

        distKm += dKm
        durSec += dt
        if (speedKmh < 1.0) idleSec += dt
        if (speedKmh >= Thresholds.HIGH_SPEED_KMH) highKm += dKm
        if (abs(speedKmh - lastSpeed) / dt >= Thresholds.HARSH_KMH_PER_SEC) harsh++

        if (speedKmh > 10.0) moving = true
        if (moving && speedKmh < 3.0) { stops++; moving = false }

        winKm += dKm; winSec += dt
        if (winSec >= 60.0) closeWindow()

        if (altitudeM != null) {
            val pa = lastAlt
            if (pa != null && altitudeM > pa) climbM += (altitudeM - pa)
            lastAlt = altitudeM
        }
        lastSpeed = speedKmh
    }

    private fun closeWindow() {
        if (winSec > 0) {
            val avg = winKm / (winSec / 3600.0)
            if (avg < Thresholds.CONGESTION_AVG_KMH) congestedKm += winKm
        }
        winKm = 0.0; winSec = 0.0
    }

    /**
     * @param odometerKm 차량 주행거리계로 잰 트립 거리(있으면 이 값을 우선 사용하고 나머지를 비례 보정)
     */
    fun finish(odometerKm: Double? = null): TripSummary {
        closeWindow()
        val measured = distKm
        val dist = if (odometerKm != null && odometerKm > 0.05) odometerKm else measured
        val k = if (measured > 0.0) dist / measured else 1.0
        val hilly = if (dist > 0 && climbM / dist >= Thresholds.HILLY_CLIMB_M_PER_KM) dist else 0.0
        val harshTrip = if (dist > 0 && harsh / dist * 10.0 >= Thresholds.HARSH_PER_10KM) dist else 0.0
        return TripSummary(
            endTime = endTime,
            distanceKm = dist,
            durationSec = durSec,
            congestedKm = (congestedKm * k).coerceAtMost(dist),
            idleSec = idleSec,
            highSpeedKm = (highKm * k).coerceAtMost(dist),
            hillyKm = hilly,
            harshKm = harshTrip,
            stops = stops,
            simulated = simulated,
        )
    }
}
