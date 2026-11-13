@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tk.carelog.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.carelog.CareLogApp
import com.tk.carelog.service.DriveRecorderService
import com.tk.carelog.ui.components.km
import com.tk.carelog.ui.screens.BriefingOverlay
import com.tk.carelog.ui.screens.DriveScreen
import com.tk.carelog.ui.screens.HandoverScreen
import com.tk.carelog.ui.screens.HomeScreen
import com.tk.carelog.ui.screens.RecordScreen
import com.tk.carelog.ui.screens.SaleScreen
import com.tk.carelog.ui.screens.SettingsScreen
import com.tk.carelog.ui.theme.CareLogTheme
import com.tk.carelog.ui.theme.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CareLogApp
        setContent {
            CareLogTheme {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    DriveRecorderService.start(this@MainActivity)
                }
                LaunchedEffect(Unit) {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            "android.car.permission.CAR_SPEED",
                            Manifest.permission.POST_NOTIFICATIONS,
                        ),
                    )
                }
                AppRoot(app)
            }
        }
    }

    // 화면이 보이는 동안만 오디오 경로를 깨워 둔다 (시연 모드 한정, 첫 음성 끊김 방지)
    override fun onStart() {
        super.onStart()
        val app = application as CareLogApp
        if (app.repo.state.settings.demoMode) app.speaker.startKeepAlive()
    }

    override fun onStop() {
        (application as CareLogApp).speaker.stopKeepAlive()
        super.onStop()
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("정비", Icons.Filled.Home),
    DRIVE("운행", Icons.Filled.PlayArrow),
    RECORD("정비 기록", Icons.Filled.Build),
    SALE("판매 준비", Icons.Filled.Share),
    SETTINGS("설정", Icons.Filled.Settings),
}

@Composable
private fun AppRoot(app: CareLogApp) {
    val snap by app.repo.snapshot.collectAsState()
    val briefings by app.repo.briefings.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var handover by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(Modifier.fillMaxSize()) {
            NavRail(tab, snap.state.odometerKm, app.vehicle.connected, snap.state.settings.demoMode, snap.state.settings.model.label) { tab = it }
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 28.dp, end = 28.dp, bottom = 28.dp, start = 8.dp)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.HOME -> HomeScreen(app, snap, onOpenDrive = { tab = Tab.DRIVE })
                        Tab.DRIVE -> DriveScreen(app, snap)
                        Tab.RECORD -> RecordScreen(app, snap)
                        Tab.SALE -> SaleScreen(app, snap, onStartHandover = { handover = true })
                        Tab.SETTINGS -> SettingsScreen(app, snap)
                    }
                }
            }
        }

        // 판정이 바뀌면 어떤 화면에 있든 위에 띄운다
        briefings.firstOrNull()?.let { b ->
            BriefingOverlay(app, snap, b, onPlanned = { tab = Tab.HOME })
        }

        AnimatedVisibility(visible = handover, enter = fadeIn(), exit = fadeOut()) {
            HandoverScreen(app, snap, onClose = { handover = false; tab = Tab.HOME })
        }
    }
}

@Composable
private fun NavRail(
    current: Tab,
    odometerKm: Double,
    connected: Boolean,
    demo: Boolean,
    modelLabel: String,
    onSelect: (Tab) -> Unit,
) {
    Column(
        Modifier.width(250.dp).fillMaxHeight().padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            LogoMark()
            Spacer(Modifier.width(12.dp))
            Text("CareLog", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Tab.entries.forEach { t ->
            val on = t == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) Palette.Panel else Palette.Bg)
                    .clickable { onSelect(t) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(50)).background(if (on) Palette.Green else Palette.Bg))
                Spacer(Modifier.width(16.dp))
                Icon(t.icon, contentDescription = null, tint = if (on) Palette.Text else Palette.Dim, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Text(t.label, color = if (on) Palette.Text else Palette.Sub, fontSize = 20.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(start = 8.dp)) {
            Text(modelLabel, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("${km(odometerKm)}", color = Palette.Sub, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(if (connected) Palette.Green else Palette.Dim))
                Spacer(Modifier.width(8.dp))
                Text(if (connected) "차량 연결됨" else "차량 미연결", color = Palette.Sub, fontSize = 15.sp)
            }
            if (demo) Text("시연 데이터", color = Palette.Dim, fontSize = 14.sp)
        }
    }
}

/** 로고: 원호 게이지 모양 */
@Composable
private fun LogoMark() {
    Canvas(Modifier.size(30.dp)) {
        val s = 4.dp.toPx()
        drawArc(Palette.Line, 135f, 270f, false, Offset(s / 2, s / 2), androidx.compose.ui.geometry.Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        drawArc(Palette.Mint, 135f, 190f, false, Offset(s / 2, s / 2), androidx.compose.ui.geometry.Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        drawCircle(Palette.Mint, radius = 3.dp.toPx())
    }
}
