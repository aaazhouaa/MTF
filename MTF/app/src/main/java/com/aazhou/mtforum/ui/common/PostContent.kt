package com.aazhou.mtforum.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** 渲染帖子正文，图文按原顺序展示；图片点击回调交给上层做大图查看。 */
@Composable
fun PostContent(
    html: String,
    onImageClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val blocks = androidx.compose.runtime.remember(html) { parseContentBlocks(html) }
    Column(modifier) {
        for (block in blocks) {
            when (block) {
                is ContentBlock.Text -> {
                    val text = block.text.trim('\n')
                    if (text.isNotBlank()) {
                        Text(
                            text = AnnotatedString(text),
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                is ContentBlock.Image -> {
                    AsyncImage(
                        model = block.url,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (onImageClick != null) {
                                    Modifier.clickable { onImageClick(block.url) }
                                } else Modifier
                            ),
                    )
                }
            }
        }
    }
}
