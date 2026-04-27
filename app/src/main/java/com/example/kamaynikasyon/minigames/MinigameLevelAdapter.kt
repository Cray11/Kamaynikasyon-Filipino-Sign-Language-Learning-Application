package com.example.kamaynikasyon.minigames

import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.core.utils.AnimationHelper
import com.google.android.material.card.MaterialCardView

data class PictureQuizLevelItem(
    val levelNumber: Int,
    val title: String,
    val isUnlocked: Boolean,
    val score: Int,
    val stars: Int = 0 // Number of stars achieved (0-3)
)

class PictureQuizLevelAdapter(
    private val items: List<PictureQuizLevelItem>,
    private val onClick: (levelNumber: Int) -> Unit,
    private val themeColor: Int
) : RecyclerView.Adapter<PictureQuizLevelAdapter.LevelVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_level_slot, parent, false)
        return LevelVH(view)
    }

    override fun onBindViewHolder(holder: LevelVH, position: Int) {
        holder.bind(items[position], onClick)
        // Add admin-style staggered slideInUp animation
        AnimationHelper.animateListItem(
            holder.itemView,
            position,
            delayPerItem = 50,
            animationType = "slideInUp"
        )
    }

    override fun getItemCount(): Int = items.size

    inner class LevelVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardLevel: MaterialCardView = itemView.findViewById(R.id.card_level)
        private val llStars: android.widget.LinearLayout = itemView.findViewById(R.id.ll_stars)
        private val ivStar1: android.widget.ImageView = itemView.findViewById(R.id.iv_star_1)
        private val ivStar2: android.widget.ImageView = itemView.findViewById(R.id.iv_star_2)
        private val ivStar3: android.widget.ImageView = itemView.findViewById(R.id.iv_star_3)
        private val textLevelNumber: TextView = itemView.findViewById(R.id.text_level_number)
        private val ivLock: android.widget.ImageView = itemView.findViewById(R.id.iv_lock)

        fun bind(item: PictureQuizLevelItem, onClick: (Int) -> Unit) {
            // Set stars: use filled star drawable for achieved, empty star drawable for not obtained
            val stars = item.stars.coerceIn(0, 3)
            val filledRes = R.drawable.img_star
            val emptyRes = R.drawable.img_emptystar
            val disabledRes = R.drawable.img_disabledstar

            if (!item.isUnlocked) {
                // If locked, show disabled star for all
                ivStar1.setImageResource(disabledRes)
                ivStar2.setImageResource(disabledRes)
                ivStar3.setImageResource(disabledRes)
            } else {
                // Unlocked: use filled or empty drawables without opacity
                ivStar1.setImageResource(if (stars >= 1) filledRes else emptyRes)
                ivStar2.setImageResource(if (stars >= 2) filledRes else emptyRes)
                ivStar3.setImageResource(if (stars >= 3) filledRes else emptyRes)
            }

            // Toggle between level number and lock icon for locked slots
            if (item.isUnlocked) {
                textLevelNumber.visibility = View.VISIBLE
                textLevelNumber.text = "${item.levelNumber}"
                ivLock.visibility = View.GONE
            } else {
                textLevelNumber.visibility = View.GONE
                ivLock.visibility = View.VISIBLE
            }
            val lockedAlpha = if (item.isUnlocked) 1.0f else 0.6f
            textLevelNumber.alpha = lockedAlpha
            ivLock.alpha = lockedAlpha

            // Set card clickability, alpha
            cardLevel.isClickable = item.isUnlocked
            cardLevel.isFocusable = item.isUnlocked
            cardLevel.isEnabled = item.isUnlocked
            cardLevel.alpha = if (item.isUnlocked) 1.0f else 0.85f

            // Convert dp to px for elevation and stroke
            val elevationPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, itemView.resources.displayMetrics)
            val strokePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, itemView.resources.displayMetrics).toInt()
            // Apply elevation
            cardLevel.cardElevation = elevationPx

            // Determine background color: unlocked uses themeColor; locked uses a slightly grayed blend of themeColor
            val bgColor = if (item.isUnlocked) {
                themeColor
            } else {
                // blend with gray to desaturate but keep hue
                ColorUtils.blendARGB(themeColor, android.graphics.Color.GRAY, 0.25f)
            }
            cardLevel.setCardBackgroundColor(bgColor)

            // Apply stroke (width + color) after background so it remains visible on top
            cardLevel.setStrokeWidth(strokePx)
            cardLevel.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE))

            // Ensure the view updates so stroke appears
            cardLevel.invalidate()

            // Use card ripple color instead of setting a foreground (foreground can obscure stroke)
            if (item.isUnlocked) {
                val highlight = TypedValue()
                // Resolve the theme highlight color (colorControlHighlight) for ripple
                val resolved = itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, highlight, true)
                val rippleColor = if (resolved) {
                    android.content.res.ColorStateList.valueOf(highlight.data)
                } else {
                    // fallback to a semi-transparent white ripple
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FFFFFF"))
                }
                try {
                    cardLevel.rippleColor = rippleColor
                } catch (e: Exception) {
                    // if rippleColor setter isn't available, ignore and continue
                }
            } else {
                try {
                    cardLevel.rippleColor = null
                } catch (_: Exception) {
                }
            }

            // Set click listener only for unlocked levels
            if (item.isUnlocked) {
                cardLevel.setOnClickListener {
                    onClick(item.levelNumber)
                }
            } else {
                cardLevel.setOnClickListener(null)
            }
        }
    }
}


