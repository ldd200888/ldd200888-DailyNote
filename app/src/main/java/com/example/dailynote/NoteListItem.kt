package com.example.dailynote

sealed class NoteListItem {
    data class Header(val date: String) : NoteListItem()
    data class Content(val note: Note) : NoteListItem()
}
