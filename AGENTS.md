# AGENTS.md

本文件为 AI 代理在本仓库工作时提供指引。

## 项目

Android 五子棋应用，单 `app` 模块，100% Kotlin。手写 MVI（无 MVI 框架、无 DI），View 体系 + ViewBinding（无 Compose）。四种对局模式：本地双人（`ui/person`）、人机 25 种算法 × 5 档难度（`ui/robot`，对局页顶部「对手设置」进 `ui/robot/AiSelectActivity`，引擎在 `domain/ai/`）、局域网 WiFi 联机（`ui/connect`）、蓝牙联机（`ui/bt`）。两种联机模式的对局页与对局逻辑共用 `ui/net`，差别只在建连层。

## 构建

- 依赖统一在 `gradle/libs.versions.toml`（version catalog）管理；新增依赖写在那里，不要内联。
- `settings.gradle.kts` 中阿里云镜像仓库优先（国内网络）；依赖解析失败通常是镜像问题，而非构件缺失。
- Java 11 / `jvmTarget = 11`，compileSdk/targetSdk 36，minSdk 24。
- AGP 9.x 内置 Kotlin 2.2 —— 不要在 plugins 里加 `org.jetbrains.kotlin.android`；Kotlin stdlib 会自动注入。
- Room 用 KSP 生成代码：**KSP 必须用 2.3.x 的独立版本号**（如 `2.3.12`），2.2.x 的旧版本号会报 "Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin"。
- 测试：`./gradlew :app:testDebugUnitTest`（覆盖 `domain/engine`、`domain/ai` 与 `data/net/Protocol.kt` 有线协议的 JVM 测试）。

## 发布产物加固（仅 release）

`app/build.gradle.kts` 通过 AGP scoped-artifacts transform 把 `buildSrc` 的 `EncryptStringsTask` 接进了 release 编译链：在 R8 之前用 ASM 把每个类的 String 常量逐条 XOR 加密（每条常量一个随机偏移），并注入 `com.github.lany192.gomoku.guard.StrGuard` 负责运行时解密（`ldc` 密文 → `StrGuard.d(密文, 偏移)`）。构建日志会打印“字符串加密：N 个类、M 条字符串常量”。

- 任务只改写 `ldc` 指令：写在注解参数里的字符串（如 `@Suppress("...")`）不会被加密，会以明文留在 dex 里 —— 别把敏感常量放注解参数。同理，字符串加密只覆盖常量池，不覆盖资源文件（strings.xml 等），资源混淆是另一个话题。
- 解密在运行时还原原值，业务代码无感知；`domain/` 的纯 Kotlin 约束不受影响。
- **debug 构建不加密**（保留明文方便调试）：查到"字符串还是明文"时先确认是不是在 debug 变体里查的。
- R8 会重命名注入的 `StrGuard` 类和 `d` 方法（映射见 `mapping.txt`），这是预期行为，不要为它加 keep。
- `-keepattributes !LineNumberTable` 实测是空操作（规则进了合并配置但 dex 行号原样保留，见 `proguard-rules.pro` 注释），行号在 release 产物里无法通过 R8 配置去除。

## 架构

分层依赖，外层可依赖内层，禁止反向：

- `domain/` —— 纯 Kotlin，**禁止 `android.*` 导入**（这是保持 JVM 可测的前提）。`engine/GameEngine` 掌管规则并返回 `EngineResult(state, events)`；`model/` 持有不可变快照（`GameState`、`Side`，其中 BLACK=1/WHITE=2，与 AI 的 `Array<IntArray>` 编码一致）。
- `core/mvi/MviViewModel` —— 基类：Intent 经无限容量 `Channel` 串行处理（对齐旧 Handler 主线程队列语义）；State 是 `StateFlow`，渲染须幂等；Effect 是 `SharedFlow(replay=0)`，承载一次性 toast/弹窗/导航。每个特性在 `ui/<feature>/` 下含 `<Feature>Contract.kt`（Intent/State/Effect）、`<Feature>ViewModel.kt`、`<Feature>Activity.kt`。例外是 `ui/net/`：联机对局页与对局 ViewModel 由局域网和蓝牙共用，不是单一特性。
- `data/net/` —— `Protocol.kt`（字节级编解码 + `TcpFrameReader`）、`LanDiscoveryManager`（UDP 发现/握手/聊天）、`LanGameClient`（TCP 对局）、`GameTransport`（对局传输抽象，`ui/net` 唯一依赖的建连接口）。阻塞式 `DatagramSocket.receive()` / `Socket.read()` 不响应协程取消 —— 须先关闭 socket 再取消 scope 来停止。
- `data/bt/` —— `BtDiscoveryManager`（已配对列表 + 系统发现广播 + RFCOMM 握手）、`BtGameClient`（RFCOMM 对局，实现 `GameTransport`）。`accept()`/`read()` 同样靠关闭 socket 打断。`BtDiscoveryManager` 是一次性的：`stop()` 会取消 scope，不可复用。
- `ui/common/GameBoardView` —— SurfaceView 棋盘；`render(state)` 是唯一渲染入口，`onCellTapped` 是唯一输入回调；自身不含任何对局逻辑。

## 人机 AI（`domain/ai/`）

用户可选 25 种算法（`AiAlgorithm`，按 `AiFamily` 六家族连续排列，顺序即 UI 展示顺序）；算法文案与选择页在 `ui/robot`（`R.string` 只在 UI 层，`domain` 不含资源 id）。

- 统一接口 `GomokuAI`：棋盘 `Array<IntArray>` 按 `[x][y]` 索引，0 空 / 1 黑（人）/ 2 白（AI 永远执白）；**实现必须先拷贝入参**。`AiAlgorithm.SHAPE_SCORE` 即原 `RobotAI.kt`（行为冻结，由 `RobotAITest` 守护），也是 `AiAlgorithm.DEFAULT`。
- `AiEngineFactory` 是算法→实现的唯一映射（穷尽 `when`，漏项编译失败），`AiTunings` 是档位→参数的唯一映射（时间预算、模拟数、表位数都有硬上限），`AiEnginePool` 只缓存学习类引擎（切走再切回不丢已学权重）。
- 新引擎一律继承 `core/AbstractAiEngine`：拷贝棋盘 → 快照档位 → `Tactics.mandatory` 战术预判（我连五 > 堵对手连五 > 我活四，按 `alertPercent` 概率看漏）→ 子类只写 `decide()`。**`Tactics` 底线不可省**，否则弱档会送人头。
- 共享设施在 `core/`：`ShapeScores`（分值常量）、`BoardScanner`、`CandidateGenerator`（候选生成，排序必须全序，保证种子确定性）、`Evaluator` 系列（棋型 / 模式表 / 神经网络 / 学习类共用的线性价值函数）。
- 学习类（TD / Q / AlphaZero / 遗传）权重按枚举名存 Room（`data/db/RoomAiWeightStore`，modelId = 算法枚举名）。`AiWeightStore` 是**阻塞式**接口，只在后台线程调用；引擎首次 `getPosition` 懒加载权重。
- 确定性约定：随机走注入的 `Random`，时间预算走注入的 `clock: () -> Long`（测试传 `{ 0L }` 关掉预算）；引擎构造必须 O(1)，大表一律 `by lazy`。
- `ui/robot/RobotGameViewModel`：搜索与 `onGameOver`/`onGameReset` 共用一条单线程 `aiDispatcher`，满足"同一实例不可并发调用"；思考中途改算法记 `pendingAlgorithm`，等本次落子落地后再换引擎（任一时刻只有一个引擎在跑）。

## 有线协议冻结（设备间互通）

局域网协议字节已冻结；不同版本的两台设备必须保持兼容。只允许**追加**新的消息类型字节。

- UDP 组播发现 230.0.2.2:1688：广播帧 `[nameLen][name][ipLen][ip][type末字节]`（JOIN=0 / EXIT=1）；JOIN 广播以单播带类型 UDP 包应答，EXIT 不应答。
- UDP 单播端口 2599：`[type][nameLen][name][ipLen][ip]`，ASK=11 / AGREE=12 / REJECT=13 / UDP_JOIN=0；CHAT=14 追加 `[chatLen][chat]`。
- TCP 端口 8899 对局：`[len][type][payload]`，len 为含自身的整帧总长；ADD_CHESS=0（payload `[x][y]`）、ROLLBACK_ASK=2 / ROLLBACK_AGREE=3 / ROLLBACK_REJECT=4、RESTART=5、DRAW_ASK=6 / DRAW_AGREE=7 / DRAW_REJECT=8、RESIGN=9。
- 编码与解码同在 `Protocol.kt`，由 `ProtocolTest` 字节冻结用例守护 —— 两侧必须同步修改，并在同一局域网的真机双端验证。
- 接收侧对端 IP 以 UDP 包的传输层源地址为准，包体中的自报 IP 仅作兜底（`WifiManager.connectionInfo.ipAddress` 是废弃 API，可能返回陈旧/错误地址，自报不可信）。

## 蓝牙联机（另一条传输，不碰上述字节）

蓝牙没有组播信令，**联机请求本身就是一条 RFCOMM 连接**，控制通道与对局通道都走 `Protocol.BT_UUID` 这一个服务记录：

- 联机页：被叫方常驻 `listenUsingRfcommWithServiceRecord`，`accept()` 返回即收到请求 → 弹窗裁决 → 回写一个字节 `BtSignal`（AGREE=20 / REJECT=21）后关闭；主叫方 `connect()` 后阻塞读该字节，读到 AGREE 才算连上。对端已有待裁决连接时直接回 REJECT（对齐局域网的"忙则拒绝"）。
- **同意联机的顺序不能动**：先 `stopListening()` 让出服务 UUID，再回写 AGREE，最后 `stop()`。反过来主叫可能在收到 AGREE 后立刻连上这个即将关闭的旧监听口，随即被判为断线。
- 对局页：两端用同一个 UUID 重新建连（服务端 `accept(30s)`、客户端 20 次 × 300ms 重试，覆盖对端换监听口的空窗）。帧格式完全复用 `Protocol.encodeTcp`/`TcpFrameReader`，因此悔棋/重开/求和/认输等语义自动与局域网一致。
- 设备身份用 MAC 地址，复用 `ConnectionItem.ip` 字段承载（同局域网版把 IP 塞进该字段的做法）。
- 主叫发起前必须 `cancelDiscovery()`：发现过程会显著拖慢甚至中断 RFCOMM 建连。
- 蓝牙运行时权限（`BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`，仅 API 31+）与蓝牙开关在 `BtConnectActivity` 中先就绪再创建 ViewModel。
- 系统广播（`ACTION_FOUND` 等）来自 `com.android.bluetooth` 进程，注册时必须 `RECEIVER_EXPORTED`，用 `NOT_EXPORTED` 在 Android 14+ 收不到。
- 蓝牙模式没有聊天（蓝牙没有连接外的带外通道）。
- **本机蓝牙地址应用拿不到**：Android 6 起 `BluetoothAdapter.getAddress()` 只回占位值 `02:00:00:00:00:00`，11 起更是要求 `LOCAL_MAC_ADDRESS` 系统权限。联机页顶部的"本机"身份因此以名称为主、地址为可选（`localBluetoothAddress` 返回 null 就不显示）—— 不要试图把它做成必然有的字段，也不要为此走反射/系统设置读取。

## 行为要点

- 联机角色：被请求方（server）执黑先手；发起方（client）执白。
- 联机悔棋：双端各自移除"请求方最后一手及其之后所有棋子"（从同一份落子历史推导）；重开通过 RESTART 消息同步 —— 不要退化为各删一子或仅本地清盘。
- 求和为协商制（ASK/AGREE/REJECT，仿悔棋）；认输为单方宣告（RESIGN），两端从同一事件各自推导胜场计数。
- 胜利判定为 4 个方向上连续五子；棋盘 15×15（`GameEngine` 默认）。
- 以上联机行为由 `ui/net/NetGameViewModel` 统一实现，**局域网与蓝牙必须保持一致** —— 要给联机加规则请改这里，不要在某一端单独加。

## 提交风格

提交信息从简；近期采用 Conventional Commits 前缀 + 中文主题（如 `chore(build): 升级Gradle和依赖配置`）。
