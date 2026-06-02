package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom

class RoomPagerAdapter(
    fragment: Fragment,
    private val rooms: List<InspectionRoom>,
    private val viewModel: InspectionViewModel,
    private val phase: InspectionPhase,
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = rooms.size

    override fun createFragment(position: Int): Fragment {
        val room = rooms[position]
        return RoomChecklistFragment().apply {
            arguments = Bundle().apply {
                putString("propertyId", room.propertyId)
                putString("roomId", room.id)
                putString("phase", phase.name)
            }
        }
    }

    override fun getItemId(position: Int): Long = rooms[position].id.hashCode().toLong()
    override fun containsItem(itemId: Long): Boolean = rooms.any { it.id.hashCode().toLong() == itemId }
}
