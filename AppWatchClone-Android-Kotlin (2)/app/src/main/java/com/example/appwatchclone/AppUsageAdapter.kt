package com.example.appwatchclone

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUsageAdapter(
    private val context: Context,
    private var items: List<AppUsageItem>,
    private val onItemClick: (AppUsageItem) -> Unit
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    private val packageManager: PackageManager = context.packageManager
    private val timeFormat = SimpleDateFormat("HH:mm:ss (dd/MM)", Locale.getDefault())

    fun updateData(newItems: List<AppUsageItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        // Récupérer le nom de l'application et son icône officielle
        var appName = item.packageName
        var appIcon: Drawable? = null

        try {
            val appInfo = packageManager.getApplicationInfo(item.packageName, 0)
            appName = packageManager.getApplicationLabel(appInfo).toString()
            appIcon = packageManager.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            // Application peut avoir été désinstallée ou être un composant système masqué
            appIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        }

        holder.tvAppName.text = appName
        holder.tvPackageName.text = item.packageName
        holder.tvTimestamp.text = "🕒 Détecté à : " + timeFormat.format(Date(item.timestamp))
        holder.ivAppIcon.setImageDrawable(appIcon)

        // Clic sur toute la carte : REDIRECTION DIRECTE VERS LES PARAMÈTRES
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // Clic sur le bouton : REDIRECTION DIRECTE VERS LES PARAMÈTRES
        holder.btnOpenSettings.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = itemView.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = itemView.findViewById(R.id.tvPackageName)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val btnOpenSettings: Button = itemView.findViewById(R.id.btnOpenSettings)
    }
}