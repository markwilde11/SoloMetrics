package nl.teamwildenberg.solometrics.Extensions


public fun Number.toStringKey(): String{
    return this.toString().padStart(4,'0')
}
