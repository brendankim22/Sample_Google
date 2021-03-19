package com.example.myapplication

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.ads.*
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.formats.OnAdManagerAdViewLoadedListener
import com.google.android.gms.ads.search.SearchAdView
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*


/**
 *  Copyright 2016 Google Inc. All rights reserved.
 *  Updated 1/23/2017
 */


/**
 * The only activity in the application - a basic example of loading an ad.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Root view where we'll add the search ad.
        val rootView = findViewById<View>(R.id.root_view) as ViewGroup

        // A button that when pushed, will trigger an ad load.
        val loadAdButton =
            findViewById<View>(R.id.load_ad_button) as Button

        // Set event handler for button to request for ads
        loadAdButton.setOnClickListener {
            val customTargeting = mapOf("experimentmodel" to "experimentModel88", "position" to "0")
            val request = OUGoogleAdRequest(
                "/22272293638/offerUp_app_home_top",
                "http://www.offerup.com",
                "banner",
                customTargeting,
                null
            )
            getGoogleBannerAd(request, object : OUAdListener {
                override fun onAdLoaded(adView: AdManagerAdView) {
                    rootView.addView(adView)
                }

                override fun onAdFailed(error: String) {
                }

                override fun onAdClicked() {
                }

                override fun onAdImpression() {
                }

            })
        }
    }

    private fun getAdSize(scrollable: Boolean): AdSize {
        val displayMetrics = Resources.getSystem().displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val density = displayMetrics.density
        val adWidth = (widthPixels / density).toInt()

        if (scrollable) {
            return AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(
                applicationContext,
                adWidth
            )
        }
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(applicationContext, adWidth)
    }

    interface OUAdListener {
        fun onAdLoaded(adView: AdManagerAdView)

        fun onAdFailed(error: String)

        fun onAdClicked()

        fun onAdImpression()
    }

    data class OUGoogleAdRequest(
        val clientId: String?,
        val contentUrl: String?,
        val adSize: String?,
        val customTargeting: Map<String, String>,
        val location: Location?
    )

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    private fun parseAdSizeFromString(adSizeString: String?): AdSize {
        return when (adSizeString) {
            "banner" -> AdSize.BANNER
            "mediumRectangle" -> AdSize.MEDIUM_RECTANGLE
            "anchoredAdaptiveBanner" -> getAdSize(false)
            "inlineAdaptiveBanner" -> getAdSize(true)
            else -> AdSize.BANNER
        }
    }

    fun getGoogleBannerAd(
        request: OUGoogleAdRequest,
        ouAdListener: OUAdListener,
        retries: Int = 0
    ) {
        val (clientId,
            contentUrl,
            adSize,
            customTargeting,
            location) = request
        val validAdSize = parseAdSizeFromString(adSize)
        val isTest = true

        if (isTest) {
            val androidId: String = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            val deviceId = md5(androidId).toUpperCase(Locale.getDefault())
            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(deviceId))
                .build()

            MobileAds.setRequestConfiguration(requestConfig)
        }

        val adRequest = AdManagerAdRequest
            .Builder()
            .apply {
                customTargeting?.forEach {
                    addCustomTargeting(it.key, it.value as? String)
                }
            }
            .setContentUrl(contentUrl)
            .setLocation(location)
            .build()

        val builder: AdLoader = AdLoader.Builder(applicationContext, clientId)
            .forAdManagerAdView(OnAdManagerAdViewLoadedListener { adView ->
                adView.setManualImpressionsEnabled(true)
                ouAdListener.onAdLoaded(adView)

                //SDK doesn't seem to track impressions when ad is returned while
                //view is on screen until user interacts with the UI. This is a
                //workaround to track these cases properly.
                if (adView.isOnScreen()) {
                    adView.adListener.onAdImpression()
                }
            }, validAdSize)
            .withAdListener(object : AdListener() {
                override fun onAdOpened() {
                    super.onAdOpened()

                    ouAdListener.onAdClicked()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    super.onAdFailedToLoad(error)

                    ouAdListener.onAdFailed(error.message)
                    if (retries > 0) {
                        getGoogleBannerAd(request, ouAdListener, retries - 1)
                    }
                }

                override fun onAdFailedToLoad(errorCode: Int) {
                    super.onAdFailedToLoad(errorCode)

                    ouAdListener.onAdFailed(errorCode.toString())
                }

                override fun onAdImpression() {
                    super.onAdImpression()

                    ouAdListener.onAdImpression()
                }
            })
            .build()

        builder.loadAd(adRequest)
    }

    companion object {
        private val TAG = "testlog"
    }
}

fun View.isOnScreen(): Boolean {
    if (!isShown) {
        return false
    }

    val actualPosition = Rect()
    getGlobalVisibleRect(actualPosition)

    val width = Resources.getSystem().displayMetrics.widthPixels
    val height = Resources.getSystem().displayMetrics.heightPixels
    val screen = Rect(0, 0, width, height)

    return actualPosition.intersect(screen)
}
