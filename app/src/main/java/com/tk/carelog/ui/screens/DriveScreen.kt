package com.tk.carelog.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.Snapshot
import com.tk.carelog.demo.DemoScenarios
import com.tk.carelog.engine.TripSummary
import com.tk.carelog.ui.components.Panel
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.PulseDot
import com.tk.carelog.ui.components.SectionLabel
import com.tk.carelog.ui.components.Speedometer
import com.tk.carelog.ui.components.Stat
import com.tk.carelog.ui.components.Tag
import com.tk.carelog.ui.components.date
import com.tk.carelog.ui.components.pct
import com.tk.carelog.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DriveScreen(app: CareLogApp, snap: Snapshot) {
    val live by app.repo.live.collectAsState()
    val running by app.demo.running.collectAsState()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("지금 주행", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    if (live.gear != "P") PulseDot(Palette.Mint, 10.dp)
                    Spacer(Modifier.weight(1f))
                    if (live.simulated) Tag("시뮬레이션", Palette.Sub, 14.sp)
                }
                if (live.label.isNotEmpty()) Text(live.label, color = Palette.Sub, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Speedometer(live.speedKmh, live.gear, size = 250.dp)
                    Spacer(Modifier.width(28.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat("%.1f km".format(live.tripKm), "이번 트립")
                        Stat(
                            if (live.tripKm > 0.3) pct((live.congestedKm / live.tripKm).coerceIn(0.0, 1.0)) else "—",
                            "정체 구간 비율",
                            color = if (live.tripKm > 0.3 && live.congestedKm / live.tripKm >= 0.3) Palette.Amber else Palette.Text,
                        )
                        Stat("${live.stops}회", "정차")
                        Stat("${(live.idleSec / 60).roundToInt()}분", "공회전")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("주행 중에는 화면 대신 기록만 합니다 · 경로·위치는 저장하지 않습니다", color = Palette.Dim, fontSize = 14.sp)
            }
            if (snap.state.settings.demoMode) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionLabel("시연 주행")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DemoScenarios.Script.entries.forEach { s ->
                            PillButton(
                                s.title,
                                onClick = { app.demo.play(s) },
                                filled = s == DemoScenarios.Script.CITY,
                                color = if (s == DemoScenarios.Script.CITY) Palette.Amber else Palette.Mint,
                                enabled = running == null,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (running != null) "$running 재생 중…" else "첫 트립은 실시간으로 보여주고, 그 주의 나머지 주행은 한 번에 반영합니다",
                        color = Palette.Sub, fontSize = 14.sp,
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(snap.profile, "최근 30일 운행 조건")
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("최근 트립")
                Spacer(Modifier.height(8.dp))
                val recent = snap.state.trips.sortedByDescending { it.endTime }.take(6)
                if (recent.isEmpty()) {
                    Text("최근 30일 기록이 없어요. 주행하면 여기에 쌓입니다.", color = Palette.Sub, fontSize = 16.sp)
                }
                recent.forEach { TripRow(it) }
            }
        }
    }
}

@Composable
private fun TripRow(t: TripSummary) {
    val time = SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(t.endTime))
    val cong = if (t.distanceKm > 0) t.congestedKm / t.distanceKm else 0.0
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Palette.Sub, fontSize = 16.sp, modifier = Modifier.width(92.dp))
        Text("%.1fkm".format(t.distanceKm), color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        Text("정체 ${pct(cong)}", color = Palette.Sub, fontSize = 16.sp, modifier = Modifier.weight(1f))
        when {
            cong >= 0.3 -> Tag("정체", Palette.Amber, 14.sp)
            t.shortTrip -> Tag("단거리", Palette.Amber, 14.sp)
            t.distanceKm > 0 && t.highSpeedKm / t.distanceKm > 0.3 -> Tag("고속", Palette.Blue, 14.sp)
            else -> Tag("일반", Palette.Mint, 14.sp)
        }
    }
}
