package com.example.tenant_landlorddisputedocumenter.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemPropertyBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.ui.staggerAppear
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyRoleUi
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyStatusUi

class PropertyAdapter(
    private val currentUserId: String,
    private val onClick: (Property) -> Unit,
) : ListAdapter<Property, PropertyAdapter.PropertyViewHolder>(PropertyDiffCallback()) {

    /** uid -> display name, set by the fragment as cached profiles load. */
    private var partyNames: Map<String, String> = emptyMap()

    fun setPartyNames(names: Map<String, String>) {
        if (names == partyNames) return
        partyNames = names
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PropertyViewHolder {
        val binding = ItemPropertyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return PropertyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PropertyViewHolder, position: Int) {
        holder.bind(getItem(position), currentUserId, partyNames, onClick)
        holder.staggerAppear(position)
    }

    class PropertyViewHolder(private val binding: ItemPropertyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            property: Property,
            currentUserId: String,
            partyNames: Map<String, String>,
            onClick: (Property) -> Unit,
        ) {
            binding.textAddress.text = property.address
            PropertyStatusUi.apply(binding.chipStatus, property.status)

            val isLandlord = property.landlordId == currentUserId
            val roleLabel = if (isLandlord) "Landlord" else "Tenant"
            val statusHint = PropertyRoleUi.propertyCardHint(property, currentUserId)
            binding.textRole.text = "$roleLabel$statusHint"

            val counterpartyId = if (isLandlord) property.tenantId else property.landlordId
            val counterpartyName = counterpartyId?.let { partyNames[it] }?.takeIf { it.isNotBlank() }
            if (counterpartyName != null) {
                val label = if (isLandlord) "Tenant" else "Landlord"
                binding.textParty.text = "$label: $counterpartyName"
                binding.textParty.visibility = android.view.View.VISIBLE
            } else {
                binding.textParty.visibility = android.view.View.GONE
            }

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
