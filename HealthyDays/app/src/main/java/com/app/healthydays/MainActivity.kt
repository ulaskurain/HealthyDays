package com.app.healthydays

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.app.healthydays.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var adView: AdView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedAppearance()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        // Obtain the NavController from the fragment inside the included layout
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_history, R.id.nav_stats),
            drawerLayout
        )

        // Set the title of the fragment in the action bar
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Set up Ad Banner
        val adContainer = binding.appBarMain.contentMain.adContainer

        adView = AdView(this)
        adView.setAdSize(getAdaptiveAdSize())
        adView.adUnitId = getString(R.string.banner_ad_unit_id)
        adContainer.addView(adView)

        adView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isDestroyed) adView.loadAd(AdRequest.Builder().build())
                }, 30_000)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.appBarMain.toolbarTitle.text = when (destination.id) {
                R.id.nav_home,
                R.id.nav_history,
                R.id.nav_stats -> destination.label
                else -> ""
            }

            updateSettingsButtonVisibility()
        }

        MobileAds.initialize(this) {
            runOnUiThread {
                if (!isDestroyed) adView.loadAd(AdRequest.Builder().build())
            }
        }

        navView.setupWithNavController(navController)

        val headerView = navView.getHeaderView(0)
        val navDate = headerView.findViewById<TextView>(R.id.nav_date)

        navDate.text = LocalDate.now().format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        updateSettingsButtonVisibility()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
    }

    override fun onDestroy() {
        adView.destroy()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val navController = findNavController(
                    R.id.nav_host_fragment_content_main
                )

                navController.navigate(R.id.settingsFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateSettingsButtonVisibility() {
        val menuItem = binding.appBarMain.toolbar.menu.findItem(R.id.action_settings)

        menuItem?.isVisible =
            findNavController(R.id.nav_host_fragment_content_main)
                .currentDestination?.id != R.id.settingsFragment
    }

    private fun applySavedAppearance() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        when (prefs.getString("appearance", "system")) {
            "light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            "dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            else -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    private fun getAdaptiveAdSize(): AdSize {
        val displayMetrics = resources.displayMetrics
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth)
    }
}