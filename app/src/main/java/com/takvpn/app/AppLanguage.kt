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

    private val actionTranslations = mapOf(
        "en" to mapOf(
            "کلیپ‌بورد" to "Clipboard", "چسباندن کانفیگ" to "Paste config", "لینک سابسکریپشن" to "Subscription link",
            "ساخت دستی" to "Manual config", "فرم خالی کانفیگ" to "Blank config form", "سریع‌ترین" to "Fastest",
            "تست و اتصال خودکار" to "Test and connect automatically", "پینگ همه" to "Ping all", "تست همه‌ی کانفیگ‌ها" to "Test all configs",
            "مرتب‌سازی" to "Sort", "بر اساس پینگ" to "By ping", "حذف ناموفق‌ها" to "Remove failed",
            "کانفیگ‌های بدون پینگ" to "Configs with no ping", "به‌روزرسانی" to "Update", "همه‌ی اشتراک‌ها" to "All subscriptions",
            "مدیریت" to "Manage", "مشاهده و حذف" to "View and remove", "اتصال و شبکه" to "Connection & network",
            "سایت‌های .ir مستقیم" to ".ir sites direct", "اتصال هنگام باز شدن" to "Connect on launch", "انتخاب اپ‌ها" to "Select apps",
            "زبان برنامه" to "App language", "حالت تاریک" to "Dark mode", "تاریک / روشن" to "Dark / light",
            "لاگ" to "Logs", "رویدادها و خطاها" to "Events and errors", "بکاپ" to "Backup", "کپی همه‌ی کانفیگ‌ها" to "Copy all configs",
            "صفر کردن مصرف" to "Reset usage", "مصرف کل داده" to "Total data usage", "انتخاب پوسته" to "Choose theme",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Change background and card colors", "رنگ نوشته‌ها" to "Text colors",
            "تغییر مستقل رنگ متن‌ها" to "Change text colors separately", "بررسی نسخه" to "Check version", "نسخه‌ی جدید؟" to "New version?",
            "درباره" to "About", "نسخه و دانلود" to "Version and download"
        ),
        "fr" to mapOf(
            "کلیپ‌بورد" to "Presse-papiers", "چسباندن کانفیگ" to "Coller une config", "لینک سابسکریپشن" to "Lien d’abonnement",
            "ساخت دستی" to "Configuration manuelle", "فرم خالی کانفیگ" to "Formulaire vide", "سریع‌ترین" to "Le plus rapide",
            "تست و اتصال خودکار" to "Tester et se connecter automatiquement", "پینگ همه" to "Tester tout", "تست همه‌ی کانفیگ‌ها" to "Tester toutes les configs",
            "مرتب‌سازی" to "Trier", "بر اساس پینگ" to "Par ping", "حذف ناموفق‌ها" to "Supprimer les échecs",
            "کانفیگ‌های بدون پینگ" to "Configs sans ping", "به‌روزرسانی" to "Mettre à jour", "همه‌ی اشتراک‌ها" to "Tous les abonnements",
            "مدیریت" to "Gérer", "مشاهده و حذف" to "Afficher et supprimer", "اتصال و شبکه" to "Connexion et réseau",
            "سایت‌های .ir مستقیم" to "Sites .ir en direct", "اتصال هنگام باز شدن" to "Connexion au lancement", "انتخاب اپ‌ها" to "Choisir les apps",
            "زبان برنامه" to "Langue de l’application", "حالت تاریک" to "Mode sombre", "تاریک / روشن" to "Sombre / clair",
            "لاگ" to "Journal", "رویدادها و خطاها" to "Événements et erreurs", "بکاپ" to "Sauvegarde", "کپی همه‌ی کانفیگ‌ها" to "Copier toutes les configs",
            "صفر کردن مصرف" to "Réinitialiser l’utilisation", "مصرف کل داده" to "Consommation totale", "انتخاب پوسته" to "Choisir le thème",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Changer les couleurs du fond et des cartes", "رنگ نوشته‌ها" to "Couleurs du texte",
            "تغییر مستقل رنگ متن‌ها" to "Modifier les couleurs du texte séparément", "بررسی نسخه" to "Vérifier la version", "نسخه‌ی جدید؟" to "Nouvelle version ?",
            "درباره" to "À propos", "نسخه و دانلود" to "Version et téléchargement"
        ),
        "ru" to mapOf(
            "کلیپ‌بورد" to "Буфер обмена", "چسباندن کانفیگ" to "Вставить конфиг", "لینک سابسکریپشن" to "Ссылка подписки",
            "ساخت دستی" to "Создать вручную", "فرم خالی کانفیگ" to "Пустая форма конфига", "سریع‌ترین" to "Самый быстрый",
            "تست و اتصال خودکار" to "Проверить и подключиться автоматически", "پینگ همه" to "Пинг всех", "تست همه‌ی کانفیگ‌ها" to "Проверить все конфиги",
            "مرتب‌سازی" to "Сортировка", "بر اساس پینگ" to "По пингу", "حذف ناموفق‌ها" to "Удалить неудачные",
            "کانفیگ‌های بدون پینگ" to "Конфиги без пинга", "به‌روزرسانی" to "Обновить", "همه‌ی اشتراک‌ها" to "Все подписки",
            "مدیریت" to "Управление", "مشاهده و حذف" to "Просмотр и удаление", "اتصال و شبکه" to "Подключение и сеть",
            "سایت‌های .ir مستقیم" to "Сайты .ir напрямую", "اتصال هنگام باز شدن" to "Подключать при запуске", "انتخاب اپ‌ها" to "Выбрать приложения",
            "زبان برنامه" to "Язык приложения", "حالت تاریک" to "Тёмная тема", "تاریک / روشن" to "Тёмная / светлая",
            "لاگ" to "Журнал", "رویدادها و خطاها" to "События и ошибки", "بکاپ" to "Резервная копия", "کپی همه‌ی کانفیگ‌ها" to "Копировать все конфиги",
            "صفر کردن مصرف" to "Сбросить трафик", "مصرف کل داده" to "Общий расход", "انتخاب پوسته" to "Выбрать тему",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Изменить фон и карточки", "رنگ نوشته‌ها" to "Цвет текста",
            "تغییر مستقل رنگ متن‌ها" to "Отдельная настройка цвета текста", "بررسی نسخه" to "Проверить версию", "نسخه‌ی جدید؟" to "Новая версия?",
            "درباره" to "О приложении", "نسخه و دانلود" to "Версия и загрузка"
        ),
        "zh" to mapOf(
            "کلیپ‌بورد" to "剪贴板", "چسباندن کانفیگ" to "粘贴配置", "لینک سابسکریپشن" to "订阅链接",
            "ساخت دستی" to "手动创建", "فرم خالی کانفیگ" to "空配置表单", "سریع‌ترین" to "最快",
            "تست و اتصال خودکار" to "测试并自动连接", "پینگ همه" to "全部测速", "تست همه‌ی کانفیگ‌ها" to "测试所有配置",
            "مرتب‌سازی" to "排序", "بر اساس پینگ" to "按延迟", "حذف ناموفق‌ها" to "删除失败项",
            "کانفیگ‌های بدون پینگ" to "无延迟结果的配置", "به‌روزرسانی" to "更新", "همه‌ی اشتراک‌ها" to "所有订阅",
            "مدیریت" to "管理", "مشاهده و حذف" to "查看和删除", "اتصال و شبکه" to "连接与网络",
            "سایت‌های .ir مستقیم" to "直连 .ir 网站", "اتصال هنگام باز شدن" to "启动时连接", "انتخاب اپ‌ها" to "选择应用",
            "زبان برنامه" to "应用语言", "حالت تاریک" to "深色模式", "تاریک / روشن" to "深色 / 浅色",
            "لاگ" to "日志", "رویدادها و خطاها" to "事件和错误", "بکاپ" to "备份", "کپی همه‌ی کانفیگ‌ها" to "复制所有配置",
            "صفر کردن مصرف" to "重置流量", "مصرف کل داده" to "总流量", "انتخاب پوسته" to "选择主题",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "更改背景和卡片颜色", "رنگ نوشته‌ها" to "文字颜色",
            "تغییر مستقل رنگ متن‌ها" to "单独调整文字颜色", "بررسی نسخه" to "检查版本", "نسخه‌ی جدید؟" to "有新版本吗？",
            "درباره" to "关于", "نسخه و دانلود" to "版本与下载"
        ),
        "tr" to mapOf(
            "کلیپ‌بورد" to "Pano", "چسباندن کانفیگ" to "Yapılandırma yapıştır", "لینک سابسکریپشن" to "Abonelik bağlantısı",
            "ساخت دستی" to "Manuel oluştur", "فرم خالی کانفیگ" to "Boş yapılandırma formu", "سریع‌ترین" to "En hızlı",
            "تست و اتصال خودکار" to "Test et ve otomatik bağlan", "پینگ همه" to "Hepsini ping", "تست همه‌ی کانفیگ‌ها" to "Tüm yapılandırmaları test et",
            "مرتب‌سازی" to "Sırala", "بر اساس پینگ" to "Ping'e göre", "حذف ناموفق‌ها" to "Başarısızları sil",
            "کانفیگ‌های بدون پینگ" to "Ping alınamayan yapılandırmalar", "به‌روزرسانی" to "Güncelle", "همه‌ی اشتراک‌ها" to "Tüm abonelikler",
            "مدیریت" to "Yönet", "مشاهده و حذف" to "Görüntüle ve sil", "اتصال و شبکه" to "Bağlantı ve ağ",
            "سایت‌های .ir مستقیم" to ".ir sitelerine doğrudan", "اتصال هنگام باز شدن" to "Açılışta bağlan", "انتخاب اپ‌ها" to "Uygulamaları seç",
            "زبان برنامه" to "Uygulama dili", "حالت تاریک" to "Karanlık mod", "تاریک / روشن" to "Karanlık / aydınlık",
            "لاگ" to "Günlük", "رویدادها و خطاها" to "Olaylar ve hatalar", "بکاپ" to "Yedek", "کپی همه‌ی کانفیگ‌ها" to "Tüm yapılandırmaları kopyala",
            "صفر کردن مصرف" to "Kullanımı sıfırla", "مصرف کل داده" to "Toplam veri kullanımı", "انتخاب پوسته" to "Tema seç",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Arka plan ve kart renklerini değiştir", "رنگ نوشته‌ها" to "Yazı renkleri",
            "تغییر مستقل رنگ متن‌ها" to "Metin renklerini ayrı ayarla", "بررسی نسخه" to "Sürümü kontrol et", "نسخه‌ی جدید؟" to "Yeni sürüm?",
            "درباره" to "Hakkında", "نسخه و دانلود" to "Sürüm ve indirme"
        ),
        "de" to mapOf(
            "کلیپ‌بورد" to "Zwischenablage", "چسباندن کانفیگ" to "Konfiguration einfügen", "لینک سابسکریپشن" to "Abonnement-Link",
            "ساخت دستی" to "Manuell erstellen", "فرم خالی کانفیگ" to "Leeres Konfigurationsformular", "سریع‌ترین" to "Am schnellsten",
            "تست و اتصال خودکار" to "Testen und automatisch verbinden", "پینگ همه" to "Alle pingen", "تست همه‌ی کانفیگ‌ها" to "Alle Konfigurationen testen",
            "مرتب‌سازی" to "Sortieren", "بر اساس پینگ" to "Nach Ping", "حذف ناموفق‌ها" to "Fehlgeschlagene entfernen",
            "کانفیگ‌های بدون پینگ" to "Konfigurationen ohne Ping", "به‌روزرسانی" to "Aktualisieren", "همه‌ی اشتراک‌ها" to "Alle Abonnements",
            "مدیریت" to "Verwalten", "مشاهده و حذف" to "Anzeigen und löschen", "اتصال و شبکه" to "Verbindung und Netzwerk",
            "سایت‌های .ir مستقیم" to ".ir-Websites direkt", "اتصال هنگام باز شدن" to "Beim Start verbinden", "انتخاب اپ‌ها" to "Apps auswählen",
            "زبان برنامه" to "App-Sprache", "حالت تاریک" to "Dunkelmodus", "تاریک / روشن" to "Dunkel / hell",
            "لاگ" to "Protokoll", "رویدادها و خطاها" to "Ereignisse und Fehler", "بکاپ" to "Backup", "کپی همه‌ی کانفیگ‌ها" to "Alle Konfigurationen kopieren",
            "صفر کردن مصرف" to "Nutzung zurücksetzen", "مصرف کل داده" to "Gesamter Datenverbrauch", "انتخاب پوسته" to "Design wählen",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Hintergrund- und Kartenfarben ändern", "رنگ نوشته‌ها" to "Textfarben",
            "تغییر مستقل رنگ متن‌ها" to "Textfarben separat ändern", "بررسی نسخه" to "Version prüfen", "نسخه‌ی جدید؟" to "Neue Version?",
            "درباره" to "Über", "نسخه و دانلود" to "Version und Download"
        ),
        "ar" to mapOf(
            "کلیپ‌بورد" to "الحافظة", "چسباندن کانفیگ" to "لصق الإعداد", "لینک سابسکریپشن" to "رابط الاشتراك",
            "ساخت دستی" to "إنشاء يدوي", "فرم خالی کانفیگ" to "نموذج إعداد فارغ", "سریع‌ترین" to "الأسرع",
            "تست و اتصال خودکار" to "اختبار والاتصال تلقائيًا", "پینگ همه" to "اختبار الجميع", "تست همه‌ی کانفیگ‌ها" to "اختبار جميع الإعدادات",
            "مرتب‌سازی" to "ترتيب", "بر اساس پینگ" to "حسب زمن الاستجابة", "حذف ناموفق‌ها" to "حذف الفاشل",
            "کانفیگ‌های بدون پینگ" to "إعدادات بلا استجابة", "به‌روزرسانی" to "تحديث", "همه‌ی اشتراک‌ها" to "كل الاشتراكات",
            "مدیریت" to "إدارة", "مشاهده و حذف" to "عرض وحذف", "اتصال و شبکه" to "الاتصال والشبكة",
            "سایت‌های .ir مستقیم" to "مواقع .ir مباشرة", "اتصال هنگام باز شدن" to "الاتصال عند بدء التشغيل", "انتخاب اپ‌ها" to "اختيار التطبيقات",
            "زبان برنامه" to "لغة التطبيق", "حالت تاریک" to "الوضع الداكن", "تاریک / روشن" to "داكن / فاتح",
            "لاگ" to "السجل", "رویدادها و خطاها" to "الأحداث والأخطاء", "بکاپ" to "نسخة احتياطية", "کپی همه‌ی کانفیگ‌ها" to "نسخ جميع الإعدادات",
            "صفر کردن مصرف" to "إعادة تعيين الاستخدام", "مصرف کل داده" to "إجمالي البيانات", "انتخاب پوسته" to "اختيار المظهر",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "تغيير لون الخلفية والبطاقات", "رنگ نوشته‌ها" to "ألوان النص",
            "تغییر مستقل رنگ متن‌ها" to "تغيير ألوان النص بشكل مستقل", "بررسی نسخه" to "التحقق من الإصدار", "نسخه‌ی جدید؟" to "إصدار جديد؟",
            "درباره" to "حول", "نسخه و دانلود" to "الإصدار والتنزيل"
        ),
        "es" to mapOf(
            "کلیپ‌بورد" to "Portapapeles", "چسباندن کانفیگ" to "Pegar configuración", "لینک سابسکریپشن" to "Enlace de suscripción",
            "ساخت دستی" to "Crear manualmente", "فرم خالی کانفیگ" to "Formulario vacío", "سریع‌ترین" to "Más rápido",
            "تست و اتصال خودکار" to "Probar y conectar automáticamente", "پینگ همه" to "Probar todos", "تست همه‌ی کانفیگ‌ها" to "Probar todas las configs",
            "مرتب‌سازی" to "Ordenar", "بر اساس پینگ" to "Por ping", "حذف ناموفق‌ها" to "Eliminar fallidos",
            "کانفیگ‌های بدون پینگ" to "Configs sin ping", "به‌روزرسانی" to "Actualizar", "همه‌ی اشتراک‌ها" to "Todas las suscripciones",
            "مدیریت" to "Gestionar", "مشاهده و حذف" to "Ver y eliminar", "اتصال و شبکه" to "Conexión y red",
            "سایت‌های .ir مستقیم" to "Sitios .ir directos", "اتصال هنگام باز شدن" to "Conectar al iniciar", "انتخاب اپ‌ها" to "Elegir apps",
            "زبان برنامه" to "Idioma de la app", "حالت تاریک" to "Modo oscuro", "تاریک / روشن" to "Oscuro / claro",
            "لاگ" to "Registro", "رویدادها و خطاها" to "Eventos y errores", "بکاپ" to "Copia de seguridad", "کپی همه‌ی کانفیگ‌ها" to "Copiar todas las configs",
            "صفر کردن مصرف" to "Restablecer uso", "مصرف کل داده" to "Uso total de datos", "انتخاب پوسته" to "Elegir tema",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Cambiar colores de fondo y tarjetas", "رنگ نوشته‌ها" to "Colores del texto",
            "تغییر مستقل رنگ متن‌ها" to "Cambiar colores del texto por separado", "بررسی نسخه" to "Comprobar versión", "نسخه‌ی جدید؟" to "¿Nueva versión?",
            "درباره" to "Acerca de", "نسخه و دانلود" to "Versión y descarga"
        ),
        "it" to mapOf(
            "کلیپ‌بورد" to "Clipboard", "چسباندن کانفیگ" to "Incolla configurazione", "لینک سابسکریپشن" to "Link abbonamento",
            "ساخت دستی" to "Crea manualmente", "فرم خالی کانفیگ" to "Modulo vuoto", "سریع‌ترین" to "Più veloce",
            "تست و اتصال خودکار" to "Test e connessione automatica", "پینگ همه" to "Ping tutti", "تست همه‌ی کانفیگ‌ها" to "Testa tutte le config",
            "مرتب‌سازی" to "Ordina", "بر اساس پینگ" to "Per ping", "حذف ناموفق‌ها" to "Rimuovi non riusciti",
            "کانفیگ‌های بدون پینگ" to "Config senza ping", "به‌روزرسانی" to "Aggiorna", "همه‌ی اشتراک‌ها" to "Tutti gli abbonamenti",
            "مدیریت" to "Gestisci", "مشاهده و حذف" to "Visualizza ed elimina", "اتصال و شبکه" to "Connessione e rete",
            "سایت‌های .ir مستقیم" to "Siti .ir diretti", "اتصال هنگام باز شدن" to "Connetti all’avvio", "انتخاب اپ‌ها" to "Seleziona app",
            "زبان برنامه" to "Lingua app", "حالت تاریک" to "Tema scuro", "تاریک / روشن" to "Scuro / chiaro",
            "لاگ" to "Registro", "رویدادها و خطاها" to "Eventi ed errori", "بکاپ" to "Backup", "کپی همه‌ی کانفیگ‌ها" to "Copia tutte le config",
            "صفر کردن مصرف" to "Azzera utilizzo", "مصرف کل داده" to "Consumo totale", "انتخاب پوسته" to "Scegli tema",
            "تغییر رنگ پس‌زمینه و کارت‌ها" to "Cambia colori sfondo e schede", "رنگ نوشته‌ها" to "Colori testo",
            "تغییر مستقل رنگ متن‌ها" to "Modifica colori testo separatamente", "بررسی نسخه" to "Controlla versione", "نسخه‌ی جدید؟" to "Nuova versione?",
            "درباره" to "Info", "نسخه و دانلود" to "Versione e download"
        )
    )

    private val dialogTranslations = mapOf(
        "en" to mapOf("App language / زبان برنامه" to "App language", "Cancel" to "Cancel"),
        "fr" to mapOf("App language / زبان برنامه" to "Langue de l’application", "Cancel" to "Annuler"),
        "ru" to mapOf("App language / زبان برنامه" to "Язык приложения", "Cancel" to "Отмена"),
        "zh" to mapOf("App language / زبان برنامه" to "应用语言", "Cancel" to "取消"),
        "tr" to mapOf("App language / زبان برنامه" to "Uygulama dili", "Cancel" to "İptal"),
        "de" to mapOf("App language / زبان برنامه" to "App-Sprache", "Cancel" to "Abbrechen"),
        "ar" to mapOf("App language / زبان برنامه" to "لغة التطبيق", "Cancel" to "إلغاء"),
        "es" to mapOf("App language / زبان برنامه" to "Idioma de la aplicación", "Cancel" to "Cancelar"),
        "it" to mapOf("App language / زبان برنامه" to "Lingua dell’app", "Cancel" to "Annulla"),
        "fa" to mapOf("App language / زبان برنامه" to "زبان برنامه", "Cancel" to "لغو")
    )

    private val runtimeTranslations = mapOf(
        "en" to mapOf("مصرف کل:" to "Total usage:", "مصرف:" to "Usage:", "در حال پیدا کردن بهترین کانفیگ..." to "Finding the best config...", "هیچ کانفیگی پینگ نداد" to "No config responded to ping", "در حال تست اتصال..." to "Testing connection...", "در حال دریافت IP خروجی واقعی تونل..." to "Getting real tunnel exit IP...", "تشخیص IP خروجی تونل ناموفق بود" to "Failed to detect tunnel exit IP", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Disconnected. Tap connect to start", "⚡ سوییچ هوشمند" to "⚡ Smart switch", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "Automatically finds and connects to the best config", "کانفیگی وجود نداره" to "No configs available", "کانفیگ اضافه شد" to "Config added", "کانفیگ معتبری اضافه نشد" to "No valid config was added"),
        "fr" to mapOf("مصرف کل:" to "Utilisation totale :", "مصرف:" to "Utilisation :", "در حال پیدا کردن بهترین کانفیگ..." to "Recherche de la meilleure config...", "هیچ کانفیگی پینگ نداد" to "Aucune config ne répond au ping", "در حال تست اتصال..." to "Test de connexion...", "در حال دریافت IP خروجی واقعی تونل..." to "Obtention de l’IP de sortie réelle...", "تشخیص IP خروجی تونل ناموفق بود" to "Échec de la détection de l’IP de sortie", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Déconnecté. Appuyez sur connecter", "⚡ سوییچ هوشمند" to "⚡ Commutation intelligente", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "Trouve et connecte automatiquement la meilleure config", "کانفیگی وجود نداره" to "Aucune config disponible", "کانفیگ اضافه شد" to "Config ajoutée", "کانفیگ معتبری اضافه نشد" to "Aucune config valide ajoutée"),
        "ru" to mapOf("مصرف کل:" to "Всего использовано:", "مصرف:" to "Использовано:", "در حال پیدا کردن بهترین کانفیگ..." to "Поиск лучшей конфигурации...", "هیچ کانفیگی پینگ نداد" to "Нет конфигураций с ответом на ping", "در حال تست اتصال..." to "Проверка подключения...", "در حال دریافت IP خروجی واقعی تونل..." to "Получение реального IP туннеля...", "تشخیص IP خروجی تونل ناموفق بود" to "Не удалось определить IP туннеля", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Отключено. Нажмите подключение", "⚡ سوییچ هوشمند" to "⚡ Умное переключение", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "Автоматически выбирает и подключает лучшую конфигурацию", "کانفیگی وجود نداره" to "Нет конфигураций", "کانفیگ اضافه شد" to "Конфигурация добавлена", "کانفیگی معتبر اضافه نشد" to "Не добавлено ни одной допустимой конфигурации"),
        "zh" to mapOf("مصرف کل:" to "总使用量：", "مصرف:" to "使用量：", "در حال پیدا کردن بهترین کانفیگ..." to "正在寻找最佳配置…", "هیچ کانفیگی پینگ نداد" to "没有配置响应 ping", "در حال تست اتصال..." to "正在测试连接…", "در حال دریافت IP خروجی واقعی تونل..." to "正在获取隧道真实出口 IP…", "تشخیص IP خروجی تونل ناموفق بود" to "无法检测隧道出口 IP", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "已断开。点击连接按钮", "⚡ سوییچ هوشمند" to "⚡ 智能切换", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "自动查找并连接最佳配置", "کانفیگی وجود نداره" to "没有配置", "کانفیگ اضافه شد" to "已添加配置", "کانفیگی معتبر اضافه نشد" to "未添加有效配置"),
        "tr" to mapOf("مصرف کل:" to "Toplam kullanım:", "مصرف:" to "Kullanım:", "در حال پیدا کردن بهترین کانفیگ..." to "En iyi yapılandırma aranıyor...", "هیچ کانفیگی پینگ نداد" to "Hiçbir yapılandırma ping yanıtı vermedi", "در حال تست اتصال..." to "Bağlantı test ediliyor...", "در حال دریافت IP خروجی واقعی تونل..." to "Gerçek tünel çıkış IP'si alınıyor...", "تشخیص IP خروجی تونل ناموفق بود" to "Tünel çıkış IP'si belirlenemedi", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Bağlantı kesildi. Bağlan düğmesine dokunun", "⚡ سوییچ هوشمند" to "⚡ Akıllı geçiş", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "En iyi yapılandırmayı otomatik bulur ve bağlanır", "کانفیگی وجود نداره" to "Yapılandırma yok", "کانفیگ اضافه شد" to "Yapılandırma eklendi", "کانفیگی معتبر اضافه نشد" to "Geçerli yapılandırma eklenmedi"),
        "de" to mapOf("مصرف کل:" to "Gesamtnutzung:", "مصرف:" to "Verbrauch:", "در حال پیدا کردن بهترین کانفیگ..." to "Beste Konfiguration wird gesucht...", "هیچ کانفیگی پینگ نداد" to "Keine Konfiguration antwortet auf Ping", "در حال تست اتصال..." to "Verbindung wird getestet...", "در حال دریافت IP خروجی واقعی تونل..." to "Echte Tunnel-Ausgangs-IP wird abgerufen...", "تشخیص IP خروجی تونل ناموفق بود" to "Tunnel-Ausgangs-IP konnte nicht erkannt werden", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Getrennt. Zum Verbinden auf Connect tippen", "⚡ سوییچ هوشمند" to "⚡ Intelligenter Wechsel", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "Findet und verbindet automatisch die beste Konfiguration", "کانفیگی وجود نداره" to "Keine Konfiguration vorhanden", "کانفیگ اضافه شد" to "Konfiguration hinzugefügt", "کانفیگی معتبر اضافه نشد" to "Keine gültige Konfiguration hinzugefügt"),
        "ar" to mapOf("مصرف کل:" to "إجمالي الاستخدام:", "مصرف:" to "الاستخدام:", "در حال پیدا کردن بهترین کانفیگ..." to "جارٍ البحث عن أفضل إعداد...", "هیچ کانفیگی پینگ نداد" to "لم يستجب أي إعداد للـ ping", "در حال تست اتصال..." to "جارٍ اختبار الاتصال...", "در حال دریافت IP خروجی واقعی تونل..." to "جارٍ جلب عنوان IP الحقيقي للنفق...", "تشخیص IP خروجی تونل ناموفق بود" to "تعذر تحديد IP خروج النفق", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "غير متصل. اضغط زر الاتصال", "⚡ سوییچ هوشمند" to "⚡ التبديل الذكي", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "يعثر تلقائيًا على أفضل إعداد ويتصل به", "کانفیگی وجود نداره" to "لا توجد إعدادات", "کانفیگ اضافه شد" to "تمت إضافة الإعداد", "کانفیگی معتبر اضافه نشد" to "لم تتم إضافة إعداد صالح"),
        "es" to mapOf("مصرف کل:" to "Uso total:", "مصرف:" to "Uso:", "در حال پیدا کردن بهترین کانفیگ..." to "Buscando la mejor configuración...", "هیچ کانفیگی پینگ نداد" to "Ninguna configuración respondió al ping", "در حال تست اتصال..." to "Probando conexión...", "در حال دریافت IP خروجی واقعی تونل..." to "Obteniendo IP real de salida del túnel...", "تشخیص IP خروجی تونل ناموفق بود" to "No se pudo detectar la IP de salida", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Desconectado. Pulsa conectar", "⚡ سوییچ هوشمند" to "⚡ Cambio inteligente", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "Encuentra y conecta automáticamente la mejor configuración", "کانفیگی وجود نداره" to "No hay configuraciones", "کانفیگ اضافه شد" to "Configuración añadida", "کانفیگی معتبر اضافه نشد" to "No se añadió ninguna configuración válida"),
        "it" to mapOf("مصرف کل:" to "Utilizzo totale:", "مصرف:" to "Utilizzo:", "در حال پیدا کردن بهترین کانفیگ..." to "Ricerca della configurazione migliore...", "هیچ کانفیگی پینگ نداد" to "Nessuna configurazione risponde al ping", "در حال تست اتصال..." to "Test connessione...", "در حال دریافت IP خروجی واقعی تونل..." to "Recupero IP reale di uscita del tunnel...", "تشخیص IP خروجی تونل ناموفق بود" to "Impossibile rilevare l'IP di uscita", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "Disconnesso. Tocca connetti", "⚡ سوییچ هوشمند" to "⚡ Switch intelligente", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "Trova e connette automaticamente la configurazione migliore", "کانفیگی وجود نداره" to "Nessuna configurazione", "کانفیگ اضافه شد" to "Configurazione aggiunta", "کانفیگی معتبر اضافه نشد" to "Nessuna configurazione valida aggiunta"),
        "fa" to mapOf("مصرف کل:" to "مصرف کل:", "مصرف:" to "مصرف:", "در حال پیدا کردن بهترین کانفیگ..." to "در حال پیدا کردن بهترین کانفیگ...", "هیچ کانفیگی پینگ نداد" to "هیچ کانفیگی پینگ نداد", "در حال تست اتصال..." to "در حال تست اتصال...", "در حال دریافت IP خروجی واقعی تونل..." to "در حال دریافت IP خروجی واقعی تونل...", "تشخیص IP خروجی تونل ناموفق بود" to "تشخیص IP خروجی تونل ناموفق بود", "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید" to "قطع شده. برای اتصال دکمه‌ی پخش رو بزنید", "⚡ سوییچ هوشمند" to "⚡ سوییچ هوشمند", "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه" to "هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه", "کانفیگی وجود نداره" to "کانفیگی وجود نداره", "کانفیگ اضافه شد" to "کانفیگ اضافه شد", "کانفیگی معتبر اضافه نشد" to "کانفیگی معتبر اضافه نشد")
    )

    fun translate(text: String, language: String): String =
        dialogTranslations[language]?.get(text) ?: runtimeTranslations[language]?.get(text) ?: actionTranslations[language]?.get(text) ?: translations[language]?.get(text) ?: text

    fun showChooser(context: Context) {
        val current = code(context)
        val labels = languages.map { it.second }.toTypedArray()
        val selectedIndex = languages.indexOfFirst { it.first == current }.coerceAtLeast(0
)
        AlertDialog.Builder(context)
            .setTitle(translate("App language / زبان برنامه", current))
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val selectedCode = languages[which].first
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, selectedCode).apply()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedCode))
                dialog.dismiss()
            }
            .setNegativeButton(translate("Cancel", current), null)
            .show()
    }
}
