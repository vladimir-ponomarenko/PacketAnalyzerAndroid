package com.packet.analyzer.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.packet.analyzer.R

@Composable
fun AppTrafficChartView(
    entries: List<Entry>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineChartColor = MaterialTheme.colorScheme.primary.toArgb()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f).toArgb()


    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(AndroidColor.TRANSPARENT)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    this.textColor = textColor
                    this.gridColor = gridColor
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} B"
                        }
                    }
                    granularity = 1f
                    setAvoidFirstLastClipping(true)
                    axisLineColor = textColor


                }

                axisLeft.apply {
                    this.textColor = textColor
                    this.gridColor = gridColor
                    granularity = 1f
                    axisMinimum = 0f
                    axisLineColor = textColor
                }
                axisRight.isEnabled = false
                legend.isEnabled = false
            }
        },
        update = { chart ->
            if (entries.isNotEmpty()) {
                val dataSet = LineDataSet(entries, context.getString(R.string.y_axis_label_packet_count)).apply {
                    color = lineChartColor
                    valueTextColor = textColor
                    setCircleColor(lineChartColor)
                    circleRadius = 2f
                    setDrawCircleHole(false)
                    lineWidth = 1.5f
                    setDrawValues(false)
                    // mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                }
                chart.data = LineData(dataSet)
            } else {
                chart.clear()
            }
            chart.notifyDataSetChanged()
            chart.invalidate()
            // chart.animateX(300)
        },
        modifier = modifier
    )
}