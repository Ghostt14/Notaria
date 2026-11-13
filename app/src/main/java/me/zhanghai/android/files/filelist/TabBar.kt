/*
 * Notaria: the folder-style tab bar, sitting between the toolbar and the breadcrumb bar.
 *
 * Tabs look like stacked paper folders: a border line runs under the inactive tabs, turns
 * up at the left of the active tab, runs along its top, slants down at its right and
 * continues under the remaining tabs. Colors come from the Material theme, so it follows
 * light / dark / dynamic colors.
 *
 * Gestures: tap switches, the x closes, "+" opens a new tab, long-press then drag
 * rearranges the tabs.
 *
 * Based on the tab bar of ShiroiKuma0/shiroikuma-shoruikanri (GPL-3.0).
 */

package me.zhanghai.android.files.filelist

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileListTabItemBinding
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.layoutInflater
import kotlin.math.abs

class TabItem(val id: Int, val title: String, val isSelected: Boolean)

class FileTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {
    private val row = FolderTabRow(context)

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(row, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    fun setTabs(
        items: List<TabItem>,
        onSelect: (Int) -> Unit,
        onClose: (Int) -> Unit,
        onNew: () -> Unit,
        onReorder: (id: Int, toIndex: Int) -> Unit
    ) {
        row.rebuild(items, onSelect, onClose, onNew, onReorder)
        // Scroll the active folder into view once it has been laid out.
        post {
            row.selectedTabView?.let {
                val scrollX = (it.left - width / 4).coerceAtLeast(0)
                smoothScrollTo(scrollX, 0)
            }
        }
    }

    private class FolderTabRow(context: Context) : LinearLayout(context) {
        private val density = resources.displayMetrics.density
        private val slantPx = 10 * density
        private val activeTopMarginPx = (4 * density).toInt()
        private val inactiveHeightPx = (34 * density).toInt()
        private val touchSlopPx = 8 * density
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            pathEffect = CornerPathEffect(8 * density)
        }
        private val shadedPaint = Paint(borderPaint)

        private var tabCount = 0
        var selectedTabView: View? = null
            private set

        private var onReorder: (Int, Int) -> Unit = { _, _ -> }

        // Drag state: the pressed folder follows the finger horizontally; the
        // others shift out of its way; release commits the new order.
        private var draggedView: View? = null
        private var draggedId = 0
        private var dragStartIndex = 0
        private var dragDownRawX = 0f
        private var dragExceededSlop = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            setWillNotDraw(false)
        }

        fun rebuild(
            items: List<TabItem>,
            onSelect: (Int) -> Unit,
            onClose: (Int) -> Unit,
            onNew: () -> Unit,
            onReorder: (Int, Int) -> Unit
        ) {
            endDrag()
            this.onReorder = onReorder
            removeAllViews()
            tabCount = items.size
            selectedTabView = null
            val selectedColor = context.getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
            val unselectedColor =
                context.getColorByAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            val buttonColor = ColorStateList.valueOf(unselectedColor)
            borderPaint.color = selectedColor
            shadedPaint.color = ColorUtils.setAlphaComponent(selectedColor, 100)

            items.forEach { item ->
                val binding = FileListTabItemBinding.inflate(context.layoutInflater, this, false)
                binding.nameText.text = item.title
                binding.nameText.setTextColor(
                    if (item.isSelected) selectedColor else unselectedColor
                )
                binding.closeButton.imageTintList = buttonColor
                binding.closeButton.setOnClickListener { onClose(item.id) }
                binding.root.setOnClickListener { onSelect(item.id) }
                binding.root.setOnLongClickListener { beginDrag(it, item.id) }
                binding.root.setOnTouchListener { view, event -> onDragTouch(view, event) }
                val params = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    if (item.isSelected) LayoutParams.MATCH_PARENT else inactiveHeightPx
                )
                params.marginEnd = slantPx.toInt()
                if (item.isSelected) {
                    params.topMargin = activeTopMarginPx
                }
                addView(binding.root, params)
                if (item.isSelected) {
                    selectedTabView = binding.root
                }
            }

            val addButton = ImageButton(context).apply {
                setImageResource(R.drawable.add_icon_white_24dp)
                imageTintList = buttonColor
                background = null
                contentDescription = context.getString(R.string.file_list_action_new_tab)
                setOnClickListener { onNew() }
            }
            addView(
                addButton,
                LayoutParams((40 * density).toInt(), (40 * density).toInt())
                    .apply { marginEnd = (4 * density).toInt() }
            )
            invalidate()
        }

        // --- Drag to rearrange ---

        private fun beginDrag(view: View, id: Int): Boolean {
            draggedView = view
            draggedId = id
            dragStartIndex = indexOfChild(view)
            dragExceededSlop = false
            view.alpha = 0.7f
            view.translationZ = 4 * density
            parent?.requestDisallowInterceptTouchEvent(true)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            return true
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun onDragTouch(view: View, event: MotionEvent): Boolean {
            if (draggedView !== view) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragDownRawX = event.rawX
                }
                return false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragDownRawX
                    if (abs(dx) > touchSlopPx) {
                        dragExceededSlop = true
                    }
                    view.translationX = dx
                    updateDragShifts(view)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val targetIndex = dragTargetIndex(view)
                    val moved = dragExceededSlop && targetIndex != dragStartIndex
                    endDrag()
                    if (moved) {
                        onReorder(draggedId, targetIndex)
                    }
                }
                MotionEvent.ACTION_CANCEL -> endDrag()
            }
            return true
        }

        private fun dragTargetIndex(dragged: View): Int {
            val draggedCenter = dragged.left + dragged.width / 2f + dragged.translationX
            var index = 0
            for (i in 0..<tabCount) {
                val child = getChildAt(i)
                if (child === dragged) {
                    continue
                }
                if ((child.left + child.right) / 2f < draggedCenter) {
                    index++
                }
            }
            return index
        }

        private fun updateDragShifts(dragged: View) {
            val targetIndex = dragTargetIndex(dragged)
            val shiftPx = (dragged.width + slantPx).let {
                if (targetIndex >= dragStartIndex) -it else it
            }
            for (i in 0..<tabCount) {
                val child = getChildAt(i)
                if (child === dragged) {
                    continue
                }
                val between = if (targetIndex >= dragStartIndex) {
                    i in (dragStartIndex + 1)..targetIndex
                } else {
                    i in targetIndex..<dragStartIndex
                }
                val translation = if (between) shiftPx else 0f
                if (child.translationX != translation) {
                    child.animate().translationX(translation).setDuration(100).start()
                }
            }
        }

        private fun endDrag() {
            val view = draggedView ?: return
            draggedView = null
            view.alpha = 1f
            view.translationZ = 0f
            view.translationX = 0f
            for (i in 0..<childCount) {
                val child = getChildAt(i)
                child.animate().cancel()
                child.translationX = 0f
            }
            invalidate()
        }

        // --- The folder outlines ---

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val bottom = height - borderPaint.strokeWidth / 2
            val endX = width.toFloat()
            val selected = selectedTabView

            // The folders in the background: their own outlines, shaded.
            for (i in 0..<tabCount) {
                val child = getChildAt(i)
                if (child == null || child === selected) {
                    continue
                }
                val left = child.left + child.translationX
                val right = child.right + child.translationX
                val top = child.top.toFloat() + borderPaint.strokeWidth / 2
                val path = Path()
                path.moveTo(left, bottom)
                path.lineTo(left, top)
                path.lineTo(right, top)
                path.lineTo(right + slantPx, bottom)
                canvas.drawPath(path, shadedPaint)
            }

            // The border line, wrapping around the active folder.
            val path = Path()
            if (selected == null) {
                path.moveTo(0f, bottom)
                path.lineTo(endX, bottom)
            } else {
                val left = selected.left + selected.translationX
                val right = selected.right + selected.translationX
                val top = selected.top.toFloat() + borderPaint.strokeWidth / 2
                path.moveTo(0f, bottom)
                // under the folders in the background
                path.lineTo(left, bottom)
                // 90° up the left side of the active folder
                path.lineTo(left, top)
                // along its top
                path.lineTo(right, top)
                // slightly slanted down to the bottom right
                path.lineTo(right + slantPx, bottom)
                // and under the rest of the folders
                path.lineTo(endX, bottom)
            }
            canvas.drawPath(path, borderPaint)
        }
    }
}
