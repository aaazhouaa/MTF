package com.aazhou.mtforum

import com.aazhou.mtforum.data.parser.ForumParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumParserTest {

    private val forumListHtml = """
        <div class="comiis_forumlist comiis_km1">
          <div class="comiis_bbs_show b_b cl">
            <h2><a href="javascript:;">MT专区</a></h2>
          </div>
          <div id="sub_forum_1" class="comiis_forum_nbox">
            <ul>
              <li><a href="https://bbs.binmt.cc/forum-2-1.html" class="b_b b_r">
                <em><img src="https://cdn.binmt.cc/icon_2.png" alt="版本发布" /></em>
                <p>版本发布</p></a></li>
              <li><a href="https://bbs.binmt.cc/forum-37-1.html" class="b_b b_r">
                <em><span class="bg_a f_f">37</span></em><p>插件交流</p></a></li>
            </ul>
          </div>
        </div>
    """.trimIndent()

    private val forumPageHtml = """
        <div class="comiis_forumlist">
          <ul>
            <li class="b_t"><a href="https://bbs.binmt.cc/thread-85763-1-1.html">
              <em>置顶</em>【重要】MT论坛总版规</a></li>
          </ul>
          <li class="forumlist_li comiis_znalist">
            <div class="forumlist_li_top cl">
              <a href="https://bbs.binmt.cc/home.php?mod=space&uid=156549&do=profile" class="wblist_tximg">
                <img class="top_tximg" src="https://avatar.mt2.cn/avatar.php?uid=156549"></a>
              <h2><a href="https://bbs.binmt.cc/home.php?mod=space&uid=156549&do=profile" class="top_user">feelingboy11</a>
                <span class="top_lev bg_a f_f">Lv.4</span></h2>
              <div class="forumlist_li_time"><span class="f_d">25 分钟前</span></div>
            </div>
            <div class="mmlist_li_box cl">
              <h2><a href="https://bbs.binmt.cc/thread-174048-1-1.html">影视仓精简TV版</a></h2>
              <div class="list_body cl"><a href="https://bbs.binmt.cc/thread-174048-1-1.html">影视仓6.1.7精简原版.apk</a></div>
            </div>
            <div class="comiis_xznalist_bottom cl">
              <ul>
                <li><a href="https://bbs.binmt.cc/thread-174048-1-1.html"><span class="comiis_tm">3</span></a></li>
                <li><span class="comiis_tm">25</span></li>
              </ul>
            </div>
          </li>
        </div>
    """.trimIndent()

    private val threadHtml = """
        <html><head><title>关于用AI自写网络验证 - MT论坛</title>
        <link rel="canonical" href="https://bbs.binmt.cc/thread-174050-1-1.html"></head>
        <body>
          <div class="comiis_postli comiis_list_readimgs" id="pid11911194">
            <div class="comiis_postli_top"><a class="postli_top_tximg"><img class="top_tximg" src="https://avatar.mt2.cn/a.png"></a>
              <h2><a class="top_user f_b">ChenHao</a><a class="top_lev bg_a f_f">Lv.3 初中生</a></h2>
              <div class="comiis_postli_time"><span class="f_d"><span class="kmtime">6 分钟前
                <code class="comiis_iplocality"><font>来自 广东</font></code></span></span></div>
            </div>
            <div class="comiis_message"><div class="comiis_messages"><div class="comiis_a comiis_message_table cl">
              目前进度百分之30<br /><img src="none.gif" file="https://cdn.binmt.cc/data/attachment/forum/x.png" />
            </div></div></div>
          </div>
          <script>formhash = 'b6a540bd', uid = '0';</script>
          <form id="fastpostform" action="forum.php?mod=post&action=reply&fid=42&tid=174050&replysubmit=yes"></form>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesForumGroups() {
        val groups = ForumParser.parseForumList(forumListHtml)
        assertEquals(1, groups.size)
        assertEquals("MT专区", groups[0].name)
        assertEquals(2, groups[0].forums.size)
        assertEquals("2", groups[0].forums[0].fid)
        assertEquals("版本发布", groups[0].forums[0].name)
    }

    @Test
    fun parsesForumPage() {
        val page = ForumParser.parseForumPage(forumPageHtml, 1)
        assertEquals(1, page.announcements.size)
        assertEquals("85763", page.announcements[0].tid)
        assertEquals(1, page.threads.size)
        val thread = page.threads[0]
        assertEquals("174048", thread.tid)
        assertEquals("影视仓精简TV版", thread.subject)
        assertEquals("feelingboy11", thread.authorName)
        assertEquals("25 分钟前", thread.timeText)
    }

    @Test
    fun announcementsExcludeRegularThreads() {
        // 真实站点：普通帖的 <li> class 也含 b_t（forumlist_li comiis_znalist b_t ...），
        // 且帖内多个链接指向同一 tid。旧选择器会把它们当公告，产生重复 key 导致列表崩溃。
        val html = """
            <div class="comiis_forumlist">
              <ul>
                <li class="b_t"><a href="https://bbs.binmt.cc/thread-85763-1-1.html"><em>置顶</em>总版规</a></li>
              </ul>
              <ul>
                <li class="forumlist_li comiis_znalist bg_f b_t b_b">
                  <div class="mmlist_li_box"><h2><a href="https://bbs.binmt.cc/thread-172677-1-1.html">MT管理器2.26.9正式版</a></h2></div>
                  <div class="list_body"><a href="https://bbs.binmt.cc/thread-172677-1-1.html">更新说明</a></div>
                </li>
                <li class="forumlist_li comiis_znalist bg_f b_t b_b">
                  <div class="mmlist_li_box"><h2><a href="https://bbs.binmt.cc/thread-172546-1-1.html">MT管理器公测版</a></h2></div>
                  <div class="list_body"><a href="https://bbs.binmt.cc/thread-172546-1-1.html">更新说明</a></div>
                </li>
              </ul>
            </div>
        """.trimIndent()
        val page = ForumParser.parseForumPage(html, 1)
        assertEquals(1, page.announcements.size)
        assertEquals("85763", page.announcements[0].tid)
        assertEquals(2, page.threads.size)
        assertEquals(2, page.threads.map { it.tid }.distinct().size)
    }

    @Test
    fun parsesThreadPage() {
        val page = ForumParser.parseThreadPage(threadHtml, 1)
        assertEquals("174050", page.tid)
        assertEquals("关于用AI自写网络验证", page.subject)
        assertEquals("b6a540bd", page.formhash)
        assertEquals("42", page.fid)
        assertEquals(1, page.posts.size)
        val post = page.posts[0]
        assertTrue(post.isOp)
        assertEquals("ChenHao", post.authorName)
        assertEquals("来自 广东", post.location)
        assertTrue(post.contentHtml.contains("目前进度百分之30"))
    }
}
