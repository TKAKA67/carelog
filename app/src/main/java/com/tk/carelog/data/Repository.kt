package com.tk.carelog.data

import com.tk.carelog.engine.Briefing
import com.tk.carelog.engine.Condition
import com.tk.carelog.engine.DrivingProfile
import com.tk.carelog.engine.DueStatus
import com.tk.carelog.engine.MaintItem
import com.tk.carelog.engine.Recommendation
import com.tk.carelog.engine.Thresholds
import com.tk.carelog.engine.TripSummary
import com.tk.carelog.engine.VerdictEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/** 주행 중 화면에 보여줄 실시간 값 */
data class LiveDrive(
    val speedKmh: Double = 0.0,
    val gear: String = "P",
    val tripKm: Double = 0.0,
    val elapsedSec: Double = 0.0,
    val congestedKm: Double = 0.0,
    val stops: Int = 0,
    val idleSec: Double = 0.0,
    val label: String = "",
    val simulated: Boolean = false,
)

/** 판매 준비·패스포트 화면용 수치 */
data class PassportStats(
    val shopCount: Int,
    val carCount: Int,
    val ownerCount: Int,
    val coverage: Double,
    val oilCompliant: Int,
    val oilTotal: Int,
    val warningCount: Int,
    val ownedMonths: Int,
) {
    val total: Int get() = shopCount + carCount + ownerCount
    val shopShare: Double get() = if (total > 0) shopCount.toDouble() / total else 0.0
}

data class Snapshot(
    val state: State,
    val profile: DrivingProfile,
    val lifetimeProfile: DrivingProfile,
    val recommendations: List<Recommendation>,
    val passport: PassportStats,
) {
    /** 홈 화면 맨 위 카드: 권장 시기가 지난 항목이 있으면 그것, 없으면 엔진오일 */
    val headline: Recommendation?
        get() = recommendations.firstOrNull { it.status == DueStatus.DUE }
            ?: recommendations.firstOrNull { it.verdict.item == MaintItem.ENGINE_OIL }

    fun rec(item: MaintItem) = recommendations.firstOrNull { it.verdict.item == item }
}

class Repository(private val store: Store) {
    private val _snapshot = MutableStateFlow(compute(store.load()))
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _briefings = MutableStateFlow<List<Briefing>>(emptyList())
    val briefings: StateFlow<List<Briefing>> = _briefings.asStateFlow()

    private val _live = MutableStateFlow(LiveDrive())
    val live: StateFlow<LiveDrive> = _live.asStateFlow()

    val state: State get() = _snapshot.value.state

    @Synchronized
    fun update(f: (State) -> State) {
        val next = f(state)
        store.save(next)
        _snapshot.value = compute(next)
    }

    fun setLive(l: LiveDrive) {
        _live.value = l
    }

    /** 트립 하나 저장 → 누적값 갱신 → 재판정 */
    @Synchronized
    fun addTrip(t: TripSummary, advanceOdometer: Boolean, evaluate: Boolean = true) {
        update { s ->
            val c = s.lifetimeConditionKm.toMutableMap()
            fun add(k: Condition, v: Double) { c[k] = (c[k] ?: 0.0) + v }
            if (t.shortTrip) add(Condition.SHORT_TRIP, t.distanceKm)
            add(Condition.CONGESTION, t.congestedKm)
            add(Condition.HIGH_SPEED, t.highSpeedKm)
            add(Condition.HILLS, t.hillyKm)
            add(Condition.HARSH, t.harshKm)
            s.copy(
                trips = (s.trips + t).takeLast(2_000),
                lifetimeRecordedKm = s.lifetimeRecordedKm + t.distanceKm,
                lifetimeConditionKm = c,
                lifetimeDurationSec = s.lifetimeDurationSec + t.durationSec,
                lifetimeIdleSec = s.lifetimeIdleSec + t.idleSec,
                odometerKm = if (advanceOdometer) s.odometerKm + t.distanceKm else s.odometerKm,
            )
        }
        if (evaluate) reevaluate()
    }

    /** 판정이 바뀐 항목은 브리핑으로 띄운다 */
    @Synchronized
    fun reevaluate() {
        val snap = _snapshot.value
        val news = VerdictEngine.briefings(snap.state.lastVerdicts, snap.recommendations, snap.state.settings.model)
        // 같은 화면에 여러 장이 쌓이지 않도록 엔진오일을 먼저, 나머지는 뒤로
        if (news.isNotEmpty()) {
            _briefings.value = (_briefings.value + news).sortedBy { if (it.item == MaintItem.ENGINE_OIL) 0 else 1 }
        }
        val now = snap.recommendations.associate { it.verdict.item to it.verdict.severe }
        if (now != snap.state.lastVerdicts) update { it.copy(lastVerdicts = now) }
    }

    fun dismissBriefing(b: Briefing) {
        _briefings.value = _briefings.value - b
    }

    fun clearBriefings() {
        _briefings.value = emptyList()
    }

    fun addRecord(item: MaintItem, trust: Trust, note: String = "") {
        update { s ->
            s.copy(
                records = s.records + ServiceRecord(item, s.odometerKm, System.currentTimeMillis(), trust, note),
                plannedService = if (s.plannedService?.startsWith(item.label) == true) null else s.plannedService,
            )
        }
    }

    /** 차량 주행거리계 값 반영. 앱이 꺼져 있던 동안 늘어난 거리는 미기록 구간으로 남긴다. */
    fun setOdometer(km: Double) {
        update { s ->
            val gap = if (s.odometerKm > 0 && km > s.odometerKm + 0.5) km - s.odometerKm else 0.0
            s.copy(odometerKm = km, unrecordedKm = s.unrecordedKm + gap)
        }
    }

    /** 명의 이전: 기록은 차에 남고 차주만 바뀐다 */
    fun transferOwnership() {
        update { s ->
            s.copy(
                ownerSince = System.currentTimeMillis(),
                ownerCount = s.ownerCount + 1,
                plannedService = null,
                settings = s.settings.copy(lastReceiptWon = 0),
            )
        }
    }

    private fun compute(s: State): Snapshot {
        val now = System.currentTimeMillis()
        val p = VerdictEngine.profile(s.trips, now, s.unrecordedKm, lifetimeKm = s.lifetimeRecordedKm)
        val life = lifetimeProfile(s)
        val verdicts = VerdictEngine.verdicts(s.settings.model, p, s.settings.declared)
        val recs = verdicts.map { v ->
            val last = s.records.filter { it.item == v.item }.maxByOrNull { it.time }
            VerdictEngine.recommendation(
                v,
                currentOdo = s.odometerKm,
                lastOdo = last?.odometerKm ?: s.settings.setupOdo,
                lastTime = last?.time ?: s.settings.setupTime,
                kmPerDay = p.kmPerDay,
                now = now,
            )
        }
        return Snapshot(s, p, life, recs, passport(s, life, now))
    }

    private fun lifetimeProfile(s: State): DrivingProfile {
        val km = s.lifetimeRecordedKm
        fun share(c: Condition) = if (km > 0) (s.lifetimeConditionKm[c] ?: 0.0) / km else 0.0
        val shares = Condition.entries.filter { it.sensed }.associateWith { c ->
            if (c == Condition.IDLING) {
                if (s.lifetimeDurationSec > 0) s.lifetimeIdleSec / s.lifetimeDurationSec else 0.0
            } else share(c)
        }
        return DrivingProfile(km, s.unrecordedKm, shares, 0.0, s.trips.size, km)
    }

    private fun passport(s: State, life: DrivingProfile, now: Long): PassportStats {
        // 엔진오일 판정 주기 준수: 보유 기간 운행 프로필 기준 주기 안에 교환했는지
        val oilVerdict = VerdictEngine.verdict(MaintItem.ENGINE_OIL, s.settings.model, life, s.settings.declared)
        val limit = (oilVerdict.intervalKm ?: 15_000) * 1.05
        val oil = s.records.filter { it.item == MaintItem.ENGINE_OIL }.sortedBy { it.odometerKm }
        var prev = s.settings.setupOdo
        var ok = 0
        oil.forEach { r ->
            if (r.odometerKm - prev <= limit) ok++
            prev = r.odometerKm
        }
        val totalKm = s.lifetimeRecordedKm + s.unrecordedKm
        val months = max(0L, (now - s.ownerSince) / (30L * 24 * 3600 * 1000)).toInt()
        return PassportStats(
            shopCount = s.records.count { it.trust == Trust.SHOP },
            carCount = s.records.count { it.trust == Trust.CAR },
            ownerCount = s.records.count { it.trust == Trust.OWNER },
            coverage = if (totalKm > 0) s.lifetimeRecordedKm / totalKm else 1.0,
            oilCompliant = ok,
            oilTotal = oil.size,
            warningCount = s.warningCount,
            ownedMonths = months,
        )
    }

    companion object {
        /** '자주' 기준 (화면 표시용) */
        val FREQUENT: Double get() = Thresholds.FREQUENT_SHARE
    }
}
