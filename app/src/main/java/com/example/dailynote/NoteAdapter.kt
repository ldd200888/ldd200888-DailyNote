package com.example.dailynote

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<NoteListItem>()
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(groupedNotes: Map<String, List<Note>>) {
        items.clear()
        groupedNotes.forEach { (day, dayNotes) ->
            items.add(NoteListItem.Header(day))
            dayNotes.forEach { note ->
                items.add(NoteListItem.Content(note))
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is NoteListItem.Header -> TYPE_HEADER
            is NoteListItem.Content -> TYPE_NOTE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_date_header, parent, false)
            HeaderHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_note, parent, false)
            NoteHolder(view)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is NoteListItem.Header -> (holder as HeaderHolder).bind(item)
            is NoteListItem.Content -> (holder as NoteHolder).bind(item.note)
        }
    }

    inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDate: TextView = itemView.findViewById(R.id.textDate)

        fun bind(header: NoteListItem.Header) {
            textDate.text = header.date
        }
    }

    inner class NoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textContent: TextView = itemView.findViewById(R.id.textContent)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)

        fun bind(note: Note) {
            textContent.text = note.content
            textTime.text = timeFormatter.format(Date(note.createdAt))
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_NOTE = 1
    }
}
