package com.guiderun.server.dto.common

/** 列表响应包装：当前 `total = items.size`，预留为后续真正分页扩展。 */
data class ListResponse<T>(
    val items: List<T>,
    val total: Long,
) {
    companion object {
        fun <T> of(items: List<T>): ListResponse<T> = ListResponse(items, items.size.toLong())
    }
}
