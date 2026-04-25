package com.midxv.InternetMeter

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppUsageInfo(
    val packageName: String,
    val received: Long,
    val transmitted: Long
) {
    val total: Long get() = received + transmitted
}

class AppUsageAdapter(
    private var usageList: List<AppUsageInfo>,
    private val packageManager: PackageManager,
    private val maxUsage: Long
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvAppUsage: TextView = view.findViewById(R.id.tvAppUsage)
        val pbUsageGraph: ProgressBar = view.findViewById(R.id.pbUsageGraph)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = usageList[position]
        try {
            val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val appIcon: Drawable = packageManager.getApplicationIcon(appInfo)
            holder.tvAppName.text = appName
            holder.ivAppIcon.setImageDrawable(appIcon)
        } catch (e: PackageManager.NameNotFoundException) {
            holder.tvAppName.text = item.packageName
        }

        val mb = item.total / (1024f * 1024f)
        holder.tvAppUsage.text = String.format("%.2f MB", mb)

        if (maxUsage > 0) {
            val progress = ((item.total.toFloat() / maxUsage.toFloat()) * 100).toInt()
            holder.pbUsageGraph.progress = progress
        } else {
            holder.pbUsageGraph.progress = 0
        }
    }

    override fun getItemCount() = usageList.size

    fun updateData(newList: List<AppUsageInfo>, newMax: Long) {
        usageList = newList
        notifyDataSetChanged()
    }
}