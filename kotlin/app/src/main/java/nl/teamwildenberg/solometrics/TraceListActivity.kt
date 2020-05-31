package nl.teamwildenberg.solometrics

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ExpandableListView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.fab
import kotlinx.android.synthetic.main.activity_main.fabLayout1
import kotlinx.android.synthetic.main.activity_main.fabLayout2
import kotlinx.android.synthetic.main.activity_main.fabLayout3
import kotlinx.android.synthetic.main.trace_list_activity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import nl.teamwildenberg.solometrics.Adapter.PaperTraceItem
import nl.teamwildenberg.solometrics.Adapter.TraceListAdapter
import nl.teamwildenberg.solometrics.Ble.BlueDevice
import nl.teamwildenberg.solometrics.Extensions.setEnabledState
import nl.teamwildenberg.solometrics.Service.PaperTrace
import nl.teamwildenberg.solometrics.Service.StorageService
import nl.teamwildenberg.solometrics.Service.WindMeasurement


class TraceListActivity : ActivityBase(), CoroutineScope by MainScope() {
    private var storageBinding: StorageService.LocalBinder? = null
    var traceList: MutableList<PaperTraceItem> = mutableListOf()
    lateinit var traceAdapter: TraceListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.trace_list_activity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        DeleteTraceListButton.setEnabledState(false)
        SaveTraceListButton.setEnabledState(false)

        traceAdapter = TraceListAdapter(this, traceList)
        traceListView.setAdapter(traceAdapter)

        traceListView.setOnGroupExpandListener { groupPosition: Int ->
            var service = storageBinding?.getService()
            var selectedItem = traceListView.getItemAtPosition(groupPosition) as PaperTraceItem

            DeleteTraceListButton.setEnabledState(true)
            SaveTraceListButton.setEnabledState(true)

            if (service != null){
                if (selectedItem.PartionList == null) {
                    var partionList = service.GetPartionList(selectedItem.Trace)
                    selectedItem.PartionList = partionList
                    traceAdapter.notifyDataSetChanged()
                }}

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

        StartTraceListButton.setOnClickListener{
            var storageServiceIntent = Intent(this, StorageService::class.java)
            if (storageBinding != null){
                var service = storageBinding!!.getService()
                if (service.activeTrace == null) {
                    storageServiceIntent.putExtra("action", "start")
                    startService(storageServiceIntent)
                }
                else{
                    stopService(storageServiceIntent)
                }
            }
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
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            storageBinding = service as StorageService.LocalBinder
            loadTraceList()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            storageBinding = null
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