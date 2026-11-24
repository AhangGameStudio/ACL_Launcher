package com.acl.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AclColorScheme = lightColorScheme(
    primary = AclColors.Accent,
    onPrimary = AclColors.GlassStrong,
    secondary = AclColors.AccentDark,
    background = AclColors.Background,
    onBackground = AclColors.Text,
    surface = AclColors.Glass,
    onSurface = AclColors.Text,
    surfaceVariant = AclColors.GlassSoft,
    onSurfaceVariant = AclColors.TextDim,
    outline = AclColors.Border,
    error = AclColors.Danger
)

private val AclTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AclColors.Text),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AclColors.Text),
    bodyLarge = TextStyle(fontSize = 14.sp, color = AclColors.Text),
    bodyMedium = TextStyle(fontSize = 13.sp, color = AclColors.TextDim),
    bodySmall = TextStyle(fontSize = 12.sp, color = AclColors.TextFaint),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AclColors.Text)
)

/**
 * ACL 主题。
 * 淡白清白配色是硬性要求，因此不跟随系统深色模式——始终保持高对比度浅色。
 */
@Composable
fun AclTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()   // 故意不跟随深色：ACL 固定用淡白高对比配色
    MaterialTheme(
        colorScheme = AclColorScheme,
        typography = AclTypography,
        content = content
    )
}
