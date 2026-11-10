package com.tk.carelog.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tk.carelog.engine.Briefing
import com.tk.carelog.ui.components.IntervalRuler
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.PulseDot
import com.tk.carelog.ui.components.Tag
import com.tk.carelog.ui.components.km
import com.tk.carelog.ui.components.kmInt
import com.tk.carelog.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * 판정이 바뀌었을 때의 브리핑. P단에서만 띄운다는 전제.
 * 교환 주기 막대가 이전 주기에서 새 주기로 줄어들거나 늘어나는 장면이 핵심.
 */
@Composable
fun BriefingOverlay(app: CareLogApp, snap: Snapshot, b: Briefing, onPlanned: () -> Unit) {
    var started by remember(b) { mutableStateOf(false) }
    var speaking by remember(b) { mutableStateOf(false) }
    LaunchedEffect(b) {
        delay(450)
        started = true
        if (snap.state.settings.voiceOn) {
            speaking = true
            app.speaker.speak(b.text)
            delay(9_000)
            speaking = false
        }
    }
    val from = (b.fromKm ?: b.toKm ?: 1).toFloat()
    val to = (b.toKm ?: b.fromKm ?: 1).toFloat()
    val full = max(from, to)
    val shown by animateFloatAsState(if (started) to / full else from / full, tween(2400), label = "interval")
    val accent = if (b.toSevere) Palette.Amber else Palette.Mint

    Box(
        Modifier.fillMaxSize().background(Color(0xD9000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(0.74f)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.Panel)
                .padding(40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (speaking) { PulseDot(accent); Spacer(Modifier.width(10.dp)) }
                Text("CareLog 브리핑", color = Palette.Sub, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Tag(if (b.toSevere) "가혹 주기로 변경" else "일반 주기로 변경", accent)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (b.toSevere) "${b.item.label} 교환 주기가 짧아졌어요" else "${b.item.label}, 더 자주 안 가셔도 돼요",
                color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(26.dp))

            // 주행거리 눈금자: 권장 교환 지점이 이전 주기에서 새 주기로 움직인다
            val rec = snap.rec(b.item)
            if (rec != null && b.fromKm != null && b.toKm != null) {
                val last = rec.lastServiceOdo
                IntervalRuler(
                    lastOdo = last,
                    currentOdo = snap.state.odometerKm,
                    dueOdo = last + if (started) b.toKm else b.fromKm,
                    ghostDueOdo = if (b.toSevere) last + b.fromKm else null,
                    color = accent,
                )
            } else {
                Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(50)).background(Palette.Line)) {
                    Box(Modifier.fillMaxWidth(shown.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(accent))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(kmInt(b.fromKm), color = Palette.Sub, fontSize = 26.sp)
                Text("  →  ", color = Palette.Sub, fontSize = 26.sp)
                Text(kmInt(b.toKm), color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                b.remainingKm?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(km(max(0.0, it)), color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("남은 거리", color = Palette.Sub, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            b.topReasons.forEach { Text("· $it", color = Palette.Sub, fontSize = 19.sp) }
            Spacer(Modifier.height(18.dp))
            Text(
                "“${b.text}”",
                color = Palette.Text.copy(alpha = 0.9f), fontSize = 19.sp, lineHeight = 26.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Panel2).padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (b.toSevere) {
                    PillButton("응, 정비 계획 추가", onClick = {
                        app.speaker.stop()
                        app.repo.update { it.copy(plannedService = "${b.item.label} — 토요일 출발 시 근처 정비소 경유") }
                        app.repo.dismissBriefing(b)
                        onPlanned()
                    }, color = Palette.Amber)
                }
                PillButton("닫기", onClick = { app.speaker.stop(); app.repo.dismissBriefing(b) }, filled = false, color = Palette.Text)
            }
        }
    }
}
