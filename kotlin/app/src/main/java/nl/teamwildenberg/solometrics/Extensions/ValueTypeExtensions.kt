package nl.teamwildenberg.solometrics.Extensions

import java.text.SimpleDateFormat
import java.util.*


public fun Number.toStringKey(): String{
    return this.toString().padStart(4,'0')
}

public fun Long.toDateString():String{
    val sdf: SimpleDateFormat = SimpleDateFormat("yyyyMMdd")
    return sdf.format(Date(this))
}