package com.midxv.InternetMeter

import android.Manifest
import android.app.AppOpsManager
import android.app.Dialog
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var rvAppUsage: RecyclerView
    private lateinit var tvTotalUsage: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var pieChart: PieChartView
    private lateinit var adapter: AppUsageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<LinearLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        rvAppUsage = findViewById(R.id.rvAppUsage)
        tvTotalUsage = findViewById(R.id.tvTotalUsage)
        btnPermission = findViewById(R.id.btnPermission)
        btnSettings = findViewById(R.id.btnSettings)
        pieChart = findViewById(R.id.pieChart)

        rvAppUsage.layoutManager = LinearLayoutManager(this)
        adapter = AppUsageAdapter(emptyList(), packageManager, 0)
        rvAppUsage.adapter = adapter

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) {
            btnPermission.visibility = Button.GONE
            manageNetworkService()
            loadUsageData()
        } else {
            btnPermission.visibility = Button.VISIBLE
            tvTotalUsage.text = "Permission required."
        }
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_settings)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val switchSpeedMonitor = dialog.findViewById<Switch>(R.id.switchSpeedMonitor)
        val rgMonitorMode = dialog.findViewById<RadioGroup>(R.id.rgMonitorMode)
        val rbLegacy = dialog.findViewById<RadioButton>(R.id.rbLegacy)
        val rbOverlay = dialog.findViewById<RadioButton>(R.id.rbOverlay)

        val prefs = getSharedPreferences("InternetMeterPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("monitor_enabled", true)
        val mode = prefs.getString("monitor_mode", "overlay")

        switchSpeedMonitor.isChecked = isEnabled
        rgMonitorMode.visibility = if (isEnabled) View.VISIBLE else View.GONE
        if (mode == "legacy") rbLegacy.isChecked = true else rbOverlay.isChecked = true

        switchSpeedMonitor.setOnCheckedChangeListener { _, isChecked ->
            rgMonitorMode.visibility = if (isChecked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean("monitor_enabled", isChecked).apply()
            if (isChecked && rbOverlay.isChecked && !Settings.canDrawOverlays(this)) {
                switchSpeedMonitor.isChecked = false
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                manageNetworkService()
            }
        }

        rgMonitorMode.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = if (checkedId == R.id.rbLegacy) "legacy" else "overlay"
            if (selectedMode == "overlay" && !Settings.canDrawOverlays(this)) {
                rbLegacy.isChecked = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                prefs.edit().putString("monitor_mode", selectedMode).apply()
                manageNetworkService()
            }
        }
        dialog.show()
    }

    private fun manageNetworkService() {
        val prefs = getSharedPreferences("InternetMeterPrefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("monitor_enabled", true)
        val serviceIntent = Intent(this, NetworkMonitorService::class.java)
        if (isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        } else {
            stopService(serviceIntent)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadUsageData() {
        val networkStatsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val appUsageMap = mutableMapOf<Int, AppUsageInfo>()
        var totalBytes = 0L

        try {
            val wifiStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_WIFI, "", startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                totalBytes += (bucket.rxBytes + bucket.txBytes)
                updateMap(appUsageMap, bucket)
            }
            wifiStats.close()

            val mobileStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_MOBILE, null, startTime, endTime)
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                totalBytes += (bucket.rxBytes + bucket.txBytes)
                updateMap(appUsageMap, bucket)
            }
            mobileStats.close()
        } catch (e: Exception) { return }

        val sortedList = appUsageMap.values.filter { it.total > 0 }.sortedByDescending { it.total }
        tvTotalUsage.text = String.format("Total Usage Today: %.2f MB", totalBytes / (1024f * 1024f))
        pieChart.setUsageData(sortedList)
        adapter.updateData(sortedList, sortedList.firstOrNull()?.total ?: 0L)
    }

    private fun updateMap(map: MutableMap<Int, AppUsageInfo>, bucket: NetworkStats.Bucket) {
        val uid = bucket.uid
        val existing = map[uid]
        if (existing != null) {
            map[uid] = AppUsageInfo(existing.packageName, existing.received + bucket.rxBytes, existing.transmitted + bucket.txBytes)
        } else {
            val packages = packageManager.getPackagesForUid(uid)
            val name = when {
                uid == Process.SYSTEM_UID -> "Android System"
                uid == 0 -> "Root / OS"
                uid == 1013 -> "Media Server"
                uid == 1052 -> "Mobile Hotspot"
                !packages.isNullOrEmpty() -> packages[0]
                else -> "System Process ($uid)"
            }
            map[uid] = AppUsageInfo(name, bucket.rxBytes, bucket.txBytes)
        }
    }
}