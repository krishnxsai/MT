package com.meditrack.app.ui.order

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.meditrack.app.R
import com.meditrack.app.data.model.Pharmacy
import com.google.android.material.button.MaterialButton

class PharmacyAdapter(
    private val onSelect: (Pharmacy) -> Unit
) : ListAdapter<Pharmacy, PharmacyAdapter.PharmacyViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Pharmacy>() {
            override fun areItemsTheSame(a: Pharmacy, b: Pharmacy) = a.id == b.id
            override fun areContentsTheSame(a: Pharmacy, b: Pharmacy) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PharmacyViewHolder =
        PharmacyViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_pharmacy, parent, false)
        )

    override fun onBindViewHolder(holder: PharmacyViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class PharmacyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.pharmacyNameText)
        private val addressText: TextView = view.findViewById(R.id.pharmacyAddressText)
        private val ratingText: TextView = view.findViewById(R.id.ratingText)
        private val hoursText: TextView = view.findViewById(R.id.hoursText)
        private val deliveryBadge: TextView = view.findViewById(R.id.deliveryBadge)
        private val estimatedDeliveryText: TextView = view.findViewById(R.id.estimatedDeliveryText)
        private val selectButton: MaterialButton = view.findViewById(R.id.selectButton)

        fun bind(pharmacy: Pharmacy) {
            nameText.text = pharmacy.name
            addressText.text = buildString {
                append(pharmacy.address)
                if (pharmacy.city.isNotEmpty()) append(", ${pharmacy.city}")
            }
            ratingText.text = if (pharmacy.rating > 0) String.format("%.1f", pharmacy.rating) else "—"
            hoursText.text = pharmacy.operatingHours.ifEmpty { "Hours not listed" }

            if (pharmacy.isDeliveryAvailable) {
                deliveryBadge.visibility = View.VISIBLE
                if (pharmacy.estimatedDeliveryTime.isNotEmpty()) {
                    estimatedDeliveryText.visibility = View.VISIBLE
                    estimatedDeliveryText.text = "~${pharmacy.estimatedDeliveryTime}"
                }
            } else {
                deliveryBadge.visibility = View.GONE
                estimatedDeliveryText.visibility = View.GONE
            }

            selectButton.setOnClickListener { onSelect(pharmacy) }
        }
    }
}

