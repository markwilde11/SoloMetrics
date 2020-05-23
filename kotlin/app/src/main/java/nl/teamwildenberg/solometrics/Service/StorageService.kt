package nl.teamwildenberg.SoloMetrics.Service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import io.paperdb.Paper
import io.reactivex.Observable
import nl.teamwildenberg.SoloMetrics.Extensions.toStringKey
import nl.teamwildenberg.SoloMetrics.Service.WindMeasurement
import nl.teamwildenberg.solometrics.Extensions.toPaper
import nl.teamwildenberg.solometrics.Service.PaperMeasurement
import java.lang.Exception
import java.time.Instant
import java.util.concurrent.TimeUnit

class StorageService: Service() {
    private val myBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

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

    public fun StartNewTrace(): PaperTrace {
        var traceKeys = Paper.book().allKeys
        var newKey = 0
        if (traceKeys.size > 0){
            var lastKey = traceKeys.last()
            var lastTrace = Paper.book().read<PaperTrace>(lastKey)
            newKey = lastTrace.key
        }
        var trace = PaperTrace(++newKey, Instant.now().epochSecond)
        Paper.book().write(newKey.toStringKey(), trace)
        return trace
    }

    public fun AddListener(trace: PaperTrace, obs: Observable<WindMeasurement>){
        var counter = 0
        var disp = obs
            .map{msmnt:WindMeasurement -> msmnt.toPaper( ++counter)}
            .buffer(60)
            .subscribe { msmntList ->
                AddMeasurements(trace, msmntList)
            }
    }

    public fun AddMeasurements(trace: PaperTrace,measurementPartition: MutableList<PaperMeasurement>): String{
        var partitionKey: Int = 1

        checkTrace(trace)
        val traceKey = trace.key.toStringKey()
        var partitionKeyList = Paper.book(traceKey).allKeys
        if (partitionKeyList.size > 0) {
            var lastPartitionKey = partitionKeyList.last()

            partitionKey = lastPartitionKey.toInt() + 1
        }

        Paper.book(trace.key.toStringKey()).write(partitionKey.toStringKey(), measurementPartition)
        return partitionKey.toStringKey()
    }

    private fun checkTrace(traceToVerify: PaperTrace){
        var trace = Paper.book().read<PaperTrace>(traceToVerify.key.toStringKey())
        if (trace == null){
            throw Exception("Trace '${traceToVerify.key}' does not exist")
        }
    }
}