package com.aazhou.mtforum.ui.common

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * 屏蔽本应用的无障碍内容：语义树清空 + 视图本身标记为不重要。
 *
 * 开着「跳过开屏广告」类无障碍服务（GKD、自动点击器）时，列表快滑必卡，关掉该服务即恢复。
 * 开销落在本进程：Compose 每帧把新流入的卡片翻译成 AccessibilityNodeInfo，交给
 * AndroidComposeView 的 AccessibilityDelegate 供服务跨进程拉取。已试过且无效的路：
 * 合并卡片语义节点、只在滑动期间清空语义树（滚动状态没能可靠送到根部）。
 * 彻底屏蔽后实测与关闭无障碍完全一致，故定稿为永久屏蔽。
 *
 * 代价：TalkBack 等读屏软件读不到这个应用。
 */
@Composable
internal fun Modifier.maskAccessibility(): Modifier {
    val view = LocalView.current
    // 语义根节点没有子节点，服务遍历到的是空树；视图本身再标记为不重要，窗口根节点也不下发
    SideEffect { view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    return this.clearAndSetSemantics {}
}
