/*
 * Notaria: section headers (Today / This week / This month / Earlier) in the list view when
 * the files are sorted by date. Drawn as an item decoration so the adapter's item model, and
 * everything that depends on item positions, stays untouched.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import java.util.Calendar

class DateHeaderDecoration(
    private val context: Context,
    private val adapter: FileListAdapter
) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val headerHeight = (44 * density).toInt()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14 * density
        color = MaterialColors.getColor(
            context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0
        )
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.5f * density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(2 * density, 5 * density), 0f)
        color = textPaint.color
        alpha = 110
    }
    private val sidePadding = 16 * density

    private val labels = arrayOf(
        context.getString(R.string.file_list_group_today),
        context.getString(R.string.file_list_group_week),
        context.getString(R.string.file_list_group_month),
        context.getString(R.string.file_list_group_earlier)
    )
    private val labelWidths = FloatArray(4)
    private val startOfToday: Long
    private val startOfWeekWindow: Long
    private val startOfMonth: Long

    init {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startOfToday = calendar.timeInMillis
        startOfWeekWindow = startOfToday - 6 * DAY_MILLIS
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        startOfMonth = calendar.timeInMillis
    }

    private fun groupOf(file: FileItem): Int {
        val time = file.attributes.lastModifiedTime().toMillis()
        return when {
            time >= startOfToday -> 0
            time >= startOfWeekWindow -> 1
            time >= startOfMonth -> 2
            else -> 3
        }
    }

    private fun isGroupStart(position: Int): Boolean {
        if (!adapter.isGroupedByDate || position < 0 || position >= adapter.itemCount) {
            return false
        }
        if (position == 0) {
            return true
        }
        return groupOf(adapter.getItem(position)) != groupOf(adapter.getItem(position - 1))
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (isGroupStart(parent.getChildAdapterPosition(view))) {
            outRect.top = headerHeight
        }
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!adapter.isGroupedByDate) {
            return
        }
        for (i in 0..<parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (!isGroupStart(position)) {
                continue
            }
            val group = groupOf(adapter.getItem(position))
            val label = labels[group]
            if (labelWidths[group] == 0f) {
                labelWidths[group] = textPaint.measureText(label)
            }
            val top = child.top + child.translationY - headerHeight
            val baseline = top + headerHeight * 0.62f
            canvas.drawText(label, sidePadding, baseline, textPaint)
            val lineStart = sidePadding + labelWidths[group] + 12 * density
            val lineY = baseline - textPaint.textSize * 0.3f
            canvas.drawLine(lineStart, lineY, parent.width - sidePadding, lineY, linePaint)
        }
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
