package com.aazhou.mtforum

import com.aazhou.mtforum.data.parser.ForumParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 站点标题锚点里的图标字体字符：私用区 U+E64E，无字形不打印，但会占一个字符宽度。 */
private const val ICON_FONT_CHAR = "\uE64E"

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
          <div id="comiis_head"><div class="comiis_head">
            <a href="https://bbs.binmt.cc/forum-42-1.html" class="kmtit">编程交流</a>
          </div></div>
          <script>var fid = parseInt('42'), tid = parseInt('174050');</script>
          <script>var replyreload, tid = '174050', authorid = '156549', formhash = 'b6a540bd';</script>
          <div class="comiis_postlist kqide">
          <div class="comiis_postli comiis_list_readimgs" id="pid11911194">
            <div class="comiis_postli_top comiis_postli_topv1 bg_f">
              <a class="postli_top_tximg bg_e"><img class="top_tximg" src="https://avatar.mt2.cn/a.png"></a>
              <h2><a href="home.php?mod=space&amp;uid=156549" class="top_user f_b">ChenHao</a>
                <a class="top_lev bg_a f_f">Lv.3 初中生</a></h2>
              <div class="comiis_postli_time"><span class="f_d"><span class="kmtime">6 分钟前
                <code class="comiis_iplocality f_d"><font>来自 广东</font></code></span></span></div>
            </div>
            <div class="comiis_message bg_f view_one b_b cl message"><div class="comiis_messages">
              <div class="comiis_a comiis_message_table cl">
                目前进度百分之30<br /><img src="none.gif" file="https://cdn.binmt.cc/data/attachment/forum/x.png" />
              </div>
            </div></div>
          </div>
          <div class="comiis_postli comiis_list_readimgs" id="pid11911239">
            <div class="comiis_postli_top bg_f b_t">
              <a class="postli_top_tximg bg_e"><img class="top_tximg" src="https://avatar.mt2.cn/b.png"></a>
              <h2><span class="f_d y">沙发</span>
                <a href="home.php?mod=space&amp;uid=140217" class="top_user f_b">秋枫Mod</a>
                <a class="top_lev bg_a f_f">Lv.6 硕士生</a></h2>
            </div>
            <div class="comiis_message bg_f view_all cl message"><div class="comiis_messages">
              <div class="comiis_a comiis_message_table cl">
                <div class="comiis_quote bg_h b_dashed f_c"><blockquote><font size="2">回复</font>
                <font color="#999999">ChenHao 发表于 2026-10-2 18:50</font><br />
                <font color="#999999">目前进度百分之30</font></blockquote></div><br />
                谢谢
              </div>
            </div></div>
            <div class="comiis_postli_times bg_f">
              <span class="f_d comiis_tm">3 小时前<code class="comiis_iplocality f_d"><font>来自 河南</font></code></span>
            </div>
          </div>
          <div class="comiis_postli comiis_list_readimgs" id="pid11911300">
            <div class="comiis_postli_top bg_f b_t">
              <a class="postli_top_tximg bg_e"><img class="top_tximg" src="https://avatar.mt2.cn/c.png"></a>
              <h2><span class="f_d y">15<sup>#</sup></span>
                <a class="top_user f_b">G弟弟</a></h2>
            </div>
            <div class="comiis_message bg_f view_all cl message"><div class="comiis_messages">
              <div class="comiis_a comiis_message_table cl">感谢分享</div>
            </div></div>
          </div>
          </div>
          <div class="comiis_pltit bg_f b_t cl"><h2>全部评论<span class="f_d">496</span></h2></div>
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
        assertEquals("编程交流", page.forumName)
        assertEquals("156549", page.opUid)
        assertEquals(496, page.totalReplies)
        assertEquals(3, page.posts.size)

        val op = page.posts[0]
        assertTrue(op.isOp)
        assertTrue(op.isFirstPost)
        assertEquals("ChenHao", op.authorName)
        assertEquals("广东", op.location)
        assertTrue(op.contentHtml.contains("目前进度百分之30"))
        assertEquals(0, op.floor)

        val first = page.posts[1]
        // 楼层显示统一成 «N#»：站点第 1 页给的是「沙发」这类称谓，换算成楼层号
        assertEquals("2#", first.floorLabel)
        assertEquals(2, first.floor)
        assertEquals("秋枫Mod", first.authorName)
        assertEquals("河南", first.location)
        assertTrue(!first.isOp)
        // 引用块应从正文 HTML 中移除（改由 quote 字段渲染），正文只留「谢谢」
        assertEquals("ChenHao", first.quote?.author)
        assertEquals("2026-10-2 18:50", first.quote?.time)
        assertEquals("目前进度百分之30", first.quote?.text)
        assertTrue(!first.contentHtml.contains("comiis_quote"))
        assertTrue(first.contentHtml.contains("谢谢"))

        // 带 sup 的楼号也要归一化成「15#」
        assertEquals("15#", page.posts[2].floorLabel)
        assertEquals(15, page.posts[2].floor)
    }

    @Test
    fun totalPagesIgnoresLinkedOtherThreads() {
        // 正文里引用的其它帖子地址（thread-其它tid-178-1.html、...tid=其它&page=178）
        // 不是本页分页元素；采信它们会把总页数抬成被引帖的页码，
        // 续拉据此一直去敲不存在的页、永不收手。
        val html = """
            <html><head><title>帖子 - MT论坛</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174050-1-1.html"></head>
            <body>
              <div class="comiis_a">参见
                <a href="https://bbs.binmt.cc/thread-999001-178-1.html">另一帖</a>
                <a href="https://bbs.binmt.cc/forum.php?mod=viewthread&tid=999002&page=178">另一帖</a>
              </div>
              <a href="https://bbs.binmt.cc/thread-174050-3-1.html">3</a>
            </body></html>
        """.trimIndent()
        assertEquals(3, ForumParser.parseThreadPage(html, 1).totalPages)
    }

    @Test
    fun totalPagesReadsTidParamForm() {
        // 倒序分页的页码在查询串里（带 tid），不带 tid 的分页链接也不误排
        val html = """
            <html><head><title>帖子</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174050-1-1.html"></head>
            <body>
              <a href="https://bbs.binmt.cc/forum.php?mod=viewthread&tid=174050&page=5&ordertype=1">5</a>
              <a href="thread-174050-2-1.html">2</a>
            </body></html>
        """.trimIndent()
        assertEquals(5, ForumParser.parseThreadPage(html, 1).totalPages)
    }

    @Test
    fun totalPagesCountsThisThreadDespiteLeadingOtherThreadLink() {
        // 真实帖子第 1 页：无 canonical，正文前部有指向其它帖的链接（thread-173642-1-1.html），
        // 真实 tid 只出现在页面 JS 里。老逻辑扫 thread-\d+ 会误取 173642，令指向 173660 的分页
        // 链接全被 parseTotalPages 过滤、总页数算成 1，于是「全部评论 260」只显示第 1 页那
        // 十几条就到底。
        val html = """
            <html><head><title>某帖 - MT论坛</title></head>
            <body>
              <script>var replyreload, tid = '173660', authorid = '123498', formhash = '9f4db49e';</script>
              <div class="comiis_a">相关帖子
                <a href="https://bbs.binmt.cc/thread-173642-1-1.html">另一帖</a>
              </div>
              <div class="comiis_pgt">
                <a href="https://bbs.binmt.cc/thread-173660-2-1.html">2</a>
                <a href="https://bbs.binmt.cc/thread-173660-3-1.html">3</a>
                <a href="https://bbs.binmt.cc/thread-173660-4-1.html">4</a>
                <a href="https://bbs.binmt.cc/thread-173660-21-1.html">21</a>
              </div>
            </body></html>
        """.trimIndent()
        val page = ForumParser.parseThreadPage(html, 1)
        assertEquals("173660", page.tid)
        assertEquals(21, page.totalPages)
    }

    @Test
    fun tidPrefersJsAssignmentOverBodyLink() {
        // 无 canonical、无 fastpostform 时，tid 应取页面 JS 的赋值，而非正文里被引帖的链接
        val html = """
            <html><head><title>某帖 - MT论坛</title></head>
            <body>
              <div class="comiis_a">引用
                <a href="https://bbs.binmt.cc/thread-173642-1-1.html">被引帖</a>
              </div>
              <script>var tid = '173660';</script>
            </body></html>
        """.trimIndent()
        assertEquals("173660", ForumParser.parseThreadPage(html, 1).tid)
    }

    @Test
    fun parsesNoticeAuthor() {
        // 快速回复框的 noticeauthor 隐藏域：AJAX 回复必需，每次进页面都会变
        val html = """
            <html><head><title>某帖 - MT论坛</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174050-1-1.html"></head>
            <body>
              <form id="fastpostform" action="forum.php?mod=post&amp;action=reply&amp;fid=42&amp;tid=174050&amp;replysubmit=yes&amp;mobile=2">
                <input type="hidden" name="formhash" value="b6a540bd" />
                <input type="hidden" name="noticeauthor" value="e3eaJPllHExBDgHFQTeqXXcU6I7Zbh1pqyEHSfjk">
              </form>
            </body></html>
        """.trimIndent()
        assertEquals(
            "e3eaJPllHExBDgHFQTeqXXcU6I7Zbh1pqyEHSfjk",
            ForumParser.parseThreadPage(html, 1).noticeAuthor,
        )
        // 没有 noticeauthor 隐藏域时为 null（未登录/站点未渲染）
        val bare =
            """<html><body><form id="fastpostform" action="forum.php?mod=post&action=reply&fid=42&tid=174050"></form></body></html>"""
        assertNull(ForumParser.parseThreadPage(bare, 1).noticeAuthor)
    }

    @Test
    fun replyResultReadsInajaxXmlContract() {
        // 带 inajax=1 时站点回 XML：CDATA 里是 succeedhandle_（成功）或 errorhandle_（失败）。
        // 老的「无错误词即成功」会把整页 HTML 误判成成功，这里要求按 handle 标记判定、失败摘站点原文。
        val successXml =
            """<root><![CDATA[<script>succeedhandle_fastpost('回复发布成功', {});</script>]]></root>"""
        val ok = ForumParser.parseReplyResult(successXml)
        assertTrue(ok.success)
        assertNull(ok.message)

        val failXml =
            """<root><![CDATA[<div class="comiis_tip"><p>抱歉，您尚未输入标题或内容<script>if(typeof errorhandle_fastpost=='function'){errorhandle_fastpost('抱歉，您尚未输入标题或内容',{});}</script></p></div>]]></root>"""
        val fail = ForumParser.parseReplyResult(failXml)
        assertTrue(!fail.success)
        assertEquals("抱歉，您尚未输入标题或内容", fail.message)

        // 回执是 inajax XML 却没带任何处理脚本：失败一定会带 errorhandle_，故按成功算，
        // 避免把已发出的回复报成失败、诱使用户重发
        val silentXml = """<?xml version="1.0" encoding="utf-8"?><root><![CDATA[]]></root>"""
        assertTrue(ForumParser.parseReplyResult(silentXml).success)

        // 既不是 inajax 回执也不带错误脚本（被重定向到登录页/风控页）：判失败，
        // 并把页面里的提示原文带给上层
        val html = """<html><body><p>抱歉，您尚未登录</p></body></html>"""
        val unknown = ForumParser.parseReplyResult(html)
        assertTrue(!unknown.success)
        assertEquals("抱歉，您尚未登录", unknown.message)
    }

    @Test
    fun quoteWithLocationAndPidLink() {
        val html = """
            <div class="comiis_a comiis_message_table cl">
              <div class="comiis_quote bg_h b_dashed f_c"><blockquote><font size="2">回复</font>
              <font size="2"><a href="forum.php?mod=redirect&amp;goto=findpost&amp;pid=11911193&amp;ptid=174049">
              <font color="#999999">秋枫Mod 发表于 8 分钟前 来自 河南]</font></a></font><br />
              拿走不谢 https://lspilot.fun/get-started/installation</blockquote></div><br />
              这东西，能不能免root
            </div>
        """.trimIndent()
        val page = ForumParser.parseThreadPage(
            """<html><head><title>求工具</title></head><body>
                <div class="comiis_postli" id="pid11911250">$html</div></body></html>""",
            1,
        )
        val quote = page.posts.single().quote
        assertEquals("11911193", quote?.pid)
        assertEquals("秋枫Mod", quote?.author)
        // 「8 分钟前」是引用当时的快照，隔天回看是错的，不采用
        assertNull(quote?.time)
        assertEquals("拿走不谢 https://lspilot.fun/get-started/installation", quote?.text)
    }

    @Test
    fun hiddenContentBlockIsNotAQuote() {
        // 「游客，回复可见」是站点用 comiis_quote 排版的提示卡，不是引用回复；
        // 误当成楼中楼会让该回复从评论列表里消失。
        // 提示本身只是站点排版用的说明，客户端有自己的锁卡，正文里不再保留。
        val html = """
            <html><head><title>帖子</title></head><body>
            <div class="comiis_postli" id="pid1">
              <div class="comiis_postli_top comiis_postli_topv1"></div>
              <div class="comiis_a">
                <div class="comiis_quote bg_h f_c">游客，如果您要查看本帖隐藏内容请<a href="x">回复</a></div>正文内容
              </div>
            </div></body></html>
        """.trimIndent()
        val post = ForumParser.parseThreadPage(html, 1).posts.single()
        assertNull(post.quote)
        assertTrue(post.hasHiddenContent)
        assertTrue(!post.contentHtml.contains("游客"))
        assertTrue(post.contentHtml.contains("正文内容"))
    }

    @Test
    fun hiddenPromptInLockedDivIsDroppedFromBody() {
        // 锁定提示的另一种排版是 div.locked：同样只用来置标志位，正文里不保留
        val html = """
            <html><head><title>帖子</title></head><body>
            <div class="comiis_postli" id="pid1">
              <div class="comiis_postli_top comiis_postli_topv1"></div>
              <div class="comiis_a">
                正文内容
                <div class="locked">username123，如果您要查看本帖隐藏内容请回复</div>
              </div>
            </div></body></html>
        """.trimIndent()
        val post = ForumParser.parseThreadPage(html, 1).posts.single()
        assertTrue(post.hasHiddenContent)
        assertTrue(!post.contentHtml.contains("username123"))
        assertTrue(post.contentHtml.contains("正文内容"))
    }

    @Test
    fun hiddenPromptOnReplyFloorKeepsBodyText() {
        // 非首帖不判定锁定（回复没有「回复可见」一说），提示照旧留在正文里
        val html = """
            <html><head><title>帖子</title></head><body>
            <div class="comiis_postli" id="pid2">
              <div class="comiis_postli_top"></div>
              <div class="comiis_a">
                <div class="locked">username123，如果您要查看本帖隐藏内容请回复</div>谢谢分享
              </div>
            </div></body></html>
        """.trimIndent()
        val post = ForumParser.parseThreadPage(html, 1).posts.single()
        assertTrue(!post.hasHiddenContent)
        assertTrue(post.contentHtml.contains("username123"))
        assertTrue(post.contentHtml.contains("谢谢分享"))
    }

    @Test
    fun plainQuoteWithoutPidStillParses() {
        val html = """
            <html><head><title>帖子</title></head><body>
            <div class="comiis_postli" id="pid11910144">
              <div class="comiis_postli_top b_t"></div>
              <div class="comiis_a">
                <div class="comiis_quote bg_h b_dashed f_c"><blockquote><font size="2">回复</font> <font color="#999999">环流泥猫 发表于 5 秒前</font><br />
                不是，怎么还有我的事。</blockquote></div><br />
                你住论坛了，随处可见都是你<img src="https://cdn.binmt.cc/static/image/smiley/doge/7.png" smilieid="1359" border="0" alt="" />
              </div>
            </div></body></html>
        """.trimIndent()
        val post = ForumParser.parseThreadPage(html, 1).posts.single()
        assertEquals("环流泥猫", post.quote?.author)
        assertEquals("不是，怎么还有我的事。", post.quote?.text)
        assertNull(post.quote?.pid)
        assertTrue(post.contentHtml.contains("你住论坛了"))
    }

    @Test
    fun quoteKeepsAbsoluteDateButDropsRelativeTime() {
        val absolute = ForumParser.parseThreadPage(
            """<html><head><title>帖子</title></head><body>
                <div class="comiis_postli" id="pid1"><div class="comiis_a">
                <div class="comiis_quote"><blockquote><font color="#999999">秋枫Mod 发表于 2026-10-5 21:18 来自 河南]</font><br />
                拿走不谢</blockquote></div></div></div></body></html>""",
            1,
        )
        // 绝对日期是被引楼层的真实时间，保留
        assertEquals("2026-10-5 21:18", absolute.posts.single().quote?.time)
        val relative = ForumParser.parseThreadPage(
            """<html><head><title>帖子</title></head><body>
                <div class="comiis_postli" id="pid1"><div class="comiis_a">
                <div class="comiis_quote"><blockquote><font color="#999999">秋枫Mod 发表于 19 秒前 来自 安徽]</font><br />
                拿走不谢</blockquote></div></div></div></body></html>""",
            1,
        )
        // 相对时间是引用那一刻的快照，隔天回看是错的，丢弃（引用卡不显示时间）
        assertNull(relative.posts.single().quote?.time)
    }

    @Test
    fun cardFieldsParsedFromGuideList() {
        // 真实「导读-最新发表」卡片：版块名在 comiis_xznalist_bk，封面取 comiis_loadimages，
        // 底部三数为 点赞/回复/浏览。
        val html = """
            <div class="comiis_forumlist comiis_xznlist mb0 cl"><ul>
            <li class="forumlist_li comiis_znalist bg_f b_t b_b">
              <div class="forumlist_li_top cl">
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=159705&do=profile" class="wblist_tximg"><img class="top_tximg" src="https://avatar.mt2.cn/avatar.php?uid=159705"></a>
                <h2><a href="https://bbs.binmt.cc/home.php?mod=space&uid=159705&do=profile" class="top_user">randian</a><span class="top_lev bg_a f_f">Lv.2</span></h2>
                <div class="forumlist_li_time"><span class="f_d">4 分钟前</span></div>
              </div>
              <div class="mmlist_li_box cl">
                <h2><a href="https://bbs.binmt.cc/thread-174077-1-1.html">DsPlayer-0.8.5 (道长壳)</a></h2>
                <div class="list_body cl"><a href="https://bbs.binmt.cc/thread-174077-1-1.html" class="f_b">只分享可用精品资源 ...</a></div>
                <div class="comiis_pyqlist_imgs comiis_pyqlist_img3p"><ul><li><img src="none.png" comiis_loadimages="https://cdn.binmt.cc/forum.php?mod=image&aid=377738"></li></ul></div>
              </div>
              <div class="comiis_xznalist_bk cl"><a href="https://bbs.binmt.cc/forum-44-1.html" class="bg_g f_0"><i class="comiis_font">x</i>综合交流</a></div>
              <div class="comiis_xznalist_bottom cl"><ul class="cl">
                <li><a href="javascript:;"><span class="comiis_tm">1</span></a></li>
                <li><a href="https://bbs.binmt.cc/thread-174077-1-1.html"><span class="comiis_tm">6</span></a></li>
                <li><span class="comiis_tm">77</span></li>
              </ul></div>
            </li>
            </ul></div>
        """.trimIndent()
        val thread = ForumParser.parseForumPage(html, 1).threads.single()
        assertEquals("174077", thread.tid)
        assertEquals("randian", thread.authorName)
        assertEquals("综合交流", thread.forumName)
        assertEquals("1", thread.likeCount)
        assertEquals("6", thread.replyCount)
        assertEquals("77", thread.viewCount)
        assertEquals(listOf("https://cdn.binmt.cc/forum.php?mod=image&aid=377738"), thread.imageUrls)
    }

    @Test
    fun portalFeedParsedFromXmlCdata() {
        // 「热门」tab 的列表来自 comiis_app_portal 插件：响应是 XML，帖子列表整段在 CDATA 里。
        // 与 guide 列表的差别有三处：统计容器是 comiis_znalist_bottom、三个数是「291阅读」
        // 这样的裸文本且顺序反转为 阅读/评论/赞、版块名不在 comiis_xznalist_bk 而挂在时间行。
        val fragment = """
            <div class="comiis_forumlist mb0 cl"><ul>
            <li class="forumlist_li comiis_znalist bg_f b_t b_b comiis_list_readimgs">
              <div class="forumlist_li_top cl">
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=108469&do=profile" class="wblist_tximg"><img class="top_tximg" src="https://avatar.mt2.cn/avatar.php?uid=108469"></a>
                <h2><a href="https://bbs.binmt.cc/home.php?mod=space&uid=108469&do=profile" class="top_user">白珩</a><span class="top_lev bg_a f_f">Lv.3</span></h2>
                <div class="forumlist_li_time"><span class="f_d">昨天&nbsp;21:17</span><a href="https://bbs.binmt.cc/forum-42-1.html" class="f_d">&nbsp来自 编程开发</a></div>
              </div>
              <div class="mmlist_li_box cl">
                <h2><a href="https://bbs.binmt.cc/thread-174324-1-1.html">水印工具 2.0</a></h2>
                <div class="list_body cl"><a href="https://bbs.binmt.cc/thread-174324-1-1.html" class="f_b">核心功能特色 ...</a></div>
              </div>
              <div class="comiis_znalist_bottom b_t cl"><ul class="cl">
                <li class="f_c"><i class="comiis_font">i</i>291阅读</li>
                <li class="f_c kmc"><a href="https://bbs.binmt.cc/thread-174324-1-1.html" class="b_l b_r"><i class="comiis_font">i</i>53评论</a></li>
                <li id="comiis_listzhan_174324"><a href="https://bbs.binmt.cc/forum.php?mod=misc&action=recommend&do=add&tid=174324" tid="174324" class="comiis_recommend_addkey f_c"><i class="comiis_font">i</i><span class="num-all_174324">3</span>赞</a></li>
              </ul></div>
            </li>
            </ul></div>
        """.trimIndent()
        val threads = ForumParser.parsePortalFeed("<root><![CDATA[$fragment]]></root>")
        val thread = threads.single()
        assertEquals("174324", thread.tid)
        assertEquals("白珩", thread.authorName)
        assertEquals("Lv.3", thread.level)
        assertEquals("编程开发", thread.forumName)
        assertEquals("291", thread.viewCount)
        assertEquals("53", thread.replyCount)
        assertEquals("3", thread.likeCount)
        assertEquals("核心功能特色 ...", thread.excerpt)
    }

    @Test
    fun portalFeedWithoutCdataYieldsNothing() {
        assertTrue(ForumParser.parsePortalFeed("<root></root>").isEmpty())
    }

    @Test
    fun cardImagesCollectedInOrderAndPlaceholdersDropped() {
        // 多图卡片：站点把每张图放在 comiis_pyqlist_imgs 的一个 li 里，真实地址在
        // comiis_loadimages（src 是懒加载占位 none.png），取图要保序。
        val html = """
            <div class="comiis_forumlist comiis_xznlist mb0 cl"><ul>
            <li class="forumlist_li comiis_znalist bg_f b_t b_b">
              <div class="forumlist_li_top cl">
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=1" class="wblist_tximg"><img class="top_tximg" src="https://avatar.mt2.cn/avatar.php?uid=1"></a>
                <h2><a href="https://bbs.binmt.cc/home.php?mod=space&uid=1" class="top_user">someone</a></h2>
                <div class="forumlist_li_time"><span class="f_d">1 小时前</span></div>
              </div>
              <div class="mmlist_li_box cl">
                <h2><a href="https://bbs.binmt.cc/thread-200001-1-1.html">多图帖</a></h2>
                <div class="comiis_pyqlist_imgs comiis_pyqlist_img3p"><ul>
                  <li><img src="none.png" comiis_loadimages="https://cdn.binmt.cc/forum.php?mod=image&aid=1"></li>
                  <li><img src="none.png" comiis_loadimages="https://cdn.binmt.cc/forum.php?mod=image&aid=2"></li>
                  <li><img src="none.png" comiis_loadimages="https://cdn.binmt.cc/forum.php?mod=image&aid=3"></li>
                </ul></div>
              </div>
              <div class="comiis_xznalist_bottom cl"><ul class="cl">
                <li><span class="comiis_tm">2</span></li>
                <li><span class="comiis_tm">5</span></li>
                <li><span class="comiis_tm">88</span></li>
              </ul></div>
            </li>
            </ul></div>
        """.trimIndent()
        val thread = ForumParser.parseForumPage(html, 1).threads.single()
        assertEquals(
            listOf(
                "https://cdn.binmt.cc/forum.php?mod=image&aid=1",
                "https://cdn.binmt.cc/forum.php?mod=image&aid=2",
                "https://cdn.binmt.cc/forum.php?mod=image&aid=3",
            ),
            thread.imageUrls,
        )
    }

    @Test
    fun cardLazyPlaceholderIsNotTreatedAsImage() {
        // 没有真实附件图时，src 只有 none.png 占位，不能当成配图（否则卡片第一格是站点素材）
        val html = """
            <div class="comiis_forumlist comiis_xznlist mb0 cl"><ul>
            <li class="forumlist_li comiis_znalist bg_f b_t b_b">
              <div class="forumlist_li_top cl">
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=1" class="wblist_tximg"><img class="top_tximg" src="https://avatar.mt2.cn/avatar.php?uid=1"></a>
                <h2><a href="https://bbs.binmt.cc/home.php?mod=space&uid=1" class="top_user">someone</a></h2>
              </div>
              <div class="mmlist_li_box cl">
                <h2><a href="https://bbs.binmt.cc/thread-300001-1-1.html">无图帖</a></h2>
                <div class="comiis_pyqlist_imgs comiis_pyqlist_img3p"><ul>
                  <li><img src="https://bbs.binmt.cc/template/comiis_app/pic/none.png"></li>
                </ul></div>
              </div>
              <div class="comiis_xznalist_bottom cl"><ul class="cl">
                <li><span class="comiis_tm">0</span></li>
                <li><span class="comiis_tm">3</span></li>
                <li><span class="comiis_tm">12</span></li>
              </ul></div>
            </li>
            </ul></div>
        """.trimIndent()
        val thread = ForumParser.parseForumPage(html, 1).threads.single()
        assertTrue(thread.imageUrls.isEmpty())
    }

    @Test
    fun cardTitleDropsInvisibleIconFontChar() {
        // 站点在标题锚点里塞图标字体字符：<span class="bg_a f_f"> 装着私用区 U+E64E。
        // Jsoup 的 text() 会把子元素文本拼进标题，trim() 去不掉；它在 Compose 里占一个
        // 字符宽度，标题就比同一张卡的正文多缩进。详情页标题取 document.title()，不受影响。
        val html = """
            <div class="comiis_forumlist comiis_xznlist mb0 cl"><ul>
            <li class="forumlist_li comiis_znalist bg_f b_t b_b">
              <div class="forumlist_li_top cl">
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=1" class="wblist_tximg"><img class="top_tximg" src="https://avatar.mt2.cn/avatar.php?uid=1"></a>
                <h2><a href="https://bbs.binmt.cc/home.php?mod=space&uid=1" class="top_user">someone</a></h2>
                <div class="forumlist_li_time"><span class="f_d">1 小时前</span></div>
              </div>
              <div class="mmlist_li_box cl">
                <h2><a href="https://bbs.binmt.cc/thread-400001-1-1.html">
                  <span class="bg_a f_f">{{ICON}}</span>
                  MT论坛1.08</a></h2>
                <div class="list_body cl"><a href="https://bbs.binmt.cc/thread-400001-1-1.html" class="f_b">
                  <span class="bg_a f_f">{{ICON}}</span>
                  【新增】留言板功能</a></div>
              </div>
              <div class="comiis_xznalist_bottom cl"><ul class="cl">
                <li><span class="comiis_tm">1</span></li>
                <li><span class="comiis_tm">2</span></li>
                <li><span class="comiis_tm">3</span></li>
              </ul></div>
            </li>
            </ul></div>
        """.trimIndent().replace("{{ICON}}", ICON_FONT_CHAR)
        val thread = ForumParser.parseForumPage(html, 1).threads.single()
        assertEquals("400001", thread.tid)
        assertEquals("MT论坛1.08", thread.subject)
        assertEquals("【新增】留言板功能", thread.excerpt)
    }

    @Test
    fun editedMarkerKeepsOnlyTime() {
        val html = """
            <html><body>
            <div class="comiis_postli" id="pid11910604">
              <div class="comiis_a">帖子正文
                本帖最后由 username123 于 2026-10-5 12:39 编辑
              </div>
            </div></body></html>
        """.trimIndent()
        val post = ForumParser.parseThreadPage(html, 1).posts.single()
        // 编辑标记只保留「时间 编辑」同行小字
        assertEquals("2026-10-5 12:39 编辑", post.editedText)
        assertTrue(post.contentHtml.contains("帖子正文"))
        assertTrue(!post.contentHtml.contains("编辑"))
    }

    @Test
    fun userSpaceStatsParsed() {
        // 结构取自站点真实的个人中心页（home.php?mod=space&uid=X&do=profile&mobile=2）：
        // kmlevs 是等级、kmlev 是用户组；五项统计是 «span: 帖子 94»，积分/金币是
        // «span: 1626»积分，在线时长在第二张 .comiis_space_profile 的 .profile_rs 里
        val html = """
            <html><body>
            <script>var discuz_uid = '156549';</script>
            <div class="comiis_space_tx comiis_space_txv1">
              <div class="comiis_space_flw"><a id="followmod" href="home.php?mod=spacecp&amp;ac=follow&amp;op=add&amp;fuid=156549" class="bg_a f_f dialog"><i class="comiis_font"></i>关注</a></div>
              <div class="user_img"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=156549&amp;size=middle"></div>
              <h2 class="fyy">feelingboy11</h2>
              <p><span class="z f14 fyy">516 人气</span><span class="z comiis_tm kmx fyy">|</span><span class="z f14 fyy">0 关注</span><span class="z comiis_tm kmx fyy">|</span><span class="z f14 fyy">57 粉丝</span></p>
              <p><span class="kmlevs bg_0 kmlv f_f">Lv.4</span><span class="kmlev f_f">高中生</span></p>
            </div>
            <div class="comiis_space_profileico bg_f b_t b_b mt10 cl"><ul>
              <li><a href="home.php?mod=space&amp;uid=156549&amp;do=thread&amp;view=me&amp;type=thread"><i class="comiis_font"></i><span>帖子 94</span></a></li>
              <li><a href="home.php?mod=space&amp;uid=156549&amp;do=thread&amp;view=me&amp;type=reply"><i class="comiis_font"></i><span>回复 165</span></a></li>
              <li><a href="javascript:;"><i class="comiis_font"></i><span>好友 3</span></a></li>
              <li><a href="home.php?mod=follow&amp;do=follower&amp;uid=156549"><i class="comiis_font"></i><span>粉丝 57</span></a></li>
              <li><a href="javascript:;"><i class="comiis_font"></i><span>人气 516</span></a></li>
            </ul></div>
            <div class="comiis_space_profilejf bg_f b_t b_b mt10 cl"><ul>
              <li class="b_t b_r"><span class="f_0">1626</span>积分</li>
              <li class="b_t b_r"><span class="f_0">7 </span>好评</li>
              <li class="b_t b_r"><span class="f_0">479</span>金币</li>
              <li class="b_t b_r"><span class="f_0">100 </span>信誉</li>
            </ul></div>
            <div class="comiis_space_profile bg_f b_t b_b mt10 cl"><ul>
              <li class="b_t"><a href="home.php?mod=spacecp&amp;ac=usergroup" class="profile_a"><div class="profile_r"><span class="kmlev bg_0 f_f f12">Lv.4</span><span class="f14">高中生</span></div><span>用户组</span></a></li>
              <li class="b_t"><a href="home.php?mod=spacecp&amp;ac=profile&amp;op=info" class="profile_a"><div class="profile_rs">添加签名</div><span>个人签名</span></a></li>
            </ul></div>
            <div class="comiis_space_profile bg_f b_t b_b mt10 cl"><ul>
              <li class="b_t"><div class="profile_rs f_c">156549</div><span>用户ID</span></li>
              <li class="b_t"><div class="profile_rs f_c">广东省</div><span>出生地</span></li>
              <li class="b_t"><div class="profile_rs f_c">207 小时</div><span>在线时间</span></li>
              <li class="b_t"><div class="profile_rs f_c">2026-5-4 23:04</div><span>注册时间</span></li>
              <li class="b_t"><div class="profile_rs f_c">2026-10-8 00:40</div><span>最后访问</span></li>
            </ul></div>
            </body></html>
        """.trimIndent()
        val p = ForumParser.parseUserProfile(html)
        assertEquals("156549", p.uid)
        assertEquals("feelingboy11", p.name)
        assertEquals("https://avatar.mt2.cn/uc_server/avatar.php?uid=156549&size=middle", p.avatarUrl)
        assertEquals("Lv.4", p.level)
        assertEquals("高中生", p.groupName)
        assertEquals(94, p.threads)
        assertEquals(165, p.posts)
        assertEquals(3, p.friends)
        assertEquals(0, p.following)
        assertEquals(57, p.followers)
        assertEquals(1626, p.credits)
        assertEquals(479, p.gold)
        assertEquals("207 小时", p.onlineTime)
    }

    @Test
    fun userNameDropsLevelGluedIntoHeading() {
        // 个别模板把等级头衔嵌在 h2 里，整取 text 会粘成「昵称Lv.4」
        val html = """
            <html><body>
            <script>var discuz_uid = '159705';</script>
            <div class="comiis_space_tx">
              <div class="user_img"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=159705&size=middle"></div>
              <h2>randian<span class="kmlevs">Lv.4</span></h2>
            </div>
            </body></html>
        """.trimIndent()
        val p = ForumParser.parseUserProfile(html)
        assertEquals("randian", p.name)
        assertEquals("Lv.4", p.level)
    }

    @Test
    fun likeAndFavoriteCountsParsed() {
        // 移动版详情页底部互动行带真实计数：点赞 em.comiis_recommend_num、收藏 span.comiis_favorite_a_num
        val html = """
            <html><head><title>测试帖 - MT论坛</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174032-1-1.html"></head>
            <body>
              <div class="comiis_postli comiis_postli_topv1" id="pid11910108">
                <div class="comiis_a">正文</div>
              </div>
              <div class="comiis_recommend_list_a">
                <a href="javascript:;"><i class="comiis_font">x</i> <em class="comiis_recommend_num">3</em>赞</a>
              </div>
              <li><a href="javascript:;" id="comiis_favorite_a">
                <span class="bg_del f_f comiis_kmvnum comiis_favorite_a_num">4</span></a></li>
            </body></html>
        """.trimIndent()
        val page = ForumParser.parseThreadPage(html, 1)
        assertEquals(3, page.likeCount)
        assertEquals(4, page.favoriteCount)
    }

    @Test
    fun attachmentImagesOutsideBodyAreMerged() {
        // 站点把附件图从正文里抽出，放进与 comiis_a 平级的 ul.comiis_img_list（单张为 comiis_img_one），
        // 只取 comiis_a 会丢图。首帖(view_one)、评论(view_all)与楼中楼子回复是同一套楼层结构，
        // 都经 parsePost，三处都必须取到图。
        val html = """
            <html><head><title>图帖 - MT论坛</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174088-1-1.html"></head>
            <body><div class="comiis_postlist">
            <div class="comiis_postli" id="pid1">
              <div class="comiis_postli_top comiis_postli_topv1"></div>
              <div class="comiis_message view_one"><div class="comiis_messages comiis_aimg_show cl">
                <div class="comiis_a comiis_message_table cl">首帖正文</div>
                <ul class="comiis_img_list cl">
                  <li><span><img src="https://oos.binmt.cc/a.jpg" comiis_loadimages="https://oos.binmt.cc/a.jpg"></span></li>
                  <li><span><img src="https://oos.binmt.cc/b.jpg" comiis_loadimages="https://oos.binmt.cc/b.jpg"></span></li>
                </ul>
              </div></div>
            </div>
            <div class="comiis_postli" id="pid2">
              <div class="comiis_postli_top"></div>
              <div class="comiis_message view_all"><div class="comiis_messages cl">
                <div class="comiis_a comiis_message_table cl">楼层正文</div>
                <div class="comiis_img_one"><img src="https://oos.binmt.cc/c.jpg" comiis_loadimages="https://oos.binmt.cc/c.jpg"></div>
              </div></div>
            </div>
            </div></body></html>
        """.trimIndent()
        val page = ForumParser.parseThreadPage(html, 1)
        val op = page.posts[0]
        assertTrue(op.contentHtml.contains("首帖正文"))
        assertTrue(op.contentHtml.contains("a.jpg"))
        assertTrue(op.contentHtml.contains("b.jpg"))
        // 图文相对顺序保持：正文在前、附件图列表在后
        assertTrue(op.contentHtml.indexOf("首帖正文") < op.contentHtml.indexOf("a.jpg"))
        val reply = page.posts[1]
        assertTrue(reply.contentHtml.contains("楼层正文"))
        assertTrue(reply.contentHtml.contains("c.jpg"))
    }

    @Test
    fun parsesFollowStateFromThreadPage() {
        // 站点用关注控件的 op 参数区分关注态；退出重进要靠它恢复「已关注」
        fun threadWithFollowLink(href: String, text: String) = """
            <html><head><title>关注态 - MT论坛</title>
            <link rel="canonical" href="https://bbs.binmt.cc/thread-174050-1-1.html"></head>
            <body>
              <script>var replyreload, tid = '174050', authorid = '156549', formhash = 'b6a540bd';</script>
              <div class="comiis_postlist">
                <div class="comiis_postli" id="pid1">
                  <div class="comiis_postli_top comiis_postli_topv1"></div>
                  <div class="comiis_message view_one"><div class="comiis_a">正文</div></div>
                </div>
              </div>
              <a href="$href" class="follow_btn">$text</a>
            </body></html>
        """.trimIndent()

        val notFollowing = ForumParser.parseThreadPage(
            threadWithFollowLink("home.php?mod=spacecp&ac=follow&op=add&fuid=156549", "关注"),
            1,
        )
        assertTrue(notFollowing.followingStateKnown)
        assertTrue(!notFollowing.isFollowingOp)

        val following = ForumParser.parseThreadPage(
            threadWithFollowLink("home.php?mod=spacecp&ac=follow&op=del&fuid=156549", "已关注"),
            1,
        )
        assertTrue(following.followingStateKnown)
        assertTrue(following.isFollowingOp)

        // 没有关注控件（未登录/站点改版）时状态未知，界面应回落到本地记录
        val unknown = ForumParser.parseThreadPage(threadHtml, 1)
        assertTrue(!unknown.followingStateKnown)
    }

    @Test
    fun noticesParsedFromNoticesBox() {
        // 结构取自站点真实的提醒页（do=notice&view=mypost）：容器 .comiis_notices_box，
        // 正文在 .ntc_body，时间在 h2，主题链接是 redirect&goto=findpost&ptid=TID
        val html = """
            <html><body>
            <a href="member.php?mod=logging&amp;action=logout&amp;formhash=abc">退出</a>
            <div class="comiis_notices_box">
              <div class="comiis_notice_list b_t mt12">
                <ul>
                  <li class="b_b bg_f cl">
                    <a href="https://bbs.binmt.cc/home.php?mod=space&amp;uid=151212&amp;do=profile" class="notice_img"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=151212&amp;size=middle"></a>
                    <div class="ntc_body"><a href="https://bbs.binmt.cc/space-uid-151212.html">rsuyeug</a> 回复了您的帖子
                      <a href="https://bbs.binmt.cc/forum.php?mod=redirect&amp;goto=findpost&amp;ptid=174032&amp;pid=11921862">(停更)MT论坛app(液态玻璃)</a> &nbsp;
                      <a href="https://bbs.binmt.cc/forum.php?mod=redirect&amp;goto=findpost&amp;pid=11921862&amp;ptid=174032" class="lit">查看</a></div>
                    <h2 class="f_d"><a class="dialog y" href="home.php?mod=spacecp&amp;ac=common&amp;op=ignore">忽略</a> 昨天 15:22</h2>
                  </li>
                  <li class="b_b bg_f cl">
                    <a href="https://bbs.binmt.cc/home.php?mod=space&amp;uid=151213&amp;do=profile" class="notice_img"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=151213&amp;size=middle"></a>
                    <div class="ntc_body"><a href="https://bbs.binmt.cc/space-uid-151213.html">Kdian</a> 回复了您的帖子
                      <a href="https://bbs.binmt.cc/forum.php?mod=redirect&amp;goto=findpost&amp;ptid=174032&amp;pid=11921863">(停更)MT论坛app(液态玻璃)</a></div>
                    <h2 class="f_d">昨天 14:46</h2>
                  </li>
                </ul>
              </div>
            </div>
            </body></html>
        """.trimIndent()
        val notices = ForumParser.parseNotices(html)
        assertTrue(!notices.loggedOut)
        assertEquals(2, notices.notices.size)
        val first = notices.notices[0]
        assertEquals("174032", first.tid)
        assertEquals("11921862", first.pid)
        assertEquals("昨天 15:22", first.timeText)
        // 帖子标题超过 8 字截断成省略号，正文与「查看」都不受污染
        assertTrue(first.text.contains("rsuyeug 回复了您的帖子"))
        assertTrue(first.text.contains("(停更)MT论坛…"))
        assertTrue(!first.text.contains("app"))
        assertTrue(!first.text.contains("查看"))
        assertTrue(!first.text.contains("忽略"))
        // 互动类提醒带头像链接：a.notice_img 的 img.src 是头像，href 里带 uid（点头像进个人主页）
        assertEquals("https://avatar.mt2.cn/uc_server/avatar.php?uid=151212&size=middle", first.avatarUrl)
        assertEquals("151212", first.uid)
    }

    @Test
    fun noticeTitleKeptWhenShort() {
        // 标题不超过 8 字时原样保留
        val html = """
            <html><body>
            <div class="comiis_notices_box"><div class="comiis_notice_list"><ul>
              <li class="b_b bg_f cl">
                <div class="ntc_body"><a href="space-uid-1.html">someone</a> 回复了您的帖子
                  <a href="forum.php?mod=redirect&amp;goto=findpost&amp;ptid=1&amp;pid=2">短标题</a></div>
                <h2 class="f_d">今天 10:00</h2>
              </li>
            </ul></div></div>
            </body></html>
        """.trimIndent()
        val notice = ForumParser.parseNotices(html).notices.single()
        assertTrue(notice.text.contains("短标题"))
        assertTrue(!notice.text.contains("…"))
        // 没有 a_note_id 时退回 pid，保证 key 仍稳定
        assertEquals("2", notice.id)
    }

    @Test
    fun noticesCategoryMenuIsNotTreatedAsNotice() {
        // 默认页（不带 view）只有类别菜单，没有任何提醒内容
        val html = """
            <html><body>
            <div class="comiis_userlist bg_f cl"><ul>
              <li class="b_b"><a href="home.php?mod=space&amp;do=pm" class="kmdbt">我的消息</a></li>
              <li class="b_b mypost"><a href="home.php?mod=space&amp;do=notice&amp;view=mypost" class="kmdbt">我的帖子</a></li>
            </ul></div>
            </body></html>
        """.trimIndent()
        val notices = ForumParser.parseNotices(html)
        assertTrue(!notices.loggedOut)
        assertTrue(notices.notices.isEmpty())
    }

    @Test
    fun threadPageParsedFromResolvedUrl() {
        // redirect 中转地址 302 到带页码的帖子页，页码从这里取
        assertEquals(
            19,
            ForumParser.pageFromThreadUrl(
                "https://bbs.binmt.cc/forum.php?mod=viewthread&tid=174032&page=19&mobile=2#pid11921862",
            ),
        )
        assertEquals(3, ForumParser.pageFromThreadUrl("https://bbs.binmt.cc/thread-174032-3-1.html"))
        // 没有页码按第 1 页处理（调用方据此跳过额外的取页请求）
        assertEquals(1, ForumParser.pageFromThreadUrl("https://bbs.binmt.cc/forum.php?mod=viewthread&tid=174032"))
    }

    @Test
    fun userProfileSplitsNameFromLevelAndReadsNumbersByLabel() {
        // 等级头衔是 h2 内的子元素，整取 text 会粘在昵称后；标签与数值分列两个节点
        val html = """
            <html><body>
              <script>var discuz_uid = '156549';</script>
              <div class="comiis_space_tx">
                <div class="user_img"><img src="https://avatar.mt2.cn/a.png"></div>
                <h2>ChenHao<span class="kmlevs">Lv.3</span><span class="kmlev">初中生</span></h2>
              </div>
              <div class="comiis_space_profileico">
                <ul>
                  <li><span>帖子</span><span>12</span></li>
                  <li><span>回复</span><span>340</span></li>
                  <li><span>好友</span><span>5</span></li>
                  <li><span>关注</span><span>7</span></li>
                  <li><span>粉丝</span><span>9</span></li>
                </ul>
              </div>
              <div class="comiis_space_profilejf"><ul>
                <li><span>积分</span><span>1024</span></li>
                <li><span>威望</span><span>3</span></li>
                <li><span>金币</span><span>88</span></li>
              </ul></div>
              <div class="comiis_space_profile"><ul>
                <li>在线时间<span class="profile_rs">128 小时</span></li>
              </ul></div>
            </body></html>
        """.trimIndent()
        val profile = ForumParser.parseUserProfile(html)
        assertTrue(profile.isLoggedIn)
        assertEquals("ChenHao", profile.name)
        assertEquals("Lv.3", profile.level)
        assertEquals("初中生", profile.groupName)
        assertEquals(12, profile.threads)
        assertEquals(340, profile.posts)
        assertEquals(5, profile.friends)
        assertEquals(7, profile.following)
        assertEquals(9, profile.followers)
        assertEquals(1024, profile.credits)
        assertEquals(88, profile.gold)
        assertEquals("128 小时", profile.onlineTime)
    }

    @Test
    fun `reply quote notice wraps author time and snippet in quote bbcode`() {
        val html = """
            <div class="comiis_a">
              <p>第一行正文 &amp; 符号</p>
              <p>第二行 &lt;b&gt;粗&lt;/b&gt; [#呵呵]</p>
            </div>
        """.trimIndent()
        val notice = ForumParser.replyQuoteNotice("勇敢的心", "2026-10-9 23:16", html, "11935213", "174328")
        assertEquals(
            "[quote][size=2][url=forum.php?mod=redirect&goto=findpost&pid=11935213&ptid=174328]" +
                "[color=#999999]勇敢的心 发表于 2026-10-9 23:16[/color][/url][/size]\n" +
                "第一行正文 & 符号\n第二行 <b>粗</b>[/quote]",
            notice,
        )
    }

    @Test
    fun `reply quote notice keeps raw entities for server side escaping`() {
        val html = "<div class=\"comiis_a\"><p>a &amp; b &lt;tag&gt;</p></div>"
        val notice = ForumParser.replyQuoteNotice("甲&乙", null, html, "1", "2")
        // 服务端会对整块做一次 dhtmlspecialchars，客户端再转义就成了双转义
        assertEquals(
            "[quote][size=2][url=forum.php?mod=redirect&goto=findpost&pid=1&ptid=2]" +
                "[color=#999999]甲&乙 发表于[/color][/url][/size]\na & b <tag>[/quote]",
            notice,
        )
    }

    @Test
    fun `reply quote snippet keeps block breaks and truncates to site length`() {
        val long = "字".repeat(120)
        val html = "<div class=\"comiis_a\"><p>$long</p><p>尾部</p></div>"
        val snippet = ForumParser.replyQuoteSnippet(html)
        assertEquals("字".repeat(100) + " ...", snippet)
    }

    @Test
    fun `reply quote snippet keeps only three lines like the site does`() {
        val html = (1..5).joinToString("") { "<p>第$it 行</p>" }
        assertEquals("第1 行\n第2 行\n第3 行", ForumParser.replyQuoteSnippet(html))
    }

    @Test
    fun `reply quote snippet drops smiley codes and collapses blank blocks`() {
        val html = """
            <div class="comiis_a">
              <p>&nbsp;</p>
              <p>看看 [#哈哈]</p>
              <p><img src="https://bbs.binmt.cc/x.png"></p>
            </div>
        """.trimIndent()
        assertEquals("看看", ForumParser.replyQuoteSnippet(html))
    }

    @Test
    fun `at users are parsed from the getatuser xml cdata`() {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<root><![CDATA[user1, user2,user3]]></root>"
        assertEquals(listOf("user1", "user2", "user3"), ForumParser.parseAtUsers(body))
    }

    @Test
    fun `at users are empty when the account has no friend`() {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<root><![CDATA[]]></root>"
        assertEquals(emptyList<String>(), ForumParser.parseAtUsers(body))
    }

    @Test
    fun `at users ignore a challenge page response`() {
        val body = "<html><script>var x = 1</script></html>"
        assertEquals(emptyList<String>(), ForumParser.parseAtUsers(body))
    }
}
