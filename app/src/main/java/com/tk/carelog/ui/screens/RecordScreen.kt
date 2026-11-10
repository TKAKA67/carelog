package com.tk.carelog.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.ServiceRecord
import com.tk.carelog.data.Snapshot
import com.tk.carelog.data.Trust
import com.tk.carelog.engine.MaintItem
import com.tk.carelog.ui.components.Panel
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.PulseDot
import com.tk.carelog.ui.components.QrCode
import com.tk.carelog.ui.components.SectionLabel
import com.tk.carelog.ui.components.Tag
import com.tk.carelog.ui.components.date
import com.tk.carelog.ui.components.km
import com.tk.carelog.ui.theme.Palette
import com.tk.carelog.ui.theme.trustColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 정비 기록: P단에서 버튼으로 기록하고, 정비사가 QR로 확인한다 (음성 기록은 Pleos STT 공개 후) */
@Composable
fun RecordScreen(app: CareLogApp, snap: Snapshot) {
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf<String?>(null) }
    var lastAdded by remember { mutableStateOf<Long?>(null) }
    var shopCode by remember { mutableStateOf<String?>(null) }

    fun log(item: MaintItem, trust: Trust) {
        app.repo.addRecord(item, trust)
        lastAdded = System.currentTimeMillis()
        val next = app.repo.snapshot.value.rec(item)?.remainingKm
        if (snap.state.settings.voiceOn) {
            app.speaker.speak("${item.label} 교환을 기록했어요. 주행거리 ${"%,d".format(snap.state.odometerKm.toInt())}킬로미터." +
                (next?.let { " 다음 교환까지 ${"%,d".format(it.toInt())}킬로미터 남았어요." } ?: ""))
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 버튼 기록
            Panel(Modifier.fillMaxWidth(), padding = 26.dp) {
                Text("정비하고 나서, 버튼 한 번", color = Palette.Sub, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("엔진오일 · 필터 교환", color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape)
                            .background(if (listening) Palette.Amber else Palette.Mint)
                            .clickable(enabled = !listening) {
                                scope.launch {
                                    listening = true; heard = null
                                    delay(500)
                                    heard = "엔진오일 · 필터 교환"
                                    delay(400)
                                    listening = false
                                    log(MaintItem.ENGINE_OIL, Trust.CAR)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (listening) PulseDot(Palette.Bg, 22.dp) else Text("기록", color = Palette.Bg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(
                            when {
                                listening && heard == null -> "기록하는 중…"
                                heard != null -> "$heard 기록됨"
                                else -> "음성 기록(“엔진오일 갈았어”)은 Pleos STT 공개 후 지원"
                            },
                            color = if (heard != null) Palette.Text else Palette.Sub, fontSize = 21.sp,
                        )
                        Text("현재 주행거리 ${km(snap.state.odometerKm)} · 시각이 자동으로 남습니다", color = Palette.Dim, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionLabel("다른 항목 기록")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintItem.entries.filter { it != MaintItem.ENGINE_OIL }.forEach { item ->
                        PillButton(item.label, onClick = { log(item, Trust.CAR) }, filled = false, color = Palette.Text)
                    }
                }
            }

            // 정비소 확인
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("정비소 확인 받기", color = Palette.Text, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("정비사가 QR을 찍고 계기판 주행거리를 입력하면\n“정비소 확인” 등급으로 남습니다", color = Palette.Sub, fontSize = 16.sp, lineHeight = 26.sp)
                        Spacer(Modifier.height(14.dp))
                        if (shopCode == null) {
                            PillButton("확인 코드 만들기", onClick = {
                                shopCode = (100000..999999).random().toString()
                            }, filled = false, color = Palette.Mint)
                        } else {
                            Text("확인 코드 $shopCode", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("계기판 ${km(snap.state.odometerKm)}", color = Palette.Sub, fontSize = 16.sp)
                            Spacer(Modifier.height(10.dp))
                            PillButton("정비사 확인 완료 (시연)", onClick = {
                                log(MaintItem.ENGINE_OIL, Trust.SHOP)
                                shopCode = null
                            }, color = Palette.Mint)
                        }
                    }
                    if (shopCode != null) {
                        QrCode("CARELOG-SHOP:${shopCode}:${snap.state.odometerKm.toInt()}", 150.dp)
                    }
                }
            }
        }

        // 기록 타임라인
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("정비 기록", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${snap.state.records.size}건 · 수정 불가", color = Palette.Sub, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            val list = snap.state.records.sortedByDescending { it.time }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.time }) { r ->
                    val fresh = lastAdded != null && r.time >= (lastAdded ?: 0L) - 2_000
                    RecordRow(r, fresh)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(r: ServiceRecord, fresh: Boolean) {
    var visible by remember { mutableStateOf(!fresh) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn() + expandVertically()) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (fresh) Palette.Mint.copy(alpha = 0.12f) else Palette.Panel2)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = trustColor(r.trust), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.item.label, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${date(r.time)} · ${km(r.odometerKm)}" + if (r.note.isNotBlank()) " · ${r.note}" else "", color = Palette.Sub, fontSize = 16.sp)
            }
            Tag(r.trust.label, trustColor(r.trust), 14.sp)
        }
    }
}
