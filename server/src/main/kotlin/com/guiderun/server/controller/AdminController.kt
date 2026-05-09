package com.guiderun.server.controller

import com.guiderun.server.dto.ApiResponse
import com.guiderun.server.scheduler.OrderAutoCloseScheduler
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Profile("dev")
class AdminController(private val scheduler: OrderAutoCloseScheduler) {

    @PostMapping("/jobs/close-finished-orders")
    fun closeFinishedOrders(): ApiResponse<String> {
        scheduler.triggerManually()
        return ApiResponse.ok("已触发自动关闭任务")
    }
}
