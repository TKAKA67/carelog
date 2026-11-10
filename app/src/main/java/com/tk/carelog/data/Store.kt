package com.tk.carelog.data

import android.content.Context
import com.tk.carelog.engine.Condition
import com.tk.carelog.engine.MaintItem
import com.tk.carelog.engine.TripSummary
import com.tk.carelog.engine.VehicleModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 기록 신뢰 등급 */
enum class Trust(val label: String) {
    SHOP("정비소 확인"),
    CAR("차주 기록"),
    OWNER("수기 입력"),
}

/** 정비 기록. 추가만 하고 수정·삭제는 하지 않는다. */
data class ServiceRecord(
    val item: MaintItem,
    val odometerKm: Double,
    val time: Long,
    val trust: Trust,
    val note: String = "",
)

data class Settings(
    val model: VehicleModel = VehicleModel.GN7_GASOLINE,
    val habitOilKm: Int = 5_000,
    val lastReceiptWon: Int = 0,
    val declared: Set<Condition> = emptySet(),
    val demoMode: Boolean = true,
    val voiceOn: Boolean = true,
    /** 기록 시작 시점 (이전 정비 기록이 없을 때 기준점) */
    val setupOdo: Double = 0.0,
    val setupTime: Long = System.currentTimeMillis(),
)

data class State(
    val settings: Settings = Settings(),
    val odometerKm: Double = 0.0,
    val unrecordedKm: Double = 0.0,
    val lifetimeRecordedKm: Double = 0.0,
    /** 보유 기간 전체의 조건별 누적 거리 (패스포트 운행 프로필용) */
    val lifetimeConditionKm: Map<Condition, Double> = emptyMap(),
    val lifetimeDurationSec: Double = 0.0,
    val lifetimeIdleSec: Double = 0.0,
    val trips: List<TripSummary> = emptyList(),
    val records: List<ServiceRecord> = emptyList(),
    /** 직전 판정 (true = 가혹). 판정이 바뀌는 순간을 잡기 위해 보관 */
    val lastVerdicts: Map<MaintItem, Boolean> = emptyMap(),
    val warningCount: Int = 0,
    val oilLevelState: String? = null,
    val plannedService: String? = null,
    val ownerSince: Long = System.currentTimeMillis(),
    val ownerCount: Int = 1,
)

/** JSON 파일 하나에 저장. 좌표·경로는 애초에 저장하지 않는다. */
class Store(context: Context) {
    private val file = File(context.filesDir, "carelog.json")

    fun load(): State = try {
        if (file.exists()) decode(JSONObject(file.readText())) else State()
    } catch (e: Exception) {
        State()
    }

    fun save(s: State) {
        file.writeText(encode(s).toString())
    }

    private fun encode(s: State): JSONObject = JSONObject().apply {
        put("settings", JSONObject().apply {
            put("model", s.settings.model.name)
            put("habitOilKm", s.settings.habitOilKm)
            put("lastReceiptWon", s.settings.lastReceiptWon)
            put("declared", JSONArray(s.settings.declared.map { it.name }))
            put("demoMode", s.settings.demoMode)
            put("voiceOn", s.settings.voiceOn)
            put("setupOdo", s.settings.setupOdo)
            put("setupTime", s.settings.setupTime)
        })
        put("odometerKm", s.odometerKm)
        put("unrecordedKm", s.unrecordedKm)
        put("lifetimeRecordedKm", s.lifetimeRecordedKm)
        put("lifetimeConditionKm", JSONObject().apply { s.lifetimeConditionKm.forEach { (k, v) -> put(k.name, v) } })
        put("lifetimeDurationSec", s.lifetimeDurationSec)
        put("lifetimeIdleSec", s.lifetimeIdleSec)
        put("trips", JSONArray(s.trips.map { t ->
            JSONObject().apply {
                put("endTime", t.endTime); put("distanceKm", t.distanceKm); put("durationSec", t.durationSec)
                put("congestedKm", t.congestedKm); put("idleSec", t.idleSec); put("highSpeedKm", t.highSpeedKm)
                put("hillyKm", t.hillyKm); put("harshKm", t.harshKm); put("stops", t.stops); put("simulated", t.simulated)
            }
        }))
        put("records", JSONArray(s.records.map { r ->
            JSONObject().apply {
                put("item", r.item.name); put("odometerKm", r.odometerKm); put("time", r.time)
                put("trust", r.trust.name); put("note", r.note)
            }
        }))
        put("lastVerdicts", JSONObject().apply { s.lastVerdicts.forEach { (k, v) -> put(k.name, v) } })
        put("warningCount", s.warningCount)
        put("oilLevelState", s.oilLevelState ?: JSONObject.NULL)
        put("plannedService", s.plannedService ?: JSONObject.NULL)
        put("ownerSince", s.ownerSince)
        put("ownerCount", s.ownerCount)
    }

    private fun decode(o: JSONObject): State {
        val so = o.optJSONObject("settings") ?: JSONObject()
        val declared = so.optJSONArray("declared")?.let { a ->
            (0 until a.length()).mapNotNull { i -> runCatching { Condition.valueOf(a.getString(i)) }.getOrNull() }.toSet()
        } ?: emptySet()
        val settings = Settings(
            model = runCatching { VehicleModel.valueOf(so.optString("model")) }.getOrDefault(VehicleModel.GN7_GASOLINE),
            habitOilKm = so.optInt("habitOilKm", 5_000),
            lastReceiptWon = so.optInt("lastReceiptWon", 0),
            declared = declared,
            demoMode = so.optBoolean("demoMode", true),
            voiceOn = so.optBoolean("voiceOn", true),
            setupOdo = so.optDouble("setupOdo", 0.0),
            setupTime = so.optLong("setupTime", System.currentTimeMillis()),
        )
        val trips = o.optJSONArray("trips")?.let { a ->
            (0 until a.length()).map { i ->
                val t = a.getJSONObject(i)
                TripSummary(
                    endTime = t.getLong("endTime"), distanceKm = t.getDouble("distanceKm"),
                    durationSec = t.getDouble("durationSec"), congestedKm = t.getDouble("congestedKm"),
                    idleSec = t.getDouble("idleSec"), highSpeedKm = t.getDouble("highSpeedKm"),
                    hillyKm = t.getDouble("hillyKm"), harshKm = t.getDouble("harshKm"),
                    stops = t.getInt("stops"), simulated = t.optBoolean("simulated", false),
                )
            }
        } ?: emptyList()
        val records = o.optJSONArray("records")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val r = a.getJSONObject(i)
                runCatching {
                    ServiceRecord(
                        MaintItem.valueOf(r.getString("item")), r.getDouble("odometerKm"), r.getLong("time"),
                        Trust.valueOf(r.getString("trust")), r.optString("note", ""),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        val lv = o.optJSONObject("lastVerdicts")
        val lastVerdicts = buildMap<MaintItem, Boolean> {
            lv?.keys()?.forEach { k -> runCatching { put(MaintItem.valueOf(k), lv.getBoolean(k)) } }
        }
        val lc = o.optJSONObject("lifetimeConditionKm")
        val lifetimeCond = buildMap<Condition, Double> {
            lc?.keys()?.forEach { k -> runCatching { put(Condition.valueOf(k), lc.getDouble(k)) } }
        }
        return State(
            settings = settings,
            odometerKm = o.optDouble("odometerKm", 0.0),
            unrecordedKm = o.optDouble("unrecordedKm", 0.0),
            lifetimeRecordedKm = o.optDouble("lifetimeRecordedKm", 0.0),
            lifetimeConditionKm = lifetimeCond,
            lifetimeDurationSec = o.optDouble("lifetimeDurationSec", 0.0),
            lifetimeIdleSec = o.optDouble("lifetimeIdleSec", 0.0),
            trips = trips,
            records = records,
            lastVerdicts = lastVerdicts,
            warningCount = o.optInt("warningCount", 0),
            oilLevelState = if (o.isNull("oilLevelState")) null else o.optString("oilLevelState"),
            plannedService = if (o.isNull("plannedService")) null else o.optString("plannedService"),
            ownerSince = o.optLong("ownerSince", System.currentTimeMillis()),
            ownerCount = o.optInt("ownerCount", 1),
        )
    }
}
