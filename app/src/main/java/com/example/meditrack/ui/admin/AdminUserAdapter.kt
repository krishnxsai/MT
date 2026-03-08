package com.example.meditrack.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ItemAdminUserBinding

class AdminUserAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, AdminUserAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemAdminUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(
        private val binding: ItemAdminUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.userName.text = user.displayName.ifEmpty { "No Name" }
            binding.userEmail.text = user.email

            val context = binding.root.context
            when (user.role) {
                UserRole.PATIENT -> {
                    binding.roleChip.text = context.getString(R.string.patient)
                    binding.roleChip.setChipBackgroundColorResource(R.color.success_container)
                    binding.userAvatar.setImageResource(R.drawable.ic_person)
                }
                UserRole.DOCTOR -> {
                    binding.roleChip.text = context.getString(R.string.doctor)
                    binding.roleChip.setChipBackgroundColorResource(R.color.primary_container)
                    binding.userAvatar.setImageResource(R.drawable.ic_doctor)
                }
                UserRole.ADMIN -> {
                    binding.roleChip.text = context.getString(R.string.admin)
                    binding.roleChip.setChipBackgroundColorResource(R.color.warning_container)
                    binding.userAvatar.setImageResource(R.drawable.ic_shield)
                }
                UserRole.PHARMACY -> {
                    binding.roleChip.text = context.getString(R.string.pharmacy_store)
                    binding.roleChip.setChipBackgroundColorResource(R.color.secondary_container)
                    binding.userAvatar.setImageResource(R.drawable.ic_medicine)
                }
            }

            binding.root.setOnClickListener { onUserClick(user) }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}

