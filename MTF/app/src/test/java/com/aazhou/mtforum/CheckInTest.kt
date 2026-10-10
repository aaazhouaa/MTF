package com.aazhou.mtforum

import com.aazhou.mtforum.data.parser.ForumParser
import com.aazhou.mtforum.data.repo.CheckInManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInTest {

    @Test
    fun `today date string matches yyyy-MM-dd format`() {
        val date = CheckInManager.todayDateString()
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date))
    }

    @Test
    fun `unsigned html parseCheckInStatus returns false`() {
        val html = """
            <script>var formhash = 'abc12345';</script>
            <div class="comiis_btnbox b_b cl">
                <a href="javascript:;" class="comiis_btn bg_c f_f" id="signresult" onclick="ajaxsign();window.location.reload();">签到</a>
            </div>
        """.trimIndent()
        val status = ForumParser.parseCheckInStatus(html)
        assertFalse(status.hasSigned)
        assertEquals("abc12345", status.formhash)
    }

    @Test
    fun `signed html parseCheckInStatus returns true`() {
        val html = """
            <script>var formhash = 'abc12345';</script>
            <div class="comiis_btnbox b_b cl">
                <a href="javascript:;" class="comiis_btn bg_b f_c">已签到</a>
            </div>
        """.trimIndent()
        val status = ForumParser.parseCheckInStatus(html)
        assertTrue(status.hasSigned)
        assertEquals("abc12345", status.formhash)
    }

    @Test
    fun `checkin xml response parsing`() {
        val successXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<root><![CDATA[已签到]]></root>"
        val successResult = ForumParser.parseCheckInResponse(successXml)
        assertTrue(successResult.success)
        assertEquals("签到成功", successResult.message)

        val alreadyXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<root><![CDATA[今日已签]]></root>"
        val alreadyResult = ForumParser.parseCheckInResponse(alreadyXml)
        assertTrue(alreadyResult.success)
        assertEquals("今日已签到", alreadyResult.message)

        val errXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<root><![CDATA[非法操作]]></root>"
        val errResult = ForumParser.parseCheckInResponse(errXml)
        assertFalse(errResult.success)
        assertEquals("非法操作", errResult.message)
    }
}
