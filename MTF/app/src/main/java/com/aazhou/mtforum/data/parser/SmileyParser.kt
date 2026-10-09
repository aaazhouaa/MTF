package com.aazhou.mtforum.data.parser

/** 一个表情包（站点后台的一套笑脸）。 */
data class SmileyPack(
    val id: String,
    val name: String,
    val folder: String,
    val items: List<Smiley>,
)

/** 单个笑脸：[code] 是写进正文的标记（如 `[呵呵]`、`[#滑稽]`），[url] 是它的图片。 */
data class Smiley(
    val code: String,
    val url: String,
)

/**
 * 解析站点的笑脸数据（`data/cache/common_smilies_var.js`）。
 *
 * 该文件形如：
 * ```
 * smilies_type['_12'] = ['默认', 'qq'];
 * smilies_array[12][1] = [['1240', '[呵呵]', 'qq001.gif', '20', '20', '50'], ...];
 * smilies_array[12][2] = [...];
 * ```
 * 一个包的条目分多页（`[1]`、`[2]`、`[3]`），这里按包合并成一列，顺序即站点给出的顺序。
 */
object SmileyParser {

    /** 站点静态资源域名：帖子页里的笑脸图都在这个域的 /static/image/smiley/ 下。 */
    private const val SMILEY_BASE = "https://cdn.binmt.cc/static/image/smiley/"

    private val RE_TYPE = Regex("""smilies_type\['_(\d+)'\]\s*=\s*\['([^']*)',\s*'([^']*)'\]""")
    private val RE_CHUNK = Regex("""smilies_array\[(\d+)\]\[\d+\]\s*=\s*\[""")
    private val RE_ITEM = Regex("""\['(\d+)',\s*'((?:[^'\\]|\\.)*)',\s*'([^']*)'""")

    fun parse(js: String): List<SmileyPack> {
        val folders = RE_TYPE.findAll(js).associate { it.groupValues[1] to (it.groupValues[2] to it.groupValues[3]) }
        // 按包把各页条目切开：一段从 smilies_array[<id>][<page>] = [ 开始，到下一个同类标记或文件结尾
        val chunks = mutableMapOf<String, MutableList<Smiley>>()
        val marks = RE_CHUNK.findAll(js).toList()
        marks.forEachIndexed { index, mark ->
            val packId = mark.groupValues[1]
            val end = marks.getOrNull(index + 1)?.range?.first ?: js.length
            val body = js.substring(mark.range.last + 1, end)
            val items = chunks.getOrPut(packId) { mutableListOf() }
            RE_ITEM.findAll(body).forEach { m ->
                val file = m.groupValues[3]
                val folder = folders[packId]?.second ?: return@forEach
                items += Smiley(code = m.groupValues[2], url = "$SMILEY_BASE$folder/$file")
            }
        }
        return folders.mapNotNull { (id, nameAndFolder) ->
            val items = chunks[id]?.distinctBy { it.code }?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SmileyPack(id = id, name = nameAndFolder.first, folder = nameAndFolder.second, items = items)
        }
    }
}
