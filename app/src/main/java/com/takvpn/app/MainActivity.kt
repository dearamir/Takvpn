package com.takvpn.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import kotlin.math.ln
import kotlin.math.sin
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.TrafficStats
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.animation.ValueAnimator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import libv2ray.Libv2ray
import org.json.JSONObject
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private class Act(val icon: String, val title: String, val sub: String, val run: () -> Unit)

    private data class UiPalette(
        val name: String, val bg: Int, val card: Int, val selected: Int,
        val header: Int, val accent: Int, val text: Int, val soft: Int,
        val bar: Int, val barText: Int, val fab: Int
    )

    private val uiPalettes by lazy { listOf(
        UiPalette(localizeUi("آبی اقیانوسی"), Color.rgb(12,22,38), Color.rgb(23,39,61), Color.rgb(35,62,91), Color.rgb(8,16,29), Color.rgb(0,205,235), Color.rgb(241,247,255), Color.rgb(157,178,202), Color.rgb(8,16,29), Color.rgb(220,239,250), Color.rgb(10,105,145)),
        UiPalette(localizeUi("مشکی طلایی"), Color.rgb(16,16,18), Color.rgb(35,35,40), Color.rgb(61,56,43), Color.rgb(8,8,10), Color.rgb(212,175,55), Color.rgb(248,244,232), Color.rgb(184,180,169), Color.rgb(8,8,10), Color.rgb(245,220,165), Color.rgb(65,55,30)),
        UiPalette(localizeUi("زمردی"), Color.rgb(10,25,22), Color.rgb(21,48,41), Color.rgb(32,76,62), Color.rgb(5,17,14), Color.rgb(0,210,145), Color.rgb(239,250,245), Color.rgb(158,193,178), Color.rgb(5,17,14), Color.rgb(218,245,232), Color.rgb(12,105,75)),
        UiPalette(localizeUi("بنفش نئونی"), Color.rgb(23,18,39), Color.rgb(43,34,66), Color.rgb(67,52,99), Color.rgb(14,10,27), Color.rgb(170,115,255), Color.rgb(248,244,255), Color.rgb(190,176,216), Color.rgb(14,10,27), Color.rgb(235,224,255), Color.rgb(92,55,150))
    ) }
    private val textColors by lazy { listOf(
        localizeUi("سفید یخی") to Color.rgb(245,248,255),
        localizeUi("طلایی") to Color.rgb(245,205,105),
        localizeUi("فیروزه‌ای") to Color.rgb(0,210,235),
        localizeUi("سبز زمردی") to Color.rgb(65,220,165),
        localizeUi("بنفش") to Color.rgb(190,145,255),
        localizeUi("نقره‌ای") to Color.rgb(190,200,215),
        localizeUi("نارنجی") to Color.rgb(255,165,70),
        localizeUi("پیش‌فرض پوسته") to Color.TRANSPARENT
    ) }
    private fun localizeUi(text: String): String {
        val language = AppLanguage.code(this)
        val translations = when (text) {
            "آبی اقیانوسی" -> mapOf("en" to "Ocean Blue", "fr" to "Bleu océan", "ru" to "Океанский синий", "zh" to "海洋蓝", "tr" to "Okyanus mavisi", "de" to "Ozeanblau", "ar" to "أزرق المحيط", "es" to "Azul océano", "it" to "Blu oceano", "fa" to "آبی اقیانوسی")
            "مشکی طلایی" -> mapOf("en" to "Black Gold", "fr" to "Noir et or", "ru" to "Чёрно-золотой", "zh" to "黑金", "tr" to "Siyah-altın", "de" to "Schwarz-Gold", "ar" to "أسود وذهبي", "es" to "Negro y oro", "it" to "Nero e oro", "fa" to "مشکی طلایی")
            "زمردی" -> mapOf("en" to "Emerald", "fr" to "Émeraude", "ru" to "Изумрудный", "zh" to "翡翠绿", "tr" to "Zümrüt", "de" to "Smaragd", "ar" to "زمردي", "es" to "Esmeralda", "it" to "Smeraldo", "fa" to "زمردی")
            "بنفش نئونی" -> mapOf("en" to "Neon Purple", "fr" to "Violet néon", "ru" to "Неоновый фиолетовый", "zh" to "霓虹紫", "tr" to "Neon mor", "de" to "Neonviolett", "ar" to "بنفسجي نيون", "es" to "Morado neón", "it" to "Viola neon", "fa" to "بنفش نئونی")
            "سفید یخی" -> mapOf("en" to "Ice White", "fr" to "Blanc glacé", "ru" to "Ледяной белый", "zh" to "冰白", "tr" to "Buz beyazı", "de" to "Eisweiß", "ar" to "أبيض جليدي", "es" to "Blanco hielo", "it" to "Bianco ghiaccio", "fa" to "سفید یخی")
            "طلایی" -> mapOf("en" to "Gold", "fr" to "Or", "ru" to "Золотой", "zh" to "金色", "tr" to "Altın", "de" to "Gold", "ar" to "ذهبي", "es" to "Dorado", "it" to "Oro", "fa" to "طلایی")
            "فیروزه‌ای" -> mapOf("en" to "Turquoise", "fr" to "Turquoise", "ru" to "Бирюзовый", "zh" to "青绿", "tr" to "Turkuaz", "de" to "Türkis", "ar" to "فيروزي", "es" to "Turquesa", "it" to "Turchese", "fa" to "فیروزه‌ای")
            "بنفش" -> mapOf("en" to "Purple", "fr" to "Violet", "ru" to "Фиолетовый", "zh" to "紫色", "tr" to "Mor", "de" to "Lila", "ar" to "بنفسجي", "es" to "Morado", "it" to "Viola", "fa" to "بنفش")
            "سبز زمردی" -> mapOf("en" to "Emerald Green", "fr" to "Vert émeraude", "ru" to "Изумрудный", "zh" to "翡翠绿", "tr" to "Zümrüt yeşili", "de" to "Smaragdgrün", "ar" to "أخضر زمردي", "es" to "Verde esmeralda", "it" to "Verde smeraldo", "fa" to "سبز زمردی")
            "نقره‌ای" -> mapOf("en" to "Silver", "fr" to "Argent", "ru" to "Серебряный", "zh" to "银色", "tr" to "Gümüş", "de" to "Silber", "ar" to "فضي", "es" to "Plateado", "it" to "Argento", "fa" to "نقره‌ای")
            "نارنجی" -> mapOf("en" to "Orange", "fr" to "Orange", "ru" to "Оранжевый", "zh" to "橙色", "tr" to "Turuncu", "de" to "Orange", "ar" to "برتقالي", "es" to "Naranja", "it" to "Arancione", "fa" to "نارنجی")
            "پیش‌فرض پوسته" -> mapOf("en" to "Theme default", "fr" to "Par défaut du thème", "ru" to "По теме", "zh" to "主题默认", "tr" to "Tema varsayılanı", "de" to "Designstandard", "ar" to "افتراضي المظهر", "es" to "Predeterminado del tema", "it" to "Predefinito tema", "fa" to "پیش‌فرض پوسته")
            "انتخاب پوسته" -> mapOf("en" to "Choose theme", "fr" to "Choisir le thème", "ru" to "Выбрать тему", "zh" to "选择主题", "tr" to "Tema seç", "de" to "Design wählen", "ar" to "اختيار المظهر", "es" to "Elegir tema", "it" to "Scegli tema", "fa" to "انتخاب پوسته")
            "رنگ نوشته‌ها" -> mapOf("en" to "Text colors", "fr" to "Couleurs du texte", "ru" to "Цвет текста", "zh" to "文字颜色", "tr" to "Yazı renkleri", "de" to "Textfarben", "ar" to "ألوان النص", "es" to "Colores del texto", "it" to "Colori testo", "fa" to "رنگ نوشته‌ها")
            "تغییر رنگ پس‌زمینه و کارت‌ها" -> mapOf("en" to "Change background and cards", "fr" to "Changer le fond et les cartes", "ru" to "Изменить фон и карточки", "zh" to "更改背景和卡片", "tr" to "Arka plan ve kartları değiştir", "de" to "Hintergrund und Karten ändern", "ar" to "تغيير الخلفية والبطاقات", "es" to "Cambiar fondo y tarjetas", "it" to "Cambia sfondo e schede", "fa" to "تغییر رنگ پس‌زمینه و کارت‌ها")
            "تغییر مستقل رنگ متن‌ها" -> mapOf("en" to "Change text colors separately", "fr" to "Changer les couleurs du texte séparément", "ru" to "Отдельно изменить цвет текста", "zh" to "单独调整文字颜色", "tr" to "Metin renklerini ayrı değiştir", "de" to "Textfarben separat ändern", "ar" to "تغيير ألوان النص بشكل مستقل", "es" to "Cambiar colores del texto por separado", "it" to "Modifica colori testo separatamente", "fa" to "تغییر مستقل رنگ متن‌ها")
            "تاریک / روشن" -> mapOf("en" to "Dark / light", "fr" to "Sombre / clair", "ru" to "Тёмный / светлый", "zh" to "深色 / 浅色", "tr" to "Koyu / açık", "de" to "Dunkel / hell", "ar" to "داكن / فاتح", "es" to "Oscuro / claro", "it" to "Scuro / chiaro", "fa" to "تاریک / روشن")
            else -> emptyMap()
        }
        return translations[language] ?: text
    }

    private fun activePalette() = uiPalettes.find { it.name == prefs.getString("ui_palette", "طلایی") } ?: uiPalettes.first()

    private fun isNightAppearance(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // Keep the selected color identity, but give Light and Dark genuinely different surfaces.
    private fun displayPalette(base: UiPalette = activePalette()): UiPalette =
        if (isNightAppearance()) base else base.copy(
            bg = Color.rgb(246, 248, 251),
            card = Color.WHITE,
            selected = Color.rgb(255, 239, 218),
            header = Color.WHITE,
            text = Color.rgb(28, 32, 39),
            soft = Color.rgb(91, 101, 114),
            bar = Color.WHITE,
            barText = Color.rgb(34, 38, 45),
            fab = base.accent
        )

    private fun activeTextColor(p: UiPalette = displayPalette()): Int =
        textColors.find { it.first == prefs.getString("ui_text_color", "پیش‌فرض پوسته") }?.second
            ?.takeIf { it != Color.TRANSPARENT } ?: p.text

    private fun applyPaletteToTree(view: View, p: UiPalette = activePalette()) {
        when (view) {
            is MaterialCardView -> {
                val bg = when (view.id) {
                    R.id.testBar -> p.bar
                    R.id.pingAllButton -> p.accent
                    else -> if (view.strokeWidth > 0) p.selected else p.card
                }
                view.setCardBackgroundColor(bg)
                view.strokeColor = p.accent
            }
            is FloatingActionButton -> {
                view.backgroundTintList = ColorStateList.valueOf(if (TakVpnService.isRunning) p.accent else p.fab)
                view.imageTintList = ColorStateList.valueOf(Color.WHITE)
            }
            is android.widget.ImageButton -> {
                view.imageTintList = ColorStateList.valueOf(Color.rgb(255,211,106))
                view.backgroundTintList = null
            }
            is TextView -> when (view.id) {
                R.id.titleText, R.id.versionText -> view.setTextColor(p.accent)
                R.id.statusText -> view.setTextColor(if (TakVpnService.isRunning) Color.rgb(90,230,155) else p.soft)
                R.id.pingText -> Unit
                R.id.typeText -> view.setTextColor(Color.parseColor("#FFFF6A00"))
                R.id.subText, R.id.usageText, R.id.emptyText, R.id.actSub -> view.setTextColor(p.soft)
                R.id.testText -> view.setTextColor(p.barText)
                R.id.pingAllLabel -> {
                    view.setTextColor(Color.WHITE)
                    view.setTypeface(null, Typeface.BOLD)
                }
                else -> {
                    if ((view.parent as? View)?.id == R.id.plusButton) {
                        view.setTextColor(p.accent)
                        view.textSize = 40f
                    } else {
                        view.setTextColor(activeTextColor(p))
                    }
                }
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) applyPaletteToTree(view.getChildAt(i), p)
    }

    private fun applyAppPalette() {
        val p = displayPalette()
        findViewById<View>(android.R.id.content)?.let {
            it.setBackgroundColor(p.bg)
            applyPaletteToTree(it, p)
        }
        connectIcon.setThemeColor(p.accent)
        window.statusBarColor = p.header
        window.navigationBarColor = p.header
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightAppearance()
            isAppearanceLightNavigationBars = !isNightAppearance()
        }
    }

    private fun buildThemePreview(p: UiPalette, textColor: Int = p.text): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(p.card)
                setStroke(dp(1), p.accent)
            }
        }
        val heading = TextView(this).apply {
            text = "TakVPN  •  پیش‌نمایش"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.accent)
        }
        val mainText = TextView(this).apply {
            text = "اتصال امن و سریع"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, dp(8), 0, dp(3))
        }
        val subText = TextView(this).apply {
            text = "این نوشته نمونه است تا خوانایی رنگ را بررسی کنید"
            textSize = 12f
            setTextColor(p.soft)
        }
        val status = TextView(this).apply {
            text = "●  متصل"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(70, 220, 145))
            setPadding(0, dp(10), 0, dp(10))
        }
        val button = TextView(this).apply {
            text = "دکمه نمونه"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(p.accent)
            }
        }
        card.addView(heading)
        card.addView(mainText)
        card.addView(subText)
        card.addView(status)
        card.addView(button, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun showAppearanceDialog() {
        val names = uiPalettes.map { it.name }.toTypedArray()
        val initialIndex = uiPalettes.indexOf(activePalette()).coerceAtLeast(0)
        var selectedIndex = initialIndex
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
        }
        val previewHolder = FrameLayout(this)
        root.addView(previewHolder, LinearLayout.LayoutParams(-1, -2))
        val choices = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        names.forEachIndexed { index, name ->
            val rb = android.widget.RadioButton(this).apply {
                text = name
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = dp(48)
                buttonTintList = ColorStateList.valueOf(activePalette().accent)
                id = View.generateViewId()
            }
            choices.addView(rb)
            if (index == initialIndex) rb.isChecked = true
        }
        root.addView(styleAppearanceChoices(choices, activePalette().accent), LinearLayout.LayoutParams(-1, dp(250)))
        fun updatePreview(index: Int) {
            selectedIndex = index
            previewHolder.removeAllViews()
            val selected = uiPalettes[index]
            val previewPalette = displayPalette(selected)
            previewHolder.addView(buildThemePreview(previewPalette, previewPalette.text))
        }
        updatePreview(initialIndex)
        choices.setOnCheckedChangeListener { group, checkedId ->
            val index = group.indexOfChild(group.findViewById(checkedId))
            if (index >= 0) updatePreview(index)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr("انتخاب پوسته"))
            .setView(root)
            .setPositiveButton(tr("تأیید"), null)
            .setNeutralButton(tr("پیش‌فرض"), null)
            .setNegativeButton(tr("لغو"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                prefs.edit().putString("ui_palette", names[selectedIndex]).apply()
                applyAppPalette()
                dialog.dismiss()
                toast("پوسته ذخیره شد")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                prefs.edit().remove("ui_palette").apply()
                applyAppPalette()
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.28f)
    }

    private fun showTextColorDialog() {
        val names = textColors.map { it.first }.toTypedArray()
        val initialIndex = names.indexOf(prefs.getString("ui_text_color", null) ?: "پیش‌فرض پوسته").coerceAtLeast(0)
        var selectedIndex = initialIndex
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(8))
        }
        val previewHolder = FrameLayout(this)
        root.addView(previewHolder, LinearLayout.LayoutParams(-1, -2))
        val choices = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        names.forEachIndexed { index, name ->
            val rb = android.widget.RadioButton(this).apply {
                text = name
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                minHeight = dp(48)
                buttonTintList = ColorStateList.valueOf(displayPalette().accent)
                id = View.generateViewId()
            }
            choices.addView(rb)
            if (index == initialIndex) rb.isChecked = true
        }
        root.addView(styleAppearanceChoices(choices, displayPalette().accent), LinearLayout.LayoutParams(-1, dp(280)))
        fun updatePreview(index: Int) {
            selectedIndex = index
            previewHolder.removeAllViews()
            val palette = displayPalette()
            val chosen = textColors[index].second.takeIf { it != Color.TRANSPARENT } ?: palette.text
            previewHolder.addView(buildThemePreview(palette, chosen))
        }
        updatePreview(initialIndex)
        choices.setOnCheckedChangeListener { group, checkedId ->
            val index = group.indexOfChild(group.findViewById(checkedId))
            if (index >= 0) updatePreview(index)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(tr("رنگ نوشته‌ها"))
            .setView(root)
            .setPositiveButton(tr("تأیید"), null)
            .setNeutralButton(tr("پیش‌فرض نوشته"), null)
            .setNegativeButton(tr("لغو"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (names[selectedIndex] == "پیش‌فرض پوسته") prefs.edit().remove("ui_text_color").apply()
                else prefs.edit().putString("ui_text_color", names[selectedIndex]).apply()
                applyAppPalette()
                dialog.dismiss()
                toast("رنگ نوشته‌ها ذخیره شد")
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                prefs.edit().remove("ui_text_color").apply()
                applyAppPalette()
                dialog.dismiss()
            }
            styleAppearanceDialogText(dialog)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.28f)
    }

    private fun styleAppearanceChoices(choices: android.widget.RadioGroup, accent: Int): ScrollView {
        choices.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(Color.rgb(22, 30, 44))
            setStroke(dp(1), androidx.core.graphics.ColorUtils.blendARGB(accent, Color.WHITE, 0.18f))
        }
        choices.setPadding(dp(10), dp(10), dp(10), dp(10))
        choices.clipToPadding = false
        for (index in 0 until choices.childCount) {
            val option = choices.getChildAt(index) as? android.widget.RadioButton ?: continue
            option.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(11).toFloat()
                setColor(Color.rgb(37, 47, 64))
                setStroke(dp(1), Color.rgb(57, 71, 91))
            }
            option.setTextColor(Color.rgb(246, 249, 255))
            option.setShadowLayer(dp(1).toFloat(), 0f, 0f, Color.BLACK)
            val params = android.widget.RadioGroup.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            }
            option.layoutParams = params
        }
        return ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            clipToPadding = false
            addView(choices, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun styleAppearanceDialogText(dialog: AlertDialog) {
        fun applyWhiteText(view: View?) {
            if (view == null) return
            if (view is TextView) {
                view.setTextColor(Color.WHITE)
                view.setTypeface(view.typeface, Typeface.BOLD)
                view.setShadowLayer(2f, 0f, 1f, Color.BLACK)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) applyWhiteText(view.getChildAt(index))
            }
        }

        val decor = dialog.window?.decorView
        // Custom-view dialogs have no ListView; styling it caused a crash on Text Color.
        applyWhiteText(decor)
    }

    private lateinit var store: ConfigStore
    private lateinit var subs: SubStore
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var connectionInfoText: TextView
    private var connectionInfoRequest = 0
    private lateinit var emptyText: TextView
    private lateinit var testText: TextView
    private lateinit var usageText: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var connectIcon: ConnectTDrawable
    private lateinit var titleGlow: GlowAnimator
    private lateinit var statusGlow: GlowAnimator
    private var versionShine: ValueAnimator? = null
    private var connectColorAnimator: ValueAnimator? = null
    private lateinit var flowSpeedView: FlowSpeedView
    private val prefs by lazy { getSharedPreferences("takvpn", Context.MODE_PRIVATE) }
    private val pings = mutableMapOf<String, Long>()
    private val errors = mutableMapOf<String, String>()
    private val tcpLatencies = mutableMapOf<String, Long>()
    private val countryFlags = mutableMapOf<String, String>()
    private val exitIps = mutableMapOf<String, String>()
    private val countryLookups = mutableSetOf<String>()
    private val pool = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())
    private var pendingConfig: String? = null
    private var pendingName = ""
    private var pendingLink = ""
    private var pendingSmart = false
    private var lastRunning = false
    private var sortAscending = true
    private var showExitIps = false
    private var exitIpRevealTapCount = 0
    private val exitIpRevealReset = Runnable { exitIpRevealTapCount = 0 }

    private var inited = false
    private var baseRx = 0L
    private var baseTx = 0L
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastTime = 0L
    private var totalBytes = 0L
    private var tick = 0

    private val ticker = object : Runnable {
        override fun run() {
            updateUsage()
            ui.postDelayed(this, 1000)
        }
    }

    private val vpnPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConfig?.let { startVpn(it) }
        } else {
            resetConnectVisual()
            toast("اجازه‌ی VPN داده نشد")
        }
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) addFromText(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.apply(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.pingAllLabel).text = tr("⌁  پینگ همه")
        findViewById<View>(R.id.sortConfigsButton).contentDescription = if (AppLanguage.code(this) == "fa") "مرتب‌سازی کانفیگ‌ها بر اساس پینگ" else "Sort configurations by ping"
        findViewById<TextView>(R.id.emptyText).text = tr("هنوز کانفیگی اضافه نشده.\nعلامت + بالا سمت چپ رو بزنید.")
        findViewById<TextView>(R.id.testText).text = tr("قطع شده. برای اتصال دکمه‌ی پخش رو بزنید")
        findViewById<View>(R.id.connectFab).contentDescription = tr("اتصال")

        store = ConfigStore(this)
        subs = SubStore(this, store)
        totalBytes = prefs.getLong("total", 0L)
        titleText = findViewById(R.id.titleText)
        statusText = findViewById(R.id.statusText)
        connectionInfoText = findViewById(R.id.connectionInfoText)
        emptyText = findViewById(R.id.emptyText)
        testText = findViewById(R.id.testText)
        usageText = findViewById(R.id.usageText)
        listContainer = findViewById(R.id.listContainer)
        fab = findViewById(R.id.connectFab)
        flowSpeedView = FlowSpeedView(this)
        findViewById<FrameLayout>(R.id.speedIndicatorSlot).addView(flowSpeedView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        connectIcon = ConnectTDrawable()
        fab.setImageDrawable(connectIcon)
        fab.imageTintList = null
        titleGlow = GlowAnimator(titleText, Color.parseColor("#FFB800"), Color.parseColor("#FFF8D6"))
        statusGlow = GlowAnimator(statusText, c(R.color.status_on), Color.WHITE)

        val versionText = findViewById<TextView>(R.id.versionText)
        versionText.text = verName()
        setupVersionShine(versionText)
        findViewById<TextView>(R.id.coreVersionCorner).text = "Xray " + BuildConfig.XRAY_CORE_VERSION
        findViewById<View>(R.id.plusButton).setOnClickListener { showActions() }
        findViewById<View>(R.id.pingAllButton).setOnClickListener { pingAll() }
        findViewById<View>(R.id.sortConfigsButton).setOnClickListener { sortByPing() }
        findViewById<View>(R.id.speedIndicatorSlot).apply {
            isClickable = true
            isFocusable = true
            contentDescription = tr("Tap three times to show or hide Exit IPs")
            setOnClickListener {
                exitIpRevealTapCount++
                ui.removeCallbacks(exitIpRevealReset)
                if (exitIpRevealTapCount >= 3) {
                    exitIpRevealTapCount = 0
                    showExitIps = !showExitIps
                    render()
                    toast(if (showExitIps) "نمایش IP خروجی فعال شد" else "نمایش IP خروجی مخفی شد")
                } else {
                    ui.postDelayed(exitIpRevealReset, 700L)
                }
            }
        }
        findViewById<View>(R.id.testBar).setOnClickListener {
            if (TakVpnService.isRunning) runConnectionTest() else toast("اول وصل بشید")
        }
        fab.setOnClickListener { onConnectClicked() }

        render()
        applyAppPalette()

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
        }

        if (savedInstanceState == null && prefs.getBoolean("auto_open", false) &&
            !TakVpnService.isRunning && store.items.isNotEmpty()
        ) {
            ui.postDelayed({
                if (!isDestroyed && !TakVpnService.isRunning) onConnectClicked()
            }, 700)
        }
    }

    override fun onStart() {
        super.onStart()
        TakVpnService.onState = { running -> runOnUiThread { updateUi(running) } }
        TakVpnService.onError = { msg ->
            AppLog.add("خطای اتصال: $msg")
            runOnUiThread { toast("خطا: $msg") }
        }
        TakVpnService.onSwitched = {
            runOnUiThread {
                render()
                fetchConnectionInfo()
                runConnectionTest()
            }
        }
        updateUi(TakVpnService.isRunning)
        connectIcon.setFlowing(true)
        flowSpeedView.startFlow()
        maybeCheckForUpdates()
        versionShine?.takeIf { !it.isRunning }?.start()
    }

    override fun onStop() {
        super.onStop()
        TakVpnService.onState = null
        TakVpnService.onError = null
        TakVpnService.onSwitched = null
        ui.removeCallbacks(ticker)
        titleGlow.stop(c(R.color.header_title))
        statusGlow.stop(c(if (TakVpnService.isRunning) R.color.status_on else R.color.status_off))
        versionShine?.cancel()
        connectIcon.stopShine()
        connectIcon.setFlowing(false)
        connectColorAnimator?.cancel()
        connectColorAnimator = null
        flowSpeedView.stopFlow()
        saveTotal()
    }

    override fun onDestroy() {
        super.onDestroy()
        pool.shutdownNow()
        ui.removeCallbacksAndMessages(null)
    }

    private fun setupVersionShine(view: TextView) {
        view.post {
            if (view.width <= 0 || isFinishing || isDestroyed) return@post

            val textWidth = view.paint.measureText(view.text.toString()).coerceAtLeast(view.width.toFloat())
            val shineWidth = textWidth * 1.4f
            val matrix = Matrix()
            val gradient = LinearGradient(
                0f, 0f, shineWidth, 0f,
                intArrayOf(
                    c(R.color.header_sub),
                    c(R.color.header_sub),
                    Color.WHITE,
                    Color.parseColor("#FFFFD1"),
                    c(R.color.header_sub)
                ),
                floatArrayOf(0f, 0.38f, 0.48f, 0.54f, 1f),
                Shader.TileMode.CLAMP
            )
            view.paint.shader = gradient

            versionShine = ValueAnimator.ofFloat(-shineWidth, textWidth + shineWidth).apply {
                duration = 2200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    matrix.setTranslate(animation.animatedValue as Float, 0f)
                    gradient.setLocalMatrix(matrix)
                    view.invalidate()
                }
                start()
            }
        }
    }

    private fun c(id: Int) = ContextCompat.getColor(this, id)

    private fun verName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    private fun smartSelected() = prefs.getBoolean("smart_sel", false)

    // ---------- actions sheet ----------

    private fun onOff(key: String) = tr(if (prefs.getBoolean(key, false)) "روشن" else "خاموش")

    private fun showActions() {
        val dialog = BottomSheetDialog(this)
        val root = layoutInflater.inflate(R.layout.sheet_actions, null)
        val container = root.findViewById<LinearLayout>(R.id.actionsContainer)

        addSection(container, "افزودن کانفیگ")
        addGrid(
            container, dialog, R.color.act_add, listOf(
                Act("📋", "کلیپ‌بورد", "چسباندن کانفیگ") { pasteFromClipboard() },
                Act("📷", "اسکن QR", "با دوربین") { launchScan() },
                Act("🔗", "افزودن اشتراک", "لینک سابسکریپشن") { addSubDialog() },
                Act("✍️", "ساخت دستی", "فرم خالی کانفیگ") {
                    ConfigEditor.createConfig(this, store) { render() }
                }
            )
        )
        addSection(container, "ابزارها")
        addGrid(
            container, dialog, R.color.act_tool, listOf(
                Act("⚡", "سریع‌ترین", "تست و اتصال خودکار") { connectFastest() },
                Act("📶", "پینگ همه", "تست همه‌ی کانفیگ‌ها") { pingAll() },
                Act("↕️", "مرتب‌سازی", "بر اساس پینگ") { sortByPing() },
                Act("🧹", "حذف ناموفق‌ها", "کانفیگ‌های بدون پینگ") { deleteDead() }
            )
        )
        addSection(container, "اشتراک‌ها")
        addGrid(
            container, dialog, R.color.act_sub, listOf(
                Act("🔄", "به‌روزرسانی", "همه‌ی اشتراک‌ها") { updateAllSubs() },
                Act("🗂️", "مدیریت", "مشاهده و حذف") { manageSubs() }
            )
        )
        addSection(container, "اتصال و شبکه")
        addGrid(
            container, dialog, R.color.act_sub, listOf(
                Act("🧭", "DNS", prefs.getString("dns", TakVpnService.DEFAULT_DNS).orEmpty()) { dnsDialog() },
                Act("🌐", "سایت‌های .ir مستقیم", onOff("direct_ir")) {
                    toggleSetting("direct_ir", "باز شدن مستقیم سایت‌های .ir")
                },
                Act("🚀", "اتصال هنگام باز شدن", onOff("auto_open")) {
                    toggleSetting("auto_open", "اتصال هنگام باز شدن اپ", "دفعه‌ی بعد که اپ رو باز کنید")
                },
                Act("📲", "انتخاب اپ‌ها", AppPicker.summary(this)) { AppPicker.show(this) }
            )
        )
        addSection(container, "سیستم")
        addGrid(
            container, dialog, R.color.act_sys, listOf(
                Act("🌐", "زبان برنامه", AppLanguage.currentLabel(this)) { AppLanguage.showChooser(this) },
                Act("🌗", "حالت تاریک", "تاریک / روشن") { toggleNight() },
                Act(
                    "🔀", "پورت‌هاپینگ",
                    "Hysteria: " + tr(if (ConfigExtras.hopEnabled) "روشن" else "خاموش")
                ) { toggleHop() },
                Act("📜", "لاگ", "رویدادها و خطاها") { showLog() },
                Act("💾", "بکاپ", "کپی همه‌ی کانفیگ‌ها") { backupAll() },
                Act("🧮", "صفر کردن مصرف", "مصرف کل داده") { resetUsage() },
                Act("🎨", "انتخاب پوسته", "تغییر رنگ پس‌زمینه و کارت‌ها") { showAppearanceDialog() },
                Act("🖋️", "رنگ نوشته‌ها", "تغییر مستقل رنگ متن‌ها") { showTextColorDialog() },
                Act("⬆️", "بررسی نسخه", "نسخه‌ی جدید؟") { checkUpdate() },
                Act("ℹ️", "درباره", "نسخه و دانلود") { showAbout() }
            )
        )

        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
        applyPaletteToTree(root)
        root.setBackgroundColor(activePalette().bg)
    }

    private fun addSection(container: LinearLayout, title: String) {
        val tv = TextView(this)
        tv.text = AppLanguage.translate(title, AppLanguage.code(this))
        tv.textSize = 14f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setTextColor(c(R.color.ink_soft))
        tv.setPadding(dp(6), dp(14), dp(6), dp(4))
        container.addView(tv)
    }

    private fun addGrid(container: LinearLayout, dialog: BottomSheetDialog, colorRes: Int, acts: List<Act>) {
        var i = 0
        while (i < acts.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val a = acts.getOrNull(i + j)
                if (a == null) {
                    row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                } else {
                    val v = layoutInflater.inflate(R.layout.item_action, row, false) as MaterialCardView
                    v.setCardBackgroundColor(c(colorRes))
                    v.findViewById<TextView>(R.id.actIcon).text = a.icon
                    v.findViewById<TextView>(R.id.actTitle).text = AppLanguage.translate(a.title, AppLanguage.code(this))
                    v.findViewById<TextView>(R.id.actSub).text = AppLanguage.translate(a.sub, AppLanguage.code(this))
                    v.setOnClickListener {
                        dialog.dismiss()
                        a.run()
                    }
                    row.addView(v)
                }
            }
            container.addView(row)
            i += 2
        }
    }

    private fun toggleSetting(key: String, title: String, note: String = "از اتصال بعدی اعمال می‌شه") {
        val v = !prefs.getBoolean(key, false)
        prefs.edit().putBoolean(key, v).apply()
        toast("${tr(title)} ${tr(if (v) "روشن شد" else "خاموش شد")}\n${tr(note)}")
    }

    private fun dnsDialog() {
        val input = EditText(this)
        input.setText(prefs.getString("dns", TakVpnService.DEFAULT_DNS))
        input.hint = "1.1.1.1, 8.8.8.8"
        input.setSingleLine()
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(this)
            .setTitle(tr("DNS (آدرس‌ها با ویرگول)"))
            .setView(box)
            .setPositiveButton(tr("ذخیره")) { _, _ ->
                val list = TakVpnService.parseDns(input.text.toString())
                if (list.isEmpty()) {
                    toast("آدرس IPv4 معتبری وارد نشد")
                } else {
                    prefs.edit().putString("dns", list.joinToString(",")).apply()
                    toast("DNS ذخیره شد. از اتصال بعدی اعمال می‌شه")
                }
            }
            .setNeutralButton(tr("پیش‌فرض")) { _, _ ->
                prefs.edit().remove("dns").apply()
                toast("DNS به پیش‌فرض برگشت")
            }
            .setNegativeButton(tr("لغو"), null)
            .show()
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) toast("کلیپ‌بورد خالیه") else addFromText(text)
    }

    private fun launchScan() {
        scanLauncher.launch(
            ScanOptions()
                .setPrompt(tr("QR کانفیگ رو جلوی دوربین بگیرید"))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .setCaptureActivity(PortraitCaptureActivity::class.java)
        )
    }

    private fun toggleNight() {
        val mode = if (isNightAppearance()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putInt("night", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun toggleHop() {
        ConfigExtras.hopEnabled = !ConfigExtras.hopEnabled
        prefs.edit().putBoolean("hop", ConfigExtras.hopEnabled).apply()
        toast("پورت‌هاپینگ " + (if (ConfigExtras.hopEnabled) "روشن شد" else "خاموش شد") + "\nاز اتصال بعدی اعمال می‌شه")
    }

    private fun resetUsage() {
        totalBytes = 0L
        saveTotal()
        if (!TakVpnService.isRunning) usageText.text = tr("مصرف کل: ") + fmt(0)
    }

    private fun backupAll() {
        if (store.items.isEmpty()) {
            toast(AppLanguage.translate("کانفیگی وجود نداره", AppLanguage.code(this)))
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("configs", store.items.joinToString("\n") { it.link }))
        toast("${store.items.size} کانفیگ توی کلیپ‌بورد کپی شد")
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Takvpn")
            .setMessage(
                "${tr("نسخه‌ی برنامه:")} ${verName()}\n${tr("نسخه‌ی هسته Xray:")} ${BuildConfig.XRAY_CORE_VERSION}\n${tr("بروزرسانی مستقیم از داخل برنامه")}"
            )
            .setPositiveButton(tr("بررسی بروزرسانی برنامه")) { _, _ -> checkUpdate() }
            .setNeutralButton(tr("بررسی بروزرسانی هسته")) { _, _ -> checkCoreUpdate() }
            .setNegativeButton(tr("بستن"), null)
            .show()
    }

    private fun checkCoreUpdate() {
        toast("در حال بررسی نسخه‌ی هسته...")
        pool.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://api.github.com/repos/2dust/AndroidLibXrayLite/releases/latest")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP ${connection.responseCode}")
                }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val latest = release.optString("tag_name").removePrefix("v")
                val current = BuildConfig.XRAY_CORE_VERSION
                val newer = compareCoreVersions(latest, current) > 0
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (newer) {
                        AlertDialog.Builder(this)
                            .setTitle(tr("هسته‌ی جدید موجود است"))
                            .setMessage(
                                "هسته‌ی فعلی: $current\nآخرین هسته: $latest\n\nبرای حفظ امنیت و سازگاری، هسته همراه نسخه‌ی جدید Takvpn نصب می‌شود."
                            )
                            .setPositiveButton(tr("بررسی بروزرسانی برنامه")) { _, _ -> checkUpdate() }
                            .setNegativeButton(tr("بعداً"), null)
                            .show()
                    } else {
                        toast("${tr("هسته‌ی Xray به‌روزه")} ($current)")
                    }
                }
            } catch (e: Throwable) {
                val message = (e.message ?: e.toString()).replace("\n", " ").take(80)
                runOnUiThread {
                    if (!isDestroyed) toast("بررسی هسته ناموفق بود: $message")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun compareCoreVersions(first: String, second: String): Int {
        // GitHub release tags include a leading "v" (for example v2.08),
        // while Android versionName is stored without it (2.07).
        // Normalize both before numeric comparison or "v2" is parsed as zero.
        val a = first.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        val b = second.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private var pendingInstallFile: java.io.File? = null

    private fun checkUpdate(silent: Boolean = false) {
        if (!silent) toast(tr("در حال بررسی نسخه..."))
        pool.execute {
            try {
                val latest = UpdateChecker.latest()
                prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (compareCoreVersions(latest.tag, verName()) > 0) {
                        val versionInfo = "${tr("نسخه‌ی فعلی:")} ${verName()}\n${tr("نسخه‌ی جدید:")} ${latest.tag}"
                        AlertDialog.Builder(this)
                            .setTitle(tr("بروزرسانی موجود است"))
                            .setMessage(versionInfo)
                            .setPositiveButton(tr("بروزرسانی")) { _, _ -> downloadAndInstallUpdate(latest) }
                            .setNegativeButton(tr("بعداً"), null)
                            .show()
                    } else if (!silent) {
                        val updateLanguage = AppLanguage.code(this)
                        val upToDateText = if (updateLanguage == "en") {
                            "Your app is up to date"
                        } else {
                            AppLanguage.translate("نسخه‌ی شما به‌روزه", updateLanguage)
                        }
                        toast("$upToDateText (${verName()})")
                    }
                }
            } catch (e: Throwable) {
                val message = (e.message ?: e.toString()).replace("\n", " ").take(100)
                AppLog.add("${tr("بررسی نسخه ناموفق: ")}$message")
                if (!silent) runOnUiThread {
                    if (!isDestroyed) toast("${tr("بررسی نسخه ناموفق: ")}$message")
                }
            }
        }
    }

    private fun downloadAndInstallUpdate(info: UpdateChecker.Result) {
        val progress = android.app.ProgressDialog(this).apply {
            setTitle("دریافت TakVPN ${info.tag}")
            setMessage("در حال دانلود فایل نصب...")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            isIndeterminate = true
            setCancelable(false)
            show()
        }
        pool.execute {
            var connection: HttpURLConnection? = null
            val target = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Takvpn-${info.tag}.apk")
            try {
                connection = URL(info.downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Takvpn")
                if (connection.responseCode !in 200..299) throw java.io.IOException("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                target.parentFile?.mkdirs()
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(target).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var count: Int
                        var downloaded = 0L
                        while (input.read(buffer).also { count = it } != -1) {
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            if (total > 0) runOnUiThread {
                                if (!isDestroyed && progress.isShowing) {
                                    progress.isIndeterminate = false
                                    progress.max = 100
                                    progress.progress = ((downloaded * 100) / total).toInt()
                                }
                            }
                        }
                    }
                }
                if (!target.exists() || target.length() == 0L) throw java.io.IOException("فایل دانلودشده خالی است")
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256.isNotBlank() && !actualHash.equals(info.sha256, true)) {
                    throw java.io.IOException("اعتبارسنجی فایل نصب ناموفق بود")
                }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (progress.isShowing) progress.dismiss()
                    pendingInstallFile = target
                    installDownloadedApk(target)
                }
            } catch (e: Throwable) {
                target.delete()
                val message = (e.message ?: e.toString()).take(120)
                AppLog.add("دانلود بروزرسانی ناموفق: $message")
                runOnUiThread {
                    if (!isDestroyed) {
                        if (progress.isShowing) progress.dismiss()
                        AlertDialog.Builder(this)
                            .setTitle(tr("دانلود ناموفق بود"))
                            .setMessage(message)
                            .setPositiveButton(tr("باشه"), null)
                            .show()
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun installDownloadedApk(file: java.io.File) {
        if (!file.exists()) {
            toast("فایل نصب پیدا نشد؛ دوباره بروزرسانی را بررسی کنید")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = file
            AlertDialog.Builder(this)
                .setTitle(tr("اجازه‌ی نصب برنامه"))
                .setMessage(tr("برای نصب نسخه‌ی جدید، اجازه‌ی نصب برنامه از TakVPN را در تنظیمات فعال کنید."))
                .setPositiveButton(tr("رفتن به تنظیمات")) { _, _ ->
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton(tr("بعداً"), null)
                .show()
            return
        }
        try {
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("باز کردن نصب‌کننده ناموفق بود: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT >= 26 &&
            packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile?.let {
                pendingInstallFile = null
                installDownloadedApk(it)
            }
        }
    }

    private fun maybeCheckForUpdates() {
        val last = prefs.getLong("last_update_check", 0L)
        if (System.currentTimeMillis() - last >= 24L * 60L * 60L * 1000L) {
            ui.postDelayed({ if (!isDestroyed) checkUpdate(silent = true) }, 1400L)
        }
    }

    // ---------- subscriptions ----------

    private fun addSubDialog() {
        val input = EditText(this)
        input.hint = "https://..."
        input.setSingleLine()
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(this)
            .setTitle(tr("لینک اشتراک"))
            .setView(box)
            .setPositiveButton(tr("افزودن")) { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) fetchSub(url)
            }
            .setNegativeButton(tr("لغو"), null)
            .show()
    }

    private fun hostOf(url: String): String =
        try {
            Uri.parse(url).host ?: "اشتراک"
        } catch (e: Exception) {
            "اشتراک"
        }

    private fun fetchSub(url: String) {
        toast("در حال دریافت اشتراک...")
        AppLog.add("دریافت اشتراک از ${hostOf(url)}")
        pool.execute {
            try {
                val res = SubFetcher.fetch(url)
                val links = res.links
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    res.info?.let { subs.setInfo(url, it) }
                    if (links.isEmpty()) {
                        AppLog.add("اشتراک خالی یا بدون کانفیگ پشتیبانی‌شده بود")
                        toast("کانفیگ پشتیبانی‌شده‌ای توی اشتراک پیدا نشد")
                    } else {
                        subs.apply(url, links)
                        AppLog.add("${links.size} کانفیگ از ${hostOf(url)} اضافه یا به‌روز شد")
                        toast("${links.size} کانفیگ از اشتراک اضافه یا به‌روز شد")
                    }
                    render()
                }
            } catch (e: Throwable) {
                val m = (e.message ?: e.toString()).replace("\n", " ").take(80)
                AppLog.add("خطای اشتراک (${hostOf(url)}): $m")
                runOnUiThread { toast("دریافت اشتراک ناموفق: $m") }
            }
        }
    }

    private fun refreshSubInfo() {
        subs.urls().forEach { url ->
            pool.execute {
                try {
                    val info = SubFetcher.fetch(url).info
                    if (info != null) {
                        runOnUiThread {
                            if (!isDestroyed) {
                                subs.setInfo(url, info)
                                render()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    AppLog.add("به‌روزرسانی اطلاعات اشتراک ناموفق (${hostOf(url)}): ${(e.message ?: "").take(60)}")
                }
            }
        }
    }

    private fun updateAllSubs() {
        val urls = subs.urls()
        if (urls.isEmpty()) {
            toast("اشتراکی ثبت نشده")
            return
        }
        urls.forEach { fetchSub(it) }
    }

    private fun manageSubs() {
        val urls = subs.urls()
        if (urls.isEmpty()) {
            toast("اشتراکی ثبت نشده")
            return
        }
        val labels = urls.map { "${hostOf(it)}  (${subs.count(it)} کانفیگ)" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(tr("برای حذف، روی اشتراک بزنید"))
            .setItems(labels) { _, i ->
                val url = urls[i]
                AlertDialog.Builder(this)
                    .setTitle(tr("حذف اشتراک"))
                    .setMessage(tr("اشتراک ${hostOf(url)} و کانفیگ‌هاش حذف بشه؟"))
                    .setPositiveButton(tr("حذف")) { _, _ ->
                        if (TakVpnService.isRunning) {
                            toast("اول اتصال رو قطع کنید")
                        } else {
                            subs.remove(url)
                            render()
                        }
                    }
                    .setNegativeButton(tr("لغو"), null)
                    .show()
            }
            .setNegativeButton(tr("بستن"), null)
            .show()
    }

    private fun subLabel(i: SubInfo): String {
        val parts = mutableListOf<String>()
        if (i.expire > 0) {
            val days = (i.expire * 1000 - System.currentTimeMillis()) / 86400000L
            parts.add(
                when {
                    days < 0 -> "⏳ منقضی شده"
                    days == 0L -> "⏳ کمتر از ۱ روز"
                    else -> "⏳ $days روز"
                }
            )
        } else {
            parts.add("⏳ نامحدود")
        }
        if (i.total > 0) {
            val rem = i.total - i.up - i.down
            parts.add("📦 " + fmt(rem.coerceAtLeast(0L)) + (if (rem <= 0) " (تمام شده)" else ""))
        } else {
            parts.add("📦 نامحدود")
        }
        return parts.joinToString("      ")
    }

    // ---------- log ----------

    private fun showLog() {
        val core = AppLog.coreLog()
        val text = "— رویدادهای اپ —\n" + AppLog.text().ifEmpty { "(خالی)" } +
            "\n\n— لاگ هسته —\n" + core.ifEmpty { "(خالی)" }
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(16), dp(8), dp(16), dp(8))
        val sv = ScrollView(this)
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle(tr("لاگ"))
            .setView(sv)
            .setPositiveButton(tr("بستن"), null)
            .setNeutralButton(tr("کپی")) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("log", text))
                toast("لاگ کپی شد")
            }
            .setNegativeButton(tr("پاک کردن")) { _, _ ->
                AppLog.clear()
                AppLog.clearCore()
            }
            .show()
    }

    // ---------- usage ----------

    private fun updateUsage() {
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        if (rx < 0 || tx < 0) {
            usageText.text = tr("مصرف: روی این گوشی در دسترس نیست")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!inited) {
            baseRx = rx
            baseTx = tx
            lastRx = rx
            lastTx = tx
            lastTime = now
            inited = true
        }
        val dRx = (rx - lastRx).coerceAtLeast(0L)
        val dTx = (tx - lastTx).coerceAtLeast(0L)
        val dt = ((now - lastTime) / 1000.0).coerceAtLeast(0.5)
        flowSpeedView.setRate(((dRx + dTx) / dt).toLong())
        totalBytes += dRx + dTx
        lastRx = rx
        lastTx = tx
        lastTime = now
        tick++
        if (tick % 10 == 0) saveTotal()
        val since = prefs.getLong("since", 0L)
        val secs = if (since > 0) (System.currentTimeMillis() - since) / 1000 else 0L
        val dur = String.format(Locale.US, "%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60)
        usageText.text =
            "⏱ $dur    ↓ ${fmt(rx - baseRx)} (${fmt((dRx / dt).toLong())}/s)    ↑ ${fmt(tx - baseTx)} (${fmt((dTx / dt).toLong())}/s)\n" +
                tr("مصرف کل: ") + fmt(totalBytes)
    }

    private fun startTracking() {
        ui.removeCallbacks(ticker)
        ui.post(ticker)
    }

    private fun stopTracking() {
        ui.removeCallbacks(ticker)
        inited = false
        saveTotal()
        usageText.text = tr("مصرف کل: ") + fmt(totalBytes)
        flowSpeedView.setRate(0L)
    }

    private fun saveTotal() {
        prefs.edit().putLong("total", totalBytes).apply()
    }

    private fun fmt(b: Long): String {
        val v = b.toDouble()
        return when {
            v < 1024 -> "$b B"
            v < 1024.0 * 1024 -> String.format(Locale.US, "%.1f KB", v / 1024)
            v < 1024.0 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", v / 1048576)
            else -> String.format(Locale.US, "%.2f GB", v / 1073741824)
        }
    }

    // ---------- configs ----------

    private fun addFromText(text: String) {
        var added = 0
        var unsupported = 0
        var invalid = 0
        text.split(Regex("(?=\\b(?:vless|vmess|trojan|hysteria2|hysteria|hy2|ss|tuic)://)"))
            .map { it.trim() }
            .filter { it.contains("://") }
            .forEach { item ->
                if (ConfigExtras.isSupported(item)) {
                    try {
                        ConfigParser.buildConfig(item)
                        store.add(item)
                        added++
                    } catch (e: Exception) {
                        invalid++
                    }
                } else {
                    unsupported++
                }
            }
        var msg = if (added > 0) "$added کانفیگ اضافه شد" else "کانفیگ معتبری اضافه نشد"
        if (unsupported > 0) msg += "\n$unsupported مورد پروتکل پشتیبانی‌نشده"
        if (invalid > 0) msg += "\n$invalid مورد نامعتبر"
        AppLog.add(msg.replace("\n", "، "))
        toast(msg)
        if (added > 0) render()
    }

    private fun addSmartCard() {
        val v = layoutInflater.inflate(R.layout.item_config, listContainer, false)
        val smartParams = v.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        smartParams.marginStart = dp(12)
        smartParams.marginEnd = dp(12)
        v.layoutParams = smartParams
        val card = v as MaterialCardView
        card.radius = dp(13).toFloat()
        val smartTitle = v.findViewById<TextView>(R.id.nameText)
        val language = AppLanguage.code(this)
        smartTitle.text = if (language == "en") "⚡ Smart switch" else AppLanguage.translate("⚡ سوییچ هوشمند", language)
        val d = v.findViewById<TextView>(R.id.smartDescription)
        d.visibility = View.VISIBLE
        d.text = if (language == "en") "Automatically finds and connects to the best config" else AppLanguage.translate("هر بار وصل بشید، بهترین کانفیگ رو خودش پیدا و وصل می‌کنه", language)
        d.setTextColor(smartTitle.currentTextColor)
        d.setTypeface(null, Typeface.BOLD)
        d.textSize = 11f
        d.maxLines = 2
        d.layoutParams = d.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        v.findViewById<View>(R.id.endpointRow).visibility = View.GONE
        (v.getChildAt(0) as? LinearLayout)?.let { content ->
            content.setPadding(content.paddingLeft, dp(10), content.paddingRight, dp(10))
        }
        card.minimumHeight = dp(76)
        v.findViewById<View>(R.id.subText).visibility = View.GONE
        v.findViewById<View>(R.id.editBtn).visibility = View.GONE
        v.findViewById<View>(R.id.deleteBtn).visibility = View.GONE
        v.findViewById<View>(R.id.shareBtn).visibility = View.GONE
        val sel = smartSelected()
        card.strokeWidth = if (sel) dp(3) else 0
        card.setCardBackgroundColor(c(if (sel) R.color.card_selected else R.color.card))
        card.setOnClickListener {
            val switchWhileConnected = TakVpnService.isRunning
            prefs.edit().putBoolean("smart_sel", true).apply()
            render()
            // Switching from a manual profile to Smart while connected must
            // choose the best profile and replace the currently active tunnel.
            if (switchWhileConnected) connectSmart()
        }
        listContainer.addView(v)
    }

    private fun render() {
        listContainer.removeAllViews()
        emptyText.visibility = if (store.items.isEmpty()) View.VISIBLE else View.GONE
        if (store.items.isNotEmpty()) addSmartCard()
        val smartSel = smartSelected()
        for (cfg in store.items) {
            val v = layoutInflater.inflate(R.layout.item_config, listContainer, false)
            val card = v as MaterialCardView
            v.findViewById<TextView>(R.id.nameText).text = cfg.name
            v.findViewById<TextView>(R.id.typeText).apply {
                text = ConfigStore.typeOf(cfg.link)
                setTextColor(Color.parseColor("#FFFF6A00"))
                setTypeface(null, Typeface.BOLD)
            }
            val endpointRow = v.findViewById<View>(R.id.endpointRow)
            val endpointText = v.findViewById<TextView>(R.id.endpointText)
            val endpoint = ServerEndpoint.addressPortFromLink(cfg.link)
            endpointText.text = endpoint.orEmpty()
            endpointRow.visibility = if (endpoint.isNullOrBlank()) View.GONE else View.VISIBLE
            val exitIpText = v.findViewById<TextView>(R.id.exitIpText)
            val exitIp = exitIps[cfg.id]
            exitIpText.text = exitIp.orEmpty()
            exitIpText.visibility = if (showExitIps && !exitIp.isNullOrBlank()) View.VISIBLE else View.GONE
            val ping = v.findViewById<TextView>(R.id.pingText)
            val (label, color) = pingLabel(cfg.id)
            ping.text = label
            ping.setTextColor(color)
            val subView = v.findViewById<TextView>(R.id.subText)
            val info = subs.infoForConfig(cfg.id)
            if (info != null) {
                subView.text = subLabel(info)
                subView.visibility = View.VISIBLE
            } else {
                subView.visibility = View.GONE
            }
            val selected = cfg.id == store.selectedId && !smartSel
            card.strokeWidth = if (selected) dp(3) else 0
            card.setCardBackgroundColor(c(if (selected) R.color.card_selected else R.color.card))
            card.setOnClickListener {
                val switchWhileConnected = TakVpnService.isRunning &&
                    TakVpnService.activeLink != cfg.link
                prefs.edit().putBoolean("smart_sel", false).apply()
                store.select(cfg.id)
                render()
                if (switchWhileConnected) {
                    // Selecting another card while connected must replace the active VPN profile.
                    connectWith(cfg, false)
                }
            }
            v.findViewById<View>(R.id.editBtn).setOnClickListener {
                ConfigEditor.editConfig(this, store, cfg) { render() }
            }
            v.findViewById<View>(R.id.deleteBtn).setOnClickListener { confirmDelete(cfg) }
            v.findViewById<View>(R.id.shareBtn).setOnClickListener { share(cfg) }
            ping.setOnClickListener { pingOne(cfg) }
            listContainer.addView(v)
        }
        applyAppPalette()
    }

    private fun pingLabel(id: String): Pair<String, Int> {
        val soft = c(R.color.ink_soft)
        val flag = countryFlags[id]?.let { "$it  " }.orEmpty()
        val (label, color) = when (val p = pings[id]) {
            null -> (if (AppLanguage.code(this) == "en") "Tap here to test ping" else "پینگ: برای تست اینجا بزنید") to Color.WHITE
            -2L -> (if (AppLanguage.code(this) == "en") "Testing..." else "در حال تست...") to soft
            -1L -> {
                val e = errors[id].orEmpty()
                (if (AppLanguage.code(this) == "en") (if (e.isBlank()) "Failed" else "Failed: $e") else (if (e.isBlank()) "ناموفق" else "ناموفق: $e")) to c(R.color.ping_fail)
            }
            else -> "$p ms" to c(if ((p ?: 0L) < 400L) R.color.ping_good else R.color.ping_slow)
        }
        return (if (pings[id] != null && pings[id] != -2L) flag + label else label) to color
    }

    private fun lookupCountryFlag(cfg: Cfg) {
        val exitIp = exitIps[cfg.id] ?: return
        synchronized(countryLookups) {
            if (cfg.id in countryLookups) return
            countryLookups.add(cfg.id)
        }
        pool.execute {
            var connection: HttpURLConnection? = null
            try {
                val encodedIp = java.net.URLEncoder.encode(exitIp, "UTF-8")
                connection = (URL("https://ipwho.is/$encodedIp").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (!json.optBoolean("success", false)) return@execute
                val code = json.optString("country_code").uppercase(Locale.US)
                if (code.length != 2 || !code.all { it in 'A'..'Z' }) return@execute
                val flag = code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
                runOnUiThread {
                    if (!isDestroyed) {
                        countryFlags[cfg.id] = flag
                        render()
                    }
                }
            } catch (e: Throwable) {
                AppLog.add("تشخیص کشور Exit IP ناموفق: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private data class ProfileTest(val latencyMs: Long, val exitIp: String, val error: String = "")

    private fun measure(link: String): ProfileTest {
        return try {
            val result = OutboundProbe.test(applicationContext, link)
            ProfileTest(result.latencyMs, result.exitIp)
        } catch (e: Throwable) {
            ProfileTest(-1L, "", (e.message ?: e.toString()).replace("\n", " ").take(90))
        }
    }

    // Smart Switch measures TCP connection latency to each server's host:port.
    private fun measureTcpEndpoint(link: String): ProfileTest {
        val endpoint = ServerEndpoint.addressPortFromLink(link) ?: return ProfileTest(-1L, "", "endpoint unavailable")
        try {
            val host: String
            val port: Int
            if (endpoint.startsWith("[")) {
                val close = endpoint.indexOf(']')
                if (close <= 1) return ProfileTest(-1L, "", "invalid endpoint")
                host = endpoint.substring(1, close)
                port = endpoint.substring(close + 1).removePrefix(":").toInt()
            } else {
                val colon = endpoint.lastIndexOf(':')
                if (colon <= 0) return ProfileTest(-1L, "", "endpoint port unavailable")
                host = endpoint.substring(0, colon)
                port = endpoint.substring(colon + 1).toInt()
            }
            if (port !in 1..65535) return ProfileTest(-1L, "", "invalid port")
            Socket().use { socket ->
                val started = SystemClock.elapsedRealtime()
                socket.connect(InetSocketAddress(host, port), 2500)
                return ProfileTest((SystemClock.elapsedRealtime() - started).coerceAtLeast(1L), "")
            }
        } catch (e: Throwable) {
            return ProfileTest(-1L, "", (e.message ?: e.toString()).replace("\n", " ").take(90))
        }
    }

    private fun tcpTestAllThen(done: (List<Cfg>) -> Unit) {
        val list = store.items.toList()
        list.forEach { tcpLatencies[it.id] = -1L }
        val left = AtomicInteger(list.size)
        list.forEach { cfg ->
            pool.execute {
                val result = measureTcpEndpoint(cfg.link)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    tcpLatencies[cfg.id] = result.latencyMs
                    if (left.decrementAndGet() == 0) done(list)
                }
            }
        }
    }

    private fun bestOfTcp(list: List<Cfg>): Cfg? =
        list.filter { (tcpLatencies[it.id] ?: -1L) > 0 }
            .minByOrNull { tcpLatencies[it.id] ?: Long.MAX_VALUE }

    private fun pingOne(cfg: Cfg) {
        pings[cfg.id] = -2L
        render()
        startPing(cfg)
    }

    private fun pingAll() {
        if (store.items.isEmpty()) {
            toast(AppLanguage.translate("کانفیگی وجود نداره", AppLanguage.code(this)))
            return
        }
        store.items.forEach { pings[it.id] = -2L }
        render()
        store.items.toList().forEach { startPing(it) }
    }

    private fun startPing(cfg: Cfg) {
        pool.execute {
            val result = measure(cfg.link)
            if (result.latencyMs <= 0) AppLog.add("تست خروجی ناموفق (${cfg.name}): ${result.error}")
            runOnUiThread {
                if (!isDestroyed) {
                    errors[cfg.id] = result.error
                    pings[cfg.id] = result.latencyMs
                    if (result.exitIp.isNotBlank()) exitIps[cfg.id] = result.exitIp else exitIps.remove(cfg.id)
                    render()
                    lookupCountryFlag(cfg)
                }
            }
        }
    }

    private fun testAllThen(done: (List<Cfg>) -> Unit) {
        val list = store.items.toList()
        list.forEach { pings[it.id] = -2L }
        render()
        val left = AtomicInteger(list.size)
        list.forEach { cfg ->
            pool.execute {
                val result = measure(cfg.link)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    errors[cfg.id] = result.error
                    pings[cfg.id] = result.latencyMs
                    if (result.exitIp.isNotBlank()) exitIps[cfg.id] = result.exitIp else exitIps.remove(cfg.id)
                    render()
                    lookupCountryFlag(cfg)
                    if (left.decrementAndGet() == 0) done(list)
                }
            }
        }
    }

    private fun bestOf(list: List<Cfg>): Cfg? =
        list.filter { (pings[it.id] ?: -1L) > 0 }.minByOrNull { pings[it.id] ?: Long.MAX_VALUE }

    private fun connectFastest() {
        if (TakVpnService.isRunning) {
            toast("اول اتصال رو قطع کنید")
            return
        }
        if (store.items.isEmpty()) {
            toast(AppLanguage.translate("کانفیگی وجود نداره", AppLanguage.code(this)))
            return
        }
        startConnectVisual()
        toast("در حال پیدا کردن سریع‌ترین کانفیگ...")
        testAllThen { list ->
            val best = bestOf(list)
            if (best == null) {
                toast(AppLanguage.translate("هیچ کانفیگی پینگ نداد", AppLanguage.code(this)))
            } else {
                prefs.edit().putBoolean("smart_sel", false).apply()
                store.select(best.id)
                render()
                AppLog.add("سریع‌ترین: ${best.name} (${pings[best.id]} ms)")
                connectWith(best, false)
            }
        }
    }

    private fun connectSmart() {
        if (store.items.isEmpty()) {
            toast(AppLanguage.translate("کانفیگی وجود نداره", AppLanguage.code(this)))
            return
        }
        startConnectVisual()
        testText.text = AppLanguage.translate("در حال پیدا کردن بهترین کانفیگ...", AppLanguage.code(this))
        fab.isEnabled = false
        tcpTestAllThen { list ->
            fab.isEnabled = true
            val best = bestOfTcp(list)
            if (best == null) {
                testText.text = AppLanguage.translate("هیچ کانفیگی پینگ نداد", AppLanguage.code(this))
                toast(AppLanguage.translate("هیچ کانفیگی پینگ نداد", AppLanguage.code(this)))
            } else {
                AppLog.add("سوییچ هوشمند (TCP): ${best.name} (${pings[best.id]} ms)")
                connectWith(best, true)
            }
        }
    }

    private fun sortByPing() {
        if (store.items.isEmpty()) {
            toast("کانفیگی برای مرتب‌سازی وجود نداره")
            return
        }
        val sel = store.selectedId
        val ascending = sortAscending
        val measured = store.items.filter { (pings[it.id] ?: -2L) >= 0L }
        val unmeasured = store.items.filter { (pings[it.id] ?: -2L) < 0L }
        val ordered = if (ascending) {
            measured.sortedWith(compareBy<Cfg> { pings[it.id] ?: Long.MAX_VALUE }.thenBy { it.name.lowercase(Locale.ROOT) })
        } else {
            measured.sortedWith(compareByDescending<Cfg> { pings[it.id] ?: Long.MIN_VALUE }.thenBy { it.name.lowercase(Locale.ROOT) })
        }
        store.items.clear()
        store.items.addAll(ordered)
        store.items.addAll(unmeasured.sortedBy { it.name.lowercase(Locale.ROOT) })
        store.select(sel)
        sortAscending = !sortAscending
        render()
        toast(if (ascending) "مرتب‌سازی پینگ: کم به زیاد" else "مرتب‌سازی پینگ: زیاد به کم")
    }

    private fun deleteDead() {
        val dead = store.items.filter { pings[it.id] == -1L }
        if (dead.isEmpty()) {
            toast("کانفیگ ناموفقی نیست (اول «پینگ همه» رو بزنید)")
            return
        }
        if (TakVpnService.isRunning) {
            toast("اول اتصال رو قطع کنید")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("حذف ناموفق‌ها"))
            .setMessage(tr("${dead.size} کانفیگ ناموفق حذف بشن؟"))
            .setPositiveButton(tr("حذف")) { _, _ ->
                dead.forEach {
                    store.remove(it.id)
                    pings.remove(it.id)
                    errors.remove(it.id)
                }
                render()
            }
            .setNegativeButton(tr("لغو"), null)
            .show()
    }

    private fun runConnectionTest() {
        val link = if (TakVpnService.isRunning && TakVpnService.activeLink.isNotEmpty()) {
            TakVpnService.activeLink
        } else {
            store.selected()?.link
        } ?: return
        val smart = smartSelected()
        val name = TakVpnService.activeName
        testText.text = AppLanguage.translate("در حال تست اتصال...", AppLanguage.code(this))
        pool.execute {
            val result = measure(link)
            AppLog.add(if (result.latencyMs > 0) "تست خروجی: ${result.latencyMs} ms, ${result.exitIp}" else "تست خروجی ناموفق: ${result.error}")
            runOnUiThread {
                if (!isDestroyed) {
                    val prefix = if (smart && name.isNotEmpty()) "هوشمند: $name\n" else ""
                    testText.text = if (result.latencyMs > 0) {
                        prefix + (if (AppLanguage.code(this) == "en") "Real tunnel latency: ${result.latencyMs} ms\n${result.exitIp}" else "تأخیر واقعی تونل: ${result.latencyMs} میلی‌ثانیه\n${result.exitIp}")
                    } else {
                        prefix + (if (AppLanguage.code(this) == "en") "Test failed" else "تست ناموفق") + (if (result.error.isNotEmpty()) ": ${result.error}" else "")
                    }
                }
            }
        }
    }

    private fun confirmDelete(cfg: Cfg) {
        if (TakVpnService.isRunning && cfg.link == TakVpnService.activeLink) {
            toast("اول اتصال رو قطع کنید")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("حذف کانفیگ"))
            .setMessage(tr("«${cfg.name}» حذف بشه؟"))
            .setPositiveButton(tr("حذف")) { _, _ ->
                store.remove(cfg.id)
                pings.remove(cfg.id)
                errors.remove(cfg.id)
                render()
            }
            .setNegativeButton(tr("لغو"), null)
            .show()
    }

    private fun share(cfg: Cfg) {
        AlertDialog.Builder(this)
            .setTitle(cfg.name)
            .setItems(arrayOf("📋 کپی لینک", "📤 ارسال به برنامه‌های دیگه", "🔳 نمایش QR")) { _, i ->
                when (i) {
                    0 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("config", cfg.link))
                        toast("لینک کپی شد")
                    }
                    1 -> {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, cfg.link)
                        startActivity(Intent.createChooser(send, "اشتراک‌گذاری کانفیگ"))
                    }
                    2 -> showQr(cfg)
                }
            }
            .show()
    }

    private fun showQr(cfg: Cfg) {
        val bmp = try {
            BarcodeEncoder().encodeBitmap(cfg.link, BarcodeFormat.QR_CODE, 800, 800)
        } catch (e: Exception) {
            toast("ساخت QR ناموفق بود (لینک خیلی بلنده)")
            return
        }
        val iv = ImageView(this)
        iv.setImageBitmap(bmp)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setBackgroundColor(Color.WHITE)
        iv.setPadding(dp(12), dp(12), dp(12), dp(12))
        val box = FrameLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(iv, FrameLayout.LayoutParams(dp(280), dp(280), Gravity.CENTER))
        AlertDialog.Builder(this)
            .setTitle(cfg.name)
            .setView(box)
            .setPositiveButton(tr("بستن"), null)
            .show()
    }

    // ---------- connect ----------

    private fun onConnectClicked() {
        if (TakVpnService.isRunning) {
            startService(Intent(this, TakVpnService::class.java).setAction(TakVpnService.ACTION_STOP))
            return
        }
        if (store.items.isEmpty()) {
            toast("اول یه کانفیگ اضافه کنید")
            return
        }
        if (smartSelected()) {
            connectSmart()
            return
        }
        val cfg = store.selected()
        if (cfg == null) {
            toast("اول یه کانفیگ انتخاب کنید")
            return
        }
        connectWith(cfg, false)
    }

    private fun connectWith(cfg: Cfg, smart: Boolean) {
        val json = try {
            ConfigExtras.build(cfg.link)
        } catch (e: Exception) {
            toast("کانفیگ نامعتبر: ${e.message}")
            return
        }
        startConnectVisual()
        AppLog.add((if (smart) "اتصال هوشمند: " else "درخواست اتصال: ") + cfg.name)
        pendingConfig = json
        pendingName = cfg.name
        pendingLink = cfg.link
        pendingSmart = smart
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpn(json)
    }

    private fun startVpn(config: String) {
        startService(
            Intent(this, TakVpnService::class.java)
                .putExtra(TakVpnService.EXTRA_CONFIG, config)
                .putExtra(TakVpnService.EXTRA_SMART, pendingSmart)
                .putExtra(TakVpnService.EXTRA_NAME, pendingName)
                .putExtra(TakVpnService.EXTRA_LINK, pendingLink)
        )
    }

    private fun fetchConnectionInfo() {
        val requestId = ++connectionInfoRequest
        if (!TakVpnService.isRunning) return
        connectionInfoText.text = AppLanguage.translate("در حال دریافت IP خروجی واقعی تونل...", AppLanguage.code(this))
        connectionInfoText.visibility = View.VISIBLE
        pool.execute {
            var connection: HttpURLConnection? = null
            try {
                val socks = java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", TakVpnService.IP_PROBE_SOCKS_PORT)
                )
                // Resolve the public IP through the active Xray outbound, not the
                // server address embedded in the imported profile.
                connection = (URL("https://api.ipify.org").openConnection(socks) as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                }
                val exitIp = connection.inputStream.bufferedReader().use { it.readText().trim() }
                if (exitIp.isBlank()) throw IllegalStateException("Empty exit IP")
                connection.disconnect()
                connection = null

                val encodedIp = java.net.URLEncoder.encode(exitIp, "UTF-8")
                connection = (URL("https://ipwho.is/$encodedIp").openConnection(socks) as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    requestMethod = "GET"
                }
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (!json.optBoolean("success", false)) throw IllegalStateException("Exit IP lookup failed")
                val code = json.optString("country_code").uppercase(Locale.US)
                val flag = if (code.length == 2 && code.all { it in 'A'..'Z' }) {
                    code.map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }.joinToString("")
                } else ""
                val country = json.optString("country")
                // Keep Persian exit details label-free; retain the English UI wording elsewhere.
                val result = listOf(exitIp, listOf(flag, code, country).filter { it.isNotBlank() }.joinToString(" "))
                    .filter { it.isNotBlank() }.joinToString("  ·  ")
                runOnUiThread {
                    if (!isDestroyed && TakVpnService.isRunning && requestId == connectionInfoRequest) {
                        connectionInfoText.text = result
                        connectionInfoText.visibility = View.VISIBLE
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    if (!isDestroyed && TakVpnService.isRunning && requestId == connectionInfoRequest) {
                        connectionInfoText.text = AppLanguage.translate("تشخیص IP خروجی تونل ناموفق بود", AppLanguage.code(this))
                        connectionInfoText.visibility = View.VISIBLE
                    }
                }
                AppLog.add("تشخیص IP خروجی ناموفق: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun updateUi(running: Boolean) {
        connectIcon.setConnected(running)
        if (running) {
            connectIcon.setFlowing(true)
            if (connectColorAnimator == null) startConnectColorTransition()
        } else resetConnectVisual()
        statusText.text = if (running) tr("متصل") else tr("قطع")
        if (running) {
            if (prefs.getLong("since", 0L) == 0L) {
                prefs.edit().putLong("since", System.currentTimeMillis()).apply()
            }
            if (!lastRunning) {
                AppLog.add("متصل شد: ${TakVpnService.activeName}")
                runConnectionTest()
                refreshSubInfo()
                fetchConnectionInfo()
            }
            titleGlow.start()
            statusGlow.start()
            startTracking()
        } else {
            prefs.edit().putLong("since", 0L).apply()
            connectionInfoRequest++
            connectionInfoText.visibility = View.GONE
            connectionInfoText.text = ""
            if (lastRunning) AppLog.add("اتصال قطع شد")
            titleGlow.stop(c(R.color.header_title))
            statusGlow.stop(c(R.color.status_off))
            testText.text = AppLanguage.translate("قطع شده. برای اتصال دکمه‌ی پخش رو بزنید", AppLanguage.code(this))
            stopTracking()
        }
        lastRunning = running
    }

    private fun startConnectVisual() {
        connectIcon.setFlowing(true)
        startConnectColorTransition()
    }

    private fun startConnectColorTransition() {
        connectColorAnimator?.cancel()
        connectColorAnimator = ValueAnimator.ofObject(android.animation.ArgbEvaluator(), Color.parseColor("#151515"), Color.parseColor("#FF9800")).apply {
            duration = 10_000L
            interpolator = LinearInterpolator()
            addUpdateListener { fab.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int) }
            start()
        }
    }

    private fun resetConnectVisual() {
        connectColorAnimator?.cancel()
        connectColorAnimator = null
        fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#151515"))
    }

    private inner class FlowSpeedView(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = Color.WHITE }
        private val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private var level = 0.04f
        private var phase = 0f
        private var waveAnimator: ValueAnimator? = null
        fun setRate(bytesPerSecond: Long) {
            val normalized = (ln(1.0 + bytesPerSecond.coerceAtLeast(0L)) / ln(1.0 + 2_000_000.0)).toFloat().coerceIn(0f, 1f)
            level = 0.04f + normalized * 0.92f
            fill.color = when {
                normalized > 0.66f -> Color.rgb(40, 210, 145)
                normalized > 0.30f -> Color.rgb(255, 175, 55)
                else -> Color.rgb(235, 85, 75)
            }
            invalidate()
        }
        fun stopFlow() { waveAnimator?.cancel(); waveAnimator = null }
        fun startFlow() {
            if (waveAnimator?.isRunning != true) waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
                duration = 1800L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }; start()
            }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); startFlow() }
        override fun onDetachedFromWindow() { stopFlow(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = minOf(width, height) * 0.43f
            if (radius <= 0f) return
            val cx = width / 2f; val cy = height / 2f
            val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
            val save = canvas.save(); canvas.clipPath(clip)
            val surfaceY = cy + radius * (0.88f - level * 1.72f)
            val liquid = Path().apply {
                moveTo(cx-radius, cy+radius); lineTo(cx-radius, surfaceY)
                for (step in 0..24) {
                    val px = cx-radius + (2f*radius*step/24f)
                    val py = surfaceY + sin(step/24.0*Math.PI*2.0 + phase.toDouble()).toFloat()*radius*0.10f
                    lineTo(px,py)
                }
                lineTo(cx+radius,cy+radius); close()
            }
            canvas.drawPath(liquid, fill)
            for (i in 0..2) {
                val bx = cx + sin((phase*0.8f+i*2.1f).toDouble()).toFloat()*radius*0.52f
                val by = cy + radius*(0.52f-((phase*0.12f+i*0.31f)%0.72f))
                bubble.color = Color.WHITE; bubble.alpha = 170
                canvas.drawCircle(bx,by,dp(1).toFloat(),bubble)
            }
            canvas.restoreToCount(save); canvas.drawCircle(cx,cy,radius,outline)
        }
    }

    private inner class ConnectTDrawable : Drawable() {
        private val tPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setShadowLayer(dp(5).toFloat(), 0f, 0f, Color.WHITE)
        }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val matrix = Matrix()
        private val badgeGradient: Shader
        private var shine: ValueAnimator? = null
        private var waveAnimator: ValueAnimator? = null
        private var rotationAnimator: ValueAnimator? = null
        private var flowing = false
        private var wavePhase = 0f
        private var rotation = 0f
        private var connected = false
        private var offset = -240f
        private var themeColor = Color.parseColor("#AA55FF")
        private var themeLight = Color.parseColor("#E0B8FF")
        private var themeDeep = Color.parseColor("#6633AA")

        init {
            badgeGradient = SweepGradient(0f, 0f,
                intArrayOf(
                    themeDeep, themeColor, themeLight, themeColor, themeDeep
                ), null)
        }

        fun setThemeColor(color: Int) {
            themeColor = color
            themeLight = androidx.core.graphics.ColorUtils.blendARGB(color, Color.WHITE, 0.42f)
            themeDeep = androidx.core.graphics.ColorUtils.blendARGB(color, Color.BLACK, 0.38f)
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val radius = minOf(b.width(), b.height()) * 0.405f
            val save = canvas.save()

            // Soft multicolor aura remains behind the rotating medallion.
            haloPaint.shader = android.graphics.RadialGradient(
                cx, cy, radius * 1.85f,
                intArrayOf((0x66000000.toInt() or (themeColor and 0x00FFFFFF)), (0x22000000.toInt() or (themeLight and 0x00FFFFFF)), Color.TRANSPARENT),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius * 1.85f, haloPaint)
            haloPaint.shader = null

            // Moving neon energy trails orbit the badge.
            if (flowing) {
                for (i in 0..2) {
                    val phase = wavePhase + i * 2.08f
                    val trailRadius = radius * (1.23f + i * 0.10f)
                    glowPaint.color = when (i) {
                        0 -> themeColor
                        1 -> themeLight
                        else -> themeDeep
                    }
                    glowPaint.strokeWidth = dp(if (i == 0) 2 else 1).toFloat()
                    glowPaint.alpha = 185 - i * 32
                    val arc = android.graphics.RectF(cx-trailRadius, cy-trailRadius, cx+trailRadius, cy+trailRadius)
                    canvas.drawArc(arc, Math.toDegrees(phase.toDouble()).toFloat(), 218f, false, glowPaint)
                }
            }

            // Rotate only the scalloped badge; the central T is drawn afterward and stays upright.
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            val badge = Path()
            val lobes = 12
            for (i in 0..(lobes * 8)) {
                val angle = i.toDouble() / (lobes * 8) * Math.PI * 2.0
                val wave = (1.0 + 0.105 * kotlin.math.cos(angle * lobes)).toFloat()
                val r = radius * wave
                val x = cx + kotlin.math.cos(angle).toFloat() * r
                val y = cy + kotlin.math.sin(angle).toFloat() * r
                if (i == 0) badge.moveTo(x, y) else badge.lineTo(x, y)
            }
            badge.close()
            val gradientMatrix = Matrix()
            gradientMatrix.setRotate(rotation, cx, cy)
            (badgeGradient as? SweepGradient)?.setLocalMatrix(gradientMatrix)
            badgePaint.shader = badgeGradient
            badgePaint.setShadowLayer(dp(8).toFloat(), 0f, 0f, themeColor)
            canvas.drawPath(badge, badgePaint)
            badgePaint.clearShadowLayer()
            badgePaint.shader = null

            // Bright rim follows the rotating flower edge.
            glowPaint.color = themeLight
            glowPaint.alpha = 235
            glowPaint.strokeWidth = dp(1).toFloat()
            glowPaint.style = Paint.Style.STROKE
            canvas.drawPath(badge, glowPaint)
            glowPaint.style = Paint.Style.STROKE
            canvas.restore()

            // Keep the T perfectly upright and centered while the badge spins.
            tPaint.textSize = b.height() * 0.67f
            tPaint.shader = null
            val ty = cy - (tPaint.ascent() + tPaint.descent()) / 2f
            canvas.drawText("T", cx, ty, tPaint)

            // Existing connected-state light sweep across the fixed letter.
            if (connected) {
                val sweep = LinearGradient(
                    cx - radius, cy, cx + radius, cy,
                    intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.parseColor("#FFFFD45C"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.44f, 0.56f, 1f), Shader.TileMode.CLAMP
                )
                matrix.setTranslate(offset, 0f)
                sweep.setLocalMatrix(matrix)
                tPaint.shader = sweep
                canvas.drawText("T", cx, ty, tPaint)
                tPaint.shader = null
            }
            canvas.restoreToCount(save)
        }

        fun setConnected(value: Boolean) {
            connected = value
            if (value) startShine() else { shine?.cancel(); shine = null }
            invalidateSelf()
        }

        fun setFlowing(value: Boolean) {
            flowing = value
            if (value) {
                if (rotationAnimator?.isRunning != true) rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = 5200L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                    addUpdateListener { rotation = it.animatedValue as Float; invalidateSelf() }; start()
                }
                if (waveAnimator?.isRunning != true) waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI*2).toFloat()).apply {
                    duration = 2100L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                    addUpdateListener { wavePhase = it.animatedValue as Float; invalidateSelf() }; start()
                }
            } else {
                rotationAnimator?.cancel(); rotationAnimator = null
                waveAnimator?.cancel(); waveAnimator = null
            }
            invalidateSelf()
        }

        fun startShine() {
            if (!connected || shine?.isRunning == true) return
            shine = ValueAnimator.ofFloat(-150f, bounds.width().coerceAtLeast(64).toFloat() + 150f).apply {
                duration = 1250L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                addUpdateListener { offset = it.animatedValue as Float; invalidateSelf() }; start()
            }
        }

        fun stopShine() {
            shine?.cancel(); shine = null
        }

        override fun setAlpha(alpha: Int) { tPaint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { tPaint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tr(text: String): String = AppLanguage.translate(text, AppLanguage.code(this))

    private fun toast(msg: String) = Toast.makeText(this, tr(msg), Toast.LENGTH_LONG).show()

    companion object {
        const val PING_URL = "https://www.gstatic.com/generate_204"
    }
}