/*
 * Notaria: the Home screen. Two cards only: total storage usage and quick access.
 */

package me.zhanghai.android.files.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.trash.TrashManager
import java.io.File

class HomeView(context: Context) : ScrollView(context) {
    var onOpenDirectory: (File) -> Unit = {}

    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(96))
    }
    private val percentText = TextView(context)
    private val usedText = TextView(context)
    private val totalText = TextView(context)
    private val bar = StorageBar(context)
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var loaded = false

    private enum class Category(
        val color: Int,
        val nameRes: Int,
        val iconRes: Int,
        val directory: String?
    ) {
        IMAGES(0xFFF28B82.toInt(), R.string.home_images, R.drawable.image_icon_white_24dp,
            Environment.DIRECTORY_PICTURES),
        VIDEOS(0xFFC58AF9.toInt(), R.string.home_videos, R.drawable.video_icon_white_24dp,
            Environment.DIRECTORY_MOVIES),
        AUDIO(0xFF8AB4F8.toInt(), R.string.home_audio, R.drawable.audio_icon_white_24dp,
            Environment.DIRECTORY_MUSIC),
        DOCUMENTS(0xFFD2B07A.toInt(), R.string.home_documents, R.drawable.document_icon_white_24dp,
            Environment.DIRECTORY_DOCUMENTS),
        DOWNLOADS(0xFF5BC8D2.toInt(), R.string.home_downloads, R.drawable.download_icon_white_24dp,
            Environment.DIRECTORY_DOWNLOADS),
        INSTALL(0xFFA5D63A.toInt(), R.string.home_install_files, R.drawable.file_apk_icon,
            Environment.DIRECTORY_DOWNLOADS),
        ARCHIVES(0xFF94A7C4.toInt(), R.string.home_archives, 0, null),
        OTHER(0xFF8DA7BD.toInt(), R.string.home_other, 0, null),
        TRASH(0xFF8A8FA0.toInt(), R.string.trash_title, 0, null)
    }

    private val surface =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer)
    private val onSurface =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val onSurfaceVariant =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

    init {
        isFillViewport = true
        clipToPadding = false
        addView(content)
        content.addView(buildUsageCard(), cardParams())
        content.addView(buildQuickAccessCard(), cardParams())
    }

    private fun cardParams() = LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(12) }

    private fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(28).toFloat()
        }
        setPadding(dp(20), dp(20), dp(20), dp(16))
    }

    private fun buildUsageCard(): View = card().apply {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        percentText.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(onSurface)
            text = "–"
        }
        val inUse = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(onSurfaceVariant)
            setText(R.string.home_in_use)
            setPadding(dp(8), 0, 0, dp(6))
        }
        header.addView(percentText)
        header.addView(inUse)
        addView(header)

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(8))
        }
        usedText.apply { setTextColor(onSurfaceVariant); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) }
        totalText.apply {
            setTextColor(onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.END
        }
        labels.addView(usedText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        labels.addView(totalText, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(labels)
        addView(bar, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(24)))
        addView(rows, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
    }

    private fun buildQuickAccessCard(): View = card().apply {
        val grid = GridLayout(context).apply { columnCount = 3 }
        listOf(
            Category.IMAGES, Category.VIDEOS, Category.AUDIO,
            Category.DOCUMENTS, Category.DOWNLOADS, Category.INSTALL
        ).forEach { category ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(12), dp(4), dp(12))
                isClickable = true
                setBackgroundResource(
                    TypedValue().also {
                        context.theme.resolveAttribute(
                            android.R.attr.selectableItemBackgroundBorderless, it, true
                        )
                    }.resourceId
                )
                setOnClickListener { openCategory(category) }
            }
            item.addView(ImageView(context).apply {
                setImageResource(category.iconRes)
                imageTintList = ColorStateList.valueOf(category.color)
            }, LinearLayout.LayoutParams(dp(36), dp(36)))
            item.addView(TextView(context).apply {
                setText(category.nameRes)
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, dp(8), 0, 0)
            })
            grid.addView(item, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 })
        }
        addView(grid)
    }

    private fun openCategory(category: Category) {
        @Suppress("DEPRECATION")
        val directory = when {
            category == Category.TRASH ->
                TrashManager.trashDir.also { it.mkdirs() }
            category.directory != null ->
                Environment.getExternalStoragePublicDirectory(category.directory)
                    .also { it.mkdirs() }
            else -> return
        }
        onOpenDirectory(directory)
    }

    /** Loads (or reloads) the numbers. Cheap to call on every show. */
    fun refresh(scope: CoroutineScope) {
        scope.launch {
            val stats = withContext(Dispatchers.IO) { computeStats() }
            showStats(stats)
        }
    }

    private class Stats(val total: Long, val used: Long, val sizes: Map<Category, Long>)

    private fun computeStats(): Stats {
        @Suppress("DEPRECATION")
        val statFs = StatFs(Environment.getExternalStorageDirectory().path)
        val total = statFs.totalBytes
        val used = total - statFs.availableBytes
        val sizes = mutableMapOf<Category, Long>()
        fun add(category: Category, size: Long) {
            sizes[category] = (sizes[category] ?: 0L) + size
        }
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val size = cursor.getLong(0)
                    val name = cursor.getString(2)?.lowercase() ?: ""
                    val extension = name.substringAfterLast('.', "")
                    when {
                        cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                            add(Category.IMAGES, size)
                        cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                            add(Category.VIDEOS, size)
                        cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                            add(Category.AUDIO, size)
                        extension in INSTALL_EXTENSIONS -> add(Category.INSTALL, size)
                        extension in ARCHIVE_EXTENSIONS -> add(Category.ARCHIVES, size)
                        extension in DOCUMENT_EXTENSIONS -> add(Category.DOCUMENTS, size)
                    }
                }
            }
        }
        val trash = TrashManager.sizeBytes()
        sizes[Category.TRASH] = trash
        // Everything we can't attribute (system, apps, app data, uncategorized files).
        sizes[Category.OTHER] = (used - sizes.values.sum()).coerceAtLeast(0L)
        return Stats(total, used, sizes)
    }

    private fun showStats(stats: Stats) {
        loaded = true
        val percent = if (stats.total > 0) (stats.used * 100 / stats.total).toInt() else 0
        percentText.text = "$percent%"
        usedText.text = Formatter.formatFileSize(context, stats.used)
        totalText.text = Formatter.formatFileSize(context, stats.total)
        val order = listOf(
            Category.IMAGES, Category.VIDEOS, Category.AUDIO, Category.DOCUMENTS,
            Category.INSTALL, Category.ARCHIVES, Category.OTHER, Category.TRASH
        )
        bar.set(
            stats.total,
            order.map { (stats.sizes[it] ?: 0L) to it.color }
        )
        rows.removeAllViews()
        order.forEach { category ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(category.color)
                    cornerRadius = dp(5).toFloat()
                }
            }, LinearLayout.LayoutParams(dp(26), dp(10)).apply { marginEnd = dp(16) })
            row.addView(TextView(context).apply {
                setText(category.nameRes)
                setTextColor(onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(context).apply {
                text = Formatter.formatFileSize(context, stats.sizes[category] ?: 0L)
                setTextColor(onSurface)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
            val openable = category.directory != null || category == Category.TRASH
            if (openable) {
                row.setOnClickListener { openCategory(category) }
            }
            rows.addView(row)
        }
    }

    private class StorageBar(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clip = Path()
        private var total = 1L
        private var segments: List<Pair<Long, Int>> = emptyList()
        private val trackColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceVariant
        )

        fun set(total: Long, segments: List<Pair<Long, Int>>) {
            this.total = total.coerceAtLeast(1L)
            this.segments = segments
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val radius = height / 2f
            clip.reset()
            clip.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius,
                Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.color = trackColor
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            var x = 0f
            segments.forEach { (size, color) ->
                val w = width * size.toFloat() / total
                if (w > 0f) {
                    paint.color = color
                    canvas.drawRect(x, 0f, x + w, height.toFloat(), paint)
                    x += w
                }
            }
            canvas.restore()
        }
    }

    companion object {
        private val INSTALL_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm", "aab")
        private val ARCHIVE_EXTENSIONS =
            setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst", "iso")
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "odp",
            "rtf", "csv", "epub", "md", "html"
        )
    }
}
