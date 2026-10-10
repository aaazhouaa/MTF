package com.aazhou.mtforum.ui.screen.threaddetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import com.aazhou.mtforum.ui.common.MtfTopBarHeight
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor
import com.aazhou.mtforum.ui.theme.dimImageInDark

/**
 * 「@朋友」选择页：默认列出好友与关注，顶部搜索框可全局搜索全站用户。
 *
 * 站点好友列表、关注列表、用户搜索三处共用同一套用户列表结构，区别只在请求地址。
 */
@Composable
fun AtUserPickerPage(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AtUserPickerViewModel = appViewModel { AtUserPickerViewModel(it.repository) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalMtfColors.current
    var keyword by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    BackHandler { onDismiss() }

    // 进页面就把键盘请到搜索框上：从回复框点 @ 过来时键盘本来是升起状态，
    // 不主动要焦点的话它会留在回复框那层，用户敲的字全落进正文草稿里
    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(MtfTopBarHeight)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    tint = bodyTextColor(),
                )
            }
            Spacer(Modifier.width(4.dp))
            AtUserSearchField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    viewModel.search(it)
                },
                focusRequester = searchFocusRequester,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
        }

        val trimmed = keyword.trim()
        val showLists = trimmed.isEmpty()
        val hasLists = state.friends.isNotEmpty() || state.follows.isNotEmpty()
        when {
            showLists && state.loadingLists -> LoadingBox()
            showLists && !hasLists -> EmptyBox("还没有好友和关注，可用上面的搜索框搜全站用户")
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 16.dp),
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
private fun AtUserSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = bodyTextColor(), fontSize = 14.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.quoteBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        "搜索用户",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = colors.textHint,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun AtUserSectionHeader(title: String, count: Int) {
    Text(
        "$title $count",
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = LocalMtfColors.current.textHint,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AtUserRow(user: AtUser, onClick: () -> Unit) {
    val colors = LocalMtfColors.current
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
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.quoteBackground)
                .dimImageInDark(),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = bodyTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.subtitle.isNotEmpty()) {
                Text(
                    user.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = colors.textHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            painterResource(R.drawable.ic_at),
            contentDescription = null,
            tint = colors.textHint,
            modifier = Modifier.size(20.dp),
        )
    }
}
