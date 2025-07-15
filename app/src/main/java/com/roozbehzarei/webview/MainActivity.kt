package com.roozbehzarei.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.roozbehzarei.webview.ui.theme.SuperWebViewTheme // اطمینان حاصل کنید که نام Theme شما همین است

// The URL of the website to be loaded in the app
private const val WEBSITE = "https://tellso.ir/v2pg/index.php"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            // نکته: اگر نام Theme شما متفاوت است، آن را در خط زیر جایگزین کنید.
            // این نام باید با نام تعریف شده در فایل ui/theme/Theme.kt مطابقت داشته باشد.
            SuperWebViewTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class) // <<< انوتیشن ضروری برای PullToRefresh
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MainScreen() {
    var webView: WebView? by remember { mutableStateOf(null) }
    val state = rememberPullToRefreshState()
    var progress by remember { mutableStateOf(0) }
    var fullScreenView: View? by remember { mutableStateOf(null) }
    var webChromeClient: WebChromeClient? = null // To handle fullscreen exit

    // --- Back Handler Logic ---
    var isBackEnabled by remember { mutableStateOf(false) }

    // This handler is for exiting fullscreen video
    BackHandler(enabled = fullScreenView != null) {
        webChromeClient?.onHideCustomView()
    }

    // This handler is for navigating back in WebView history
    BackHandler(enabled = isBackEnabled && fullScreenView == null) {
        webView?.goBack()
    }

    // --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScrollConnection)
    ) {
        WebViewer(
            modifier = Modifier.fillMaxSize(),
            onWebViewReady = { createdWebView ->
                webView = createdWebView
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        progress = newProgress
                    }
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        super.onShowCustomView(view, callback)
                        fullScreenView = view
                    }
                    override fun onHideCustomView() {
                        super.onHideCustomView()
                        fullScreenView = null
                    }
                }
                createdWebView.webChromeClient = webChromeClient

                createdWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        isBackEnabled = view?.canGoBack() ?: false
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isBackEnabled = view?.canGoBack() ?: false
                        if (state.isRefreshing) {
                            state.endRefresh()
                        }
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith(WEBSITE)) {
                            return false
                        }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            view?.context?.startActivity(intent)
                        } catch (e: Exception) {
                            // Could not handle the intent
                        }
                        return true
                    }
                }
            }
        )

        if (state.isRefreshing) {
            LaunchedEffect(true) {
                webView?.reload()
            }
        }

        PullToRefreshContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            state = state,
            contentColor = MaterialTheme.colorScheme.primary,
            containerColor = MaterialTheme.colorScheme.surface
        )

        ProgressIndicator(progress)
    }

    if (fullScreenView != null) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { fullScreenView!! }
        )
    }
}

@Composable
private fun ProgressIndicator(progress: Int) {
    AnimatedVisibility(
        modifier = Modifier.fillMaxWidth(),
        visible = progress in 1..99
    ) {
        LinearProgressIndicator(progress = { progress / 100f })
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewer(
    modifier: Modifier = Modifier,
    onWebViewReady: (WebView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(false)
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        isAlgorithmicDarkeningAllowed = true
                    }
                }
                loadUrl(WEBSITE)
                onWebViewReady(this)
            }
        }
    )
}
