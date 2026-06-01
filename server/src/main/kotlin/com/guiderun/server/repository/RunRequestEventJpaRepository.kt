package com.guiderun.server.repository

import com.guiderun.server.entity.RunRequestEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 订单状态变更审计流数据访问：按时间顺序回放 + 志愿者放弃次数计数（用于 3 次降级 ABORTED 判定）。
 */
interface RunRequestEventJpaRepository : JpaRepository<RunRequestEventEntity, Long> {

    /** 统计同一订单内志愿者放弃次数（ACCEPTED→MATCHING 的事件条数）。 */
    @Query("""
        SELECT COUNT(e) FROM RunRequestEventEntity e
        WHERE e.requestId = :requestId
          AND e.fromStatus = 'ACCEPTED'
          AND e.toStatus   = 'MATCHING'
    """)
    fun countAbandonsByRequestId(@Param("requestId") requestId: String): Int

    fun findByRequestIdOrderByOccurredAtAsc(requestId: String): List<RunRequestEventEntity>
}
