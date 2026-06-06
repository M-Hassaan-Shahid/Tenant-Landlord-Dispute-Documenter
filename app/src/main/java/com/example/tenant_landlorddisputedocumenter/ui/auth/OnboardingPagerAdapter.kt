package com.example.tenant_landlorddisputedocumenter.ui.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.databinding.ItemOnboardingPageBinding

data class OnboardingPage(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

class OnboardingPagerAdapter(
    private val pages: List<OnboardingPage>,
) : RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    class PageViewHolder(
        private val binding: ItemOnboardingPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: OnboardingPage) {
            binding.iconFeature.setImageResource(page.iconRes)
            binding.textFeatureTitle.setText(page.titleRes)
            binding.textFeatureBody.setText(page.bodyRes)
        }
    }
}
