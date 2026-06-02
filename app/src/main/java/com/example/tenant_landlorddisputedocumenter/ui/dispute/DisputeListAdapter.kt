package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemDisputeBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus

class DisputeListAdapter(
    private val onResolve: (Dispute) -> Unit,
) : ListAdapter<Dispute, DisputeListAdapter.Holder>(Diff()) {

    private var itemNames: Map<String, String> = emptyMap()

    fun updateItemNames(names: Map<String, String>) {
        itemNames = names
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDisputeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), itemNames, onResolve)
    }

    class Holder(private val binding: ItemDisputeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(dispute: Dispute, itemNames: Map<String, String>, onResolve: (Dispute) -> Unit) {
            val label = itemNames[dispute.itemId] ?: dispute.itemId
            binding.textItemLabel.text = label
            binding.textReason.text = dispute.reason
            binding.chipStatus.text = dispute.status.name
            val ctx = binding.root.context
            val chipBg = if (dispute.status == DisputeStatus.OPEN) {
                R.color.chip_pending_bg
            } else {
                R.color.chip_occupied_bg
            }
            binding.chipStatus.setChipBackgroundColorResource(chipBg)
            val open = dispute.status == DisputeStatus.OPEN
            binding.buttonResolve.visibility = if (open) View.VISIBLE else View.GONE
            binding.buttonResolve.setOnClickListener { if (open) onResolve(dispute) }
        }
    }

    private class Diff : DiffUtil.ItemCallback<Dispute>() {
        override fun areItemsTheSame(oldItem: Dispute, newItem: Dispute) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Dispute, newItem: Dispute) = oldItem == newItem
    }
}
