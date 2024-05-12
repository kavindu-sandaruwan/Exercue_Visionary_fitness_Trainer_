package com.example.poseexercise.views.fragment

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.poseexercise.R
import com.example.poseexercise.data.database.AppRepository
import com.example.poseexercise.data.results.WorkoutResult
import com.example.poseexercise.util.MemoryManagement
import com.example.poseexercise.util.MyApplication
import com.example.poseexercise.viewmodels.ResultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Profile view with the information on workout for a week
 */
class ProfileFragment : Fragment(), MemoryManagement {
    // Declare variables for ViewModel, Chart, UI components, and data
    private lateinit var resultViewModel: ResultViewModel
    private var workoutResults: List<WorkoutResult>? = null
    private lateinit var workOutTime: TextView
    private lateinit var appRepository: AppRepository
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize ViewModel, Chart, and UI components
        resultViewModel = ResultViewModel(MyApplication.getInstance())
        workOutTime = view.findViewById(R.id.total_time)
        appRepository = AppRepository(requireActivity().application)
        // Load data and set up the chart
        loadDataAndSetupChart()
    }

    private fun loadDataAndSetupChart() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Fetch workout results asynchronously
            workoutResults = resultViewModel.getAllResult()
            // Filter workout results for the current week
            val currentWeek = getCurrentCalendarWeek()
            workoutResults = workoutResults?.filter {
                getCalendarWeek(it.timestamp) == currentWeek
            }
            val decimalFormat = DecimalFormat("#.##")
            val totalWorkoutTimeForCurrentWeek =
                workoutResults?.let { calculateTotalWorkoutTimeForWeek(it, currentWeek) }
            val formattedWorkoutTime = decimalFormat.format(totalWorkoutTimeForCurrentWeek)

            // Observe exercise plans from the database
            withContext(Dispatchers.Main) {
                appRepository.allPlans.observe(viewLifecycleOwner) { exercisePlans ->
                    // Calculate progress and update UI
                    val totalPlannedRepetitions = exercisePlans.sumOf { it.repeatCount }
                    val totalCompletedRepetitions = workoutResults?.sumOf { it.repeatedCount } ?: 0
                    val progressPercentage =
                        if (totalPlannedRepetitions != 0) {
                            (totalCompletedRepetitions.toDouble() / totalPlannedRepetitions) * 100
                        } else {
                            0.0
                        }

                    // Update the TextView with the formatted workout time
                    workOutTime.text = formattedWorkoutTime

                    val totalCaloriesPerDay =
                        workoutResults?.let { calculateTotalCaloriesPerDay(it) }

                    // Update the ProgressBar and TextView with the progress percentage
                    updateProgressViews(progressPercentage)
                }
            }
        }
    }

    // Function to update progress views (ProgressBar and TextView)
    private fun updateProgressViews(progressPercentage: Double) {
        // Update progress views (ProgressBar and TextView)
        val cappedProgress = min(progressPercentage, 110.0)

        val progressBar = view?.findViewById<ProgressBar>(R.id.progress_bar)
        val progressTextView = view?.findViewById<TextView>(R.id.percentage)

        progressBar?.progress = cappedProgress.toInt()
        progressTextView?.text = String.format("%.2f%%", cappedProgress)
    }

    // Function to get the current calendar week
    private fun getCurrentCalendarWeek(): Int {
        val calendar = Calendar.getInstance(Locale.getDefault())
        // Set the first day of the week to Sunday
        calendar.firstDayOfWeek = Calendar.SUNDAY
        // Get the week of the year
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    // Function to calculate total workout time for a specific week
    private fun calculateTotalWorkoutTimeForWeek(
        workoutResults: List<WorkoutResult>,
        targetWeek: Int
    ): Double {
        return workoutResults
            .filter { getCalendarWeek(it.timestamp) == targetWeek }
            .sumOf { it.workoutTimeInMin }
    }

    // Function to get the week of the year from a timestamp
    private fun getCalendarWeek(timestamp: Long): Int {
        val calendar = Calendar.getInstance(Locale.getDefault())
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.WEEK_OF_YEAR)
    }

    // Function to calculate total calories per day from workout results
    private fun calculateTotalCaloriesPerDay(workoutResults: List<WorkoutResult>): Map<String, Double> {
        val totalCaloriesPerDay = mutableMapOf<String, Double>()

        // Initialize entries for each day of the week
        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        for (day in daysOfWeek) {
            totalCaloriesPerDay[day] = 0.0
        }

        for (result in workoutResults) {
            val startDate = getStartOfDay(result.timestamp)
            val key = formatDate(startDate)
            val totalCalories = totalCaloriesPerDay.getOrDefault(key, 0.0) + result.calorie
            totalCaloriesPerDay[key] = totalCalories
        }

        return totalCaloriesPerDay
    }

    // Function to get the start of the day
    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        // Set the calendar to the start of the day
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        return calendar.timeInMillis
    }

    // Function to format a timestamp to a string representing the day of the week
    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    // Function to calculate the total exercise count for the week
    private fun calculateTotalExerciseCountForWeek(workoutResults: List<WorkoutResult>?): Int {
        val totalExerciseCount = workoutResults?.count { result ->
            val startDate = getStartOfDay(result.timestamp)
            val dayOfWeek = getDayOfWeek(startDate)
            // returns a value from 1 (Sunday) to 7 (Saturday)
            dayOfWeek in 1..7
        } ?: 0

        return totalExerciseCount
    }

    // Function to get the day of the week from a timestamp
    private fun getDayOfWeek(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.DAY_OF_WEEK)
    }

    // Function to get bar colors based on total calories per week
    private fun getBarColors(totalCaloriesPerWeek: Map<String, Double>): List<Int> {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primaryColor)

        return totalCaloriesPerWeek
            .map { (_, value) -> if (value > 0) primaryColor else Color.TRANSPARENT }
    }

    override fun clearMemory() {
        // Clear memory
        workoutResults = null
    }

    override fun onDestroy() {
        clearMemory()
        super.onDestroy()
    }
}
