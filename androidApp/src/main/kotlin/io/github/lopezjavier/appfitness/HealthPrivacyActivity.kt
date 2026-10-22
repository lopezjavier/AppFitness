package io.github.lopezjavier.appfitness

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val PADDING_DP = 24f
private const val TITLE_SP = 22f
private const val BODY_SP = 16f
private const val GAP_DP = 16f

/**
 * Health Connect permission rationale (`ACTION_SHOW_PERMISSIONS_RATIONALE`, also reached through the
 * `VIEW_PERMISSION_USAGE` alias on Android 14+). A plain themed screen outside Compose: it only shows the privacy text.
 */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = dp(PADDING_DP)
        val title = TextView(this).apply {
            setText(R.string.health_privacy_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isAccessibilityHeading = true
        }
        val body = TextView(this).apply {
            setText(R.string.health_privacy_body)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_SP)
            setPadding(0, dp(GAP_DP), 0, 0)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(title, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(body, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
}
