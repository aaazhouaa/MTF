package com.aazhou.mtforum.ui.screen.chat

import com.aazhou.mtforum.data.model.ChatConversation
import com.aazhou.mtforum.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话快照与本地消息的合并语义。
 *
 * 站点不给消息 id、时间只有「上午 06:45:45」这种文本，同一条消息只能靠正文身份对上；
 * 站点对刚发出的这条又有同步延迟。这组用例锁死「不重复、不丢消息」两条不变式。
 */
class ChatMergeTest {

    private fun remote(vararg messages: ChatMessage) =
        ChatConversation(touid = "155639", messages = messages.toList())

    private fun self(text: String, html: String? = null, sending: Boolean = false) =
        ChatMessage(text = text, contentHtml = html, isSelf = true, sending = sending)

    private fun friend(text: String) = ChatMessage(text = text, isSelf = false)

    @Test
    fun keepsLocalMessageMissingFromSnapshot() {
        // 站点对刚发出的这条有同步延迟：快照里还没有，本地必须保留，否则表现为「发完不显示」
        val snapshot = remote(friend("在吗"), self("你好"))
        val local = snapshot.messages + self("刚发的这条")
        val merged = mergeChatMessages(snapshot, local)

        assertEquals(3, merged.messages.size)
        assertEquals("刚发的这条", merged.messages.last().text)
        assertTrue(!merged.messages.last().sending)
    }

    @Test
    fun doesNotDuplicateMessageAlreadyInSnapshot() {
        // 快照已收录同一条且本地也有一份：不能多出一个气泡
        val snapshot = remote(friend("在吗"), self("你好"), self("消息提醒有点问题，暂时不改了"))
        val merged = mergeChatMessages(snapshot, snapshot.messages)

        assertEquals(3, merged.messages.size)
        assertEquals(1, merged.messages.count { it.text == "消息提醒有点问题，暂时不改了" })
    }

    @Test
    fun keepsFriendNewMessages() {
        val snapshot = remote(friend("在吗"), self("你好"), friend("在的"))
        val local = listOf(friend("在吗"), self("你好"))
        val merged = mergeChatMessages(snapshot, local)

        assertEquals(2, merged.messages.count { !it.isSelf })
        assertEquals("在的", merged.messages.last().text)
    }

    @Test
    fun matchesImageMessageByUrlAcrossLocalAndSiteForms() {
        // 本地是 BBCode 原文，站点是渲染后的 <img>，两者必须判为同一条
        val url = "https://cdn.binmt.cc/data/attachment/forum/202601/a.jpg"
        val localMsg = self(
            text = "[img]$url[/img] 测试",
            html = "<img src=\"$url\" /> 测试",
            sending = true,
        )
        val siteMsg = self(text = "测试", html = "<img src=\"$url\" border=\"0\" alt=\"\" /> 测试")
        val merged = mergeChatMessages(remote(siteMsg), listOf(localMsg))

        assertEquals(1, merged.messages.size)
    }

    @Test
    fun ignoresSmileyImagesWhenMatching() {
        // 表情图地址带 static/image/smiley，不是消息正文里的贴图，不能参与身份比对
        val localMsg = self(text = "[#呵呵]", html = "<img src=\"https://bbs.binmt.cc/static/image/smiley/tb_1.png\" />")
        val siteMsg = self(
            text = "",
            html = "<img src=\"https://bbs.binmt.cc/static/image/smiley/tb_1.png\" />",
        )
        val merged = mergeChatMessages(remote(siteMsg), listOf(localMsg))

        assertEquals(1, merged.messages.size)
    }

    @Test
    fun keepsSecondIdenticalMessageOnceSnapshotHasOne() {
        // 连发两条相同内容、站点只收录了第一条：本地剩下那条仍要保留，不能被多重集消耗吞掉
        val snapshot = remote(friend("在吗"), self("哈哈"))
        val local = snapshot.messages + self("哈哈")
        val merged = mergeChatMessages(snapshot, local)

        assertEquals(2, merged.messages.count { it.text == "哈哈" })
    }

    @Test
    fun snapshotOnlyWhenNothingLocalMatches() {
        // 站点改了正文渲染等，本地一条都对不上：只补本地待确认的，不把整段本地缓存复活
        val snapshot = remote(friend("在吗"), self("你好"))
        val local = listOf(self("改版前的一条"), self("另一条"))
        val merged = mergeChatMessages(snapshot, local)

        assertEquals(2, merged.messages.size)
        assertEquals("你好", merged.messages.last().text)
    }

    @Test
    fun pendingKeptWhenNothingMatches() {
        // 站点改了正文渲染等，本地一条都对不上：只补本地待确认（sending）的
        val snapshot = remote(friend("在吗"), self("你好"))
        val local = listOf(self("发送中的这条", sending = true))
        val merged = mergeChatMessages(snapshot, local)

        assertEquals(3, merged.messages.size)
        assertEquals("发送中的这条", merged.messages.last().text)
    }

    @Test
    fun emptySiteSelfKeepsLocalSelfMessage() {
        // 新会话刚发出一条：站点快照还没收录、也没有任何自己的消息作锚点，
        // 本地那条（已非 sending）必须保留，否则表现为「发完不显示、重进才回来」
        val snapshot = remote(friend("在吗"))
        val local = snapshot.messages + self("你下载的是最新的吗")
        val merged = mergeChatMessages(snapshot, local)

        assertEquals(2, merged.messages.size)
        assertEquals("你下载的是最新的吗", merged.messages.last().text)
    }

    @Test
    fun noLocalSelfMessagesReturnsSnapshotAsIs() {
        val snapshot = remote(friend("在吗"), friend("在的"))
        val merged = mergeChatMessages(snapshot, listOf(friend("在吗")))

        assertEquals(2, merged.messages.size)
    }
}
