package com.guiderun.server.repository

import com.guiderun.server.common.RunRequestStatus
import com.guiderun.server.entity.RunRequestEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 跑步请求数据访问：覆盖按状态扫描（定时关单）、双端历史分页、活跃订单恢复等查询。
 * `findFirst*StatusIn*` 系列用于冷启动恢复——按角色筛选最近一条非终态订单。
 */
interface RunRequestJpaRepository : JpaRepository<RunRequestEntity, String> {

    fun findByStatus(status: RunRequestStatus): List<RunRequestEntity>

    fun findByBlindRunnerIdOrderByCreatedAtDesc(
        blindRunnerId: String,
        pageable: Pageable,
    ): List<RunRequestEntity>

    fun findByVolunteerIdOrderByCreatedAtDesc(
        volunteerId: String,
        pageable: Pageable,
    ): List<RunRequestEntity>

    fun findByVolunteerIdAndStatus(volunteerId: String, status: RunRequestStatus): List<RunRequestEntity>

    fun findByBlindRunnerIdAndStatus(blindRunnerId: String, status: RunRequestStatus): List<RunRequestEntity>

    fun findByStatusAndRunEndedAtBefore(
        status: RunRequestStatus,
        before: java.time.Instant,
    ): List<RunRequestEntity>

    fun findFirstByBlindRunnerIdAndStatusInOrderByCreatedAtDesc(
        blindRunnerId: String,
        statuses: Collection<RunRequestStatus>,
    ): RunRequestEntity?

    fun findFirstByVolunteerIdAndStatusInOrderByCreatedAtDesc(
        volunteerId: String,
        statuses: Collection<RunRequestStatus>,
    ): RunRequestEntity?

    fun findByBlindRunnerIdAndStatusInOrderByCreatedAtDesc(
        blindRunnerId: String,
        statuses: Collection<RunRequestStatus>,
        pageable: Pageable,
    ): List<RunRequestEntity>

    fun findByVolunteerIdAndStatusInOrderByCreatedAtDesc(
        volunteerId: String,
        statuses: Collection<RunRequestStatus>,
        pageable: Pageable,
    ): List<RunRequestEntity>
}
