package com.example.dailynote

import android.icu.util.ChineseCalendar
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(
    private val onNoteLongClick: (Note) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface HeaderPositionListener {
        fun onHeaderPositionsChanged(dayHeaders: List<String>)
    }

    private val items = mutableListOf<NoteListItem>()
    private val groupedNotes = linkedMapOf<String, List<Note>>()
    private val expandedDays = linkedSetOf<String>()

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val weekFormatter = SimpleDateFormat("EEEE", Locale.CHINA)
    private val todayText = dayFormatter.format(Date())

    private var expandMode = BackupPreferences.EXPAND_MODE_LATEST_DAY
    private var headerPositionListener: HeaderPositionListener? = null

    fun submit(grouped: Map<String, List<Note>>, preferredExpandMode: Int = expandMode) {
        val modeChanged = preferredExpandMode != expandMode
        val previousDays = groupedNotes.keys.toSet()
        expandMode = preferredExpandMode
        groupedNotes.clear()
        groupedNotes.putAll(grouped)

        if (groupedNotes.isEmpty()) {
            expandedDays.clear()
            items.clear()
            notifyDataSetChanged()
            headerPositionListener?.onHeaderPositionsChanged(emptyList())
            return
        }

        val validDays = groupedNotes.keys.toSet()
        val addedDays = validDays - previousDays
        expandedDays.retainAll(validDays)

        if (modeChanged || expandedDays.isEmpty()) {
            applyExpandMode(validDays)
        }
        if (expandMode != BackupPreferences.EXPAND_MODE_ALL_COLLAPSED && todayText in addedDays) {
            expandedDays.add(todayText)
        }

        rebuildItems()
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

    fun setHeaderPositionListener(listener: HeaderPositionListener?) {
        headerPositionListener = listener
    }

    fun getDayHeaders(): List<String> = groupedNotes.keys.toList()

    fun getHeaderAdapterPosition(day: String): Int {
        return items.indexOfFirst { it is NoteListItem.Header && it.date == day }
    }

    private fun applyExpandMode(validDays: Set<String>) {
        expandedDays.clear()
        when (expandMode) {
            BackupPreferences.EXPAND_MODE_ALL_EXPANDED -> expandedDays.addAll(validDays)
            BackupPreferences.EXPAND_MODE_ALL_COLLAPSED -> Unit
            else -> {
                if (todayText in validDays) {
                    expandedDays.add(todayText)
                } else {
                    groupedNotes.keys.firstOrNull()?.let(expandedDays::add)
                }
            }
        }
    }

    private fun rebuildItems() {
        items.clear()
        groupedNotes.forEach { (day, dayNotes) ->
            val expanded = expandedDays.contains(day)
            items.add(NoteListItem.Header(date = day, isExpanded = expanded, noteCount = dayNotes.size))
            if (expanded) {
                dayNotes.forEach { note ->
                    items.add(NoteListItem.Content(note))
                }
            }
        }
        notifyDataSetChanged()
        headerPositionListener?.onHeaderPositionsChanged(groupedNotes.keys.toList())
    }

    inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDate: TextView = itemView.findViewById(R.id.textDate)
        private val textCount: TextView = itemView.findViewById(R.id.textCount)
        private val imageExpand: ImageView = itemView.findViewById(R.id.imageExpand)

        fun bind(header: NoteListItem.Header) {
            textDate.text = buildHeaderText(header.date, header.noteCount)
            textCount.visibility = View.GONE
            imageExpand.rotation = if (header.isExpanded) 180f else 0f

            itemView.setOnClickListener {
                if (header.isExpanded) {
                    expandedDays.remove(header.date)
                } else {
                    expandedDays.add(header.date)
                }
                rebuildItems()
            }
        }

        private fun buildHeaderText(dayText: String, noteCount: Int): CharSequence {
            val date = parseDate(dayText) ?: return dayText
            val weekText = weekFormatter.format(date)
            val lunarText = buildLunarText(date)
            return SpannableStringBuilder().apply {
                append(dayText)
                append("     ")
                val countStart = length
                append("${noteCount}条")
                setSpan(
                    RelativeSizeSpan(0.8f),
                    countStart,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                append("\n")
                append("$weekText  $lunarText")
            }
        }

        private fun parseDate(dayText: String): Date? {
            return try {
                dayFormatter.parse(dayText)
            } catch (_: ParseException) {
                null
            }
        }

        private fun buildLunarText(date: Date): String {
            val lunarCalendar = ChineseCalendar().apply {
                timeInMillis = date.time
            }
            val zodiacYear = zodiacName(lunarCalendar.get(ChineseCalendar.YEAR))
            val month = lunarCalendar.get(ChineseCalendar.MONTH) + 1
            val day = lunarCalendar.get(ChineseCalendar.DAY_OF_MONTH)
            val isLeapMonth = lunarCalendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1
            val monthText = if (isLeapMonth) "闰${lunarMonthName(month)}" else lunarMonthName(month)
            return "${zodiacYear}年${monthText}${lunarDayName(day)}"
        }

        private fun zodiacName(lunarYearInCycle: Int): String {
            val zodiacNames = arrayOf(
                "鼠", "牛", "虎", "兔", "龙", "蛇",
                "马", "羊", "猴", "鸡", "狗", "猪"
            )
            return zodiacNames[(lunarYearInCycle - 1).mod(zodiacNames.size)]
        }

        private fun lunarMonthName(month: Int): String {
            val monthNames = arrayOf(
                "正月", "二月", "三月", "四月", "五月", "六月",
                "七月", "八月", "九月", "十月", "冬月", "腊月"
            )
            return monthNames.getOrElse(month - 1) { "${month}月" }
        }

        private fun lunarDayName(day: Int): String {
            val dayNames = arrayOf(
                "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
            )
            return dayNames.getOrElse(day - 1) { day.toString() }
        }
    }

    inner class NoteHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textContent: TextView = itemView.findViewById(R.id.textContent)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)

        fun bind(note: Note) {
            textContent.text = note.content
            textTime.text = timeFormatter.format(Date(note.createdAt))
            itemView.setOnLongClickListener {
                onNoteLongClick(note)
                true
            }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_NOTE = 1
    }
}
