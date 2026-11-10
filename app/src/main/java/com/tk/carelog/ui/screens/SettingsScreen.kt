package com.tk.carelog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.Snapshot
import com.tk.carelog.engine.Condition
import com.tk.carelog.engine.VehicleModel
import com.tk.carelog.ui.components.Panel
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.SectionLabel
import com.tk.carelog.ui.theme.Palette

@Composable
fun SettingsScreen(app: CareLogApp, snap: Snapshot) {
    val s = snap.state.settings
    val repo = app.repo
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("차종 (차량 제원 정보로 자동 인식 예정)")
                Spacer(Modifier.height(10.dp))
                ChoiceRow(VehicleModel.entries.map { it.label }, VehicleModel.entries.indexOf(s.model)) { i ->
                    repo.update { st -> st.copy(settings = st.settings.copy(model = VehicleModel.entries[i])) }
                    repo.reevaluate()
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("센서로 알 수 없는 운행 조건 — 해당하면 선택")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Condition.entries.filter { !it.sensed }.forEach { c ->
                        val on = c in s.declared
                        Chip(c.label, on) {
                            repo.update { st ->
                                val d = if (on) st.settings.declared - c else st.settings.declared + c
                                st.copy(settings = st.settings.copy(declared = d))
                            }
                            repo.reevaluate()
                        }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("지금까지 엔진오일을 갈던 주기")
                Spacer(Modifier.height(10.dp))
                val habits = listOf(5_000, 7_500, 10_000, 15_000)
                ChoiceRow(habits.map { "%,dkm".format(it) }, habits.indexOf(s.habitOilKm)) { i ->
                    repo.update { st -> st.copy(settings = st.settings.copy(habitOilKm = habits[i])) }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel("최근 엔진오일 교환 영수증 금액")
                Spacer(Modifier.height(10.dp))
                val won = listOf(0, 60_000, 80_000, 100_000)
                ChoiceRow(won.map { if (it == 0) "입력 안 함" else "%,d원".format(it) }, won.indexOf(s.lastReceiptWon)) { i ->
                    repo.update { st -> st.copy(settings = st.settings.copy(lastReceiptWon = won[i])) }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Toggle("음성 안내", "P단에서 정비 알림을 읽어줍니다", s.voiceOn) { v ->
                    repo.update { st -> st.copy(settings = st.settings.copy(voiceOn = v)) }
                }
                Spacer(Modifier.height(12.dp))
                Toggle("시연 모드", "시연용 차량 데이터와 시연 주행 버튼을 사용합니다", s.demoMode) { v ->
                    repo.update { st -> st.copy(settings = st.settings.copy(demoMode = v)) }
                }
            }
            if (s.demoMode) {
                Panel(Modifier.fillMaxWidth()) {
                    SectionLabel("시연 데이터")
                    Spacer(Modifier.height(6.dp))
                    Text("3년 2개월 탄 그랜저(42,180km) 상태로 되돌립니다", color = Palette.Sub, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    PillButton("시연 상태로 초기화", onClick = { app.demo.reset() }, filled = false, color = Palette.Amber)
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("개인정보")
                Spacer(Modifier.height(6.dp))
                Text(
                    "경로와 위치는 저장하지 않고, 운행 조건별 거리 비율과 정비 기록만 남깁니다.\n패스포트와 시승 응답 모드에서는 집·직장·차주 정보를 보여주지 않습니다.",
                    color = Palette.Sub, fontSize = 16.sp, lineHeight = 26.sp,
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> Chip(l, i == selected) { onSelect(i) } }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (on) Palette.Bg else Palette.Text,
        fontSize = 18.sp,
        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Palette.Mint else Palette.Panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
private fun Toggle(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Palette.Sub, fontSize = 16.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Mint, checkedThumbColor = Palette.Bg),
        )
    }
}
