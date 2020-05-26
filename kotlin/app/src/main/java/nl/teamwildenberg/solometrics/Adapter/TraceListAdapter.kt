package nl.teamwildenberg.solometrics.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import nl.teamwildenberg.solometrics.Ble.DeviceTypeEnum
import nl.teamwildenberg.solometrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.R
import nl.teamwildenberg.solometrics.Service.PaperTrace
import java.time.Instant

public class TraceListAdapter(private val context: Context,
                              private val dataSource: MutableList<PaperTrace>) : BaseAdapter() {
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    //view holder is used to prevent findViewById calls
    private class TraceListItemViewHolder {
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
        val trace = dataSource[position]
        val viewHolder: TraceListItemViewHolder

        // Get view for row item
        if (view == null) {
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(R.layout.list_item, parent, false)

            viewHolder = TraceListItemViewHolder()
            viewHolder.title = view!!.findViewById<View>(R.id.title) as TextView
            viewHolder.description = view.findViewById<View>(R.id.description) as TextView
            viewHolder.image = view.findViewById<View>(R.id.image) as ImageView
        } else {
            //no need to call findViewById, can use existing ones from saved view holder
            viewHolder = view.tag as TraceListItemViewHolder
        }

        viewHolder.title!!.text = trace.key.toStringKey()
        viewHolder.description!!.text = "Trace started  ${Instant.now().epochSecond - trace.epoch} seconds ago"
        viewHolder.image!!.setImageResource(R.drawable.ic_near_me_black_24dp)
        view.tag = viewHolder
        return view
    }

    fun add(item: PaperTrace) {
        dataSource.add(item)
        notifyDataSetChanged()
    }
}