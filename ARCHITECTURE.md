# GuideRun 架构说明

## 架构模式：MVVM + Clean Architecture

项目采用 **MVVM**（Model-View-ViewModel）结合 **Clean Architecture** 三层分离的方案。选择这一组合的原因：

- **MVVM**：与 Jetpack ViewModel / StateFlow / Compose 天然契合，UI 通过 `collectAsStateWithLifecycle()` 被动驱动，避免 Activity/Fragment 持有业务状态
- **Clean Architecture**：Domain 层不依赖 Android 框架，纯 Kotlin 对象可独立单测；Repository 接口在 domain 定义、实现在 data，依赖倒置使数据源可替换

```
┌────────────────────────────────────────┐
│             UI Layer                   │
│  Compose Screen / XML Fragment         │  ← 只关心展示，不写业务逻辑
│  ViewModel（StateFlow + UiState）      │  ← 管理 UI 状态，不直接访问 DB
├────────────────────────────────────────┤
│           Domain Layer                 │
│  Model（纯 Kotlin 数据类）              │  ← 无 Android 依赖
│  Repository 接口                       │
│  UseCase（业务规则）                    │
├────────────────────────────────────────┤
│            Data Layer                  │
│  Repository 实现                       │  ← Room / Retrofit / DataStore
│  DTO ↔ Domain Mapper                   │
│  Local（Room + DataStore）             │
│  Remote（Retrofit + WebSocket）        │
└────────────────────────────────────────┘
```

**单向数据流**：UI 触发 Event → ViewModel 调用 UseCase → Repository → 数据源，结果以 `StateFlow<UiState>` 回流 UI，UI 永远是 State 的函数。

---

## 客户端分层详解

### UI 层

#### 双端分离策略

| 端 | 技术 | 原因 |
|----|------|------|
| 视障端 | XML + Fragment（Navigation Component） | TalkBack 对 XML 无障碍支持更稳定，`contentDescription` / `importantForAccessibility` 在 XML 体系下经过充分验证 |
| 志愿者端 | Jetpack Compose + Navigation Compose | 现代 Android 推荐方案，声明式 UI + Material3 主题系统，主题切换成本低 |
| 公共（登录/角色选择） | Jetpack Compose | 不涉及复杂无障碍，共用 Compose NavHost |

#### 关键 UI 类职责

| 类 | 职责 |
|----|------|
| `MainActivity` | 路由分派：读 token+role → 跳 Login / BlindActivity / VolunteerHomeScreen，自身无 UI |
| `MainViewModel` | 解析启动目标（`StartTarget` 三态），`resolveTarget()` 冷启动分派 |
| `BlindActivity` | 视障端 NavController 宿主 + 音量键分派（`VolumeKeyDispatcher`）+ 字号注入（`attachBaseContext`） |
| `BaseBlindActivity` | 语音指令宿主抽象（`VoiceCommandHost`），提供 `bindVoiceCommands{}` DSL 给子 Fragment |
| `HomeViewModel` | 双端共享：会话信息、当前角色、活跃订单状态，WebSocket 事件入口 |
| `AppNavGraph.kt` | 志愿者端 + Auth 完整路由图（Compose NavHost） |
| `Screen.kt` | Compose 路由 sealed 类，类型安全路由定义 |

#### 视障端 Fragment 命名约定

所有视障端页面：`Blind{功能}Fragment`，对应 ViewModel：`Blind{功能}ViewModel`，对应布局：`fragment_blind_{功能}.xml`。

#### 视障端专属组件

| 组件 | 职责 |
|----|------|
| `BlindLongPressGestureView` | 统一手势语义：短按→朗读 / 长按 2s→确认 / 长按 5s→撤销，阈值到达瞬间给 TTS+震动反馈 |
| `BlindPageHeader` | 标题栏，带顶部/底部分割线，短按触发 TTS |
| `BlindActionFooter` | 操作热区吸底容器，统一底边安全距离 |
| `BlindConfirmDialogFragment` | 破坏性操作全屏确认弹窗（长按 2s 才执行，防误触） |
| `BlindMetricCard` | 跑步三指标（距离/时长/配速）竖排展示卡片 |

---

### ViewModel 层

每个屏幕对应一个 ViewModel，持有且只持有：

- `val uiState: StateFlow<XxxUiState>` — UI 当前状态
- `val events: SharedFlow<XxxEvent>` — 一次性事件（导航、Toast）
- UseCase 调用入口（通过 Hilt 注入）

**UiState 结构约定**（以 `BlindWaitingMatchUiState` 为例）：

```kotlin
data class BlindWaitingMatchUiState(
    val isLoading: Boolean = false,
    val request: RunRequest? = null,
    val volunteerLocation: LatLng? = null,
    val distanceText: String = "",
    val error: String? = null,
)
```

状态只能由 ViewModel 内部通过 `_uiState.update {}` 修改，UI 层只读。

---

### Domain 层

#### 领域模型（`domain/model/`）

纯 Kotlin data class，不依赖任何框架：

| 模型 | 说明 |
|------|------|
| `User` | 用户基本信息 + 角色集合 |
| `RunRequest` | 跑步请求完整状态（9 状态枚举 `RunRequestStatus`） |
| `TrackPoint` | 轨迹点（lat / lng / timestamp / speed） |
| `Review` | 双向评价（rating / tags / comment） |
| `EmergencyContact` | 紧急联系人 |

#### Repository 接口（`domain/repository/`）

接口定义在 domain，与数据源解耦：

| 接口 | 主要方法 |
|------|---------|
| `AuthRepository` | `sendSms()` / `login()` / `logout()` |
| `UserRepository` | `getMe()` / `updateMe()` / `getEmergencyContacts()` + 紧急联系人 CRUD |
| `RunRequestRepository` | `create()` / `cancel()` / `accept()` / `getById()` / `getHistory()` / `getAvailable()` / 轨迹与评价同库 |
| `LocationProvider` | `locationUpdates()`（Flow）+ `getLastLocation()` |

#### UseCase（`domain/usecase/`）

每个 UseCase 对应一个业务动作，构造注入 Repository：

| UseCase | 职责 |
|---------|------|
| `CreateRunRequestUseCase` | 验证参数 + 调用 Repository 创建请求 |
| `CancelRunRequestUseCase` | 取消前校验状态合法性 |
| `PollRequestStatusUseCase` | 轮询请求状态直到目标状态或超时 |
| `RecoverActiveRequestUseCase` | 冷启动时恢复进行中订单，触发路由跳转 |

---

### Data 层

#### Repository 实现（`data/repository/`）

实现 domain 的 Repository 接口，组合 Remote + Local：

```
Repository 实现
    ├── 优先读 Remote（Retrofit suspend 函数）
    ├── 写入 Room 缓存
    └── 降级读 Room（网络不可用时）
```

#### 网络层（`data/remote/`）

| 类 | 职责 |
|----|------|
| `AuthApi` / `UserApi` / `RunRequestApi` | 按业务领域拆分的 Retrofit 接口，全 suspend 函数 |
| `AuthInterceptor` | 自动附加 Bearer Token，401 时广播 `AuthEvent` 触发重登录 |
| `WebSocketManager` | OkHttp WebSocket 封装，指数退避重连，`Flow<WsMessage>` 向上暴露 |
| `TokenCipher` | Android KeyStore AES-256/GCM Token 静态加密 |

#### 本地层（`data/local/`）

| 类 | 职责 |
|----|------|
| `AppDatabase` | Room 数据库入口，版本迁移 `Migrations.kt` |
| `UserPreferences` | DataStore 封装，Token/角色/字号偏好 |
| `RequestPreferences` | DataStore 缓存活跃订单 ID，冷启动恢复用 |

#### 定位层（`data/location/`）

| 类 | 职责 |
|----|------|
| `LocationProvider`（domain 接口） | 抽象定位入口，按 GMS 可用性绑定具体实现 |
| `FusedLocationProviderImpl` | Google Fused Location 实现，含双源去重 |
| `LegacyLocationProviderImpl` | 系统 LocationManager 兜底实现（无 GMS 设备） |
| `CoordTransform` | WGS-84 → GCJ-02 坐标转换（高德地图渲染前必须转换） |
| `ForwardGeocoder` | 正向地理编码（地址 → 经纬度），用于 CreateRequest 集合点 |
| `ReverseGeocoder` | 反向地理编码（经纬度 → 地址描述），用于位置朗读 |

#### DTO ↔ Domain 映射（`data/mapper/`）

网络层的 DTO（kotlinx.serialization `@Serializable`）与 Domain Model 严格分离，通过 mapper 转换，避免网络字段污染领域模型。

---

## 服务端架构（Spring Boot 3.3）

```
Controller → Service → Repository (JPA)
                ↓
          StateMachine（RunRequestStateMachine）
                ↓
         WebSocket 推送（status_changed / peer_location 等消息）
```

| 层 | 说明 |
|----|------|
| `Controller` | REST 接口，参数校验（`@Valid`），返回统一 `ApiResponse<T>` |
| `Service` | 业务逻辑，`@Transactional` 边界，调用状态机 |
| `RunRequestStateMachine` | 9 状态转移规则，乐观锁（`@Version`）防并发抢单 |
| `WebSocketHandler` | 实时双向通信：位置 / 状态变更 / 紧急求助 / 配速指标 |
| `Repository` | Spring Data JPA，Flyway 管理 Schema 迁移 |

---

## 无障碍系统架构

无障碍是本项目的核心技术亮点，独立成包 `accessibility/`：

```
VolumeKeyDispatcher（音量键语义分发）
    ├── 短按 → 系统调音量
    ├── 连按 3 次（+键）→ PhoneDialer.dial()
    ├── 连按 3 次（-键）→ SosCoordinator.trigger()
    └── 长按 1.5s（+键）→ VoiceCommandManager.startListening()

VoiceCommandManager（Singleton，语音指令协调）
    ├── AsrEngine（接口）← IflytekAsrEngine（讯飞 MSC 实现）
    ├── CommandParser（归一化 + 精确包含 + Levenshtein 模糊）
    └── 派发
        ├── 全局指令 → CommandExecutor（导航/SOS/拨号）
        └── 上下文指令 → Fragment.bindVoiceCommands{} 注册的 Handler

TtsManager（四级优先级 TTS 队列）
    ├── CRITICAL  - SOS / 紧急中断，立即抢占
    ├── INTERACTION - 倒计时 / 阈值反馈，接力不打断同级
    ├── HIGH      - 状态变化 / 入页引导，打断 NORMAL
    └── NORMAL    - 距离播报 / 鼓励语，排队等待

HapticFeedback（语义化震动）
    ├── tick()    - 短触（短按确认）
    ├── confirm() - 重振（长按阈值到达 / 操作确认）
    ├── warning() - 长振（撤销 / 警告）
    └── error()   - 三短振（操作失败）
```

---

## 关键设计决策

### 1. 视障端坚持 XML View

TalkBack 在 Compose 上的 `semantics` 支持在 2024 年前仍存在若干边缘 bug（尤其是焦点遍历顺序、`LiveRegion`），对视障用户体验影响大。视障端 13 个核心页面全部使用 XML，牺牲开发体验换取无障碍可靠性。

### 2. Token 静态加密不用 EncryptedSharedPreferences

`EncryptedSharedPreferences` 已被 Google 标记为 deprecated，内部实现存在密钥迁移问题。本项目直接使用 Android KeyStore + AES-256/GCM，零额外依赖，密钥永不出 KeyStore。

### 3. 协商式结束跑步

志愿者不能单方面结束跑步，必须走 `REQUEST_END_RUN → 视障端长按确认 → FINISHED`。防止志愿者因配速过慢单方面终止，保障视障用户权益。

### 4. 坐标系强制 GCJ-02 转换

GPS 返回 WGS-84 坐标，高德地图使用 GCJ-02（中国国家标准），未转换会导致标记/折线整体偏移约 500m。所有写入高德地图 API 的坐标，必须经 `CoordTransform.wgs84ToGcj02()` 转换。

### 5. 乐观锁防并发抢单

`run_requests` 表有 `@Version` 字段，多志愿者同时接单时，第一个到达数据库的成功，其余抛 `OptimisticLockingFailureException` → 服务端返回 409 → 客户端提示"已被他人接单"。

---

## 数据流示例：视障用户发起跑步请求

```
BlindCreateRequestFragment
    │  用户长按 2s "发起请求" 按钮
    ▼
BlindCreateRequestViewModel.createRequest()
    │  调用 UseCase
    ▼
CreateRunRequestUseCase.execute(params)
    │  校验参数（时长 / 集合点非空）
    ▼
RunRequestRepository.create(params)
    │  Retrofit POST /api/v1/run-requests
    ▼
服务端 RunRequestController
    │  RunRequestService.create()
    │  状态 CREATED → MATCHING
    │  WebSocket 广播给附近志愿者
    ▼
(返回 RunRequest)
    ▼
Repository → Mapper.toDomain() → RunRequest
    ▼
ViewModel._uiState.update { copy(request = it, isLoading = false) }
    ▼
Fragment collectAsStateWithLifecycle() 触发重组/视图更新
    │
TtsManager.speak("请求已发起，等待志愿者接单", HIGH)
HapticFeedback.success()
    │
导航至 BlindWaitingMatchFragment
```
