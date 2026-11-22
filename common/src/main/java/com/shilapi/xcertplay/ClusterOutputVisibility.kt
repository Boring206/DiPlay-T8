package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup

/** Window-alpha hiding does not trigger View visibility callbacks. */
internal interface ClusterOutputVisibility {
    fun setOutputVisible(visible: Boolean)
    companion object {
        fun update(view: View?, visible: Boolean) {
            if (view is ClusterOutputVisibility) view.setOutputVisible(visible)
            if (view is ViewGroup) for (index in 0 until view.childCount) update(view.getChildAt(index), visible)
        }
    }
}
