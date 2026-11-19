package com.takvpn.app

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Stores TakVPN's UI language independently from the device language.
 * English is the first-install default.
 */
object AppLanguage {
    private const val PREFS = "takvpn"
    private const val KEY = "app_language"

    private val languages = listOf(
        "en" to "English",
        "fa" to "فارسی",
        "fr" to "Français",
        "ru" to "Русский",
        "zh" to "中文",
        "tr" to "Türkçe",
        "de" to "Deutsch",
        "ar" to "العربية",
        "es" to "Español",
        "it" to "Italiano"
    )

    fun code(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "en") ?: "en"

    fun currentLabel(context: Context): String =
        languages.firstOrNull { it.first == code(context) }?.second ?: "English"

    fun apply(context: Context) {
        val selected = code(context)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selected))
    }

    fun showChooser(context: Context) {
        val current = code(context)
        val labels = languages.map { it.second }.toTypedArray()
        val selectedIndex = languages.indexOfFirst { it.first == current }.coerceAtLeast(0
)
        AlertDialog.Builder(context)
            .setTitle("App language / زبان برنامه")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val selectedCode = languages[which].first
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, selectedCode).apply()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedCode))
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
