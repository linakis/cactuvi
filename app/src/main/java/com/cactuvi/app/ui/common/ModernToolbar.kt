package com.cactuvi.app.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.cactuvi.app.R

class ModernToolbar
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backButton: ImageButton
    private val titleText: TextView
    private val actionButton: ImageButton

    var title: String = ""
        set(value) {
            field = value
            titleText.text = value
        }

    var onBackClick: (() -> Unit)? = null
        set(value) {
            field = value
            backButton.setOnClickListener { value?.invoke() }
        }

    var onActionClick: (() -> Unit)? = null
        set(value) {
            field = value
            actionButton.setOnClickListener { value?.invoke() }
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_modern_toolbar, this, true)

        backButton = findViewById(R.id.backButton)
        titleText = findViewById(R.id.titleText)
        actionButton = findViewById(R.id.actionButton)

        // Parse custom attributes
        context.obtainStyledAttributes(attrs, R.styleable.ModernToolbar).apply {
            title = getString(R.styleable.ModernToolbar_title) ?: ""

            val showAction = getBoolean(R.styleable.ModernToolbar_showActionButton, false)
            if (showAction) {
                actionButton.visibility = VISIBLE
                getResourceId(R.styleable.ModernToolbar_actionIcon, 0).let { iconRes ->
                    if (iconRes != 0) {
                        actionButton.setImageResource(iconRes)
                    }
                }
            }

            recycle()
        }
    }

    fun setActionIcon(iconRes: Int) {
        actionButton.setImageResource(iconRes)
        actionButton.visibility = VISIBLE
    }

    fun hideActionButton() {
        actionButton.visibility = GONE
    }

    fun showActionButton() {
        actionButton.visibility = VISIBLE
    }
}
