package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.EditPostResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 编辑页解析：取回 BBCode 原文、令牌、分类，以及提交结果的成败判定。 */
class ForumParserEditPostTest {

    private val tid = "174032"

    private val editPageHtml = """
        <html><body>
        <form method="post" name="postform" action="forum.php?mod=post&action=edit&extra=&tid=123&pid=456&page=1&editsubmit=yes&mobile=2">
          <input type="hidden" name="formhash" value="a1b2c3d4" />
          <input type="text" name="subject" id="subject" value="旧标题" />
          <textarea name="message" id="e_textarea">第一段 [b]加粗[/b]

第二段 &lt;转义&gt; 与 [attachimg]99[/attachimg]</textarea>
          <select name="typeid">
            <option value="0">请选择</option>
            <option value="12" selected>资源</option>
          </select>
          <script>var uploadConfig = {uploadformdata:{uid:"99001", hash:"deadbeef"}};</script>
          <input type="submit" name="editsubmit" value="提交" />
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun `parseEditPostInit reads bbcode textarea verbatim`() {
        val data = ForumParser.parseEditPostInit(editPageHtml)
        assertEquals("a1b2c3d4", data.formhash)
        assertEquals("旧标题", data.subject)
        // 换行必须原样保留（站点 textarea 里就是多段原文），实体要还原成字符
        assertEquals(
            "第一段 [b]加粗[/b]\n\n第二段 <转义> 与 [attachimg]99[/attachimg]",
            data.message,
        )
        assertNull(data.permissionDenied)
    }

    /**
     * 编辑页已有的图片附件：站点把它们列在 ul#imglist 里（结构与真机抓包一致），
     * 每条 li 带 span.del[aid] 与 img#aimg_<aid>，缩略图地址即正文引用的那张图。
     */
    @Test
    fun `parseEditPostInit reads existing image attachments`() {
        val html = """
            <html><body>
            <form method="post" name="postform" action="forum.php?mod=post&action=edit&tid=174032&pid=11910108&mobile=2">
              <input type="hidden" name="formhash" value="a1b2c3d4" />
              <textarea name="message" id="needmessage">放几张界面截图:
            [attachimg]377576[/attachimg]
            [attachimg]377577[/attachimg]</textarea>
              <div class="comiis_upbox comiis_allowpostimg bg_f cl">
                <ul id="imglist" class="comiis_post_imglist cl">
                  <li class="up_btn"><a href="javascript:;"><input type="file" name="Filedata" id="filedata"></a></li>
                  <li><span aid="377577" up="1" class="del"><a href="javascript:;"></a></span><span class="p_img"><a href="javascript:;" onclick="comiis_addsmilies('[attachimg]377577[/attachimg]')"><span class="charu f_f">插入</span><img style="height:54px;width:54px;" id="aimg_377577" title="shot2.jpg" src="https://oos.binmt.cc//forum/202610/05/133004m0uxckwnluoljn49.jpg" class="vm b_ok"></a></span>
            <input type="hidden" name="attachnew[377577][description]" value="">
            </li>
                  <li><span aid="377576" up="1" class="del"><a href="javascript:;"></a></span><span class="p_img"><a href="javascript:;" onclick="comiis_addsmilies('[attachimg]377576[/attachimg]')"><span class="charu f_f">插入</span><img style="height:54px;width:54px;" id="aimg_377576" title="shot1.jpg" src="https://oos.binmt.cc//forum/202610/05/133003r0izxax0y1wrxlzt.jpg" class="vm b_ok"></a></span>
            <input type="hidden" name="attachnew[377576][description]" value="">
            </li>
                </ul>
              </div>
            </form>
            </body></html>
        """.trimIndent()
        val attachments = ForumParser.parseEditPostInit(html).attachments
        assertEquals(2, attachments.size)
        // 顺序按站点表单给的来（新的在前），不按正文里的引用顺序
        assertEquals("377577", attachments[0].aid)
        assertEquals("https://oos.binmt.cc//forum/202610/05/133004m0uxckwnluoljn49.jpg", attachments[0].url)
        assertEquals("shot2.jpg", attachments[0].fileName)
        assertEquals("377576", attachments[1].aid)
        assertEquals("shot1.jpg", attachments[1].fileName)
    }

    /** 没有附件时列表里只剩上传按钮，不能把它当成一条附件。 */
    @Test
    fun `parseEditPostInit without attachments yields empty list`() {
        val html = editPageHtml.replace(
            "<script>var uploadConfig",
            "<ul id=\"imglist\" class=\"comiis_post_imglist cl\"><li class=\"up_btn\"></li></ul>\n          <script>var uploadConfig",
        )
        assertTrue(ForumParser.parseEditPostInit(html).attachments.isEmpty())
    }

    @Test
    fun `parseEditPostInit reads upload credentials and types`() {
        val data = ForumParser.parseEditPostInit(editPageHtml)
        assertEquals("99001", data.uploadUid)
        assertEquals("deadbeef", data.uploadHash)
        assertEquals(1, data.types.size)
        assertEquals("12", data.types.first().id)
        assertEquals("资源", data.types.first().name)
    }

    @Test
    fun `parseEditPostInit flags login redirect`() {
        val html = editPageHtml.replace(
            "<form method=\"post\" name=\"postform\"",
            "<form id=\"loginform\" method=\"post\" action=\"member.php?mod=logging&action=login\"",
        )
        assertEquals("请先登录后再编辑", ForumParser.parseEditPostInit(html).permissionDenied)
    }

    @Test
    fun `reply edit page has no subject field`() {
        val html = editPageHtml
            .replace(Regex("<input type=\"text\" name=\"subject\"[^>]*/>"), "")
        assertNull(ForumParser.parseEditPostInit(html).subject)
    }

    @Test
    fun `edit result success markers`() {
        assertTrue(ForumParser.parseEditPostResult("succeedhandle_('123', 'ok')", tid).success)
        assertTrue(ForumParser.parseEditPostResult("<div>帖子更新成功</div>", tid).success)
        assertTrue(ForumParser.parseEditPostResult("post_edit_succeed", tid).success)
    }

    /**
     * 编辑成功时站点 301 回帖子页；帖子页自带的快速回复回调就叫 errorhandle_ 这名。
     *
     * 帖子页还带自己楼层的「编辑」链接（action=edit），而提交按钮名 editsubmit 只在编辑
     * 表单上出现：这两个标记同时成立才算「表单被原样退回」，少一个都会把成功判成失败。
     */
    @Test
    fun `edit result success when redirected back to the thread page`() {
        val body = """
            <html><head><title>(停更)MT论坛app(液态玻璃) - MT论坛</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174032-1-1.html" /></head><body>
            <script type="text/javascript">
                function errorhandle_fastposts(message, param) {
                    popup.open(message, 'alert');
                    comiis_prevent_post('#fastpostsubmit_btn', 0);
                }
                function succeedhandle_fastposts(message, param) {
                    message = '本版回帖需要审核，您的帖子将在通过审核后显示';
                    popup.open(message, 'alert');
                }
            </script>
            <div id="postlist">
              <div id="post_11921862">
                本贴最后由 username123 于 2026-10-11 09:47 编辑 卒。
                <a href="forum.php?mod=post&amp;action=edit&amp;tid=174032&amp;pid=11921862&amp;mobile=2">编辑</a>
              </div>
              <a href="forum.php?mod=viewthread&tid=174032&page=1">刷新</a>
              <a href="forum.php?mod=viewthread&amp;tid=174032&amp;page=1">下一页</a>
              <a href="forum.php?mod=redirect&goto=findpost&ptid=174032&pid=11921862">查看</a>
            </div>
            <form id="fastpostform" action="forum.php?mod=post&action=reply&fid=42&tid=174032&replysubmit=yes&mobile=2"></form>
            <a href="member.php?mod=logging&action=login">登录</a>
            </body></html>
        """.trimIndent()
        val result = ForumParser.parseEditPostResult(body, tid)
        assertTrue(result.success)
        assertNull(result.message)
    }

    /** 帖子页里自己楼层的「编辑」链接也带 action=edit，光有它不算「表单被退回」。 */
    @Test
    fun `edit result success when thread page carries edit links`() {
        val body = """
            <html><body>
            <div id="post_11921862">
              <a href="forum.php?mod=post&action=edit&tid=174032&pid=11921862&mobile=2">编辑</a>
              <a href="forum.php?mod=post&action=edit&tid=174032&pid=11921863&mobile=2">编辑</a>
            </div>
            <a href="forum.php?mod=viewthread&amp;tid=174032&amp;page=1">刷新</a>
            </body></html>
        """.trimIndent()
        assertTrue(ForumParser.parseEditPostResult(body, tid).success)
    }

    @Test
    fun `edit result failure when form comes back`() {
        val result = ForumParser.parseEditPostResult(editPageHtml, tid)
        assertFalse(result.success)
        assertEquals("编辑未生效，请稍后重试", result.message)
    }

    @Test
    fun `edit result failure on login redirect and ajax error`() {
        assertFalse(ForumParser.parseEditPostResult("action=login", tid).success)
        val errored = ForumParser.parseEditPostResult(
            "errorhandle_showmessage('您没有权限编辑该帖', 'forum.php')",
            tid,
        )
        assertFalse(errored.success)
        assertEquals("您没有权限编辑该帖", errored.message)
    }

    /** 帖子页里到处都是登录入口与帖子链接，空 tid 时不能拿它们当「跳回本帖」的信号。 */
    @Test
    fun `edit result without tid does not treat thread links as success`() {
        val body = "<a href=\"forum.php?mod=viewthread&tid=174032\">别的一帖</a>"
        assertFalse(ForumParser.parseEditPostResult(body, "").success)
    }

    @Test
    fun `success result carries no message`() {
        val result: EditPostResult = ForumParser.parseEditPostResult("succeedhandle_('123')", tid)
        assertTrue(result.success)
        assertNull(result.message)
    }
}
