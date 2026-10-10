package com.aazhou.mtforum.ui.screen.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.R
import com.aazhou.mtforum.ui.common.MtfContentDialog
import com.aazhou.mtforum.ui.common.appViewModel
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.bodyTextColor

/** 精致的浮层式登录弹窗。 */
@Composable
fun LoginDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val colors = LocalMtfColors.current
    val focusManager = LocalFocusManager.current
    val viewModel: LoginViewModel = appViewModel { LoginViewModel(it.repository, it.sessionStore) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is LoginViewModel.LoginUiState.Success) {
            viewModel.reset()
            onSuccess()
        }
    }

    val isLoading = state is LoginViewModel.LoginUiState.Loading
    val errorMessage = (state as? LoginViewModel.LoginUiState.Error)?.message

    val doLogin = {
        if (!isLoading) {
            focusManager.clearFocus()
            viewModel.login(username, password)
        }
    }

    MtfContentDialog(
        title = "账号登录",
        subtitle = "登录 MT论坛 畅享更多功能",
        onDismissRequest = onDismiss,
    ) {
        // 用户名输入框
        OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        if (errorMessage != null) viewModel.reset()
                    },
                    placeholder = {
                        Text("用户名 / UID", fontSize = 14.sp, color = colors.textHint)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_me),
                            contentDescription = null,
                            tint = colors.textHint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (username.isNotEmpty()) {
                            IconButton(onClick = { username = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "清空",
                                    tint = colors.textHint,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = colors.divider,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // 密码输入框
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (errorMessage != null) viewModel.reset()
                    },
                    placeholder = {
                        Text("登录密码", fontSize = 14.sp, color = colors.textHint)
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock_outline),
                            contentDescription = null,
                            tint = colors.textHint,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    if (passwordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                                ),
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = colors.textHint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { doLogin() },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = colors.divider,
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // 错误信息展示条
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 登录主操作按钮
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isLoading) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                        .clickable(enabled = !isLoading, onClick = doLogin),
                ) {
                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "正在登录…",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    } else {
                        Text(
                            text = "登 录",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
    }
}
