# GuideRun - 助盲跑

> 面向视障人士的跑步陪伴社交应用

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2026+-green.svg)](https://developer.android.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

## 项目简介

GuideRun 是一款连接视障用户与跑步志愿者的社交应用。视障用户通过 App 发起跑步请求，附近的志愿者接单后前往约定地点汇合，双方完成一次陪伴跑步。

### 核心亮点

- **无障碍优先设计** - TalkBack / TTS / 震动反馈 / 语音指令，视障用户可独立完成全流程
- **双角色系统** - 同一用户可同时持有视障用户和志愿者角色
- **实时位置共享** - WebSocket 双向通信 + 高德地图定位
- **完整状态机** - 9 个状态的 RunRequest 生命周期管理

## 功能特性

### 视障用户端

- 三按钮大触摸区首页（发起请求 / 历史记录 / 设置）
- 智能默认值预填（时长、集合点 = 当前定位）
- TTS 语音播报（优先级队列、语速可调）
- 语音指令识别（发起跑步、取消、救命等热词）
- 震动反馈（成功 / 错误 / 通知 / 紧急 四档语义化）
- 等待匹配时周期性鼓励播报
- SOS 紧急求助（摇一摇 / 音量键+电源键）

### 志愿者端

- 附近可接单列表（距离、视障用户信息、期望时长）
- 高德地图导航至集合点
- 实时位置上报（前台服务）
- 跑步统计（距离、时长、配速）
- 轨迹回放（地图展示历史轨迹）
- 志愿者徽章系统

### 双向功能

- WebSocket 实时状态同步
- 跑步轨迹记录与上传
- 双向评价系统（评分 + 标签 + 评论）
- 紧急联系人管理

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
- UI：Jetpack Compose + XML View（视障端关键页面）
- 架构：MVVM + Clean Architecture
- 异步：Coroutines + Flow
- DI：Hilt
- 网络：Retrofit + OkHttp + Kotlinx.serialization
- 本地存储：Room + DataStore
- 地图：高德地图 SDK
- 语音：Android TextToSpeech / SpeechRecognizer

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
- 数据库：MySQL 8.0 + Flyway 迁移
- ORM：Spring Data JPA + Hypersistence Utils
- 实时通信：Spring WebSocket

## 项目结构

```
GuideRun/
├── client/                    # Android 客户端
│   └── app/src/main/java/com/guiderun/app/
│       ├── accessibility/     # 无障碍组件 (TTS/ASR/震动)
│       ├── data/              # 数据层 (Room/Retrofit/Repository)
│       ├── di/                # Hilt 依赖注入
│       ├── domain/            # 领域层 (Models/UseCases)
│       ├── service/           # 前台服务 (位置上报/轨迹记录)
│       └── ui/                # UI 层
│           ├── auth/          # 登录注册
│           ├── blind/         # 视障端 (XML View)
│           ├── volunteer/     # 志愿者端 (Compose)
│           └── home/          # 首页
├── server/                    # Spring Boot 服务端
│   └── src/main/kotlin/com/guiderun/server/
│       ├── controller/        # REST API
│       ├── domain/            # 领域模型
│       ├── repository/        # 数据访问
│       ├── service/           # 业务逻辑
│       └── websocket/         # WebSocket 处理
├── DESIGN.md                  # 完整设计方案
├── PROGRESS.md                # 开发进度追踪
└── docker-compose.yml         # Docker 配置
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
3. 连接 Android 设备或启动模拟器
4. 运行应用

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
- `FINISHED` - 跑步结束
- `CLOSED` - 双方评价完成
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
    │  10. 结束跑步            │                         │
    │ <────────────────────── │ <────────────────────── │
    │                         │                         │
    │  11. 评价               │  12. 评价                │
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

### 关键实现

**TTS 管理器**
- 优先级队列（紧急消息打断普通消息）
- 语速可调（默认 1.5x）
- 生命周期感知

**震动反馈**
```kotlin
// 语义化震动
haptic.success()      // 短-短
haptic.error()        // 长振
haptic.notification() // 单短
haptic.emergency()    // 三次渐强
```

**语音指令**
- 热词：发起跑步、取消、救命、结束、评价
- 识别失败降级到触觉操作

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
GET    /api/v1/users/me         # 当前用户信息
PATCH  /api/v1/users/me         # 更新基础信息
POST   /api/v1/users/me/roles   # 申请新角色
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
POST   /api/v1/run-requests/{id}/end-run       # 结束跑步

# 双向
POST   /api/v1/run-requests/{id}/tracks        # 上传轨迹
GET    /api/v1/run-requests/{id}/tracks        # 查询轨迹
POST   /api/v1/run-requests/{id}/reviews       # 评价
```

### WebSocket

```javascript
// 连接
ws://localhost:8080/api/v1/ws?token=<access_token>

// 客户端 → 服务端
{ "type": "subscribe", "channel": "request:<id>" }
{ "type": "location", "lat": 39.9, "lng": 116.4 }

// 服务端 → 客户端
{ "type": "status_changed", "requestId": "...", "from": "...", "to": "..." }
{ "type": "new_match_available", "requestId": "..." }
{ "type": "peer_location", "requestId": "...", "lat": 39.9, "lng": 116.4 }
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

- 视障端关键页面使用 XML View 保证 TalkBack 稳定性
- TTS 优先级队列实现紧急消息打断
- 语义化震动反馈（成功/错误/通知/紧急）
- 语音指令热词识别

### 2. 状态机驱动的业务逻辑

- 9 个状态的完整生命周期管理
- 乐观锁防止并发抢单
- 幂等键防止重复请求

### 3. 实时位置共享

- WebSocket 双向通信
- 前台服务持续位置上报
- 断线重连 + 指数退避

### 4. 现代 Android 开发实践

- MVVM + Clean Architecture 分层
- Coroutines + Flow 响应式编程
- Hilt 依赖注入
- Jetpack Compose + XML View 混合使用

---

**GuideRun** - 让每一次跑步都有陪伴
