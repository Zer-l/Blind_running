package com.guiderun.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Spring Boot 应用入口。
 *
 * - `@SpringBootApplication`：开启自动装配 + 组件扫描（包含本类所在包及子包）
 * - `@ConfigurationPropertiesScan`：扫描 `@ConfigurationProperties` 类（如 [com.guiderun.server.config.JwtProperties]）
 * - `@EnableScheduling`：开启 `@Scheduled` 定时任务（如 [com.guiderun.server.scheduler.OrderAutoCloseScheduler]）
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class GuideRunServerApplication

fun main(args: Array<String>) {
    runApplication<GuideRunServerApplication>(*args)
}
