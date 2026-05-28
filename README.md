# GuideRun - 助盲跑

> 面向视障人士的跑步陪伴社交应用

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2026+-green.svg)](https://developer.android.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

## 项目简介

GuideRun 是一款连接视障用户与跑步志愿者的社交应用。视障用户通过 App 发起跑步请求，附近的志愿者接单后前往约定地点汇合，双方完成一次陪伴跑步。

### 核心亮点

- **无障碍优先设计** - TalkBack / TTS 四级优先级 / 震动反馈 / 35 条语音指令，视障用户可独立完成全流程
- **双角色系统** - 同一用户可同时持有视障用户和志愿者角色
- **实时位置共享** - WebSocket 双向通信 + 高德地图定位
- **完整状态机** - 9 个状态的 RunRequest 生命周期管理
- **设计系统** - 3 套视障端对比度主题 + 4 套志愿者端预设主题 + 字号缩放

## 功能特性

### 视障用户端

- 三按钮大触摸区首页（发起请求 / 历史记录 / 设置），操作热区统一吸底
- 智能默认值预填（时长、集合点 = 当前定位）
- TTS 语音播报（四级优先级：CRITICAL / INTERACTION / HIGH / NORMAL，语速可调）
- 讯飞 MSC 语音指令识别（35 条指令覆盖导航、操作、数值选择、筛选等）
- 震动反馈（tick / warning / error / confirm 四档语义化）
- LongPressGestureView 统一手势：短按朗读 / 长按 2s 确认 / 5s 倒计时撤销
- SOS 紧急求助（音量-键连按 3 次）
- 互拨电话（音量+键连按 3 次，ACCEPTED 后可用）
- 语音批量录入请求参数（地点 / 时长 / 备注一次说完）
- 补充评价（CLOSED 状态仍可补评）

### 志愿者端

- 附近可接单列表（3/5/10 km 搜索范围切换）
- 高德地图导航至集合点（蓝色直线方向指引）
- 实时位置上报（前台服务）
- 跑步统计（距离、时长、配速、滑动平均配速）
- 轨迹回放（配速颜色：快红慢绿，1x/5x/10x/20x 倍速）
- 志愿者徽章系统
- 4 套预设主题 + 自动/手动切换

### 双向功能

- WebSocket 实时状态同步（status_changed / peer_location / emergency / peer_metrics / review_received）
- 协商式结束跑步（志愿者申请 → 视障端长按确认）
- 跑步轨迹记录与上传
- 双向评价系统（评分 + 标签 + 评论）
- 紧急联系人管理（CRUD）
- 系统电话互拨（CALL_PHONE 权限降级方案）
- Android 15 edge-to-edge 适配

## 技术架构

### 客户端 (Android)

```
┌────────────────────────────────────────────────────┐
│                    UI Layer                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │ Compose  │  │ XML View │  │   Navigation     │  │
│  │(志愿者端) │  │(视障端)  │  │   Compose        │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
├────────────────────────────────────────────────────┤
│                 ViewModel Layer                    │
│  ┌──────────────────────────────────────────────┐  │
│  │            StateFlow + UiState               │  │
│  └──────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────┤
│                  Domain Layer                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │  Models  │  │  Repos   │  │    UseCases      │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
├────────────────────────────────────────────────────┤
│                   Data Layer                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │   Room   │  │ Retrofit │  │    DataStore     │  │
│  │  (Local) │  │ (Remote) │  │   (Preferences)  │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
└────────────────────────────────────────────────────┘
```

**技术栈：**
- 语言：Kotlin 2.0
- UI：Jetpack Compose + XML View（视障端关键页面，TalkBack 稳定性）
- 架构：MVVM + Clean Architecture
- 异步：Coroutines + Flow
- DI：Hilt
- 网络：Retrofit + OkHttp + Kotlinx.serialization
- 本地存储：Room + DataStore
- 地图：高德地图 SDK
- TTS：Android 系统 TextToSpeech（四级优先级队列）
- ASR：讯飞 MSC SDK v5+（云端 IAT 听写，`local.properties` 配置 `IFLYTEK_APPID`）
- 语音指令解析：自研 CommandParser（包含匹配 + Levenshtein 模糊，35 条指令枚举）
- 日志：Timber

### 服务端

```
┌────────────────────────────────────────────────────┐
│                 Spring Boot 3.3                    │
│  ┌──────────────────────────────────────────────┐  │
│  │           REST API Controllers               │  │
│  └──────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────┐  │
│  │        WebSocket Handler (实时通信)           │  │
│  └──────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────┐  │
│  │           Service Layer                      │  │
│  │  (RunRequestStateMachine + Business Logic)   │  │
│  └──────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────┐  │
│  │         JPA Repositories + Flyway            │  │
│  └──────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────┤
│                  MySQL 8.0                         │
│  ┌──────────────────────────────────────────────┐  │
│  │  users | run_requests | reviews | tracks ... │  │
│  └──────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────┘
```

**技术栈：**
- 语言：Kotlin 2.0
- 框架：Spring Boot 3.3
- 数据库：MySQL 8.0.16+ + Flyway 迁移
- ORM：Spring Data JPA + Hypersistence Utils（JSON 映射）
- 实时通信：Spring WebSocket

## 项目结构

```
GuideRun/
├── client/                              # Android 客户端
│   └── app/src/main/java/com/guiderun/app/
│       ├── App.kt                       # @HiltAndroidApp
│       ├── accessibility/               # 无障碍组件
│       │   ├── TtsManager.kt            #   四级优先级 TTS
│       │   ├── HapticFeedback.kt        #   语义化震动
│       │   ├── AccessibilityHelper.kt
│       │   ├── WaitingMessageGenerator.kt
│       │   ├── asr/                     #   ASR 引擎抽象
│       │   │   ├── AsrEngine.kt         #     接口
│       │   │   ├── AsrResult.kt         #     密封类结果
│       │   │   └── IflytekAsrEngine.kt  #     讯飞实现
│       │   └── voice/                   #   语音指令系统
│       │       ├── VoiceCommandExt.kt   #     Fragment.bindVoiceCommands{}
│       │       ├── VoiceCommandHost.kt  #     跨 Activity 抽象
│       │       ├── VoiceCommandContextHandler.kt
│       │       └── VolumeKeyDispatcher.kt #   音量键三路语义
│       ├── data/                        # 数据层
│       │   ├── local/                   #   Room (AppDatabase / Entity / DAO / Migration)
│       │   ├── remote/                  #   Retrofit (Api / DTO / WebSocketManager)
│       │   ├── location/                #   定位 (Fused / Legacy / CoordTransform / Geocoder)
│       │   ├── mapper/                  #   DTO <-> Domain 映射
│       │   └── repository/              #   Repository 实现
│       ├── di/                          # Hilt 模块 (Network / Database / Repository / Location / Voice)
│       ├── domain/                      # 领域层
│       │   ├── model/                   #   领域模型 (User / RunRequest / Review / TrackPoint ...)
│       │   ├── repository/              #   Repository 接口
│       │   ├── usecase/                 #   UseCase (Create / Cancel / Poll / RecoverActive)
│       │   └── exception/               #   领域异常
│       ├── ui/                          # UI 层
│       │   ├── auth/                    #   登录注册 (Compose)
│       │   ├── home/                    #   首页 (Compose)
│       │   ├── blind/                   #   视障端 (XML View)
│       │   │   ├── BaseBlindActivity.kt #     语音指令宿主 + 音量键
│       │   │   ├── BlindActivity.kt     #     NavController + 导航翻译
│       │   │   ├── BlindHomeFragment.kt
│       │   │   ├── CreateRequestFragment.kt
│       │   │   ├── WaitingMatchFragment.kt
│       │   │   ├── MatchedFragment.kt
│       │   │   ├── BlindRunningFragment.kt
│       │   │   ├── BlindReviewFragment.kt
│       │   │   ├── BlindHistoryFragment.kt
│       │   │   ├── TrackPlaybackFragment.kt
│       │   │   └── widget/              #     公共组件
│       │   │       ├── LongPressGestureView.kt  # 统一手势控件
│       │   │       ├── BlindPageHeader.kt
│       │   │       ├── BlindActionFooter.kt
│       │   │       ├── BlindMetricCard.kt
│       │   │       └── BlindConfirmDialogFragment.kt
│       │   ├── volunteer/               #   志愿者端 (Compose)
│       │   │   ├── VolunteerOrderListScreen.kt
│       │   │   ├── RequestDetailScreen.kt
│       │   │   ├── NavigatingScreen.kt
│       │   │   ├── MetScreen.kt
│       │   │   ├── VolunteerRunningScreen.kt
│       │   │   ├── VolunteerReviewScreen.kt
│       │   │   ├── VolunteerHistoryScreen.kt
│       │   │   └── TrackPlaybackScreen.kt
│       │   ├── profile/                 #   个人中心 (XML + Compose 混合)
│       │   │   ├── SettingsFragment.kt
│       │   │   ├── ProfileEditFragment.kt
│       │   │   ├── VolunteerProfileEditScreen.kt
│       │   │   ├── EmergencyContactListFragment.kt
│       │   │   ├── EmergencyContactEditFragment.kt
│       │   │   ├── BlindStatsFragment.kt
│       │   │   └── AccessibilitySettingsFragment.kt
│       │   ├── settings/                #   设置 (Compose)
│       │   │   ├── SettingsScreen.kt
│       │   │   └── ThemeSelectionScreen.kt
│       │   ├── theme/                   #   设计系统
│       │   │   ├── BlindDesignTokens.kt #     视障端设计 Token
│       │   │   ├── AppDesignTokens.kt   #     通用设计 Token
│       │   │   ├── BlindThemeResolver.kt #    主题解析
│       │   │   ├── Theme.kt / Color.kt / Type.kt
│       │   ├── shared/map/              #   地图组件 (GuideRunMap)
│       │   ├── common/                  #   通用组件 (CallPeerButton / InterruptDialog)
│       │   ├── navigation/              #   导航 (Screen / AppNavGraph / ActiveOrderRouter)
│       │   └── MainViewModel.kt
│       ├── service/                     # 前台服务
│       │   ├── LocationUpdateService.kt #   志愿者位置上报
│       │   ├── BlindRunTrackingService.kt
│       │   └── VolunteerRunTrackingService.kt
│       └── util/                        # 工具类
│           ├── PaceCalculator.kt        #   配速计算
│           ├── PhoneDialer.kt           #   拨号
│           ├── PermissionHelper.kt      #   权限分层
│           ├── EdgeToEdgeHelper.kt      #   Android 15 适配
│           ├── ConfirmableAction.kt     #   倒计时确认复用
│           └── SimpleKalmanFilter.kt    #   GPS 卡尔曼滤波
├── server/                              # Spring Boot 服务端
│   └── src/main/kotlin/com/guiderun/server/
│       ├── controller/                  # REST API (Auth / User / RunRequest / Admin / FileUpload)
│       ├── entity/                      # JPA 实体
│       ├── repository/                  # 数据访问
│       ├── service/                     # 业务逻辑 + 状态机
│       ├── mapper/                      # Entity <-> DTO 映射
│       ├── dto/                         # 数据传输对象
│       ├── websocket/                   # WebSocket 处理
│       ├── config/                      # 配置 (Security / WebSocket / JPA)
│       ├── filter/                      # JWT 认证过滤器
│       ├── interceptor/                 # 拦截器
│       ├── exception/                   # 全局异常处理
│       ├── scheduler/                   # 定时任务
│       └── common/                      # 通用工具
├── DESIGN.md                            # 完整设计方案
├── PROGRESS.md                          # 开发进度追踪
└── docker-compose.yml                   # Docker 配置
```

## 快速开始

### 环境要求

**客户端：**
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK 34
- Gradle 8.x

**服务端：**
- JDK 21+
- MySQL 8.0.16+
- Docker (可选)

### 1. 克隆项目

```bash
git clone https://github.com/your-username/GuideRun.git
cd GuideRun
```

### 2. 启动服务端

**方式 A：使用 Docker Compose（推荐）**

```bash
docker-compose up -d
```

**方式 B：使用本地 MySQL**

```sql
CREATE DATABASE IF NOT EXISTS guiderun
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'guiderun'@'localhost' IDENTIFIED BY 'guiderun_dev';
GRANT ALL PRIVILEGES ON guiderun.* TO 'guiderun'@'localhost';
FLUSH PRIVILEGES;
```

然后启动后端：

```bash
cd server
./gradlew bootRun
```

服务将在 `http://localhost:8080` 启动。

### 3. 运行客户端

1. 使用 Android Studio 打开 `client/` 目录
2. 同步 Gradle 依赖
3. 在 `local.properties` 中配置 `IFLYTEK_APPID=你的讯飞AppId`（语音指令功能需要）
4. 连接 Android 设备或启动模拟器
5. 运行应用

**测试账号：**
- 手机号：任意 11 位数字（如 `13800000001`）
- 验证码：`123456`（Mock 模式）

**预置数据：**
- 视障用户：`13800000001`（张先生）、`13800000002`（李阿姨）
- 志愿者：`13900000010`（跑者小王）、`13900000011`

## 核心业务流程

### RunRequest 状态机

```
CREATED → MATCHING → ACCEPTED → EN_ROUTE → MET → RUNNING → FINISHED → CLOSED
   │         │          │          │        │        │          │
   └─────────┴──────────┴──────────┴────────┴────────┴──────────┘
                              ↓
                          ABORTED (异常终止)
```

**状态说明：**
- `CREATED` - 视障用户创建请求
- `MATCHING` - 等待志愿者接单
- `ACCEPTED` - 志愿者已接单
- `EN_ROUTE` - 志愿者前往集合点
- `MET` - 双方已汇合
- `RUNNING` - 跑步进行中
- `FINISHED` - 跑步结束（协商式：志愿者申请，视障端确认）
- `CLOSED` - 双方评价完成（含补充评价）
- `ABORTED` - 异常终止

### 完整流程示例

```
视障用户                    服务端                    志愿者
    │                         │                         │
    │  1. 发起跑步请求         │                         │
    │ ──────────────────────> │                         │
    │                         │  2. 推送新订单通知       │
    │                         │ ──────────────────────> │
    │                         │                         │
    │                         │  3. 志愿者接单           │
    │                         │ <────────────────────── │
    │  4. 通知已匹配           │                         │
    │ <────────────────────── │                         │
    │                         │                         │
    │                         │  5. 志愿者出发           │
    │                         │ <────────────────────── │
    │  6. 实时位置更新         │                         │
    │ <────────────────────── │ ──────────────────────> │
    │                         │                         │
    │  7. 确认汇合             │  8. 确认汇合            │
    │ ──────────────────────> │ <────────────────────── │
    │                         │                         │
    │  9. 开始跑步             │                         │
    │ <────────────────────── │ ──────────────────────> │
    │                         │                         │
    │                         │  10. 申请结束跑步        │
    │  11. 视障端确认结束       │ <────────────────────── │
    │ ──────────────────────> │                         │
    │                         │                         │
    │  12. 评价               │  13. 评价                │
    │ ──────────────────────> │ <────────────────────── │
    │                         │                         │
```

## 无障碍设计

### 设计原则

1. **路径极简** - 打开发起请求 ≤ 2 步
2. **语音优先** - TTS 播报 + 语音指令
3. **多通道反馈** - 语音 + 震动 + 视觉
4. **大触摸区** - 关键按钮 ≥ 96dp
5. **默认值智能** - 用户只需"确认"而非"填写"
6. **可逆优于正确** - 关键操作提供撤销时间窗

### TTS 管理器

四级优先级队列，支持打断与接力：

| 优先级 | 场景 | 行为 |
|--------|------|------|
| CRITICAL | SOS / 紧急中断 | 立即打断所有，不可被抢占 |
| INTERACTION | 倒计时数字 / 阈值反馈 | 不打断同级，接力播报 |
| HIGH | 状态变化 / 入页引导 | 打断 NORMAL，锁内排队 |
| NORMAL | 鼓励语 / 距离播报 | 排队等待 |

- 语速可调（默认 1.0x，支持 0.5x~2.0x）
- 生命周期感知，页面切换时不中断重要播报
- `speakAndWait()` 支持协程挂起等待播报完成

### 语音指令系统

完整架构分六层：

```
┌─────────────────────────────────────────────────────────┐
│  触发层：VolumeKeyDispatcher                              │
│  音量+长按 1.5s → 语音 | 音量-连按3次 → SOS               │
│  音量+连按3次 → 拨号 | 短按 → 调音量                       │
├─────────────────────────────────────────────────────────┤
│  ASR 层：AsrEngine 接口 + IflytekAsrEngine               │
│  讯飞 MSC SDK，云端 IAT 听写，VAD_BOS=4000 / VAD_EOS=1200 │
├─────────────────────────────────────────────────────────┤
│  解析层：CommandParser                                    │
│  归一化 → 精确包含匹配（最长 phrase）→ Levenshtein 模糊兜底 │
├─────────────────────────────────────────────────────────┤
│  指令枚举：VoiceCommand（35 个）                           │
│  全局导航 / 全局动作 / 上下文通用 / 数值选择 / 筛选 / 设置  │
├─────────────────────────────────────────────────────────┤
│  协调层：VoiceCommandManager（Singleton）                 │
│  Idle → Listening → Parsing 状态机，TTS"请说指令"→ 派发    │
├─────────────────────────────────────────────────────────┤
│  执行层：                                                 │
│  全局指令 → CommandExecutor（导航/SOS/拨号/状态朗读）       │
│  上下文指令 → Fragment.bindVoiceCommands{} 注册处理器      │
└─────────────────────────────────────────────────────────┘
```

**35 条语音指令覆盖：**
- 全局导航：发起请求 / 查看历史 / 个人资料 / 回首页
- 全局动作：拨打电话 / SOS / 状态查询 / 帮助
- 上下文通用：确认 / 取消 / 保存 / 跳过 / 重试 / 暂停 / 结束
- 数值选择：30/60/90/120 分钟、1~5 星评分
- 历史筛选：已完成 / 已取消 / 全部
- 设置子页：编辑资料 / 紧急联系人 / 统计 / 无障碍设置

### LongPressGestureView

视障端统一手势控件，所有长按按钮使用同一组件：

- **短按**：tick 震动 + HIGH TTS 播报 contentDescription
- **长按 2s**：confirm 震动 + 执行主操作
- **长按 5s**：warning 震动 + 执行撤销/次级操作
- 阈值到达瞬间震动 + TTS"松开 XX"，避免松手才反馈

### 震动反馈

```kotlin
haptic.tick()       // 轻触反馈（短按）
haptic.success()    // 确认成功（短-短）
haptic.warning()    // 警告/撤销（长振）
haptic.error()      // 操作失败
haptic.confirm()    // 长按确认
```

### 设计系统

**视障端（3 套对比度主题）：**
- 标准模式：深色背景 + 高对比度文字
- 高对比度：纯黑背景 + 纯白文字
- 亮色模式：白底黑字

**志愿者端（4 套预设主题）：**
- 默认蓝 / 暗夜黑 / 森林绿 / 暖阳橙

两套主题系统独立互不干扰，通过 `BlindDesignTokens` / `AppDesignTokens` 统一管理间距、字号、圆角、颜色语义。

## API 文档

### 认证接口

```http
POST /api/v1/auth/send-sms     # 发送验证码
POST /api/v1/auth/login         # 登录
POST /api/v1/auth/refresh       # 刷新 Token
POST /api/v1/auth/logout        # 登出
```

### 用户接口

```http
GET    /api/v1/users/me                # 当前用户信息
PATCH  /api/v1/users/me                # 更新基础信息
POST   /api/v1/users/me/roles          # 申请新角色
GET    /api/v1/users/me/blind-stats    # 视障端跑步统计
GET    /api/v1/users/me/volunteer-stats # 志愿者端跑步统计
```

### 紧急联系人接口

```http
GET    /api/v1/users/me/emergency-contacts      # 列表
POST   /api/v1/users/me/emergency-contacts      # 新增
PATCH  /api/v1/users/me/emergency-contacts/{id} # 更新
DELETE /api/v1/users/me/emergency-contacts/{id} # 删除
```

### 跑步请求接口

```http
# 视障用户
POST   /api/v1/run-requests                    # 发起请求
GET    /api/v1/run-requests/{id}               # 查询请求
POST   /api/v1/run-requests/{id}/cancel        # 取消请求

# 志愿者
GET    /api/v1/run-requests/available          # 附近可接单
POST   /api/v1/run-requests/{id}/accept        # 接单
POST   /api/v1/run-requests/{id}/depart        # 确认出发
POST   /api/v1/run-requests/{id}/confirm-met   # 确认汇合
POST   /api/v1/run-requests/{id}/start-run     # 开始跑步
POST   /api/v1/run-requests/{id}/request-end-run # 申请结束跑步

# 双向
POST   /api/v1/run-requests/{id}/tracks        # 上传轨迹
GET    /api/v1/run-requests/{id}/tracks        # 查询轨迹
POST   /api/v1/run-requests/{id}/reviews       # 评价
GET    /api/v1/run-requests/{id}/reviews       # 查询评价
POST   /api/v1/run-requests/{id}/emergency     # 紧急求助
POST   /api/v1/run-requests/{id}/peer-metrics  # 推送实时指标
```

### WebSocket

```javascript
// 连接
ws://localhost:8080/ws/v1?token=<access_token>

// 客户端 → 服务端
{ "type": "location", "lat": 39.9, "lng": 116.4 }

// 服务端 → 客户端
{ "type": "status_changed", "requestId": "...", "from": "...", "to": "...", "triggeredRole": "BLIND" }
{ "type": "peer_location", "requestId": "...", "lat": 39.9, "lng": 116.4 }
{ "type": "peer_metrics", "requestId": "...", "distance": 1200, "duration": 600, "pace": 300 }
{ "type": "review_received", "requestId": "...", "rating": 5 }
{ "type": "emergency", "requestId": "..." }
```

## 构建与测试

### 客户端

```bash
cd client

# 构建 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 运行仪器测试（需连接设备）
./gradlew connectedAndroidTest

# 代码检查
./gradlew lint
```

### 服务端

```bash
cd server

# 构建
./gradlew build

# 运行测试
./gradlew test

# 启动应用
./gradlew bootRun
```

## 技术亮点

### 1. 无障碍优先设计

- 视障端 13 个 XML 页面统一设计 Token + LongPressGestureView 统一手势
- TTS 四级优先级队列 + `speakAndWait()` 协程挂起
- 讯飞 MSC 语音指令系统（35 条指令，Levenshtein 模糊匹配）
- 音量键三路语义共存（短按调音量 / 连按3次拨号或SOS / 长按1.5s语音指令）
- 语义化震动反馈（tick / success / warning / error / confirm）
- 操作热区吸底 + 大字号缩放 + 3 套对比度主题

### 2. 状态机驱动的业务逻辑

- 9 个状态的完整生命周期管理
- 乐观锁防止并发抢单
- 幂等键防止重复请求
- 协商式结束跑步（志愿者申请 → 视障端确认）

### 3. 实时位置共享

- WebSocket 双向通信 + 指数退避重连
- 前台服务持续位置上报
- GCJ-02 坐标系自动转换（WGS-84 → GCJ-02）
- GPS 卡尔曼滤波平滑

### 4. 现代 Android 开发实践

- MVVM + Clean Architecture 分层
- Coroutines + Flow 响应式编程
- Hilt 依赖注入
- Jetpack Compose + XML View 混合使用
- Android 15 edge-to-edge 适配
- 权限分层批量申请

---

**GuideRun** - 让每一次跑步都有陪伴
