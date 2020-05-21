package nl.teamwildenberg.SoloMetrics

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import com.google.android.material.snackbar.Snackbar
import android.view.Menu
import android.view.MenuItem
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.android.schedulers.AndroidSchedulers

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import nl.teamwildenberg.SoloMetrics.Ble.BlueDevice
import nl.teamwildenberg.SoloMetrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.SoloMetrics.Service.ScreenDuinoService
import kotlin.coroutines.CoroutineContext


class MainActivity : ActivityBase(),CoroutineScope {
    private lateinit var mJob: Job
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.Main
    private var screenBinding: ScreenDuinoService.MyLocalBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val thisActivity = this
        mJob = Job()

        UltrasonicButton.setOnClickListener { view ->
            launch {
                if (screenBinding?.ultraSonicDevice == null) {
                    discoverDevice(DeviceTypeEnum.Ultrasonic, thisActivity)
                }
                else{
                    callScreenDuinoService(screenBinding!!.ultraSonicDevice, thisActivity)
                }
            }
        }
        ScreenDuinoButton.setOnClickListener { view ->
            launch {
                if (screenBinding?.screenDuinoDevice == null) {
                    discoverDevice(DeviceTypeEnum.SoloScreenDuino, thisActivity)
                }
                else{
                    callScreenDuinoService(screenBinding!!.screenDuinoDevice, thisActivity)
                }
            }
        }


    }

    override fun onResume() {
        super.onResume()

        val bindServiceIntent = Intent(this, ScreenDuinoService::class.java)
        this.bindService(bindServiceIntent, myConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onPause() {
        super.onPause()
        this.unbindService(myConnection)
    }

    private suspend fun discoverDevice(deviceType: DeviceTypeEnum, thisActivity: Activity) {
        var permissionResult = this.requestPermissionss(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_ADMIN, activityRequestCode =  1)

        if (permissionResult) {
            startActivityForResult(intent, 2)
            val changePageIntent = Intent(thisActivity, DeviceListActivity::class.java)
            changePageIntent.putExtra("deviceType", deviceType)
            var activityResult: ActivityResult?

            launch {

                activityResult = launchIntent(changePageIntent).await()
                if (activityResult?.resultCode == RESULT_OK) {
                    val theDevice =
                        activityResult?.data?.getParcelableExtra<BlueDevice>("deviceId");


                    var fab = thisActivity.findViewById<FloatingActionButton>(R.id.UltrasonicButton)
                    Snackbar.make(fab, "Connecting to '${theDevice?.name}'", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()

                    callScreenDuinoService(theDevice, thisActivity)
                }
            }
        }
    }

    private suspend fun callScreenDuinoService(theDevice: BlueDevice?, thisActivity: Activity){
        var permissionResult = this.requestPermissionss(Manifest.permission.FOREGROUND_SERVICE, activityRequestCode =  1)

        if (permissionResult) {
            val startServiceIntent = Intent(thisActivity, ScreenDuinoService::class.java)
            startServiceIntent.putExtra("blueDevice", theDevice)
            ContextCompat.startForegroundService(thisActivity, startServiceIntent)
        }
        else{
            var fab = thisActivity.findViewById<FloatingActionButton>(R.id.UltrasonicButton)
            Snackbar.make(fab, "Cancelled connection '${theDevice?.name}, allow to run as service'", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val myConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            var txtScreen = findViewById<TextView>(R.id.ScreenTextView)
            var txtWindSpeed = findViewById<TextView>(R.id.WindSpeedTextView)
            var txtBoatDirection = findViewById<TextView>(R.id.BoatDirectionTextView)
            var txtWindDirectoin = findViewById<TextView>(R.id.WindDirectionTextView)
            var txtBattery = findViewById<TextView>(R.id.UltrasonicBatteryTextView)
            var previousBoatDirection : Int = 0;
            var previousWindDirection : Int =0;


            screenBinding = service as ScreenDuinoService.MyLocalBinder
            var disp = screenBinding?.screenStatusChannel
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe({ theStatus ->
                    if (theStatus.screenConnected ) {
                        txtScreen.setText("Screen connected: ${theStatus.windMeasurement?.WindSpeed}")
                    }
                    else{
                        txtScreen.setText("---")
                    }
                    if (theStatus.ultraSonicConnected ) {
                        var msmnt = theStatus.windMeasurement
                        if ( msmnt != null) {
                            txtWindSpeed.setText("${msmnt.WindSpeed}")
                            txtBoatDirection.setText("${msmnt.BoatDirection}")
                            txtWindDirectoin.setText("${msmnt.WindDirection}")
                            txtBattery.setText("${msmnt.BatteryPercentage}")

                            var boatAngle = -angleOffset(msmnt.BoatDirection, previousBoatDirection)
                            var windAngle = -angleOffset(msmnt.WindDirection, previousWindDirection)

                       //     boatDirectionImage.animate().rotation(previousBoatDirection.toFloat()).start();
                       //     windDirectionImage.animate().rotation(previousWindDirection.toFloat()).start();
                            boatDirectionImage.animate().setDuration(600).rotationBy(boatAngle.toFloat()).start();
                            windDirectionImage.animate().setDuration(600).rotationBy(windAngle.toFloat()).start();

                            previousBoatDirection = msmnt.BoatDirection;
                            previousWindDirection = msmnt.WindDirection;
                        }
                    }
                    else{
                        txtWindSpeed.setText("---")
                        txtBoatDirection.setText("---")
                        txtWindDirectoin.setText("---")
                        txtBattery.setText("---")
                    }
                })
        }

        private fun angleOffset(newDirection : Int, oldDirection: Int): Int{
            var angle = newDirection - oldDirection
            if (angle > 180)
                return 360 - angle
            else if(angle <-180)
                return angle + 360
            else
                return angle
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenBinding = null
        }
    }
}
