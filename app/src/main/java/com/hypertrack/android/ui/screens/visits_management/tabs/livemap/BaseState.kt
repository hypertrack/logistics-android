package com.hypertrack.android.ui.screens.visits_management.tabs.livemap

import android.content.Context
import com.hypertrack.android.ui.screens.visits_management.tabs.livemap.SharedHelper.Companion.getInstance

open class BaseState protected constructor(mContext: Context) {
    @JvmField
    protected val sharedHelper: SharedHelper = getInstance(mContext)

}