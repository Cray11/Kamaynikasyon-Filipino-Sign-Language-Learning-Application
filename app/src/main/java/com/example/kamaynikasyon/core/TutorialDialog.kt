package com.example.kamaynikasyon.core

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.DialogTutorialBinding

data class TutorialPage(
    val iconRes: Int,
    val title: String,
    val description: String,
    val iconPath: String? = null  // Optional asset path for images
)

class TutorialDialog(
    context: Context,
    private val pages: List<TutorialPage>,
    private val onDismiss: (dontShowAgain: Boolean) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogTutorialBinding
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure dialog window
        window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.5f)
            setElevation(0f)
            // Set dialog width to be 90% of screen width
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        setupViewPager()
        setupProgressBar()
        setupButtons()
        updateButtons() // Initialize button states
    }

    private fun setupViewPager() {
        val adapter = TutorialPageAdapter(pages, context)
        binding.viewPagerTutorial.adapter = adapter

        binding.viewPagerTutorial.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
                updateProgressBar()
                updateButtons()
                updateDialogContent(position)
            }
        })
        
        // Set initial content
        updateDialogContent(0)
    }
    
    private fun updateDialogContent(position: Int) {
        val page = pages[position]
        binding.tvTutorialTitle.text = page.title
        binding.tvTutorialDescription.text = page.description
    }

    private fun setupProgressBar() {
        binding.progressBar.max = 100
        updateProgressBar()
    }

    private fun updateProgressBar() {
        val totalPages = pages.size
        if (totalPages > 0) {
            val progress = ((currentPage + 1) * 100) / totalPages
            binding.progressBar.progress = progress
        }
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener {
            dismiss()
            onDismiss(binding.checkboxDontShowAgain.isChecked)
        }

        binding.btnPrev.setOnClickListener {
            if (currentPage > 0) {
                binding.viewPagerTutorial.currentItem = currentPage - 1
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentPage < pages.size - 1) {
                binding.viewPagerTutorial.currentItem = currentPage + 1
            } else {
                dismiss()
                onDismiss(binding.checkboxDontShowAgain.isChecked)
            }
        }
    }

    private fun updateButtons() {
        binding.btnNext.text = if (currentPage == pages.size - 1) "Got it" else "Next"
        // Hide Previous button on first page, show it on other pages
        binding.btnPrev.visibility = if (currentPage == 0) View.GONE else View.VISIBLE
    }

    private class TutorialPageAdapter(
        private val pages: List<TutorialPage>,
        private val dialogContext: Context
    ) : RecyclerView.Adapter<TutorialPageAdapter.TutorialPageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TutorialPageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tutorial_page, parent, false)
            return TutorialPageViewHolder(view, dialogContext)
        }

        override fun onBindViewHolder(holder: TutorialPageViewHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount(): Int = pages.size

        class TutorialPageViewHolder(itemView: View, private val dialogContext: Context) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.iv_tutorial_icon)
            private val titleView: TextView = itemView.findViewById(R.id.tv_tutorial_page_title)
            private val descriptionView: TextView = itemView.findViewById(R.id.tv_tutorial_page_description)

            fun bind(page: TutorialPage) {
                // Load from asset path if provided, otherwise use resource ID
                if (!page.iconPath.isNullOrBlank()) {
                    try {
                        val normalizedPath = page.iconPath.removePrefix("file:///android_asset/")
                        Log.d("TutorialDialog", "Attempting to load image from assets: $normalizedPath")
                        dialogContext.assets.open(normalizedPath).use { inputStream ->
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            if (bitmap != null) {
                                iconView.setImageBitmap(bitmap)
                                Log.d("TutorialDialog", "Successfully loaded image: $normalizedPath")
                            } else {
                                Log.e("TutorialDialog", "Bitmap is null for path: $normalizedPath")
                                iconView.setImageResource(page.iconRes)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TutorialDialog", "Failed to load image from assets: ${page.iconPath}", e)
                        iconView.setImageResource(page.iconRes)
                    }
                } else {
                    iconView.setImageResource(page.iconRes)
                }
                titleView.text = page.title
                descriptionView.text = page.description
            }
        }
    }
}
