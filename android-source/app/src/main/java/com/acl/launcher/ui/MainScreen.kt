package com.acl.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acl.launcher.core.Ai
import com.acl.launcher.core.LaunchOpt
import com.acl.launcher.core.Pack
import com.acl.launcher.core.PackJob
import com.acl.launcher.core.PackOptions
import com.acl.launcher.core.Settings
import com.acl.launcher.core.Update
import com.acl.launcher.ui.theme.AclColors
import com.acl.launcher.ui.theme.GlowBackground
import kotlinx.coroutines.launch

/** 底部标签页。 */
enum class Tab(val title: String) {
    HOME("主页"),
    PACK("AI 整合包"),
    SETTINGS("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(settings: Settings, packsRoot: java.io.File) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Box(
        Modifier
            .fillMaxSize()
            .background(AclColors.Background)
    ) {
        // 动态光晕背景
        GlowBackground(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 46.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ACL 启动器",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AclColors.Text
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "v${Update.CURRENT_VERSION}",
                    fontSize = 12.sp,
                    color = AclColors.TextFaint
                )
            }

            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.HOME -> HomeTab(settings)
                    Tab.PACK -> PackTab(settings, packsRoot)
                    Tab.SETTINGS -> SettingsTab(settings)
                }
            }

            // 底部导航
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE6FFFFFF))
                    .border(1.dp, AclColors.Border, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (t in Tab.entries) {
                    val selected = tab == t
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(AclColors.Accent, AclColors.AccentDark)
                                    )
                                } else {
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                }
                            )
                            .clickable { tab = t }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t.title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color.White else AclColors.TextDim
                        )
                    }
                }
            }
        }
    }
}

// ── 主页 ────────────────────────────────────────────────────────────────

@Composable
private fun HomeTab(settings: Settings) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("准备就绪") }
    var optResult by remember { mutableStateOf<LaunchOpt.Params?>(null) }
    var aiTesting by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf("") }

    val ramTotalGb = remember {
        (Runtime.getRuntime().maxMemory() / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1)
    }
    val cpuCores = remember { Runtime.getRuntime().availableProcessors() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionTitle("启动")
                Spacer(Modifier.height(12.dp))
                Text(
                    "MC 版本：${settings.packMcVersion}",
                    fontSize = 13.sp,
                    color = AclColors.TextDim
                )
                Text(
                    "加载器：${settings.packLoader}　模组数：${settings.packTargetCount}",
                    fontSize = 13.sp,
                    color = AclColors.TextDim
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("启动游戏（AI 优化参数）", Modifier.weight(1f)) {
                        scope.launch {
                            status = "AI 正在优化启动参数…"
                            val p = LaunchOpt.optimize(
                                settings,
                                settings.packMcVersion,
                                settings.packLoader,
                                settings.packTargetCount,
                                ramTotalGb.coerceAtLeast(1),
                                cpuCores
                            )
                            optResult = p
                            status = "参数已保存：${LaunchOpt.describe(p)}"
                        }
                    }
                }
                optResult?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    GlassCard(padding = 12) {
                        Text("优化结果", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AclColors.Text)
                        Spacer(Modifier.height(6.dp))
                        Text(LaunchOpt.describe(p), fontSize = 12.sp, color = AclColors.TextDim)
                        Spacer(Modifier.height(4.dp))
                        Text(p.note, fontSize = 11.sp, color = AclColors.TextFaint)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(status, fontSize = 12.sp, color = AclColors.TextFaint)
            }
        }

        item {
            GlassCard {
                SectionTitle("AI 助手")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (Ai.isConfigured(settings)) "已配置：${settings.aiModel}" else "未配置（到设置页填写）",
                        fontSize = 13.sp,
                        color = if (Ai.isConfigured(settings)) AclColors.Text else AclColors.TextDim,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton(if (aiTesting) "测试中" else "测试") {
                        aiTesting = true
                        aiTestResult = ""
                        scope.launch {
                            val r = Ai.test(settings)
                            aiTestResult = if (r.ok) "连通正常（${r.elapsedMs}ms）" else "失败：${r.error}"
                            aiTesting = false
                        }
                    }
                }
                if (aiTestResult.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        aiTestResult,
                        fontSize = 12.sp,
                        color = if (aiTestResult.startsWith("连通")) AclColors.Success else AclColors.Danger
                    )
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("版本更新")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        updateInfo.ifEmpty { "当前 v${Update.CURRENT_VERSION}" },
                        fontSize = 13.sp,
                        color = AclColors.TextDim,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton("检查更新") {
                        scope.launch {
                            updateInfo = "检查中…"
                            val info = Update.check(settings)
                            updateInfo = when {
                                !info.ok -> "检查失败：${info.error}"
                                Update.hasUpdate(info) -> "有新版本 v${info.version}"
                                else -> "已是最新（v${Update.CURRENT_VERSION}）"
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("关于")
                Spacer(Modifier.height(10.dp))
                Text(
                    "ACL 启动器 · Android 版\n" +
                        "由桌面版 PCL 移植，保留 AI 一键整合包、AI 启动优化、版本更新等功能。\n" +
                        "手机端不直接运行 Java 版 Minecraft，整合包产出后可导入电脑端继续使用。",
                    fontSize = 12.sp,
                    color = AclColors.TextDim
                )
            }
        }
    }
}

// ── AI 一键整合包 ───────────────────────────────────────────────────────

@Composable
private fun PackTab(settings: Settings, packsRoot: java.io.File) {
    val scope = rememberCoroutineScope()
    var mcVersions by remember { mutableStateOf(listOf<String>()) }
    var mcDropdownOpen by remember { mutableStateOf(false) }
    var loaderDropdownOpen by remember { mutableStateOf(false) }
    var demand by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<PackJob?>(null) }
    var running by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf("") }
    val logState = rememberLazyListState()
    val loaders = listOf("fabric", "forge", "neoforge", "quilt")

    LaunchedEffect(Unit) {
        mcVersions = Pack.fetchMcVersions()
    }

    // 日志自动滚到底
    LaunchedEffect(job?.lines?.size) {
        val n = job?.lines?.size ?: 0
        if (n > 0) logState.animateScrollToItem(n - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        GlassCard {
            SectionTitle("配置")
            Spacer(Modifier.height(12.dp))

            // MC 版本下拉（全部版本）
            Text("MC 版本", fontSize = 12.sp, color = AclColors.TextDim)
            Spacer(Modifier.height(4.dp))
            Box {
                GlassDropdown(
                    text = settings.packMcVersion,
                    expanded = mcDropdownOpen,
                    onExpandChange = { mcDropdownOpen = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(Modifier.height(260.dp)) {
                        items(mcVersions) { v ->
                            DropdownMenuItem(
                                text = {
                                    Text(v, fontSize = 13.sp, color = AclColors.Text)
                                },
                                onClick = {
                                    settings.packMcVersion = v
                                    mcDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 加载器下拉
            Text("加载器", fontSize = 12.sp, color = AclColors.TextDim)
            Spacer(Modifier.height(4.dp))
            Box {
                GlassDropdown(
                    text = settings.packLoader,
                    expanded = loaderDropdownOpen,
                    onExpandChange = { loaderDropdownOpen = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (l in loaders) {
                        DropdownMenuItem(
                            text = { Text(l, fontSize = 13.sp, color = AclColors.Text) },
                            onClick = {
                                settings.packLoader = l
                                loaderDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 模组数量
            Text("模组数量", fontSize = 12.sp, color = AclColors.TextDim)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = settings.packTargetCount.toString(),
                onValueChange = { s ->
                    s.toIntOrNull()?.let { settings.packTargetCount = it.coerceIn(1, 60) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = aclFieldColors()
            )

            Spacer(Modifier.height(12.dp))

            // 需求描述
            Text("需求描述", fontSize = 12.sp, color = AclColors.TextDim)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = demand,
                onValueChange = { demand = it },
                placeholder = { Text("例：生存向，带小地图、任务、光影", fontSize = 13.sp, color = AclColors.TextFaint) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                colors = aclFieldColors()
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (running) "生成中…" else "开始生成",
                    Modifier.weight(1f),
                    enabled = !running
                ) {
                    if (running) return@PrimaryButton
                    scope.launch {
                        running = true
                        resultMsg = ""
                        val o = PackOptions()
                        o.mcVersion = settings.packMcVersion
                        o.loader = settings.packLoader
                        o.demand = demand.trim()
                        o.keywords = o.demand
                        o.targetCount = settings.packTargetCount
                        val j = PackJob(o)
                        job = j
                        val dir = java.io.File(packsRoot, "ACL Pack ${o.mcVersion} ${o.loader}")
                        Pack.run(j, settings, dir)
                        resultMsg = if (j.succeeded) {
                            "完成：${j.reportPath}"
                        } else if (j.canceled) {
                            "已取消"
                        } else {
                            "未完成，请看下方日志"
                        }
                        running = false
                    }
                }
                GhostButton("取消", enabled = running) {
                    job?.canceled = true
                }
            }
            if (resultMsg.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(resultMsg, fontSize = 12.sp, color = AclColors.TextDim)
            }
        }

        Spacer(Modifier.height(14.dp))

        // 流程
        job?.let { j ->
            GlassCard {
                SectionTitle("流程")
                Spacer(Modifier.height(10.dp))
                for (s in j.steps) {
                    StatusDot(
                        active = s.state == 1,
                        done = s.state == 2,
                        text = if (s.detail.isEmpty()) s.title else "${s.title}　${s.detail}"
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            // 日志
            GlassCard {
                SectionTitle("实时日志")
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    Modifier.height(240.dp),
                    state = logState
                ) {
                    items(j.snapshotLines()) { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            color = if (line.contains("失败") || line.contains("警告")) AclColors.Warning else AclColors.TextDim
                        )
                    }
                }
            }
        } ?: GlassCard {
            SectionTitle("流程")
            Spacer(Modifier.height(10.dp))
            EmptyHint("点「开始生成」后，这里会显示六个步骤的实时进度")
        }
    }
}

// ── 设置 ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(settings: Settings) {
    val scope = rememberCoroutineScope()
    var aiUrl by remember { mutableStateOf(settings.aiUrl) }
    var aiKey by remember { mutableStateOf(settings.aiKey) }
    var aiModel by remember { mutableStateOf(settings.aiModel) }
    var aiEnabled by remember { mutableStateOf(settings.aiEnabled) }
    var glass by remember { mutableStateOf(settings.glassEnabled) }
    var updateSrc by remember { mutableStateOf(settings.updateSource) }
    var saved by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                SectionTitle("AI 助手")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (aiEnabled) "已开启" else "已关闭",
                        fontSize = 13.sp,
                        color = AclColors.TextDim,
                        modifier = Modifier.weight(1f)
                    )
                    SwitchBox(aiEnabled) { aiEnabled = it }
                }
                Spacer(Modifier.height(10.dp))
                Text("接口地址", fontSize = 12.sp, color = AclColors.TextDim)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = aiUrl,
                    onValueChange = { aiUrl = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = aclFieldColors()
                )
                Spacer(Modifier.height(10.dp))
                Text("API 密钥", fontSize = 12.sp, color = AclColors.TextDim)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = aiKey,
                    onValueChange = { aiKey = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = aclFieldColors()
                )
                Spacer(Modifier.height(10.dp))
                Text("模型", fontSize = 12.sp, color = AclColors.TextDim)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = { aiModel = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = aclFieldColors()
                )
            }
        }

        item {
            GlassCard {
                SectionTitle("外观")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("毛玻璃效果", fontSize = 13.sp, color = AclColors.TextDim, modifier = Modifier.weight(1f))
                    SwitchBox(glass) { glass = it }
                }
            }
        }

        item {
            GlassCard {
                SectionTitle("更新")
                Spacer(Modifier.height(12.dp))
                Text("更新源", fontSize = 12.sp, color = AclColors.TextDim)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = updateSrc,
                    onValueChange = { updateSrc = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = aclFieldColors()
                )
            }
        }

        item {
            PrimaryButton("保存设置", Modifier.fillMaxWidth()) {
                settings.aiUrl = aiUrl.trim()
                settings.aiKey = aiKey.trim()
                settings.aiModel = aiModel.trim()
                settings.aiEnabled = aiEnabled
                settings.glassEnabled = glass
                settings.updateSource = updateSrc.trim()
                saved = "已保存"
                scope.launch { }
            }
            if (saved.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(saved, fontSize = 12.sp, color = AclColors.Success)
            }
        }
    }
}

// ── 小部件 ──────────────────────────────────────────────────────────────

/** 毛玻璃下拉框：清白底 + 深墨字，保证对比度。 */
@Composable
private fun GlassDropdown(
    text: String,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xE6FFFFFF))
                .border(1.dp, AclColors.Border, RoundedCornerShape(10.dp))
                .clickable { onExpandChange(true) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, fontSize = 13.sp, color = AclColors.Text, modifier = Modifier.weight(1f))
                Text("▾", fontSize = 12.sp, color = AclColors.TextDim)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) },
            modifier = Modifier
                .background(Color(0xF2FFFFFF))
                .border(1.dp, AclColors.Border, RoundedCornerShape(12.dp))
        ) {
            content()
        }
    }
}

/** 开关。 */
@Composable
private fun SwitchBox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) AclColors.Accent else Color(0xFFCFD6DD))
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
        )
    }
}

/** 输入框配色：清白底 + 深墨字，高对比度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun aclFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AclColors.Text,
    unfocusedTextColor = AclColors.Text,
    focusedContainerColor = Color(0xE6FFFFFF),
    unfocusedContainerColor = Color(0xE6FFFFFF),
    focusedBorderColor = AclColors.Accent,
    unfocusedBorderColor = AclColors.Border,
    cursorColor = AclColors.Accent,
    focusedPlaceholderColor = AclColors.TextFaint,
    unfocusedPlaceholderColor = AclColors.TextFaint
)
