package com.aazhou.mtforum.ui.common

/** 通用加载状态。 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** 把任意可空文本格式化为显示值。 */
fun String?.orDash(): String = if (isNullOrBlank()) "—" else this
