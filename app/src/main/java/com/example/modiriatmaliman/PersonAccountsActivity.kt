package com.example.modiriatmaliman

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.NumberFormat
import java.util.Locale

private fun paDigits(v:String)=v.replace(Regex("[۰-۹]")){(it.value[0].code-'۰'.code).toString()}.replace(Regex("[٠-٩]")){(it.value[0].code-'٠'.code).toString()}
private fun paMoney(v:Long)=NumberFormat.getInstance(Locale("fa","IR")).format(v)

class PersonAccountsActivity:Activity(){
 private lateinit var db:SQLiteDatabase
 private lateinit var root:LinearLayout
 private val green=0xFF16865B.toInt()
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val helper=object:SQLiteOpenHelper(this,"finance.db",null,2){
   override fun onCreate(d:SQLiteDatabase){}
   override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){}
  }
  db=helper.writableDatabase
  db.execSQL("CREATE TABLE IF NOT EXISTS people(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,phone TEXT,note TEXT)")
  db.execSQL("CREATE TABLE IF NOT EXISTS person_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,person_id INTEGER NOT NULL,kind TEXT NOT NULL,amount INTEGER NOT NULL,day TEXT NOT NULL,note TEXT)")
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_person_tx ON person_transactions(person_id,day,id)")
  showList()
 }
 private fun page(title:String){
  root=LinearLayout(this).apply{orientation=1;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(18),dp(18),dp(18),dp(12));setBackgroundColor(0xFFF5F7F6.toInt())}
  val sc=ScrollView(this);sc.addView(root);setContentView(sc);txt(title,25f,true,green)
 }
 private fun add(v:View){root.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})}
 private fun txt(s:String,size:Float=15f,bold:Boolean=false,color:Int=0xFF17231E.toInt()){
  add(TextView(this).apply{text=s;textSize=size;setTextColor(color);gravity=Gravity.RIGHT;if(bold)setTypeface(null,1);setPadding(0,dp(5),0,dp(5))})
 }
 private fun btn(s:String,f:()->Unit){add(Button(this).apply{text=s;isAllCaps=false;setTextColor(-1);backgroundTintList=android.content.res.ColorStateList.valueOf(green);setOnClickListener{f()}})}
 private fun inp(h:String,num:Boolean=false)=EditText(this).apply{hint=h;textSize=16f;gravity=Gravity.RIGHT;setSingleLine(true);if(num)inputType=android.text.InputType.TYPE_CLASS_NUMBER;setPadding(dp(10),dp(6),dp(10),dp(6));setBackgroundColor(-1)}
 private fun bal(id:Long):Long{val c=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='received' THEN amount ELSE -amount END),0) FROM person_transactions WHERE person_id=?",arrayOf(id.toString()));c.moveToFirst();val n=c.getLong(0);c.close();return n}
 private fun showList(){
  page("حساب اشخاص");txt("مدیریت طلب و پرداخت هر شخص",13f,false,0xFF65736C.toInt())
  btn("＋ افزودن شخص"){addPerson()}
  val c=db.rawQuery("SELECT id,name FROM people ORDER BY name",null);var count=0
  while(c.moveToNext()){
   count++;val id=c.getLong(0);val name=c.getString(1);val b=bal(id)
   val status=if(b>0)"طلبکار از ما" else if(b<0)"بدهکار به ما" else "تسویه"
   val card=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(10),dp(14),dp(10));setBackgroundColor(-1);setOnClickListener{showPerson(id)}}
   card.addView(TextView(this).apply{text=name;textSize=18f;setTextColor(0xFF17231E.toInt());gravity=Gravity.RIGHT;setTypeface(null,1)})
   card.addView(TextView(this).apply{text=status+" • "+paMoney(kotlin.math.abs(b))+" تومان";textSize=14f;setTextColor(green);gravity=Gravity.RIGHT})
   add(card)
  };c.close()
  if(count==0)txt("هنوز شخصی ثبت نشده است.",14f,false,0xFF65736C.toInt())
  btn("گزارش کلی اشخاص"){summary()};btn("بازگشت"){finish()}
 }
 private fun addPerson(){
  val n=inp("نام شخص");val ph=inp("شماره تماس (اختیاری)",true);val note=inp("توضیحات")
  val box=LinearLayout(this).apply{orientation=1;addView(n);addView(ph);addView(note)}
  AlertDialog.Builder(this).setTitle("افزودن شخص").setView(box).setPositiveButton("ذخیره"){_,_->
   val name=n.text.toString().trim()
   if(name.isEmpty())toast("نام شخص را وارد کنید") else try{
    val v=ContentValues().apply{put("name",name);put("phone",ph.text.toString());put("note",note.text.toString())}
    db.insertOrThrow("people",null,v);showList()
   }catch(e:Exception){toast("این نام قبلاً ثبت شده است")}
  }.setNegativeButton("انصراف",null).show()
 }
 private fun showPerson(id:Long){
  val p=db.rawQuery("SELECT name,phone,note FROM people WHERE id=?",arrayOf(id.toString()))
  if(!p.moveToFirst()){p.close();showList();return};val name=p.getString(0);val phone=p.getString(1).orEmpty();val note=p.getString(2).orEmpty();p.close()
  val b=bal(id);page(name)
  txt("مانده: "+paMoney(kotlin.math.abs(b))+" تومان • "+(if(b>0)"طلبکار از ما" else if(b<0)"بدهکار به ما" else "تسویه"),18f,true,green)
  if(phone.isNotBlank())txt("تماس: "+phone,13f);if(note.isNotBlank())txt(note,13f)
  btn("＋ ثبت دریافتی از شخص"){trans(id,"received")};btn("− ثبت پرداختی به شخص"){trans(id,"paid")}
  txt("گردش حساب",18f,true)
  val c=db.rawQuery("SELECT kind,amount,day,note FROM person_transactions WHERE person_id=? ORDER BY day DESC,id DESC",arrayOf(id.toString()))
  if(!c.moveToFirst())txt("تراکنشی ثبت نشده است.",13f,false,0xFF65736C.toInt()) else do{
   val k=c.getString(0);val a=c.getLong(1);val d=c.getString(2);val memo=c.getString(3).orEmpty()
   txt((if(k=="received")"دریافتی" else "پرداختی")+" • "+paMoney(a)+" تومان • "+d+(if(memo.isNotBlank())"\n"+memo else ""),14f)
  }while(c.moveToNext());c.close()
  btn("گزارش این شخص"){personReport(id,name)};btn("بازگشت به فهرست"){showList()}
 }
 private fun trans(id:Long,kind:String){
  val a=inp("مبلغ به تومان",true);val d=inp("تاریخ شمسی YYYY-MM-DD").apply{setText(today())};val note=inp("توضیحات")
  val box=LinearLayout(this).apply{orientation=1;addView(a);addView(d);addView(note)}
  AlertDialog.Builder(this).setTitle(if(kind=="received")"ثبت دریافتی" else "ثبت پرداختی").setView(box).setPositiveButton("ذخیره"){_,_->
   val amount=paDigits(a.text.toString()).replace(",","").replace("٬","").trim().toLongOrNull();val date=paDigits(d.text.toString()).trim()
   if(amount==null||amount<=0||!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date))toast("مبلغ و تاریخ شمسی معتبر وارد کنید") else{
    val v=ContentValues().apply{put("person_id",id);put("kind",kind);put("amount",amount);put("day",date);put("note",note.text.toString())}
    db.insert("person_transactions",null,v);showPerson(id)
   }
  }.setNegativeButton("انصراف",null).show()
 }
 private fun summary(){
  page("گزارش کلی اشخاص");var credit=0L;var debit=0L
  val c=db.rawQuery("SELECT id,name FROM people ORDER BY name",null)
  while(c.moveToNext()){val b=bal(c.getLong(0));if(b>0)credit+=b else debit-=b;txt(c.getString(1)+" • "+(if(b>0)"طلبکار از ما" else if(b<0)"بدهکار به ما" else "تسویه")+" • "+paMoney(kotlin.math.abs(b))+" تومان")}
  c.close();txt("جمع طلب اشخاص از ما: "+paMoney(credit)+" تومان",17f,true,green);txt("جمع بدهی اشخاص به ما: "+paMoney(debit)+" تومان",17f,true);txt("خالص حساب اشخاص: "+paMoney(credit-debit)+" تومان",18f,true)
  btn("بازگشت"){showList()}
 }
 private fun personReport(id:Long,name:String){
  page("گزارش "+name);var received=0L;var paid=0L
  val c=db.rawQuery("SELECT kind,SUM(amount) FROM person_transactions WHERE person_id=? GROUP BY kind",arrayOf(id.toString()))
  while(c.moveToNext()){if(c.getString(0)=="received")received=c.getLong(1) else paid=c.getLong(1)};c.close()
  val b=received-paid;txt("مجموع دریافتی: "+paMoney(received)+" تومان",16f,true);txt("مجموع پرداختی: "+paMoney(paid)+" تومان",16f,true)
  txt("مانده: "+paMoney(kotlin.math.abs(b))+" تومان • "+(if(b>0)"طلبکار از ما" else if(b<0)"بدهکار به ما" else "تسویه"),19f,true,green)
  btn("بازگشت به حساب شخص"){showPerson(id)}
 }
 private fun today():String{
  val c=java.util.Calendar.getInstance();val gy=c.get(1);val gm=c.get(2)+1;val gd=c.get(5);val gdays=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334);val gy2=if(gm>2)gy+1 else gy
  var days=355666+365*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400+gd+gdays[gm-1];var jy=-1595+33*(days/12053);days%=12053;jy+=4*(days/1461);days%=1461
  if(days>365){jy+=(days-1)/365;days=(days-1)%365};val jm=if(days<186)1+days/31 else 7+(days-186)/30;val jd=1+if(days<186)days%31 else (days-186)%30
  return "%04d-%02d-%02d".format(Locale.US,jy,jm,jd)
 }
 private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
}
