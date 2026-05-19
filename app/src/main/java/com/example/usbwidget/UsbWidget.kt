package com.example.usbwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.RemoteViews

/**
 * UsbWidget — Widget écran d'accueil pour basculer le tethering USB en 1 tap.
 *
 * ⚠️  PRÉREQUIS : accorder la permission une seule fois via ADB :
 *     adb shell pm grant com.example.usbwidget android.permission.TETHER_PRIVILEGED
 *
 * Ajouter le widget :
 *     Appui long sur l'écran d'accueil → Widgets → "Modem USB" → glisser sur l'écran
 */
class UsbWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "UsbWidget"
        const val ACTION_TOGGLE = "com.example.usbwidget.TOGGLE"

        // Préfixes des interfaces réseau USB tethering (RNDIS / NCM)
        private val USB_IFACES = listOf("rndis", "usb0", "usb1", "ncm0", "ncm1")

        /** Retourne true si le tethering USB est actuellement actif. */
        fun isEnabled(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val method = ConnectivityManager::class.java.getDeclaredMethod("getTetheredIfaces")
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val ifaces = method.invoke(cm) as? Array<String> ?: return false
                ifaces.any { iface -> USB_IFACES.any { iface.startsWith(it, true) } }
            } catch (e: Exception) {
                Log.e(TAG, "isEnabled: ${e.message}")
                false
            }
        }

        /** Active ou désactive le tethering USB. Retourne true si succès. */
        fun toggle(context: Context, enable: Boolean): Boolean {
            return try {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val method = ConnectivityManager::class.java
                    .getDeclaredMethod("setUsbTethering", Boolean::class.java)
                method.isAccessible = true
                val code = method.invoke(cm, enable) as? Int ?: 0
                Log.i(TAG, "setUsbTethering($enable) → code=$code")
                code == 0  // 0 = TETHER_ERROR_NO_ERROR
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission refusée — accordez via ADB")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Erreur toggle: ${e.message}")
                false
            }
        }

        /** Vibre brièvement pour feedback tactile. */
        fun vibrate(context: Context) {
            try {
                val effect = VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)
                        .defaultVibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java).vibrate(effect)
                }
            } catch (_: Exception) {}
        }

        /** Rafraîchit tous les widgets actifs sur l'écran. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, UsbWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, UsbWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    // ─── Cycle de vie ────────────────────────────────────────────────

    /** Appelé lors de l'ajout ou du rafraîchissement du widget. */
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, mgr, it) }
    }

    /** Intercepte le broadcast ACTION_TOGGLE envoyé par l'appui sur le widget. */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TOGGLE) {
            val wasEnabled = isEnabled(context)
            val success = toggle(context, !wasEnabled)

            if (success) {
                vibrate(context)
                Log.i(TAG, "Tethering ${if (!wasEnabled) "activé" else "désactivé"}")
            } else {
                Log.w(TAG, "Échec toggle — permission accordée ?")
            }

            // Léger délai pour laisser le système appliquer le changement
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                refreshAll(context)
            }, 500)
        }
    }

    // ─── Rendu visuel ────────────────────────────────────────────────

    /**
     * Met à jour l'apparence d'un widget en fonction de l'état tethering actuel.
     *
     * État ON → fond vert, toggle activé, texte "Actif"
     * État OFF → fond gris, toggle désactivé, texte "Inactif"
     */
    private fun renderWidget(context: Context, mgr: AppWidgetManager, id: Int) {
        val isOn = isEnabled(context)
        val hasPerm = context.checkSelfPermission("android.permission.TETHER_PRIVILEGED") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Icône
        views.setImageViewResource(
            R.id.widget_icon,
            if (isOn) R.drawable.ic_usb_on else R.drawable.ic_usb_off
        )

        // Texte de statut
        views.setTextViewText(
            R.id.widget_status,
            when {
                !hasPerm -> context.getString(R.string.w_no_perm)
                isOn     -> context.getString(R.string.w_on)
                else     -> context.getString(R.string.w_off)
            }
        )

        // Couleur du texte de statut
        views.setTextColor(
            R.id.widget_status,
            context.getColor(if (isOn) R.color.active else R.color.inactive)
        )

        // Image du toggle ON/OFF
        views.setImageViewResource(
            R.id.widget_toggle_img,
            if (isOn) R.drawable.toggle_on else R.drawable.toggle_off
        )

        // Fond du widget (vert actif, gris neutre inactif)
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (isOn) R.drawable.bg_on else R.drawable.bg_off
        )

        // PendingIntent : tap sur n'importe quelle zone → ACTION_TOGGLE
        val toggleIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, UsbWidget::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, toggleIntent)

        mgr.updateAppWidget(id, views)
    }
}
