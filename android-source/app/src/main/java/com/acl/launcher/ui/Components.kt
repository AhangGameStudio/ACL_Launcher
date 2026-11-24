package com.acl.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acl.launcher.ui.theme.AclColors

/**
 * 毛玻璃卡片。
 * 半透明白底 + 细边框，保证文字对比度（桌面版 v0.7.6 修过下拉对比度问题）。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xE6FFFFFF),
                        Color(0xCCFFFFFF)
                    )
                )
            )
            .border(1.dp, AclColors.Border, RoundedCornerShape(18.dp))
            .padding(padding.dp)
    ) {
        content()
    }
}

/** 主按钮。 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(AclColors.Accent, AclColors.AccentDark))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFBFC9D2), Color(0xFFAEB9C4)))
                }
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 次要按钮（毛玻璃描边）。 */
@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xB3FFFFFF))
            .border(1.dp, AclColors.Border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) AclColors.Text else AclColors.TextFaint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 分区标题：带一个绿色菱形标记（对应桌面版流程面板的小方块）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AclColors.Accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AclColors.Text)
    }
}

/** 键值行。 */
@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, fontSize = 12.sp, color = AclColors.TextDim)
        Text(value, fontSize = 12.sp, color = AclColors.Text, fontWeight = FontWeight.Medium)
    }
}

/** 状态圆点 + 文字。 */
@Composable
fun StatusDot(active: Boolean, done: Boolean, text: String, modifier: Modifier = Modifier) {
    val color = when {
        done -> AclColors.Success
        active -> AclColors.Accent
        else -> AclColors.TextFaint
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = AclColors.TextDim, modifier = Modifier.weight(1f))
    }
}

/** 空态占位。 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, fontSize = 13.sp, color = AclColors.TextFaint)
    }
}
