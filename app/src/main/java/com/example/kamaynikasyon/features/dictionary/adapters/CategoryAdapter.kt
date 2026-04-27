package com.example.kamaynikasyon.features.dictionary.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.features.dictionary.data.models.CategoryIndex

class CategoryAdapter(
    private val categories: MutableList<CategoryIndex>,
    private val onCategoryClick: (CategoryIndex) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    // Track which positions have been animated to prevent re-animation on scroll
    private val animatedPositions = mutableSetOf<Int>()

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.categoryIcon)
        val name: TextView = itemView.findViewById(R.id.categoryName)
        val description: TextView = itemView.findViewById(R.id.categoryDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        
        holder.icon.text = category.icon
        holder.name.text = category.name
        holder.description.text = category.description
        
        holder.itemView.setOnClickListener {
            onCategoryClick(category)
        }
        
        // Only animate if this position hasn't been animated before
        if (!animatedPositions.contains(position)) {
            animatedPositions.add(position)
            // Reset view state before animating
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 0f
            // Add admin-style staggered slideInUp animation
            com.example.kamaynikasyon.core.utils.AnimationHelper.animateListItem(
                holder.itemView,
                position,
                delayPerItem = 50,
                animationType = "slideInUp"
            )
        } else {
            // Already animated, just ensure it's visible
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<CategoryIndex>) {
        categories.clear()
        categories.addAll(newCategories)
        // Clear animated positions when data is updated
        animatedPositions.clear()
        notifyDataSetChanged()
    }
}
