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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.Snapshot
import com.tk.carelog.engine.DueStatus
import com.tk.carelog.ui.components.Panel
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.QrCode
import com.tk.carelog.ui.components.RingGauge
import com.tk.carelog.ui.components.SectionLabel
import com.tk.carelog.ui.components.Tag
import com.tk.carelog.ui.components.daysText
import com.tk.carelog.ui.components.km
import com.tk.carelog.ui.components.pct
import com.tk.carelog.ui.theme.Palette
import com.tk.carelog.ui.theme.statusColor
import kotlin.math.max

/** 판매 준비 리포트: 기록 완성도 점검 → 판매 전 정비 → 패스포트 공유 → 시승 응답 모드 */
@Composable
fun SaleScreen(app: CareLogApp, snap: Snapshot, onStartHandover: () -> Unit) {
    val p = snap.passport
    val code = remember(snap.state.ownerSince) { passportCode(snap.state.ownerSince) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel(Modifier.fillMaxWidth(), padding = 26.dp) {
                Text("판매 준비 리포트", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("차를 팔기 전에 관리 기록을 점검하고, 빈 곳을 채워요", color = Palette.Sub, fontSize = 16.sp)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    Score(p.shopShare, pct(p.shopShare), "정비소 확인 비율")
                    Score(p.coverage, pct(p.coverage), "기록된 주행 비율")
                    val oilFrac = if (p.oilTotal > 0) p.oilCompliant.toDouble() / p.oilTotal else 0.0
                    Score(oilFrac, "${p.oilCompliant}/${p.oilTotal}", "엔진오일 주기 준수")
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "미기록 ${km(snap.state.unrecordedKm)}은 패스포트에 그대로 표시됩니다 · 경고등 이력 ${if (p.warningCount == 0) "없음" else "${p.warningCount}회"}",
                    color = Palette.Dim, fontSize = 14.sp,
                )
            }

            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("판매 전에 챙기면 좋은 정비")
                Spacer(Modifier.height(10.dp))
                val todo = snap.recommendations.filter { r ->
                    r.status != DueStatus.OK || (r.predictedDays ?: Int.MAX_VALUE) <= 90
                }
                if (todo.isEmpty()) {
                    Text("지금 바로 처리할 항목이 없어요. 이대로 패스포트를 공유해도 좋아요.", color = Palette.Mint, fontSize = 19.sp)
                }
                todo.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.verdict.item.label, color = Palette.Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                            val rem = r.remainingKm?.let { "${km(max(0.0, it))} 남음 · " } ?: ""
                            Text(rem + if (r.status == DueStatus.DUE) "권장 시기 지남" else "예상 ${daysText(r.predictedDays)}", color = Palette.Sub, fontSize = 16.sp)
                        }
                        Tag(if (r.status == DueStatus.OK) "판매 전 권장" else "지금 권장", if (r.status == DueStatus.OK) Palette.Amber else statusColor(r.status))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("판매 전에 처리하면 패스포트에 ‘권장 시기 지남’ 없이 넘길 수 있어요", color = Palette.Dim, fontSize = 14.sp)
            }

            ProfileCard(snap.lifetimeProfile, "보유 기간 운행 프로필 (${p.ownedMonths / 12}년 ${p.ownedMonths % 12}개월)")
        }

        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth(), padding = 26.dp) {
                Text("케어 패스포트", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("매물 등록용 · 개인정보 제외 버전", color = Palette.Sub, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode("CARELOG-PASSPORT:$code", 170.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(code, color = Palette.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("포함", color = Palette.Mint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("정비 기록 ${p.total}건 (신뢰 등급)\n운행 프로필 · 주기 준수율\n경고등 이력 · 미기록 구간", color = Palette.Sub, fontSize = 16.sp, lineHeight = 23.sp)
                        Text("제외", color = Palette.Amber, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("집 · 직장 · 경로 · 차주 정보", color = Palette.Sub, fontSize = 16.sp)
                    }
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f), color = Palette.Panel2, padding = 26.dp) {
                Text("시승 응답 모드", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "시승하러 온 구매자가 운전석에서 물어보면\n차가 기록을 바탕으로 직접 답합니다",
                    color = Palette.Sub, fontSize = 18.sp, lineHeight = 24.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text("“엔진오일 언제 갈았어?”", color = Palette.Text, fontSize = 23.sp)
                Spacer(Modifier.weight(1f))
                PillButton("시승 응답 모드 켜기", onClick = {
                    app.speaker.stop()
                    onStartHandover()
                }, color = Palette.Mint, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Score(frac: Double, value: String, label: String) {
    val color: Color = when {
        frac >= 0.9 -> Palette.Mint
        frac >= 0.6 -> Palette.Amber
        else -> Palette.Red
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RingGauge(frac.toFloat(), color, size = 132.dp, stroke = 12.dp) {
            Text(value, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Palette.Sub, fontSize = 16.sp)
    }
}

/** 차주별 패스포트 코드 (예: CL-7Q4K-2M9X) */
private fun passportCode(seed: Long): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val r = java.util.Random(seed)
    fun part() = (1..4).map { chars[r.nextInt(chars.length)] }.joinToString("")
    return "CL-${part()}-${part()}"
}
