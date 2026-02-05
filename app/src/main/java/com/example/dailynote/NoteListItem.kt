package com.example.dailynote

sealed class NoteListItem {
    data class Header(
        val date: String,
        val isExpanded: Boolean,
        val noteCount: Int
    ) : NoteListItem()

    data class Content(val note: Note) : NoteListItem()
}
