package com.iyonzkasir.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iyonzkasir.core.*
import com.iyonzkasir.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOutletPickerScreen(app: AppContainer, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val outlets by app.teamRepo.activeOutlets.collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("Pilih Peran Perangkat Ini") }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(AppSp.lg), verticalArrangement = Arrangement.spacedBy(AppSp.md)) {
            Text("HP/Tablet ini dipakai di mana?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Pilih 'Semua Cabang' untuk HP Owner, atau pilih cabang spesifik untuk HP Kasir/Dapur.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                Modifier.fillMaxWidth().clickable {
                    SessionManager.bindBusiness(ctx, SessionManager.businessId, "")
                    SessionManager.setViewingOutlet(ctx, "ALL")
                    onDone()
                },
                colors = CardDefaults.cardColors(containerColor = AppColors.brandSoft)
            ) {
                Row(Modifier.padding(AppSp.lg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, null, tint = AppColors.brand, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(AppSp.md))
                    Column(Modifier.weight(1f)) {
                        Text("HP Owner (Semua Cabang)", fontWeight = FontWeight.Bold)
                        Text("Pantau seluruh gerobak, kelola karyawan & menu", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = AppColors.brand)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                items(outlets, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth().clickable {
                        SessionManager.bindBusiness(ctx, SessionManager.businessId, o.id)
                        SessionManager.setViewingOutlet(ctx, o.id)
                        onDone()
                    }, shape = AppRd.card) {
                        Row(Modifier.padding(AppSp.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, null, tint = AppColors.brand)
                            Spacer(Modifier.width(AppSp.md))
                            Column(Modifier.weight(1f)) {
                                Text(o.name, fontWeight = FontWeight.SemiBold)
                                if (o.address.isNotBlank()) Text(o.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
        }
    }
}
