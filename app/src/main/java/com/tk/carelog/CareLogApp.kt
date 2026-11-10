package com.tk.carelog

import android.app.Application
import com.tk.carelog.data.Repository
import com.tk.carelog.data.Store
import com.tk.carelog.demo.DemoScenarios
import com.tk.carelog.vehicle.PleosVehicleSource
import com.tk.carelog.voice.Speaker

class CareLogApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var vehicle: PleosVehicleSource
        private set
    lateinit var speaker: Speaker
        private set
    lateinit var demo: DemoScenarios
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(Store(this))
        vehicle = PleosVehicleSource(this).also { it.start() }
        speaker = Speaker(this)
        demo = DemoScenarios(repo)
        // 처음 설치했거나 운행 기록이 비어 있으면 시연용 차량 상태로 시작
        if (repo.state.settings.demoMode && (repo.state.records.isEmpty() || repo.state.trips.isEmpty())) demo.reset()
    }
}
