/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Checkable
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import me.zhanghai.android.foregroundcompat.ForegroundLinearLayout

class CheckableForegroundLinearLayout : ForegroundLinearLayout, Checkable {
    private var _isChecked = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            refreshDrawableState()
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    // The View constructor can call this before the library has created its foreground helper
    // (it happens when the theme supplies scrollbar attributes, e.g. after the Settings screen
    // has applied its theme overlay), which would crash with a NullPointerException.
    override fun onRtlPropertiesChanged(layoutDirection: Int) {
        try {
            super.onRtlPropertiesChanged(layoutDirection)
        } catch (e: NullPointerException) {
            // Ignore: the helper picks up the layout direction once it exists.
        }
    }

    override fun isChecked(): Boolean = _isChecked

    override fun setChecked(checked: Boolean) {
        _isChecked = checked
        refreshDrawableState()
    }

    override fun toggle() {
        _isChecked = !_isChecked
        refreshDrawableState()
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray =
        super.onCreateDrawableState(extraSpace + 1).apply {
            if (_isChecked) {
                mergeDrawableStates(this, CHECKED_STATE_SET)
            }
        }

    companion object {
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }
}
