package nl.teamwildenberg.solometrics

import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import io.paperdb.Paper
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.toObservable
import io.reactivex.schedulers.Schedulers
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNull
import nl.teamwildenberg.SoloMetrics.Extensions.toStringKey
import nl.teamwildenberg.SoloMetrics.Service.PaperTrace
import nl.teamwildenberg.SoloMetrics.Service.StorageService
import nl.teamwildenberg.SoloMetrics.Service.WindMeasurement
import nl.teamwildenberg.solometrics.Service.PaperMeasurement
import org.hamcrest.CoreMatchers.`is`
import org.junit.*
import org.junit.runner.RunWith
import java.lang.Exception
import java.time.Instant

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class StorageServiceTest {
    @get:Rule
    val serviceRule = ServiceTestRule()
    private lateinit var service: StorageService

    @Before
    fun initializeService(){
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext<Context>(),
            StorageService::class.java
        ).apply {
            // Data can be passed to the service via the Intent.
            putExtra("action", "")
        }

        // Bind the service and grab a reference to the binder.
        val binder: IBinder = serviceRule.bindService(serviceIntent)

        // Get the reference to the service, or you can call
        // public methods on the binder directly.
        service = (binder as StorageService.LocalBinder).getService()

        Paper.book().destroy()
        Paper.book("0001").destroy()
        Paper.book("0002").destroy()
    }

    @After
    fun teardownService(){
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext<Context>(),
            StorageService::class.java
        ).apply{
            putExtra("action", "stop")
        }
        serviceRule.startService(serviceIntent)

    }

    private fun startNewTrace(){
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext<Context>(),
            StorageService::class.java
        ).apply {
            // Data can be passed to the service via the Intent.
            putExtra("action", "start")
        }
        serviceRule.startService(serviceIntent)
    }

    @Test
    fun StorageServiceTest_traceStart() {
        @Test
        fun existingTraceTest_Success() {
            // ARRANGE

            // ACT
            startNewTrace()
            // ASSERT
            assertEquals(service.trace?.key, 2)
            assertEquals(service.trace?.epoch, Instant.now().epochSecond)
        }
    }
    @Test
    fun StorageServiceTest_traceStart_onExisting() {
        // ARRANGE
        var key:Int = 0;

        Paper.book().write((++key).toString(), PaperTrace(key, Instant.now().epochSecond))
        // ACT
        startNewTrace()

        // ASSERT
        assertEquals(service.trace?.key, 2)
    }

    @Test()
    fun StorageServiceTest_traceStart_alreadyStarted() {
        // ARRANGE
        var key:Int = 0;
        startNewTrace()

        // ACT
        startNewTrace()
        assertEquals(service.trace?.key, 1)
    }
    @Test
    fun StorageServiceTest_Partition_ToNewTrace(){
        startNewTrace()
        var measurementList = generateMeasurementList(10)
        var partitionKey = service.trace?.let { service.AddMeasurements(it, measurementList) }
        assertEquals(partitionKey, 1.toStringKey())
    }

    @Test
    fun StorageServiceTest_Partition_ToExistingTrace(){
        // ARRANGE
        startNewTrace()
        var measurementList = generateMeasurementList(10)
        service.trace?.let { service.AddMeasurements(it, measurementList) }

        // ACT
        var partitionKey = service.trace?.let { service.AddMeasurements(it, measurementList) }

        // ASSERT
        assertEquals(partitionKey, 2.toStringKey())
        var partitionKeyList = Paper.book(service.trace?.key?.toStringKey()).allKeys
        assertEquals(partitionKeyList.size, 2)
        var measurementListResult = Paper.book(service.trace?.key?.toStringKey()).read<MutableList<PaperMeasurement>>(partitionKey)
        assertEquals(measurementListResult.size, measurementList.size)
    }

    @Test
    fun StorageServiceTest_Partition_Bind(){
        // ARRANGE
        RxJavaPlugins.setIoSchedulerHandler{ Schedulers.trampoline()}

        startNewTrace()
        lateinit var partitionKeyList: List<String>
        val measurementList = generateWindMeasurementList(120)
        var measurementObservable = measurementList
            .toObservable()
            .observeOn(Schedulers.io())
            .doFinally(){
                var trace = service.trace
                if (trace != null) {
                    partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
                }
            }
        // ACT
        service.bindMeasurementObserver( measurementObservable)

        // ASSERT
        assertEquals(partitionKeyList.size, 2)
    }

    @Test
    fun StorageServiceTest_Partition_BindBeforeStarting(){
        // ARRANGE
        RxJavaPlugins.setIoSchedulerHandler{ Schedulers.trampoline()}

        var partitionKeyList: List<String> = listOf()
        val measurementList = generateWindMeasurementList(120)
        var measurementObservable = measurementList
            .toObservable()
            .observeOn(Schedulers.io())
            .doFinally(){
                var trace = service.trace
                if (trace != null) {
                    partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
                }
            }
        // ACT
        service.bindMeasurementObserver( measurementObservable)
        startNewTrace()

        // ASSERT
        assertEquals(partitionKeyList.size, 0)
    }


    private fun generateWindMeasurementList(arraySize:Int): MutableList<WindMeasurement>{
        var partitionKey : Int = 0;
        var counter: Int = 0;

        var msmnList : MutableList<WindMeasurement> = mutableListOf()
        for(i in 0..arraySize-1 ){
            val msmnt = WindMeasurement(100 * partitionKey, 100+partitionKey, -partitionKey, 0 )
            msmnList.add(msmnt)
            counter ++;
        }
        return msmnList
    }
    private fun generateMeasurementList(arraySize:Int): MutableList<PaperMeasurement>{
        var partitionKey : Int = 0;
        var counter: Int = 0;

        var msmnList : MutableList<PaperMeasurement> = mutableListOf()
        for(i in 0..arraySize-1 ){
            val msmnt = PaperMeasurement(counter, Instant.now().epochSecond,100 * partitionKey, 100+partitionKey, -partitionKey, 0 )
            msmnList.add(msmnt)
            counter ++;
        }
        return msmnList
    }
}
