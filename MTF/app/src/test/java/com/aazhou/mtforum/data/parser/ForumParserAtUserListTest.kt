package com.aazhou.mtforum.data.parser

import com.aazhou.mtforum.data.model.AtUser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「@朋友」用户列表解析：样例取自站点真实页面（好友页/关注页/全局用户搜索页）。
 *
 * 好友行的操作区里有 `touid=`（发消息）和 `uid=`（备注）两个链接，按 uid 或 mod=space
 * 匹配都会先撞上它们；昵称只能认 p.tit 里指向 do=profile 的链接。
 */
class ForumParserAtUserListTest {

    @Test
    fun friendList_ignoresActionLinksAndReadsNickname() {
        val users = ForumParser.parseAtUserList(
            """
            <div class="comiis_friend_boxs">
              <div class="mt10 bg_f b_b cl">
                <div class="comiis_userlist01 cl">
                  <li class="b_t">
                    <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=friend&op=ignore&uid=102514&handlekey=delfriendhk" id="a_ignore_102514" class="dialog kmdel f_g comiis_font">x</a>
                    <p class="ytit f_d">
                      <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=friend&op=changegroup&uid=102514&handlekey=editgrouphk" id="friend_group_102514" class="dialog">分组</a>
                      <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=follow&op=add&fuid=102514&hash=1011f6f2&from=a_followmod_102514&handlekey=followmod" uid="102514" id="a_followmod_102514" class="dialog">关注</a>
                      <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=poke&op=send&uid=102514" id="a_poke_102514">打招呼</a>
                      <a href="https://bbs.binmt.cc/home.php?mod=space&do=pm&subop=view&touid=102514" id="a_sendpm_102514">发消息</a>
                    </p>
                    <a href="https://bbs.binmt.cc/home.php?mod=space&uid=102514&do=profile" class="list01_limg bg_e"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=102514&size=middle" /></a>
                    <p class="tit">
                      <a href="https://bbs.binmt.cc/home.php?mod=space&uid=102514&do=profile">choukongbai</a>
                      <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=friend&op=editnote&uid=102514&handlekey=editnote" id="friend_editnote" class="dialog">备注</a>
                    </p>
                    <p class="txt">
                      <font class="f_d" style="margin-right:3px;">小学生</font>
                      <font class="f_d"> 积分:184</font>
                    </p>
                  </li>
                  <li class="b_t">
                    <a href="https://bbs.binmt.cc/home.php?mod=space&uid=102515&do=profile" class="list01_limg bg_e"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=102515&size=middle" /></a>
                    <p class="tit"><a href="https://bbs.binmt.cc/home.php?mod=space&uid=102515&do=profile">xiaoyu</a></p>
                    <p class="txt"><font class="f_d" style="margin-right:3px;">初中生</font><font class="f_d"> 积分:90</font></p>
                  </li>
                </div>
              </div>
            </div>
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                AtUser(
                    uid = "102514",
                    name = "choukongbai",
                    avatarUrl = "https://avatar.mt2.cn/uc_server/avatar.php?uid=102514&size=middle",
                    subtitle = "小学生 积分:184",
                ),
                AtUser(
                    uid = "102515",
                    name = "xiaoyu",
                    avatarUrl = "https://avatar.mt2.cn/uc_server/avatar.php?uid=102515&size=middle",
                    subtitle = "初中生 积分:90",
                ),
            ),
            users,
        )
    }

    @Test
    fun followList_readsFollowerCounts() {
        val users = ForumParser.parseAtUserList(
            """
            <div class="comiis_userlist01 cl">
              <li class="b_t">
                <p class="ytit f_d">
                  <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=follow&op=add&hash=1011f6f2&fuid=499" id="a_followmod_499" uid="499" class="user_gz">关注</a>
                </p>
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=499&do=profile" class="list01_limg bg_e"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=499&size=middle" /></a>
                <p class="tit">
                  <a href="https://bbs.binmt.cc/home.php?mod=space&uid=499&do=profile">赤〆夜</a>
                  <font class="kmtit f_0" id="followbkame_499"></font>
                </p>
                <p class="txt">
                  <a href="https://bbs.binmt.cc/home.php?mod=follow&do=follower&uid=499"><font class="f_d"><code class="f_a" id="followernum_499">1438</code> 粉丝</font></a>
                  <a href="https://bbs.binmt.cc/home.php?mod=follow&do=following&uid=499"><font class="f_d" style="margin:0 3px;"><code class="f_a">0</code> 关注</font></a>
                  <font id="comiis_friendtip_499" class="f_d"> </font>
                </p>
              </li>
            </div>
            """.trimIndent(),
        )
        assertEquals(1, users.size)
        assertEquals(AtUser("499", "赤〆夜", "https://avatar.mt2.cn/uc_server/avatar.php?uid=499&size=middle", "1438 粉丝 0 关注"), users[0])
    }

    @Test
    fun searchResultList_readsLevelAndCredits() {
        val users = ForumParser.parseAtUserList(
            """
            <div class="comiis_userlist01 cl">
              <li class="b_t">
                <p class="ytit f_d">
                  <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=friend&op=add&uid=1" id="a_friend_1" class="dialog">加好友</a>
                  <a href="https://bbs.binmt.cc/home.php?mod=spacecp&ac=follow&op=add&hash=1011f6f2&fuid=1" id="a_followmod_1" class="dialog">关注</a>
                  <a href="https://bbs.binmt.cc/home.php?mod=space&do=pm&subop=view&touid=1" id="a_sendpm_1">发消息</a>
                </p>
                <a href="https://bbs.binmt.cc/home.php?mod=space&uid=1&do=profile" class="list01_limg bg_e"><img src="https://avatar.mt2.cn/uc_server/avatar.php?uid=1&size=middle" /></a>
                <p class="tit"><a href="https://bbs.binmt.cc/home.php?mod=space&uid=1&do=profile">admin</a></p>
                <p class="txt f_d">
                  <font style="margin-right:3px;">硕士生</font>
                  积分: 5313
                </p>
              </li>
            </div>
            """.trimIndent(),
        )
        assertEquals(listOf(AtUser("1", "admin", "https://avatar.mt2.cn/uc_server/avatar.php?uid=1&size=middle", "硕士生 积分: 5313")), users)
    }

    @Test
    fun emptyPage_returnsEmptyList() {
        assertEquals(emptyList<AtUser>(), ForumParser.parseAtUserList("<html><body>暂无数据</body></html>"))
    }
}
