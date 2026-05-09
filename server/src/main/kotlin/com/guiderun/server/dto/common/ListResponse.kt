package com.guiderun.server.dto.common

data class ListResponse<T>(
    val items: List<T>,
    val total: Long,
) {
    companion object {
        fun <T> of(items: List<T>): ListResponse<T> = ListResponse(items, items.size.toLong())
    }
}
