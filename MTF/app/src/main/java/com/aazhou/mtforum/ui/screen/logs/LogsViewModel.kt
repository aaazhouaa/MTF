package com.aazhou.mtforum.ui.screen.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.data.log.LogEntry
import com.aazhou.mtforum.data.log.LogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 日志列表：整份读入内存（上限 50 条），删除走清空或单条删除。
 *
 * 等级只决定「哪些日志会写入」，列表里再按等级筛选纯属查看便利，不影响存储内容。
 */
class LogsViewModel(private val store: LogStore) : ViewModel() {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** 全部日志，新的在前。 */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _entries.value = store.entries() }
    }

    fun clear() {
        viewModelScope.launch {
            store.clear()
            _entries.value = emptyList()
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            store.remove(id)
            _entries.value = store.entries()
        }
    }
}

/** 日志详情：按 id 取一条；条目可能已被清空，取不到时为 null。 */
class LogDetailViewModel(
    private val store: LogStore,
    private val id: Long,
) : ViewModel() {

    private val _entry = MutableStateFlow<LogEntry?>(null)
    val entry: StateFlow<LogEntry?> = _entry.asStateFlow()

    init {
        viewModelScope.launch { _entry.value = store.entries().firstOrNull { it.id == id } }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            store.remove(id)
            onDone()
        }
    }
}
