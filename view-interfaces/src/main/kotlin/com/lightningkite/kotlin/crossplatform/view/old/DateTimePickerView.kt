package com.lightningkite.kotlin.crossplatform.view.old

import lk.kotlin.observable.property.MutableObservableProperty
import java.util.*

interface DateTimePickerView : View {
    class Style : View.Style()
    companion object {
        val DefaultStyle = Style()
    }


    val date: MutableObservableProperty<Date>
}