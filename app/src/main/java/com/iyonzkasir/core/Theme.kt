package com.iyonzkasir.core

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// ═══════ BRAND COLOR PRESETS ═══════
enum class BrandPreset(
    val id: String,
    val label: String,
    val emoji: String,
    val primaryHex: Long,
    val darkHex: Long,
    val lightBgHex: Long,
    val darkBgHex: Long
) {
    ORANGE("orange", "Orange", "🟧", 0xFFFF6B2C, 0xFFE55A1F, 0xFFFFEFE8, 0xFF2D1B12),
    HIJAU("hijau", "Hijau", "🟩", 0xFF2E7D32, 0xFF1B5E20, 0xFFE8F5E9, 0xFF142817),
    BIRU("biru", "Biru", "🟦", 0xFF1976D2, 0xFF0D47A1, 0xFFE3F2FD, 0xFF122133),
    UNGU("ungu", "Ungu", "🟪", 0xFF7B1FA2, 0xFF4A148C, 0xFFF3E5F5, 0xFF26142E),
    MERAH("merah", "Merah", "🟥", 0xFFD32F2F, 0xFFB71C1C, 0xFFFFEBEE, 0xFF301414),
    PINK("pink", "Pink", "🌸", 0xFFE91E63, 0xFFAD1457, 0xFFFCE4EC, 0xFF2E141F),
    TEAL("teal", "Teal", "🟢", 0xFF00897B, 0xFF004D40, 0xFFE0F2F1, 0xFF102724),
    GOLD("gold", "Gold", "🟨", 0xFFE09F00, 0xFFB27900, 0xFFFFF8E1, 0xFF2E240E);

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: ORANGE
    }
}

enum class DisplayThemeMode(val id: String, val label: String) {
    SYSTEM("system", "Ikut Sistem HP"),
    LIGHT("light", "Terang"),
    DARK("dark", "Gelap");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

// ═══════ THEME STATE ═══════
object AppThemeState {
    var mode by mutableStateOf(DisplayThemeMode.SYSTEM)
        private set
    var preset by mutableStateOf(BrandPreset.ORANGE)
        private set

    fun updateMode(newMode: DisplayThemeMode) { mode = newMode }
    fun updatePreset(newPreset: BrandPreset) { preset = newPreset }
}

val LocalIsDarkMode = compositionLocalOf { false }

// ═══════ SEMANTIC & ADAPTIVE COLORS ═══════
object AppColors {
    val brand: Color get() = Color(AppThemeState.preset.primaryHex)
    val brandDark: Color get() = Color(AppThemeState.preset.darkHex)

    val brandSoft: Color
        @Composable get() = if (LocalIsDarkMode.current)
            Color(AppThemeState.preset.darkBgHex)
        else
            Color(AppThemeState.preset.lightBgHex)

    val success = Color(0xFF1E9E54)
    val warning = Color(0xFFF57C00)
    val danger = Color(0xFFD32F2F)
    val info = Color(0xFF1976D2)

    val card: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    val cardAlt: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant

    val border: Color
        @Composable get() = MaterialTheme.colorScheme.outline
}

// ═══════ SPACING, RADIUS, ELEVATION ═══════
object AppSp {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object AppRd {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 24.dp

    val card = RoundedCornerShape(md)
    val cardLg = RoundedCornerShape(lg)
    val pill = RoundedCornerShape(999.dp)
}

object AppEl {
    val none = 0.dp
    val card = 1.dp
    val raised = 4.dp
}

val CATEGORY_COLORS = listOf(
    "#FF6B2C", "#E53935", "#8E24AA", "#3949AB",
    "#1E88E5", "#00897B", "#43A047", "#F9A825",
    "#FB8C00", "#6D4C41", "#546E7A", "#D81B60"
)

// ═══════ APP THEME COMPOSABLE ═══════
@Composable
fun IyonzAppTheme(content: @Composable () -> Unit) {
    val isDark = when (AppThemeState.mode) {
        DisplayThemeMode.SYSTEM -> isSystemInDarkTheme()
        DisplayThemeMode.LIGHT -> false
        DisplayThemeMode.DARK -> true
    }

    val preset = AppThemeState.preset
    val primary = Color(preset.primaryHex)
    val secondary = Color(preset.darkHex)

    val scheme = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            background = Color(0xFF0E0E10),
            surface = Color(0xFF18181B),
            surfaceVariant = Color(0xFF232328),
            onSurface = Color(0xFFF4F4F5),
            onSurfaceVariant = Color(0xFFA1A1AA),
            outline = Color(0xFF2E2E35)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = secondary,
            background = Color(0xFFF8F8FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFF1F1F5),
            onSurface = Color(0xFF18181B),
            onSurfaceVariant = Color(0xFF71717A),
            outline = Color(0xFFE4E4E7)
        )
    }

    CompositionLocalProvider(LocalIsDarkMode provides isDark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// ═══════ SHARED REUSABLE UI ═══════
@Composable
fun EmptyPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(AppSp.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape)
                .background(AppColors.brand.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(40.dp), tint = AppColors.brand)
        }
        Spacer(Modifier.height(AppSp.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(AppSp.xs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(AppSp.xl))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand),
                shape = AppRd.card
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
