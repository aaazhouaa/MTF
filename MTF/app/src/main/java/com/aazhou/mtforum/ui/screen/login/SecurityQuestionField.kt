package com.aazhou.mtforum.ui.screen.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.common.MtfContentDialog
import com.aazhou.mtforum.ui.theme.LocalIsDarkTheme
import com.aazhou.mtforum.ui.theme.LocalMtfColors

/**
 * 精致的安全提问选择框 + 答案输入。
 *
 * 站点登录页把安全提问作为必带字段（select[name=questionid] 默认 0「未设置请忽略」），
 * 设过安全提问的账号不带它登录会被拒；未设置时选中第 0 项，等价于不带该字段，答案框隐藏。
 */
@Composable
fun SecurityQuestionField(
    questionId: Int,
    onQuestionIdChange: (Int) -> Unit,
    answer: String,
    onAnswerChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val colors = LocalMtfColors.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary

    var showPicker by remember { mutableStateOf(false) }
    val current = SECURITY_QUESTIONS.firstOrNull { it.first == questionId } ?: SECURITY_QUESTIONS.first()
    val isQuestionSet = questionId > 0

    Column(modifier = modifier.fillMaxWidth()) {
        // 安全提问选择输入框
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = if (isQuestionSet) primary.copy(alpha = 0.6f) else borderColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .background(containerColor)
                .clickable { showPicker = true }
                .padding(horizontal = 16.dp, vertical = if (isQuestionSet) 8.dp else 0.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_outline),
                contentDescription = null,
                tint = if (isQuestionSet) primary else hintColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                if (isQuestionSet) {
                    Text(
                        text = "安全提问",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = current.second,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = textColor,
                        fontWeight = FontWeight.Normal,
                    )
                } else {
                    Text(
                        text = current.second,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = hintColor,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = "选择安全提问",
                tint = if (isQuestionSet) primary else hintColor,
                modifier = Modifier.size(16.dp),
            )
        }

        // 答案输入框平滑展开
        AnimatedVisibility(
            visible = isQuestionSet,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = onAnswerChange,
                    placeholder = {
                        Text("请输入安全提问的答案", fontSize = 14.sp, color = hintColor)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit_two),
                            contentDescription = null,
                            tint = hintColor,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (answer.isNotEmpty()) {
                            IconButton(onClick = { onAnswerChange("") }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "清空答案",
                                    tint = hintColor,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = primary,
                        unfocusedBorderColor = borderColor,
                        focusedContainerColor = containerColor,
                        unfocusedContainerColor = containerColor,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // 精致独立的选择弹窗
    if (showPicker) {
        MtfContentDialog(
            title = "安全提问",
            subtitle = "若账号未设置安全提问，请选择第一项",
            onDismissRequest = { showPicker = false },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SECURITY_QUESTIONS, key = { it.first }) { (id, label) ->
                    val isSelected = id == questionId
                    val itemBg = if (isSelected) {
                        primary.copy(alpha = if (isDark) 0.18f else 0.08f)
                    } else {
                        colors.quoteBackground.copy(alpha = if (isDark) 0.35f else 0.55f)
                    }
                    val itemBorder = if (isSelected) {
                        primary.copy(alpha = 0.55f)
                    } else {
                        colors.divider.copy(alpha = 0.5f)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBg)
                            .border(1.dp, itemBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                onQuestionIdChange(id)
                                if (id == 0) onAnswerChange("")
                                showPicker = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) primary else textColor,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(primary.copy(alpha = 0.15f)),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = "已选择",
                                    tint = primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
