package com.example.meditrack.ui.appointment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.Appointment
import com.example.meditrack.data.model.AppointmentStatus
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class AppointmentAdapter(
    private val isDoctor: Boolean,
    private val onConfirm: ((Appointment) -> Unit)? = null,
    private val onReject: ((Appointment) -> Unit)? = null,
    private val onCancel: ((Appointment) -> Unit)? = null,
    private val onReschedule: ((Appointment) -> Unit)? = null
) : ListAdapter<Appointment, AppointmentAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Appointment>() {
            override fun areItemsTheSame(a: Appointment, b: Appointment) = a.id == b.id
            override fun areContentsTheSame(a: Appointment, b: Appointment) = a == b
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.nameText)
        val dateText: TextView = view.findViewById(R.id.dateText)
        val timeText: TextView = view.findViewById(R.id.timeText)
        val statusBadge: TextView = view.findViewById(R.id.statusBadge)
        val typeText: TextView = view.findViewById(R.id.typeText)
        val notesText: TextView = view.findViewById(R.id.notesText)
        val confirmButton: MaterialButton = view.findViewById(R.id.confirmButton)
        val rejectButton: MaterialButton = view.findViewById(R.id.rejectButton)
        val cancelButton: MaterialButton = view.findViewById(R.id.cancelButton)
        val rescheduleButton: MaterialButton = view.findViewById(R.id.rescheduleButton)
        val actionsContainer: View = view.findViewById(R.id.actionsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_appointment, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appt = getItem(position)
        val ctx = holder.itemView.context
        val dateFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

        holder.nameText.text = if (isDoctor) appt.patientName else "Dr. ${appt.doctorName}"
        holder.dateText.text = dateFmt.format(appt.date)
        holder.timeText.text = "${appt.startTime} – ${appt.endTime}"
        holder.typeText.text = appt.type.name.replace("_", " ")

        if (appt.notes.isNotEmpty()) {
            holder.notesText.visibility = View.VISIBLE
            holder.notesText.text = appt.notes
        } else {
            holder.notesText.visibility = View.GONE
        }

        // Status badge
        holder.statusBadge.text = appt.status.name
        val (bgColor, textColor) = when (appt.status) {
            AppointmentStatus.PENDING -> R.color.premium_warning_surface to R.color.premium_warning
            AppointmentStatus.CONFIRMED -> R.color.premium_success_surface to R.color.premium_success
            AppointmentStatus.REJECTED -> R.color.premium_error_surface to R.color.premium_error
            AppointmentStatus.CANCELLED -> R.color.premium_surface_dim to R.color.premium_text_tertiary
            AppointmentStatus.COMPLETED -> R.color.premium_info_surface to R.color.premium_info
        }
        holder.statusBadge.setBackgroundColor(ContextCompat.getColor(ctx, bgColor))
        holder.statusBadge.setTextColor(ContextCompat.getColor(ctx, textColor))

        // Action buttons visibility
        val showDoctorActions = isDoctor && appt.status == AppointmentStatus.PENDING
        val showCancelAction = appt.status == AppointmentStatus.PENDING || appt.status == AppointmentStatus.CONFIRMED
        val showRescheduleAction = appt.status == AppointmentStatus.PENDING || appt.status == AppointmentStatus.CONFIRMED

        holder.confirmButton.visibility = if (showDoctorActions) View.VISIBLE else View.GONE
        holder.rejectButton.visibility = if (showDoctorActions) View.VISIBLE else View.GONE
        holder.cancelButton.visibility = if (showCancelAction) View.VISIBLE else View.GONE
        holder.rescheduleButton.visibility = if (showRescheduleAction) View.VISIBLE else View.GONE
        holder.actionsContainer.visibility =
            if (showDoctorActions || showCancelAction || showRescheduleAction) View.VISIBLE else View.GONE

        holder.confirmButton.setOnClickListener { onConfirm?.invoke(appt) }
        holder.rejectButton.setOnClickListener { onReject?.invoke(appt) }
        holder.cancelButton.setOnClickListener { onCancel?.invoke(appt) }
        holder.rescheduleButton.setOnClickListener { onReschedule?.invoke(appt) }
    }
}

