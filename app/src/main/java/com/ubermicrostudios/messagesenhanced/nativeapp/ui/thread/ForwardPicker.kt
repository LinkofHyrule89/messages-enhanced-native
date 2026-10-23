package com.ubermicrostudios.messagesenhanced.nativeapp.ui.thread

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.ConversationDto
import com.ubermicrostudios.messagesenhanced.nativeapp.databinding.DialogForwardBinding
import com.ubermicrostudios.messagesenhanced.nativeapp.util.Participants
import com.ubermicrostudios.messagesenhanced.nativeapp.util.formatPhoneForDisplay

/** Pick a conversation, matching the web forward sheet's name filter (first 80). */
object ForwardPicker {
    fun show(
        context: Context,
        title: String,
        conversations: List<ConversationDto>,
        onPick: (ConversationDto) -> Unit,
    ) {
        val binding = DialogForwardBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun render(query: String) {
            val q = query.trim().lowercase()
            val list = conversations
                .filter { q.isEmpty() || Participants.convName(it).lowercase().contains(q) }
                .take(80)
            val labels = if (list.isEmpty()) {
                listOf(context.getString(R.string.forward_empty))
            } else {
                list.map { formatPhoneForDisplay(Participants.convName(it)) }
            }
            binding.forwardList.adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, labels) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(0xFFF2F4F7.toInt())
                    return view
                }
            }
            binding.forwardList.setOnItemClickListener { _, _, position, _ ->
                val conversation = list.getOrNull(position) ?: return@setOnItemClickListener
                dialog.dismiss()
                onPick(conversation)
            }
        }
        binding.forwardSearch.doAfterTextChanged { render(it?.toString().orEmpty()) }
        render("")
        dialog.show()
    }
}
