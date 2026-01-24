package com.cactuvi.app.ui.common

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.cactuvi.app.R

class CactuviCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {
    
    init {
        // Cactuvi-inspired card styling
        setCardBackgroundColor(ColorStateList.valueOf(
            ContextCompat.getColor(context, R.color.surface_variant_cactuvi)
        ))
        radius = context.resources.getDimension(R.dimen.corner_radius_large)
        setCardElevation(context.resources.getDimension(R.dimen.elevation_card))
        strokeWidth = 1
        setStrokeColor(ContextCompat.getColor(context, R.color.glass_border))
        
        // Ripple effect
        isClickable = true
        isFocusable = true
        foreground = ContextCompat.getDrawable(context, R.drawable.ripple_cactuvi)
    }
}
