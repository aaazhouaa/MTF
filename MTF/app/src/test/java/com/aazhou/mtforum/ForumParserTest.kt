package com.aazhou.mtforum

import com.aazhou.mtforum.data.parser.ForumParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("来自 广东", op.location)
        assertTrue(op.contentHtml.contains("目前进度百分之30"))
        assertEquals(0, op.floor)

        val first = page.posts[1]
        assertEquals("沙发", first.floorLabel)
        assertEquals(2, first.floor)
        assertEquals("秋枫Mod", first.authorName)
        assertEquals("来自 河南", first.location)
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
        assertEquals("8 分钟前 来自 河南", quote?.time)
        assertEquals("拿走不谢 https://lspilot.fun/get-started/installation", quote?.text)
    }

    @Test
    fun hiddenContentBlockIsNotAQuote() {
        // 「游客，回复可见」是站点用 comiis_quote 排版的提示卡，不是引用回复；
        // 误当成楼中楼会让该回复从评论列表里消失。
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
        assertTrue(post.contentHtml.contains("游客"))
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
        assertEquals("5 秒前", post.quote?.time)
        assertEquals("不是，怎么还有我的事。", post.quote?.text)
        assertNull(post.quote?.pid)
        assertTrue(post.contentHtml.contains("你住论坛了"))
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
        assertEquals("https://cdn.binmt.cc/forum.php?mod=image&aid=377738", thread.thumbnail)
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
        val html = """
            <html><body>
            <script>var discuz_uid = '159705';</script>
            <div class="comiis_space_tx">
              <h2>randian</h2><span class="kmlev">硕士生</span>
              <div class="user_img"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=159705"></div>
            </div>
            <div class="comiis_space_profileico"><ul>
              <li>帖子 <span>42</span></li>
              <li>回复 <span>318</span></li>
              <li>好友 <span>7</span></li>
              <li>关注 <span>12</span></li>
              <li>粉丝 <span>55</span></li>
            </ul></div>
            <div class="comiis_space_profilejf"><ul>
              <li>积分 <span class="f_0">1234</span></li>
              <li>威望 <span class="f_0">10</span></li>
              <li>金币 <span class="f_0">88</span></li>
            </ul></div>
            <div class="comiis_space_profile"><ul><li>在线时间 <span class="profile_rs">156 小时</span></li></ul></div>
            </body></html>
        """.trimIndent()
        val p = ForumParser.parseUserProfile(html)
        assertEquals("159705", p.uid)
        assertEquals("randian", p.name)
        assertEquals("硕士生", p.groupName)
        assertEquals(42, p.threads)
        assertEquals(318, p.posts)
        assertEquals(7, p.friends)
        assertEquals(12, p.following)
        assertEquals(55, p.followers)
        assertEquals(1234, p.credits)
        assertEquals(88, p.gold)
        assertEquals("156 小时", p.onlineTime)
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
}
