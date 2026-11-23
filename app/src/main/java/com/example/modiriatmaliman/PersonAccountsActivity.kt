package com.example.modiriatmaliman

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.NumberFormat
import java.util.Locale

private fun paDigits(v:String)=v.replace(Regex("[۰-۹]")){(it.value[0].code-'۰'.code).toString()}.replace(Regex("[٠-٩]")){(it.value[0].code-'٠'.code).toString()}
private fun paMoney(v:Long)=NumberFormat.getInstance(Locale("fa","IR")).format(v)

class PersonAccountsActivity:Activity(){
 private lateinit var db:SQLiteDatabase
 private lateinit var root:LinearLayout
 private val green=0xFF145A43.toInt()
 private val ink=0xFF202A25.toInt()
 private val muted=0xFF778078.toInt()
 private val bg=0xFFF4F2EC.toInt()
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=bg;window.navigationBarColor=bg
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
  // Use the exact same database helper as the main finance module.
  // This guarantees both modules read/write the same finance.db file.
  db=DB(this).writableDatabase
  db.execSQL("CREATE TABLE IF NOT EXISTS people(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,phone TEXT,note TEXT)")
  db.execSQL("CREATE TABLE IF NOT EXISTS person_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,person_id INTEGER NOT NULL,kind TEXT NOT NULL,amount INTEGER NOT NULL,day TEXT NOT NULL,note TEXT)")
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_person_tx ON person_transactions(person_id,day,id)")
  showList()
 }
 private fun page(title:String,subtitle:String=""){
  root=LinearLayout(this).apply{orientation=1;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(18),dp(16),dp(18),dp(18));setBackgroundColor(bg)}
  val sc=ScrollView(this);sc.isFillViewport=true;sc.addView(root);setContentView(sc)
  txt(title,25f,true,green)
  if(subtitle.isNotBlank())txt(subtitle,13f,false,muted)
 }
 private fun add(v:View){root.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})}
 private fun txt(s:String,size:Float=15f,bold:Boolean=false,color:Int=ink){
  add(TextView(this).apply{text=s;textSize=size;setTextColor(color);gravity=Gravity.RIGHT;if(bold)setTypeface(null,1);setPadding(0,dp(5),0,dp(5))})
 }
 private fun btn(s:String,secondary:Boolean=false,f:()->Unit){
  add(Button(this).apply{text=s;isAllCaps=false;isEnabled=true;isClickable=true;minHeight=dp(48);setTextColor(if(secondary)green else -1);backgroundTintList=android.content.res.ColorStateList.valueOf(if(secondary)0xFFE2EEE8.toInt() else green);setOnClickListener{f()}})
 }
 private fun inp(h:String,num:Boolean=false)=EditText(this).apply{hint=h;textSize=16f;gravity=Gravity.RIGHT;setSingleLine(true);if(num)inputType=android.text.InputType.TYPE_CLASS_NUMBER;setPadding(dp(12),dp(9),dp(12),dp(9));setTextColor(ink);setHintTextColor(muted);background=android.graphics.drawable.GradientDrawable().apply{setColor(-1);cornerRadius=dp(10).toFloat();setStroke(dp(1),0xFFDCE5DF.toInt())}}
 private fun card(title:String,detail:String,action:()->Unit){
  val box=LinearLayout(this).apply{orientation=1;setPadding(dp(15),dp(12),dp(15),dp(12));background=android.graphics.drawable.GradientDrawable().apply{setColor(-1);cornerRadius=dp(14).toFloat();setStroke(dp(1),0xFFE2E9E4.toInt())};isClickable=true;isFocusable=true;setOnClickListener{action()}}
  box.addView(TextView(this).apply{text=title;textSize=17f;setTextColor(ink);gravity=Gravity.RIGHT;setTypeface(null,1)})
  box.addView(TextView(this).apply{text=detail;textSize=13f;setTextColor(green);gravity=Gravity.RIGHT})
  add(box)
 }
 private fun bal(id:Long):Long{
  val c=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='received' THEN amount ELSE -amount END),0) FROM person_transactions WHERE person_id=?",arrayOf(id.toString()))
  c.moveToFirst();val n=c.getLong(0);c.close();return n
 }
 private fun status(b:Long)=when{b>0L->"طلبکار از ما";b<0L->"بدهکار به ما";else->"تسویه"}
 private fun showList(){
  page("حساب اشخاص","دفتر معین اشخاص • ثبت دریافت، پرداخت و مانده حساب")
  val c=db.rawQuery("SELECT COUNT(*) FROM people",null);c.moveToFirst();val count=c.getInt(0);c.close()
  var totalPositive=0L;var totalNegative=0L
  val totals=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN x.balance>0 THEN x.balance ELSE 0 END),0),COALESCE(SUM(CASE WHEN x.balance<0 THEN -x.balance ELSE 0 END),0) FROM (SELECT p.id,COALESCE(SUM(CASE WHEN t.kind='received' THEN t.amount ELSE -t.amount END),0) balance FROM people p LEFT JOIN person_transactions t ON t.person_id=p.id GROUP BY p.id) x",null)
  if(totals.moveToFirst()){totalPositive=totals.getLong(0);totalNegative=totals.getLong(1)};totals.close()
  card("اشخاص ثبت‌شده",count.toString()+" شخص • طلب از اشخاص: "+paMoney(totalPositive)+" تومان • بدهی به اشخاص: "+paMoney(totalNegative)+" تومان"){}
  btn("＋ افزودن شخص جدید"){addPerson()}
  btn("گزارش جامع اشخاص"){summary()}
  txt("فهرست اشخاص",18f,true)
  val q=inp("جست‌وجوی نام شخص")
  add(q)
  val list=LinearLayout(this).apply{orientation=1}
  add(list)
  fun render(filter:String){
   list.removeAllViews()
   val cur=db.rawQuery("SELECT id,name,phone FROM people WHERE name LIKE ? ORDER BY name",arrayOf("%$filter%"))
   if(!cur.moveToFirst())list.addView(TextView(this).apply{text=if(filter.isBlank())"هنوز شخصی ثبت نشده است." else "موردی پیدا نشد.";textSize=14f;setTextColor(muted);gravity=Gravity.RIGHT})
   else do{val id=cur.getLong(0);val name=cur.getString(1);val phone=cur.getString(2).orEmpty();val b=bal(id)
    val box=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(12),dp(14),dp(12));background=android.graphics.drawable.GradientDrawable().apply{setColor(-1);cornerRadius=dp(13).toFloat();setStroke(dp(1),0xFFE2E9E4.toInt())}}
    box.addView(TextView(this).apply{text=name;textSize=18f;setTextColor(ink);gravity=Gravity.RIGHT;setTypeface(null,1);setOnClickListener{showPerson(id)}})
    box.addView(TextView(this).apply{text=status(b)+" • "+paMoney(kotlin.math.abs(b))+" تومان"+if(phone.isNotBlank())" • "+phone else "";textSize=13f;setTextColor(if(b==0L)muted else green);gravity=Gravity.RIGHT;setOnClickListener{showPerson(id)}})
    val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
    val receive=Button(this).apply{text="＋ دریافت";isAllCaps=false;textSize=12f;minHeight=dp(40);setTextColor(-1);backgroundTintList=android.content.res.ColorStateList.valueOf(green);setOnClickListener{transactionPage(id,name,"received")}}
    val pay=Button(this).apply{text="− پرداخت";isAllCaps=false;textSize=12f;minHeight=dp(40);setTextColor(green);backgroundTintList=android.content.res.ColorStateList.valueOf(0xFFE2EEE8.toInt());setOnClickListener{transactionPage(id,name,"paid")}}
    actions.addView(receive,LinearLayout.LayoutParams(0,dp(44),1f).apply{marginEnd=dp(5)})
    actions.addView(pay,LinearLayout.LayoutParams(0,dp(44),1f).apply{marginStart=dp(5)})
    box.addView(actions)
    box.setOnClickListener{showPerson(id)}
    list.addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
   }while(cur.moveToNext())
   cur.close()
  }
  render("")
  q.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){render(s?.toString().orEmpty())};override fun afterTextChanged(s:android.text.Editable?){}})
  btn("بازگشت به صفحه اصلی", secondary=true) { finish() }
 }
 private fun addPerson(){
  val n=inp("نام و نام خانوادگی");val ph=inp("شماره تماس (اختیاری)",true);val note=inp("توضیحات")
  val box=LinearLayout(this).apply{orientation=1;addView(n);addView(ph);addView(note)}
  AlertDialog.Builder(this).setTitle("افزودن شخص").setView(box).setPositiveButton("ذخیره"){_,_->
   val name=n.text.toString().trim()
   if(name.isEmpty())toast("نام شخص را وارد کنید") else try{
    val v=ContentValues().apply{put("name",name);put("phone",ph.text.toString());put("note",note.text.toString())}
    db.insertOrThrow("people",null,v);showList()
   }catch(e:Exception){toast("این نام قبلاً ثبت شده یا اطلاعات نامعتبر است")}
  }.setNegativeButton("انصراف",null).show()
 }
 private fun showPerson(id:Long){
  val p=db.rawQuery("SELECT name,phone,note FROM people WHERE id=?",arrayOf(id.toString()))
  if(!p.moveToFirst()){p.close();showList();return}
  val name=p.getString(0);val phone=p.getString(1).orEmpty();val note=p.getString(2).orEmpty();p.close()
  val b=bal(id)
  page(name,"کارت حساب شخص • گردش مالی و مانده لحظه‌ای")
  card("مانده حساب",paMoney(kotlin.math.abs(b))+" تومان • "+status(b)){}
  if(phone.isNotBlank())txt("شماره تماس: "+phone,13f,false,muted)
  if(note.isNotBlank())txt("توضیحات: "+note,13f,false,muted)
  txt("ثبت عملیات",18f,true)
  btn("＋ ثبت دریافتی از شخص") { transactionPage(id,name,"received") }
  btn("− ثبت پرداختی به شخص", secondary=true) { transactionPage(id,name,"paid") }
  btn("گزارش کامل این شخص"){personReport(id,name)}
  btn("ویرایش مشخصات شخص"){editPerson(id,name,phone,note)}
  txt("آخرین گردش حساب",18f,true)
  val c=db.rawQuery("SELECT id,kind,amount,day,note FROM person_transactions WHERE person_id=? ORDER BY day DESC,id DESC LIMIT 30",arrayOf(id.toString()))
  if(!c.moveToFirst())txt("هنوز دریافتی یا پرداختی ثبت نشده است.",13f,false,muted)
  else do{val kind=c.getString(1);val amount=c.getLong(2);val day=c.getString(3);val memo=c.getString(4).orEmpty()
   card((if(kind=="received")"دریافتی از شخص" else "پرداختی به شخص")+" • "+paMoney(amount)+" تومان",day+(if(memo.isNotBlank())" • "+memo else "")){}
  }while(c.moveToNext())
  c.close()
  btn("بازگشت به فهرست اشخاص", secondary=true) { showList() }
 }
 private fun transactionPage(id:Long,name:String,kind:String){
  val isReceived=kind=="received"
  page(if(isReceived)"ثبت دریافتی" else "ثبت پرداختی","حساب: "+name)
  card(if(isReceived)"دریافتی از شخص" else "پرداختی به شخص",if(isReceived)"این مبلغ به مانده بستانکاری شخص اضافه می‌شود." else "این مبلغ از مانده بستانکاری شخص کسر می‌شود."){}
  val amount=inp("مبلغ به تومان",true).apply{enableThousandsGrouping()}
  val date=inp("تاریخ شمسی (مثلاً ۱۴۰۵-۰۹-۰۲)").apply{setText(today())}
  val note=inp("شرح عملیات / توضیحات")
  txt("مبلغ",13f,false,muted);add(amount)
  txt("تاریخ",13f,false,muted);add(date)
  txt("شرح",13f,false,muted);add(note)
  btn("ثبت نهایی عملیات"){
   val value=paDigits(amount.text.toString()).replace(Regex("[^0-9]"),"").trim().toLongOrNull()
   val day=paDigits(date.text.toString()).trim().replace('/','-')
   if(value==null||value<=0||!Regex("^\\d{4}-\\d{1,2}-\\d{1,2}$").matches(day))toast("مبلغ مثبت و تاریخ شمسی با قالب سال-ماه-روز وارد کنید")
   else{
    val parts=day.split("-");val normalized="%04d-%02d-%02d".format(Locale.US,parts[0].toInt(),parts[1].toInt(),parts[2].toInt())
    val v=ContentValues().apply{put("person_id",id);put("kind",kind);put("amount",value);put("day",normalized);put("note",note.text.toString().trim())}
    val inserted=db.insert("person_transactions",null,v)
    if(inserted==-1L)toast("ثبت عملیات انجام نشد؛ دوباره تلاش کنید") else{
     val newBalance=bal(id)
     toast("عملیات با موفقیت ثبت شد")
     showPerson(id)
     askToSendSmsReport(id,name,kind,value,normalized,note.text.toString().trim(),newBalance)
    }
   }
  }
  btn("انصراف و بازگشت", secondary=true) { showPerson(id) }
 }
 private fun askToSendSmsReport(id:Long,name:String,kind:String,amount:Long,day:String,note:String,balance:Long){
  val c=db.rawQuery("SELECT phone FROM people WHERE id=?",arrayOf(id.toString()))
  val phone=if(c.moveToFirst())c.getString(0).orEmpty().trim() else ""
  c.close()
  if(phone.isBlank()){
   AlertDialog.Builder(this)
    .setTitle("شماره تماس ثبت نشده")
    .setMessage("تراکنش ذخیره شد، اما برای ارسال پیامک باید شماره موبایل این شخص را در مشخصات او ثبت کنید.")
    .setPositiveButton("ویرایش مشخصات"){_,_->editPersonForSms(id)}
    .setNegativeButton("فعلاً نه",null).show()
   return
  }
  val transactionLabel=if(kind=="received")"دریافت از شما" else "پرداخت به شما"
  val message=buildString{
   append("گزارش حساب ")
   append(name)
   append("\nآخرین تراکنش: ")
   append(transactionLabel)
   append("\nمبلغ: ")
   append(paMoney(amount))
   append(" تومان\nتاریخ: ")
   append(day)
   if(note.isNotBlank())append("\nشرح: ").append(note)
   append("\nمانده حساب: ")
   append(paMoney(kotlin.math.abs(balance)))
   append(" تومان (")
   append(status(balance))
   append(")")
  }
  AlertDialog.Builder(this)
   .setTitle("ارسال گزارش پیامکی؟")
   .setMessage("تراکنش ثبت شد. گزارش آخرین تراکنش و مانده حساب برای "+name+" آماده ارسال است.")
   .setPositiveButton("باز کردن پیامک"){_,_->
    try{
     val sms=Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(phone)))
     sms.putExtra("sms_body",message)
     startActivity(sms)
    }catch(e:Exception){toast("برنامه پیامک در دسترس نیست")}
   }
   .setNegativeButton("بعداً",null)
   .show()
 }
 private fun editPersonForSms(id:Long){
  val p=db.rawQuery("SELECT name,phone,note FROM people WHERE id=?",arrayOf(id.toString()))
  if(!p.moveToFirst()){p.close();return}
  val name=p.getString(0);val phone=p.getString(1).orEmpty();val note=p.getString(2).orEmpty();p.close()
  editPerson(id,name,phone,note)
 }
 private fun editPerson(id:Long,name:String,phone:String,note:String){
  val n=inp("نام شخص").apply{setText(name)};val ph=inp("شماره تماس").apply{setText(phone)};val memo=inp("توضیحات").apply{setText(note)}
  val box=LinearLayout(this).apply{orientation=1;addView(n);addView(ph);addView(memo)}
  AlertDialog.Builder(this).setTitle("ویرایش مشخصات").setView(box).setPositiveButton("ذخیره"){_,_->
   val v=ContentValues().apply{put("name",n.text.toString().trim());put("phone",ph.text.toString());put("note",memo.text.toString())}
   if(v.getAsString("name").isNullOrBlank())toast("نام نمی‌تواند خالی باشد") else try{db.update("people",v,"id=?",arrayOf(id.toString()));showPerson(id)}catch(e:Exception){toast("نام تکراری است")}
  }.setNegativeButton("انصراف",null).show()
 }
 private fun summary(){
  page("گزارش جامع اشخاص","خلاصه مانده‌ها و گردش کلی حساب‌ها")
  var received=0L;var paid=0L
  val totals=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='received' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN kind='paid' THEN amount ELSE 0 END),0) FROM person_transactions",null)
  if(totals.moveToFirst()){received=totals.getLong(0);paid=totals.getLong(1)};totals.close()
  val people=db.rawQuery("SELECT id,name FROM people ORDER BY name",null)
  var credit=0L;var debt=0L;var settled=0
  while(people.moveToNext()){val id=people.getLong(0);val name=people.getString(1);val b=bal(id);if(b>0)credit+=b else if(b<0)debt-=b else settled++;card(name,status(b)+" • "+paMoney(kotlin.math.abs(b))+" تومان"){showPerson(id)}}
  people.close()
  card("جمع دریافتی‌ها",paMoney(received)+" تومان"){}
  card("جمع پرداختی‌ها",paMoney(paid)+" تومان"){}
  card("مانده خالص",paMoney(received-paid)+" تومان"){}
  card("طلب اشخاص از ما",paMoney(credit)+" تومان"){}
  card("بدهی اشخاص به ما",paMoney(debt)+" تومان"){}
  txt("تعداد حساب‌های تسویه‌شده: "+settled,14f,false,muted)
  btn("بازگشت به فهرست", secondary=true) { showList() }
 }
 private fun personReport(id:Long,name:String){
  page("گزارش حساب • "+name,"ریزگردش، جمع عملیات و مانده نهایی")
  var received=0L;var paid=0L
  val sums=db.rawQuery("SELECT kind,COALESCE(SUM(amount),0),COUNT(*) FROM person_transactions WHERE person_id=? GROUP BY kind",arrayOf(id.toString()))
  var count=0
  while(sums.moveToNext()){if(sums.getString(0)=="received")received=sums.getLong(1) else paid=sums.getLong(1);count+=sums.getInt(2)}
  sums.close();val b=received-paid
  card("مجموع دریافتی",paMoney(received)+" تومان"){}
  card("مجموع پرداختی",paMoney(paid)+" تومان"){}
  card("مانده نهایی",paMoney(kotlin.math.abs(b))+" تومان • "+status(b)){}
  txt("تعداد عملیات: "+count,14f,false,muted);txt("ریزگردش حساب",18f,true)
  val c=db.rawQuery("SELECT kind,amount,day,note FROM person_transactions WHERE person_id=? ORDER BY day DESC,id DESC",arrayOf(id.toString()))
  if(!c.moveToFirst())txt("تراکنشی برای نمایش وجود ندارد.",13f,false,muted)
  else do{val k=c.getString(0);val a=c.getLong(1);val d=c.getString(2);val memo=c.getString(3).orEmpty();card((if(k=="received")"دریافتی" else "پرداختی")+" • "+paMoney(a)+" تومان",d+(if(memo.isNotBlank())" • "+memo else "")){} }while(c.moveToNext())
  c.close()
  btn("بازگشت به حساب شخص", secondary=true) { showPerson(id) }
 }
 private fun today():String{
  val c=java.util.Calendar.getInstance();val gy=c.get(1);val gm=c.get(2)+1;val gd=c.get(5);val gdays=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334);val gy2=if(gm>2)gy+1 else gy
  var days=355666+365*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400+gd+gdays[gm-1];var jy=-1595+33*(days/12053);days%=12053;jy+=4*(days/1461);days%=1461
  if(days>365){jy+=(days-1)/365;days=(days-1)%365};val jm=if(days<186)1+days/31 else 7+(days-186)/30;val jd=1+if(days<186)days%31 else (days-186)%30
  return "%04d-%02d-%02d".format(Locale.US,jy,jm,jd)
 }
 private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
}
