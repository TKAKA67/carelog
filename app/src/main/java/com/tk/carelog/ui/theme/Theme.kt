package com.tk.carelog.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.tk.carelog.R
import com.tk.carelog.data.Trust
import com.tk.carelog.engine.DueStatus

/**
 * Pleos Connect 다크 테마 토큰을 기준으로 맞춘 색.
 * background_secondary / surface / button 값은 Pleos 디자인 가이드의 다크 토큰을 따랐다.
 */
object Palette {
    val Bg = Color(0xFF1D1E21)       // background_secondary (dark)
    val Panel = Color(0xFF26272B)
    val Panel2 = Color(0xFF313236)   // background_primary (dark)
    val Line = Color(0xFF44464E)     // button / surface 계열
    val Text = Color(0xFFFFFFFF)
    val Sub = Color(0xFFA9ABB3)
    val Dim = Color(0xFF6E717A)

    val Green = Color(0xFF00E074)    // informative positive
    val Blue = Color(0xFF0082FF)     // informative
    val Red = Color(0xFFFF4C28)      // informative negative
    val Amber = Color(0xFFFFB020)    // 가혹 주기 표시용

    // 예전 이름 호환
    val Mint = Green
}

/** Pleos 시스템 UI 서체 (Asta Sans, OFL) */
val AstaSans = FontFamily(
    Font(R.font.asta_sans_regular, FontWeight.Normal),
    Font(R.font.asta_sans_regular, FontWeight.Medium),
    Font(R.font.asta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.asta_sans_bold, FontWeight.Bold),
)

fun statusColor(s: DueStatus): Color = when (s) {
    DueStatus.OK -> Palette.Green
    DueStatus.SOON -> Palette.Amber
    DueStatus.DUE -> Palette.Red
}

fun trustColor(t: Trust): Color = when (t) {
    Trust.SHOP -> Palette.Green
    Trust.CAR -> Palette.Blue
    Trust.OWNER -> Palette.Sub
}

/**
 * 화면 폭 기준으로 전체 UI 를 비례 확대한다.
 * 기준 폭 1280dp 로 설계했고, 넓은 Connect-W 화면에서는 같은 비율로 커진다.
 */
@Composable
fun CareLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.Bg,
            surface = Palette.Panel,
            primary = Palette.Green,
            onPrimary = Palette.Bg,
            secondary = Palette.Amber,
            onSurface = Palette.Text,
        ),
    ) {
        BoxWithConstraints {
            val base = LocalDensity.current
            val k = (maxWidth.value / 1280f).coerceIn(1f, 1.9f)
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * k, base.fontScale),
                LocalTextStyle provides TextStyle(fontFamily = AstaSans, color = Palette.Text, fontFeatureSettings = "tnum"),
            ) {
                content()
            }
        }
    }
}
