package lk.kotlin.crossplatform.android

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.support.design.widget.TabLayout
import android.support.design.widget.TextInputEditText
import android.support.design.widget.TextInputLayout
import android.support.v4.text.TextUtilsCompat
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.view.menu.MenuBuilder
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.InputType.*
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.lightningkite.kotlin.crossplatform.view.*
import lk.android.animations.AnimationSet
import lk.android.animations.SwapView
import lk.android.lifecycle.lifecycle
import lk.kotlin.crossplatform.view.*
import lk.kotlin.crossplatform.view.Gravity
import lk.kotlin.lifecycle.LifecycleConnectable
import lk.kotlin.lifecycle.LifecycleListener
import lk.kotlin.observable.list.ObservableList
import lk.kotlin.observable.list.ObservableListListenerSet
import lk.kotlin.observable.list.addListenerSet
import lk.kotlin.observable.list.lifecycle.bind
import lk.kotlin.observable.list.removeListenerSet
import lk.kotlin.observable.property.*
import lk.kotlin.observable.property.lifecycle.bind
import java.text.DateFormat
import java.util.*


class MaterialViewFactory(val context: Context) : ViewFactory<View> {

    val dpScale = context.resources.displayMetrics.density
    fun dip(amount: Int) = (amount * dpScale).toInt()
    fun dip(amount: Float) = (amount * dpScale).toInt()
    fun undip(amount: Int) = (amount / dpScale).toInt()
    fun undip(amount: Float) = (amount / dpScale).toInt()
    fun Animation.toAndroid() = when (this) {
        Animation.Push -> AnimationSet.slidePush
        Animation.Pop -> AnimationSet.slidePop
        Animation.MoveUp -> AnimationSet.slideUp
        Animation.MoveDown -> AnimationSet.slideDown
        Animation.Fade -> AnimationSet.fade
        Animation.Flip -> AnimationSet.flipVertical
    }

    fun ViewGroup.LayoutParams.absorb(gravity: Gravity) {
        when (gravity) {
            Gravity.TopLeft,
            Gravity.TopCenter,
            Gravity.TopRight,
            Gravity.TopStart,
            Gravity.TopEnd,
            Gravity.CenterLeft,
            Gravity.Center,
            Gravity.CenterRight,
            Gravity.CenterStart,
            Gravity.CenterEnd,
            Gravity.BottomLeft,
            Gravity.BottomCenter,
            Gravity.BottomRight,
            Gravity.BottomStart,
            Gravity.BottomEnd -> {
                width = WRAP_CONTENT; height = WRAP_CONTENT
            }

            Gravity.CenterFill,
            Gravity.TopFill,
            Gravity.BottomFill -> {
                width = MATCH_PARENT; height = WRAP_CONTENT
            }

            Gravity.FillLeft,
            Gravity.FillCenter,
            Gravity.FillRight,
            Gravity.FillStart,
            Gravity.FillEnd -> {
                width = WRAP_CONTENT; height = MATCH_PARENT
            }

            Gravity.Fill -> {
                width = MATCH_PARENT; height = MATCH_PARENT
            }
        }
    }

    fun Gravity.toAndroidManual(): Int {
        return when (horizontal) {
            HorizontalGravity.Left -> android.view.Gravity.LEFT
            HorizontalGravity.Center -> android.view.Gravity.CENTER_HORIZONTAL
            HorizontalGravity.Fill -> android.view.Gravity.CENTER_HORIZONTAL
            HorizontalGravity.Right -> android.view.Gravity.RIGHT
            HorizontalGravity.Start -> android.view.Gravity.START
            HorizontalGravity.End -> android.view.Gravity.END
            else -> 0
        } or when (vertical) {
            VerticalGravity.Top -> android.view.Gravity.TOP
            VerticalGravity.Center -> android.view.Gravity.CENTER_VERTICAL
            VerticalGravity.Fill -> android.view.Gravity.CENTER_VERTICAL
            VerticalGravity.Bottom -> android.view.Gravity.BOTTOM
            else -> 0
        }
    }

    val gravityMap = Gravity.values().associate { it to it.toAndroidManual() }
    fun Gravity.toAndroid() = gravityMap[this]!!

    open class ListRecyclerViewAdapter<T>(
            val context: Context,
            val list:ObservableList<T>,
            val lifecycle:LifecycleConnectable,
            val itemToType: (T)->Int,
            val makeView: (Int, ItemObservable<T>) -> View
    ) : RecyclerView.Adapter<ListRecyclerViewAdapter.ViewHolder<T>>() {

        var onScrollToBottom: (() -> Unit)? = null
        init{
            lifecycle.connect(object : LifecycleListener{
                val set = ObservableListListenerSet(
                        onAddListener = { item: T, position: Int ->
                            notifyItemInserted(position)
                        },
                        onRemoveListener = { item: T, position: Int ->
                            notifyItemRemoved(position)
                        },
                        onChangeListener = { oldItem: T, item: T, position: Int ->
                            notifyItemChanged(position)
                        },
                        onMoveListener = { item: T, oldPosition: Int, position: Int ->
                            notifyItemMoved(oldPosition, position)
                        },
                        onReplaceListener = { list: ObservableList<T> ->
                            notifyDataSetChanged()
                        }
                )

                override fun onStart() {
                    list.addListenerSet(set)
                    notifyDataSetChanged()
                }

                override fun onStop() {
                    list.removeListenerSet(set)
                }
            })
        }

        override fun getItemViewType(position: Int): Int = itemToType(list[position])

        override fun getItemCount(): Int = list.size

        var default: T? = null
        var shouldSetDefault = true
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<T> {
            if (shouldSetDefault) {
                default = list.first()
                shouldSetDefault = false
            }
            val observable = ItemObservable(this)
            itemObservables.add(observable)
            val newView = makeView.invoke(viewType, observable)
            val holder = ViewHolder(newView, observable)
            observable.viewHolder = holder
            itemHolders.add(holder)
            holder.view.lifecycle.setAlwaysOnRecursive() //Necessary because Android is broke
            return holder
        }

        override fun onBindViewHolder(holder: ViewHolder<T>, position: Int) {
            if (itemCount > 0 && position + 1 == itemCount) {
                onScrollToBottom?.invoke()
            }
            if (list.isNotEmpty()) {
                holder.observable.apply {
                    this.backupPosition = position
                    update()
                }
            }
        }

        private val itemHolders = ArrayList<ViewHolder<T>>()
        private val itemObservables = ArrayList<ItemObservable<T>>()

        class ItemObservable<T>(val parent: ListRecyclerViewAdapter<T>) : BaseObservableProperty<T>() {
            var viewHolder: ViewHolder<T>? = null
            val position get() = viewHolder?.adapterPosition?.takeUnless { it == -1 } ?: backupPosition
            var backupPosition: Int = 0

            @Suppress("UNCHECKED_CAST")
            override var value: T
                get() {
                    if (position >= 0 && position < parent.list.size) {
                        return parent.list[position]
                    } else return parent.default as T
                }
                set(value) {
                    if (position < 0 || position >= parent.list.size) return
                    val list = parent.list as? MutableList<T> ?: throw IllegalAccessException()
                    list[position] = value
                    update()
                }

            override fun update() {
                if (position >= 0 && position < parent.list.size) {
                    super.update()
                }
            }
        }

        class ViewHolder<T>(val view: View, val observable: ItemObservable<T>) : RecyclerView.ViewHolder(view) {
            init {
                adapterPosition
            }
        }
    }







    override fun window(
            stack: StackObservableProperty<() -> View>,
            tabs: List<Pair<TabItem, () -> View>>,
            actions: ObservableList<Pair<TabItem, () -> Unit>>
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL

        val currentView = stack.withAnimations().transform { it.first.invoke() to it.second }

        //Top bar
        addView(Toolbar(context).apply {
            lifecycle.bind(actions) {
                menu.clear()
                it.forEachIndexed { index, pair ->
                    menu.add(0, index, Menu.NONE, pair.first.text).apply {
                        //TODO: Icon
                        setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
//                        setIcon()
                    }
                }
            }
            lifecycle.bind(currentView) {
                title = it.toString()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        //Main
        addView(
                swap(currentView),
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        )

        //Tabs
        addView(TabLayout(context).apply {

            for (tab in tabs) {
                addTab(newTab().apply {
                    //TODO: set image
                    text = tab.first.text
                })
            }

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabReselected(tab: TabLayout.Tab) {
                    stack.reset(tabs[tab.position].second)
                }

                override fun onTabSelected(tab: TabLayout.Tab) {
                    stack.reset(tabs[tab.position].second)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}
            })

        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    override fun pages(vararg pageGenerator: () -> View): View = FrameLayout(context).apply {
        addView(ViewPager(context).apply {
            adapter = object : PagerAdapter(){

                val viewsGeneratedBy = WeakHashMap<View, ()->View>()

                override fun isViewFromObject(view: View, generator: Any): Boolean = viewsGeneratedBy[view] == generator

                override fun getCount(): Int = pageGenerator.size

                override fun instantiateItem(container: ViewGroup, position: Int): Any {
                    val view = pageGenerator[position].invoke()
                    viewsGeneratedBy[view] = pageGenerator[position]
                    container.addView(view)
                    return view
                }

                override fun destroyItem(container: ViewGroup, position: Int, view: Any) {
                    container.removeView(view as View)
                }

                override fun getPageTitle(position: Int): CharSequence? = viewsGeneratedBy.entries.find { it.value == pageGenerator[position] }?.key?.toString()
            }
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    override fun tabs(options: ObservableList<TabItem>, selected: MutableObservableProperty<TabItem>): View = TabLayout(context).apply {

        lifecycle.bind(options){
            this.removeAllTabs()
            for(tab in it){
                addTab(newTab().apply {
                    //TODO: set image
                    text = tab.text
                })
            }
        }

        addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {
                selected.value = options[tab.position]
            }

            override fun onTabSelected(tab: TabLayout.Tab) {
                selected.value = options[tab.position]
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
        })
    }

    override fun <T> list(
            data: ObservableList<T>,
            onBottom: () -> Unit,
            itemToType: (T) -> Int,
            makeView: (type: Int, obs: ObservableProperty<T>) -> View
    ): View = LayoutInflater.from(context).inflate(R.layout.vertical_recycler_view_scrollbar, null).let{ it as RecyclerView }.apply{
        adapter = ListRecyclerViewAdapter(
                context = context,
                list = data,
                lifecycle = lifecycle,
                itemToType = itemToType,
                makeView = makeView
        )
    }

    override fun <T> grid(
            minItemSize: Float,
            data: ObservableList<T>,
            onBottom: () -> Unit,
            itemToType: (T) -> Int,
            makeView: (type: Int, obs: ObservableProperty<T>) -> View
    ): View = LayoutInflater.from(context).inflate(R.layout.vertical_recycler_view_scrollbar, null).let{ it as RecyclerView }.apply{
        layoutManager = GridLayoutManager(context, (undip(resources.displayMetrics.widthPixels) / minItemSize).toInt().coerceAtLeast(1)).apply {
            this.orientation = GridLayoutManager.VERTICAL
        }
        adapter = ListRecyclerViewAdapter(
                context = context,
                list = data,
                lifecycle = lifecycle,
                itemToType = itemToType,
                makeView = makeView
        )
    }

    override fun header(text: ObservableProperty<String>): View = TextView(
            context,
            null,
            R.attr.headerStyle
    ).apply {
        this.lifecycle.bind(text) {
            this.text = it
        }
    }

    override fun subheader(text: ObservableProperty<String>): View = TextView(
            context,
            null,
            R.attr.subheaderStyle
    ).apply {
        this.lifecycle.bind(text) {
            this.text = it
        }
    }

    override fun body(text: ObservableProperty<String>): View = TextView(
            context,
            null,
            R.attr.bodyStyle
    ).apply {
        this.lifecycle.bind(text) {
            this.text = it
        }
    }

    override fun work(): View = ProgressBar(context)

    override fun progress(observable: ObservableProperty<Float>): View = ProgressBar(context).apply {
        lifecycle.bind(observable) {
            this.max = 10000
            this.progress = (it * 10000).toInt()
        }
    }

    override fun image(minSize: Point, image: ObservableProperty<Image>): View = ImageView(context).apply {
        minimumWidth = dip(minSize.x)
        minimumHeight = dip(minSize.y)
        //TODO: Image
    }

    override fun button(
            image: ObservableProperty<Image?>,
            label: ObservableProperty<String?>,
            onClick: () -> Unit
    ): View = Button(context).apply {
        lifecycle.bind(label) {
            text = it
        }
        lifecycle.bind(image) {
            if (it == null)
                setCompoundDrawables(null, null, null, null)
            else
                setCompoundDrawables(null, null, null, null)
        }
        setOnClickListener { onClick() }
    }

    override fun <T> picker(
            options: ObservableList<T>,
            selected: MutableObservableProperty<T>,
            makeView: (obs: ObservableProperty<T>) -> View
    ): View = Spinner(context).apply {
        val futureAdapter = object : BaseAdapter() {

            val observables = WeakHashMap<View, StandardObservableProperty<T>>()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView?.also {
                    observables[it]?.value = options[position]
                } ?: run {
                    val observable = StandardObservableProperty(options[position])
                    val view = makeView.invoke(observable)
                    observables[view] = observable
                    view
                }

                return view
            }

            override fun getItem(position: Int): Any? = options[position]

            override fun getItemId(position: Int): Long = position.toLong()

            override fun getCount(): Int = options.size
        }
        lifecycle.bind(options) {
            futureAdapter.notifyDataSetChanged()
            //fix selected index
            val newIndex = options.indexOf(selected.value)
            if (newIndex != -1) {
                setSelection(newIndex)
            } else {
                setSelection(0)
            }
        }
        lifecycle.bind(selected) {
            val newIndex = options.indexOf(it)
            if (newIndex == this.selectedItemPosition) return@bind
            if (newIndex != -1) {
                setSelection(newIndex)
            } else {
                setSelection(0)
            }
        }
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newValue = options[position]
                if (selected.value == newValue) return
                selected.value = newValue
            }
        }
        adapter = futureAdapter
    }

    override fun field(
            image: Image,
            hint: String,
            help: String,
            type: InputType,
            error: ObservableProperty<String>,
            text: MutableObservableProperty<String>
    ): View = TextInputLayout(context).apply {
        lifecycle.bind(error) {
            this.error = it
        }
        this.hint = hint
        addView(TextInputEditText(context).apply {
            setText(text.value)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {}
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (text.value != s) {
                        text.value = (s.toString())
                    }
                }
            })
            lifecycle.bind(text) {
                if (text.value != text.toString()) {
                    this.setText(text.value)
                }
            }

            //TODO: Images
            setCompoundDrawables(null, null, null, null)

            inputType = when (type) {
                InputType.Paragraph -> TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_SENTENCES or TYPE_TEXT_FLAG_MULTI_LINE
                InputType.Name -> TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_WORDS or TYPE_TEXT_VARIATION_PERSON_NAME
                InputType.Password -> TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD
                InputType.Sentence -> TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_SENTENCES
                InputType.CapitalizedIdentifier -> TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_CHARACTERS
                InputType.Email -> TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                InputType.URL -> TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_URI
                InputType.Phone -> TYPE_CLASS_PHONE
                InputType.Address -> TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_POSTAL_ADDRESS or TYPE_TEXT_FLAG_CAP_WORDS
                InputType.Integer -> TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_SIGNED
                InputType.Float -> TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_DECIMAL or TYPE_NUMBER_FLAG_SIGNED
                InputType.PositiveInteger -> TYPE_CLASS_NUMBER
                InputType.PositiveFloat -> TYPE_CLASS_NUMBER or TYPE_NUMBER_FLAG_DECIMAL
            }

        })
    }

    private fun showDatePickerDialog(observable: MutableObservableProperty<Date>, onComplete: () -> Unit = {}) {
        val start = Calendar.getInstance().apply { time = observable.value }
        DatePickerDialog(
                context,
                DatePickerDialog.OnDateSetListener { view, year, monthOfYear, dayOfMonth ->
                    start.set(Calendar.YEAR, year)
                    start.set(Calendar.MONTH, monthOfYear)
                    start.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    observable.value = start.time
                    onComplete.invoke()
                },
                start.get(Calendar.YEAR),
                start.get(Calendar.MONTH),
                start.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePickerDialog(observable: MutableObservableProperty<Date>, onComplete: () -> Unit = {}) {
        val start = Calendar.getInstance().apply { time = observable.value }
        TimePickerDialog(
                context,
                TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
                    start.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    start.set(Calendar.MINUTE, minute)
                    observable.value = start.time
                    onComplete.invoke()
                },
                start.get(Calendar.HOUR_OF_DAY),
                start.get(Calendar.MINUTE),
                false
        ).show()
    }

    override fun datePicker(observable: MutableObservableProperty<Date>): View = button(
            label = observable.transform { DateFormat.getDateInstance().format(it) },
            onClick = {
                showDatePickerDialog(observable)
            }
    )

    override fun dateTimePicker(observable: MutableObservableProperty<Date>): View = button(
            label = observable.transform { DateFormat.getDateTimeInstance().format(it) },
            onClick = {
                showDatePickerDialog(observable) {
                    showTimePickerDialog(observable)
                }
            }
    )

    override fun timePicker(observable: MutableObservableProperty<Date>): View = button(
            label = observable.transform { DateFormat.getDateTimeInstance().format(it) },
            onClick = {
                showTimePickerDialog(observable)
            }
    )

    override fun slider(steps: Int, observable: MutableObservableProperty<Float>): View = SeekBar(context).apply {
        max = steps
        val range = 0f..1f
        lifecycle.bind(observable) {
            val newProg = ((it - range.start) / (range.endInclusive - range.start) * steps).toInt()
            if (this.progress != newProg) {
                this.progress = newProg
            }
        }
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newValue = ((progress.toDouble() / steps) * (range.endInclusive - range.start) + range.start).toFloat()
                    if (observable.value != newValue) {
                        observable.value = newValue
                    }
                }
            }
        })
    }

    override fun toggle(observable: MutableObservableProperty<Boolean>): View = CheckBox(context).apply {
        this.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked != observable.value) {
                observable.value = (isChecked)
            }
        }
        lifecycle.bind(observable) {
            val value = observable.value
            if (isChecked != value) {
                isChecked = value
            }
        }
    }

    override fun refresh(
            contains: View,
            working: ObservableProperty<Boolean>,
            onRefresh: () -> Unit
    ): View = SwipeRefreshLayout(context).apply {
        addView(contains)
        lifecycle.bind(working) {
            this.isRefreshing = it
        }
        setOnRefreshListener(onRefresh)
    }

    override fun scroll(view: View): View = ScrollView(context).apply {
        addView(view)
    }

    override fun margin(left: Float, top: Float, right: Float, bottom: Float, view: View): View = view.apply {
        val params = layoutParams as? ViewGroup.MarginLayoutParams
                ?: ViewGroup.MarginLayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        params.setMargins(dip(left), dip(top), dip(right), dip(bottom))
    }

    override fun swap(view: ObservableProperty<Pair<View, Animation>>): View = SwapView(context).apply {
        lifecycle.bind(view) {
            swap(it.first, it.second.toAndroid())
        }
    }

    override fun horizontal(spacing: Float, vararg views: Pair<Gravity, View>): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val (leftSubjective, rightSubjective) = if (TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_LTR)
            HorizontalGravity.Left to HorizontalGravity.Right
        else
            HorizontalGravity.Right to HorizontalGravity.Left
        val reordered = views.filter { it.first.horizontal == HorizontalGravity.Left } +
                views.filter { it.first.horizontal == leftSubjective } +
                views.filter { it.first.horizontal == HorizontalGravity.Center } +
                views.filter { it.first.horizontal == HorizontalGravity.Fill } +
                views.filter { it.first.horizontal == rightSubjective } +
                views.filter { it.first.horizontal == HorizontalGravity.Right }
        for (item in reordered) {
            item.second.layoutParams = LinearLayout.LayoutParams(item.second.layoutParams).apply {
                absorb(item.first)
                gravity = item.first.toAndroid()
            }
            addView(item.second)
        }
    }

    override fun vertical(spacing: Float, vararg views: Pair<Gravity, View>): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val reordered = views.filter { it.first.vertical == VerticalGravity.Top } +
                views.filter { it.first.vertical == VerticalGravity.Center } +
                views.filter { it.first.vertical == VerticalGravity.Fill } +
                views.filter { it.first.vertical == VerticalGravity.Bottom }
        for (item in reordered) {
            item.second.layoutParams = LinearLayout.LayoutParams(item.second.layoutParams).apply {
                absorb(item.first)
                gravity = item.first.toAndroid()
            }
            addView(item.second)
        }
    }

    override fun frame(vararg views: Pair<Gravity, View>): View = FrameLayout(context).apply {
        for (item in views) {
            item.second.layoutParams = FrameLayout.LayoutParams(item.second.layoutParams).apply {
                absorb(item.first)
                gravity = item.first.toAndroid()
            }
            addView(item.second)
        }
    }

    override fun codeLayout(vararg views: Pair<(ArrayList<Rectangle>) -> Rectangle, View>): View = TODO()


}
