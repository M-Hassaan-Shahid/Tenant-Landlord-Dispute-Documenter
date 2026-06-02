package com.example.tenant_landlorddisputedocumenter.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemPropertyBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyStatusUi

class PropertyAdapter(
    private val currentUserId: String,
    private val onClick: (Property) -> Unit,
) : ListAdapter<Property, PropertyAdapter.PropertyViewHolder>(PropertyDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val binding = ItemPropertyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return PropertyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        holder.bind(getItem(position), currentUserId, onClick)
    }

    class PropertyViewHolder(private val binding: ItemPropertyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(property: Property, currentUserId: String, onClick: (Property) -> Unit) {
            binding.textAddress.text = property.address
            PropertyStatusUi.apply(binding.chipStatus, property.status)

            val isLandlord = property.landlordId == currentUserId
            val roleLabel = if (isLandlord) "Landlord" else "Tenant"
            val statusHint = when (property.status) {
                PropertyStatus.ACTIVE -> " · Move-in in progress"
                PropertyStatus.OCCUPIED -> " · Lease active"
                PropertyStatus.PENDING_APPROVAL -> " · Approval pending"
                PropertyStatus.PENDING -> " · Awaiting tenant"
                else -> ""
            }
            binding.textRole.text = "$roleLabel$statusHint"

            binding.textRent.text =
                "PKR ${property.rent.toInt()}/mo · Deposit PKR ${property.deposit.toInt()}"

            binding.iconProperty.setImageResource(
                if (isLandlord) R.drawable.ic_home else R.drawable.ic_apartment,
            )

            binding.root.setOnClickListener { onClick(property) }
        }
    }
}

class PropertyDiffCallback : DiffUtil.ItemCallback<Property>() {
    override fun areItemsTheSame(oldItem: Property, newItem: Property) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Property, newItem: Property) = oldItem == newItem
}
