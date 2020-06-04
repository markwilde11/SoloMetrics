package nl.teamwildenberg.solometrics.Service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nl.teamwildenberg.solometrics.Ble.BleService
import nl.teamwildenberg.solometrics.Ble.BlueDevice
import nl.teamwildenberg.solometrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.solometrics.MainActivity
import nl.teamwildenberg.solometrics.R
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


class ScreenDuinoService: Service() {
    val CHANNEL_ID: String = "ScreenDuinoServiceChannel"
    private val screenDisposable: CompositeDisposable = CompositeDisposable()
    private val ultrasonicDisposable: CompositeDisposable = CompositeDisposable()
    private val userSettingsDisposable: CompositeDisposable = CompositeDisposable()
    private var screenInstanceCounter: Int = 0
    private var ultrasonicInstanceCounter: Int = 0

    var screenDuinoDevice: BlueDevice? = null
    var ultraSonicDevice: BlueDevice?= null
    var windMeasurement: WindMeasurement?= null
    var storageIsConnected: Boolean = false
    var screenText: String = ""

    private val localBinder = LocalBinder()
    private var storageBinding: StorageService.LocalBinder? = null

    override fun onCreate() {
        super.onCreate()

        val bindServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindServiceIntent, myServiceConnection, Context.BIND_NOT_FOREGROUND)

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (    (screenDuinoDevice == null)
            &&  (ultraSonicDevice == null)
        ) {
            createNotificationChannel()
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0, notificationIntent, 0
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SoloMetrics Service")
                .setContentText("Bluetooth watcher for both the UltraSonic and ScreenDuino")
                .setColor(Color.rgb(127,0,55))
                .setSmallIcon(R.drawable.ic_solometrics_notification)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_solometrics_notification_large))
                .setContentIntent(pendingIntent)
                .build()

            startForeground(startId, notification)
        }

        Log.d("ScreenDuinoService", "starting command - Begin")
        if (intent != null) {
            // hydrate UltraSonic bluetooth device
            Log.d("ScreenDuinoService", "starting command - Hydrate device")
            val deviceType = intent.getSerializableExtra("deviceType") as DeviceTypeEnum
            val blueDevice = intent.getParcelableExtra("blueDevice") as BlueDevice?

            // hydrate ScreenDuino bluetooth device

            when (deviceType) {
                DeviceTypeEnum.Ultrasonic ->{
                    Log.d("ScreenDuinoService", "starting command - Connect to ultrasonic")
                    // connect to UltraSonic
                    ultraSonicDevice = blueDevice
                    connectToUltrasonic(blueDevice, ultrasonicDisposable)
                }
                DeviceTypeEnum.SoloScreenDuino -> {
                    Log.d("ScreenDuinoService", "starting command - Connect to screen")
                    screenDuinoDevice = blueDevice
                    connectToScreen(blueDevice, screenDisposable)
                }
            }
        }
        else
        {

        }

        Log.d("ScreenDuinoService", "starting command - End")
        if (    (screenDuinoDevice == null)
            &&  (ultraSonicDevice == null)
            ){
            this.stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        this.unbindService(myServiceConnection)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return localBinder
    }

    private fun connectToScreen(theBlueDevice: BlueDevice?, theDisposable: CompositeDisposable){
        if (theDisposable.size() == 0 && theBlueDevice != null) {
            localBinder.screenStatusChannel.onNext(DeviceStatusEnum.Connecting)
            theDisposable.clear()
            GlobalScope.launch {
                val bls = BleService(this@ScreenDuinoService)

                val obs = bls.Connect(theBlueDevice)
                obs.take(1).subscribe({char ->
                    localBinder.screenStatusChannel.onNext(DeviceStatusEnum.Connected)
                })

                theDisposable += obs
                    .subscribe({ char ->
                        Log.d("ScreenDuinoService", "connectToDevice - connected: ${char.value}")
                        var screenValues = parseScreenValues(char.value)
                        // broadcast something
                        screenInstanceCounter++
                    })
            }
        }
        else{
            localBinder.screenStatusChannel.onNext(DeviceStatusEnum.Disconnected)
            theDisposable.clear()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SoloMetrics Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(
                NotificationManager::class.java
            )
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun connectToUltrasonic(theBlueDevice: BlueDevice?, theDisposable: CompositeDisposable) {

        if (theDisposable.size() == 0 && theBlueDevice != null) {
            localBinder.ultrasonicStatusChannel.onNext(DeviceStatusEnum.Connecting)
            GlobalScope.launch {
                val bls = BleService(this@ScreenDuinoService)
                val obs = bls.Connect(theBlueDevice)

                // shortlived subscriber to set the user settings
                userSettingsDisposable += obs.take(1).subscribe({char ->
                    bls.setChar(theBlueDevice, "0000a003-0000-1000-8000-00805f9b34fb", true)
                    localBinder.ultrasonicStatusChannel.onNext(DeviceStatusEnum.Connected)
                })

                theDisposable += obs
                    .sample(1, TimeUnit.SECONDS)
                    .doOnDispose({
                        if (screenDuinoDevice != null) {
                            var value = ScreenValue(0,0,0,0)
                            var screenValueArray = serializeScreenValues(value)
                            bls.setChar(
                                    screenDuinoDevice!!,
                                    screenValueArray)
                        }
                    })
                    .subscribe({ char ->
                        Log.d("Mainactivity", "connectToDevice - connected: ${char.value}")
                        // KUDO's (int is 4 bits) there
                        // https://stackoverflow.com/a/49986095/553589

                        var msmnt = parseWindMeasurements(char.value);
                        //                temp: valueList[5] - 100);
                        Log.d("Mainactivity", "speed: ${msmnt.WindSpeed} windDir ${msmnt.WindDirection}, boatDir ${msmnt.BoatDirection}, Battery ${msmnt.BatteryPercentage}")
                        var value = ScreenValue(
                            WindRelativeDirection = calculateRelativeDirection(msmnt),
                            ///WindDirection =  msmnt.WindDirection,
                            WindSpeed =  msmnt.WindSpeed,
                            BoatDirection = msmnt.BoatDirection,
                            BatteryPercentage =  msmnt.BatteryPercentage
                        )
                        var screenValueArray = serializeScreenValues(value)
                        if (screenDuinoDevice != null) {
                            bls.setChar(
                                screenDuinoDevice!!,
                                screenValueArray)
                        }
                        localBinder.windMeasurementChannel.onNext(msmnt)
                        ultrasonicInstanceCounter++
                    })
            }
        }
        else{
            localBinder.ultrasonicStatusChannel.onNext(DeviceStatusEnum.Disconnected)
            theDisposable.clear()
        }
    }

    public fun Byte.toUnsignedInt(): Int{
        val value = this.toInt()
        if (value < 0)
            return value.absoluteValue + 127
        else
            return value

    }

    private fun calculateRelativeDirection(mngmnt: WindMeasurement): Int{
        if (mngmnt.WindDirection > 180)
            return (mngmnt.WindDirection -360).absoluteValue;
        else
            return (mngmnt.WindDirection)
    }

    private fun parseScreenValues(valueArray : ByteArray): ScreenValue{

        val secondElement = (valueArray[1].toUnsignedInt()).shl(8)
        var firstElement = valueArray[0].toUnsignedInt()

        var value = ScreenValue(
            WindRelativeDirection = valueArray[1].toInt().shl(8) or valueArray[0].toInt(),
            BoatDirection = valueArray[3].toInt().shl(8) or valueArray[2].toInt(),
            WindSpeed = valueArray[5].toInt().shl(8) or valueArray[4].toInt(),
            BatteryPercentage = valueArray[7].toInt().shl(8) or valueArray[6].toInt()
        )
        return value
    }

    private fun serializeScreenValues(value: ScreenValue): ByteArray{
        var valueArray: ByteArray = ByteArray(8)
        valueArray[1] = ( value.WindRelativeDirection and 0xFFFF).toByte()
        valueArray[0] = ( (value.WindRelativeDirection ushr 8) and 0xFFFF).toByte()
        valueArray[3] = ( value.BoatDirection and 0xFFFF).toByte()
        valueArray[2] = ( (value.BoatDirection ushr 8) and 0xFFFF).toByte()
        valueArray[5] = ( value.WindSpeed and 0xFFFF).toByte()
        valueArray[4] = ( (value.WindSpeed ushr 8) and 0xFFFF).toByte()
        valueArray[7] = ( value.BatteryPercentage and 0xFFFF).toByte()
        valueArray[6] = ( (value.BatteryPercentage ushr 8) and 0xFFFF).toByte()
        return valueArray
    }


    private fun parseWindMeasurements(value : ByteArray): WindMeasurement{

        val secondElement = (value[1].toUnsignedInt()).shl(8)
        var firstElement = value[0].toUnsignedInt()
        var boatDirection = 360 - (value[9].toUnsignedInt().shl(8) or value[8].toUnsignedInt())
        if (boatDirection == 360)
            boatDirection = 0
        var msmnt = WindMeasurement(
            WindSpeed = ((secondElement or firstElement)  * 1.9438444924574 / 100).roundToInt(),
            WindDirection = value[3].toUnsignedInt().shl(8) or value[2].toUnsignedInt(),
            BoatDirection = boatDirection,
            BatteryPercentage = value[4].toUnsignedInt()*10
        )

        return msmnt
    }

    inner class LocalBinder(
        val screenStatusChannel: Subject<DeviceStatusEnum> = PublishSubject.create<DeviceStatusEnum>(),
        val ultrasonicStatusChannel: Subject<DeviceStatusEnum> = PublishSubject.create<DeviceStatusEnum>(),
        val windMeasurementChannel: Subject<WindMeasurement> = PublishSubject.create<WindMeasurement>(),
        var screenStatus: DeviceStatusEnum = DeviceStatusEnum.Disconnected,
        var ultrasonicStatus: DeviceStatusEnum = DeviceStatusEnum.Disconnected
    ): Binder(){
        init{
            screenStatusChannel.subscribe{
                screenStatus = it
            }
            ultrasonicStatusChannel.subscribe{
                ultrasonicStatus =it
            }
        }

        fun getService(): ScreenDuinoService{
            return this@ScreenDuinoService
        }
    }

    private val myServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
            var storageService = storageBinding!!.getService()
            //TODO: wind advertisement
//            storageService.bindMeasurementObserver( screenStatusChannel.map{ status -> windMeasurement})
            storageIsConnected = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            var storageService = storageBinding!!.getService()
            storageService.unbindMeasurementObserver()
            storageIsConnected = false
            storageBinding = null;
        }
    }
}
