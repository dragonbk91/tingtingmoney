package money.tingting

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent

open class BaseActivity : ComponentActivity() {
    companion object {
        private const val TAG = "Analytics"
    }

    protected lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
    }

    override fun onResume() {
        super.onResume()
        // Log screen view when activity becomes visible
        logScreenView(this::class.simpleName ?: "Unknown")
    }

    protected fun logScreenView(screenName: String) {
        Log.d(TAG, "Screen view: $screenName")
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
    }

    fun logButtonClick(buttonName: String) {
        Log.d(TAG, "Button click: $buttonName")
        firebaseAnalytics.logEvent("button_click") {
            param(FirebaseAnalytics.Param.ITEM_NAME, buttonName)
            param(FirebaseAnalytics.Param.CONTENT_TYPE, "button")
        }
    }
}
