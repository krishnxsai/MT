package com.example.meditrack.ui.insights

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.TextView
import com.example.meditrack.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom marker view for displaying values when user taps on chart data points
 * Material Design 3 styled with elevation and smooth corners
 */
class ChartMarkerView(
    context: Context,
    private val chartType: ChartStyleHelper.ChartType,
    private val dates: List<Date> = emptyList(),
    private val labelSuffix: String = ""
) : MarkerView(context, R.layout.view_chart_marker) {

    private val valueText: TextView = findViewById(R.id.markerValue)
    private val dateText: TextView = findViewById(R.id.markerDate)
    private val accentView: View? = findViewById(R.id.markerAccent)
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    init {
        // Apply color based on chart type
        val accentColor = when (chartType) {
            ChartStyleHelper.ChartType.HEART_RATE -> ChartStyleHelper.ChartColors.heartRatePrimary
            ChartStyleHelper.ChartType.BLOOD_PRESSURE -> ChartStyleHelper.ChartColors.bpSystolicPrimary
            ChartStyleHelper.ChartType.GLUCOSE -> ChartStyleHelper.ChartColors.glucosePrimary
            ChartStyleHelper.ChartType.WEIGHT -> ChartStyleHelper.ChartColors.weightPrimary
        }

        // Set accent line/indicator color
        accentView?.setBackgroundColor(accentColor)
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            // Format value based on chart type
            val formattedValue = ChartStyleHelper.formatValue(entry.y, chartType)
            valueText.text = if (labelSuffix.isNotEmpty()) {
                "$formattedValue $labelSuffix"
            } else {
                formattedValue
            }

            // Format date if available
            val index = entry.x.toInt()
            if (index >= 0 && index < dates.size) {
                dateText.text = dateFormat.format(dates[index])
                dateText.visibility = View.VISIBLE
            } else {
                dateText.visibility = View.GONE
            }
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        // Center the marker above the selected point
        return MPPointF((-width / 2).toFloat(), (-height - 16).toFloat())
    }

    override fun getOffsetForDrawingAtPoint(posX: Float, posY: Float): MPPointF {
        val offset = getOffset()

        // Adjust to keep marker within chart bounds
        val chartWidth = chartView?.width ?: 0

        var newX = offset.x
        var newY = offset.y

        // Keep within horizontal bounds
        if (posX + newX < 0) {
            newX = -posX
        } else if (posX + newX + width > chartWidth) {
            newX = chartWidth - posX - width
        }

        // If marker would go above chart, show below point instead
        if (posY + newY < 0) {
            newY = 16f
        }

        return MPPointF(newX, newY)
    }
}

/**
 * Multi-value marker for blood pressure (systolic/diastolic)
 */
class BloodPressureMarkerView(
    context: Context,
    private val dates: List<Date> = emptyList()
) : MarkerView(context, R.layout.view_chart_marker_bp) {

    private val systolicText: TextView = findViewById(R.id.markerSystolic)
    private val diastolicText: TextView = findViewById(R.id.markerDiastolic)
    private val dateText: TextView = findViewById(R.id.markerDate)
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    private var systolicValue: Float = 0f
    private var diastolicValue: Float = 0f

    fun setSystolicValue(value: Float) {
        systolicValue = value
        updateDisplay()
    }

    fun setDiastolicValue(value: Float) {
        diastolicValue = value
        updateDisplay()
    }

    private fun updateDisplay() {
        systolicText.text = "${systolicValue.toInt()}"
        diastolicText.text = "${diastolicValue.toInt()}"
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            val index = entry.x.toInt()

            // Update the appropriate value based on dataset index
            when (highlight?.dataSetIndex) {
                0 -> systolicValue = entry.y
                1 -> diastolicValue = entry.y
            }

            updateDisplay()

            // Format date if available
            if (index >= 0 && index < dates.size) {
                dateText.text = dateFormat.format(dates[index])
                dateText.visibility = View.VISIBLE
            } else {
                dateText.visibility = View.GONE
            }
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-width / 2).toFloat(), (-height - 16).toFloat())
    }
}


