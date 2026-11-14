package com.example.daftarkarkard

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.*
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

data class Worker(val id: Long, val name: String)
data class Work(val id: Long, val workerId: Long, val date: String, val start: String, val end: String,
                val wage: Long, val overtime: Double, val note: String)
data class Payment(val id: Long, val workerId: Long, val date: String, val amount: Long, val note: String)

class Db(context: Context) : SQLiteOpenHelper(context, "daftar_native.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE workers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE works(id INTEGER PRIMARY KEY AUTOINCREMENT, worker_id INTEGER NOT NULL, date TEXT NOT NULL, start TEXT, end TEXT, wage INTEGER NOT NULL, overtime REAL NOT NULL DEFAULT 0, note TEXT)")
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT, worker_id INTEGER NOT NULL, date TEXT NOT NULL, amount INTEGER NOT NULL, note TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun workers(): List<Worker> {
        val out= mutableListOf<Worker>(); readableDatabase.rawQuery("SELECT id,name FROM workers ORDER BY name",null).use { c -> while(c.moveToNext()) out += Worker(c.getLong(0),c.getString(1)) }; return out
    }
    fun addWorker(name:String) { writableDatabase.execSQL("INSERT INTO workers(name) VALUES(?)", arrayOf(name.trim())) }
    fun deleteWorker(id:Long) { writableDatabase.beginTransaction(); try { writableDatabase.delete("works","worker_id=?", arrayOf(id.toString())); writableDatabase.delete("payments","worker_id=?", arrayOf(id.toString())); writableDatabase.delete("workers","id=?",arrayOf(id.toString())); writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() } }
    fun works(worker:Long):List<Work> { val a= mutableListOf<Work>(); readableDatabase.rawQuery("SELECT id,worker_id,date,start,end,wage,overtime,note FROM works WHERE worker_id=? ORDER BY date DESC,id DESC",arrayOf(worker.toString())).use { c -> while(c.moveToNext()) a += Work(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)?: "",c.getString(4)?: "",c.getLong(5),c.getDouble(6),c.getString(7)?: "") }; return a }
    fun allWorks():List<Work> = workers().flatMap { works(it.id) }
    fun addWorks(ids:List<Long>, date:String, start:String, end:String, wage:Long, ot:Double, note:String) {
        writableDatabase.beginTransaction(); try { ids.forEach { writableDatabase.execSQL("INSERT INTO works(worker_id,date,start,end,wage,overtime,note) VALUES(?,?,?,?,?,?,?)",arrayOf(it,date,start,end,wage,ot,note)) }; writableDatabase.setTransactionSuccessful() } finally { writableDatabase.endTransaction() }
    }
    fun updateWork(w:Work) { writableDatabase.execSQL("UPDATE works SET date=?,start=?,end=?,wage=?,overtime=?,note=? WHERE id=?",arrayOf(w.date,w.start,w.end,w.wage,w.overtime,w.note,w.id)) }
    fun deleteWork(id:Long) { writableDatabase.delete("works","id=?",arrayOf(id.toString())) }
    fun payments(worker:Long):List<Payment> { val a= mutableListOf<Payment>(); readableDatabase.rawQuery("SELECT id,worker_id,date,amount,note FROM payments WHERE worker_id=? ORDER BY date DESC",arrayOf(worker.toString())).use { c -> while(c.moveToNext()) a += Payment(c.getLong(0),c.getLong(1),c.getString(2),c.getLong(3),c.getString(4)?: "") }; return a }
    fun addPayment(worker:Long,date:String,amount:Long,note:String) { writableDatabase.execSQL("INSERT INTO payments(worker_id,date,amount,note) VALUES(?,?,?,?)",arrayOf(worker,date,amount,note)) }
    fun deletePayment(id:Long) { writableDatabase.delete("payments","id=?",arrayOf(id.toString())) }
    fun total(worker:Long):Long = works(worker).sumOf { it.wage + (it.wage*it.overtime/8.0).toLong() }
    fun paid(worker:Long):Long = payments(worker).sumOf { it.amount }
}

class MainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var content: LinearLayout
    private val green=Color.rgb(8,75,61)
    private val teal=Color.rgb(20,126,105)
    private var currentWorker:Worker?=null
    private val backupReq=501
    private val restoreReq=502

    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); db=Db(this); showHome() }

    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun label(t:String,size:Float=16f):TextView = TextView(this).apply { text=t; textSize=size; setTextColor(Color.rgb(35,45,42)); gravity=Gravity.RIGHT; setPadding(dp(8),dp(8),dp(8),dp(8)) }
    private fun button(t:String, action:()->Unit):Button = Button(this).apply { text=t; isAllCaps=false; setOnClickListener { action() } }
    private fun card():LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),dp(10)); background=rounded(Color.WHITE,18); elevation=dp(2).toFloat() }
    private fun rounded(color:Int,r:Int)=android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=dp(r).toFloat(); setStroke(dp(1),Color.rgb(225,232,228)) }
    private fun base(title:String) {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245,248,246)) }
        val header=TextView(this).apply { text=title; textSize=24f; setTextColor(Color.WHITE); gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); background=rounded(green,22) }
        root.addView(header,LinearLayout.LayoutParams(-1,dp(82)))
        val scroll=ScrollView(this); content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(24)) }; scroll.addView(content)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }
    private fun row(vararg views:View) { val r=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }; views.forEach { r.addView(it,LinearLayout.LayoutParams(0,-2,1f)) }; content.addView(r) }
    private fun showHome() {
        base("دفتر کارکرد من")
        val all=db.workers()
        val total=all.sumOf { db.total(it.id) }; val paid=all.sumOf { db.paid(it.id) }
        content.addView(card().apply {
            addView(label("خلاصه حساب کل",20f)); addView(label("جمع کارکرد: ${fmt(total)}")); addView(label("جمع دریافتی: ${fmt(paid)}")); addView(label("مانده: ${fmt(total-paid)}",19f))
        })
        content.addView(button("＋ افزودن شخص") { workerDialog() })
        content.addView(button("ثبت کارکرد برای چند شخص") { batchWorkDialog() })
        content.addView(button("ثبت دریافتی") { paymentWorkerDialog() })
        content.addView(button("پشتیبان‌گیری از اطلاعات") { createBackup() })
        content.addView(button("بازیابی پشتیبان") { restoreBackup() })
        content.addView(label("فهرست اشخاص",20f))
        if(all.isEmpty()) content.addView(label("هنوز شخصی ثبت نشده است."))
        all.forEach { w ->
            val c=card()
            c.addView(label(w.name,19f)); c.addView(label("کارکرد: ${fmt(db.total(w.id))}    دریافتی: ${fmt(db.paid(w.id))}    مانده: ${fmt(db.total(w.id)-db.paid(w.id))}"))
            c.addView(button("ورود به صفحه شخص") { currentWorker=w; showWorker(w) })
            c.addView(button("حذف شخص") { confirm("شخص و تمام سوابق او حذف شود؟") { db.deleteWorker(w.id); showHome() } })
            content.addView(c)
        }
    }
    private fun workerDialog() {
        val input=EditText(this).apply { hint="نام شخص"; textDirection=View.TEXT_DIRECTION_RTL }
        formDialog("افزودن شخص", listOf(input)) {
            val n=input.text.toString().trim()
            if(n.isEmpty()) { toast("نام را وارد کنید"); false } else { db.addWorker(n); showHome(); true }
        }
    }
    private fun showWorker(w:Worker) {
        base("پرونده: ${w.name}")
        content.addView(button("بازگشت") { showHome() })
        content.addView(card().apply { addView(label("جمع کارکرد: ${fmt(db.total(w.id))}")); addView(label("جمع دریافتی: ${fmt(db.paid(w.id))}")); addView(label("مانده: ${fmt(db.total(w.id)-db.paid(w.id))}",20f)) })
        content.addView(button("ثبت کارکرد") { workDialog(listOf(w.id)) })
        content.addView(button("ثبت دریافتی") { paymentDialog(w) })
        content.addView(button("ساخت PDF خلاصه حساب") { exportPdf(w) })
        content.addView(label("ریز کارکردها",20f))
        db.works(w.id).forEach { item ->
            val c=card(); c.addView(label("${item.date}  |  دستمزد ${fmt(item.wage)}  |  اضافه‌کاری ${item.overtime} ساعت"))
            c.addView(label("شروع ${item.start}   پایان ${item.end}   |   مبلغ با اضافه‌کاری: ${fmt(item.wage+(item.wage*item.overtime/8.0).toLong())}"))
            if(item.note.isNotBlank()) c.addView(label(item.note))
            c.addView(button("ویرایش") { editWorkDialog(item,w) })
            c.addView(button("حذف") { confirm("این رکورد حذف شود؟") { db.deleteWork(item.id); showWorker(w) } })
            content.addView(c)
        }
        content.addView(label("دریافتی‌ها",20f))
        db.payments(w.id).forEach { p -> content.addView(card().apply { addView(label("${p.date}  |  ${fmt(p.amount)}")); if(p.note.isNotBlank()) addView(label(p.note)); addView(button("حذف دریافتی") { confirm("این دریافتی حذف شود؟") { db.deletePayment(p.id); showWorker(w) } }) }) }
    }
    private fun batchWorkDialog() {
        val people=db.workers()
        if(people.isEmpty()) { toast("ابتدا شخص اضافه کنید"); return }
        val checks=people.map { w -> CheckBox(this).apply { text=w.name; tag=w.id } }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; checks.forEach { addView(it) } }
        val scroll=ScrollView(this).apply { addView(box) }
        val wrapper=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(scroll,LinearLayout.LayoutParams(-1,dp(180))) }
        val date=EditText(this).apply { hint="تاریخ میلادی YYYY-MM-DD"; setText(today()) }
        val start=EditText(this).apply { hint="ساعت شروع، مثلاً 07:00" }
        val end=EditText(this).apply { hint="ساعت پایان، مثلاً 16:00" }
        val wage=EditText(this).apply { hint="دستمزد یک روز (۸ ساعت)"; inputType=2 }
        val ot=EditText(this).apply { hint="اضافه‌کاری به ساعت"; inputType=8194; setText("0") }
        val note=EditText(this).apply { hint="توضیحات (اختیاری)" }
        listOf(date,start,end,wage,ot,note).forEach { wrapper.addView(it) }
        formDialog("ثبت یکسان برای چند شخص",listOf(wrapper)) {
            val ids=checks.filter { it.isChecked }.map { it.tag as Long }
            val amount=wage.text.toString().toLongOrNull()
            val extra=ot.text.toString().toDoubleOrNull()
            if(ids.isEmpty() || amount==null || extra==null || date.text.isBlank()) { toast("اشخاص، تاریخ، دستمزد و اضافه‌کاری را بررسی کنید"); false }
            else { db.addWorks(ids,date.text.toString(),start.text.toString(),end.text.toString(),amount,extra,note.text.toString()); showHome(); true }
        }
    }
    private fun workDialog(ids:List<Long>) {
        val date=EditText(this).apply { hint="تاریخ میلادی YYYY-MM-DD"; setText(today()) }
        val start=EditText(this).apply { hint="ساعت شروع" }; val end=EditText(this).apply { hint="ساعت پایان" }
        val wage=EditText(this).apply { hint="دستمزد روزانه (۸ ساعت)"; inputType=2 }
        val ot=EditText(this).apply { hint="اضافه‌کاری به ساعت"; inputType=8194; setText("0") }
        val note=EditText(this).apply { hint="توضیحات" }
        formDialog("ثبت کارکرد",listOf(date,start,end,wage,ot,note)) {
            val amount=wage.text.toString().toLongOrNull(); val extra=ot.text.toString().toDoubleOrNull()
            if(amount==null || date.text.isBlank() || extra==null) { toast("تاریخ، دستمزد و اضافه‌کاری را بررسی کنید"); false }
            else { db.addWorks(ids,date.text.toString(),start.text.toString(),end.text.toString(),amount,extra,note.text.toString()); currentWorker?.let { showWorker(it) } ?: showHome(); true }
        }
    }
    private fun editWorkDialog(w:Work, person:Worker) {
        val date=EditText(this).apply { setText(w.date); hint="تاریخ میلادی YYYY-MM-DD" }
        val start=EditText(this).apply { setText(w.start); hint="شروع" }; val end=EditText(this).apply { setText(w.end); hint="پایان" }
        val wage=EditText(this).apply { setText(w.wage.toString()); inputType=2 }
        val ot=EditText(this).apply { setText(w.overtime.toString()); inputType=8194 }
        val note=EditText(this).apply { setText(w.note) }
        formDialog("ویرایش کارکرد",listOf(date,start,end,wage,ot,note)) {
            val amount=wage.text.toString().toLongOrNull(); val extra=ot.text.toString().toDoubleOrNull()
            if(amount==null || extra==null || date.text.isBlank()) { toast("اطلاعات را بررسی کنید"); false }
            else { db.updateWork(w.copy(date=date.text.toString(),start=start.text.toString(),end=end.text.toString(),wage=amount,overtime=extra,note=note.text.toString())); showWorker(person); true }
        }
    }
    private fun paymentWorkerDialog() {
        val people=db.workers(); if(people.isEmpty()){toast("ابتدا شخص اضافه کنید");return}
        val names=people.map{it.name}.toTypedArray()
        AlertDialog.Builder(this).setTitle("انتخاب شخص").setItems(names){_,i->paymentDialog(people[i])}.setNegativeButton("لغو",null).show().window?.setBackgroundDrawable(rounded(Color.WHITE,20))
    }
    private fun paymentDialog(w:Worker) {
        val date=EditText(this).apply { hint="تاریخ میلادی YYYY-MM-DD"; setText(today()) }
        val amount=EditText(this).apply { hint="مبلغ دریافتی"; inputType=2 }
        val note=EditText(this).apply { hint="توضیحات" }
        formDialog("ثبت دریافتی برای ${w.name}",listOf(date,amount,note)) {
            val v=amount.text.toString().toLongOrNull()
            if(v==null || date.text.isBlank()) { toast("تاریخ و مبلغ را وارد کنید"); false } else { db.addPayment(w.id,date.text.toString(),v,note.text.toString()); currentWorker?.let{showWorker(it)} ?: showHome(); true }
        }
    }
    private fun formDialog(title:String, views:List<View>, save:()->Boolean) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(8),dp(18),dp(4)); views.forEach { v -> addView(v,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) }) } }
        val scroll=ScrollView(this).apply { addView(box) }
        val d=AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("ذخیره",null).setNegativeButton("لغو",null).create()
        d.setOnShowListener { d.window?.setBackgroundDrawable(rounded(Color.WHITE,22)); d.getButton(-1).setTextColor(teal); d.getButton(-2).setTextColor(Color.GRAY); d.getButton(-1).setOnClickListener { if(save()) d.dismiss() } }
        d.show()
    }
    private fun confirm(message:String, yes:()->Unit) { val d=AlertDialog.Builder(this).setMessage(message).setPositiveButton("تأیید"){_,_->yes()}.setNegativeButton("لغو",null).create(); d.setOnShowListener { d.window?.setBackgroundDrawable(rounded(Color.WHITE,22)); d.getButton(-1).setTextColor(teal) }; d.show() }
    private fun exportPdf(w:Worker) {
        try {
            val pdf=PdfDocument(); val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=12f; color=Color.BLACK }
            var pageNo=1; var y=50
            var page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create())
            fun line(s:String) { if(y>790){pdf.finishPage(page);pageNo++;page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNo).create());y=50}; page.canvas.drawText(s,35f,y.toFloat(),paint); y+=24 }
            line("Daftar Karkard - ${w.name}"); line("Total work: ${fmt(db.total(w.id))}"); line("Received: ${fmt(db.paid(w.id))}"); line("Balance: ${fmt(db.total(w.id)-db.paid(w.id))}"); line("-----------------------------------------------")
            db.works(w.id).forEach { line("${it.date} | wage ${it.wage} | overtime ${it.overtime} h | ${it.start}-${it.end}") }
            line("Payments"); db.payments(w.id).forEach { line("${it.date} | ${it.amount} | ${it.note}") }
            pdf.finishPage(page)
            val name="Daftar-${w.name}-${System.currentTimeMillis()}.pdf"
            val values=android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.DISPLAY_NAME,name); put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/pdf"); put(android.provider.MediaStore.Downloads.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS) }
            val uri=contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: throw IOException("Cannot create PDF")
            contentResolver.openOutputStream(uri).use { pdf.writeTo(it!!) }; pdf.close(); toast("PDF در پوشه Downloads ذخیره شد")
        } catch(e:Exception){toast("ساخت PDF ناموفق بود: ${e.message}")}
    }
    private fun createBackup(){ val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/octet-stream";putExtra(Intent.EXTRA_TITLE,"daftar-karkard-backup.db")};startActivityForResult(i,backupReq) }
    private fun restoreBackup(){ val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"};startActivityForResult(i,restoreReq) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK)return;val uri=data?.data?:return
        try { if(requestCode==backupReq){ val f=getDatabasePath("daftar_native.db"); contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { input -> input.copyTo(out) } } ?: throw IOException("Cannot open destination"); toast("پشتیبان ذخیره شد") }
            else if(requestCode==restoreReq){ val temp=File(filesDir,"restore.db"); contentResolver.openInputStream(uri)!!.use{input->temp.outputStream().use{input.copyTo(it)}}; db.close(); temp.copyTo(getDatabasePath("daftar_native.db"),true); db=Db(this); showHome(); toast("بازیابی انجام شد") }
        }catch(e:Exception){toast("خطا: ${e.message}")}}
    private fun fmt(v:Long)=String.format(Locale.US,"%,d",v)
    private fun today()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
