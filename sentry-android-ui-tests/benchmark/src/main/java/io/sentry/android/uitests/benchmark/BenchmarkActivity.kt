package io.sentry.android.uitests.benchmark

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/** Idling resource based on a boolean flag. */
class BooleanIdlingResource(private val name: String) : IdlingResource {

    private val isIdle = AtomicBoolean(true)

    private val isIdleLock = Object()

    private var callback: IdlingResource.ResourceCallback? = null

    fun setIdle(idling: Boolean) {
        if (!isIdle.getAndSet(idling) && idling) {
            callback?.onTransitionToIdle()
        }
    }

    override fun getName(): String = name

    override fun isIdleNow(): Boolean = synchronized(isIdleLock) { isIdle.get() }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
    }

}

/** A simple list of items, using [RecyclerView]. */
class BenchmarkActivity : AppCompatActivity() {

    companion object {

        /** The activity will set this manage when scrolling. */
        val scrollingIdlingResource = BooleanIdlingResource("benchmark-activity")

        init {
            IdlingRegistry.getInstance().register(scrollingIdlingResource)
        }
    }

    internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView = view.findViewById<ImageView>(R.id.benchmark_item_list_image)
        val textView = view.findViewById<TextView>(R.id.benchmark_item_list_text)
    }

    internal inner class Adapter : RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.benchmark_item_list, parent, false)
            return ViewHolder(view)
        }

        @Suppress("MagicNumber")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.imageView.setImageBitmap(generateBitmap())

            @SuppressLint("SetTextI18n")
            holder.textView.text = "Item $position ${"sentry ".repeat(position)}"
        }

        // Disables view recycling.
        override fun getItemViewType(position: Int): Int = position

        override fun getItemCount(): Int = 200
    }

    /**
     * Each background thread will run non-stop calculations during the benchmark. One such thread seems
     * enough to represent a busy application. This number can be increased to mimic busier applications.
     */
    private val backgroundThreadPoolSize = 1
    private val executor: ExecutorService = Executors.newFixedThreadPool(backgroundThreadPoolSize)
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_benchmark)

        findViewById<RecyclerView>(R.id.benchmark_transaction_list).apply {
            layoutManager = LinearLayoutManager(this@BenchmarkActivity)
            adapter = Adapter()
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    scrollingIdlingResource.setIdle(newState == RecyclerView.SCROLL_STATE_IDLE)
                }
            })
        }
    }

    @Suppress("MagicNumber")
    internal fun generateBitmap(): Bitmap {
        val bitmapSize = 100
        val colors = (0 until (bitmapSize * bitmapSize)).map {
            Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
        }.toIntArray()
        return Bitmap.createBitmap(colors, bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    }

    @Suppress("MagicNumber")
    override fun onResume() {
        super.onResume()
        resumed = true

        repeat(backgroundThreadPoolSize) {
            executor.execute {
                var x = 0
                for (i in 0..1_000_000_000) {
                    x += i * i
                    if (!resumed) break
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
    }
}