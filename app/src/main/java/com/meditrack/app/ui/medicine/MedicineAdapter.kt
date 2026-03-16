package com.meditrack.app.ui.medicine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.Medicine
import com.meditrack.app.databinding.ItemMedicineCardBinding

class MedicineAdapter(
    private val onItemClick: (Medicine) -> Unit,
    private val onToggleClick: (Medicine, Boolean) -> Unit
) : ListAdapter<Medicine, MedicineAdapter.MedicineViewHolder>(MedicineDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val binding = ItemMedicineCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MedicineViewHolder(
        private val binding: ItemMedicineCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isBinding = false

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.toggleContainer.setOnClickListener {
                if (isBinding) return@setOnClickListener
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val medicine = getItem(position)
                    val newState = !medicine.isActive
                    onToggleClick(medicine, newState)
                }
            }
        }

        fun bind(medicine: Medicine) {
            isBinding = true
            binding.apply {
                val context = root.context

                medicineName.text = medicine.name
                dosageText.text = "${medicine.dosage} ${medicine.unit}"

                val timesText = medicine.reminderTimes.joinToString(", ") { formatTime(it) }
                reminderTime.text = timesText.ifEmpty { "No reminders set" }

                // Show "Rx" indicator for doctor-prescribed medicines
                // Note: the prescribedBadge view is optional in the layout
                if (medicine.prescribedByDoctor) {
                    reminderTime.text = "Rx  •  $timesText".ifEmpty { "Rx  •  Prescribed by doctor" }
                }

                // Update toggle container and status based on state
                if (medicine.isActive) {
                    toggleContainer.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_active_bg))
                    toggleIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_active))
                    toggleIcon.setImageResource(R.drawable.ic_check)
                    statusText.text = context.getString(R.string.active)
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.status_active))
                } else {
                    toggleContainer.setCardBackgroundColor(ContextCompat.getColor(context, R.color.status_paused_bg))
                    toggleIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_paused))
                    toggleIcon.setImageResource(R.drawable.ic_pause)
                    statusText.text = context.getString(R.string.paused)
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.status_paused))
                }

                // Set color strip and icon container
                val colorRes = getColorForMedicine(medicine.color)
                val containerColorRes = getContainerColor(medicine.color)
                colorStrip.backgroundTintList = ContextCompat.getColorStateList(context, colorRes)
                iconContainer.backgroundTintList = ContextCompat.getColorStateList(context, containerColorRes)
                medicineIcon.setColorFilter(ContextCompat.getColor(context, colorRes))

                // ── Stock Level Display ──
                if (medicine.isRefillTrackingEnabled) {
                    stockContainer.visibility = View.VISIBLE
                    stockText.text = "${medicine.currentQuantity} left"

                    val pct = medicine.stockPercentage
                    stockProgressBar.progress = if (pct >= 0) pct else 0

                    // Color the progress bar based on stock level
                    val progressTint = when {
                        medicine.isOutOfStock -> R.color.error
                        medicine.isLowStock -> R.color.warning
                        else -> R.color.primary
                    }
                    stockProgressBar.progressTintList = ContextCompat.getColorStateList(context, progressTint)

                    // Low stock warning
                    if (medicine.isLowStock && medicine.isActive) {
                        lowStockWarning.visibility = View.VISIBLE
                        lowStockWarning.text = if (medicine.isOutOfStock)
                            "⚠ Out of stock — refill needed"
                        else
                            "⚠ Low stock — refill soon"
                    } else {
                        lowStockWarning.visibility = View.GONE
                    }
                } else {
                    stockContainer.visibility = View.GONE
                    lowStockWarning.visibility = View.GONE
                }
            }
            isBinding = false
        }

        private fun formatTime(time: String): String {
            return try {
                val parts = time.split(":")
                val hour = parts[0].toInt()
                val minute = parts[1]
                val amPm = if (hour >= 12) "PM" else "AM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$displayHour:$minute $amPm"
            } catch (e: Exception) {
                time
            }
        }

        private fun getColorForMedicine(color: String): Int {
            return when (color.lowercase()) {
                "red" -> R.color.medicine_red
                "orange" -> R.color.medicine_orange
                "yellow" -> R.color.medicine_yellow
                "green" -> R.color.medicine_green
                "teal" -> R.color.medicine_teal
                "blue" -> R.color.medicine_blue
                "purple" -> R.color.medicine_purple
                "pink" -> R.color.medicine_pink
                else -> R.color.primary
            }
        }

        private fun getContainerColor(color: String): Int {
            return when (color.lowercase()) {
                "red" -> R.color.medicine_red_surface
                "orange" -> R.color.medicine_orange_surface
                "yellow" -> R.color.medicine_yellow_surface
                "green" -> R.color.medicine_green_surface
                "teal" -> R.color.medicine_teal_surface
                "blue" -> R.color.medicine_blue_surface
                "purple" -> R.color.medicine_purple_surface
                "pink" -> R.color.medicine_pink_surface
                else -> R.color.primary_container
            }
        }
    }

    class MedicineDiffCallback : DiffUtil.ItemCallback<Medicine>() {
        override fun areItemsTheSame(oldItem: Medicine, newItem: Medicine): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Medicine, newItem: Medicine): Boolean {
            return oldItem == newItem
        }
    }
}
