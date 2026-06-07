package com.example.tenant_landlorddisputedocumenter.ui.rooms

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.databinding.ItemRoomBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom
import com.example.tenant_landlorddisputedocumenter.ui.staggerAppear

class RoomAdapter(
    private val onDelete: (InspectionRoom) -> Unit,
) : ListAdapter<InspectionRoom, RoomAdapter.RoomViewHolder>(RoomDiffCallback()) {

    private var deleteEnabled = true

    fun setDeleteEnabled(enabled: Boolean) {
        deleteEnabled = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val binding = ItemRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RoomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        holder.staggerAppear(position)
        holder.bind(getItem(position), deleteEnabled, onDelete)
    }

    class RoomViewHolder(private val binding: ItemRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(room: InspectionRoom, deleteEnabled: Boolean, onDelete: (InspectionRoom) -> Unit) {
            binding.textRoomName.text = room.name
            binding.buttonDeleteRoom.visibility =
                if (deleteEnabled) android.view.View.VISIBLE else android.view.View.GONE
            binding.buttonDeleteRoom.setOnClickListener { if (deleteEnabled) onDelete(room) }
        }
    }
}

class RoomDiffCallback : DiffUtil.ItemCallback<InspectionRoom>() {
    override fun areItemsTheSame(oldItem: InspectionRoom, newItem: InspectionRoom) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: InspectionRoom, newItem: InspectionRoom) = oldItem == newItem
}
