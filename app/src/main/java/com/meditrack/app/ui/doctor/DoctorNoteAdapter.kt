package com.meditrack.app.ui.doctor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.DoctorNote
import com.meditrack.app.data.model.NoteCategory
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class DoctorNoteAdapter(
    private val onNoteClick: (DoctorNote) -> Unit,
    private val onDeleteClick: (DoctorNote) -> Unit
) : ListAdapter<DoctorNote, DoctorNoteAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doctor_note_premium, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val card: MaterialCardView = itemView.findViewById(R.id.noteCard)
        private val categoryIcon: ImageView = itemView.findViewById(R.id.categoryIcon)
        private val categoryText: TextView = itemView.findViewById(R.id.categoryText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val noteText: TextView = itemView.findViewById(R.id.noteText)
        private val privateContainer: View = itemView.findViewById(R.id.privateContainer)
        private val deleteButton: MaterialCardView = itemView.findViewById(R.id.deleteButton)

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())

        init {
            card.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onNoteClick(getItem(position))
                }
            }

            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }
        }

        fun bind(note: DoctorNote) {
            noteText.text = note.note
            dateText.text = note.createdAt?.let { dateFormat.format(it) } ?: "—"

            // Category styling
            categoryText.text = getCategoryDisplayName(note.category)
            val iconRes = getCategoryIcon(note.category)
            categoryIcon.setImageResource(iconRes)

            // Highlight WARNING notes with a tinted stroke
            if (note.category == NoteCategory.WARNING) {
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.medicine_orange)
                card.strokeWidth = 2
                categoryIcon.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(itemView.context, R.color.medicine_orange),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            } else {
                card.strokeColor = androidx.core.content.ContextCompat.getColor(itemView.context, R.color.divider)
                card.strokeWidth = 1
                categoryIcon.clearColorFilter()
            }

            // Private indicator
            privateContainer.visibility = if (note.isPrivate) View.VISIBLE else View.GONE
        }

        private fun getCategoryDisplayName(category: NoteCategory): String {
            return when (category) {
                NoteCategory.GENERAL -> "General"
                NoteCategory.DIAGNOSIS -> "Diagnosis"
                NoteCategory.PRESCRIPTION -> "Prescription"
                NoteCategory.FOLLOW_UP -> "Follow-up"
                NoteCategory.LAB_RESULTS -> "Lab Results"
                NoteCategory.OBSERVATION -> "Observation"
                NoteCategory.WARNING -> "Warning"
            }
        }

        private fun getCategoryIcon(category: NoteCategory): Int {
            return when (category) {
                NoteCategory.GENERAL -> R.drawable.ic_edit
                NoteCategory.DIAGNOSIS -> R.drawable.ic_health
                NoteCategory.PRESCRIPTION -> R.drawable.ic_pill
                NoteCategory.FOLLOW_UP -> R.drawable.ic_clock
                NoteCategory.LAB_RESULTS -> R.drawable.ic_health
                NoteCategory.OBSERVATION -> R.drawable.ic_visibility
                NoteCategory.WARNING -> R.drawable.ic_close
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<DoctorNote>() {
        override fun areItemsTheSame(oldItem: DoctorNote, newItem: DoctorNote): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DoctorNote, newItem: DoctorNote): Boolean {
            return oldItem == newItem
        }
    }
}

