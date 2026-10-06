package com.aazhou.mtforum

import com.aazhou.mtforum.data.parser.ForumParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点赞响应解析。
 *
 * 样本取自站点真实返回（登录态下点赞一次），用于锁死「剩余次数来自响应字段」
 * 这一行为，避免日后被改成写死的提示。
 */
class LikeResponseTest {

    /** 真实成功响应：评价指数 2 +1，今天还能评价 4 次。 */
    private val successSample = """
        <?xml version="1.0" encoding="utf-8"?>
        <root><![CDATA[<div class="comiis_tip bg_f cl">
        <dt class="f_b" id="messagetext">
        <p>评价指数 <strong id="recommentc" class="xi1 xs2">2</strong> <span id="recommentv" class="xg2 xs0">+1</span><br />您今天还能评价 4 次<script type="text/javascript" reload="1">if(typeof errorhandle_recommend_add=='function') {errorhandle_recommend_add('评价指数 2 +1您今天还能评价 4 次', {'recommendv':'+1','recommendc':'2','daycount':'4'});}</script></p>
        </dt>
        <dd class="b_t cl"><a href="javascript:;" onclick="popup.close();" class="tip_btn tip_all bg_f f_b"><span>关闭</span></dd>
        </div>
        ]]></root>
    """.trimIndent()

    @Test
    fun successParsesRemainingAndCount() {
        val result = ForumParser.parseLikeResult(successSample, like = true)
        assertTrue(result.success)
        // 剩余次数取自 daycount，不得为写死值
        assertEquals(4, result.remainingToday)
        assertEquals(2, result.serverCount)
    }

    @Test
    fun remainingFallsBackToChineseText() {
        // 无 daycount 时用「还能评价 N 次」兜底
        val body = "<root>评价指数 5 +1 您今天还能评价 2 次</root>"
        assertEquals(2, ForumParser.parseLikeResult(body, like = true).remainingToday)
    }

    @Test
    fun errorResponseIsNotSuccess() {
        val body = "<root>抱歉，您今天评价机会已用完</root>"
        val result = ForumParser.parseLikeResult(body, like = true)
        assertFalse(result.success)
    }

    /** 真实取消响应：站点仅回 `ok`，无次数与计数。 */
    private val cancelSample =
        """<?xml version="1.0" encoding="utf-8"?><root><![CDATA[<div class="comiis_tip bg_f cl"><dt class="f_b" id="messagetext"><p>ok<script type="text/javascript" reload="1">if(typeof errorhandle_=='function') {errorhandle_('ok', {});}</script></p></dt></div>]]></root>"""

    @Test
    fun cancelReturnsOk() {
        val result = ForumParser.parseLikeResult(cancelSample, like = false)
        assertTrue(result.success)
        assertNull(result.remainingToday)
    }
}
