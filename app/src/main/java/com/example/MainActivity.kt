package com.example

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.io.OutputStream
import java.util.Base64

class MainActivity : ComponentActivity() {
  var webView: WebView? = null
  var mInterstitialAd: InterstitialAd? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize Mobile Ads SDK
    MobileAds.initialize(this) {}

    // Load the Interstitial Ad in background
    loadInterstitialAd()

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          CVBuilderApp(
            activity = this,
            onWebViewCreated = { webViewInstance ->
              webView = webViewInstance
            }
          )
        }
      }
    }
  }

  fun loadInterstitialAd() {
    val adRequest = AdRequest.Builder().build()
    InterstitialAd.load(
      this,
      "ca-app-pub-4092819395691806/9837878449", // Interstitial Ad Unit ID
      adRequest,
      object : InterstitialAdLoadCallback() {
        override fun onAdFailedToLoad(adError: LoadAdError) {
          mInterstitialAd = null
        }

        override fun onAdLoaded(interstitialAd: InterstitialAd) {
          mInterstitialAd = interstitialAd
        }
      }
    )
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CVBuilderApp(
  activity: MainActivity,
  onWebViewCreated: (WebView) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    // WebView occupying all remaining space
    AndroidView(
      factory = { context ->
        WebView(context).apply {
          layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
          )
          
          webViewClient = WebViewClient()
          webChromeClient = WebChromeClient()
          
          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
          }
          
          addJavascriptInterface(CVBuilderInterface(activity), "CVBuilderAndroid")
          loadUrl("file:///android_asset/index.html")
          onWebViewCreated(this)
        }
      },
      modifier = Modifier.weight(1f)
    )

    // Google AdMob Banner Ad View
    AndroidView(
      factory = { context ->
        AdView(context).apply {
          setAdSize(AdSize.BANNER)
          adUnitId = "ca-app-pub-4092819395691806/8161000542" // Banner Ad Unit ID
          loadAd(AdRequest.Builder().build())
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(50.dp)
    )
  }
}

class CVBuilderInterface(private val activity: MainActivity) {
  @JavascriptInterface
  fun downloadPDF(base64Data: String, fileName: String) {
    try {
      val cleanBase64 = if (base64Data.contains(",")) {
        base64Data.split(",")[1]
      } else {
        base64Data
      }
      
      val pdfBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Base64.getDecoder().decode(cleanBase64)
      } else {
        android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
      }
      
      val savedUri = savePdfToDownloads(activity, pdfBytes, fileName)
      
      activity.runOnUiThread {
        if (savedUri != null) {
          Toast.makeText(activity, "PDF saved to Downloads folder!", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(activity, "Failed to save PDF to Downloads.", Toast.LENGTH_SHORT).show()
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      activity.runOnUiThread {
        Toast.makeText(activity, "Error saving PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
      }
    }
  }

  @JavascriptInterface
  fun showInterstitialAd() {
    activity.runOnUiThread {
      val webView = activity.webView
      val ad = activity.mInterstitialAd
      if (ad != null) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
          override fun onAdDismissedFullScreenContent() {
            activity.mInterstitialAd = null
            activity.loadInterstitialAd()
            webView?.evaluateJavascript("generatePremiumPDF()", null)
          }

          override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
            activity.mInterstitialAd = null
            activity.loadInterstitialAd()
            webView?.evaluateJavascript("generatePremiumPDF()", null)
          }
        }
        ad.show(activity)
      } else {
        activity.loadInterstitialAd()
        webView?.evaluateJavascript("generatePremiumPDF()", null)
      }
    }
  }
}

private fun savePdfToDownloads(context: Context, bytes: ByteArray, fileName: String): Uri? {
  val resolver = context.contentResolver
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    val contentValues = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
      put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
      put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    if (uri != null) {
      try {
        resolver.openOutputStream(uri)?.use { out ->
          out.write(bytes)
          out.flush()
        }
        uri
      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    } else null
  } else {
    try {
      val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
      val file = java.io.File(downloadsDir, fileName)
      java.io.FileOutputStream(file).use { out ->
        out.write(bytes)
        out.flush()
      }
      Uri.fromFile(file)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }
}
