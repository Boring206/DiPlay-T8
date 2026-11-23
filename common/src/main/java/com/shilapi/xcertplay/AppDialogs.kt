package com.shilapi.xcertplay

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.InsetDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ListAdapter
import android.widget.ListView
import com.shilapi.xcertplay.host.R
import kotlin.math.roundToInt

internal interface AppAppearanceOwner {
    val currentAppNight: Boolean
}

internal fun Context.appDialogTheme(): Int = if (
    (this as? AppAppearanceOwner)?.currentAppNight ?: resolveAppNightNow()
) {
    R.style.Theme_Xcertplay_Dialog_Dark
} else {
    R.style.Theme_Xcertplay_Dialog_Light
}

internal fun Context.appDialogContext(): Context = ContextThemeWrapper(this, appDialogTheme())

internal fun Context.appDialogBuilder(): AlertDialog.Builder = AlertDialog.Builder(this, appDialogTheme())

internal fun Context.appDialog(): Dialog = Dialog(this, appDialogTheme())

/** Resolve relative icon padding before ListView measures detached rows for its wrap-content height. */
internal fun AlertDialog.prepareSingleChoiceRows() {
    create()
    val choices = listView ?: return
    val nativeAdapter = choices.adapter ?: return
    val checkedPosition = choices.checkedItemPosition
    choices.adapter = object : ListAdapter by nativeAdapter {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            nativeAdapter.getView(position, convertView, parent).apply {
                layoutDirection = parent.resources.configuration.layoutDirection
            }
    }
    if (checkedPosition != ListView.INVALID_POSITION) {
        choices.setItemChecked(checkedPosition, true)
        choices.setSelection(checkedPosition)
    }
}

/** Reserve visible space above and below tall dialogs using the native window background padding. */
internal fun Dialog.addVerticalWindowInsets() {
    val dialogWindow = window ?: return
    val background = dialogWindow.decorView.background ?: return
    val inset = (APP_DIALOG_VERTICAL_INSET_DP * context.resources.displayMetrics.density).roundToInt()
    dialogWindow.setBackgroundDrawable(InsetDrawable(background, 0, inset, 0, inset))
}

private const val APP_DIALOG_VERTICAL_INSET_DP = 24
