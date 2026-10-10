package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aazhou.mtforum.R
import com.aazhou.mtforum.data.model.AtUser
import com.aazhou.mtforum.image.smallImageRequest
import com.aazhou.mtforum.ui.common.EmptyBox
import com.aazhou.mtforum.ui.common.LoadingBox
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark

@Composable
fun AtUserPickerPage(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AtUserPickerViewModel = appViewModel { AtUserPickerViewModel(it.repository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    var keyword by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler { onDismiss() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        // 顶栏：包含状态栏下移背景 + 返回 + 搜索胶囊
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.topBar)
                .statusBarsPadding()
                .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    tint = bodyTextColor(),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.quoteBackground)
                    .then(
                        if (isDark) Modifier.border(0.5.dp, colors.divider, RoundedCornerShape(20.dp))
                        else Modifier
                    )
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search_new),
                    contentDescription = null,
                    tint = colors.textHint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = keyword,
                    onValueChange = {
                        keyword = it
                        viewModel.search(it)
                    },
                    textStyle = TextStyle(color = bodyTextColor(), fontSize = 14.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (keyword.isEmpty()) {
                                Text(
                                    "搜索用户名 / 论坛用户...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                    color = colors.textHint,
                                )
                            }
                            inner()
                        }
                    },
                )

                AnimatedVisibility(
                    visible = keyword.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable {
                                keyword = ""
                                viewModel.search("")
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "清空",
                            tint = colors.textHint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        val trimmed = keyword.trim()
        val showLists = trimmed.isEmpty()
        val hasLists = state.friends.isNotEmpty() || state.follows.isNotEmpty()
        when {
            showLists && state.loadingLists -> LoadingBox()
            showLists && !hasLists -> EmptyBox("还没有好友和关注，可搜索全站用户")
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            ) {
                if (showLists) {
                    if (state.friends.isNotEmpty()) {
                        item(key = "header_friends") {
                            AtUserSectionHeader("好友", state.friends.size)
                        }
                        items(state.friends, key = { "friend_${it.uid}" }) { user ->
                            AtUserRow(user) { onPick(user.name) }
                        }
                    }
                    if (state.follows.isNotEmpty()) {
                        item(key = "header_follows") {
                            AtUserSectionHeader("关注", state.follows.size)
                        }
                        items(state.follows, key = { "follow_${it.uid}" }) { user ->
                            AtUserRow(user) { onPick(user.name) }
                        }
                    }
                } else {
                    if (state.searching && state.results.isEmpty()) {
                        item(key = "searching") { LoadingBox() }
                    } else if (state.results.isEmpty()) {
                        item(key = "no_result") { EmptyBox("没有找到相关用户") }
                    } else {
                        item(key = "header_search") {
                            AtUserSectionHeader("搜索结果", state.results.size)
                        }
                        items(state.results, key = { "search_${it.uid}" }) { user ->
                            AtUserRow(user) { onPick(user.name) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AtUserSectionHeader(title: String, count: Int) {
    val colors = LocalMtfColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.quoteBackground)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun AtUserRow(user: AtUser, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        AsyncImage(
            model = smallImageRequest(user.avatarUrl),
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .then(
                    if (isDark) Modifier.border(0.5.dp, colors.divider, CircleShape)
                    else Modifier
                )
                .dimImageInDark(),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val parts = user.subtitle.split(" ")
                    val groupName = parts.firstOrNull().orEmpty()
                    val pointsPart = parts.drop(1).joinToString(" ")

                    if (groupName.isNotBlank()) {
                        val isSpecialGroup = groupName.contains("管理") || groupName.contains("版主")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isSpecialGroup) colors.gold.copy(alpha = if (isDark) 0.22f else 0.12f)
                                    else colors.quoteBackground,
                                )
                                .padding(horizontal = 5.dp, vertical = 1.5.dp),
                        ) {
                            Text(
                                groupName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = if (isSpecialGroup) colors.gold else colors.textSecondary,
                            )
                        }
                    }
                    if (pointsPart.isNotBlank()) {
                        Text(
                            pointsPart,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.textHint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = if (isDark) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_at),
                contentDescription = "@该用户",
                tint = primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
