package com.aazhou.mtforum.ui.common

import org.jsoup.Jsoup

/** 命中即折叠的灌水关键词（包含匹配，仅短回复生效）。 */
private val WATER_KEYWORDS = listOf(
    "感谢", "看看", "隐藏", "分享",
    "学习学习", "学习一下", "支持一下", "66666",
)

/** 关键词包含匹配只对不超过这么多字的短回复生效：长回复里的「感谢/分享」多半是正常讨论。 */
private const val KEYWORD_MAX_LENGTH = 5

/** 折叠的灌水短语（完全匹配，不做包含判断）。 */
private val WATER_PHRASES = hashSetOf(
    "拿走试试", "大佬牛逼", "拿走了", "查看内容", "这么牛逼啊",
    "大佬厉害了", "牛逼啊大佬", "我来了", "谢谢大佬", "我来试一试",
    "哇哇哇", "啊啊啊", "膜拜大佬", "严肃学习", "大佬牛批",
    "谢谢啦", "谢谢了", "来了来了", "回复一下表示支持",
    "谢谢楼主", "可以可以", "小飞机来了", "小飞机来喽", "小飞机来咯",
    "论坛有你更精彩",
)

private val RE_SMILEY_CODE = Regex("\\{:\\d+_\\d+:\\}")
private val RE_EM_CODE = Regex("\\[em:\\d+\\]")
private val RE_SPACE = Regex("[\\s\\u00A0\\u3000]")
private val RE_EMOJI = Regex(
    "[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2300-\u23FF\u2B50\u2B55\u200D\uFE0F]",
)
private val RE_PUNCT = Regex(
    "[\\p{P}\\p{S}，。！？!?,.~～、_—\\-+=\\[\\](){};；:：“”\"'/\\\\`]",
)

/** 楼层正文是否属于无信息量的灌水回复，命中即默认折叠。 */
fun isWaterReply(html: String?): Boolean {
    // 无正文的异常楼层不折叠；纯表情回复 html 非空，下面的空文本判定仍会命中
    if (html.isNullOrBlank()) return false
    val textOnly = Jsoup.parse(html).text().trim()
    // 去表情码与空白后的文本：用于字数与短语判定
    val clean = RE_SPACE.replace(
        RE_EM_CODE.replace(RE_SMILEY_CODE.replace(textOnly, ""), ""),
        "",
    )
    // 再去标点与符号：用于「是否有实义字符」判定
    val stripped = RE_PUNCT.replace(RE_EMOJI.replace(clean, ""), "")

    if (clean.length <= KEYWORD_MAX_LENGTH &&
        WATER_KEYWORDS.any { textOnly.contains(it, ignoreCase = true) }
    ) {
        return true
    }
    if (WATER_PHRASES.contains(stripped) || WATER_PHRASES.contains(clean)) return true
    if (clean.length <= 2) return true
    if (stripped.isEmpty()) return true
    if (stripped.all { it.isDigit() } && (isSequentialOrRepeatedDigits(stripped) || stripped.length >= 3)) {
        return true
    }
    return false
}

/** 全部相同（1111、66666）或逐位连续（123456、654321）的数字串。 */
private fun isSequentialOrRepeatedDigits(s: String): Boolean {
    if (s.length < 2 || !s.all { it.isDigit() }) return false
    if (s.all { it == s[0] }) return true
    if (s.length < 3) return false
    val ascending = s.indices.drop(1).all { s[it] - s[it - 1] == 1 }
    val descending = s.indices.drop(1).all { s[it - 1] - s[it] == 1 }
    return ascending || descending
}
