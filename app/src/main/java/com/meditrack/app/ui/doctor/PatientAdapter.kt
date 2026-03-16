package com.meditrack.app.ui.doctor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.meditrack.app.R
import com.meditrack.app.data.model.User
import com.google.android.material.card.MaterialCardView

class PatientAdapter(
    private val onPatientClick: (User) -> Unit
) : ListAdapter<User, PatientAdapter.PatientViewHolder>(PatientDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_patient_card_premium, parent, false)
        return PatientViewHolder(view)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PatientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val card: MaterialCardView = itemView.findViewById(R.id.patientCard)
        private val profileImage: ImageView = itemView.findViewById(R.id.patientImage)
        private val nameText: TextView = itemView.findViewById(R.id.patientName)
        private val emailText: TextView = itemView.findViewById(R.id.patientEmail)
        private val phoneText: TextView = itemView.findViewById(R.id.patientPhone)
        private val chevron: ImageView = itemView.findViewById(R.id.chevronIcon)

        init {
            card.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPatientClick(getItem(position))
                }
            }
        }

        fun bind(patient: User) {
            nameText.text = patient.displayName.ifEmpty { "Unknown Patient" }
            emailText.text = patient.email

            if (patient.phoneNumber.isNotEmpty()) {
                phoneText.visibility = View.VISIBLE
                phoneText.text = patient.phoneNumber
            } else {
                phoneText.visibility = View.GONE
            }

            if (patient.profileImageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(patient.profileImageUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(profileImage)
            } else {
                profileImage.setImageResource(R.drawable.ic_person)
            }
        }
    }

    class PatientDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}

