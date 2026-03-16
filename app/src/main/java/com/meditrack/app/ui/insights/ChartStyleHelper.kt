package com.meditrack.app.ui.insights

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.meditrack.app.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for creating beautiful, Material Design 3 styled charts
 * Optimized for medical data visualization with accessibility in mind
 */
object ChartStyleHelper {

    // Medical-grade color palette - Clean, modern, accessible
    object ChartColors {
        // Heart Rate - coral red
        val heartRatePrimary = Color.parseColor("#EF4444")
        val heartRateFill = Color.parseColor("#FEF2F2")

        // Blood Pressure Systolic - medical blue
        val bpSystolicPrimary = Color.parseColor("#3B82F6")
        val bpSystolicFill = Color.parseColor("#EFF6FF")

        // Blood Pressure Diastolic - cyan
        val bpDiastolicPrimary = Color.parseColor("#06B6D4")
        val bpDiastolicFill = Color.parseColor("#ECFEFF")

        // Glucose - purple
        val glucosePrimary = Color.parseColor("#8B5CF6")
        val glucoseFill = Color.parseColor("#F5F3FF")

        // Weight - green
        val weightPrimary = Color.parseColor("#22C55E")
        val weightFill = Color.parseColor("#F0FDF4")

        // General chart colors - cleaner look
        val gridLine = Color.parseColor("#F3F4F6")
        val axisLabel = Color.parseColor("#6B7280")
        val legendText = Color.parseColor("#111827")
        val highlightColor = Color.parseColor("#059669")
        val noDataText = Color.parseColor("#9CA3AF")
        val background = Color.parseColor("#FFFFFF")
    }

    /**
     * Chart type enumeration for easy styling
     */
    enum class ChartType {
        HEART_RATE,
        BLOOD_PRESSURE,
        GLUCOSE,
        WEIGHT
    }

    /**
     * Apply modern Material Design 3 styling to a LineChart
     */
    fun styleLineChart(
        chart: LineChart,
        context: Context,
        chartType: ChartType,
        onValueSelected: ((Entry?, Int) -> Unit)? = null
    ) {
        chart.apply {
            // Basic chart configuration
            description.isEnabled = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            // Enable touch interactions
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false

            // Remove extra padding
            setViewPortOffsets(48f, 24f, 24f, 48f)
            extraBottomOffset = 8f
            extraTopOffset = 8f

            // Configure legend with Material Design styling
            legend.apply {
                isEnabled = true
                textColor = ChartColors.legendText
                textSize = 12f
                form = Legend.LegendForm.CIRCLE
                formSize = 10f
                xEntrySpace = 16f
                yEntrySpace = 8f
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }

            // Style X-axis (bottom)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ChartColors.gridLine
                axisLineWidth = 1f
                textColor = ChartColors.axisLabel
                textSize = 11f
                granularity = 1f
                setLabelCount(5, false)
                yOffset = 8f
            }

            // Style Y-axis (left)
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ChartColors.gridLine
                gridLineWidth = 0.5f
                enableGridDashedLine(8f, 4f, 0f)
                setDrawAxisLine(false)
                textColor = ChartColors.axisLabel
                textSize = 11f
                setLabelCount(5, true)
                xOffset = 8f

                // Add limit lines for normal ranges based on chart type
                addNormalRangeLimitLines(this, chartType, context)
            }

            // Disable right Y-axis
            axisRight.isEnabled = false

            // Configure highlight
            isHighlightPerTapEnabled = true
            isHighlightPerDragEnabled = true

            // Set no data text styling
            setNoDataText("No data available")
            setNoDataTextColor(ChartColors.noDataText)

            // Value selected listener
            onValueSelected?.let { callback ->
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        e?.let { entry ->
                            callback(entry, h?.dataSetIndex ?: 0)
                        }
                    }

                    override fun onNothingSelected() {
                        callback(null, -1)
                    }
                })
            }
        }
    }

    /**
     * Add normal range limit lines to help users understand their readings
     */
    private fun addNormalRangeLimitLines(axis: YAxis, chartType: ChartType, context: Context) {
        axis.removeAllLimitLines()

        when (chartType) {
            ChartType.HEART_RATE -> {
                // Normal resting heart rate: 60-100 bpm
                addLimitLine(axis, 60f, "Low", ContextCompat.getColor(context, R.color.info))
                addLimitLine(axis, 100f, "High", ContextCompat.getColor(context, R.color.warning))
            }
            ChartType.BLOOD_PRESSURE -> {
                // Normal BP: <120/<80, Elevated: 120-129, High: >=130
                addLimitLine(axis, 120f, "Normal", ContextCompat.getColor(context, R.color.success))
                addLimitLine(axis, 140f, "High", ContextCompat.getColor(context, R.color.error))
            }
            ChartType.GLUCOSE -> {
                // Normal fasting glucose: 70-100 mg/dL
                addLimitLine(axis, 70f, "Low", ContextCompat.getColor(context, R.color.info))
                addLimitLine(axis, 100f, "Pre-diabetic", ContextCompat.getColor(context, R.color.warning))
                addLimitLine(axis, 126f, "Diabetic", ContextCompat.getColor(context, R.color.error))
            }
            ChartType.WEIGHT -> {
                // No standard limit lines for weight
            }
        }
    }

    private fun addLimitLine(axis: YAxis, value: Float, label: String, color: Int) {
        val limitLine = LimitLine(value, label).apply {
            lineWidth = 1f
            lineColor = color
            enableDashedLine(10f, 8f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            textSize = 9f
            textColor = color
        }
        axis.addLimitLine(limitLine)
    }

    /**
     * Create a beautifully styled LineDataSet with gradient fill
     */
    fun createStyledDataSet(
        entries: List<Entry>,
        label: String,
        chartType: ChartType,
        isSecondary: Boolean = false
    ): LineDataSet {
        val primaryColor = getColorForType(chartType, isSecondary)

        return LineDataSet(entries, label).apply {
            // Line styling
            color = primaryColor
            lineWidth = 2.5f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.15f

            // Circle/point styling
            setDrawCircles(entries.size <= 15)
            setCircleColor(primaryColor)
            circleRadius = 4f
            circleHoleRadius = 2.5f
            circleHoleColor = Color.WHITE

            // Fill gradient
            setDrawFilled(true)
            fillColor = primaryColor
            fillAlpha = 80

            // Highlight styling
            highLightColor = ChartColors.highlightColor
            setDrawHighlightIndicators(true)
            highlightLineWidth = 1.5f
            enableDashedHighlightLine(6f, 4f, 0f)

            // Value labels
            setDrawValues(false) // We'll show values on tap instead

            // Horizontal highlight only
            setDrawHorizontalHighlightIndicator(false)
            setDrawVerticalHighlightIndicator(true)
        }
    }

    private fun getColorForType(chartType: ChartType, isSecondary: Boolean): Int {
        return when (chartType) {
            ChartType.HEART_RATE -> ChartColors.heartRatePrimary
            ChartType.BLOOD_PRESSURE -> if (isSecondary) {
                ChartColors.bpDiastolicPrimary
            } else {
                ChartColors.bpSystolicPrimary
            }
            ChartType.GLUCOSE -> ChartColors.glucosePrimary
            ChartType.WEIGHT -> ChartColors.weightPrimary
        }
    }

    /**
     * Create a date formatter for X-axis labels
     */
    fun createDateFormatter(dates: List<Date>): ValueFormatter {
        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        return object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < dates.size) {
                    dateFormat.format(dates[index])
                } else ""
            }
        }
    }

    /**
     * Animate chart with smooth entry animation
     */
    fun animateChart(chart: LineChart, durationMs: Int = 800) {
        chart.animateX(durationMs, com.github.mikephil.charting.animation.Easing.EaseOutCubic)
    }

    /**
     * Animate chart refresh when data changes (for filter changes)
     */
    fun animateChartRefresh(chart: LineChart, newData: LineData, durationMs: Int = 500) {
        // Fade out
        chart.animate()
            .alpha(0.3f)
            .setDuration(durationMs / 2L)
            .withEndAction {
                // Update data
                chart.data = newData
                chart.notifyDataSetChanged()

                // Fade in with animation
                chart.alpha = 0.3f
                chart.animate()
                    .alpha(1f)
                    .setDuration(durationMs / 2L)
                    .start()

                animateChart(chart, durationMs)
            }
            .start()
    }

    /**
     * Format value for display in tooltip/marker
     */
    fun formatValue(value: Float, chartType: ChartType): String {
        return when (chartType) {
            ChartType.HEART_RATE -> "${value.toInt()} bpm"
            ChartType.BLOOD_PRESSURE -> "${value.toInt()} mmHg"
            ChartType.GLUCOSE -> "${value.toInt()} mg/dL"
            ChartType.WEIGHT -> String.format(Locale.getDefault(), "%.1f kg", value)
        }
    }

    /**
     * Get trend icon resource based on data trend
     */
    fun getTrendIconRes(trend: String): Int {
        return when (trend.lowercase()) {
            "increasing" -> R.drawable.ic_trending_up
            "decreasing" -> R.drawable.ic_trending_down
            else -> R.drawable.ic_trending_flat
        }
    }

    /**
     * Get trend color based on chart type and direction
     * (For some metrics like weight/BP, decreasing might be good)
     */
    fun getTrendColor(trend: String, chartType: ChartType, context: Context): Int {
        val isPositiveTrend = when (chartType) {
            ChartType.WEIGHT -> trend.lowercase() == "decreasing" || trend.lowercase() == "stable"
            ChartType.BLOOD_PRESSURE -> trend.lowercase() == "decreasing" || trend.lowercase() == "stable"
            ChartType.GLUCOSE -> trend.lowercase() == "stable"
            ChartType.HEART_RATE -> trend.lowercase() == "stable"
        }

        return when {
            trend.lowercase() == "stable" -> ContextCompat.getColor(context, R.color.info)
            isPositiveTrend -> ContextCompat.getColor(context, R.color.success)
            else -> ContextCompat.getColor(context, R.color.warning)
        }
    }
}

