package com.aazhou.mtforum.data.model

import kotlinx.serialization.Serializable

/** 好友请求项（home.php?mod=spacecp&ac=friend&op=request）。 */
@Serializable
data class FriendRequestItem(
    val uid: String,
    val username: String,
    val avatarUrl: String? = null,
    val description: String? = null,
)

/** 好友请求分页数据。 */
@Serializable
data class FriendRequestPage(
    val requests: List<FriendRequestItem>,
    val page: Int,
    val hasNext: Boolean,
    val formhash: String? = null,
)
