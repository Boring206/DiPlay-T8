// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import com.shilapi.xcertplay.host.R

/**
 * Single source of truth for the BYD-series badge presets: the stored preference key, the localized
 * setting label and the packaged res/raw icon. The settings preview and the icon actually sent to
 * the iPhone both read this enum, so a badge can never be added to one side only.
 */
enum class CarBrandBadge(
    val key: String,
    val labelRes: Int,
    val iconRawRes: Int,
) {
    DEFAULT("default", R.string.settings_car_brand_default, R.raw.ic_car_home),
    WANGCHAO("wangchao", R.string.settings_car_brand_wangchao, R.raw.ic_car_wangchao),
    HAIYANG("haiyang", R.string.settings_car_brand_haiyang, R.raw.ic_car_haiyang),
    FANGCHENGBAO(
        "fangchengbao",
        R.string.settings_car_brand_fangchengbao,
        R.raw.ic_car_fangchengbao,
    ),
    DENZA("denza", R.string.settings_car_brand_denza, R.raw.ic_car_denza),
    YANGWANG("yangwang", R.string.settings_car_brand_yangwang, R.raw.ic_car_yangwang),
    LINGHUI("linghui", R.string.settings_car_brand_linghui, R.raw.ic_car_linghui),
    ;

    companion object {
        /** Stored values from older builds or unknown keys fall back to the default BYD badge. */
        fun fromKey(key: String?): CarBrandBadge =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
