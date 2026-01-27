package com.cactuvi.app.ui.common

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.cactuvi.app.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Dialog shown when VPN is not detected and VPN warning is enabled. Gives user options to continue
 * anyway, disable warning, or close app.
 */
@AndroidEntryPoint
class VPNWarningDialog : DialogFragment() {

    @Inject lateinit var preferencesManager: PreferencesManager

    private var onContinue: (() -> Unit)? = null
    private var onCloseApp: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("No VPN Detected")
            .setMessage(
                "For privacy and security, we recommend using a VPN when streaming content."
            )
            .setPositiveButton("Continue Anyway") { _, _ ->
                onContinue?.invoke()
                dismiss()
            }
            .setNeutralButton("Disable Warning") { _, _ ->
                preferencesManager.setVpnWarningEnabled(false)
                onContinue?.invoke()
                dismiss()
            }
            .setNegativeButton("Close App") { _, _ ->
                onCloseApp?.invoke()
                dismiss()
            }
            .setCancelable(false)
            .create()
    }

    companion object {
        const val TAG = "VPNWarningDialog"

        fun newInstance(
            onContinue: () -> Unit = {},
            onCloseApp: () -> Unit = {}
        ): VPNWarningDialog {
            return VPNWarningDialog().apply {
                this.onContinue = onContinue
                this.onCloseApp = onCloseApp
            }
        }
    }
}
