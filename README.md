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
- BlindLongPressGestureView 统一手势：短按朗读 / 长按 2s 确认 / 5s 倒计时撤销
- SOS 紧急求助（音量-键连按 3 次）
- 互拨电话（音量+键连按 3 次，ACCEPTED 后可用）
- 语音批量录入请求参数（地点 / 时长 / 备注一次说完）
- 已匹配志愿者后定时播报距离信息（15 秒间隔，与等待匹配页同节奏）
- 补充评价（CLOSED 状态仍可补评）

### 志愿者端

- 附近可接单列表（3/5/10 km 搜索范围切换）
- 接单详情页：上方地图 + 下方订单卡片，接单前直观显示双方位置（替代旧版纯文本距离）
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
│       │   ├── local/                   #   Room + DataStore
│       │   │   ├── AppDatabase.kt / Migrations.kt / Entity / DAO
│       │   │   ├── UserPreferences.kt   #     DataStore，token 经 TokenCipher 加解密
│       │   │   ├── TokenCipher.kt       #     KeyStore AES-256/GCM token 加密
│       │   │   └── RequestPreferences.kt
│       │   ├── remote/                  #   Retrofit (Api / DTO / WebSocketManager / AuthInterceptor)
│       │   ├── location/                #   定位 (Fused / Legacy / CoordTransform / Geocoder)
│       │   ├── mapper/                  #   DTO <-> Domain 映射
│       │   └── repository/              #   Repository 实现
│       ├── di/                          # Hilt 模块 (Network / Database / Repository / Location / Voice)
│       ├── domain/                      # 领域层
│       │   ├── model/                   #   领域模型 (User / RunRequest / Review / TrackPoint ...)
│       │   ├── repository/              #   Repository 接口
│       │   ├── usecase/                 #   UseCase (Create / Cancel / Poll / RecoverActive)
│       │   └── exception/               #   领域异常
│       ├── ui/                          # UI 层（按双端清晰分组）
│       │   ├── auth/                    #   登录 / 角色选择（公共 Compose）
│       │   │   ├── LoginScreen.kt
│       │   │   └── RoleSelectScreen.kt
│       │   ├── blind/                   #   视障端 XML Fragments（全 Blind 前缀）
│       │   │   ├── BaseBlindActivity.kt #     语音指令宿主 + 音量键 + 字号注入
│       │   │   ├── BlindActivity.kt     #     NavController + 起始目的地分派
│       │   │   ├── BlindHomeFragment.kt
│       │   │   ├── BlindCreateRequestFragment.kt
│       │   │   ├── BlindWaitingMatchFragment.kt
│       │   │   ├── BlindMatchedFragment.kt
│       │   │   ├── BlindRunningFragment.kt
│       │   │   ├── BlindReviewFragment.kt
│       │   │   ├── BlindHistoryFragment.kt
│       │   │   ├── BlindTrackPlaybackFragment.kt
│       │   │   ├── BlindProfileEditFragment.kt
│       │   │   ├── BlindEmergencyContactListFragment.kt
│       │   │   ├── BlindEmergencyContactEditFragment.kt
│       │   │   ├── BlindStatsFragment.kt
│       │   │   ├── BlindAccessibilitySettingsFragment.kt
│       │   │   ├── BlindSettingsFragment.kt
│       │   │   └── widget/              #     视障专属组件
│       │   │       ├── BlindLongPressGestureView.kt  # 统一长按手势（2s 确认 + 5s 撤销）
│       │   │       ├── BlindPageHeader.kt
│       │   │       ├── BlindActionFooter.kt
│       │   │       ├── BlindMetricCard.kt
│       │   │       └── BlindConfirmDialogFragment.kt
│       │   ├── volunteer/               #   志愿者端 Compose Screens（全 Volunteer 前缀）
│       │   │   ├── VolunteerHomeScreen.kt
│       │   │   ├── VolunteerOrderListScreen.kt
│       │   │   ├── VolunteerRequestDetailScreen.kt
│       │   │   ├── VolunteerNavigatingScreen.kt
│       │   │   ├── VolunteerMetScreen.kt
│       │   │   ├── VolunteerRunningScreen.kt
│       │   │   ├── VolunteerReviewScreen.kt
│       │   │   ├── VolunteerHistoryScreen.kt
│       │   │   ├── VolunteerTrackPlaybackScreen.kt
│       │   │   ├── VolunteerProfileEditScreen.kt
│       │   │   ├── VolunteerSettingsScreen.kt
│       │   │   ├── VolunteerThemeSelectionScreen.kt
│       │   │   ├── CallPeerButton.kt    #     志愿者专用拨号按钮
│       │   │   └── InterruptDialog.kt   #     志愿者中断弹窗（非破坏性）
│       │   ├── navigation/              #   路由层
│       │   │   ├── Screen.kt            #     Compose 路由 sealed 类
│       │   │   ├── AppNavGraph.kt       #     志愿者 + Auth 流程图
│       │   │   ├── ActiveOrderRouter.kt #     双端订单恢复路由映射
│       │   │   └── MainViewModel.kt     #     StartTarget 三态启动分派
│       │   ├── shared/                  #   双端共享
│       │   │   ├── HomeViewModel.kt     #     会话/角色/活跃订单（blind + volunteer 共用）
│       │   │   └── map/GuideRunMap.kt   #     高德地图 Composable
│       │   └── theme/                   #   设计系统
│       │       ├── BlindDesignTokens.kt
│       │       ├── AppDesignTokens.kt
│       │       ├── BlindThemeResolver.kt
│       │       └── Theme.kt / Color.kt / Type.kt
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
│           ├── CoroutineExt.kt          #   runCatchingCancellable（结构化并发安全）
│           ├── SpeedSmoother.kt         #   5s 滑动平均配速
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
└── docker-compose.yml                   # 一键拉起 MySQL + Redis 开发环境

（DESIGN.md / PROGRESS.md / PRD 等设计文档为内部使用，不在公开仓库中）
```

## 快速开始

### 环境要求

**客户端：**
- Android Studio 最新版（Koala/Ladybug 及以上）
- JDK 11+（项目 `sourceCompatibility = VERSION_11`）
- Android SDK：`compileSdk = 36` / `minSdk = 26` / `targetSdk = 36`
- Gradle 8.x（含 wrapper，无需本地安装）

**服务端：**
- JDK 21+（`languageVersion = 21`）
- MySQL 8.0.16+
- Docker（推荐，一键拉起 MySQL + Redis）

### 1. 克隆项目

```bash
git clone https://github.com/your-username/GuideRun.git
cd GuideRun
```

### 2. 启动服务端

**方式 A：Docker Compose（推荐，一键拉起 MySQL + Redis）**

```bash
docker compose up -d   # Docker v2 命令（无连字符）
```

容器配置与 `application.yml` 的 `${DB_PASSWORD:guiderun_dev}` 默认值对齐，可零配置启动。

**方式 B：本地 MySQL**

```sql
CREATE DATABASE IF NOT EXISTS guiderun
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'guiderun'@'localhost' IDENTIFIED BY 'guiderun_dev';
GRANT ALL PRIVILEGES ON guiderun.* TO 'guiderun'@'localhost';
FLUSH PRIVILEGES;
```

启动后端：

```bash
cd server
./gradlew bootRun   # 自动激活 application-dev.yml profile（Flyway seed + SQL 日志）
```

服务在 `http://localhost:8080` 监听。生产环境通过环境变量覆盖：
`DB_USERNAME` / `DB_PASSWORD` / `JWT_SECRET`。

### 3. 运行客户端

1. 使用 Android Studio 打开 `client/` 目录
2. 在 `client/local.properties` 配置 **3 项必需值**：

   ```properties
   BASE_URL=http://10.0.2.2:8080/        # 模拟器访问宿主机后端；真机改成局域网 IP
   AMAP_KEY=你的高德地图 Key               # 高德开放平台申请，与签名 SHA1 绑定
   IFLYTEK_APPID=你的讯飞 AppId           # 讯飞开放平台申请，语音指令必需
   ```
3. （可选）配置发布签名：参考 `client/keystore.properties.example`，缺省时 release 构建自动 fallback 到 debug 签名
4. 同步 Gradle 依赖
5. 连接设备 / 启动模拟器
6. `./gradlew installDebug` 或在 IDE 中运行

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
{ "type": "end_run_requested", "requestId": "..." }   // 志愿者申请结束跑步 → 视障端弹"长按确认"
```

## 安全与发布

### Token 静态加密

`UserPreferences` 写入 access/refresh token 前先经 `TokenCipher` 加密：
- Android **KeyStore** 中生成 AES‑256 密钥，**永不出 KeyStore**
- AES/GCM/NoPadding，每次随机 12 字节 IV，密文 `Base64(iv ‖ ct)` 写 DataStore
- 解密失败（密钥被系统清除 / 升级前明文残留）→ 返回 null → 触发重新登录
- 零新依赖（`EncryptedSharedPreferences` 已被 Google 标记 deprecated，本项目用平台 API 实现等效能力）

### 网络层

- `network_security_config.xml` 设 `cleartextTrafficPermitted=false`（release 拒绝明文 HTTP）
- `HttpLoggingInterceptor` 与 `Timber.DebugTree` 仅 `BuildConfig.DEBUG` 启用
- 401 → `AuthInterceptor` 自动清 token + 广播 `AuthEvent` → 跳转登录

### R8 混淆 + 资源压缩

- `release { isMinifyEnabled = true; isShrinkResources = true }`
- `proguard-rules.pro` 完整 keep：kotlinx.serialization / Retrofit / Room / 协程 / 高德 / 讯飞 / Hilt / 异常类
- Release APK ≈ 55 MB（含讯飞 .so + 高德 SDK），全 pipeline 通过 `minifyReleaseWithR8`

### 发布签名

- 签名信息读 `client/keystore.properties`（与 `keystore/release.jks` 一同 `.gitignore`）
- 缺省时 release 自动 fallback 到 debug 签名（本地构建不阻断）
- 模板见 `client/keystore.properties.example`，含 `keytool -genkeypair` 一键生成命令

### 静态分析（Detekt）

- 配置文件：`client/app/detekt-config.yml`
- 保留**真有用**规则：UnusedPrivateMember / UnusedParameter / EmptyFunctionBlock / UnreachableCode
- 关闭**风格审美**类规则（MagicNumber / FunctionNaming / LongMethod / WildcardImport 等，针对 GPS 物理常数 + Compose @Composable PascalCase + 业务复杂逻辑特性豁免）
- 运行：`./gradlew detekt`

## 构建与测试

### 客户端

```bash
cd client

# Debug APK（开发）
./gradlew assembleDebug

# Release APK（含 R8 混淆 + 资源压缩；缺签名 fallback debug）
./gradlew assembleRelease

# 单元测试
./gradlew test

# 仪器测试（需连接设备）
./gradlew connectedAndroidTest

# Android Lint
./gradlew lint

# Detekt 静态分析
./gradlew detekt
```

### 服务端

```bash
cd server

# 构建（生成 fat jar）
./gradlew build

# 运行测试
./gradlew test

# 启动应用（自动激活 dev profile）
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

### 5. 双端无缝启动分派

`MainActivity` 仅作"路由分派"：

```
LAUNCHER → MainActivity
            └─ MainViewModel.resolveTarget(token, role)
                ├─ null token            → Login (Compose NavHost)
                ├─ token + BLIND_RUNNER  → BlindActivity.start() + finish() → BlindHomeFragment
                └─ token + 其他          → VolunteerHomeScreen (Compose NavHost)
```

- 视障路径**无 Compose 跳板**：MainActivity 直接 `BlindActivity.start() + finish()`，避免双 Activity 之间的视觉断裂
- 登录 / 角色选择完成 → `reresolveAfterLogin()` 重读 token+role → 按新角色直接分派
- 视障端登出 → `BlindSettingsFragment` 显式 `startActivity(MainActivity, CLEAR_TASK)` 重启回 Login
- WS 重连成功 → `HomeViewModel` 自动重拉 `getMe()`（冷启动断网时姓名/角色空白会自动恢复，无需切前后台）

---

**GuideRun** - 让每一次跑步都有陪伴
