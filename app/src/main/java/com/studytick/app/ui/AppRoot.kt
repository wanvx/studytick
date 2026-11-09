package com.studytick.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studytick.app.util.TimeFmt

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
fun AppRoot(vm: StudyViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val today by vm.today.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val goalMinutes by vm.goalMinutes.collectAsStateWithLifecycle()
    val weeklyGoalHours by vm.weeklyGoalHours.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // 系统导航栏（三键 / 手势条）实际占用的高度，edge-to-edge 下必须自己避让
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 悬浮导航栏自身高度 + 与导航栏之间的间距
    val contentBottomPadding = navBarInset + NAV_BAR_CLEARANCE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                },
                label = "tab",
            ) { index ->
                when (index) {
                    0 -> HomeScreen(
                        state = today,
                        onToggle = vm::toggleTimer,
                        bottomPadding = contentBottomPadding,
                    )

                    1 -> CalendarScreen(
                        state = today,
                        records = records,
                        onToggle = vm::toggleTimer,
                        bottomPadding = contentBottomPadding,
                    )

                    else -> ProfileScreen(
                        goalMinutes = goalMinutes,
                        weeklyGoalHours = weeklyGoalHours,
                        updateState = update,
                        onGoalChange = vm::setGoal,
                        onWeeklyGoalChange = vm::setWeeklyGoal,
                        onCheckUpdate = vm::checkUpdate,
                        onOpenUrl = openUrl,
                        onPinWidget = vm::pinWidget,
                        onClearData = vm::clearData,
                        bottomPadding = contentBottomPadding,
                    )
                }
            }
        }

        FloatingNavBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )
    }
}

/** 悬浮导航栏高度（约 56dp）上加一点呼吸间距 */
private val NAV_BAR_CLEARANCE = 88.dp

@Composable
private fun FloatingNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        NavItem("今日", Icons.Rounded.Timer),
        NavItem("日历", Icons.Rounded.CalendarMonth),
        NavItem("我的", Icons.Rounded.PersonOutline),
    )

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                Surface(
                    onClick = { onSelect(index) },
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
