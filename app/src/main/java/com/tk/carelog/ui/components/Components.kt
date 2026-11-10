package com.tk.carelog.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tk.carelog.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// ---------------------------------------------------------------- 표기

fun num(x: Double?): String = x?.let { "%,d".format(it.roundToInt()) } ?: "—"
fun km(x: Double?): String = x?.let { num(it) + "km" } ?: "—"
fun kmInt(x: Int?): String = x?.let { "%,d".format(it) + "km" } ?: "정기 교환 없음"
fun pct(x: Double): String = "${(x * 100).roundToInt()}%"
fun date(t: Long): String = SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(t))
fun monthDay(t: Long): String = SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(t))
fun daysText(d: Int?): String = when {
    d == null -> ""
    d <= 0 -> "지금"
    d < 45 -> "약 ${d}일 후"
    d < 365 -> "약 ${(d / 30.0).roundToInt()}개월 후"
    else -> "1년 이상 남음"
}

// ---------------------------------------------------------------- 글자 크기

object T {
    val display = 76.sp
    val h1 = 30.sp
    val h2 = 23.sp
    val body = 19.sp
    val small = 16.sp
    val caption = 14.sp
}

// ---------------------------------------------------------------- 블록

/** 화면의 큰 구역. 모서리는 크게, 그림자 없이 면 색으로만 구분한다. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    padding: Dp = 28.dp,
    color: Color = Palette.Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(color).padding(padding),
        content = content,
    )
}

// 예전 이름 유지
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 24.dp,
    color: Color = Palette.Panel,
    content: @Composable ColumnScope.() -> Unit,
) = Section(modifier, padding, color, content)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = T.h2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing, color = Palette.Sub, fontSize = T.small,
                modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable { onTrailing() }.padding(6.dp) else Modifier,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Palette.Sub, fontSize = T.small, modifier = modifier)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Palette.Line.copy(alpha = 0.6f)))
}

/** 상태 표시. 글자 + 앞의 점 하나로 끝낸다. */
@Composable
fun Tag(text: String, color: Color, size: TextUnit = T.caption) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.13f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text("  $text", color = color, fontSize = size, fontWeight = FontWeight.SemiBold)
    }
}

/** 차량 화면용 버튼: 높이 60dp 이상 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    color: Color = Palette.Green,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        !enabled -> Palette.Panel2
        filled -> color
        else -> Palette.Panel2
    }
    val fg = when {
        !enabled -> Palette.Dim
        filled -> Color(0xFF111214)
        else -> Palette.Text
    }
    Box(
        modifier
            .height(60.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = T.body, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- 주행거리 눈금자 (대표 시각 요소)

/**
 * 마지막 정비 → 현재 → 다음 권장 정비를 주행거리계 위에 그린다.
 * ghostDue 가 있으면 "일반 주기였다면" 위치를 흐리게 같이 보여준다.
 */
@Composable
fun IntervalRuler(
    lastOdo: Double,
    currentOdo: Double,
    dueOdo: Double,
    ghostDueOdo: Double?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val end = max(dueOdo, ghostDueOdo ?: dueOdo).coerceAtLeast(currentOdo + 1)
    val span = (end - lastOdo).coerceAtLeast(1.0)
    fun f(x: Double) = ((x - lastOdo) / span).toFloat().coerceIn(0f, 1f)
    val dueF by animateFloatAsState(f(dueOdo), tween(1600), label = "due")
    val curF by animateFloatAsState(f(currentOdo), tween(900), label = "cur")
    val ghostF = ghostDueOdo?.let { f(it) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val w = maxWidth
        Column {
            // 현재 위치 라벨
            Box(Modifier.fillMaxWidth().height(30.dp)) {
                Text(
                    "지금 ${num(currentOdo)}",
                    fontSize = T.small, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.offset(x = (w * curF - 44.dp).coerceIn(0.dp, w - 120.dp)),
                )
            }
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val h = 12.dp.toPx()
                val y = size.height / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(Palette.Panel2, Offset(0f, y - h / 2), Size(size.width, h), r)
                // 권장 구간까지 옅게
                drawRoundRect(color.copy(alpha = 0.22f), Offset(0f, y - h / 2), Size(size.width * dueF, h), r)
                // 이미 달린 거리
                drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * curF, h), r)
                // 일반 주기였다면
                if (ghostF != null && ghostF > dueF + 0.01f) {
                    val gx = size.width * ghostF
                    drawLine(
                        Palette.Sub, Offset(gx, 0f), Offset(gx, size.height), 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
                // 권장 교환 지점
                val dx = size.width * dueF
                drawLine(color, Offset(dx, 0f), Offset(dx, size.height), 4.dp.toPx(), cap = StrokeCap.Round)
                // 현재 위치
                drawCircle(Color.White, 9.dp.toPx(), Offset(size.width * curF, y))
                drawCircle(color, 5.dp.toPx(), Offset(size.width * curF, y))
            }
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                Column(Modifier.align(Alignment.TopStart)) {
                    Text(num(lastOdo), fontSize = T.small, color = Palette.Sub)
                    Text("마지막 교환", fontSize = T.caption, color = Palette.Dim)
                }
                Column(
                    Modifier.offset(x = (w * dueF - 60.dp).coerceIn(90.dp, w - 120.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(num(dueOdo), fontSize = T.small, color = color, fontWeight = FontWeight.SemiBold)
                    Text("권장 교환", fontSize = T.caption, color = Palette.Dim)
                }
                if (ghostF != null && ghostF > dueF + 0.05f && ghostDueOdo != null) {
                    Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                        Text(num(ghostDueOdo), fontSize = T.small, color = Palette.Sub)
                        Text("일반 주기였다면", fontSize = T.caption, color = Palette.Dim)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 게이지

@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    size: Dp,
    stroke: Dp = 14.dp,
    track: Color = Palette.Panel2,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1400), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 135f, 270f, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * shown, false, tl, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun Meter(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    marker: Float? = null,
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1200), label = "meter")
    Box(modifier.fillMaxWidth().height(height + 10.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Palette.Panel2)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
        }
        if (marker != null) {
            Box(Modifier.fillMaxWidth(marker.coerceIn(0f, 1f)).height(height + 10.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(Palette.Sub))
            }
        }
    }
}

@Composable
fun Speedometer(speedKmh: Double, gear: String, size: Dp = 280.dp) {
    val frac = (speedKmh / 140.0).toFloat().coerceIn(0f, 1f)
    val shown by animateFloatAsState(frac, tween(250), label = "speed")
    val color = when {
        speedKmh >= 90 -> Palette.Blue
        speedKmh >= 20 -> Palette.Green
        else -> Palette.Amber
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 16.dp.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(Palette.Panel2, 150f, 240f, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, 150f, 240f * shown, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${speedKmh.roundToInt()}", fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text("km/h", color = Palette.Sub, fontSize = T.small)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("P", "R", "N", "D").forEach { g ->
                    val on = g == gear
                    Text(
                        g,
                        color = if (on) Color(0xFF111214) else Palette.Dim,
                        fontSize = T.small, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.width(30.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color.White else Color.Transparent).padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PulseDot(color: Color, size: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.7f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulseScale")
    Box(Modifier.size(size).scale(s).clip(CircleShape).background(color))
}

// ---------------------------------------------------------------- QR

@Composable
fun QrCode(text: String, size: Dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
        }.getOrNull()
    }
    Box(modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
        if (matrix != null) {
            Canvas(Modifier.fillMaxSize()) {
                val n = matrix.width
                val cell = this.size.width / n
                for (y in 0 until n) for (x in 0 until n) {
                    if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

@Composable
fun Stat(value: String, label: String, color: Color = Palette.Text, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Palette.Sub, fontSize = T.small)
    }
}

/** 화면 머리글: 제목과 한 줄 설명 */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = T.h1, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Palette.Sub, fontSize = T.small, modifier = Modifier.padding(top = 4.dp))
        }
        trailing()
    }
}

@Composable
fun OutlineBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, Palette.Line, RoundedCornerShape(18.dp)), content = content)
}
