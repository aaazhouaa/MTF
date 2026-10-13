package com.aazhou.mtforum

import com.aazhou.mtforum.ui.common.isWaterReply
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterReplyTest {

    @Test
    fun keywordIsMatchedByContainmentOnlyForShortReplies() {
        // 关键词按「包含」匹配，且仅对去空白后 5 字以内的短回复生效
        assertTrue(isWaterReply("感谢分享"))
        assertTrue(isWaterReply("看看隐藏"))
        assertTrue(isWaterReply("支持一下"))
    }

    @Test
    fun longReplyIsNotWaterEvenWithKeyword() {
        // 超过 5 字的回复不再因关键词折叠：长回复里的「感谢/分享」多半是正常讨论
        assertFalse(isWaterReply("感谢分享，正好需要"))
        assertFalse(isWaterReply("我看看隐藏内容是什么"))
        assertFalse(isWaterReply("这个必须支持一下"))
    }

    @Test
    fun phraseIsMatchedExactly() {
        // 灌水短语按「完全」匹配：多一个字就不算
        assertTrue(isWaterReply("大佬牛逼"))
        assertTrue(isWaterReply("论坛有你更精彩"))
        assertFalse(isWaterReply("大佬牛逼啊兄弟，这个思路确实厉害"))
    }

    @Test
    fun shortOrEmojiOnlyReplyIsWater() {
        assertTrue(isWaterReply("好的"))
        assertTrue(isWaterReply("收到"))
        assertTrue(isWaterReply("<img src=\"https://cdn.binmt.cc/static/image/smiley/doge/7.png\" smilieid=\"1359\" />"))
        assertTrue(isWaterReply("。。。"))
    }

    @Test
    fun digitsOnlyReplyIsWater() {
        assertTrue(isWaterReply("1111"))
        assertTrue(isWaterReply("123456"))
        assertTrue(isWaterReply("654321"))
        assertTrue(isWaterReply("999"))
    }

    @Test
    fun normalReplyIsNotWater() {
        assertFalse(isWaterReply("由于降低了请求频率，所以回复私信刷新加载等功能会稍微慢一点"))
        assertFalse(isWaterReply("这个工具我用过，界面做得挺不错的，推荐"))
        assertFalse(isWaterReply(""))
    }
}
