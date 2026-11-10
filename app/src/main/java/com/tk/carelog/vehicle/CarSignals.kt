package com.tk.carelog.vehicle

import android.annotation.SuppressLint
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log

/**
 * 차속·기어(AAOS 표준 차량 속성)와 위치(고도, GPX 재생 시 속도 대체)를 모읍니다.
 * 에뮬레이터 Virtual Console이 차속을 지원하지 않으면, Extended controls의 GPX 재생 속도를 대신 사용합니다.
 */
class CarSignals(private val context: Context) {
    @Volatile var carSpeedKmh: Double? = null
        private set
    @Volatile var locationSpeedKmh: Double? = null
        private set
    @Volatile var altitudeM: Double? = null
        private set
    @Volatile var gearPark: Boolean? = null
        private set

    /** 차속 우선, 없으면 위치 속도 */
    val speedKmh: Double? get() = carSpeedKmh ?: locationSpeedKmh

    private var car: Car? = null
    private var cpm: CarPropertyManager? = null
    private var locationManager: LocationManager? = null

    private val propCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                    (value.value as? Float)?.let { carSpeedKmh = (it * 3.6).coerceAtLeast(0.0) } // m/s → km/h
                VehiclePropertyIds.GEAR_SELECTION ->
                    (value.value as? Int)?.let { gearPark = it == VehicleGear.GEAR_PARK }
            }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {}
    }

    private val locListener = LocationListener { loc: Location ->
        if (loc.hasSpeed()) locationSpeedKmh = loc.speed * 3.6
        if (loc.hasAltitude()) altitudeM = loc.altitude
    }

    @SuppressLint("MissingPermission")
    fun start() {
        try {
            car = Car.createCar(context)
            cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            cpm?.registerCallback(propCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            cpm?.registerCallback(propCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (t: Throwable) {
            Log.w(TAG, "차량 속성 구독 실패 (권한/지원 여부 확인)", t)
        }
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
        } catch (t: Throwable) {
            Log.w(TAG, "위치 구독 실패", t)
        }
    }

    fun stop() {
        try { cpm?.unregisterCallback(propCallback) } catch (_: Throwable) {}
        try { locationManager?.removeUpdates(locListener) } catch (_: Throwable) {}
        try { car?.disconnect() } catch (_: Throwable) {}
    }

    companion object { private const val TAG = "CareLog/Signals" }
}
