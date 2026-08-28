package hu.roadrecord.app.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import hu.roadrecord.app.MainActivity

class NextStopOverlayService:Service(){
 companion object{
  const val ACTION_SHOW="hu.roadrecord.overlay.SHOW"
  const val ACTION_UPDATE="hu.roadrecord.overlay.UPDATE"
  const val ACTION_HIDE="hu.roadrecord.overlay.HIDE"
  const val EXTRA_NAME="name"
  const val EXTRA_ADDRESS="address"
  const val EXTRA_CURRENT_NAME="current_name"
  const val EXTRA_CURRENT_ADDRESS="current_address"
  @Volatile var visible=false
 }
 private var root:View?=null
 private var currentNameView:TextView?=null
 private var currentAddressView:TextView?=null
 private var nameView:TextView?=null
 private var addressView:TextView?=null
 private val windowManager by lazy{getSystemService(WINDOW_SERVICE) as WindowManager}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==ACTION_HIDE){hide();stopSelf();return START_NOT_STICKY}
  if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
  if(root==null)show()
  currentNameView?.text=intent?.getStringExtra(EXTRA_CURRENT_NAME)?.ifBlank{"–"}?:"–"
  currentAddressView?.text=intent?.getStringExtra(EXTRA_CURRENT_ADDRESS).orEmpty()
  nameView?.text=intent?.getStringExtra(EXTRA_NAME)?.ifBlank{"Nincs további megálló"}?:"Nincs további megálló"
  addressView?.text=intent?.getStringExtra(EXTRA_ADDRESS).orEmpty()
  return START_NOT_STICKY
 }
 private fun show(){
  val density=resources.displayMetrics.density
  fun text(size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{setTextColor(color);textSize=size;if(bold)setTypeface(typeface,Typeface.BOLD);maxLines=1}
  currentNameView=text(14f,Color.WHITE,true);currentAddressView=text(10f,0xFFCFDCF2.toInt());nameView=text(14f,Color.WHITE,true);addressView=text(10f,0xFFCFDCF2.toInt())
  fun stopColumn(title:String,name:TextView,address:TextView)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;addView(text(9f,0xFF8FB7F5.toInt(),true).apply{this.text=title},LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));addView(name,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));addView(address,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))}
  val current=stopColumn("JELENLEGI",currentNameView!!,currentAddressView!!);val next=stopColumn("KÖVETKEZŐ",nameView!!,addressView!!)
  val close=ImageButton(this).apply{setImageResource(android.R.drawable.ic_menu_close_clear_cancel);setColorFilter(Color.WHITE);background=GradientDrawable().apply{setColor(0x33FFFFFF);shape=GradientDrawable.OVAL};contentDescription="Lebegő sáv bezárása";setOnClickListener{hide();stopSelf()}}
  val container=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding((12*density).toInt(),(6*density).toInt(),(6*density).toInt(),(6*density).toInt());background=GradientDrawable().apply{setColor(0xF20D2B60.toInt());cornerRadius=12*density};addView(current,LinearLayout.LayoutParams(0,(58*density).toInt(),1f));addView(View(this@NextStopOverlayService).apply{setBackgroundColor(0x55FFFFFF)},LinearLayout.LayoutParams((1*density).toInt(),(42*density).toInt()).apply{setMargins((8*density).toInt(),0,(8*density).toInt(),0)});addView(next,LinearLayout.LayoutParams(0,(58*density).toInt(),1f));addView(close,LinearLayout.LayoutParams((42*density).toInt(),(42*density).toInt()).apply{marginStart=(5*density).toInt()});setOnClickListener{startActivity(Intent(this@NextStopOverlayService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}}
  val params=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP;horizontalMargin=.02f;y=(8*density).toInt()}
  windowManager.addView(container,params);root=container;visible=true
 }
 private fun hide(){root?.let{runCatching{windowManager.removeView(it)}};root=null;visible=false}
 override fun onDestroy(){hide();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
