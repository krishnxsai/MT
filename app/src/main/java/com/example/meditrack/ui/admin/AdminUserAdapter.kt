package com.example.meditrack.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meditrack.R
import com.example.meditrack.data.model.AccountStatus
import com.example.meditrack.data.model.User
import com.example.meditrack.data.model.UserRole
import com.example.meditrack.databinding.ItemAdminUserBinding

class AdminUserAdapter(
    private val onUserClick: (User) -> Unit,
    private val onApprove: ((User) -> Unit)? = null,
    private val onReject: ((User) -> Unit)? = null,
    private val onSuspend: ((User) -> Unit)? = null,
    private val onReactivate: ((User) -> Unit)? = null
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

            // ── Role chip ──
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

            // ── Status chip ──
            val statusChip = binding.statusChip
            when (user.status) {
                AccountStatus.PENDING -> {
                    statusChip.text = context.getString(R.string.status_pending)
                    statusChip.setChipBackgroundColorResource(R.color.warning_container)
                    statusChip.visibility = View.VISIBLE
                }
                AccountStatus.APPROVED -> {
                    statusChip.text = context.getString(R.string.status_approved)
                    statusChip.setChipBackgroundColorResource(R.color.success_container)
                    statusChip.visibility = View.VISIBLE
                }
                AccountStatus.REJECTED -> {
                    statusChip.text = context.getString(R.string.status_rejected)
                    statusChip.setChipBackgroundColorResource(R.color.error_container)
                    statusChip.visibility = View.VISIBLE
                }
                AccountStatus.SUSPENDED -> {
                    statusChip.text = context.getString(R.string.status_suspended)
                    statusChip.setChipBackgroundColorResource(R.color.error_container)
                    statusChip.visibility = View.VISIBLE
                }
            }

            // ── Action buttons ──
            val showApprove = user.status == AccountStatus.PENDING && onApprove != null
            val showReject = user.status == AccountStatus.PENDING && onReject != null
            val showSuspend = user.status == AccountStatus.APPROVED && user.role != UserRole.ADMIN && onSuspend != null
            val showReactivate = (user.status == AccountStatus.SUSPENDED || user.status == AccountStatus.REJECTED) && onReactivate != null

            binding.approveButton.visibility = if (showApprove) View.VISIBLE else View.GONE
            binding.rejectButton.visibility = if (showReject) View.VISIBLE else View.GONE
            binding.suspendButton.visibility = if (showSuspend) View.VISIBLE else View.GONE
            binding.reactivateButton.visibility = if (showReactivate) View.VISIBLE else View.GONE

            binding.actionButtonsLayout.visibility =
                if (showApprove || showReject || showSuspend || showReactivate) View.VISIBLE else View.GONE

            binding.approveButton.setOnClickListener { onApprove?.invoke(user) }
            binding.rejectButton.setOnClickListener { onReject?.invoke(user) }
            binding.suspendButton.setOnClickListener { onSuspend?.invoke(user) }
            binding.reactivateButton.setOnClickListener { onReactivate?.invoke(user) }

            binding.root.setOnClickListener { onUserClick(user) }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.uid == newItem.uid
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}

