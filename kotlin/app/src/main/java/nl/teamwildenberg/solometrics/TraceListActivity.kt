package nl.teamwildenberg.solometrics

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.util.DisplayMetrics
import android.util.Log
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.preference.PreferenceFragmentCompat
import com.google.gson.Gson
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.fab
import kotlinx.android.synthetic.main.activity_main.fabLayout1
import kotlinx.android.synthetic.main.activity_main.fabLayout2
import kotlinx.android.synthetic.main.activity_main.fabLayout3
import kotlinx.android.synthetic.main.trace_list_activity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nl.teamwildenberg.solometrics.Adapter.PaperTraceItem
import nl.teamwildenberg.solometrics.Adapter.TraceListAdapter
import nl.teamwildenberg.solometrics.Extensions.setEnabledState
import nl.teamwildenberg.solometrics.Extensions.toDateString
import nl.teamwildenberg.solometrics.Service.DeviceStatusEnum
import nl.teamwildenberg.solometrics.Service.PaperTrace
import nl.teamwildenberg.solometrics.Service.StorageService
import nl.teamwildenberg.solometrics.Service.StorageStatusEnum
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream


class TraceListActivity : ActivityBase(), CoroutineScope by MainScope() {
    private var storageBinding: StorageService.LocalBinder? = null
    var traceList: MutableList<PaperTraceItem> = mutableListOf()
    lateinit var traceAdapter: TraceListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.trace_list_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        traceAdapter = TraceListAdapter(this, traceList)
        traceListView.setAdapter(traceAdapter)
        var selectedTraceItem : PaperTraceItem? = null
        traceListView.setOnGroupClickListener { expandableListView, view, i, l ->
            var service = storageBinding?.getService()
            Log.d("setOnGroupExpandListener", i.toString())
            var listItem = traceListView.getItemAtPosition(i)
            if (listItem != null){
                selectedTraceItem = listItem as PaperTraceItem
                Log.d("setOnGroupExpandListener", selectedTraceItem.toString())

                DeleteTraceListButton.setEnabledState(true)
                SaveTraceListButton.setEnabledState(true)

                if (service != null) {
                    if (selectedTraceItem!!.PartionList == null) {
                        var partionList = service.GetPartionList(selectedTraceItem!!.Trace)
                        selectedTraceItem!!.PartionList = partionList
                        traceAdapter.notifyDataSetChanged()
                    }
                }
            }
            return@setOnGroupClickListener false
        }

        DeleteTraceListButton.setEnabledState(false)
        SaveTraceListButton.setEnabledState(false)

        DeleteTraceListButton.setOnClickListener{
            var service = storageBinding?.getService()
            if (selectedTraceItem != null) {
                service?.DeleteTrace(selectedTraceItem!!.Trace)
                selectedTraceItem = null
                DeleteTraceListButton.setEnabledState(false)
                SaveTraceListButton.setEnabledState(false)

            }
            collapseFABMenu()
        }


        var metrics = DisplayMetrics()
        getWindowManager().getDefaultDisplay().getMetrics(metrics)
        var width = metrics.widthPixels

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            traceListView.setIndicatorBounds(
                width - GetDipsFromPixel(50),
                width - GetDipsFromPixel(10)
            )
        } else {
            traceListView.setIndicatorBoundsRelative(
                width - GetDipsFromPixel(50),
                width - GetDipsFromPixel(10)
            )
        }

        SaveTraceListButton.setOnClickListener {
            var service  = storageBinding?.getService()
            if ((selectedTraceItem != null) && (service != null)){
                // Request code for creating a PDF document.
                var pickerInitialUri: Uri? = null
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    var title = selectedTraceItem
                    var dateString = selectedTraceItem?.Trace?.epoch?.toDateString()
                    putExtra(Intent.EXTRA_TITLE, "Track_${dateString}.json")
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
                }

                var activityResult: ActivityResult?
                launch {

                    activityResult = launchIntent(intent).await()
                    if (activityResult?.resultCode == RESULT_OK) {
                        activityResult?.data?.data?.also {uri ->
                            // Perform operations on the document using its URI.
                            var listToSerialize = service.GetWindMeasurementList(selectedTraceItem!!.Trace)
                            var fileOutputString = Gson().toJson(listToSerialize)
                            val contentResolver = applicationContext.contentResolver

                            try {
                                contentResolver.openFileDescriptor(uri, "w")?.use {
                                    FileOutputStream(it.fileDescriptor).use {
                                        it.write(
                                            (fileOutputString).toByteArray()
                                        )
                                    }
                                }
                            } catch (e: FileNotFoundException) {
                                e.printStackTrace()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }

                        }
                    }
                }
            }
        }

        StartTraceListButton.setOnClickListener{
            var storageServiceIntent = Intent(this, StorageService::class.java)
            if (storageBinding != null){
                var service = storageBinding!!.getService()
                if (service.activeTrace == null) {
                    storageServiceIntent.putExtra("action", "start")
                    startService(storageServiceIntent)
                }
                else{
                    storageServiceIntent.putExtra("action", "stop")
                    startService(storageServiceIntent)
                }
            }
            collapseFABMenu()
        }

        var fabList: MutableList<LinearLayout> = mutableListOf()
        fabList.add(fabLayout3)
        fabList.add(fabLayout2)
        fabList.add(fabLayout1)
        initFloatingMenu(this.fabBGLayoutTrace, fab, fabList)


        var storageServiceIntent = Intent(this, StorageService::class.java)
        startService(storageServiceIntent)
    }

    override fun onResume() {
        super.onResume()

        val bindStorageServiceIntent = Intent(this, StorageService::class.java)
        this.bindService(bindStorageServiceIntent, storageServiceConnection, Context.BIND_NOT_FOREGROUND)
    }

    override fun onPause() {
        super.onPause()
        this.unbindService(storageServiceConnection)
    }


    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
        }
    }

    fun GetDipsFromPixel(pixels: Int): Int {
        // Get the screen's density scale
        val scale = resources.displayMetrics.density
        // Convert the dps to pixels, based on density scale
        return (pixels * scale + 0.5f).toInt()
    }

    /// KUDOS:
    /// https://stackoverflow.com/a/46499387/553589
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.getItemId()) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        var finishIntent = Intent(this, TraceListActivity::class.java)
        setResult(Activity.RESULT_CANCELED, finishIntent)
        this.finish();
    }

    private val storageServiceConnection = object: ServiceConnection {
        var disp: Disposable? = null
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
            disp = storageBinding?.storageActionChannel
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe { theStatus ->
                    when(theStatus.state){
                        StorageStatusEnum.Delete -> {
                            var item = traceList.filter { itm -> itm.Trace == theStatus.trace }.first()
                            traceList.remove(item)
                            traceAdapter.notifyDataSetChanged()
                        }
                        StorageStatusEnum.Add -> {
                                traceList.add(PaperTraceItem(theStatus.trace!!, null))
                                traceAdapter.notifyDataSetChanged()
                        }
                    }
                }
                loadTraceList()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            storageBinding = null
            disp?.dispose()
        }
    }

    private fun loadTraceList(){
        var service = storageBinding!!.getService()
        var list = service?.GetTraceList()
        traceList.clear()
        list.forEach{trace: PaperTrace ->
            traceList.add(PaperTraceItem(trace, null))
        }

        traceAdapter.notifyDataSetChanged()

    }
}