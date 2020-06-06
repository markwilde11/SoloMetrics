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
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.teamwildenberg.solometrics.Ble.BlueDevice
import nl.teamwildenberg.solometrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.solometrics.Service.*
import kotlin.coroutines.CoroutineContext


class MainActivity : ActivityBase(),CoroutineScope {
    private lateinit var mJob: Job
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val thisActivity = this
        mJob = Job()

        var versionName= BuildConfig.VERSION_NAME
        setTitle("SoloMetrics ( v${versionName} )")

        GpsButton.setOnClickListener { view ->
            var gpsServiceIntent = Intent(this, GpsService::class.java)
            if (gpsServiceConnection.status == DeviceStatusEnum.Disconnected){
                startService(gpsServiceIntent)
            }
            else{
                stopService(gpsServiceIntent)
            }
            collapseFABMenu()
        }

        UltrasonicButton.setOnClickListener { view ->
            var deviceType = DeviceTypeEnum.Ultrasonic
            launch {
                if (screenDuinoServiceConnection == null || screenDuinoServiceConnection?.ultrasonicStatus == DeviceStatusEnum.Disconnected) {
                    discoverDevice(deviceType, thisActivity)
                }
                else{
                    callScreenDuinoService(deviceType, null, thisActivity)
                }
            }
            collapseFABMenu()
        }
        ScreenDuinoButton.setOnClickListener { view ->
            var deviceType = DeviceTypeEnum.SoloScreenDuino
            launch {
                if (screenDuinoServiceConnection == null || screenDuinoServiceConnection?.screenStatus == DeviceStatusEnum.Disconnected) {
                    discoverDevice(DeviceTypeEnum.SoloScreenDuino, thisActivity)
                }
                else{
                    callScreenDuinoService(deviceType, null, thisActivity)
                }
            }
            collapseFABMenu()
        }

        TraceListButton.setOnClickListener{view->
            launch{
                openTraceListActivity(thisActivity)
            }
            collapseFABMenu()
        }

        var fabList: MutableList<LinearLayout> = mutableListOf()
        fabList.add(fabLayout4)
        fabList.add(fabLayout3)
        fabList.add(fabLayout2)
        fabList.add(fabLayout1)
        initFloatingMenu(fabBGLayout, fab, fabList)
    }

    override fun onResume() {
        super.onResume()

        val bindServiceIntent = Intent(this, ScreenDuinoService::class.java)
        this.bindService(bindServiceIntent, screenDuinoServiceConnection, Context.BIND_NOT_FOREGROUND)

        val bindGpsServiceIntent = Intent(this, GpsService::class.java)
        this.bindService(bindGpsServiceIntent, gpsServiceConnection, Context.BIND_NOT_FOREGROUND)


        val bindStorageServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindStorageServiceIntent, storageServiceConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onPause() {
        super.onPause()
        this.unbindService(screenDuinoServiceConnection)
        this.unbindService(gpsServiceConnection)
        this.unbindService(storageServiceConnection)
    }

    private suspend fun openTraceListActivity(thisActivity: Activity) {
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

                    callScreenDuinoService(deviceType, theDevice, thisActivity)
                }
            }
        }
    }

    private suspend fun callScreenDuinoService(deviceType: DeviceTypeEnum, theDevice: BlueDevice?, thisActivity: Activity){
        var permissionResult = this.requestPermissionss(Manifest.permission.FOREGROUND_SERVICE, activityRequestCode =  1)

        if (permissionResult) {
            val startServiceIntent = Intent(thisActivity, ScreenDuinoService::class.java)
            startServiceIntent.putExtra("deviceType", deviceType)
            startServiceIntent.putExtra("blueDevice", theDevice)
            
            ContextCompat.startForegroundService(thisActivity, startServiceIntent)
        }
        else{
            var fab = thisActivity.findViewById<FloatingActionButton>(R.id.UltrasonicButton)
            Snackbar.make(fab, "Cancelled connection '${deviceType}, allow to run as service'", Snackbar.LENGTH_LONG)
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

    private val gpsServiceConnection = object: ServiceConnection{
        public var status: DeviceStatusEnum = DeviceStatusEnum.Disconnected
        private var observableDisposable: CompositeDisposable = CompositeDisposable()
        private var gpsBinding: GpsService.LocalBinder? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            gpsBinding = service as GpsService.LocalBinder
            status = gpsBinding!!.gpsStatus
            setStatusColor(gpsStatus, R.drawable.ic_baseline_gps_fixed_24, gpsBinding!!.gpsStatus)
            if (gpsBinding !=null){
                observableDisposable += gpsBinding!!.gpsStatusChannel
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe{ theStatus ->
                        if (gpsBinding!=null){
                            status = gpsBinding!!.gpsStatus
                        }
                        setStatusColor(gpsStatus, R.drawable.ic_baseline_gps_fixed_24, theStatus)
                    }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            setStatusColor(gpsStatus, R.drawable.ic_baseline_gps_fixed_24, DeviceStatusEnum.Disconnected)
            observableDisposable.clear()
            gpsBinding = null
        }
    }

    private val storageServiceConnection = object: ServiceConnection{
        public var status: DeviceStatusEnum = DeviceStatusEnum.Disconnected
        private var observableDisposable: CompositeDisposable = CompositeDisposable()
        private var storageBinding: StorageService.LocalBinder? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
            setStatusColor(StorageStatusView, R.drawable.ic_sd_storage_black_24dp, storageBinding!!.storageStatus)
            if (storageBinding !=null){
                status = storageBinding!!.storageStatus
                observableDisposable += storageBinding!!.storageStatusChannel
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe{ theStatus ->
                        status = theStatus
                        setStatusColor(StorageStatusView, R.drawable.ic_sd_storage_black_24dp, theStatus)
                    }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            storageBinding = null
        }
    }

    private val screenDuinoServiceConnection = object : ServiceConnection {
        public var screenStatus: DeviceStatusEnum = DeviceStatusEnum.Disconnected
        public var ultrasonicStatus: DeviceStatusEnum = DeviceStatusEnum.Disconnected
        private var observableDisposable: CompositeDisposable = CompositeDisposable()
        private var screenBinding: ScreenDuinoService.LocalBinder? = null

        var previousBoatDirection : Int = 0;
        var previousWindDirection : Int =0;

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            screenBinding = service as ScreenDuinoService.LocalBinder
            if (screenBinding != null) {
                screenStatus = screenBinding!!.screenStatus
                setStatusColor(ScreenStatusView, R.drawable.ic_web_black_24dp, screenBinding!!.screenStatus)
                observableDisposable += screenBinding!!.screenStatusChannel
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ theStatus ->
                        screenStatus = screenBinding!!.screenStatus
                        setStatusColor(ScreenStatusView, R.drawable.ic_web_black_24dp, theStatus)
                    })

                ultrasonicStatus = screenBinding!!.ultrasonicStatus
                setStatusColor(UltrasonicStatusView, R.drawable.ic_nature_black_24dp, screenBinding!!.ultrasonicStatus)
                observableDisposable += screenBinding!!.ultrasonicStatusChannel
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ theStatus ->
                        setStatusColor(UltrasonicStatusView, R.drawable.ic_nature_black_24dp, theStatus)
                        ultrasonicStatus = screenBinding!!.ultrasonicStatus
                        when (theStatus) {
                            DeviceStatusEnum.Disconnected -> {
                                updateCompass(null)
                            }
                            DeviceStatusEnum.Connected -> {
                                screenBinding!!.windMeasurementChannel
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe{ windMeasurement ->
                                        updateCompass(windMeasurement)
                                    }
                            }
                        }
                    })
            }


        }


        private fun updateCompass(windMeasurement: WindMeasurement?){

            var txtWindSpeed = findViewById<TextView>(R.id.WindSpeedTextView)
            var txtBoatDirection = findViewById<TextView>(R.id.BoatDirectionTextView)
            var txtWindDirectoin = findViewById<TextView>(R.id.WindDirectionTextView)
            var txtBattery = findViewById<TextView>(R.id.UltrasonicBatteryTextView)

            if (windMeasurement != null) {
                txtWindSpeed.setText("${windMeasurement.WindSpeed}")
                txtBoatDirection.setText("${windMeasurement.BoatDirection}")
                txtWindDirectoin.setText("${windMeasurement.WindDirection}")
                txtBattery.setText("${windMeasurement.BatteryPercentage}")

                val bra = RotateAnimation(
                    -previousBoatDirection.toFloat(),
                    -windMeasurement.BoatDirection.toFloat(),
                    Animation.RELATIVE_TO_SELF,
                    0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f
                )
                bra.setDuration(600);
                bra.setFillAfter(true);
                boatDirectionImage.startAnimation(bra);
                previousBoatDirection = windMeasurement.BoatDirection;

                val wra = RotateAnimation(
                    previousWindDirection.toFloat(),
                    windMeasurement.WindDirection.toFloat(),
                    Animation.RELATIVE_TO_SELF,
                    0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f
                )
                wra.setDuration(600);
                wra.setFillAfter(true);
                windDirectionImage.startAnimation(wra);
                previousWindDirection = windMeasurement.WindDirection;
            }
            else{
                txtWindSpeed.setText("---")
                txtBoatDirection.setText("---")
                txtWindDirectoin.setText("---")
                txtBattery.setText("---")

            }

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
            observableDisposable.clear()
        }
    }


    private fun setStatusColor(img: ImageView, drawable: Int, status: DeviceStatusEnum){
        when (status) {
            DeviceStatusEnum.Disconnected -> {
                img.setStatusColor(drawable, R.color.statusDisconnected)
            }
            DeviceStatusEnum.Connecting -> {
                img.setStatusColor(drawable, R.color.statusConnecting)
            }
            DeviceStatusEnum.Connected -> {
                img.setStatusColor(drawable, R.color.statusConnected)
            }
        }
    }

    private fun ImageView.setStatusColor( drawable: Int, color: Int) {
        this.setColorFilter(ContextCompat.getColor(context, color), android.graphics.PorterDuff.Mode.SRC_IN)
    }
}
