package com.ubermicrostudios.messagesenhanced.nativeapp.ui.thread

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.MessageDto
import com.ubermicrostudios.messagesenhanced.nativeapp.databinding.ItemViewerReplyBinding
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Formatters
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ViewerReplies

class ViewerReplyAdapter : RecyclerView.Adapter<ViewerReplyAdapter.Row>() {
    private var items: List<MessageDto> = emptyList()
    var title: String = ""
    var isGroup: Boolean = false

    fun submit(next: List<MessageDto>) {
        items = next
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Row {
        val binding = ItemViewerReplyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Row(binding)
    }

    override fun onBindViewHolder(holder: Row, position: Int) {
        val message = items[position]
        val name = ViewerReplies.senderLabel(message, title, isGroup)
        val binding = holder.binding
        binding.replySender.text = name
        binding.replyTime.text = Formatters.relativeTime(message.timestampMs)
        binding.replyBody.text = ViewerReplies.replyText(message)
        binding.replyAvatar.text = Formatters.initials(name)
        binding.replyAvatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Formatters.avatarColor(name))
        }
    }

    class Row(val binding: ItemViewerReplyBinding) : RecyclerView.ViewHolder(binding.root)
}
