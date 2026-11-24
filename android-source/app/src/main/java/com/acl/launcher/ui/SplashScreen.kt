package com.acl.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acl.launcher.R
import com.acl.launcher.ui.theme.AclColors
import kotlinx.coroutines.delay

/**
 * 启动页。
 *
 * 图标比桌面版明显缩小：drawable/splash_logo.png 生成时已按 0.62 缩放并居中留白，
 * 这里只用 size() 控制整体尺寸，不再叠加 scale，避免二次缩小。
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    logoSize: Int = 150
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        // 模拟一段加载，进度条平滑推进后进入主页
        repeat(24) {
            delay(45)
            progress = (progress + 0.042f).coerceAtMost(1f)
        }
        delay(150)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(AclColors.Background, AclColors.BackgroundAlt)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = "ACL 启动器",
                modifier = Modifier
                    .size(logoSize.dp)
                    .alpha(0.4f + 0.6f * progress)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "ACL 启动器",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AclColors.Text,
                modifier = Modifier.alpha(0.3f + 0.7f * progress)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "淡白清白 · 透明毛玻璃",
                fontSize = 12.sp,
                color = AclColors.TextDim,
                modifier = Modifier.alpha(0.2f + 0.6f * progress)
            )
            Spacer(Modifier.height(22.dp))
            // 进度条
            Box(
                Modifier
                    .size(width = 148.dp, height = 4.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(AclColors.Accent, AclColors.AccentDark)
                        ),
                        androidx.compose.ui.graphics.RectangleShape
                    )
                    .alpha(progress)
            )
        }
    }
}
