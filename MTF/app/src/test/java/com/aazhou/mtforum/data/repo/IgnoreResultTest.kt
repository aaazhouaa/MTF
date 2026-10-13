package com.aazhou.mtforum.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 屏蔽提醒的回执判据。两种形状都取自站点真实响应：缺 inajax 时站点只回一张表单页，
 * 屏蔽根本不生效，早先「不含错误词」的判据会把这种情况当成成功。
 */
class IgnoreResultTest {

    @Test
    fun succeedReceiptIsSuccess() {
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <root><![CDATA[<div class="comiis_tip bg_f cl">
            <dt class="f_b" id="messagetext">
            <p>操作成功 <script type="text/javascript" reload="1">if(typeof succeedhandle_ignoreform_n1MP1uzZ=='function') {succeedhandle_ignoreform_n1MP1uzZ('https://bbs.binmt.cc/./?mobile=2', '操作成功 ', {'id':'0','type':'post','uid':'155872'});}</script></p>
            </dt>
            <script type="text/javascript">
            setTimeout(function() {
            window.location.href = 'https://bbs.binmt.cc/./?mobile=2';
            }, '3000');
            </script>
            </div>]]></root>
        """.trimIndent()
        assertTrue(isIgnoreSucceeded(body))
    }

    @Test
    fun receiptWithoutHandlekeyIsStillSuccess() {
        // 不带 handlekey 也能屏蔽成功，只是回执里 JS 回调没有名字
        val body = "<root><![CDATA[<p>操作成功 <script>if(typeof succeedhandle_=='function') {succeedhandle_('', '操作成功 ', {'id':'0'});}</script></p>]]></root>"
        assertTrue(isIgnoreSucceeded(body))
    }

    @Test
    fun formPageMeansNotSubmitted() {
        // 少了 inajax=1：站点把表单又渲染了一遍，屏蔽没发生
        val body = """<?xml version="1.0" encoding="utf-8"?><!DOCTYPE html>
            <html><body>
            <form method="post" id="ignoreform_Ab1234" action="home.php?mod=spacecp&amp;ac=common&amp;op=ignore&amp;type=post">
            <input type="hidden" name="ignoresubmit" value="true">
            <input type="hidden" name="formhash" value="1011f6f2">
            <input type="radio" name="authorid" value="155872" checked="checked">
            </form></body></html>"""
        assertFalse(isIgnoreSucceeded(body))
    }

    @Test
    fun emptyBodyIsNotSuccess() {
        assertFalse(isIgnoreSucceeded(""))
    }
}
