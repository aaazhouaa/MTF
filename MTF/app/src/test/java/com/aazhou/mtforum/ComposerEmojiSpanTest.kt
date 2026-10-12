package com.aazhou.mtforum

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.TextUnit
import com.aazhou.mtforum.data.parser.Smiley
import com.aazhou.mtforum.ui.screen.threaddetail.formatRichComposerText
import com.aazhou.mtforum.ui.screen.threaddetail.smileySlotSpacing
import com.aazhou.mtforum.ui.theme.LightMtfColors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输入框里表情占位 span 的约束。
 *
 * 表情图片是贴图层按文本坐标画的，占位文字本身是透明的，唯一作用是占住横向宽度。
 * 但它同时决定所在行的行盒高度：字号一缩到远小于正文，整行只有这个 span 的纯表情行
 * 行盒就被压矮，贴图按行盒夹紧后跟着缩成一点；混排行有正文撑着所以看不出差别
 * （[#呵呵] 的占位曾被缩到 10sp，纯表情行的表情只剩一点点大）。
 */
class ComposerEmojiSpanTest {

    private val smiles = mapOf(
        "[呵呵]" to Smiley(code = "[呵呵]", url = "https://cdn.binmt.cc/static/image/smiley/qq/qq001.gif"),
        "[#滑稽]" to Smiley(code = "[#滑稽]", url = "https://cdn.binmt.cc/static/image/smiley/comiis_tb/tb001.gif"),
    )

    @Test
    fun smileyPlaceholderDoesNotOverrideFontSize() {
        val annotated = formatRichComposerText("[呵呵]", smiles, LightMtfColors, Color.Black)
        val span = annotated.spanStyles.single().item
        assertEquals(Color.Transparent, span.color)
        // 不覆盖字号：占位跟随正文，行盒才不会被压矮
        assertEquals(TextUnit.Unspecified, span.fontSize)
    }

    @Test
    fun smileySlotSpacedToOneEmojiWidth() {
        // 方括号约 0.4em、全角 1em：[呵呵] 原宽 2.8em，收回到一个表情宽 1.2em
        assertEquals(-0.4f, smileySlotSpacing("[呵呵]").value, 0.001f)
        // [酷] 原宽 1.8em，按 3 个字符摊到 -0.2em
        assertEquals(-0.2f, smileySlotSpacing("[酷]").value, 0.001f)
        // 带 # 的码：# 按半角 0.55em 计，原宽 3.35em，摊到 5 个字符
        assertEquals(-0.43f, smileySlotSpacing("[#滑稽]").value, 0.001f)
    }

    @Test
    fun smileyPlaceholderCoversWholeCode() {
        val annotated = formatRichComposerText("看[呵呵]这个", smiles, LightMtfColors, Color.Black)
        val smiley = annotated.spanStyles.single { it.item.color == Color.Transparent }
        // 区间必须正好圈住整个表情码，含方括号，否则贴图定位会错位
        assertEquals(1, smiley.start)
        assertEquals("看".length + "[呵呵]".length, smiley.end)
    }
}
