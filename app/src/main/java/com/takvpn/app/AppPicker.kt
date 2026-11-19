package com.takvpn.app

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

object AppPicker {
    private fun tr(context: Context, text: String): String = AppLanguage.translate(text, AppLanguage.code(context))

    private class AppItem(val label: String, val pkg: String)

    fun summary(ctx: Context): String {
        val p = ctx.getSharedPreferences("takvpn", Context.MODE_PRIVATE)
        val n = (p.getStringSet("split_apps", emptySet()) ?: emptySet()).size
        val lang = AppLanguage.code(ctx)
        return when (p.getInt("split_mode", 0)) {
            1 -> when (lang) {
                "fa" -> "فقط $n اپ با VPN"
                "fr" -> "$n applications via VPN"
                "de" -> "$n Apps über VPN"
                "ru" -> "$n приложений через VPN"
                "zh" -> "$n 个应用使用 VPN"
                "tr" -> "$n uygulama VPN kullanıyor"
                "ar" -> "$n تطبيقات عبر VPN"
                "es" -> "$n apps usan VPN"
                "it" -> "$n app tramite VPN"
                else -> "$n apps use VPN"
            }
            2 -> when (lang) {
                "fa" -> "$n اپ بدون VPN"
                "fr" -> "$n applications sans VPN"
                "de" -> "$n Apps ohne VPN"
                "ru" -> "$n приложений без VPN"
                "zh" -> "$n 个应用绕过 VPN"
                "tr" -> "$n uygulama VPN dışında"
                "ar" -> "$n تطبيقات بدون VPN"
                "es" -> "$n apps sin VPN"
                "it" -> "$n app senza VPN"
                else -> "$n apps bypass VPN"
            }
            else -> tr(ctx, "همه‌ی اپ‌ها با VPN")
        }
    }

    fun show(act: AppCompatActivity) {
        val p = act.getSharedPreferences("takvpn", Context.MODE_PRIVATE)
        val pm = act.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val all = try {
            pm.queryIntentActivities(launch, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != act.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
        val selected = HashSet(p.getStringSet("split_apps", emptySet()) ?: emptySet())
        var mode = p.getInt("split_mode", 0)
        val density = act.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(8), dp(16), 0)

        val group = RadioGroup(act)
        val labels = listOf(
            tr(act, "همه‌ی اپ‌ها با VPN (خاموش)"),
            tr(act, "فقط اپ‌های انتخاب‌شده با VPN"),
            tr(act, "اپ‌های انتخاب‌شده بدون VPN")
        )
        labels.forEachIndexed { i, t ->
            val rb = RadioButton(act)
            rb.id = 100 + i
            rb.text = t
            group.addView(rb)
        }
        group.check(100 + mode)
        group.setOnCheckedChangeListener { _, id -> mode = id - 100 }
        root.addView(group)

        val search = EditText(act)
        search.hint = tr(act, "جستجوی اپ")
        search.setSingleLine()
        root.addView(
            search,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val adapter = object : ArrayAdapter<AppItem>(
            act, android.R.layout.simple_list_item_multiple_choice, ArrayList(all)
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as CheckedTextView
                val item = getItem(position)!!
                v.text = item.label
                v.isChecked = item.pkg in selected
                return v
            }
        }
        val lv = ListView(act)
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            val item = adapter.getItem(pos)!!
            if (!selected.add(item.pkg)) selected.remove(item.pkg)
            adapter.notifyDataSetChanged()
        }
        search.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty().trim()
            adapter.clear()
            adapter.addAll(
                if (q.isEmpty()) all
                else all.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
            )
            adapter.notifyDataSetChanged()
        }
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)))

        AlertDialog.Builder(act)
            .setTitle(tr(act, "انتخاب اپ‌ها"))
            .setView(root)
            .setPositiveButton(tr(act, "ذخیره")) { _, _ ->
                p.edit().putInt("split_mode", mode)
                    .putStringSet("split_apps", HashSet(selected)).apply()
                var msg = tr(act, "ذخیره شد. از اتصال بعدی اعمال می‌شه")
                if (mode != 0 && selected.isEmpty()) {
                    msg += "\n" + tr(act, "(اپی انتخاب نشده، پس مثل حالت خاموش عمل می‌کنه)")
                }
                Toast.makeText(act, msg, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(tr(act, "لغو"), null)
            .show()
    }
}
