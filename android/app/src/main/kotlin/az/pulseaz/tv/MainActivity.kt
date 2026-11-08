package az.pulseaz.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL

private data class TvChannel(
    val name: String,
    val code: String,
    val category: String,
    val streamUrl: String?,
    val officialUrl: String,
    val logoUrl: String? = null,
    val note: String = "Açıq kataloqda tapılmış ünvan; işləkliyi şəbəkə və yayımçı tərəfindən dəyişə bilər."
)

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 13, 25)
    private val panel = Color.rgb(15, 27, 47)
    private val panel2 = Color.rgb(21, 39, 64)
    private val cyan = Color.rgb(32, 215, 241)
    private val white = Color.WHITE
    private val muted = Color.rgb(155, 174, 197)

    private lateinit var playerView: PlayerView
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var officialButton: TextView
    private lateinit var emptyPlayerHint: TextView
    private lateinit var searchBox: EditText
    private lateinit var channelList: LinearLayout
    private lateinit var filterRow: LinearLayout
    private var player: ExoPlayer? = null
    private var selectedChannel: TvChannel? = null
    private var selectedCategory = "Hamısı"
    private var favoritesOnly = false

    private val favorites by lazy {
        getSharedPreferences("pulseaz_tv", MODE_PRIVATE)
    }
    private val logoCache = HashMap<String, Bitmap>()
    private val loadingLogos = HashSet<String>()

    private val channels = listOf(
        TvChannel("AzTV", "AZ", "Xəbər", "https://stream8.cinerama.uz/1455/tracks-v1a1/mono.m3u8", "https://aztv.az/", "https://i.imgur.com/snBMMeH.png"),
        TvChannel("ATV", "ATV", "Əyləncə", "https://lives.atv.az:5443/ATV_TV_STREAM/streams/atvcanli.m3u8", "https://atv.az/"),
        TvChannel("Baku TV", "BAKU", "Xəbər", "https://rtmp.baku.tv/hls/bakutv.m3u8", "https://baku.tv/live", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3b/Baku_TV_%282018%29.png/500px-Baku_TV_%282018%29.png"),
        TvChannel("CBC Sport", "CBC", "İdman", null, "https://cbcsport.az/", "https://upload.wikimedia.org/wikipedia/az/0/04/CBC_Sport_TV_loqo.png", "Açıq siyahıda tapılan HLS geobloklu göstərildiyi üçün tətbiqə əlavə edilməyib."),
        TvChannel("EL TV", "EL", "Regional", "https://live.eltv.az/live/eltv/playlist.m3u8", "https://eltv.az/"),
        TvChannel("İctimai TV", "İTV", "Xəbər", "https://live.itv.az/itv.m3u8", "https://itv.az/news", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/ff/%C4%B0ctimai_TV_%282021-h.h.%29.svg/470px-%C4%B0ctimai_TV_%282021-h.h.%29.svg.png"),
        TvChannel("İdman TV", "İDMAN", "İdman", null, "https://aztv.az/"),
        TvChannel("Kanal 35", "35", "Regional", "https://str2.yodacdn.net/kanal35/index.m3u8", "https://kanal35.az/"),
        TvChannel("Kanal S", "KS", "Regional", "https://lives.atv.az:5443/KANAL-S/streams/kanals.m3u8", "https://kanals.az/", "https://i.imgur.com/W0JBoSQ.png"),
        TvChannel("Mədəniyyət TV", "MƏD", "Mədəniyyət", "https://str.yodacdn.net/medeniyyettele/index.m3u8", "https://aztv.az/", "https://upload.wikimedia.org/wikipedia/commons/f/fc/M%C9%99d%C9%99niyy%C9%99t_TV_logo.png"),
        TvChannel("Naxçıvan TV", "NTV", "Regional", "https://str2.yodacdn.net/ntv/video.m3u8", "https://ntv.az/"),
        TvChannel("ARB TV", "ARB", "Əyləncə", null, "https://arb.az/"),
        TvChannel("ARB 24", "24", "Xəbər", null, "https://arb24.az/"),
        TvChannel("Real TV", "REAL", "Xəbər", null, "https://www.realtv.az/"),
        TvChannel("Space TV", "SPACE", "Əyləncə", null, "https://spacetv.az/"),
        TvChannel("Xəzər TV", "XƏZƏR", "Əyləncə", "https://www.xezerxeber.az/stream/index.m3u8", "https://tvstream.az/")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildScreen()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("PulseAZTV/1.0 (Android)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
        playerView.player = player
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                statusText.text = when (playbackState) {
                    Player.STATE_BUFFERING -> "Yayım yüklənir…"
                    Player.STATE_READY -> "Canlı yayım hazırdır"
                    Player.STATE_ENDED -> "Yayım başa çatdı"
                    else -> if (selectedChannel?.streamUrl == null) "Rəsmi səhifədən izləmək mümkündür" else "Kanal seçilib"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                statusText.text = "Bu yayım hazırda açılmadı. Rəsmi səhifəni yoxla."
                emptyPlayerHint.text = "YAYIM AÇILMADI"
                emptyPlayerHint.visibility = View.VISIBLE
                officialButton.visibility = View.VISIBLE
            }
        })
        renderChannels()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(8))
        }
        val appLogo = ImageView(this).apply {
            setImageResource(R.drawable.pulseaz_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(appLogo, LinearLayout.LayoutParams(dp(44), dp(44)))
        val brandColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = text("PulseAZ TV", 21f, white, true)
        val tagline = text("AZƏRBAYCAN KANALLARI", 9f, muted, true)
        brandColumn.addView(brand)
        brandColumn.addView(tagline)
        val brandParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        brandParams.marginStart = dp(10)
        header.addView(brandColumn, brandParams)
        val livePill = text("AZ · TV", 11f, cyan, true).apply {
            background = rounded(Color.rgb(14, 55, 71), 30)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        header.addView(livePill)
        root.addView(header)

        val subtitle = text("Sevdiyin Azərbaycan kanalını seç və izlə.", 13f, muted, false)
        subtitle.setPadding(dp(20), 0, dp(20), dp(12))
        root.addView(subtitle)

        val content = ScrollView(this).apply { isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(18))
        }

        val playerFrame = FrameLayout(this).apply {
            background = rounded(Color.BLACK, 18)
            clipToOutline = true
        }
        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
        }
        playerFrame.addView(playerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(218)
        ))
        emptyPlayerHint = text("KANAL SEÇ", 12f, muted, true).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
        }
        playerFrame.addView(emptyPlayerHint, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(218)
        ))
        column.addView(playerFrame)

        titleText = text("Xoş gəlmisən!", 18f, white, true).apply {
            setPadding(dp(2), dp(13), dp(2), dp(3))
        }
        column.addView(titleText)

        statusText = text("İzləmək üçün aşağıdan kanal seç.", 12f, muted, false)
        column.addView(statusText)

        officialButton = text("Rəsmi səhifəni aç  ↗", 12f, cyan, true).apply {
            gravity = Gravity.CENTER
            background = rounded(panel2, 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            visibility = View.GONE
            setOnClickListener { selectedChannel?.let { openOfficial(it) } }
        }
        val officialParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        officialParams.topMargin = dp(8)
        column.addView(officialButton, officialParams)

        val toolsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, dp(10))
        }
        searchBox = EditText(this).apply {
            hint = "Kanal axtar…"
            setHintTextColor(Color.rgb(116, 136, 160))
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panel, 12)
        }
        toolsRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(48), 1f))
        column.addView(toolsRow)

        val filterScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filterScroller.addView(filterRow)
        column.addView(filterScroller)

        val listHeading = text("KANALLAR", 11f, muted, true).apply {
            setPadding(dp(2), dp(18), dp(2), dp(9))
        }
        column.addView(listHeading)

        channelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(channelList)

        val footer = text(
            "Yayım bağlantıları açıq kataloqlardan toplanıb. Bəzi kanallar üçün rəsmi sayt açılır; yayımlar kanal sahibinin şərtlərinə və region məhdudiyyətlərinə tabedir.",
            10f, muted, false
        ).apply {
            setPadding(dp(3), dp(18), dp(3), dp(8))
        }
        column.addView(footer)

        content.addView(column)
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderChannels()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderChannels() {
        if (!::channelList.isInitialized || !::filterRow.isInitialized) return
        renderFilters()
        channelList.removeAllViews()
        val query = if (::searchBox.isInitialized) searchBox.text.toString().trim().lowercase() else ""
        val saved = favorites.getStringSet("favorites", emptySet()) ?: emptySet()
        val visible = channels.filter { channel ->
            (selectedCategory == "Hamısı" || channel.category == selectedCategory) &&
            (!favoritesOnly || saved.contains(channel.code)) &&
            (channel.name.lowercase().contains(query) || channel.category.lowercase().contains(query))
        }
        if (visible.isEmpty()) {
            val empty = text("Bu filtrə uyğun kanal tapılmadı.", 13f, muted, false)
            empty.setPadding(dp(5), dp(20), dp(5), dp(20))
            channelList.addView(empty)
        } else {
            visible.forEach { channel ->
                channelList.addView(channelCard(channel))
                val gap = View(this)
                channelList.addView(gap, LinearLayout.LayoutParams(1, dp(8)))
            }
        }
    }

    private fun renderFilters() {
        filterRow.removeAllViews()
        val categories = listOf("Hamısı", "Xəbər", "İdman", "Əyləncə", "Mədəniyyət", "Regional")
        categories.forEach { category ->
            val chip = text(category, 12f, if (selectedCategory == category) bg else muted, true).apply {
                background = rounded(if (selectedCategory == category) cyan else panel, 30)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener {
                    selectedCategory = category
                    favoritesOnly = false
                    renderChannels()
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(7)
            filterRow.addView(chip, lp)
        }
        val favChip = text(if (favoritesOnly) "★ Sevimlilər" else "☆ Sevimlilər", 12f, if (favoritesOnly) bg else muted, true).apply {
            background = rounded(if (favoritesOnly) cyan else panel, 30)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener {
                favoritesOnly = !favoritesOnly
                selectedCategory = "Hamısı"
                renderChannels()
            }
        }
        filterRow.addView(favChip)
    }

    private fun channelCard(channel: TvChannel): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16)
            setPadding(dp(12), dp(10), dp(11), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { playChannel(channel) }
        }

        val logoFrame = FrameLayout(this).apply { background = rounded(panel2, 13) }
        val fallback = text(channel.code, 12f, white, true).apply {
            gravity = Gravity.CENTER
        }
        logoFrame.addView(fallback, FrameLayout.LayoutParams(
            dp(58), dp(58), Gravity.CENTER
        ))
        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        logoFrame.addView(logo, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        card.addView(logoFrame, LinearLayout.LayoutParams(dp(58), dp(58)))
        channel.logoUrl?.let { loadLogo(it, logo, fallback) }

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val channelName = text(channel.name, 15f, white, true)
        val meta = text(
            if (channel.streamUrl == null) "Rəsmi səhifə ilə izlənir" else "HLS yayım linki · sınaq tələb edir",
            10f, muted, false
        )
        info.addView(channelName)
        info.addView(meta)
        val infoLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        infoLp.marginStart = dp(12)
        card.addView(info, infoLp)

        val saved = favorites.getStringSet("favorites", emptySet()) ?: emptySet()
        val star = text(if (saved.contains(channel.code)) "★" else "☆", 23f, if (saved.contains(channel.code)) cyan else muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(7), dp(5))
            setOnClickListener {
                val updated = HashSet(favorites.getStringSet("favorites", emptySet()) ?: emptySet())
                if (updated.contains(channel.code)) updated.remove(channel.code) else updated.add(channel.code)
                favorites.edit().putStringSet("favorites", updated).apply()
                renderChannels()
            }
        }
        card.addView(star, LinearLayout.LayoutParams(dp(42), dp(48)))
        return card
    }

    private fun playChannel(channel: TvChannel) {
        selectedChannel = channel
        titleText.text = channel.name
        officialButton.visibility = View.VISIBLE
        officialButton.text = "Rəsmi səhifəni aç  ↗"
        if (channel.streamUrl.isNullOrBlank()) {
            emptyPlayerHint.text = "BİRBAŞA HLS YOXDUR"
            emptyPlayerHint.visibility = View.VISIBLE
            statusText.text = "Birbaşa HLS ünvanı əlavə edilməyib. Rəsmi yayım səhifəsini aça bilərsən."
            player?.pause()
            return
        }
        statusText.text = "Yayım bağlantısı yoxlanılır…"
        emptyPlayerHint.visibility = View.GONE
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(channel.streamUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
        } catch (e: Exception) {
            statusText.text = "Yayım başlatmaq mümkün olmadı. Rəsmi səhifəni yoxla."
        }
    }

    private fun openOfficial(channel: TvChannel) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channel.officialUrl)))
        } catch (_: Exception) {
            statusText.text = "Rəsmi səhifə açıla bilmədi."
        }
    }

    private fun loadLogo(url: String, image: ImageView, fallback: TextView) {
        synchronized(logoCache) {
            val cached = logoCache[url]
            if (cached != null) {
                image.setImageBitmap(cached)
                image.visibility = View.VISIBLE
                fallback.visibility = View.GONE
                return
            }
            if (!loadingLogos.add(url)) return
        }
        Thread {
            var connection: HttpURLConnection? = null
            var bitmap: Bitmap? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "PulseAZTV/1.0")
                }
                connection.inputStream.use { bitmap = BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                bitmap = null
            } finally {
                connection?.disconnect()
                synchronized(logoCache) {
                    loadingLogos.remove(url)
                    if (bitmap != null) logoCache[url] = bitmap!!
                }
            }
            bitmap?.let { loaded ->
                image.post {
                    image.setImageBitmap(loaded)
                    image.visibility = View.VISIBLE
                    fallback.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
