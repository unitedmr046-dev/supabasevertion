package com.iyonzkasir.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.iyonzkasir.core.*
import com.iyonzkasir.di.AppContainer

enum class ActivitySection(val label: String) {
    KITCHEN("Dapur"),
    ORDERS("Open Bill & Riwayat"),
    KASBON("Kasbon"),
    WEB("Order Web")
}

@Composable
fun ActivityHubV2Screen(
    app: AppContainer,
    initialSection: ActivitySection? = null,
    onResumeOpenBill: (String) -> Unit
) {
    val user by SessionManager.userState
    val tabs = remember(user) {
        buildList {
            if (SessionManager.can(AppAccess.KITCHEN)) add(ActivitySection.KITCHEN)
            if (SessionManager.can(AppAccess.ORDERS)) add(ActivitySection.ORDERS)
            if (SessionManager.can(AppAccess.KASBON)) add(ActivitySection.KASBON)
            if (SessionManager.can(AppAccess.WEB_ORDERS)) add(ActivitySection.WEB)
        }
    }
    var selected by remember(tabs, initialSection) {
        mutableStateOf(if (initialSection != null && initialSection in tabs) initialSection else tabs.firstOrNull())
    }

    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1 && selected != null) {
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0), containerColor = AppColors.card) {
                tabs.forEach { sec ->
                    Tab(selected = selected == sec, onClick = { selected = sec }, text = { Text(sec.label) })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) {
                ActivitySection.KITCHEN -> KitchenV2Screen(app)
                ActivitySection.ORDERS -> OrdersHistoryV2Screen(app, onResumeOpenBill)
                ActivitySection.KASBON -> KasbonAndCrmV2Screen(app)
                ActivitySection.WEB -> WebOrdersV2Screen(app, onOrderAccepted = onResumeOpenBill)
                null -> EmptyPlaceholder(Icons.Default.Lock, "Tidak ada akses aktivitas")
            }
        }
    }
}
