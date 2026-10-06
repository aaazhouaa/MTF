package com.aazhou.mtforum.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aazhou.mtforum.ui.common.container
import com.aazhou.mtforum.ui.theme.DefaultThemeIndex
import com.aazhou.mtforum.ui.theme.LocalMtfColors
import com.aazhou.mtforum.ui.theme.ThemePresets
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val themeStore = LocalContext.current.container().themeStore
    val scope = rememberCoroutineScope()
    val themeIndex by themeStore.themeIndex.collectAsStateWithLifecycle(initialValue = DefaultThemeIndex)
    val darkOverride by themeStore.darkOverride.collectAsStateWithLifecycle(initialValue = null)
    val colors = LocalMtfColors.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        containerColor = colors.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("主题色彩", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ThemePresets.indices.chunked(3).forEach { rowIndices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowIndices.forEach { index ->
                        ThemeColorCell(
                            name = ThemePresets[index].name,
                            color = ThemePresets[index].primaryDay,
                            selected = index == themeIndex,
                            onClick = { scope.launch { themeStore.setThemeIndex(index) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 补足末行空位，保持三列对齐
                    repeat(3 - rowIndices.size) { Box(Modifier.weight(1f)) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("跟随系统深色模式", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭后固定为浅色，可用首页顶栏按钮切深色",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Switch(
                    checked = darkOverride == null,
                    onCheckedChange = { follow -> scope.launch { themeStore.setDarkOverride(if (follow) null else false) } },
                )
            }

            Text(
                "MTF v${com.aazhou.mtforum.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ThemeColorCell(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMtfColors.current
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painterResource(com.aazhou.mtforum.R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
