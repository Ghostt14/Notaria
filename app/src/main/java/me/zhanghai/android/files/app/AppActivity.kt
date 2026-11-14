/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.night.NightModeHelper

const val EXTRA_SLIDE = "me.zhanghai.android.files.extra.SLIDE"

abstract class AppActivity : AppCompatActivity() {
    private var isDelegateCreated = false

    override fun getDelegate(): AppCompatDelegate {
        val delegate = super.getDelegate()

        if (!isDelegateCreated) {
            isDelegateCreated = true
            NightModeHelper.apply(this)
        }
        return delegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CustomThemeHelper.apply(this)

        super.onCreate(savedInstanceState)
    }

    override fun finish() {
        super.finish()
        // Screens opened with a slide (Settings, About) slide back out the same way.
        if (intent?.getBooleanExtra(EXTRA_SLIDE, false) == true) {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                me.zhanghai.android.files.R.anim.slide_in_left,
                me.zhanghai.android.files.R.anim.slide_out_right
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
}
