package com.aazhou.mtforum.ui.screen.drafts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.PostDraft
import com.aazhou.mtforum.data.repo.DraftStore
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.MtfConfirmDialog
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DraftsViewModel(private val draftStore: DraftStore) : ViewModel() {
    val drafts: StateFlow<List<PostDraft>> = draftStore.items

    /** 多选删除：一次可删多份，删除只发生在用户确认之后。 */
    fun remove(fids: List<String>) {
        viewModelScope.launch { draftStore.remove(fids) }
    }

    fun clear() {
        viewModelScope.launch { draftStore.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    /** 继续写这一篇：跳到对应版块的发帖页（草稿由发帖页自行回填）。 */
    onOpenDraft: (String, String) -> Unit,
) {
    val colors = LocalMtfColors.current
    val viewModel: DraftsViewModel = appViewModel { DraftsViewModel(it.draftStore) }
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val selected = remember { mutableStateListOf<String>() }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        contentColor = bodyTextColor(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.topBar),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "返回",
                            tint = bodyTextColor(),
                        )
                    }
                },
                title = {
                    Text(
                        "草稿箱",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Bold,
                        color = bodyTextColor(),
                    )
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        Text(
                            "删除(${selected.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { confirmDelete = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    } else if (drafts.isNotEmpty()) {
                        Text(
                            "清空",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { confirmClear = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                },
                expandedHeight = MtfTopBarHeight,
            )
        },
    ) { padding ->
        if (drafts.isEmpty()) {
            EmptyBox("暂无草稿", Modifier.padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "select-all") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "共 ${drafts.size} 篇",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textHint,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (selected.size == drafts.size) "取消全选" else "全选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    if (selected.size == drafts.size) {
                                        selected.clear()
                                    } else {
                                        selected.clear()
                                        selected.addAll(drafts.map { it.fid })
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                items(drafts, key = { it.fid }) { draft ->
                    val checked = draft.fid in selected
                    DraftCard(
                        draft = draft,
                        checked = checked,
                        onClick = {
                            // 已进入多选态时，点卡片也是勾选/取消，不再跳去发帖页
                            if (selected.isEmpty()) {
                                onOpenDraft(draft.fid, draft.forumName)
                            } else if (checked) {
                                selected.remove(draft.fid)
                            } else {
                                selected.add(draft.fid)
                            }
                        },
                        onToggle = { if (checked) selected.remove(draft.fid) else selected.add(draft.fid) },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        MtfConfirmDialog(
            title = "删除草稿",
            message = "确定删除选中的 ${selected.size} 篇草稿吗？删除后无法恢复。",
            confirmText = "确认删除",
            onDismissRequest = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.remove(selected.toList())
                selected.clear()
            },
        )
    }

    if (confirmClear) {
        MtfConfirmDialog(
            title = "清空草稿箱",
            message = "确定清空全部 ${drafts.size} 篇草稿吗？清空后无法恢复。",
            confirmText = "确认清空",
            onDismissRequest = { confirmClear = false },
            onConfirm = {
                confirmClear = false
                viewModel.clear()
                selected.clear()
            },
        )
    }
}

/** 一篇草稿：左侧多选勾框，右侧版块/时间 + 标题 + 正文摘录。 */
@Composable
private fun DraftCard(
    draft: PostDraft,
    checked: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .then(
                if (isDark) {
                    Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else colors.background,
                )
                .border(
                    width = if (checked) 0.dp else 1.dp,
                    color = if (checked) Color.Transparent else colors.divider,
                    shape = CircleShape,
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = "已选中",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = draft.forumName.ifBlank { "版块 ${draft.fid}" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatDraftTime(draft.savedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textHint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = draft.subject.ifBlank { "无标题" },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (draft.message.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = draft.message.trim(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDraftTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}
