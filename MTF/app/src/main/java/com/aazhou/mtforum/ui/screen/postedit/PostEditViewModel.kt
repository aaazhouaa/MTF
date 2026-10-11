package com.aazhou.mtforum.ui.screen.postedit

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 已上传图片附件 */
data class UploadedImageAttachment(
    val aid: String,
    val localFile: File,
    val fileName: String,
)

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
                _message.value = TextFieldValue(data.message)
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
        _message.value = TextFieldValue(draft.message)
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

    suspend fun uploadImageFile(file: File): String? {
        // 发新帖的凭据随发帖页来，编辑态的随编辑页来（两个页面的内联上传配置同款）
        val uid = _initData.value?.uploadUid ?: _editData.value?.uploadUid
        val hash = _initData.value?.uploadHash ?: _editData.value?.uploadHash
        if (uid.isNullOrBlank() || hash.isNullOrBlank()) {
            _state.value = PostState.Error("图片上传凭据未就绪，请稍后重试")
            return null
        }
        _uploading.value = true
        return try {
            val aid = repository.uploadReplyImage(file, uid, hash)
            if (aid != null) {
                _uploadedImages.value = _uploadedImages.value + UploadedImageAttachment(
                    aid = aid,
                    localFile = file,
                    fileName = file.name,
                )
            }
            aid
        } catch (e: Exception) {
            _state.value = PostState.Error(e.message ?: "图片上传失败")
            null
        } finally {
            _uploading.value = false
        }
    }

    fun removeAttachment(attachment: UploadedImageAttachment) {
        _uploadedImages.value = _uploadedImages.value.filter { it.aid != attachment.aid }
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
                val result = repository.editPost(
                    tid = editTid!!,
                    pid = editPid!!,
                    formhash = formhash,
                    message = normalizeAtMentions(_message.value.text, _atUsers.value),
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
