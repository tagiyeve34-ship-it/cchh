package com.hesabat.twopersonmessenger

import android.content.Intent
import android.os.*
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.hesabat.twopersonmessenger.databinding.ActivityChatBinding
import java.net.URLEncoder

class ChatActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var last = 0
    private var lastIncomingCallId = 0L
    private var callScreenOpening = false
    private val poll = object : Runnable { override fun run() { load(); heartbeat(); checkIncomingCall(); handler.postDelayed(this, 1800) } }
    private val categories = linkedMapOf(
        "🕘" to listOf("😀","😂","🥰","😍","😊","😉","😎","😭","😢","😡","👍","❤️","🔥","🙏","👌","🎉","🤝","💯"),
        "😀" to listOf("😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🫣","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴"),
        "🐻" to listOf("🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🦄","🐝","🦋","🐌","🐞","🐠","🐬","🐳","🌸","🌹","🌺","🌻","🌞","⭐","🌙","🌈","🔥","💧"),
        "🍔" to listOf("🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🥑","🍆","🥕","🌽","🌶️","🥐","🍞","🧀","🥚","🍳","🍔","🍟","🍕","🌭","🥪","🌮","🍿","🍫","🍩","🍪","🎂","☕","🥤"),
        "⚽" to listOf("⚽","🏀","🏈","⚾","🎾","🏐","🏉","🥏","🎱","🏓","🏸","🥅","⛳","🏹","🎣","🥊","🥋","🎽","🛹","🛼","🚲","🏆","🥇","🎮","🎲","🎯","🎸","🎹","🎧","🎬","📷"),
        "🚗" to listOf("🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚚","🚛","🚜","🏍️","🛵","🚲","✈️","🚁","🚀","🚢","⛵","🚆","🚇","🚉","🏠","🏢","🏨","🏥","🏦","🗽","🗼","🏰","🌍"),
        "💡" to listOf("⌚","📱","💻","⌨️","🖥️","🖨️","📷","🎥","📺","📻","🎙️","⏰","💡","🔦","📕","📚","✏️","📝","💼","📁","📌","📎","🔒","🔑","🔨","🧰","💰","💳","🎁","🎈","❤️","💔","💯","✅","❌","⚠️"),
        "🏳" to listOf("🇦🇿","🇹🇷","🇬🇪","🇷🇺","🇺🇦","🇺🇸","🇬🇧","🇩🇪","🇫🇷","🇮🇹","🇪🇸","🇨🇳","🇯🇵","🇰🇷","🇦🇪","🇸🇦","🇮🇳","🇧🇷","🇨🇦","🇲🇽","🇰🇿","🇺🇿","🇹🇲","🇪🇺")
    )

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        b = ActivityChatBinding.inflate(layoutInflater); setContentView(b.root)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        adapter = MessageAdapter(Session.uid(this)) { deleteDialog(it) }
        b.list.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.list.adapter = adapter
        b.sendBtn.setOnClickListener { send() }
        b.backBtn.setOnClickListener { finish() }
        b.settingsBtn.setOnClickListener { showMenu() }
        b.callBtn.setOnClickListener { openCall(false) }
        b.videoBtn.setOnClickListener { openCall(true) }
        b.attachBtn.setOnClickListener { Toast.makeText(this,"Fayl və şəkil göndərilməsi növbəti server mərhələsində aktivləşdiriləcək",Toast.LENGTH_SHORT).show() }
        b.message.addTextChangedListener { updateSendState() }
        setupEmoji()
        updateSendState()
        handler.post(poll)
    }

    override fun onResume() {
        super.onResume()
        val sec=getSharedPreferences("security",0)
        if(sec.getBoolean("return_to_documents",false)){
            sec.edit().putBoolean("return_to_documents",false).apply()
            startActivity(Intent(this,DocumentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
    }

    override fun onDestroy() { handler.removeCallbacks(poll); super.onDestroy() }

    private fun setupEmoji() {
        categories.keys.forEach { label ->
            val v = TextView(this).apply {
                text=label; textSize=22f; gravity=android.view.Gravity.CENTER
                setPadding(18,8,18,8); setOnClickListener { showEmojiCategory(label) }
            }
            b.categoryRow.addView(v)
        }
        showEmojiCategory(categories.keys.first())
        b.emojiBtn.setOnClickListener { toggleEmoji() }
        b.keyboardBtn.setOnClickListener { closeEmojiAndKeyboard() }
    }

    private fun showEmojiCategory(key:String) {
        b.emojiGrid.removeAllViews()
        categories[key].orEmpty().forEach { e ->
            val v=TextView(this).apply {
                text=e; textSize=29f; gravity=android.view.Gravity.CENTER
                setPadding(4,8,4,8)
                setOnClickListener { val s=b.message.selectionStart.coerceAtLeast(0); b.message.text.insert(s,e) }
            }
            b.emojiGrid.addView(v, android.widget.GridLayout.LayoutParams().apply { width=0; height=56.dp; columnSpec=android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f) })
        }
    }

    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()

    private fun toggleEmoji() {
        if (b.emojiPanel.visibility==View.VISIBLE) closeEmojiAndKeyboard() else {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(b.message.windowToken,0)
            b.emojiPanel.visibility=View.VISIBLE
        }
    }
    private fun closeEmojiAndKeyboard() {
        b.emojiPanel.visibility=View.GONE; b.message.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(b.message,InputMethodManager.SHOW_IMPLICIT)
    }
    private fun updateSendState() { b.sendBtn.alpha=if(b.message.text.isNullOrBlank()) .72f else 1f }

    private fun showMenu() {
        PopupMenu(this,b.settingsBtn).apply {
            menu.add("Tənzimləmələr"); menu.add("Söhbəti bu cihazda təmizlə")
            setOnMenuItemClickListener { when(it.title.toString()) { "Tənzimləmələr" -> startActivity(Intent(this@ChatActivity,SettingsActivity::class.java)); else -> clearLocalChat() }; true }; show()
        }
    }
    private fun clearLocalChat() {
        AlertDialog.Builder(this).setTitle("Söhbəti təmizlə").setMessage("Bu cihazda görünən tarixçə gizlədiləcək. Serverdə mesajlar silinməyəcək.")
            .setNegativeButton("Ləğv et",null).setPositiveButton("Təmizlə") { _,_ ->
                val now=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.US).apply{timeZone=java.util.TimeZone.getTimeZone("GMT+04:00")}.format(java.util.Date())
                getSharedPreferences("security",0).edit().putString("cleared_before",now).apply(); adapter.items.clear(); adapter.notifyDataSetChanged(); last=0
            }.show()
    }
    private fun openCall(video:Boolean) { startActivity(Intent(this,CallActivity::class.java).putExtra("video",video).putExtra("incoming",false)) }
    private fun checkIncomingCall(){
        if(callScreenOpening) return
        Api.get("call_incoming.php?after_id=$lastIncomingCallId",Session.token(this)){ok,raw->
            if(!ok) return@get
            val r=runCatching{Gson().fromJson(raw,IncomingResponse::class.java)}.getOrNull()?:return@get
            val c=r.call?:return@get
            lastIncomingCallId=maxOf(lastIncomingCallId,c.id); callScreenOpening=true
            runOnUiThread{ startActivity(Intent(this,CallActivity::class.java).putExtra("incoming",true).putExtra("video",c.call_type=="video").putExtra("call_uuid",c.call_uuid)) }
        }
    }
    override fun onRestart(){ super.onRestart(); callScreenOpening=false }
    private fun load() {
        val cut=getSharedPreferences("security",0).getString("cleared_before","")?:""
        val q=if(cut.isBlank()) "messages.php?after_id=$last&limit=100" else "messages.php?after_id=$last&limit=100&after_time="+URLEncoder.encode(cut,"UTF-8")
        Api.get(q,Session.token(this)){ok,raw-> if(ok) runCatching { Gson().fromJson(raw,MessageResponse::class.java) }.getOrNull()?.let { r -> if(r.messages.isNotEmpty()) runOnUiThread { adapter.items.addAll(r.messages); last=r.messages.maxOf{it.id}; adapter.notifyItemRangeInserted(adapter.items.size-r.messages.size,r.messages.size); b.list.scrollToPosition(adapter.items.size-1); markRead() } }}
    }
    private fun send() {
        val t=b.message.text.toString().trim(); if(t.isEmpty()) return; b.message.setText("")
        Api.post("send_message.php",mapOf("type" to "text","text" to t),Session.token(this)){ok,raw-> if(ok) runCatching { Gson().fromJson(raw,SendResponse::class.java) }.getOrNull()?.message?.let { m -> runOnUiThread { adapter.items.add(m); last=maxOf(last,m.id); adapter.notifyItemInserted(adapter.items.size-1); b.list.scrollToPosition(adapter.items.size-1) } } else runOnUiThread { Toast.makeText(this,"Mesaj göndərilmədi",Toast.LENGTH_SHORT).show() }}
    }
    private fun markRead(){ if(getSharedPreferences("settings",0).getBoolean("read",true)) Api.post("read_messages.php", emptyMap<String,String>(),Session.token(this)){_,_->} }
    private fun heartbeat(){ Api.post("heartbeat.php", emptyMap<String,String>(),Session.token(this)){_,_->} }
    private fun deleteDialog(m:Message){ AlertDialog.Builder(this).setItems(arrayOf("Cavab ver","Məndən sil","Hər iki tərəfdən sil")){_,w-> if(w==0){ b.message.setText("↪ ${m.text ?: "Mesaj"}\n"); b.message.setSelection(b.message.text.length); return@setItems }; Api.post("delete_message.php",mapOf("message_id" to m.id,"mode" to if(w==1)"me" else "everyone"),Session.token(this)){ok,_-> if(ok)runOnUiThread{adapter.items.remove(m);adapter.notifyDataSetChanged()} }}.show() }
}
