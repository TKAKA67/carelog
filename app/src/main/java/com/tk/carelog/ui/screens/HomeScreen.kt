package com.tk.carelog.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.Snapshot
import com.tk.carelog.engine.Condition
import com.tk.carelog.engine.DrivingProfile
import com.tk.carelog.engine.DueStatus
import com.tk.carelog.engine.MaintItem
import com.tk.carelog.engine.Recommendation
import com.tk.carelog.engine.Thresholds
import com.tk.carelog.engine.VehicleModel
import com.tk.carelog.engine.VerdictEngine
import com.tk.carelog.ui.components.Hairline
import com.tk.carelog.ui.components.IntervalRuler
import com.tk.carelog.ui.components.Meter
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.Section
import com.tk.carelog.ui.components.SectionTitle
import com.tk.carelog.ui.components.T
import com.tk.carelog.ui.components.Tag
import com.tk.carelog.ui.components.daysText
import com.tk.carelog.ui.components.km
import com.tk.carelog.ui.components.kmInt
import com.tk.carelog.ui.components.monthDay
import com.tk.carelog.ui.components.num
import com.tk.carelog.ui.components.pct
import com.tk.carelog.ui.theme.Palette
import com.tk.carelog.ui.theme.statusColor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HomeScreen(app: CareLogApp, snap: Snapshot, onOpenDrive: () -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier.weight(1.45f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            WarningBanner(snap)
            snap.headline?.let { HeadlineSection(app, snap, it) }
            OtherItems(snap)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ProfileCard(snap.profile, "최근 30일 운행", onMore = onOpenDrive)
            PlanCard(app, snap)
            HabitCard(snap)
        }
    }
}

@Composable
private fun WarningBanner(snap: Snapshot) {
    val st = snap.state.oilLevelState ?: return
    if (!st.contains("LOW", ignoreCase = true)) return
    Section(color = Palette.Red.copy(alpha = 0.16f)) {
        Text("엔진오일 레벨 경고", color = Palette.Red, fontSize = T.h2, fontWeight = FontWeight.Bold)
        Text("차량 경고에 따라 바로 점검하세요. 이 안내가 다른 권장보다 먼저입니다.", fontSize = T.body)
    }
}

/** 가장 먼저 챙길 정비: 큰 숫자 + 주행거리 눈금자 */
@Composable
private fun HeadlineSection(app: CareLogApp, snap: Snapshot, r: Recommendation) {
    val v = r.verdict
    var showWhy by remember { mutableStateOf(false) }
    val s = v.item.schedule(snap.state.settings.model)
    val remain = r.remainingKm?.let { max(0.0, it) }
    val accent = when {
        r.status == DueStatus.DUE -> Palette.Red
        v.severe -> Palette.Amber
        else -> Palette.Green
    }

    Section(padding = 32.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.item.label, fontSize = T.h2, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(14.dp))
            Tag(if (v.severe) "가혹 주기 ${kmInt(v.intervalKm)}" else "일반 주기 ${kmInt(v.intervalKm)}", if (v.severe) Palette.Amber else Palette.Green)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(remain), fontSize = 84.sp, fontWeight = FontWeight.Bold, lineHeight = 84.sp)
            Text(" km 남음", fontSize = T.h2, color = Palette.Sub, modifier = Modifier.padding(bottom = 14.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                val whenText = if (r.status == DueStatus.DUE) "권장 시기가 지났어요" else daysText(r.predictedDays)
                Text(whenText, color = accent, fontSize = T.h2, fontWeight = FontWeight.SemiBold)
                r.dueTime?.let { Text("늦어도 ${monthDay(it)}까지", color = Palette.Sub, fontSize = T.small) }
            }
        }
        Spacer(Modifier.height(18.dp))
        val interval = v.intervalKm
        if (interval != null) {
            val ghost = if (v.severe && s.normalKm != null && s.normalKm != interval) r.lastServiceOdo + s.normalKm else null
            IntervalRuler(
                lastOdo = r.lastServiceOdo,
                currentOdo = snap.state.odometerKm,
                dueOdo = r.lastServiceOdo + interval,
                ghostDueOdo = ghost,
                color = accent,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            v.reasons.firstOrNull()?.let { "판정 근거: $it" } ?: "최근 운행 조건 기준",
            color = Palette.Sub, fontSize = T.small,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(if (snap.state.plannedService == null) "정비 계획 잡기" else "계획 잡힘", onClick = {
                app.repo.update { it.copy(plannedService = "${v.item.label} 교환 · 토요일 출발할 때 정비소 경유") }
            }, color = accent, enabled = snap.state.plannedService == null)
            PillButton(if (showWhy) "근거 닫기" else "왜 이 주기야?", onClick = {
                showWhy = !showWhy
                if (showWhy && snap.state.settings.voiceOn) {
                    app.speaker.speak("${v.item.label} 주기는 지금 ${if (v.severe) "가혹" else "일반"} 주기예요. ${v.spoken ?: "최근 운행 조건을 기준으로 판단했어요"}.")
                }
            }, filled = false)
        }
        AnimatedVisibility(showWhy) {
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                v.reasons.forEach { Text(it, color = Palette.Sub, fontSize = T.small) }
                Text("매뉴얼의 일반·가혹 주기 중 하나만 권장합니다. 보증·고장 판단은 블루핸즈 기준입니다.", color = Palette.Dim, fontSize = T.caption)
            }
        }
    }
}

@Composable
private fun OtherItems(snap: Snapshot) {
    val others = snap.recommendations.filter { it.verdict.item != snap.headline?.verdict?.item }
    Section {
        SectionTitle("다른 정비 항목")
        Spacer(Modifier.height(8.dp))
        others.forEachIndexed { i, r ->
            if (i > 0) Hairline()
            ItemRow(r, snap.state.settings.model)
        }
    }
}

@Composable
private fun ItemRow(r: Recommendation, model: VehicleModel) {
    val v = r.verdict
    val interval = v.intervalKm
    val remain = r.remainingKm?.let { max(0.0, it) }
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.item.label, fontSize = T.body, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(if (v.severe) "가혹" else "일반", color = if (v.severe) Palette.Amber else Palette.Sub, fontSize = T.caption)
            }
            if (interval != null && remain != null) {
                Meter((remain / interval).toFloat(), if (r.status == DueStatus.OK) Palette.Sub else statusColor(r.status), Modifier.padding(top = 6.dp, end = 24.dp), height = 6.dp)
            } else {
                Text("일반 조건에서는 정기 교환 없음, 가혹 조건이면 ${kmInt(v.item.schedule(model).severeKm)}", color = Palette.Dim, fontSize = T.caption)
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(170.dp)) {
            Text(remain?.let { km(it) } ?: "—", fontSize = T.body, fontWeight = FontWeight.SemiBold,
                color = if (r.status == DueStatus.OK) Palette.Text else statusColor(r.status))
            Text(
                if (r.status == DueStatus.DUE) "지금 권장" else daysText(r.predictedDays),
                color = Palette.Sub, fontSize = T.caption,
            )
        }
    }
}

/** 조건별 비율과 '자주' 기준선 (다른 화면에서도 씀) */
@Composable
fun ProfileCard(p: DrivingProfile, title: String, onMore: (() -> Unit)? = null) {
    Section {
        SectionTitle(title, trailing = if (onMore != null) "운행 보기" else null, onTrailing = onMore)
        Text("기록 ${km(p.recordedKm)}, 트립 ${p.tripCount}회", color = Palette.Sub, fontSize = T.caption)
        Spacer(Modifier.height(16.dp))
        Condition.entries.filter { it.sensed }.forEach { c ->
            val sh = p.shares[c] ?: 0.0
            val hot = sh >= Thresholds.FREQUENT_SHARE - Thresholds.UNCERTAIN_BAND
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(c.label, color = if (hot) Palette.Text else Palette.Sub, fontSize = T.small, modifier = Modifier.width(150.dp))
                Meter(sh.toFloat(), if (hot) Palette.Amber else Palette.Green.copy(alpha = 0.7f), Modifier.weight(1f), height = 8.dp, marker = Thresholds.FREQUENT_SHARE.toFloat())
                Text(pct(sh), color = if (hot) Palette.Amber else Palette.Text, fontSize = T.small, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(60.dp).padding(start = 12.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("세로선은 ‘자주’의 기준 ${pct(Thresholds.FREQUENT_SHARE)}입니다. 매뉴얼에 정의가 없어 CareLog가 정한 값을 그대로 보여줍니다.", color = Palette.Dim, fontSize = T.caption)
    }
}

@Composable
private fun PlanCard(app: CareLogApp, snap: Snapshot) {
    val plan = snap.state.plannedService ?: return
    Section(color = Palette.Panel2) {
        SectionTitle("정비 계획", trailing = "취소", onTrailing = { app.repo.update { it.copy(plannedService = null) } })
        Spacer(Modifier.height(6.dp))
        Text(plan, fontSize = T.body)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteDot("출발", Palette.Sub)
            RouteLine()
            RouteDot("정비소", Palette.Amber)
            RouteLine()
            RouteDot("목적지", Palette.Green)
        }
        Spacer(Modifier.height(12.dp))
        Text("목적지를 정하면 내비게이션에 경유지로 넣어 드려요.", color = Palette.Sub, fontSize = T.caption)
    }
}

@Composable
private fun RouteDot(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(color))
        Text(label, color = Palette.Sub, fontSize = T.caption, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RowScope.RouteLine() {
    Box(
        Modifier.weight(1f).padding(start = 6.dp, end = 6.dp, bottom = 26.dp).height(3.dp)
            .clip(RoundedCornerShape(50)).background(Palette.Line),
    )
}

/** 기존 교환 습관과 비교 */
@Composable
private fun HabitCard(snap: Snapshot) {
    val s = snap.state.settings
    val oil = snap.rec(MaintItem.ENGINE_OIL) ?: return
    val perYear = max(snap.profile.kmPerDay * 365, 12_000.0)
    val saved = VerdictEngine.yearlySavedChanges(perYear, s.habitOilKm, oil.verdict.intervalKm) ?: return
    Section {
        SectionTitle("교환 습관과 비교")
        Spacer(Modifier.height(10.dp))
        when {
            saved > 0.05 -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("연 ${"%.1f".format(saved)}회", color = Palette.Green, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("  덜 가도 돼요", fontSize = T.body, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            saved < -0.05 -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("연 ${"%.1f".format(-saved)}회", color = Palette.Amber, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("  더 자주 필요해요", fontSize = T.body, modifier = Modifier.padding(bottom = 7.dp))
                }
            }
            else -> Text("지금 습관이 판정 주기와 비슷해요", fontSize = T.body)
        }
        Spacer(Modifier.height(6.dp))
        val won = if (saved > 0.05 && s.lastReceiptWon > 0) ", 영수증 기준 약 ${"%,d".format((saved * s.lastReceiptWon).roundToInt())}원" else ""
        Text("${"%,d".format(s.habitOilKm)}km마다 갈던 습관과 판정 주기 ${kmInt(oil.verdict.intervalKm)}을 연 ${km(perYear)} 기준으로 비교$won", color = Palette.Sub, fontSize = T.caption)
    }
}
