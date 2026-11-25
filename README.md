# ACL 启动器

> Windows 端的 Minecraft 启动器：全量版本下载、AI 崩溃诊断、6 位房间码联机，都装在一个 5.4 MB 的单文件里。

[![版本](https://img.shields.io/badge/version-0.9.0-35e07a?style=flat-square)](../../releases)
[![平台](https://img.shields.io/badge/platform-Windows%2010%20%7C%2011-1f9c5c?style=flat-square)](#系统要求)
[![运行时](https://img.shields.io/badge/runtime-.NET%20Framework%204.8-2b6f4d?style=flat-square)](#系统要求)
[![许可](https://img.shields.io/badge/license-PCL%20分发有限许可-b08a3e?style=flat-square)](#许可与合规)

本仓库同时是这个项目的**官网与下载站**（GitHub Pages）。直接访问 Pages 地址即可下载，不需要进仓库找文件。

---

## 目录

- [功能](#功能)
- [下载](#下载)
- [快速开始](#快速开始)
- [联机怎么用](#联机怎么用)
- [系统要求](#系统要求)
- [项目结构](#项目结构)
- [从源码构建](#从源码构建)
- [更新日志](#更新日志)
- [常见问题](#常见问题)
- [许可与合规](#许可与合规)

---

## 功能

### 1. 全量版本下载

不是"只给正式版"的半成品：

| 分类 | 覆盖内容 |
|---|---|
| 原版游戏 | 最新版本、正式版、预览版（快照）、远古版、愚人节版 |
| 社区资源 | Mod、整合包、数据包、资源包、光影包 |
| 加载器 | Forge（含推荐版）、NeoForge、Fabric、Fabric API、LiteLoader、OptiFine、OptiFabric |

版本清单直接拉官方 manifest，分类齐全、可回溯到远古版本。

### 2. AI 崩溃诊断

崩溃后不是只丢给你一段红字：

1. 先跑**本地规则库**（离线、毫秒级、结论可解释），命中就直接给出原因与修复步骤；
2. 规则没命中，再交给大模型分析，提示词固定输出「结论 / 置信度 / 证据 / 步骤 / 可疑对象」。

AI 功能**默认关闭**，开启后才会上传日志；密钥加密存在本机配置里。

### 3. 6 位房间码联机

房主在游戏里「对局域网开放」，启动器生成一个 6 位码；朋友输入码即可进入。

- **局域网模式**：UDP 广播发现 + TCP 直连，零服务器，延迟就是局域网延迟
- **中继模式**：双方都主动连出到中继，按房间码配对后双向透传；房主不需要公网 IP、不用端口映射

> 6 位数字无法表达对方 IP，跨网必然需要一个中转节点。中继脚本在本仓库 `server/acl-relay.js`。

### 4. 自己写的下载引擎

- **预分配 + 分片定点写入**，不产生临时文件、不做收尾合并（省一次全量读、一次全量写）
- 分片级重试、实时速度与剩余时间
- 实测：`162.5 MB / 4.07 s ≈ 38 MB/s`（清华 TUNA 镜像，与 `curl` 单连接持平；在限速源上优势更明显）

### 5. 暗色界面 · 左缘工具坞

- 鼠标贴到窗口**左边缘**，工具坞滑出
- 点九宫格展开**右半圆轮盘**：启动 / 下载 / 版本 / 设置 / 更多 / 联机 / 百宝箱
- 工具坞内含最小化、关闭；**窗口任意空白处都能拖动**，控件照常响应
- 主题为单一 HSL 公式生成的 8 级色阶，整体深墨绿底、低饱和强调色

### 6. 单文件 · 绿色免安装

所有依赖内嵌在 exe 里，运行时从资源加载。**不写注册表**，配置与日志都在程序目录的 `ACL` 文件夹。

---

## 下载

### 方式一：官网（推荐）

打开 Pages 站点，点「下载 Windows 版」。仓库根目录的 `index.html` 就是站点，`download/` 里放安装包。

### 方式二：仓库直链

| 文件 | 说明 |
|---|---|
| [`download/ACL-Launcher-v0.9.0.exe`](download/ACL-Launcher-v0.9.0.exe) | 单文件，双击即用 |
| [`download/ACL-Launcher-v0.9.0.zip`](download/ACL-Launcher-v0.9.0.zip) | 压缩包，含说明与中继脚本 |

**校验值**（v0.9.0）

```
文件      ACL-Launcher-v0.9.0.exe
大小      6,417,920 字节（6.12 MB）
SHA-256   61291e428ad3c2a9622a504ce754c308cd3131d933be788b1748a388fca9ee1f
```

校验方法（PowerShell）：

```powershell
Get-FileHash .\ACL-Launcher-v0.9.0.exe -Algorithm SHA256
```

---

## 快速开始

1. **下载 exe**，放进一个你打算长期保留的目录，例如 `D:\Games\ACL`
   （不要放在桌面或下载文件夹，之后容易误删）
2. **双击运行**。首次启动会在同目录创建：
   ```
   ACL\           配置、日志、皮肤
   .minecraft\    游戏本体与存档
   ```
3. **装游戏**：进入「下载」→ 选一个正式版 → 需要 Mod 就顺手装 Forge / Fabric
4. **启动**：回「启动」→ 选版本 → 点启动

> 提示：`ACL` 和 `.minecraft` 跟着 exe 走。整个文件夹拷到别的机器上就能继续用，存档不会丢。

---

## 联机怎么用

**房主**

1. 进游戏，`Esc` → 「对局域网开放」→ 记下端口（或让启动器自动探测）
2. 启动器里贴左边缘 → 工具坞 → 九宫格 → 轮盘「联机」
3. 点「自动探测」拿到端口 → 「创建房间」→ 得到 6 位码
4. 把 6 位码发给朋友

**访客**

1. 同样打开「联机」，输入 6 位码 → 「加入」
2. 界面给出 `127.0.0.1:端口`
3. 游戏里「多人游戏」→「添加服务器」→ 粘贴这个地址

**跨网怎么办**

需要一个能公网访问的中继：

```bash
# 在任意有公网 IP 的机器上（需要 Node.js）
node server/acl-relay.js 25566
```

然后双方在「中继设置」里填同一个 `地址:端口`，再按上面的流程建房 / 加入。
中继只做字节转发，不解析协议、不存数据；带宽取决于中继机器的上行。

---

## 系统要求

| 项 | 要求 |
|---|---|
| 系统 | Windows 10（1809+）/ Windows 11 |
| 运行时 | .NET Framework 4.8（Win10 1809 起系统自带） |
| 内存 | 4 GB 起（游戏本身另算） |
| 磁盘 | 启动器约 6 MB；游戏按版本 1–3 GB |
| Java | 启动器会自动探测；没有就装 Java 8 / 17 / 21 |

**不支持** macOS / Linux：界面基于 WPF 与 .NET Framework。

---

## 项目结构

```
ACL_Launcher/
├── index.html                  官网首页（GitHub Pages 入口）
├── 404.html                    自定义 404
├── assets/                     样式、脚本、图标、截图、分享图
│   ├── style.css
│   ├── main.js
│   ├── og.png                  社交分享图 1200×630
│   └── shot-*.png              界面截图
├── download/                   安装包（exe / zip）
├── server/
│   └── acl-relay.js            联机中继服务端（Node.js，无依赖）
├── manifest.webmanifest        PWA 清单
├── robots.txt
├── sitemap.xml
└── .nojekyll                   让 Pages 跳过 Jekyll 处理
```

源码工程不在此仓库，`download/` 里的产物由源码构建得到。

---

## 从源码构建

> 仅 Windows。需要 **.NET 8 SDK** 与 **VS 2022 Build Tools**（勾选「.NET 桌面开发」）。

```cmd
:: 1) 先编三个 C# 子工程（SDK 式工程用 dotnet）
dotnet build MeloongCore\Shared\MeloongCore.csproj -c Release
dotnet build MeloongCore\Wpf\MeloongCore.Wpf.csproj -c Release
dotnet build PCLCS\PCLCS.csproj -c Release

:: 2) 再用 VS 的 MSBuild 编 VB 主工程（XAML 代码生成只有它支持）
"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\MSBuild\Current\Bin\MSBuild.exe" ^
  "Plain Craft Launcher 2\Plain Craft Launcher 2.vbproj" /p:Configuration=Release /p:BuildProjectReferences=false
```

几个已知的坑：

- 主工程是 `net48`，`.NET 8 SDK` 的 `dotnet build` **编不了 WPF 的 XAML**（会报 `BC30590` 找不到事件），必须用 VS 的 MSBuild
- Build Tools 不含 .NET SDK 工作负载，所以 SDK 式子工程只能交给 `dotnet`
- 子工程把 `LangVersion` 写死成 13.0，SDK 8 的 Roslyn 只到 12，需要改成 `12.0`
- 自检参数：`ACL.exe --acl-selftest out.txt`（渲染页面 + 调一次 AI 并回报结果）

---

## 更新日志


## 常见问题

<details>
<summary><b>提示缺少 .NET Framework 怎么办？</b></summary>

去微软官网装 .NET Framework 4.8 运行时。Windows 10 1809 及 Windows 11 已经自带。
</details>

<details>
<summary><b>打开就闪退，没有报错</b></summary>

先看程序目录 `ACL\` 下的日志文件（最近的 `Log*.txt`）。另外确认没有另一个实例在运行——
启动器有单例锁，重复启动会直接退出。
</details>

<details>
<summary><b>下载很慢</b></summary>

在「设置 → 下载」里切换下载源，或调整下载线程数。国内建议用镜像源。
</details>

<details>
<summary><b>联机房间码找不到房间</b></summary>

局域网模式只在**同一网段**内广播可见。跨网必须配置中继地址，并且双方填的是同一个。
另外检查防火墙是否拦截了 UDP 45678（房间广播）与 TCP 中继端口。
</details>

<details>
<summary><b>会不会上传我的信息？</b></summary>

AI 诊断默认关闭；开启后只发送崩溃日志与系统环境，不含账号密码。配置里的 API 密钥加密存在本机。
</details>

---

## 许可与合规

**这一节请务必读完。**

本项目**基于 [Plain Craft Launcher (PCL)](https://github.com/Meloong-Git/PCL) 的源码修改而来**，不是从零实现。
PCL 随源码附带的《PCL 分发有限许可》规定：若将其用于实现 Minecraft 启动 / 下载 / Mod 管理等实质功能，
即视为「重度使用」，分发时需满足：

1. 名称以 `Plain Craft Launcher (PCL)` 开头（可加后缀），不得让人误以为是官方版本
2. Windows 版本界面主色调保持蓝色
3. 关于页首位署名原作者并附赞助链接，同时公开修改后的源代码
4. 保留原 `LICENCE` 文件
5. 包含启动功能时，实现与 `McLaunchPrecheck` 一致的正版购买劝导

**当前这个构建用于个人自用**：名称、主色（深墨绿）、关于页署名都未按上述条款调整。
如果要**公开发布或分发**，请先完成上述调整，否则属于违反原项目许可。

游戏本体与所有资源均从官方或公开镜像下载，本仓库**不包含、不分发任何 Minecraft 游戏文件**。
Minecraft 是 Mojang Studios 的商标，本项目与 Mojang 及 PCL 官方均无隶属关系。

---

## 部署这个站点

1. 把所有文件推到仓库默认分支（`main`）
2. 仓库 `Settings → Pages` → Source 选 `Deploy from a branch` → 分支 `main`、目录 `/ (root)`
3. **把以下文件里的占位域名 `https://YOUR-NAME.github.io/ACL_Launcher/` 换成你的真实地址**：
   - `index.html`：`canonical`、`og:url`、`og:image`、`twitter:image`、两处 JSON-LD 的 `url` / `downloadUrl`
   - `sitemap.xml`：三处 `<loc>`
   - `robots.txt`：`Sitemap:` 一行
4. 首次部署后到 [Google Search Console](https://search.google.com/search-console) 提交 `sitemap.xml`
5. 换版本时：把新 exe 放进 `download/`，更新 `index.html` 里的版本号、文件大小、SHA-256，以及 `sitemap.xml` 的 `lastmod`

> `download/` 已在 `robots.txt` 里 `Disallow`，避免搜索引擎把 exe 当网页索引；这不影响正常下载。
