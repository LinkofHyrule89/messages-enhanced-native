package com.ubermicrostudios.messagesenhanced.nativeapp.ui.thread

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.SpannableStringBuilder
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Mentions
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.imageview.ShapeableImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.ConversationDto
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.LinkPreviewDto
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.MessageDto
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.ParticipantDto
import com.ubermicrostudios.messagesenhanced.nativeapp.databinding.ItemMessageMeBinding
import com.ubermicrostudios.messagesenhanced.nativeapp.databinding.ItemMessageThemBinding
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.conversations.icon
import com.ubermicrostudios.messagesenhanced.nativeapp.ui.conversations.tint
import com.ubermicrostudios.messagesenhanced.nativeapp.util.BubbleColors
import com.ubermicrostudios.messagesenhanced.nativeapp.util.CircleAvatars
import com.ubermicrostudios.messagesenhanced.nativeapp.util.RecyclerIds
import com.ubermicrostudios.messagesenhanced.nativeapp.util.circleAvatar
import com.ubermicrostudios.messagesenhanced.nativeapp.util.stableCacheKey
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ConversationEncryption
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Encryption
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Formatters
import com.ubermicrostudios.messagesenhanced.nativeapp.util.LinkUrls
import com.ubermicrostudios.messagesenhanced.nativeapp.util.MessageRuns
import com.ubermicrostudios.messagesenhanced.nativeapp.util.MessageStatus
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Participants
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ReceiptKind
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ReplyTexts
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ThemeColors
import com.ubermicrostudios.messagesenhanced.nativeapp.util.formatPhoneForDisplay
import com.ubermicrostudios.messagesenhanced.nativeapp.util.formatPhonesInText

class MessageAdapter(
    private val mediaUrl: (MessageDto) -> String,
    private val previewOf: (String) -> LinkPreviewDto?,
    private val imageOf: (LinkPreviewDto) -> String?,
    private val requestPreview: (String) -> Unit,
    private val chrome: () -> ThreadChrome,
    private val conversation: () -> ConversationDto,
    private val origin: () -> String,
    private val messageById: (String) -> MessageDto?,
    private val onQuote: (String) -> Unit,
    private val syncGesture: (View) -> Unit,
    private val onRecycled: (View) -> Unit,
    private val onImage: (MessageDto) -> Unit,
) : ListAdapter<MessageRuns.Row, RecyclerView.ViewHolder>(Diff) {

    var starredIds: Set<String> = emptySet()
    var selectedIds: Set<String> = emptySet()
    var selecting: Boolean = false
    var detailId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    fun setMarks(starred: Set<String>, selected: Set<String>, selecting: Boolean) {
        if (starred == starredIds && selected == selectedIds && selecting == this.selecting) return
        starredIds = starred
        selectedIds = selected
        this.selecting = selecting
        notifyDataSetChanged()
    }

    object Diff : DiffUtil.ItemCallback<MessageRuns.Row>() {
        override fun areItemsTheSame(a: MessageRuns.Row, b: MessageRuns.Row) =
            a.message.messageId == b.message.messageId

        override fun areContentsTheSame(a: MessageRuns.Row, b: MessageRuns.Row) = a == b
    }

    override fun getItemId(position: Int): Long =
        RecyclerIds.of(1, getItem(position).message.messageId)

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).message.isFromMe) TYPE_ME else TYPE_THEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val holder = if (viewType == TYPE_ME) {
            MeHolder(ItemMessageMeBinding.inflate(inflater, parent, false))
        } else {
            ThemHolder(ItemMessageThemBinding.inflate(inflater, parent, false))
        }
        (holder.itemView as? ViewGroup)?.apply {
            clipChildren = false
            clipToPadding = false
        }
        return holder
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        onRecycled(holder.itemView)
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = getItem(position)
        when (holder) {
            is MeHolder -> holder.bind(row)
            is ThemHolder -> holder.bind(row)
        }
    }

    inner class MeHolder(private val b: ItemMessageMeBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: MessageRuns.Row) = bindBubble(
            row,
            root = b.root,
            bubble = b.bubble,
            fromMe = true,
            sender = b.sender,
            body = b.body,
            time = b.time,
            lockIcon = b.lockIcon,
            reactions = b.reactions,
            image = b.image,
            attachment = b.attachment,
            linkCard = b.linkCard,
            linkImage = b.linkImage,
            linkSite = b.linkSite,
            linkTitle = b.linkTitle,
            linkDesc = b.linkDesc,
            receipt = b.receipt,
            receiptAvatars = b.receiptAvatars,
            receiptLabel = b.receiptLabel,
            receiptIcon = b.receiptIcon,
            tapDetail = b.tapDetail,
            tapDetailText = b.tapDetailText,
            tapDetailLock = b.tapDetailLock,
        )
    }

    inner class ThemHolder(private val b: ItemMessageThemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: MessageRuns.Row) {
            bindBubble(
                row,
                root = b.root,
                bubble = b.bubble,
                fromMe = false,
                sender = b.sender,
                body = b.body,
                time = b.time,
                lockIcon = b.lockIcon,
                reactions = b.reactions,
                image = b.image,
                attachment = b.attachment,
                linkCard = b.linkCard,
                linkImage = b.linkImage,
                linkSite = b.linkSite,
                linkTitle = b.linkTitle,
                linkDesc = b.linkDesc,
                receipt = b.receipt,
                receiptAvatars = b.receiptAvatars,
                receiptLabel = b.receiptLabel,
                receiptIcon = b.receiptIcon,
                tapDetail = b.tapDetail,
                tapDetailText = b.tapDetailText,
                tapDetailLock = b.tapDetailLock,
            )
            bindSenderAvatar(row, b.senderAvatar, b.senderAvatarPhoto)
        }
    }

    private fun bindBubble(
        row: MessageRuns.Row,
        root: View,
        bubble: View,
        fromMe: Boolean,
        sender: TextView,
        body: TextView,
        time: TextView,
        lockIcon: ImageView,
        reactions: TextView,
        image: ImageView,
        attachment: TextView,
        linkCard: View,
        linkImage: ImageView,
        linkSite: TextView,
        linkTitle: TextView,
        linkDesc: TextView,
        receipt: View,
        receiptAvatars: LinearLayout,
        receiptLabel: TextView,
        receiptIcon: ImageView,
        tapDetail: View,
        tapDetailText: TextView,
        tapDetailLock: ImageView,
    ) {
        val item = row.message
        val look = chrome()
        val density = bubble.resources.displayMetrics.density
        val grouped = row.pos == MessageRuns.Pos.MID || row.pos == MessageRuns.Pos.LAST
        root.setPadding(root.paddingLeft, if (grouped) (1 * density).toInt() else (6 * density).toInt(), root.paddingRight, root.paddingBottom)

        val senderName = item.senderName.ifBlank { item.senderNumber }
        sender.isVisible = row.showSender && senderName.isNotBlank()
        sender.text = formatPhoneForDisplay(senderName)
        sender.setTextColor(look.colors.light)

        bubble.background = bubbleDrawable(density, fromMe, row.pos, BubbleColors.fill(look.colors, fromMe))
        // Hide the previewed URL when its card shows; no card -> keep the link.
        val deleted = MessageStatus.isDeleted(item.status)
        val shownText = if (deleted) MessageStatus.DELETED_TEXT else LinkUrls.firstHttpUrl(item.body)
            ?.takeIf { previewOf(it) != null }
            ?.let { LinkUrls.stripUrl(item.body, it) } ?: item.body
        linkify(body, shownText, if (fromMe) Color.WHITE else look.colors.light)
        body.setTypeface(null, if (deleted) android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)
        body.alpha = if (deleted) 0.7f else 1f
        body.isVisible = shownText.isNotBlank()

        val kind = if (item.isFromMe) MessageStatus.statusKind(item) else null
        val clock = Formatters.clockTime(item.timestampMs)
        val statusOnly = MessageStatus.bubbleLabel(item)
        time.isVisible = statusOnly.isNotEmpty()
        time.text = statusOnly
        time.setTextColor(if (kind == ReceiptKind.FAILED) 0xFFFF8589.toInt() else 0x99FFFFFF.toInt())

        val locked = Encryption.messageEncrypted(item, look.encryption) ||
            Encryption.protocolEncrypted(look.displayProtocol)
        lockIcon.isVisible = false
        bindReveal(root, clock, locked)
        bindQuote(root, item)
        root.setTag(R.id.tag_message_id, item.messageId)
        syncGesture(root)

        val reactionText = Formatters.formatReactions(item.reactions)
        reactions.text = reactionText
        reactions.isVisible = reactionText.isNotBlank()
        root.findViewById<TextView>(R.id.star_mark)?.isVisible = item.messageId in starredIds
        bubble.foreground = if (selecting && item.messageId in selectedIds) selectionStroke(density) else null

        when {
            item.isImageMedia() && item.localPreview.isNotBlank() -> {
                image.isVisible = true
                attachment.isVisible = false
                val src = item.localPreview
                if (src.startsWith("http://") || src.startsWith("https://")) {
                    image.load(src) {
                        crossfade(true)
                        stableCacheKey(src)
                    }
                } else {
                    image.load(File(src)) { crossfade(true) }
                }
            }
            item.isImageMedia() && item.messageId.isNotBlank() && !item.messageId.startsWith("local-") -> {
                image.isVisible = true
                attachment.isVisible = false
                image.load(mediaUrl(item)) {
                    crossfade(true)
                    stableCacheKey(mediaUrl(item))
                }
            }
            item.hasAttachment() -> {
                image.isVisible = false
                attachment.isVisible = true
                attachment.text = "[attachment]"
            }
            else -> {
                image.isVisible = false
                attachment.isVisible = false
            }
        }

        bindLink(item.body, linkCard, linkImage, linkSite, linkTitle, linkDesc)
        bindReceipt(row, kind, receipt, receiptAvatars, receiptLabel, receiptIcon, look)
        bindTapDetail(item, locked, tapDetail, tapDetailText, tapDetailLock)
        if (image.isVisible) {
            image.setOnClickListener { if (!selecting) onImage(item) }
        } else {
            image.setOnClickListener(null)
        }
        image.isClickable = image.isVisible
        reactions.isClickable = reactions.isVisible
    }

    private fun bindTapDetail(
        item: MessageDto,
        locked: Boolean,
        box: View,
        text: TextView,
        lock: ImageView,
    ) {
        val open = item.messageId == detailId
        box.isVisible = open
        if (!open) return
        text.text = displayReadBy(MessageStatus.detailLine(item), item.statusText)
        lock.isVisible = locked
        lock.imageTintList = ColorStateList.valueOf(box.context.getColor(R.color.muted))
    }

    private fun selectionStroke(density: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18f * density
        setStroke((2 * density).toInt(), 0xFF3E8BFF.toInt())
    }

    private fun bindReveal(root: View, clock: String, locked: Boolean) {
        val time = root.findViewById<TextView>(R.id.reveal_clock) ?: return
        val icon = root.findViewById<ImageView>(R.id.reveal_lock) ?: return
        time.text = clock
        icon.isVisible = locked
        icon.imageTintList = ColorStateList.valueOf(root.context.getColor(R.color.muted))
    }

    /**
     * Letter avatar left of the first bubble in a group sender run.
     * Mid-run rows keep an invisible spacer so bubbles stay aligned.
     * Reply status does not affect visibility — only [MessageRuns.Row.showSender].
     */
    private fun bindSenderAvatar(row: MessageRuns.Row, avatar: TextView, photo: ImageView) {
        val item = row.message
        photo.visibility = View.GONE
        if (!conversation().isGroup || item.isFromMe) {
            avatar.visibility = View.GONE
            return
        }
        if (!row.showSender) {
            avatar.visibility = View.INVISIBLE
            avatar.text = ""
            return
        }
        // Match the sender to a group participant (number first, then name) so
        // we can load the same contact photo the chat list uses.
        val conv = conversation()
        val person = item.senderNumber.takeIf { it.any(Char::isDigit) }?.let { Participants.memberByName(conv, it) }
            ?: item.senderName.takeIf { it.isNotBlank() }?.let { Participants.memberByName(conv, it) }
        val display = person?.name?.ifBlank { null } ?: item.senderName.ifBlank { item.senderNumber }
        val seed = item.senderNumber.ifBlank { item.senderName }.ifBlank { "?" }
        // Initials from the name (not the phone number's leading "1").
        val label = if (display.any(Char::isLetter)) Formatters.initials(display).take(1) else "#"
        avatar.visibility = View.VISIBLE
        avatar.text = label
        val url = person?.let { Participants.personAvatarUrl(origin(), it) }
        if (!url.isNullOrBlank()) {
            CircleAvatars.clip(photo as ShapeableImageView)
            photo.visibility = View.VISIBLE
            photo.load(url) {
                circleAvatar(url)
                listener(onError = { _, _ -> photo.visibility = View.GONE })
            }
        }
        val bg = ContextCompat.getDrawable(avatar.context, R.drawable.bg_sender_avatar)?.mutate()
        bg?.setTint(Participants.avatarColor(seed))
        avatar.background = bg
    }

    private fun bindQuote(root: View, item: MessageDto) {
        val box = root.findViewById<View>(R.id.reply_quote) ?: return
        val name = root.findViewById<TextView>(R.id.reply_quote_name)
        val text = root.findViewById<TextView>(R.id.reply_quote_text)
        val replyId = item.replyToId
        if (replyId.isBlank()) {
            box.isVisible = false
            box.setOnClickListener(null)
            box.isClickable = false
            return
        }
        box.isVisible = true
        val original = messageById(replyId)
        if (original == null) {
            name.isVisible = false
            text.text = ReplyTexts.MISSING
            text.setTypeface(null, Typeface.ITALIC)
            // Not loaded yet: tapping pages older history to find it.
            box.isClickable = true
            box.setOnClickListener { onQuote(replyId) }
            return
        }
        val conv = conversation()
        name.isVisible = true
        name.text = formatPhoneForDisplay(ReplyTexts.author(original, conv.name, conv.isGroup))
        text.setTypeface(null, Typeface.NORMAL)
        text.text = ReplyTexts.quoteText(ReplyTexts.snippetSource(original))
        box.isClickable = true
        box.setOnClickListener { onQuote(replyId) }
    }

    private fun bindLink(
        text: String,
        card: View,
        image: ImageView,
        site: TextView,
        title: TextView,
        desc: TextView,
    ) {
        val url = LinkUrls.firstHttpUrl(text)
        if (url == null) {
            card.isVisible = false
            return
        }
        requestPreview(url)
        val preview = previewOf(url)
        if (preview == null) {
            card.isVisible = false
            return
        }
        card.isVisible = true
        card.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(preview.url.ifBlank { url }))
            runCatching { card.context.startActivity(intent) }
                .onFailure { Toast.makeText(card.context, R.string.link_open_failed, Toast.LENGTH_SHORT).show() }
        }
        val picture = imageOf(preview)
        if (picture.isNullOrBlank()) {
            image.isVisible = false
            image.setImageDrawable(null)
        } else {
            image.isVisible = true
            image.load(picture) {
                crossfade(true)
                stableCacheKey(picture)
            }
        }
        val siteLabel = preview.siteLabel()
        site.isVisible = siteLabel.isNotBlank()
        site.text = siteLabel
        title.isVisible = preview.title.isNotBlank()
        title.text = preview.title
        desc.isVisible = preview.description.isNotBlank()
        desc.text = preview.description
    }

    private fun bindReceipt(
        row: MessageRuns.Row,
        kind: ReceiptKind?,
        receipt: View,
        avatars: LinearLayout,
        label: TextView,
        icon: ImageView,
        look: ThreadChrome,
    ) {
        avatars.removeAllViews()
        if (!row.showReceipt || kind == null) {
            receipt.isVisible = false
            return
        }
        receipt.isVisible = true
        val conv = conversation()
        val names = if (conv.isGroup && kind != ReceiptKind.FAILED && kind != ReceiptKind.SENDING) {
            MessageStatus.readByNames(row.message.statusText)
        } else {
            null
        }
        val others = if (conv.isGroup) Participants.otherMembers(conv).size else 0
        if (names != null && names.isNotEmpty() && !(others > 0 && names.size >= others)) {
            icon.isVisible = false
            label.isVisible = true
            label.text = displayReadBy(row.message.statusText.trim())
            label.setTextColor(look.colors.light)
            val density = avatars.resources.displayMetrics.density
            val size = (20 * density).toInt()
            val overlap = (6 * density).toInt()
            val ring = (2 * density).toInt()
            names.take(4).forEachIndexed { index, name ->
                val person = Participants.memberByName(conv, name)
                avatars.addView(readByChip(avatars, person, name, size, ring, if (index == 0) 0 else -overlap))
            }
            return
        }
        label.isVisible = false
        icon.isVisible = true
        icon.setImageResource(kind.icon())
        val tint = if (kind == ReceiptKind.READ) look.colors.accent else kind.tint(icon.context)
        icon.imageTintList = ColorStateList.valueOf(tint)
        icon.contentDescription = displayReadBy(row.message.statusText.ifBlank { kind.label })
    }

    private fun applyMentionChips(view: TextView, sb: SpannableStringBuilder, ranges: List<IntRange>, color: Int) {
        val density = view.resources.displayMetrics.density
        for (r in ranges) {
            if (r.last >= sb.length) continue
            sb.setSpan(MentionChipSpan(0x33FFFFFF, color, density), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun linkify(view: TextView, text: String, linkColor: Int) {
        val parts = LinkUrls.parts(text)
        val mentions = Mentions.ranges(text)
        if (parts.none { it.url != null }) {
            if (mentions.isEmpty()) {
                view.text = text
            } else {
                view.text = SpannableStringBuilder(text).also { applyMentionChips(view, it, mentions, linkColor) }
            }
            view.movementMethod = null
            return
        }
        val spanned = SpannableStringBuilder()
        for (part in parts) {
            val start = spanned.length
            spanned.append(part.text)
            val url = part.url ?: continue
            spanned.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { widget.context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(widget.context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
                            }
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = linkColor
                        ds.isUnderlineText = true
                    }
                },
                start,
                spanned.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        if (spanned.toString() == text) applyMentionChips(view, spanned, mentions, linkColor)
        view.text = spanned
        view.movementMethod = LinkMovementMethod.getInstance()
        view.highlightColor = Color.TRANSPARENT
    }

    private fun bubbleDrawable(density: Float, fromMe: Boolean, pos: MessageRuns.Pos, color: Int): GradientDrawable {
        val radius = 20f * density
        val tail = 6f * density
        val radii = FloatArray(8) { radius }
        fun tailTop() {
            if (fromMe) {
                radii[2] = tail
                radii[3] = tail
            } else {
                radii[0] = tail
                radii[1] = tail
            }
        }
        fun tailBottom() {
            if (fromMe) {
                radii[4] = tail
                radii[5] = tail
            } else {
                radii[6] = tail
                radii[7] = tail
            }
        }
        when (pos) {
            MessageRuns.Pos.SOLO -> Unit
            MessageRuns.Pos.FIRST -> tailBottom()
            MessageRuns.Pos.MID -> {
                tailTop()
                tailBottom()
            }
            MessageRuns.Pos.LAST -> tailTop()
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(color)
        }
    }

    private fun displayReadBy(text: String, nameSource: String = text): String {
        val names = MessageStatus.readByNames(nameSource)
        if (names.isNullOrEmpty()) return formatPhoneForDisplay(text)
        return formatPhonesInText(text, names)
    }

    private fun readByChip(
        parent: LinearLayout,
        person: ParticipantDto?,
        name: String,
        size: Int,
        ring: Int,
        marginStart: Int,
    ): View {
        val seed = person?.number?.ifBlank { person.phone }?.ifBlank { name } ?: name
        val display = person?.name?.ifBlank { name } ?: name
        val chip = FrameLayout(parent.context)
        chip.layoutParams = LinearLayout.LayoutParams(size, size).apply { this.marginStart = marginStart }
        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF0D0F12.toInt())
        }
        val inner = size - ring * 2
        val initials = TextView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(inner, inner).apply {
                gravity = Gravity.CENTER
            }
            gravity = Gravity.CENTER
            text = Formatters.initials(display)
            setTextColor(Color.WHITE)
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Participants.avatarColor(seed))
            }
        }
        chip.addView(initials)
        val url = person?.let { Participants.personAvatarUrl(origin(), it) }
        if (!url.isNullOrBlank()) {
            val photo = ShapeableImageView(parent.context).apply {
                layoutParams = FrameLayout.LayoutParams(inner, inner).apply {
                    gravity = Gravity.CENTER
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            CircleAvatars.clip(photo)
            chip.addView(photo)
            photo.load(url) {
                circleAvatar(url)
                listener(onError = { _, _ -> photo.isVisible = false })
            }
        }
        return chip
    }

    companion object {
        private const val TYPE_ME = 1
        private const val TYPE_THEM = 2
    }
}

data class ThreadChrome(
    val colors: ThemeColors = ThemeColors.Default,
    val encryption: ConversationEncryption = ConversationEncryption(),
    val displayProtocol: String = "",
)
