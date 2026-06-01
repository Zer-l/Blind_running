package com.guiderun.server.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 定时任务装配开关，与主应用类上的 `@EnableScheduling` 双重保险。
 * 单独类形式便于测试环境通过 Profile 排除。
 */
@Configuration
@EnableScheduling
class SchedulingConfig
