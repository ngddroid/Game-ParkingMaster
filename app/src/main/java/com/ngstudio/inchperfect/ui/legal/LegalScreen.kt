package com.ngstudio.inchperfect.ui.legal

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngstudio.inchperfect.ui.components.Ic
import com.ngstudio.inchperfect.ui.components.CityBackground
import com.ngstudio.inchperfect.ui.components.RoundButton
import com.ngstudio.inchperfect.ui.theme.Palette
import com.ngstudio.inchperfect.ui.theme.Type

/** Shows the bundled, game-themed Terms of Use or Privacy Policy (assets/legal/{doc}.html). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val title = if (doc == "privacy") "PRIVACY POLICY" else "TERMS OF USE"
    Box(Modifier.fillMaxSize()) {
        CityBackground(intensity = 0.4f)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Ic.BACK, onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text(title, style = Type.Title.copy(color = Palette.Text))
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AColor.TRANSPARENT)
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                if (url.startsWith("mailto:")) { try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, request.url)) } catch (_: Exception) {} }
                                return !url.startsWith("file:///android_asset/")
                            }
                        }
                        loadUrl("file:///android_asset/legal/${if (doc == "privacy") "privacy" else "terms"}.html")
                    }
                },
                modifier = Modifier.weight(1f).padding(top = 10.dp).clip(RoundedCornerShape(20.dp)),
            )
        }
    }
}
