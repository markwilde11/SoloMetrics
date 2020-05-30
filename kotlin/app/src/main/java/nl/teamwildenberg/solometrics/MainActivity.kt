package nl.teamwildenberg.solometrics

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.teamwildenberg.solometrics.Ble.BlueDevice
import nl.teamwildenberg.solometrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.solometrics.Service.ScreenDuinoService
import nl.teamwildenberg.solometrics.Service.StorageService
import kotlin.coroutines.CoroutineContext


class MainActivity : ActivityBase(),CoroutineScope {
    private lateinit var mJob: Job
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.Main
    private var screenBinding: ScreenDuinoService.LocalBinder? = null
    private var storageBinding: StorageService.LocalBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val thisActivity = this
        mJob = Job()

        var versionName= BuildConfig.VERSION_NAME
        setTitle("SoloMetrics ( v${versionName} )")

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
//        StorageButton.setOnClickListener { view ->
//            var storageServiceIntent = Intent(this, StorageService::class.java)
//            if (storageBinding == null){
//                storageServiceIntent.putExtra("action", "start")
//                startService(storageServiceIntent)
//            }
//            else
//            {
//                stopService(storageServiceIntent)
//            }
//        }

        TraceListButton.setOnClickListener{view->
            launch{
                openTraceListActivity(thisActivity)
            }
        }

        var fabList: MutableList<LinearLayout> = mutableListOf()
        fabList.add(fabLayout3)
        fabList.add(fabLayout2)
        fabList.add(fabLayout1)
        initFloatingMenu(fabBGLayout, fab, fabList)
    }

    override fun onResume() {
        super.onResume()

        val bindServiceIntent = Intent(this, ScreenDuinoService::class.java)
        this.bindService(bindServiceIntent, screenDuinoServiceConnection, Context.BIND_NOT_FOREGROUND)

        val bindStorageServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindStorageServiceIntent, storageServiceConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onPause() {
        super.onPause()
        this.unbindService(screenDuinoServiceConnection)
        this.unbindService(storageServiceConnection)
    }

    private suspend fun openTraceListActivity(thisActivity: Activity) {
        startActivityForResult(intent, 3)
        val changePageIntent = Intent(thisActivity, TraceListActivity::class.java)
        var activityResult: ActivityResult?

        launch {

            activityResult = launchIntent(changePageIntent).await()
            if (activityResult?.resultCode == RESULT_OK) {

            }
        }
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

    private val storageServiceConnection = object: ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            storageBinding = null
        }
    }

    private val screenDuinoServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            var txtScreen = findViewById<TextView>(R.id.ScreenTextView)
            var txtWindSpeed = findViewById<TextView>(R.id.WindSpeedTextView)
            var txtBoatDirection = findViewById<TextView>(R.id.BoatDirectionTextView)
            var txtWindDirectoin = findViewById<TextView>(R.id.WindDirectionTextView)
            var txtBattery = findViewById<TextView>(R.id.UltrasonicBatteryTextView)
            var previousBoatDirection : Int = 0;
            var previousWindDirection : Int =0;


            screenBinding = service as ScreenDuinoService.LocalBinder
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

                            val bra = RotateAnimation(
                                - previousBoatDirection.toFloat(),
                                - msmnt.BoatDirection.toFloat(),
                                Animation.RELATIVE_TO_SELF,
                                0.5f,
                                Animation.RELATIVE_TO_SELF,
                                0.5f
                            )
                            bra.setDuration(600);
                            bra.setFillAfter(true);
                            boatDirectionImage.startAnimation(bra);
                            previousBoatDirection = msmnt.BoatDirection;

                            val wra = RotateAnimation(
                                previousWindDirection.toFloat(),
                                msmnt.WindDirection.toFloat(),
                                Animation.RELATIVE_TO_SELF,
                                0.5f,
                                Animation.RELATIVE_TO_SELF,
                                0.5f
                            )
                            wra.setDuration(600);
                            wra.setFillAfter(true);
                            windDirectionImage.startAnimation(wra);
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
