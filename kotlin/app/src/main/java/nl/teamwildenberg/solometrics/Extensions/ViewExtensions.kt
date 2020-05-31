package nl.teamwildenberg.solometrics.Extensions

import android.content.res.ColorStateList
import com.google.android.material.floatingactionbutton.FloatingActionButton
import nl.teamwildenberg.solometrics.R

public fun FloatingActionButton.setEnabledState(isEnabled: Boolean){
    this.isEnabled = isEnabled

       if (isEnabled){
            this.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.colorAccent, null))
        } else {
            this.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.disabled, null))
        }

}
