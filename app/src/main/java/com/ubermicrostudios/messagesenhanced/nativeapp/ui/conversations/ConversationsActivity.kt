package com.ubermicrostudios.messagesenhanced.nativeapp.ui.conversations

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.CircleCropTransformation
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.ConversationDto
import com.ubermicrostudios.messagesenhanced.nativeapp.databinding.ActivityConversationsBinding
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.Insets
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.SessionActivity
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.settings.SettingsActivity
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.thread.ThreadActivity
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.thread.ThreadFragment
import com.ubermicrostudios.messagesenhanced.nativeapp.update.UpdateChecker
import com.ubermicrostudios.messagesenhanced.nativeapp.update.UpdatePrompts
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ConversationOrder
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Participants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ConversationsActivity : SessionActivity() {

    @Inject lateinit var updateChecker: UpdateChecker

    private lateinit var binding: ActivityConversationsBinding
    private lateinit var updatePrompts: UpdatePrompts
    private val viewModel: ConversationsViewModel by viewModels()
    private lateinit var adapter: ConversationAdapter
    private lateinit var searchAdapter: SearchAdapter
    private var writingSearch = false
    private var shownProfileKey: String? = null

    private val twoPane: Boolean
        get() = binding.threadContainer != null

    private val newChat = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val id = result.data?.getStringExtra(NewChatActivity.EXTRA_CONV).orEmpty()
        if (id.isBlank()) return@registerForActivityResult
        val name = result.data?.getStringExtra(NewChatActivity.EXTRA_NAME).orEmpty()
        val group = result.data?.getBooleanExtra(NewChatActivity.EXTRA_GROUP, false) == true
        viewModel.refresh()
        val known = viewModel.state.value.conversations.firstOrNull { it.conversationId == id }
        openThread(known ?: ConversationDto(conversationId = id, name = name, isGroup = group))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!viewModel.hasSession()) {
            goToLogin()
            return
        }
        Insets.enable(this)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updatePrompts = UpdatePrompts(this, updateChecker)
        val refreshSpan = (64 * resources.displayMetrics.density).toInt()
        if (twoPane) {
            Insets.applyTabletShell(
                root = binding.root,
                appBar = binding.appBar,
                chrome = binding.listChrome,
                scrolling = listOf(binding.list, binding.searchResults),
                fab = binding.startChat,
            ) { height ->
                binding.refresh.setProgressViewOffset(false, height, height + refreshSpan)
            }
        } else {
            Insets.applyScrollingChrome(
                root = binding.root,
                chrome = binding.listChrome,
                scrolling = listOf(binding.list, binding.searchResults),
                insetEnd = true,
                fab = binding.startChat,
            ) { height ->
                binding.refresh.setProgressViewOffset(false, height, height + refreshSpan)
            }
        }
        binding.startChat.setOnClickListener {
            newChat.launch(Intent(this, NewChatActivity::class.java))
        }
        observeAuthLoss()

        adapter = ConversationAdapter(
            origin = { viewModel.origin() },
            onClick = { openThread(it) },
            onMenu = { conversation, anchor -> showConversationMenu(conversation, anchor) },
        )
        searchAdapter = SearchAdapter(
            onConversation = { conversation ->
                clearSearchField()
                openThread(resolveConversation(conversation))
            },
            onMessage = { hit ->
                val known = viewModel.state.value.conversations
                    .firstOrNull { it.conversationId == hit.message.conversationId }
                val conversation = known ?: ConversationDto(
                    conversationId = hit.message.conversationId,
                    name = hit.title,
                )
                if (!twoPane) clearSearchField()
                openThread(conversation, hit.message.messageId, hit.message.timestampMs)
            },
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.searchResults.layoutManager = LinearLayoutManager(this)
        binding.searchResults.adapter = searchAdapter

        binding.profile.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.profile.contentDescription = getString(R.string.profile_open_settings)
        binding.searchButton.setOnClickListener { onSearchButton() }
        binding.search.doAfterTextChanged {
            if (writingSearch) return@doAfterTextChanged
            viewModel.onSearchInput(it?.toString().orEmpty())
        }
        binding.search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.submitSearch()
                true
            } else {
                false
            }
        }
        binding.search.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            binding.search.postDelayed({
                if (!binding.search.hasFocus() && binding.search.text.isNullOrBlank() && viewModel.state.value.search.open) {
                    viewModel.closeSearch()
                }
            }, 150)
        }
        binding.refresh.setOnRefreshListener { viewModel.refresh() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.state.value.search.open) {
                    clearSearchField()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        val listListener = FragmentResultListener { _, _ -> viewModel.onResume() }
        supportFragmentManager.setFragmentResultListener(ThreadFragment.RESULT_LIST, this, listListener)
        supportFragmentManager.setFragmentResultListener(ThreadFragment.RESULT_CLOSE, this) { _, _ ->
            supportFragmentManager.findFragmentById(R.id.thread_container)?.let { fragment ->
                supportFragmentManager.beginTransaction().remove(fragment).commit()
            }
            binding.threadPlaceholder?.isVisible = true
            viewModel.select(null)
            adapter.selectedId = null
            viewModel.onResume()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }

        handleOpenIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            viewModel.onResume()
            updatePrompts.onHostResume()
            updatePrompts.checkOnLaunch()
        }
    }

    override fun onDestroy() {
        if (::updatePrompts.isInitialized) updatePrompts.dismiss()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun render(state: ConversationsUiState) {
        adapter.colors = state.colors
        adapter.typingNames = state.typingNames
        adapter.selectedId = state.selectedId
        adapter.submitList(ConversationOrder.rows(state.conversations))
        state.notice?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
        val query = state.search.query.trim()
        val searching = query.isNotEmpty()
        binding.search.isVisible = state.search.open
        if (!writingSearch && binding.search.text?.toString().orEmpty() != state.search.query) {
            writingSearch = true
            binding.search.setText(state.search.query)
            binding.search.setSelection(binding.search.text?.length ?: 0)
            writingSearch = false
        }
        binding.refresh.isVisible = !searching
        binding.refresh.isRefreshing = state.refreshing && !searching
        searchAdapter.query = query
        val rows = searchAdapter.rows(
            getString(R.string.search_conversations),
            getString(R.string.search_messages),
            state.search,
        )
        searchAdapter.submitList(rows)
        binding.searchResults.isVisible = state.search.phase == SearchPhase.RESULTS
        binding.progress.isVisible = (state.loading && !searching) || state.search.phase == SearchPhase.LOADING
        binding.empty.isVisible = when {
            state.search.phase == SearchPhase.SHORT -> true
            state.search.phase == SearchPhase.EMPTY -> true
            !searching && !state.loading && state.conversations.isEmpty() -> true
            else -> false
        }
        binding.empty.text = when {
            state.search.phase == SearchPhase.SHORT -> getString(R.string.search_keep_typing)
            state.search.phase == SearchPhase.EMPTY && !state.search.messageError.isNullOrBlank() ->
                getString(R.string.search_failed, state.search.messageError)
            state.search.phase == SearchPhase.EMPTY -> getString(R.string.search_no_results, query)
            else -> getString(R.string.empty_conversations)
        }
        binding.error.isVisible = !state.error.isNullOrBlank()
        binding.error.text = state.error.orEmpty()
        renderProfile(state.profile)
    }

    private fun renderProfile(profile: ProfileUi) {
        val key = listOf(profile.photoUrl, profile.fallbackUrl, profile.initial, profile.colorSeed).joinToString("|")
        binding.profile.contentDescription = getString(R.string.profile_open_settings) + ", " + profile.name
        if (key == shownProfileKey) return
        shownProfileKey = key
        val color = Participants.avatarColor(profile.colorSeed)
        binding.profileInitials.text = profile.initial
        binding.profileInitials.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        val url = profile.photoUrl ?: profile.fallbackUrl
        if (url.isNullOrBlank()) {
            binding.profilePhoto.isVisible = false
            binding.profilePhoto.setImageDrawable(null)
            return
        }
        binding.profilePhoto.isVisible = true
        val accountPhoto = profile.photoUrl != null
        binding.profilePhoto.load(url) {
            transformations(CircleCropTransformation())
            listener(
                onError = { _, _ ->
                    if (accountPhoto) {
                        shownProfileKey = null
                        viewModel.onProfilePhotoError()
                    } else {
                        binding.profilePhoto.isVisible = false
                    }
                },
            )
        }
    }

    private fun onSearchButton() {
        val open = viewModel.state.value.search.open
        val blank = viewModel.state.value.search.query.isBlank()
        if (open && blank) {
            clearSearchField()
            return
        }
        viewModel.onSearchButton()
        binding.search.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.search, 0)
    }

    private fun clearSearchField() {
        writingSearch = true
        binding.search.setText("")
        writingSearch = false
        binding.search.clearFocus()
        viewModel.closeSearch()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.search.windowToken, 0)
    }

    private fun resolveConversation(conversation: ConversationDto): ConversationDto =
        viewModel.state.value.conversations.firstOrNull { it.conversationId == conversation.conversationId }
            ?: conversation

    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_THREAD) return
        val conv = intent.getStringExtra(EXTRA_CONV).orEmpty()
        if (conv.isBlank()) return
        val match = viewModel.state.value.conversations.firstOrNull { it.conversationId == conv }
        if (match != null) {
            openThread(match)
        } else {
            openThread(
                ConversationDto(conversationId = conv, name = getString(R.string.thread_title)),
            )
        }
    }

    private fun showConversationMenu(conversation: ConversationDto, anchor: View) {
        if (conversation.googlePinned) {
            Toast.makeText(this, R.string.pinned_on_phone, Toast.LENGTH_SHORT).show()
            return
        }
        val pinned = conversation.localPinnedAtMs > 0L
        val popup = PopupMenu(this, anchor)
        popup.menu.add(
            0,
            MENU_PIN,
            0,
            if (pinned) R.string.unpin_conversation else R.string.pin_conversation,
        )
        popup.setOnMenuItemClickListener {
            viewModel.pin(conversation, !pinned)
            true
        }
        popup.show()
    }

    private fun openThread(conv: ConversationDto, jumpId: String? = null, jumpTs: Long = 0L) {
        viewModel.select(conv.conversationId)
        adapter.selectedId = conv.conversationId
        if (twoPane) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.thread_container, ThreadFragment.newInstance(conv, jumpId, jumpTs))
                .commit()
            binding.threadPlaceholder?.isVisible = false
        } else {
            startActivity(
                Intent(this, ThreadActivity::class.java).also {
                    ThreadFragment.writeIntent(it, conv, jumpId, jumpTs)
                },
            )
        }
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.ubermicrostudios.messagesenhanced.nativeapp.OPEN_THREAD"
        const val EXTRA_CONV = "conv"
        private const val MENU_PIN = 1
    }
}
