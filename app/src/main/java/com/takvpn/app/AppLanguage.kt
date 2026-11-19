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

    private val translations = mapOf(
        "ru" to mapOf(
            "ابزارها" to "Инструменты",
            "اشتراک‌ها" to "Подписки",
            "اتصال و شبکه" to "Подключение и сеть",
            "سیستم" to "Система",
            "چسباندن کانفیگ" to "Вставить конфиг",
            "اسکن QR" to "Сканировать QR",
            "با دوربین" to "Камера",
            "افزودن اشتراک" to "Добавить подписку",
            "ساخت دستی" to "Вручную",
            "پینگ همه" to "Пинг всех",
            "مرتب‌سازی" to "Сортировка",
            "بر اساس پینگ" to "По пингу",
            "حذف ناموفق‌ها" to "Удалить неудачные",
            "زبان برنامه" to "Язык приложения",
            "حالت تاریک" to "Тёмная тема",
            "بکاپ" to "Резервная копия",
            "درباره" to "О приложении"
        ),
        "zh" to mapOf(
            "ابزارها" to "工具",
            "اشتراک‌ها" to "订阅",
            "اتصال و شبکه" to "连接与网络",
            "سیستم" to "系统",
            "چسباندن کانفیگ" to "粘贴配置",
            "اسکن QR" to "扫描二维码",
            "با دوربین" to "使用相机",
            "افزودن اشتراک" to "添加订阅",
            "ساخت دستی" to "手动添加",
            "پینگ همه" to "全部测速",
            "مرتب‌سازی" to "排序",
            "بر اساس پینگ" to "按延迟",
            "حذف ناموفق‌ها" to "删除失败项",
            "زبان برنامه" to "应用语言",
            "حالت تاریک" to "深色模式",
            "بکاپ" to "备份",
            "درباره" to "关于"
        ),
        "tr" to mapOf(
            "ابزارها" to "Araçlar",
            "اشتراک‌ها" to "Abonelikler",
            "اتصال و شبکه" to "Bağlantı ve ağ",
            "سیستم" to "Sistem",
            "چسباندن کانفیگ" to "Yapılandırmayı yapıştır",
            "اسکن QR" to "QR tara",
            "با دوربین" to "Kamera ile",
            "افزودن اشتراک" to "Abonelik ekle",
            "ساخت دستی" to "Manuel",
            "پینگ همه" to "Tümünü ping",
            "مرتب‌سازی" to "Sırala",
            "بر اساس پینگ" to "Ping'e göre",
            "حذف ناموفق‌ها" to "Başarısızları sil",
            "زبان برنامه" to "Uygulama dili",
            "حالت تاریک" to "Karanlık mod",
            "بکاپ" to "Yedek",
            "درباره" to "Hakkında"
        ),
        "de" to mapOf(
            "ابزارها" to "Werkzeuge",
            "اشتراک‌ها" to "Abonnements",
            "اتصال و شبکه" to "Verbindung & Netzwerk",
            "سیستم" to "System",
            "چسباندن کانفیگ" to "Konfiguration einfügen",
            "اسکن QR" to "QR scannen",
            "با دوربین" to "Mit Kamera",
            "افزودن اشتراک" to "Abonnement hinzufügen",
            "ساخت دستی" to "Manuell",
            "پینگ همه" to "Alle pingen",
            "مرتب‌سازی" to "Sortieren",
            "بر اساس پینگ" to "Nach Ping",
            "حذف ناموفق‌ها" to "Fehlgeschlagene entfernen",
            "زبان برنامه" to "App-Sprache",
            "حالت تاریک" to "Dunkelmodus",
            "بکاپ" to "Backup",
            "درباره" to "Über"
        ),
        "ar" to mapOf(
            "ابزارها" to "الأدوات",
            "اشتراک‌ها" to "الاشتراكات",
            "اتصال و شبکه" to "الاتصال والشبكة",
            "سیستم" to "النظام",
            "چسباندن کانفیگ" to "لصق الإعداد",
            "اسکن QR" to "مسح QR",
            "با دوربین" to "باستخدام الكاميرا",
            "افزودن اشتراک" to "إضافة اشتراك",
            "ساخت دستی" to "يدوي",
            "پینگ همه" to "اختبار الجميع",
            "مرتب‌سازی" to "ترتيب",
            "بر اساس پینگ" to "حسب زمن الاستجابة",
            "حذف ناموفق‌ها" to "حذف الفاشل",
            "زبان برنامه" to "لغة التطبيق",
            "حالت تاریک" to "الوضع الداكن",
            "بکاپ" to "نسخة احتياطية",
            "درباره" to "حول"
        ),
        "es" to mapOf(
            "ابزارها" to "Herramientas",
            "اشتراک‌ها" to "Suscripciones",
            "اتصال و شبکه" to "Conexión y red",
            "سیستم" to "Sistema",
            "چسباندن کانفیگ" to "Pegar configuración",
            "اسکن QR" to "Escanear QR",
            "با دوربین" to "Con cámara",
            "افزودن اشتراک" to "Añadir suscripción",
            "ساخت دستی" to "Manual",
            "پینگ همه" to "Ping a todos",
            "مرتب‌سازی" to "Ordenar",
            "بر اساس پینگ" to "Por ping",
            "حذف ناموفق‌ها" to "Eliminar fallidos",
            "زبان برنامه" to "Idioma de la app",
            "حالت تاریک" to "Modo oscuro",
            "بکاپ" to "Copia de seguridad",
            "درباره" to "Acerca de"
        ),
        "it" to mapOf(
            "ابزارها" to "Strumenti",
            "اشتراک‌ها" to "Abbonamenti",
            "اتصال و شبکه" to "Connessione e rete",
            "سیستم" to "Sistema",
            "چسباندن کانفیگ" to "Incolla configurazione",
            "اسکن QR" to "Scansiona QR",
            "با دوربین" to "Con fotocamera",
            "افزودن اشتراک" to "Aggiungi abbonamento",
            "ساخت دستی" to "Manuale",
            "پینگ همه" to "Ping tutti",
            "مرتب‌سازی" to "Ordina",
            "بر اساس پینگ" to "Per ping",
            "حذف ناموفق‌ها" to "Rimuovi non riusciti",
            "زبان برنامه" to "Lingua app",
            "حالت تاریک" to "Tema scuro",
            "بکاپ" to "Backup",
            "درباره" to "Informazioni"
        )
    )

    fun translate(text: String, language: String): String = translations[language]?.get(text) ?: text

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
