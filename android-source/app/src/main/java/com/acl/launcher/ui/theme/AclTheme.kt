package com.acl.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ACL 配色：淡白 + 清白 + 透明。
 * 与桌面版 v0.7.6 的毛玻璃风格一致：底色淡白，卡片半透明白，文字深墨。
 */
object AclColors {
    val Background = Color(0xFFF7F9FB)
    val BackgroundAlt = Color(0xFFEFF3F6)
    val Glass = Color(0xCCFFFFFF)
    val GlassStrong = Color(0xE6FFFFFF)
    val GlassSoft = Color(0x99FFFFFF)
    val Border = Color(0x1A10151C)
    val Accent = Color(0xFF1F9E5A)
    val AccentDark = Color(0xFF14713E)
    val Text = Color(0xFF10151C)
    val TextDim = Color(0xFF5A6672)
    val TextFaint = Color(0xFF8A96A3)
    val Danger = Color(0xFFD64545)
    val Warning = Color(0xFFE0A33B)
    val Success = Color(0xFF4FD08A)
}

/** 毛玻璃卡片底色。 */
fun glassBrush(alpha: Int = 0xCC): Brush = Brush.verticalGradient(
    listOf(
        Color(0xFF, 0xFF, 0xFF, alpha).copy(alpha = alpha / 255f),
        Color(0xFF, 0xF8, 0xFB, 0xFD).copy(alpha = (alpha * 0.88f) / 255f)
    )
)

/**
 * 动态光晕背景。
 * 对应桌面版的动态渐变光斑效果，用 Compose 实现为缓慢漂移的柔和色块。
 */
@Composable
fun GlowBackground(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(modifier = modifier.fillMaxSize().background(AclColors.Background)) {
        if (enabled) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x3320C08A),
                                Color(0x00000000)
                            ),
                            radius = 900f
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x224F8DF7),
                                Color(0x00000000)
                            ),
                            radius = 1100f
                        )
                    )
            )
        }
    }
}

/** 模糊背景层，用于在毛玻璃卡片后面垫一层柔化内容。 */
@Composable
fun BlurBackdrop(content: @Composable () -> Unit, radius: Dp = 18.dp) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blur(radius)) { content() }
    }
}
