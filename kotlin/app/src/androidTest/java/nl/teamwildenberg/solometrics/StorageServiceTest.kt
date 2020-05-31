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
import junit.framework.Assert.*
import nl.teamwildenberg.solometrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.Service.*
import org.junit.*
import org.junit.runner.RunWith
import java.lang.RuntimeException
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

    private fun stopTrace(trace: PaperTrace){
        val serviceIntent = Intent(
            ApplicationProvider.getApplicationContext<Context>(),
            StorageService::class.java
        ).apply {
            // Data can be passed to the service via the Intent.
            putExtra("action", "stop")
        }
        serviceRule.startService(serviceIntent)
    }

    @Test
    fun StorageServiceTest_traceStart() {
    //    RxJavaPlugins.setIoSchedulerHandler{ Schedulers.trampoline()}

        // ARRANGE
        var status: StorageStatus? = null
        service.storageStatusChannel
            .subscribe{
                status = it
            }
        // ACT
        startNewTrace()
        // ASSERT
        assertEquals(1, service.activeTrace?.key)
        assertEquals(service.activeTrace?.epoch, Instant.now().epochSecond)
        assertEquals(StorageStatusEnum.StartNew, status?.state)
        assertEquals(service.activeTrace, status?.trace)
    }

    @Test
    fun StorageServiceTest_traceStop(){
        // ARRANGE
        startNewTrace()
        var trace = service.activeTrace
        var status: StorageStatus? = null
        service.storageStatusChannel
            .subscribe{
                status = it
            }
        // ACT
        if (trace != null) {
            stopTrace(trace)
        }
        assertNull(service.activeTrace)
        assertEquals(StorageStatusEnum.Stopped, status?.state)
        assertEquals(trace, status?.trace)

    }

    @Test
    fun StorageServiceTest_traceStart_onExisting() {
        // ARRANGE
        var key:Int = 0;

        Paper.book().write((++key).toString(), PaperTrace(key, Instant.now().epochSecond))
        // ACT
        startNewTrace()

        // ASSERT
        assertEquals(2, service.activeTrace?.key)
    }

    @Test()
    fun StorageServiceTest_traceStart_alreadyStarted() {
        // ARRANGE
        var key:Int = 0;
        startNewTrace()

        // ACT
        startNewTrace()
        assertEquals(1, service.activeTrace?.key)
    }
    @Test
    fun StorageServiceTest_Partition_ToNewTrace(){
        startNewTrace()
        var measurementList = generateMeasurementList(10)
        var partitionKey = service.activeTrace?.let { service.AddMeasurements(it, measurementList) }
        assertEquals(1.toStringKey(), partitionKey)
    }

    @Test
    fun StorageServiceTest_Partition_ToExistingTrace(){
        // ARRANGE
        startNewTrace()
        var measurementList = generateMeasurementList(10)
        service.activeTrace?.let { service.AddMeasurements(it, measurementList) }

        // ACT
        var partitionKey = service.activeTrace?.let { service.AddMeasurements(it, measurementList) }

        // ASSERT
        assertEquals(partitionKey, 2.toStringKey())
        var partitionKeyList = Paper.book(service.activeTrace?.key?.toStringKey()).allKeys
        assertEquals(partitionKeyList.size, 2)
        var measurementListResult = Paper.book(service.activeTrace?.key?.toStringKey()).read<MutableList<PaperMeasurement>>(partitionKey)
        assertEquals(measurementList.size, measurementListResult.size)
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
                var trace = service.activeTrace
                if (trace != null) {
                    partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
                }
            }
        // ACT
        service.bindMeasurementObserver( measurementObservable)

        // ASSERT
        assertEquals(2, partitionKeyList.size)
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
                var trace = service.activeTrace
                if (trace != null) {
                    partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
                }
            }
        // ACT
        service.bindMeasurementObserver( measurementObservable)
        startNewTrace()

        // ASSERT
        assertEquals(0,partitionKeyList.size)
    }

    @Test
    fun StorageServiceTest_TraceList_Empty(){
        // ARRANGE
        // ACT
        var traceList = service.GetTraceList()
        // ASSERT
        assertEquals(0, traceList.size)
    }

    @Test
    fun StorageServiceTest_TraceList_Multiple(){
        // ARRANGE
        var key: Int = 0
        Paper.book().write((++key).toString(), PaperTrace(key, Instant.now().epochSecond))
        startNewTrace()

        // ACT
        var traceList = service.GetTraceList()
        // ASSERT
        assertEquals(2, traceList.size)
        assertTrue(traceList.last().epoch > (Instant.now().epochSecond -1))
    }

    @Test
    fun StorageServiceTest_WindMeasurementEmpty(){
        // ARRANGE
        startNewTrace()
        var trace = service.activeTrace

        // ACT
        var windMeasurementList = trace?.let { service.GetWindMeasurementList(it) }
        // ASSERT
        assertEquals(0, windMeasurementList?.size)
    }

    @Test
    fun StorageServiceTest_WindMeasurementMultiple(){
        // ARRANGE
        RxJavaPlugins.setIoSchedulerHandler{ Schedulers.trampoline()}

        startNewTrace()
        lateinit var partitionKeyList: List<String>
        val measurementList = generateWindMeasurementList(120)
        var measurementObservable = measurementList
            .toObservable()
            .observeOn(Schedulers.io())
            .doFinally(){
                var trace = service.activeTrace
                if (trace != null) {
                    partitionKeyList = Paper.book(trace.key.toStringKey()).allKeys
                }
            }
        service.bindMeasurementObserver( measurementObservable)
        var trace = service.activeTrace

        // ACT
        var windMeasurementList = trace?.let { service.GetWindMeasurementList(it) }
        // ASSERT
        assertEquals(120, windMeasurementList?.size)
    }

    @Test
    fun StorageServiceTest_Delete_Empty(){
        //ARRANGE
        startNewTrace()
        var trace = service.activeTrace
        var traceKey: String? = trace?.key?.toStringKey();
        var status: StorageStatus? = null
        service.storageStatusChannel
            .subscribe{
                status = it
            }

        //ACT
        if (trace != null) {
            service.DeleteTrace(trace)
        }

        //ASSESS
        var keyList = Paper.book().allKeys.filter{ key -> key == traceKey}
        assertEquals(0, keyList.size)
        assertEquals(StorageStatusEnum.Delete, status?.state)
        assertEquals(status?.trace, trace)

    }

    @Test(expected = RuntimeException::class)
    fun StorageServiceTest_Delete_Fulll(){
        //ARRANGE
        startNewTrace()
        var measurementList = generateMeasurementList(10)
        var partitionKey = service.activeTrace?.let { service.AddMeasurements(it, measurementList) }
        var trace = service.activeTrace
        var traceKey: String? = trace?.key?.toStringKey();

        //ACT
        if (trace != null) {
            var result = service.DeleteTrace(trace)

            //ASSESS
            assertEquals(StorageService.DeleteTraceResult.Success, result)
        }

        var keyList = Paper.book().allKeys.filter{ key -> key == traceKey}
        assertEquals(0, keyList.size)
        var book = Paper.bookOn(traceKey)

        var partionKeyList = book.allKeys
    }

    @Test
    fun StorageServiceTest_Delete_NonExisting(){
        //ARRANGE
        var paperTrace = PaperTrace(-1, Instant.now().epochSecond)

        //ACT
        var result = service.DeleteTrace(paperTrace)

        //ASSERT
        assertEquals(StorageService.DeleteTraceResult.NotFound, result)
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
