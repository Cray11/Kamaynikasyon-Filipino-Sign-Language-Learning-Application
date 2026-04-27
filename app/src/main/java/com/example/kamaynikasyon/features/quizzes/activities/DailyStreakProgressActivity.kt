package com.example.kamaynikasyon.features.quizzes.activities

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.example.kamaynikasyon.R
import com.example.kamaynikasyon.databinding.ActivityDailyStreakProgressBinding
import com.example.kamaynikasyon.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar as JavaCalendar

class DailyStreakProgressActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDailyStreakProgressBinding
    private lateinit var allProgressMap: Map<String, com.example.kamaynikasyon.data.database.DailyStreakProgress>
    private val today = LocalDate.now()
    private var calendarEndDate: LocalDate = today.plusMonths(1) // Default fallback
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyStreakProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        loadProgressData()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide the default title
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun loadProgressData() {
        lifecycleScope.launch {
            // Load all progress data for calendar (6 months back, 1 month forward from user's start month)
            allProgressMap = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val dao = db.dailyStreakProgressDao()
                
                // Get user's start month (earliest date from progress, or use today if none)
                val earliestDateStr = dao.getEarliestDate()
                val userStartDate = if (earliestDateStr != null) {
                    LocalDate.parse(earliestDateStr)
                } else {
                    today
                }
                
                // Calculate end date: 1 month from the start of user's start month
                val userStartMonth = userStartDate.withDayOfMonth(1)
                val endDate = userStartMonth.plusMonths(1).minusDays(1) // Last day of the month after start month
                
                // Ensure end date is not before today (in case user started long ago)
                val finalEndDate = if (endDate.isBefore(today)) {
                    today.plusMonths(1) // Fallback to 1 month from today
                } else {
                    endDate
                }
                
                // Store the end date for use in setupCalendar
                calendarEndDate = finalEndDate
                
                val startDate = today.minusMonths(6)
                val dateRange = mutableListOf<String>()
                var current = startDate
                while (!current.isAfter(finalEndDate)) {
                    dateRange.add(current.toString())
                    current = current.plusDays(1)
                }
                val allProgresses = dao.getByDates(dateRange)
                allProgresses.associateBy { it.date }
            }
            
            setupCalendar()
        }
    }
    
    private fun setupCalendar() {
        val calendarView = binding.materialCalendarView
        
        // Style the calendar programmatically (only use methods that exist)
        try {
            // Set header colors if available
            calendarView.setHeaderColor(ContextCompat.getColor(this, R.color.primary_color))
            calendarView.setHeaderLabelColor(ContextCompat.getColor(this, R.color.background_white))
        } catch (e: Exception) {
            // Some methods might not be available in this version, continue without styling
            android.util.Log.w("DailyStreakProgress", "Could not apply calendar styles: ${e.message}")
        }
        
        // Create events for progress indicators with emoji drawables
        val events = mutableListOf<EventDay>()
        
        // Create emoji drawables programmatically
        val emojiCompletedDrawable = createEmojiDrawable("✅")
        val emojiMissedDrawable = createEmojiDrawable("❌")
        val emojiTodayDrawable = createEmojiDrawable("🗓️")
        
        allProgressMap.forEach { (dateStr, progress) ->
            val date = LocalDate.parse(dateStr)
            val calendar = JavaCalendar.getInstance()
            calendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
            
            // Determine emoji drawable based on status
            val emojiDrawable = when {
                progress.completed -> emojiCompletedDrawable
                date.isBefore(today) -> emojiMissedDrawable
                date.isEqual(today) -> emojiTodayDrawable
                else -> null
            }
            
            if (emojiDrawable != null) {
                events.add(EventDay(calendar, emojiDrawable))
            }
        }
        
        // Also add events for all dates in range to show emojis even without progress
        // Use the end date calculated in loadProgressData
        val startDate = today.minusMonths(6)
        var current = startDate
        while (!current.isAfter(calendarEndDate)) {
            val dateStr = current.toString()
            if (!allProgressMap.containsKey(dateStr)) {
                val calendar = JavaCalendar.getInstance()
                calendar.set(current.year, current.monthValue - 1, current.dayOfMonth)
                
                val emojiDrawable = when {
                    current.isBefore(today) -> emojiMissedDrawable
                    current.isEqual(today) -> emojiTodayDrawable
                    else -> null
                }
                
                if (emojiDrawable != null) {
                    events.add(EventDay(calendar, emojiDrawable))
                }
            }
            current = current.plusDays(1)
        }
        
        // Set events to calendar
        calendarView.setEvents(events)
        
        // Set calendar to current month
        val currentCalendar = JavaCalendar.getInstance()
        currentCalendar.set(today.year, today.monthValue - 1, today.dayOfMonth)
        calendarView.setDate(currentCalendar)
        
        // Function to update status text
        fun updateStatusText(selectedDate: LocalDate) {
            val entry = allProgressMap[selectedDate.toString()]
            val status = when {
                entry?.completed == true -> "✅ Completed"
                selectedDate.isEqual(today) -> "🗓️ Today (pending)"
                selectedDate.isBefore(today) -> "❌ Missed"
                else -> "⏳ Future"
            }
            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(java.sql.Date.valueOf(selectedDate.toString()))
            binding.tvDateStatus.text = "$dateStr\n$status"
        }
        
        // Set initial status for today's date
        updateStatusText(today)

        // Handle date selection (read-only - just shows status)
        calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                val calendar = eventDay.calendar
                val sel = LocalDate.of(
                    calendar.get(JavaCalendar.YEAR), 
                    calendar.get(JavaCalendar.MONTH) + 1, 
                    calendar.get(JavaCalendar.DAY_OF_MONTH)
                )
                updateStatusText(sel)
            }
        })
    }
    
    private fun createEmojiDrawable(emoji: String): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // Smaller emoji size
                textSize = 24f * resources.displayMetrics.density
                textAlign = Paint.Align.CENTER
                isFakeBoldText = false
            }
            
            override fun draw(canvas: Canvas) {
                val bounds = bounds
                val x = bounds.centerX().toFloat()
                // Position emoji lower in the bounds to add space below day number
                val y = bounds.bottom.toFloat() - (10 * resources.displayMetrics.density)
                canvas.drawText(emoji, x, y, paint)
            }
            
            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }
            
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
                paint.colorFilter = colorFilter
            }
            
            override fun getOpacity(): Int {
                return android.graphics.PixelFormat.TRANSLUCENT
            }
            
            override fun getIntrinsicWidth(): Int {
                return (20 * resources.displayMetrics.density).toInt()
            }
            
            override fun getIntrinsicHeight(): Int {
                // Make drawable taller to accommodate spacing below day number
                return (32 * resources.displayMetrics.density).toInt()
            }
        }
    }
}

