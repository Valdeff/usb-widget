package com.example.usbwidget

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.usbwidget.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Activité minimale — sert uniquement à :
 *   1. Expliquer comment ajouter le widget sur l'écran d'accueil
 *   2. Afficher/copier la commande ADB pour la permission
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        val adbCmd = "adb shell pm grant $packageName android.permission.TETHER_PRIVILEGED"

        b.tvPermStatus.text = if (UsbWidget.isEnabled(this))
            "✅ Tethering USB actuellement actif"
        else if (checkSelfPermission("android.permission.TETHER_PRIVILEGED") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED)
            "✅ Permission accordée — widget prêt"
        else
            "⚠️ Permission manquante — voir ci-dessous"

        b.tvAdbCommand.text = adbCmd

        b.btnCopy.setOnClickListener {
            val cb = getSystemService(ClipboardManager::class.java)
            cb.setPrimaryClip(ClipData.newPlainText("ADB", adbCmd))
            Toast.makeText(this, "Commande copiée !", Toast.LENGTH_SHORT).show()
        }

        b.btnHowTo.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Ajouter le widget")
                .setMessage("1. Retournez à l'écran d'accueil\n2. Appui long sur une zone vide\n3. Appuyez sur « Widgets »\n4. Faites défiler jusqu'à « Modem USB »\n5. Maintenez et glissez sur l'écran d'accueil\n\nLe widget s'affiche immédiatement !")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
