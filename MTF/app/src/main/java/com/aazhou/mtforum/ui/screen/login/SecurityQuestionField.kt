package com.aazhou.mtforum.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aazhou.mtforum.R

/**
 * 安全提问下拉 + 答案输入。
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
    var expanded by remember { mutableStateOf(false) }
    val current = SECURITY_QUESTIONS.firstOrNull { it.first == questionId } ?: SECURITY_QUESTIONS.first()

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .background(containerColor)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_outline),
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = current.second,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = if (questionId == 0) hintColor else textColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = "展开安全提问",
                tint = hintColor,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = containerColor,
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            SECURITY_QUESTIONS.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)) },
                    onClick = {
                        expanded = false
                        onQuestionIdChange(id)
                        if (id == 0) onAnswerChange("")
                    },
                )
            }
        }
    }

    if (questionId > 0) {
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = answer,
            onValueChange = onAnswerChange,
            placeholder = { Text("请输入问题答案", fontSize = 14.sp, color = hintColor) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = borderColor,
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
