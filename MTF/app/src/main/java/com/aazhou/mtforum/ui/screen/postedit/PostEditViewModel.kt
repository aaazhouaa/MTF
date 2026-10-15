package com.aazhou.mtforum.ui.screen.postedit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.aazhou.mtforum.data.model.EditPostPageData
import com.aazhou.mtforum.data.model.NewThreadPageData
import com.aazhou.mtforum.data.model.PostDraft
import com.aazhou.mtforum.data.model.ThreadType
import com.aazhou.mtforum.data.parser.SmileyPack
import com.aazhou.mtforum.data.repo.DraftStore
import com.aazhou.mtforum.data.repo.ForumRepository
import com.aazhou.mtforum.data.repo.SmileyStore
import com.aazhou.mtforum.ui.screen.threaddetail.insertAtSelection
import com.aazhou.mtforum.ui.screen.threaddetail.normalizeAtMentions
import com.aazhou.mtforum.ui.screen.threaddetail.withAttachTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 已上传图片附件 */
data class UploadedImageAttachment(
    val aid: String,
    /**
     * 相册里选中的那张原图：缩略图与大图都按它加载。
     *
     * 压缩缓存只有一份固定路径（reply_upload.jpg），按它取图会让每一张附件都显示成
     * 最后插入的那张，故这里只留来源 Uri。
     */
    val sourceUri: Uri?,
    val fileName: String,
    /** 编辑态回填的站上已有附件：没有本地来源，缩略图与预览都走站点地址。 */
    val remoteUrl: String? = null,
) {
    /** 缩略图与大图共用的加载对象：新上传走相册原图，回填的站上附件走站点地址。 */
    val imageModel: Any get() = sourceUri ?: remoteUrl.orEmpty()
}

class PostEditViewModel(
    val fid: String,
    val forumName: String,
    private val repository: ForumRepository,
    private val smileyStore: SmileyStore,
    /** 编辑自己的帖子/评论时带上站点 tid/pid；为空表示发新帖。 */
    private val editTid: String? = null,
    private val editPid: String? = null,
    private val draftStore: DraftStore? = null,
) : ViewModel() {

    /** 是否在编辑站上已有内容（编辑态不落草稿：离开即放弃本次修改）。 */
    val editing: Boolean = !editTid.isNullOrBlank() && !editPid.isNullOrBlank()

    private val _initData = MutableStateFlow<NewThreadPageData?>(null)
    val initData: StateFlow<NewThreadPageData?> = _initData.asStateFlow()

    private val _editData = MutableStateFlow<EditPostPageData?>(null)
    val editData: StateFlow<EditPostPageData?> = _editData.asStateFlow()

    /** 编辑态回填的主题分类 id：分类列表随编辑页才回来，先记下等它就绪。 */
    private var pendingTypeId: String? = null

    /**
     * 进入编辑时该楼挂着的附件 aid：提交时与正文里还引用的那些对比，差集就是被删掉的图。
     *
     * 只从正文摘标记的话图仍挂在帖子上——站点解除附件关联靠的是独立的 deleteattach
     * 请求，编辑表单本身不带任何删除语义的字段。取附件列表而不取正文标记：标记会被
     * 用户改来改去，拿它当依据一改就残缺，差集算出来是空，删除就静默失效。
     */
    private var editInitialAids: Set<String> = emptySet()

    private val _loadingInit = MutableStateFlow(true)
    val loadingInit: StateFlow<Boolean> = _loadingInit.asStateFlow()

    private val _selectedType = MutableStateFlow<ThreadType?>(null)
    val selectedType: StateFlow<ThreadType?> = _selectedType.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()

    private val _message = MutableStateFlow(TextFieldValue())
    val message: StateFlow<TextFieldValue> = _message.asStateFlow()

    private val _uploadedImages = MutableStateFlow<List<UploadedImageAttachment>>(emptyList())
    val uploadedImages: StateFlow<List<UploadedImageAttachment>> = _uploadedImages.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _smileyPacks = MutableStateFlow<List<SmileyPack>>(emptyList())
    val smileyPacks: StateFlow<List<SmileyPack>> = _smileyPacks.asStateFlow()

    private val _atUsers = MutableStateFlow<List<String>>(emptyList())
    val atUsers: StateFlow<List<String>> = _atUsers.asStateFlow()

    val recentCodes = smileyStore.recent

    private val _state = MutableStateFlow<PostState>(PostState.Idle)
    val state: StateFlow<PostState> = _state.asStateFlow()

    val canPost: Boolean get() = repository.isLoggedIn()

    init {
        if (editing) {
            loadEditData()
        } else {
            applyDraft()
            loadInitData()
        }
        loadSmileysAndAtUsers()
    }

    /** 编辑态：拉编辑页，把站点表单里的原标题/原文填进输入框。 */
    private fun loadEditData() {
        viewModelScope.launch {
            _loadingInit.value = true
            try {
                val data = repository.fetchEditPostInit(editTid!!, editPid!!)
                _editData.value = data
                _subject.value = data.subject.orEmpty()
                // 站点回的正文不一定带着这些标记（同评论编辑），没有就补齐再回填，
                // 正文和缩略图得一一对应，删标记才等于删图
                _message.value = TextFieldValue(withAttachTags(data.message, data.attachments.map { it.aid }))
                // 站点把已挂的图片附件单列在编辑表单里，回填成缩略图：只取正文的话，
                // 里面的 [attachimg] 标记在输入框里只是段弱化文字，编辑页一张图都看不见
                _uploadedImages.value = data.attachments.map {
                    UploadedImageAttachment(
                        aid = it.aid,
                        sourceUri = null,
                        fileName = it.fileName,
                        remoteUrl = it.url,
                    )
                }
                editInitialAids = data.attachments.mapTo(HashSet()) { it.aid }
                if (data.permissionDenied != null) {
                    _state.value = PostState.Error(data.permissionDenied)
                }
            } catch (e: Exception) {
                _state.value = PostState.Error(e.message ?: "加载编辑数据失败")
            } finally {
                _loadingInit.value = false
            }
        }
    }

    /** 进发帖页先把该版块的旧草稿垫上：标题、正文、主题分类都按离开时的样子还原。 */
    private fun applyDraft() {
        val draft = draftStore?.items?.value?.firstOrNull { it.fid == fid } ?: return
        _subject.value = draft.subject
        // 光标落在正文末尾：接着上次写的往下写，不用先拖到尾
        _message.value = TextFieldValue(draft.message, TextRange(draft.message.length))
        pendingTypeId = draft.typeId
    }

    /**
     * 此刻值得存进草稿箱的内容；没输入有效内容（编辑态、标题正文皆空）返回 null。
     * 判据是「有内容」而非「可发布」：只填了标题或只贴了段正文都算，用户回头还得接着写。
     */
    fun currentDraft(): PostDraft? {
        if (editing) return null
        val subject = _subject.value.trim()
        val message = _message.value.text
        if (subject.isBlank() && message.isBlank()) return null
        return PostDraft(
            fid = fid,
            forumName = forumName,
            subject = subject,
            message = message,
            typeId = _selectedType.value?.id ?: pendingTypeId,
            savedAt = System.currentTimeMillis(),
        )
    }

    private fun loadInitData() {
        viewModelScope.launch {
            _loadingInit.value = true
            try {
                val data = repository.fetchNewThreadInit(fid)
                _initData.value = data
                if (data.types.isNotEmpty() && _selectedType.value == null) {
                    // 草稿里记的分类可能已被站点撤下，那时退回第一个可选分类
                    _selectedType.value = pendingTypeId
                        ?.let { id -> data.types.firstOrNull { it.id == id } }
                        ?: data.types.first()
                    pendingTypeId = null
                }
                if (data.permissionDenied != null) {
                    _state.value = PostState.Error(data.permissionDenied)
                }
            } catch (e: Exception) {
                _state.value = PostState.Error(e.message ?: "加载发帖数据失败")
            } finally {
                _loadingInit.value = false
            }
        }
    }

    private fun loadSmileysAndAtUsers() {
        viewModelScope.launch {
            runCatching {
                _smileyPacks.value = repository.smileyPacks()
            }
            runCatching {
                _atUsers.value = repository.fetchAtUsers()
            }
        }
    }

    fun onSubjectChange(newSubject: String) {
        if (newSubject.length <= 80) {
            _subject.value = newSubject
        }
    }

    fun onMessageChange(newMessage: TextFieldValue) {
        _message.value = newMessage
    }

    /** 记录可 @ 的用户名（站点名单与选择器选中项），供提交前补 @ 分隔空白用。 */
    fun noteAtUsers(names: List<String>) {
        val merged = (_atUsers.value + names).filter { it.isNotBlank() }.distinct()
        _atUsers.value = merged
    }

    fun onSelectType(type: ThreadType?) {
        _selectedType.value = type
    }

    fun recordSmileyUsed(code: String) {
        viewModelScope.launch {
            smileyStore.addRecent(code)
        }
    }

    /**
     * 一次多选时按选择顺序逐张「压缩 → 上传」交替进行，全部结束后交回成功的那几个 aid。
     *
     * 压缩产物是同一份固定路径的缓存文件，必须逐张交替：先把所有图压完再上传，后一张
     * 会把前一张覆盖掉，整批全传成同一张。单张失败不打断整批，剩下的照传。
     */
    suspend fun uploadImages(sources: List<Uri>, prepare: suspend (Uri) -> File): List<String> {
        // 发新帖的凭据随发帖页来，编辑态的随编辑页来（两个页面的内联上传配置同款）
        val uid = _initData.value?.uploadUid ?: _editData.value?.uploadUid
        val hash = _initData.value?.uploadHash ?: _editData.value?.uploadHash
        if (uid.isNullOrBlank() || hash.isNullOrBlank()) {
            _state.value = PostState.Error("图片上传凭据未就绪，请稍后重试")
            return emptyList()
        }
        _uploading.value = true
        return try {
            val aids = mutableListOf<String>()
            for (uri in sources) {
                val file = try {
                    prepare(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue
                }
                val aid = try {
                    repository.uploadReplyImage(file, uid, hash)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: continue
                _uploadedImages.value = _uploadedImages.value + UploadedImageAttachment(
                    aid = aid,
                    sourceUri = uri,
                    fileName = file.name,
                )
                aids += aid
            }
            aids
        } finally {
            _uploading.value = false
        }
    }

    fun removeAttachment(attachment: UploadedImageAttachment) {
        _uploadedImages.value = _uploadedImages.value.filter { it.aid != attachment.aid }
        // 站上已有的附件，删的是正文里的引用：标记留在输入框里，保存后图仍旧挂在帖子上
        if (attachment.remoteUrl == null) return
        val current = _message.value
        val tag = "[attachimg]${attachment.aid}[/attachimg]"
        val start = current.text.indexOf(tag)
        if (start < 0) return
        val end = start + tag.length
        val cursor = current.selection.start
        val newCursor = if (cursor >= end) cursor - tag.length else minOf(cursor, start)
        _message.value = current.copy(
            text = current.text.removeRange(start, end),
            selection = TextRange(newCursor.coerceIn(0, current.text.length - tag.length)),
        )
    }

    fun insertTextAtSelection(insertText: String) {
        _message.value = _message.value.insertAtSelection(insertText)
    }

    fun submit() {
        if (!repository.isLoggedIn()) {
            _state.value = PostState.Error("请先登录后再发帖")
            return
        }
        val sub = _subject.value.trim()
        val msg = _message.value.text.trim()
        if (msg.isBlank()) {
            _state.value = PostState.Error("请输入帖子内容")
            return
        }
        if (editing) {
            submitEdit(sub, msg)
            return
        }
        if (sub.isBlank()) {
            _state.value = PostState.Error("请输入帖子标题")
            return
        }
        val init = _initData.value
        val formhash = init?.formhash
        if (formhash.isNullOrBlank()) {
            _state.value = PostState.Error("无法获取发帖令牌，请稍后重试")
            return
        }
        _state.value = PostState.Sending
        viewModelScope.launch {
            try {
                val aids = _uploadedImages.value.map { it.aid }
                val result = repository.submitNewThread(
                    fid = fid,
                    formhash = formhash,
                    subject = sub,
                    // 站点只认「@用户名 + ASCII 空白」，候选还按出现顺序受用户组 @ 名额截断，
                    // 少一个空格整条消息的 @ 都会静默失效，故提交前按已知名单补回分隔空白
                    message = normalizeAtMentions(_message.value.text, _atUsers.value),
                    posttime = init.posttime,
                    typeid = _selectedType.value?.id,
                    aids = aids,
                )
                if (result.success) {
                    _state.value = PostState.Success
                } else {
                    _state.value = PostState.Error(result.message ?: "发帖失败，请稍后重试")
                }
            } catch (e: Exception) {
                _state.value = PostState.Error(e.message ?: "发帖失败")
            }
        }
    }

    /** 提交编辑：首帖连标题一起交，评论只交正文。 */
    private fun submitEdit(subject: String, message: String) {
        val edit = _editData.value
        val formhash = edit?.formhash
        if (formhash.isNullOrBlank()) {
            _state.value = PostState.Error("无法获取编辑令牌，请稍后重试")
            return
        }
        // 站点只在首帖表单里给标题输入框，编辑态据此区分「改主题」与「改评论」
        val isFirstPost = !edit.subject.isNullOrBlank()
        if (isFirstPost && subject.isBlank()) {
            _state.value = PostState.Error("请输入帖子标题")
            return
        }
        _state.value = PostState.Sending
        viewModelScope.launch {
            try {
                val text = normalizeAtMentions(_message.value.text, _atUsers.value)
                // 进入时挂着、现在正文里不再引用的附件，就是这次要删掉的
                val deletedAids = editInitialAids - repository.attachAidsIn(text).toSet()
                // 删除要先于提交：站点用独立的 deleteattach 请求解除关联，
                // 编辑表单本身不带任何删除语义的字段
                for (aid in deletedAids) {
                    repository.deleteAttach(editTid!!, editPid!!, aid, formhash)
                }
                val result = repository.editPost(
                    tid = editTid!!,
                    pid = editPid!!,
                    formhash = formhash,
                    message = text,
                    subject = if (isFirstPost) subject else null,
                )
                _state.value = if (result.success) {
                    PostState.Success
                } else {
                    PostState.Error(result.message ?: "编辑未生效，请稍后重试")
                }
            } catch (e: Exception) {
                _state.value = PostState.Error(e.message ?: "编辑失败")
            }
        }
    }

    fun consume() {
        _state.value = PostState.Idle
    }

    sealed interface PostState {
        data object Idle : PostState
        data object Sending : PostState
        data object Success : PostState
        data class Error(val message: String) : PostState
    }
}
