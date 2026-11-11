package com.example.swiftcache

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.swiftcache.model.AppCacheInfo
import com.example.swiftcache.service.CacheAccessibilityService
import com.example.swiftcache.utils.CacheScanner
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var permissionLayout: LinearLayout
    private lateinit var tvPermTitle: TextView
    private lateinit var tvPermDesc: TextView
    private lateinit var btnGrantPermission: MaterialButton
    private lateinit var btnCleanAll: MaterialButton
    private lateinit var rvApps: RecyclerView
    
    private val appList = mutableListOf<AppCacheInfo>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        permissionLayout = findViewById(R.id.permissionLayout)
        tvPermTitle = findViewById(R.id.tvPermTitle)
        tvPermDesc = findViewById(R.id.tvPermDesc)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        btnCleanAll = findViewById(R.id.btnCleanAll)
        rvApps = findViewById(R.id.rvApps)

        setupRecyclerView()

        btnCleanAll.setOnClickListener {
            val selectedApps = appList.filter { it.isSelected }
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, "Heç bir tətbiq seçilməyib.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "${selectedApps.size} tətbiq üçün təmizləmə başlayır...", Toast.LENGTH_SHORT).show()
            // Hissə 3-də bura Accessibility Service-i işə salacaq
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndScan()
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter(appList) { position, isChecked ->
            appList[position].isSelected = isChecked
        }
        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter
    }

    private fun checkPermissionsAndScan() {
        val hasUsagePermission = hasUsageAccessPermission()
        val hasAccessibilityPermission = isAccessibilityServiceEnabled()

        if (!hasUsagePermission) {
            showPermissionUI(
                "İstifadə İcazəsi Lazımdır",
                "Tətbiqlərin keş ölçüsünü hesablamaq üçün sistem istifadə statistikasına icazə verin.",
                "İcazə Ver"
            ) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } else if (!hasAccessibilityPermission) {
            showPermissionUI(
                "Əlçatanlıq İcazəsi Lazımdır",
                "Keşi avtomatik təmizləmək üçün əlçatanlıq xidmətini aktivləşdirin.",
                "Xidməti Aktivləşdir"
            ) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        } else {
            permissionLayout.visibility = LinearLayout.GONE
            startScanning()
        }
    }

    private fun startScanning() {
        tvStatus.text = "Keş yaddaşı skan edilir..."
        btnCleanAll.isEnabled = false
        
        lifecycleScope.launch {
            val result = CacheScanner.scanApps(this@MainActivity)
            appList.clear()
            appList.addAll(result)
            adapter.notifyDataSetChanged()
            
            val totalCache = result.sumOf { it.cacheSize }
            tvStatus.text = "Ümumi Keş: ${CacheScanner.formatSize(totalCache)}"
            btnCleanAll.isEnabled = true
        }
    }

    private fun showPermissionUI(title: String, desc: String, btnText: String, action: () -> Unit) {
        permissionLayout.visibility = LinearLayout.VISIBLE
        tvPermTitle.text = title
        tvPermDesc.text = desc
        btnGrantPermission.text = btnText
        btnGrantPermission.setOnClickListener { action() }
        tvStatus.text = "İcazə gözlənilir..."
    }

    private fun hasUsageAccessPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${CacheAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabledServices.split(":").any { it.equals(service, ignoreCase = true) }
    }

    inner class AppAdapter(
        private val items: List<AppCacheInfo>,
        private val onCheckedChange: (Int, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
            val tvAppName: TextView = view.findViewById(R.id.tvAppName)
            val tvCacheSize: TextView = view.findViewById(R.id.tvCacheSize)
            val cbSelect: MaterialCheckBox = view.findViewById(R.id.cbSelect)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_cache, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvAppName.text = item.appName
            holder.tvCacheSize.text = CacheScanner.formatSize(item.cacheSize)
            holder.ivAppIcon.setImageDrawable(item.icon)
            
            holder.cbSelect.setOnCheckedChangeListener(null)
            holder.cbSelect.isChecked = item.isSelected
            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(position, isChecked)
            }
        }

        override fun getItemCount() = items.size
    }
}
