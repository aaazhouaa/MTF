package com.aazhou.mtforum

import com.aazhou.mtforum.data.model.NoticeCategory
import com.aazhou.mtforum.ui.screen.notice.newestNoticeCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** 消息页默认分类：五类里最新一条消息落在哪类就选哪类。 */
class NoticeCategorySelectionTest {

    @Test
    fun newestCategoryWins() {
        assertEquals(
            NoticeCategory.PRIVATE_PM,
            newestNoticeCategory(
                mapOf(
                    NoticeCategory.MY_POST to 1_000L,
                    NoticeCategory.PRIVATE_PM to 2_000L,
                    NoticeCategory.SYSTEM to 500L,
                ),
                NoticeCategory.MY_POST,
            ),
        )
    }

    @Test
    fun unparsableTimeDoesNotWin() {
        // 时间解析不出来的分类（Long.MIN_VALUE）不参与比较，哪怕它排在后头
        assertEquals(
            NoticeCategory.MY_POST,
            newestNoticeCategory(
                mapOf(
                    NoticeCategory.MY_POST to 1_000L,
                    NoticeCategory.PRIVATE_PM to Long.MIN_VALUE,
                ),
                NoticeCategory.MY_POST,
            ),
        )
    }

    @Test
    fun allUnparsableFallsBack() {
        assertEquals(
            NoticeCategory.MY_POST,
            newestNoticeCategory(
                mapOf(
                    NoticeCategory.PRIVATE_PM to Long.MIN_VALUE,
                    NoticeCategory.ANNOUNCE_PM to Long.MIN_VALUE,
                ),
                NoticeCategory.MY_POST,
            ),
        )
    }

    @Test
    fun nothingFetchedFallsBack() {
        assertEquals(
            NoticeCategory.MY_POST,
            newestNoticeCategory(emptyMap(), NoticeCategory.MY_POST),
        )
    }

    @Test
    fun tieKeepsDeclarationOrder() {
        // 时间戳相同取先出现的一方：调用方按分类声明序插入，结果稳定可预期
        assertEquals(
            NoticeCategory.PRIVATE_PM,
            newestNoticeCategory(
                mapOf(
                    NoticeCategory.PRIVATE_PM to 1_000L,
                    NoticeCategory.INTERACTIVE to 1_000L,
                ),
                NoticeCategory.MY_POST,
            ),
        )
        assertEquals(
            NoticeCategory.INTERACTIVE,
            newestNoticeCategory(
                mapOf(
                    NoticeCategory.INTERACTIVE to 1_000L,
                    NoticeCategory.PRIVATE_PM to 1_000L,
                ),
                NoticeCategory.MY_POST,
            ),
        )
    }
}
