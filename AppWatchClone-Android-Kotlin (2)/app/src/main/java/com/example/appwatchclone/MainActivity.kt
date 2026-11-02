package com.example.appwatchclone

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var cardPermissionWarning: MaterialCardView
    private lateinit var btnGrantPermission: Button
    private lateinit var switchService: SwitchMaterial
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvHistoryHeader: TextView
    private lateinit var btnClearHistory: TextView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var rvUsageHistory: RecyclerView

    private lateinit var adapter: AppUsageAdapter

    // Demande de permission notification pour Android 13+
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Permission notification accordée", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupListeners()
        checkNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
        updateServiceSwitchState()
        loadUsageHistory()
    }

    private fun initViews() {
        cardPermissionWarning = findViewById(R.id.cardPermissionWarning)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        switchService = findViewById(R.id.switchService)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        tvHistoryHeader = findViewById(R.id.tvHistoryHeader)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        rvUsageHistory = findViewById(R.id.rvUsageHistory)
    }

    private fun setupRecyclerView() {
        adapter = AppUsageAdapter(this, emptyList()) { item ->
            // ACTION PRINCIPALE : Rediriger DIRECTEMENT vers les paramètres système de l'application
            redirectToApplicationSettings(item.packageName)
        }
        rvUsageHistory.layoutManager = LinearLayoutManager(this)
        rvUsageHistory.adapter = adapter
    }

    private fun setupListeners() {
        // Bouton pour activer la permission spéciale Usage Access
        btnGrantPermission.setOnClickListener {
            openUsageAccessSettings()
        }

        // Interrupteur pour démarrer / arrêter la surveillance Foreground Service
        switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!hasUsageStatsPermission()) {
                    switchService.isChecked = false
                    Toast.makeText(this, "Veuillez d'abord activer l'autorisation système", Toast.LENGTH_LONG).show()
                    openUsageAccessSettings()
                    return@setOnCheckedChangeListener
                }
                startMonitorService()
            } else {
                stopMonitorService()
            }
        }

        // Pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener {
            loadUsageHistory()
            swipeRefreshLayout.isRefreshing = false
        }

        // Vider l'historique
        btnClearHistory.setOnClickListener {
            getSharedPreferences("appwatch_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("apps", "[]")
                .apply()
            loadUsageHistory()
            Toast.makeText(this, "Historique effacé", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * REDIRECTION DIRECTE : Ouvre l'écran des informations de l'application dans les Paramètres système Android.
     * Permet à l'utilisateur de voir la mémoire, les autorisations, de forcer l'arrêt ou de la désinstaller immédiatement.
     */
    private fun redirectToApplicationSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Ouverture des paramètres de : $packageName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir les paramètres de $packageName", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startMonitorService() {
        val intent = Intent(this, UsageMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        tvServiceStatus.text = "Statut : Actif en arrière-plan (Surveillance active)"
        tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun stopMonitorService() {
        val intent = Intent(this, UsageMonitorService::class.java)
        stopService(intent)
        tvServiceStatus.text = "Statut : Inactif"
        tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
    }

    private fun updatePermissionState() {
        val granted = hasUsageStatsPermission()
        cardPermissionWarning.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (UsageMonitorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateServiceSwitchState() {
        val running = isServiceRunning()
        switchService.isChecked = running
        if (running) {
            tvServiceStatus.text = "Statut : Actif en arrière-plan (Surveillance active)"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            tvServiceStatus.text = "Statut : Inactif"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    private fun loadUsageHistory() {
        val prefs = getSharedPreferences("appwatch_prefs", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("apps", "[]") ?: "[]"

        val items = mutableListOf<AppUsageItem>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pkg = obj.optString("pkg", "")
                val time = obj.optLong("time", 0L)
                if (pkg.isNotEmpty()) {
                    items.add(AppUsageItem(pkg, time))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        adapter.updateData(items)
        tvHistoryHeader.text = "Applications récentes (${items.size})"

        if (items.isEmpty()) {
            layoutEmptyState.visibility = View.VISIBLE
            rvUsageHistory.visibility = View.GONE
        } else {
            layoutEmptyState.visibility = View.GONE
            rvUsageHistory.visibility = View.VISIBLE
        }
    }
}