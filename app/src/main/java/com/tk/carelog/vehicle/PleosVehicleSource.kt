package com.tk.carelog.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle

/**
 * Pleos Vehicle SDK 래퍼.
 * 문서(Vehicle SDK 2.3.2) 기준 호출 형태:
 *   val vehicle = Vehicle(context); vehicle.initialize()
 *   vehicle.getOdometer().getCurrentDistanceDriven(onComplete = { ... }, onFailed = { ... })
 *   vehicle.getSafety().getEngineOilLevelState(onComplete = { ... }, onFailed = { ... })
 *
 * 값의 정확한 타입(Float? / Int? / enum)은 Android Studio 자동완성으로 확인하세요.
 * 여기서는 타입에 상관없이 컴파일되도록 Number/toString 으로만 다룹니다.
 * 빌드 오류가 나면 이 파일만 고치면 됩니다 (나머지 앱은 이 클래스에만 의존).
 */
class PleosVehicleSource(private val context: Context) {
    private var vehicle: Vehicle? = null
    var lastError: String? = null
        private set

    val connected: Boolean get() = vehicle != null

    fun start() {
        try {
            vehicle = Vehicle(context).also { it.initialize() }
        } catch (t: Throwable) {
            lastError = "Vehicle 초기화 실패: ${t.message}"
            Log.w(TAG, lastError, t)
            vehicle = null
        }
    }

    /** 총 주행거리(km) */
    fun readOdometer(cb: (Double?) -> Unit) {
        val v = vehicle ?: return cb(null)
        try {
            v.getOdometer().getCurrentDistanceDriven(
                onComplete = { value -> cb((value as? Number)?.toDouble()) },
                onFailed = { e -> lastError = "주행거리 읽기 실패: ${e.message}"; cb(null) },
            )
        } catch (t: Throwable) {
            lastError = "주행거리 API 오류: ${t.message}"; cb(null)
        }
    }

    /** 엔진오일 레벨 상태 (정상/부족 등). 원시 값을 문자열로 돌려줍니다. */
    fun readEngineOilLevel(cb: (String?) -> Unit) {
        val v = vehicle ?: return cb(null)
        try {
            v.getSafety().getEngineOilLevelState(
                onComplete = { value -> cb(value?.toString()) },
                onFailed = { _ -> cb(null) },
            )
        } catch (t: Throwable) {
            cb(null)
        }
    }

    fun release() {
        try { vehicle?.release() } catch (_: Throwable) {}
        vehicle = null
    }

    companion object { private const val TAG = "CareLog/Pleos" }
}
