package com.aazhou.mtforum

import com.aazhou.mtforum.ui.common.toAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmileyAnnotationTest {

    /**
     * 行内表情靠注解值去 inlineContent map 里查：注解值必须是占位字符本身，
     * 否则表情不会被替换（曾经把注解值写成图片地址，导致表情全部不显示）。
     */
    @Test
    fun annotationValueMatchesInlineContentKey() {
        val url = "https://cdn.binmt.cc/static/image/smiley/doge/2.png"
        val placeholder = '\uE000'
        val smiles = mapOf(placeholder to url)
        val text = "看这个表情$placeholder 后面"

        val annotated = text.toAnnotatedString(smiles)
        val tag = "androidx.compose.foundation.text.inlineContent"
        val annotations = annotated.getStringAnnotations(tag, 0, annotated.length)

        assertEquals(1, annotations.size)
        assertEquals(placeholder.toString(), annotations.single().item)
    }

    @Test
    fun noAnnotationWhenNoSmiley() {
        val annotated = "普通文字".toAnnotatedString(mapOf('\uE000' to "https://x/y.png"))
        val tag = "androidx.compose.foundation.text.inlineContent"
        assertTrue(annotated.getStringAnnotations(tag, 0, annotated.length).isEmpty())
    }
}
