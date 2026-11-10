package com.tk.carelog.engine

/**
 * 매뉴얼의 가혹조건. sensed=false 는 센서로 알 수 없어 차주에게 한 번 묻는 조건.
 */
enum class Condition(val label: String, val sensed: Boolean) {
    SHORT_TRIP("단거리 반복", true),
    CONGESTION("교통체증·정지출발", true),
    IDLING("장시간 공회전", true),
    HIGH_SPEED("고속 주행", true),
    HILLS("산길·언덕길", true),
    HARSH("급가속·급감속", true),
    DUSTY("먼지·비포장 도로", false),
    TOWING("견인", false),
    COMMERCIAL("영업용 운행", false),
}

enum class VehicleModel(val label: String) {
    GN7_GASOLINE("그랜저 GN7 가솔린"),
    GN7_HYBRID("그랜저 GN7 하이브리드"),
    NX4("투싼 NX4"),
}

/**
 * 정비 항목과 매뉴얼 주기.
 * normalKm == null  → 일반 조건에서는 정기 교환 없음(점검만)
 * severeKm == null  → 가혹 조건에서 매뉴얼이 숫자 대신 "수시 점검·조기 교환"으로 안내
 *
 * ⚠ 엔진오일 수치는 2026 GN7/NX4 매뉴얼로 확인한 값.
 *   나머지 항목 수치는 제출 전 해당 차종 매뉴얼 "정기 점검 및 교환 주기" 표로 반드시 다시 확인할 것.
 */
data class Schedule(
    val normalKm: Int?, val normalMonths: Int?,
    val severeKm: Int?, val severeMonths: Int?,
)

enum class MaintItem(val label: String, val triggers: Set<Condition>) {
    ENGINE_OIL("엔진오일·필터", Condition.entries.toSet()),
    TRANSMISSION_FLUID(
        "자동변속기 오일",
        setOf(
            Condition.SHORT_TRIP, Condition.CONGESTION, Condition.IDLING, Condition.HILLS,
            Condition.HIGH_SPEED, Condition.TOWING, Condition.COMMERCIAL, Condition.DUSTY,
        ),
    ),
    AIR_CLEANER("에어클리너 필터", setOf(Condition.DUSTY)),
    CABIN_FILTER("에어컨 필터", setOf(Condition.DUSTY));

    fun schedule(model: VehicleModel): Schedule = when (this) {
        ENGINE_OIL -> when (model) {
            VehicleModel.GN7_GASOLINE -> Schedule(15_000, 12, 7_500, 6)
            VehicleModel.GN7_HYBRID -> Schedule(10_000, 12, 5_000, 6)
            VehicleModel.NX4 -> Schedule(10_000, 12, 5_000, 6)
        }
        TRANSMISSION_FLUID -> Schedule(null, null, 100_000, null)   // TODO 매뉴얼 재확인
        AIR_CLEANER -> Schedule(40_000, null, null, null)           // TODO 매뉴얼 재확인
        CABIN_FILTER -> Schedule(15_000, 12, null, null)            // TODO 매뉴얼 재확인
    }
}
