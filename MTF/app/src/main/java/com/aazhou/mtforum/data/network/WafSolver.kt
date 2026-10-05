package com.aazhou.mtforum.data.network

import java.util.regex.Pattern

/**
 * 阿里云 ESA WAF 的 JS 挑战（acw_sc__v2）本地求解。
 *
 * 站点在判定请求可疑时返回一段约 2.7KB 的混淆 JS：把 arg1 按 40 项置换表重排，
 * 再与固定密钥逐字节异或，得到 acw_sc__v2 的值；浏览器写入该 Cookie 后刷新即放行。
 * 原生 HTTP 客户端没有 JS 引擎，故在本地复现该算法。
 *
 * 置换表与密钥取自站点当前版本，站点改版会失配；失配时仍由 [looksLikeChallenge] 识别。
 * 算法已用站点真实挑战页交叉验证：注入求解结果后返回完整论坛页。
 */
object WafSolver {

    const val COOKIE_NAME = "acw_sc__v2"

    private val ARG1 = Pattern.compile("var\\s+arg1\\s*=\\s*['\"]([0-9A-Fa-f]+)['\"]")

    /** 40 项 1-based 位置置换表。 */
    private val PERMUTATION = intArrayOf(
        15, 35, 29, 24, 33, 16, 1, 38, 10, 9,
        19, 31, 40, 27, 22, 23, 25, 13, 6, 11,
        39, 18, 20, 8, 14, 21, 32, 26, 2, 30,
        7, 4, 17, 5, 3, 28, 34, 37, 12, 36,
    )

    private const val XOR_KEY_HEX = "3000176000856006061501533003690027800375"

    private const val HEX = "0123456789abcdef"

    /** 是否为 WAF 挑战页：体积小、含 acw_sc__v2 且带 arg1 赋值。 */
    fun looksLikeChallenge(body: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        // 真实论坛页远大于挑战页（挑战页约 3KB，页面 10KB+）
        if (body.length > 64 * 1024) return false
        if (!body.contains(COOKIE_NAME)) return false
        return ARG1.matcher(body).find()
    }

    /** 求解挑战页，返回 acw_sc__v2 的值；无法求解返回 null。 */
    fun solve(body: String?): String? {
        if (body == null) return null
        val matcher = ARG1.matcher(body)
        if (!matcher.find()) return null
        val arg1 = matcher.group(1) ?: return null

        // 按置换表重排：只附加被置换表覆盖到的下标，与 JS 里 q.join('') 一致
        val reordered = StringBuilder()
        for (oneBased in PERMUTATION) {
            val idx = oneBased - 1
            if (idx in arg1.indices) reordered.append(arg1[idx])
        }
        val u = reordered.toString()

        val out = StringBuilder()
        val len = minOf(u.length, XOR_KEY_HEX.length)
        var i = 0
        while (i + 1 < len) {
            val a = hexVal(u[i]) * 16 + hexVal(u[i + 1])
            val b = hexVal(XOR_KEY_HEX[i]) * 16 + hexVal(XOR_KEY_HEX[i + 1])
            val v = a xor b
            out.append(HEX[v ushr 4]).append(HEX[v and 0xF])
            i += 2
        }
        return out.toString()
    }

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> 0
    }
}
