package com.iyonzkasir.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.StaffEntity
import com.iyonzkasir.data.repo.PinVerifyResult
import com.iyonzkasir.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffPinScreen(app: AppContainer, onLoggedIn: (StaffEntity) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceOutlet = SessionManager.deviceOutletId.ifBlank { "ALL" }
    val staffList by app.teamRepo.observeStaffForOutlet(deviceOutlet).collectAsState(initial = emptyList())

    var selected by remember { mutableStateOf<StaffEntity?>(null) }
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lockSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(staffList) {
        if (staffList.isNotEmpty() && !app.teamRepo.hasActiveEmployees()) {
            val owner = staffList.firstOrNull { it.isOwner }
            if (owner != null) {
                SessionManager.loginStaff(ctx, owner.toSessionUser())
                onLoggedIn(owner)
            }
        }
    }

    LaunchedEffect(selected, lockSeconds) {
        if (selected != null && lockSeconds > 0) {
            delay(1000)
            lockSeconds = PinLockGuard.remainingLockSeconds(ctx, selected!!.id)
            if (lockSeconds == 0) errorMsg = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pilih Pengguna") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.brand, titleContentColor = Color.White))
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(AppSp.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            val cur = selected
            if (cur == null) {
                Text("Siapa yang bertugas?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Ketuk nama untuk masuk dengan PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(AppSp.lg))
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(AppSp.md), verticalArrangement = Arrangement.spacedBy(AppSp.md)) {
                    items(staffList, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth().clickable {
                            if (s.isOwner && s.pinHash.isBlank()) {
                                SessionManager.loginStaff(ctx, s.toSessionUser())
                                onLoggedIn(s)
                            } else {
                                selected = s; pin = ""
                                lockSeconds = PinLockGuard.remainingLockSeconds(ctx, s.id)
                                errorMsg = if (lockSeconds > 0) "Terlalu banyak salah PIN. Tunggu $lockSeconds detik" else null
                            }
                        }, shape = AppRd.cardLg) {
                            Column(Modifier.padding(AppSp.lg).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(56.dp).clip(CircleShape).background(AppColors.brandSoft), contentAlignment = Alignment.Center) {
                                    Text(s.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AppColors.brand)
                                }
                                Spacer(Modifier.height(AppSp.sm))
                                Text(s.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(s.jobTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(AppSp.md))
                Box(Modifier.size(72.dp).clip(CircleShape).background(AppColors.brandSoft), contentAlignment = Alignment.Center) {
                    Text(cur.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = AppColors.brand)
                }
                Spacer(Modifier.height(AppSp.sm))
                Text(cur.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(cur.jobTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(AppSp.xl))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { idx ->
                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (idx < pin.length) AppColors.brand else MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
                errorMsg?.let {
                    Spacer(Modifier.height(AppSp.md))
                    Text(it, color = AppColors.danger, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(AppSp.xl))
                val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("CLR","0","⌫"))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key ->
                                Surface(
                                    modifier = Modifier.size(68.dp).clip(CircleShape).clickable(enabled = lockSeconds == 0) {
                                        when (key) {
                                            "CLR" -> { pin = ""; errorMsg = null }
                                            "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            else -> if (pin.length < 6) {
                                                pin += key; errorMsg = null
                                                if (pin.length >= 4) {
                                                    scope.launch {
                                                        when (val res = app.teamRepo.verifyPin(ctx, cur, pin)) {
                                                            is PinVerifyResult.Success -> { SessionManager.loginStaff(ctx, cur.toSessionUser()); onLoggedIn(cur) }
                                                            is PinVerifyResult.Wrong -> if (pin.length >= 6) { errorMsg = "PIN salah (${res.attempts}/5)"; pin = "" }
                                                            is PinVerifyResult.Locked -> { lockSeconds = res.secondsLeft; errorMsg = "Tunggu ${res.secondsLeft} detik lagi"; pin = "" }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    color = if (key == "CLR" || key == "⌫") MaterialTheme.colorScheme.surfaceVariant else AppColors.card,
                                    shadowElevation = 2.dp
                                ) { Box(contentAlignment = Alignment.Center) { Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) } }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selected = null; pin = ""; errorMsg = null }) { Text("Ganti Pengguna", color = AppColors.brand) }
            }
        }
    }
}
