package com.guiderun.app.util

import kotlinx.coroutines.CancellationException

/**
 * 与 [runCatching] 行为一致，但**重新抛出** [CancellationException]。
 *
 * 直接用 `runCatching` 包裹 suspend 调用时，协程被取消（用户离开页面、scope 关闭）
 * 抛出的 [CancellationException] 会被当成普通失败吞掉转成 `Result.failure`，
 * 导致调用方在已取消的协程里继续执行（弹错误 UI / 改 state），破坏结构化并发。
 *
 * 任何"包裹网络/DB/定位等可取消调用"的地方都应使用本函数替代裸 [runCatching]。
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
