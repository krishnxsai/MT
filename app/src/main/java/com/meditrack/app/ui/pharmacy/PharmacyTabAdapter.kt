package com.meditrack.app.ui.pharmacy

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class PharmacyTabAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val ordersFragment = PharmacyOrdersFragment.newInstance()
    private val inventoryFragment = PharmacyInventoryFragment.newInstance()
    private val analyticsFragment = PharmacyAnalyticsFragment.newInstance()

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ordersFragment
            1 -> inventoryFragment
            2 -> analyticsFragment
            else -> ordersFragment
        }
    }

    fun getOrdersFragment(): PharmacyOrdersFragment = ordersFragment
}
