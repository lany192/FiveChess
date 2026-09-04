# AGENTS.md

This file provides guidance to the AI agent when working with code in this repository.

## Project

Android Gomoku (五子棋) app, single `app` module, 100% Kotlin. Hand-rolled MVI (no MVI framework, no DI) with View-based UI + ViewBinding (no Compose). Three game modes: local 2-player (`ui/person`), vs AI with 3 difficulty levels (`ui/robot`, engine in `domain/ai/RobotAI.kt`), LAN WiFi (`ui/connect` + `ui/wifi`).

## Build

- Dependencies are managed in `gradle/libs.versions.toml` (version catalog); add new deps there, not inline.
- Repositories use the Aliyun mirror first (China network) in `settings.gradle.kts`; resolution failures are often mirror-related, not missing artifacts.
- Java 11 / `jvmTarget = 11`, compileSdk/targetSdk 36, minSdk 24.
- AGP 9.x ships Kotlin 2.2 built-in — do NOT add `org.jetbrains.kotlin.android` to plugins; the Kotlin stdlib is injected automatically.
- Tests: `./gradlew :app:testDebugUnitTest` (JVM tests for `domain/engine`, `domain/ai`, and the wire protocol in `data/net/Protocol.kt`).

## Architecture

Layers, outer may depend on inner, never the reverse:

- `domain/` — pure Kotlin, **no `android.*` imports** (this is what keeps it JVM-testable). `engine/GameEngine` owns rules and returns `EngineResult(state, events)`; `model/` holds immutable snapshots (`GameState`, `Side` where BLACK=1/WHITE=2 matching the AI's `Array<IntArray>` codes); `ai/RobotAI` + `ai/Difficulty` (EASY/MEDIUM/HARD).
- `core/mvi/MviViewModel` — base class: Intents flow through an unlimited `Channel` processed serially (mirrors the old Handler main-thread queue); State is a `StateFlow` rendered idempotently; Effects are a `SharedFlow(replay=0)` for one-shot toast/dialog/navigation. Each feature has a `<Feature>Contract.kt` (Intent/State/Effect), `<Feature>ViewModel.kt`, `<Feature>Activity.kt` under `ui/<feature>/`.
- `data/net/` — `Protocol.kt` (byte-level codec + `TcpFrameReader`), `LanDiscoveryManager` (UDP discovery/handshake/chat), `LanGameClient` (TCP gameplay). Blocking `DatagramSocket.receive()` does not respond to coroutine cancellation — stop by closing the socket, then cancelling the scope.
- `ui/common/GameBoardView` — SurfaceView board; `render(state)` is the single render entry, `onCellTapped` is the single input callback; it holds no game logic.

## Wire-protocol freeze (interop between devices)

The LAN protocol bytes are frozen; two devices on different app versions must stay compatible. Only ADDING new message-type bytes is allowed.

- UDP multicast discovery 230.0.2.2:1688: broadcast frame `[nameLen][name][ipLen][ip][type末字节]` (JOIN=0 / EXIT=1); a JOIN broadcast is answered by a single-cast typed UDP packet, an EXIT is not.
- UDP unicast port 2599: `[type][nameLen][name][ipLen][ip]` with ASK=11 / AGREE=12 / REJECT=13 / UDP_JOIN=0; CHAT=14 appends `[chatLen][chat]`.
- TCP port 8899 gameplay: `[len][type][payload]` where len is the TOTAL frame length; ADD_CHESS=0 (payload `[x][y]`), ROLLBACK_ASK=2 / ROLLBACK_AGREE=3 / ROLLBACK_REJECT=4, RESTART=5.
- Encoder and decoder live in the same `Protocol.kt` and are covered by `ProtocolTest` byte-freeze fixtures — change both sides together, and test on two real devices on the same LAN.

## Behavior notes

- LAN roles: the requested side (server) plays BLACK and moves first; the requester (client) plays WHITE.
- LAN rollback removes, on both peers, the requester's last move and everything after it (derived from the same move history), and restarts are synchronized via the RESTART message — don't regress to per-side single-stone removal or local-only clears.
- Win condition is 5-in-a-row contiguous in 4 directions; board is 15×15 (`GameEngine` default).

## Commit style

Messages are short; recent ones use Conventional-Commits prefixes with Chinese subjects (e.g. `chore(build): 升级Gradle和依赖配置`).
