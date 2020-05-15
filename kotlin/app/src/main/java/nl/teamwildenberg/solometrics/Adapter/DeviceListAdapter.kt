package nl.teamwildenberg.SoloMetrics.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import nl.teamwildenberg.SoloMetrics.Ble.BlueDevice
import nl.teamwildenberg.SoloMetrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.SoloMetrics.R

public class DeviceListAdapter(private val context: Context,
                               private val dataSource: MutableList<BlueDevice>) : BaseAdapter() {
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    //view holder is used to prevent findViewById calls
    private class DeviceListItemViewHolder {
        internal var image: ImageView? = null
        internal var title: TextView? = null
        internal var description: TextView? = null
        internal var hours: TextView? = null
    }

    //1
    override fun getCount(): Int {
        return dataSource.size
    }

    //2
    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    //3
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    //4
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val device = dataSource[position]
        val viewHolder: DeviceListItemViewHolder

        // Get view for row item
        if (view == null) {
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(R.layout.list_item, parent, false)

            viewHolder = DeviceListItemViewHolder()
            viewHolder.title = view!!.findViewById<View>(R.id.title) as TextView
            viewHolder.description = view.findViewById<View>(R.id.description) as TextView
            viewHolder.image = view.findViewById<View>(R.id.image) as ImageView
        } else {
            //no need to call findViewById, can use existing ones from saved view holder
            viewHolder = view.tag as DeviceListItemViewHolder
        }

        viewHolder.title!!.text = device.name
        viewHolder.description!!.text = device.address
        if (device.type == DeviceTypeEnum.Ultrasonic) {
            viewHolder.image!!.setImageResource(R.drawable.ic_nature_black_24dp)
        }
        else{
            viewHolder.image!!.setImageResource(R.drawable.ic_web_black_24dp)
        }
        view.tag = viewHolder
        return view
    }

    fun add(item: BlueDevice) {
        dataSource.add(item)
        notifyDataSetChanged()
    }
}