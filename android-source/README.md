# ACL 启动器 · Android 版源码

Windows 桌面端 PCL/ACL 的手机版移植：**Kotlin + Jetpack Compose**，包名 `com.acl.launcher`。

## 构建

需要 JDK 17（Gradle/AGP 必须）、Android SDK（compileSdk 36 / build-tools 34+）。

```bash
export JAVA_HOME="C:\Program Files\Zulu\zulu-17"
export ANDROID_HOME="C:\Android\Sdk"

gradle :app:assembleRelease --no-daemon --console=plain
```

产物：`app/build/outputs/apk/release/app-release.apk`

> `gradle.properties` 里 `android.overridePathCheck=true` 是必需的（工程路径含中文）。

## 目录

| 路径 | 内容 |
|---|---|
| `app/src/main/java/com/acl/launcher/core/` | 核心服务层（对应桌面版 `Modules/ACL/`） |
| `app/src/main/java/com/acl/launcher/ui/` | Compose 界面 |
| `app/src/main/res/` | 图标、主题、字符串 |

## 核心模块

| 文件 | 对应桌面版 | 说明 |
|---|---|---|
| `core/Http.kt` | `AclDownload.vb` | 连接池、gzip 解压、Range 分片并发下载 |
| `core/Pack.kt` | `AclPack.vb` | AI 一键整合包全流程（搜索/版本匹配/依赖/下载全并行） |
| `core/Ai.kt` | `AclAi.vb` | OpenAI 兼容 AI 客户端，含空 content 重试 |
| `core/LaunchOpt.kt` | `AclLaunchOpt.vb` | AI 启动参数优化（JVM/GC/内存） |
| `core/Diagnose.kt` | `AclDiagnose.vb` | 19 条崩溃规则本地诊断 |
| `core/Update.kt` | `AclUpdate.vb` | 版本检查 |
| `core/Settings.kt` | `Settings.vb` | SharedPreferences 设置存储，键名与桌面版一致 |
| `core/SelfTest.kt` | `--acl-selftest` | 纯 JVM 自检，不依赖 Android |

## 移植中踩到的坑

1. **Android 的 `HttpURLConnection` 不自动解 gzip**。请求头声明了 `Accept-Encoding: gzip` 就必须自己按
   `Content-Encoding` 解压，否则拿到的是压缩二进制，JSON 解析直接报
   `Unexpected JSON token at offset 0`。
2. **AGP 9.x 启用 Compose 必须加 `org.jetbrains.kotlin.plugin.compose` 插件**，否则报
   `the Compose Compiler Gradle plugin is required`。
3. **不要在协程里嵌套 `runBlocking`**。后台线程直接用阻塞式 API（`*Blocking`），
   否则会占死调度器线程。
4. **Kotlin 属性 setter 不能返回表达式**：`set(v) = prefs.edit()...apply()` 编译不过，
   必须写成块式 `set(v) { prefs.edit()...apply() }`。

## 与桌面版的差异

手机端不能直接运行 Java 版 Minecraft，因此「AI 自主运行测试」那一步会标记为跳过，
整合包产出后可导入电脑端继续使用。其余功能（AI 解析、依赖补齐、适配选配、并发下载、
启动参数优化、版本更新）行为一致。
