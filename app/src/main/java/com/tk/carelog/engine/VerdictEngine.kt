package com.tk.carelog.engine

import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt

/** 최근 기간의 조건별 비율 */
data class DrivingProfile(
    val recordedKm: Double,
    val unrecordedKm: Double,
    val shares: Map<Condition, Double>,
    val kmPerDay: Double,
    val tripCount: Int,
    /** 설치 이후 누적 기록 거리 (판정 근거가 충분한지 판단) */
    val lifetimeKm: Double = recordedKm,
)

data class ItemVerdict(
    val item: MaintItem,
    val severe: Boolean,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val reasons: List<String>,
    /** 음성 브리핑용 한 문장 */
    val spoken: String? = null,
)

enum class DueStatus { OK, SOON, DUE }

/** 화면에 띄우는 "권장" 한 줄 */
data class Recommendation(
    val verdict: ItemVerdict,
    val lastServiceOdo: Double,
    val lastServiceTime: Long,
    val remainingKm: Double?,
    val dueTime: Long?,
    val predictedDays: Int?,
    val status: DueStatus,
)

/** 판정이 바뀌었을 때 띄우는 브리핑 */
data class Briefing(
    val item: MaintItem,
    val fromKm: Int?,
    val toKm: Int?,
    val toSevere: Boolean,
    val topReasons: List<String>,
    val remainingKm: Double?,
    val text: String,
)

object VerdictEngine {
    private const val DAY_MS = 24L * 3600 * 1000

    fun profile(trips: List<TripSummary>, now: Long, unrecordedKm: Double, days: Int = 30, lifetimeKm: Double? = null): DrivingProfile {
        val from = now - days * DAY_MS
        val recent = trips.filter { it.endTime >= from }
        val km = recent.sumOf { it.distanceKm }
        val sec = recent.sumOf { it.durationSec }
        fun share(x: Double) = if (km > 0) x / km else 0.0
        val shares = mapOf(
            Condition.SHORT_TRIP to share(recent.filter { it.shortTrip }.sumOf { it.distanceKm }),
            Condition.CONGESTION to share(recent.sumOf { it.congestedKm }),
            Condition.IDLING to if (sec > 0) recent.sumOf { it.idleSec } / sec else 0.0,
            Condition.HIGH_SPEED to share(recent.sumOf { it.highSpeedKm }),
            Condition.HILLS to share(recent.sumOf { it.hillyKm }),
            Condition.HARSH to share(recent.sumOf { it.harshKm }),
        )
        val firstTime = recent.minOfOrNull { it.endTime } ?: now
        val spanDays = max(1.0, (now - firstTime).toDouble() / DAY_MS)
        return DrivingProfile(km, unrecordedKm, shares, km / spanDays, recent.size, lifetimeKm ?: km)
    }

    fun verdicts(
        model: VehicleModel,
        p: DrivingProfile,
        declared: Set<Condition>,
    ): List<ItemVerdict> = MaintItem.entries.map { item -> verdict(item, model, p, declared) }

    private fun pct(x: Double) = (x * 100).roundToInt()

    fun verdict(item: MaintItem, model: VehicleModel, p: DrivingProfile, declared: Set<Condition>): ItemVerdict {
        val s = item.schedule(model)
        val reasons = mutableListOf<String>()
        var severe = false
        var spoken: String? = null
        val th = Thresholds.FREQUENT_SHARE

        for (c in item.triggers) {
            if (!c.sensed) {
                if (c in declared) { severe = true; reasons += "${c.label}: 차주 응답 '해당'" }
                continue
            }
            val sh = p.shares[c] ?: 0.0
            when {
                sh >= th -> {
                    severe = true
                    reasons += "${c.label} ${pct(sh)}% / 기준 ${pct(th)}%"
                    if (spoken == null) spoken = "${c.label} 주행이 ${pct(sh)}퍼센트로, 기준인 ${pct(th)}퍼센트보다 많아요"
                }
                sh >= th - Thresholds.UNCERTAIN_BAND -> {
                    severe = true; reasons += "${c.label} ${pct(sh)}% — 기준 근처라 가혹 주기 권장"
                }
            }
        }
        // 미기록 비율은 보유 기간 전체 기준으로 본다
        val total = p.lifetimeKm + p.unrecordedKm
        if (p.lifetimeKm < Thresholds.MIN_RECORDED_KM && item.triggers.any { it.sensed }) {
            severe = true
            reasons += "누적 기록 ${p.lifetimeKm.roundToInt()}km — 판정 근거가 쌓일 때까지 가혹 주기 기본 적용"
        } else if (total > 0 && p.unrecordedKm / total > th && item.triggers.any { it.sensed }) {
            severe = true
            reasons += "미기록 구간 ${pct(p.unrecordedKm / total)}% — 가혹 주기 권장"
        }
        if (!severe) {
            val main = item.triggers.filter { it.sensed }
                .maxByOrNull { p.shares[it] ?: 0.0 }
            if (main != null) reasons += "가장 높은 조건: ${main.label} ${pct(p.shares[main] ?: 0.0)}% (기준 ${pct(th)}% 미만)"
        }

        // 가혹인데 매뉴얼이 숫자 없이 "수시 점검"이면 일반 주기를 상한으로 두고 사유를 붙임
        val km = if (severe) (s.severeKm ?: s.normalKm) else s.normalKm
        val months = if (severe) (s.severeMonths ?: s.normalMonths) else s.normalMonths
        if (severe && s.severeKm == null && s.normalKm != null) reasons += "매뉴얼: 가혹 조건에서는 수시 점검·조기 교환"
        return ItemVerdict(item, severe, km, months, reasons, spoken)
    }

    fun recommendation(
        v: ItemVerdict,
        currentOdo: Double,
        lastOdo: Double,
        lastTime: Long,
        kmPerDay: Double,
        now: Long,
    ): Recommendation {
        val remaining = v.intervalKm?.let { lastOdo + it - currentOdo }
        val dueTime = v.intervalMonths?.let {
            Calendar.getInstance().apply { timeInMillis = lastTime; add(Calendar.MONTH, it) }.timeInMillis
        }
        val daysByKm = if (remaining != null && kmPerDay > 0.1) (remaining / kmPerDay).toInt() else null
        val daysByTime = dueTime?.let { ((it - now) / DAY_MS).toInt() }
        val predicted = listOfNotNull(daysByKm, daysByTime).minOrNull()
        val status = when {
            (remaining != null && remaining <= 0) || (daysByTime != null && daysByTime <= 0) -> DueStatus.DUE
            (remaining != null && remaining <= 1_000) || (predicted != null && predicted <= 30) -> DueStatus.SOON
            else -> DueStatus.OK
        }
        return Recommendation(v, lastOdo, lastTime, remaining, dueTime, predicted, status)
    }

    /** 이전 판정과 비교해 바뀐 항목에 대한 브리핑 문장 생성 */
    fun briefings(prev: Map<MaintItem, Boolean>, recs: List<Recommendation>, model: VehicleModel): List<Briefing> =
        recs.mapNotNull { r ->
            val v = r.verdict
            val before = prev[v.item] ?: return@mapNotNull null
            if (before == v.severe) return@mapNotNull null
            val s = v.item.schedule(model)
            val from = if (before) s.severeKm ?: s.normalKm else s.normalKm
            val rem = r.remainingKm?.let { "남은 거리는 ${"%,d".format(max(0.0, it).roundToInt())}킬로미터예요." } ?: ""
            val reasons = v.reasons.take(2)
            val text = if (v.severe) {
                "최근 30일 ${v.spoken ?: "운행 조건이 바뀌었어요"}. 그래서 ${v.item.label} 주기가 가혹조건으로 바뀌었어요. $rem 정비 계획을 추가해 둘까요?"
            } else {
                "최근 운행이 가혹조건 기준 아래라 ${v.item.label} 주기는 일반 주기(${v.intervalKm?.let { "%,d".format(it) + "km" } ?: "매뉴얼 기준"})로 관리하면 돼요. 더 자주 가지 않으셔도 돼요."
            }
            Briefing(v.item, from, v.intervalKm, v.severe, reasons, r.remainingKm, text)
        }

    /** 연간 절약 횟수 = 연간 주행거리 × (1/기존 주기 − 1/판정 주기) */
    fun yearlySavedChanges(kmPerYear: Double, habitKm: Int, verdictKm: Int?): Double? {
        if (verdictKm == null || habitKm <= 0) return null
        return kmPerYear * (1.0 / habitKm - 1.0 / verdictKm)
    }
}
