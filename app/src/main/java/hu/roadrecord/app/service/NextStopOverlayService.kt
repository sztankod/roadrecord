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
  @Volatile var visible=false
 }
 private var root:View?=null
 private var nameView:TextView?=null
 private var addressView:TextView?=null
 private val windowManager by lazy{getSystemService(WINDOW_SERVICE) as WindowManager}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==ACTION_HIDE){hide();stopSelf();return START_NOT_STICKY}
  if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
  if(root==null)show()
  nameView?.text=intent?.getStringExtra(EXTRA_NAME)?.ifBlank{"Nincs további megálló"}?:"Nincs további megálló"
  addressView?.text=intent?.getStringExtra(EXTRA_ADDRESS).orEmpty()
  return START_NOT_STICKY
 }
 private fun show(){
  val density=resources.displayMetrics.density
  fun text(size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{setTextColor(color);textSize=size;if(bold)setTypeface(typeface,Typeface.BOLD);maxLines=1}
  nameView=text(15f,Color.WHITE,true);addressView=text(11f,0xFFCFDCF2.toInt())
  val labels=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;addView(nameView,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));addView(addressView,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))}
  val close=ImageButton(this).apply{setImageResource(android.R.drawable.ic_menu_close_clear_cancel);setColorFilter(Color.WHITE);setBackgroundColor(Color.TRANSPARENT);contentDescription="Lebegő sáv bezárása";setOnClickListener{hide();stopSelf()}}
  val container=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding((14*density).toInt(),(6*density).toInt(),(6*density).toInt(),(6*density).toInt());background=GradientDrawable().apply{setColor(0xF20D2B60.toInt());cornerRadius=12*density};addView(labels,LinearLayout.LayoutParams(0,(54*density).toInt(),1f));addView(close,LinearLayout.LayoutParams((46*density).toInt(),(46*density).toInt()));setOnClickListener{startActivity(Intent(this@NextStopOverlayService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}}
  val params=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP;horizontalMargin=.02f;y=(8*density).toInt()}
  windowManager.addView(container,params);root=container;visible=true
 }
 private fun hide(){root?.let{runCatching{windowManager.removeView(it)}};root=null;visible=false}
 override fun onDestroy(){hide();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
