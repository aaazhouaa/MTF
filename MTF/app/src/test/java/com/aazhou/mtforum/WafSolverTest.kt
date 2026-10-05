package com.aazhou.mtforum

import com.aazhou.mtforum.data.network.WafSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WafSolverTest {

    /** 站点真实下发的挑战页片段（2026-10-05 抓取），arg1 与期望 cookie 已交叉验证。 */
    private val realChallenge =
        "<html><script>var arg1='FC14DE01DDC16C57C91593F32ADEC4CE64A4E536';" +
            "(function(a,c){var G=a0j,d=a();while(!![]){try{var e=-parseInt(G(0x117))/0x1;}catch(f){}}}(a0i,0x760bf));" +
            "document.cookie='acw_sc__v2='+v;location.reload();</script></html>"

    @Test
    fun detectsChallengePage() {
        assertTrue(WafSolver.looksLikeChallenge(realChallenge))
    }

    @Test
    fun ignoresNormalPage() {
        val normal = "<html><head><title>MT论坛</title></head><body>" +
            "<div class=\"comiis_postlist\">内容</div></body></html>"
        assertFalse(WafSolver.looksLikeChallenge(normal))
    }

    @Test
    fun solvesRealChallenge() {
        assertEquals(
            "6ac37095dd990d3920f93802f9e9ad04ea9e4d61",
            WafSolver.solve(realChallenge),
        )
    }

    @Test
    fun returnsNullWhenNoArg1() {
        assertEquals(null, WafSolver.solve("<html><script>alert(1)</script></html>"))
    }
}
