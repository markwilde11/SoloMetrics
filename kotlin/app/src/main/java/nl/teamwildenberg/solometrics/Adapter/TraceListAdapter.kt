package nl.teamwildenberg.solometrics.Adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import nl.teamwildenberg.solometrics.Extensions.toStringKey
import nl.teamwildenberg.solometrics.R
import java.time.Instant

public class TraceListAdapter(private val context: Context,
                              private val dataSource: MutableList<PaperTraceItem>) : BaseExpandableListAdapter() {
    private val inflater: LayoutInflater
            = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    //view holder is used to prevent findViewById calls
    private class TraceListItemViewHolder {
        internal var image: ImageView? = null
        internal var title: TextView? = null
        internal var description: TextView? = null
        internal var hours: TextView? = null
    }
    //view holder is used to prevent findViewById calls
    private class PartitionListViewHolder {
        internal var sequence: TextView? = null
        internal var title: TextView? = null
    }


    //1
    override fun getGroupCount(): Int {
        return dataSource.size
    }


    //2
    override fun getGroup(position: Int): Any {
        return dataSource[position]
    }

    //3
    override fun getGroupId(position: Int): Long {
        return position.toLong()
    }

    override fun getChildId(parent: Int, child: Int): Long {
        return child.toLong()
    }

    override fun getChild(parent: Int, child: Int): Any? {
        return null
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return false
    }

    override fun getChildrenCount(parent: Int): Int {
        return (if (dataSource[parent].PartionList == null){
            0
        } else {
            return dataSource[parent].PartionList!!.size
        })
    }

    override fun hasStableIds(): Boolean {
        return false
    }


    //4
    override fun getGroupView(position: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val item = dataSource[position]
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

        viewHolder.title!!.text = item.Trace.key.toStringKey()
        viewHolder.description!!.text = "Trace started  ${Instant.now().epochSecond - item.Trace.epoch} seconds ago"
        viewHolder.image!!.setImageResource(R.drawable.ic_near_me_black_24dp)
        view.tag = viewHolder
        return view
    }

    override fun getChildView(
        parent: Int,
        child: Int,
        isLastChild: Boolean,
        convertView: View?,
        parentview: ViewGroup?
    ): View? {
        var view = convertView
        val item = dataSource[parent].PartionList?.get(child)
        val viewHolder: PartitionListViewHolder

        if (view == null) {
            val inflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.inflate_xml_groupview, parentview, false)
            viewHolder = PartitionListViewHolder()
            viewHolder.sequence = view.findViewById<View>(R.id.sequence) as TextView
            viewHolder.title = view.findViewById<View>(R.id.title) as TextView
        }else{
            viewHolder = view.tag as PartitionListViewHolder
        }

        viewHolder.sequence!!.text = "#${child}"
        viewHolder.title!!.text = "Number of measurements - ${item!!.size}"
        view!!.tag = viewHolder
//        val child_textvew =
//            convertView!!.findViewById<View>(R.id.inflate_xml_groupview) as TextView
//        child_textvew.text = getChild(parent, child).toString()
        return view
    }

    fun add(item: PaperTraceItem) {
        dataSource.add(item)
        notifyDataSetChanged()
    }
}