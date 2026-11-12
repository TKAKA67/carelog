package com.tk.carelog.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.data.Snapshot
import com.tk.carelog.data.Trust
import com.tk.carelog.engine.Condition
import com.tk.carelog.engine.MaintItem
import com.tk.carelog.ui.components.PillButton
import com.tk.carelog.ui.components.PulseDot
import com.tk.carelog.ui.components.Tag
import com.tk.carelog.ui.components.km
import com.tk.carelog.ui.components.pct
import com.tk.carelog.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 시승 응답 모드. 구매자가 운전석에 앉아 묻고, 차가 기록으로 답한다.
 * 집·직장·경로 같은 개인정보는 보여주지 않는다.
 */
@Composable
fun HandoverScreen(app: CareLogApp, snap: Snapshot, onClose: () -> Unit) {
    var stage by remember { mutableStateOf(Stage.QA) }
    var question by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }

    // 답변을 어절 단위로 이어서 보여준다 (음성 재생 중 화면 갱신을 줄이기 위해 글자 단위는 쓰지 않음)
    LaunchedEffect(answer) {
        typed = ""
        val words = answer.split(" ")
        for (i in words.indices) {
            typed = words.subList(0, i + 1).joinToString(" ")
            delay(90)
        }
    }

    val qa = remember(snap) { buildAnswers(snap) }

    Box(
        Modifier.fillMaxSize().background(Palette.Bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "handover") { st ->
            when (st) {
                Stage.QA -> Column(Modifier.fillMaxSize().padding(40.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시승 응답 모드", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                        Tag("개인정보 숨김", Palette.Mint)
                        Spacer(Modifier.weight(1f))
                        PillButton("종료", onClick = { app.speaker.stop(); onClose() }, filled = false, color = Palette.Sub)
                    }
                    Text("이 차의 관리 기록에 대해 물어보세요", color = Palette.Sub, fontSize = 19.sp)
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            qa.forEach { (q, a) ->
                                val on = q == question
                                Text(
                                    "“$q”",
                                    color = if (on) Palette.Bg else Palette.Text,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(if (on) Palette.Mint else Palette.Panel)
                                        .clickable {
                                            question = q
                                            answer = a
                                            if (snap.state.settings.voiceOn) app.speaker.speak(a)
                                        }
                                        .padding(horizontal = 22.dp, vertical = 18.dp),
                                )
                            }
                        }
                        Column(
                            Modifier.weight(1.1f).fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp)).background(Palette.Panel).padding(30.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulseDot(if (question != null) Palette.Mint else Palette.Dim, 12.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("CareLog", color = Palette.Sub, fontSize = 18.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (question == null) "왼쪽 질문을 누르면 차가 기록으로 답합니다." else typed,
                                color = if (question == null) Palette.Dim else Palette.Text,
                                fontSize = 26.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.weight(1f))
                            Text("모든 답은 차량에 남은 기록 기준입니다 · 정비소 확인 / 차주 기록 / 수기 입력 등급 표시", color = Palette.Dim, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PillButton("명의 이전 완료 → 새 차주에게 기록 승계", onClick = {
                            app.speaker.stop()
                            app.repo.transferOwnership()
                            stage = Stage.WELCOME
                        }, color = Palette.Mint)
                    }
                }

                Stage.WELCOME -> WelcomeNewOwner(app, onClose)
            }
        }
    }
}

private enum class Stage { QA, WELCOME }

@Composable
private fun WelcomeNewOwner(app: CareLogApp, onClose: () -> Unit) {
    val snap = app.repo.snapshot.value
    val oil = snap.rec(MaintItem.ENGINE_OIL)
    val remain = oil?.remainingKm?.let { max(0.0, it) }
    val line = "안녕하세요, 새 차주님. 이전 차주의 관리 기록 ${snap.state.records.size}건을 이어받았어요." +
        (remain?.let { " 다음 엔진오일은 ${"%,d".format(it.roundToInt())}킬로미터 후예요." } ?: "")
    LaunchedEffect(Unit) {
        delay(600)
        if (snap.state.settings.voiceOn) app.speaker.speak(line)
    }
    Column(
        Modifier.fillMaxSize().padding(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("안녕하세요, 새 차주님", color = Palette.Text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("이전 차주의 관리 기록 ${snap.state.records.size}건을 이어받았어요", color = Palette.Sub, fontSize = 26.sp)
        Spacer(Modifier.height(36.dp))
        if (remain != null) {
            Text("다음 엔진오일까지", color = Palette.Sub, fontSize = 23.sp)
            Text(km(remain), color = Palette.Mint, fontSize = 72.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(40.dp))
        PillButton("시작하기", onClick = { app.speaker.stop(); onClose() }, color = Palette.Mint)
    }
}

/** 구매자 질문과 답 (기록에서 바로 만든다) */
private fun buildAnswers(snap: Snapshot): List<Pair<String, String>> {
    val s = snap.state
    val out = mutableListOf<Pair<String, String>>()

    val oil = s.records.filter { it.item == MaintItem.ENGINE_OIL }.maxByOrNull { it.time }
    val oilRec = snap.rec(MaintItem.ENGINE_OIL)
    out += "엔진오일 언제 갈았어?" to if (oil == null) {
        "엔진오일 교환 기록이 아직 없어요."
    } else {
        val ago = (s.odometerKm - oil.odometerKm).roundToInt()
        val trust = if (oil.trust == Trust.SHOP) "정비소에서 확인한 기록이에요." else "${oil.trust.label} 기록이에요."
        "${"%,d".format(ago)}킬로미터 전에 교환했고, $trust" +
            (oilRec?.remainingKm?.let { " 다음 교환까지 ${"%,d".format(max(0.0, it).roundToInt())}킬로미터 남았어요." } ?: "")
    }

    val life = snap.lifetimeProfile
    val top = Condition.entries.filter { it.sensed }.maxByOrNull { life.shares[it] ?: 0.0 }
    val anyHot = Condition.entries.any { it.sensed && (life.shares[it] ?: 0.0) >= 0.3 }
    out += "이 차 어떻게 운행됐어?" to buildString {
        append("보유 기간 동안 ${"%,d".format(s.lifetimeRecordedKm.roundToInt())}킬로미터를 기록했어요. ")
        if (top != null) append("가장 많은 조건은 ${top.label}으로 ${pct(life.shares[top] ?: 0.0)}예요. ")
        append(if (anyHot) "가혹 조건이 잦았던 차라 가혹 주기로 관리했어요." else "가혹 조건 기준보다 낮은 운행이 대부분이었어요.")
    }

    val p = snap.passport
    out += "정비 기록 믿을 수 있어?" to
        "정비 기록 ${p.total}건 중 ${p.shopCount}건은 정비소가 확인했고, ${p.carCount}건은 차가 직접 기록했어요. " +
        "엔진오일은 ${p.oilTotal}번 중 ${p.oilCompliant}번을 권장 주기 안에 교환했어요. " +
        "기록되지 않은 주행 ${"%,d".format(s.unrecordedKm.roundToInt())}킬로미터도 숨기지 않고 보여드려요."

    out += "경고등 뜬 적 있어?" to
        if (p.warningCount == 0) "기록 기간 동안 엔진오일·브레이크·타이어 공기압 경고는 없었어요."
        else "기록 기간 동안 경고가 ${p.warningCount}번 있었어요. 자세한 내용은 패스포트에서 볼 수 있어요."

    return out
}
