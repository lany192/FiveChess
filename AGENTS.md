# AGENTS.md

本文件为 AI 代理在本仓库工作时提供指引。

## 项目

Android 五子棋应用，单 `app` 模块，100% Kotlin。手写 MVI（无 MVI 框架、无 DI），View 体系 + ViewBinding（无 Compose）。三种对局模式：本地双人（`ui/person`）、人机三档难度（`ui/robot`，引擎在 `domain/ai/RobotAI.kt`）、局域网 WiFi 联机（`ui/connect` + `ui/wifi`）。

## 构建

- 依赖统一在 `gradle/libs.versions.toml`（version catalog）管理；新增依赖写在那里，不要内联。
- `settings.gradle.kts` 中阿里云镜像仓库优先（国内网络）；依赖解析失败通常是镜像问题，而非构件缺失。
- Java 11 / `jvmTarget = 11`，compileSdk/targetSdk 36，minSdk 24。
- AGP 9.x 内置 Kotlin 2.2 —— 不要在 plugins 里加 `org.jetbrains.kotlin.android`；Kotlin stdlib 会自动注入。
- 测试：`./gradlew :app:testDebugUnitTest`（覆盖 `domain/engine`、`domain/ai` 与 `data/net/Protocol.kt` 有线协议的 JVM 测试）。

## 架构

分层依赖，外层可依赖内层，禁止反向：

- `domain/` —— 纯 Kotlin，**禁止 `android.*` 导入**（这是保持 JVM 可测的前提）。`engine/GameEngine` 掌管规则并返回 `EngineResult(state, events)`；`model/` 持有不可变快照（`GameState`、`Side`，其中 BLACK=1/WHITE=2，与 AI 的 `Array<IntArray>` 编码一致）；`ai/RobotAI` + `ai/Difficulty`（EASY/MEDIUM/HARD）。
- `core/mvi/MviViewModel` —— 基类：Intent 经无限容量 `Channel` 串行处理（对齐旧 Handler 主线程队列语义）；State 是 `StateFlow`，渲染须幂等；Effect 是 `SharedFlow(replay=0)`，承载一次性 toast/弹窗/导航。每个特性在 `ui/<feature>/` 下含 `<Feature>Contract.kt`（Intent/State/Effect）、`<Feature>ViewModel.kt`、`<Feature>Activity.kt`。
- `data/net/` —— `Protocol.kt`（字节级编解码 + `TcpFrameReader`）、`LanDiscoveryManager`（UDP 发现/握手/聊天）、`LanGameClient`（TCP 对局）。阻塞式 `DatagramSocket.receive()` 不响应协程取消 —— 须先关闭 socket 再取消 scope 来停止。
- `ui/common/GameBoardView` —— SurfaceView 棋盘；`render(state)` 是唯一渲染入口，`onCellTapped` 是唯一输入回调；自身不含任何对局逻辑。

## 有线协议冻结（设备间互通）

局域网协议字节已冻结；不同版本的两台设备必须保持兼容。只允许**追加**新的消息类型字节。

- UDP 组播发现 230.0.2.2:1688：广播帧 `[nameLen][name][ipLen][ip][type末字节]`（JOIN=0 / EXIT=1）；JOIN 广播以单播带类型 UDP 包应答，EXIT 不应答。
- UDP 单播端口 2599：`[type][nameLen][name][ipLen][ip]`，ASK=11 / AGREE=12 / REJECT=13 / UDP_JOIN=0；CHAT=14 追加 `[chatLen][chat]`。
- TCP 端口 8899 对局：`[len][type][payload]`，len 为含自身的整帧总长；ADD_CHESS=0（payload `[x][y]`）、ROLLBACK_ASK=2 / ROLLBACK_AGREE=3 / ROLLBACK_REJECT=4、RESTART=5、DRAW_ASK=6 / DRAW_AGREE=7 / DRAW_REJECT=8、RESIGN=9。
- 编码与解码同在 `Protocol.kt`，由 `ProtocolTest` 字节冻结用例守护 —— 两侧必须同步修改，并在同一局域网的真机双端验证。
- 接收侧对端 IP 以 UDP 包的传输层源地址为准，包体中的自报 IP 仅作兜底（`WifiManager.connectionInfo.ipAddress` 是废弃 API，可能返回陈旧/错误地址，自报不可信）。

## 行为要点

- 联机角色：被请求方（server）执黑先手；发起方（client）执白。
- 联机悔棋：双端各自移除"请求方最后一手及其之后所有棋子"（从同一份落子历史推导）；重开通过 RESTART 消息同步 —— 不要退化为各删一子或仅本地清盘。
- 求和为协商制（ASK/AGREE/REJECT，仿悔棋）；认输为单方宣告（RESIGN），两端从同一事件各自推导胜场计数。
- 胜利判定为 4 个方向上连续五子；棋盘 15×15（`GameEngine` 默认）。

## 提交风格

提交信息从简；近期采用 Conventional Commits 前缀 + 中文主题（如 `chore(build): 升级Gradle和依赖配置`）。
