package nl.teamwildenberg.SoloMetrics.Service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import io.paperdb.Paper
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import nl.teamwildenberg.SoloMetrics.Ble.BlueDevice
import nl.teamwildenberg.SoloMetrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.Extensions.toPaper
import nl.teamwildenberg.solometrics.Service.PaperMeasurement
import java.lang.Exception
import java.time.Instant

class StorageService: Service() {
    private val myBinder = LocalBinder()
    public var trace: PaperTrace? = null;
    private val measurementDisposable: CompositeDisposable = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent != null) {
            // hydrate UltraSonic bluetooth device
            Log.d("StorageService", "starting command - determine if start")
            val actionString = intent.getStringExtra("action")
            if (actionString == "start") {
                StartNewTrace()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

    }


    override fun onBind(intent: Intent?): IBinder? {
        return myBinder
    }


    inner class LocalBinder(
    ): Binder(){
        fun getService(): StorageService{
            return this@StorageService
        }
    }

    private fun StartNewTrace() {
        if (trace != null){
            throw Exception("Storage already started")
        }
        var traceKeys = Paper.book().allKeys
        var newKey = 0
        if (traceKeys.size > 0){
            var lastKey = traceKeys.last()
            var lastTrace = Paper.book().read<PaperTrace>(lastKey)
            newKey = lastTrace.key
        }
        trace = PaperTrace(++newKey, Instant.now().epochSecond)
        Paper.book().write(newKey.toStringKey(), trace)
    }

    public fun StopTrace(){
        if (trace != null){
            trace = null
        }

    }

    public fun bindMeasurementObserver(obs: Observable<WindMeasurement>){
        var counter = 0
        measurementDisposable += obs
            .map{msmnt:WindMeasurement -> msmnt.toPaper( ++counter)}
            .buffer(60)
            .subscribe { msmntList ->
                trace?.let { AddMeasurements(it, msmntList) }
            }
    }

    public fun unbindMeasurementObserver(){
        measurementDisposable.clear()
    }

    public fun AddMeasurements(targetTrace:PaperTrace, measurementPartition: MutableList<PaperMeasurement>): String{
        var partitionKey: Int = 1

        checkTrace(targetTrace)
        val traceKey = targetTrace.key.toStringKey()
        var partitionKeyList = Paper.book(traceKey).allKeys
        if (partitionKeyList.size > 0) {
            var lastPartitionKey = partitionKeyList.last()

            partitionKey = lastPartitionKey.toInt() + 1
        }

        Paper.book(targetTrace.key.toStringKey()).write(partitionKey.toStringKey(), measurementPartition)
        return partitionKey.toStringKey()
    }

    private fun checkTrace(traceToVerify: PaperTrace){
        var trace = Paper.book().read<PaperTrace>(traceToVerify.key.toStringKey())
        if (trace == null){
            throw Exception("Trace '${traceToVerify.key}' does not exist")
        }
    }
}