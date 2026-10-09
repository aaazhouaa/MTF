package com.aazhou.mtforum

import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.data.parser.SmileyParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 表情表整份存本地 DataStore，靠 [Smiley] / [SmileyPack] 的 JSON 序列化落地。
 * 这里用一小段站点真实结构的 smilies JS 走「解析 -> 序列化 -> 反序列化」，
 * 确认从缓存里读回来的表与解析出来的是同一份：包的顺序、code、url 都不能变。
 */
class SmileyStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val smiliesJs = """
        smilies_type['_12'] = ['默认', 'qq'];
        smilies_type['_13'] = ['滑稽', 'comiis_tb'];
        smilies_array[12][1] = [
          ['1240', '[呵呵]', 'qq001.gif', '20', '20', '50'],
          ['1241', '[哈哈]', 'qq002.gif', '20', '20', '50'],
          ['1242', '[吐舌]', 'qq003.gif', '20', '20', '50']
        ];
        smilies_array[12][2] = [
          ['1243', '[啊]', 'qq004.gif', '20', '20', '50']
        ];
        smilies_array[13][1] = [
          ['1300', '[#滑稽]', 'tb001.gif', '20', '20', '50']
        ];
    """.trimIndent()

    @Test
    fun parsesPacksInSiteOrder() {
        val packs = SmileyParser.parse(smiliesJs)
        assertEquals(2, packs.size)
        val qq = packs[0]
        assertEquals("12", qq.id)
        assertEquals("默认", qq.name)
        assertEquals("qq", qq.folder)
        // 同一包的多个分页要并成一列，顺序即站点给出的顺序
        assertEquals(listOf("[呵呵]", "[哈哈]", "[吐舌]", "[啊]"), qq.items.map { it.code })
        assertEquals("https://cdn.binmt.cc/static/image/smiley/qq/qq004.gif", qq.items.last().url)
        assertEquals(listOf("[#滑稽]"), packs[1].items.map { it.code })
    }

    @Test
    fun survivesJsonRoundTrip() {
        val packs = SmileyParser.parse(smiliesJs)
        val decoded = json.decodeFromString<List<SmileyPack>>(json.encodeToString(packs))
        assertEquals(packs, decoded)
    }
}
