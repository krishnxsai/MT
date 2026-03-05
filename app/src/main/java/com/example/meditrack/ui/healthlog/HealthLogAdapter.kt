package com.example.meditrack.ui.healthlog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.HealthLog
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class HealthLogAdapter(
    private val onItemClick: (HealthLog) -> Unit,
    private val onDeleteClick: (HealthLog) -> Unit
) : ListAdapter<HealthLog, HealthLogAdapter.HealthLogViewHolder>(HealthLogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HealthLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_health_log, parent, false)
        return HealthLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: HealthLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HealthLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val card: MaterialCardView = itemView.findViewById(R.id.healthLogCard)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val heartRateText: TextView = itemView.findViewById(R.id.heartRateText)
        private val bloodPressureText: TextView = itemView.findViewById(R.id.bloodPressureText)
        private val glucoseText: TextView = itemView.findViewById(R.id.glucoseText)
        private val symptomsText: TextView = itemView.findViewById(R.id.symptomsText)
        private val notesText: TextView = itemView.findViewById(R.id.notesText)
        private val heartRateContainer: View = itemView.findViewById(R.id.heartRateContainer)
        private val bloodPressureContainer: View = itemView.findViewById(R.id.bloodPressureContainer)
        private val glucoseContainer: View = itemView.findViewById(R.id.glucoseContainer)
        private val symptomsContainer: View = itemView.findViewById(R.id.symptomsContainer)
        private val notesContainer: View = itemView.findViewById(R.id.notesContainer)
        private val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        init {
            card.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            deleteButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeleteClick(getItem(position))
                }
            }
        }

        fun bind(healthLog: HealthLog) {
            dateText.text = dateFormat.format(healthLog.date)
            timeText.text = timeFormat.format(healthLog.date)

            // Heart Rate
            if (healthLog.heartRate != null) {
                heartRateContainer.visibility = View.VISIBLE
                heartRateText.text = "${healthLog.heartRate} bpm"
            } else {
                heartRateContainer.visibility = View.GONE
            }

            // Blood Pressure
            if (healthLog.bloodPressureSystolic != null && healthLog.bloodPressureDiastolic != null) {
                bloodPressureContainer.visibility = View.VISIBLE
                bloodPressureText.text = "${healthLog.bloodPressureSystolic}/${healthLog.bloodPressureDiastolic} mmHg"
            } else {
                bloodPressureContainer.visibility = View.GONE
            }

            // Glucose
            if (healthLog.glucoseLevel != null) {
                glucoseContainer.visibility = View.VISIBLE
                glucoseText.text = "${healthLog.glucoseLevel} mg/dL"
            } else {
                glucoseContainer.visibility = View.GONE
            }

            // Symptoms
            if (healthLog.symptoms.isNotEmpty()) {
                symptomsContainer.visibility = View.VISIBLE
                symptomsText.text = healthLog.symptoms.joinToString(", ")
            } else {
                symptomsContainer.visibility = View.GONE
            }

            // Notes
            if (healthLog.notes.isNotEmpty()) {
                notesContainer.visibility = View.VISIBLE
                notesText.text = healthLog.notes
            } else {
                notesContainer.visibility = View.GONE
            }
        }
    }

    class HealthLogDiffCallback : DiffUtil.ItemCallback<HealthLog>() {
        override fun areItemsTheSame(oldItem: HealthLog, newItem: HealthLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HealthLog, newItem: HealthLog): Boolean {
            return oldItem == newItem
        }
    }
}

