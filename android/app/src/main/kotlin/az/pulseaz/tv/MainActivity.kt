package az.pulseaz.tv

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
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
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import java.net.HttpURLConnection
import java.net.URL

private data class TvChannel(
    val name: String,
    val code: String,
    val category: String,
    val streams: List<String>,
    val logoUrl: String? = null,
    val qualityHint: String = "Canlı yayım"
)

private data class RadioStation(
    val name: String,
    val code: String,
    val genre: String,
    val streams: List<String>,
    val logoUrl: String? = null
)

class MainActivity : Activity() {
    private val bg = Color.rgb(6, 10, 22)
    private val panel = Color.rgb(15, 23, 42)
    private val panelRaised = Color.rgb(23, 34, 58)
    private val accent = Color.rgb(45, 214, 190)
    private val accentBlue = Color.rgb(73, 151, 255)
    private val textMain = Color.rgb(245, 248, 255)
    private val textMuted = Color.rgb(150, 165, 190)
    private val danger = Color.rgb(255, 115, 125)

    private lateinit var playerView: PlayerView
    private lateinit var playerFrame: FrameLayout
    private lateinit var emptyPlayerHint: TextView
    private lateinit var audioPanel: FrameLayout
    private lateinit var audioTitle: TextView
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var playPauseButton: TextView
    private lateinit var qualityButton: TextView
    private lateinit var fitButton: TextView
    private lateinit var retryButton: TextView
    private lateinit var searchBox: EditText
    private lateinit var channelList: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var tabTv: TextView
    private lateinit var tabRadio: TextView
    private lateinit var sectionLabel: TextView
    private lateinit var liveBadge: TextView

    private var player: ExoPlayer? = null
    private var selectedChannel: TvChannel? = null
    private var selectedRadio: RadioStation? = null
    private var selectedCategory = "Hamısı"
    private var selectedTab = "tv"
    private var favoritesOnly = false
    private var activeStreams: List<String> = emptyList()
    private var activeStreamIndex = 0
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private val animations = mutableListOf<Animator>()
    private val logoCache = HashMap<String, Bitmap>()
    private val loadingLogos = HashSet<String>()
    private val favorites by lazy { getSharedPreferences("pulseaz_tv", MODE_PRIVATE) }

    private val channels = listOf(
        TvChannel("AzTV", "AZTV", "Xəbər", listOf(
            "https://str.yodacdn.net/azertv/index.m3u8",
            "https://stream8.cinerama.uz/1455/tracks-v1a1/mono.m3u8"
        ), "https://www.google.com/s2/favicons?domain=aztv.az&sz=128", "Mövcud HLS variantından asılıdır"),
        TvChannel("Baku TV", "BAKU", "Xəbər", listOf(
            "https://rtmp.baku.tv/hls/bakutv.m3u8"
        ), "https://www.google.com/s2/favicons?domain=baku.tv&sz=128", "Canlı yayım"),
        TvChannel("AnewZ", "ANEWZ", "Xəbər", listOf(
            "https://53be5ef2d13aa.streamlock.net/cubesanewz-secure/smil:cubesanewz-secure-web.smil/playlist.m3u8"
        ), "https://www.google.com/s2/favicons?domain=anewz.tv&sz=128", "Canlı yayım"),
        TvChannel("İctimai TV", "ITV", "Xəbər", listOf(
            "https://live.itv.az/itv.m3u8"
        ), "https://www.google.com/s2/favicons?domain=itv.az&sz=128", "Canlı yayım"),
        TvChannel("EL TV", "ELTV", "Regional", listOf(
            "https://live.eltv.az/live/eltv/playlist.m3u8"
        ), "https://www.google.com/s2/favicons?domain=eltv.az&sz=128", "Bəzi vaxtlarda yayım olmaya bilər"),
        TvChannel("Kanal 35", "K35", "Regional", listOf(
            "https://str2.yodacdn.net/kanal35/index.m3u8"
        ), "https://www.google.com/s2/favicons?domain=kanal35.az&sz=128", "720p mənbə kimi siyahılanıb"),
        TvChannel("Kanal S", "KANALS", "Regional", listOf(
            "https://lives.atv.az:5443/KANAL-S/streams/kanals.m3u8"
        ), "https://www.google.com/s2/favicons?domain=kanals.az&sz=128", "Canlı yayım"),
        TvChannel("Mədəniyyət TV", "MEDEN", "Mədəniyyət", listOf(
            "https://str.yodacdn.net/medeniyyettele/index.m3u8"
        ), "https://www.google.com/s2/favicons?domain=aztv.az&sz=128", "1080p mənbə kimi siyahılanıb"),
        TvChannel("Naxçıvan TV", "NTV", "Regional", listOf(
            "https://str2.yodacdn.net/ntv/video.m3u8"
        ), "https://www.google.com/s2/favicons?domain=ntv.az&sz=128", "720p mənbə kimi siyahılanıb"),
        TvChannel("Xəzər TV", "XEZER", "Əyləncə", listOf(
            "https://www.xezerxeber.az/stream/index.m3u8"
        ), "https://www.google.com/s2/favicons?domain=xezerxeber.az&sz=128", "720p mənbə kimi siyahılanıb"),
        TvChannel("Ayaz TV", "AYAZ", "Musiqi", listOf(
            "https://janya-ayaztv.vgcdn.net/ptnr-WebApp/title-Ayaz_TV/v1/vglive-sk-934820/main.m3u8"
        ), "https://www.google.com/s2/favicons?domain=ayaztv.az&sz=128", "720p mənbə kimi siyahılanıb"),
        TvChannel("Vilayət TV", "VILAYET", "Regional", listOf(
            "https://nl.livekadeh.com/hls2/vilayet.m3u8"
        ), "https://www.google.com/s2/favicons?domain=livekadeh.com&sz=128", "Açıq siyahıda olan HLS")
    )

    private val radios = listOf(
        RadioStation("İctimai Radio · 90 FM", "ITV90", "Xəbər", listOf(
            "https://stream.itv.az/rtmp:/radio/playlist.m3u8"
        ), "https://www.google.com/s2/favicons?domain=itv.az&sz=128"),
        RadioStation("Azərbaycan Beynəlxalq Radiosu", "AZINT", "Milli", listOf(
            "https://str.yodacdn.net/radio_int/tracks-a1/mono.m3u8"
        ), "https://www.google.com/s2/favicons?domain=aztv.az&sz=128"),
        RadioStation("Araz FM · 103.3", "ARAZ", "Musiqi", listOf(
            "https://s14.myradiostream.com/30454/listen.mp3"
        ), "https://www.google.com/s2/favicons?domain=arazfm.az&sz=128"),
        RadioStation("ASAN Radio", "ASAN", "Xəbər", listOf(
            "https://play.asanradio.az/"
        ), "https://www.google.com/s2/favicons?domain=asanradio.az&sz=128"),
        RadioStation("Yurd FM", "YURD", "Milli", listOf(
            "https://icecast.livetv.az/yurdfm"
        ), "https://www.google.com/s2/favicons?domain=livetv.az&sz=128"),
        RadioStation("Media FM · 105.5", "MEDIA", "Musiqi", listOf(
            "https://icecast.livetv.az/mediafm"
        ), "https://www.google.com/s2/favicons?domain=livetv.az&sz=128"),
        RadioStation("Anti Radio", "ANTI", "Musiqi", listOf(
            "https://stream.antiradio.net/listen/anti_radio/mp3",
            "https://stream.antiradio.net/radio/8000/mp3"
        ), "https://www.google.com/s2/favicons?domain=antiradio.net&sz=128"),
        RadioStation("NAZ Radio", "NAZ", "Musiqi", listOf(
            "https://s2.myradiostream.com/5286/listen.mp3"
        ), "https://www.google.com/s2/favicons?domain=myradiostream.com&sz=128"),
        RadioStation("AutoLife Radio Azerbaijan", "AUTOLIFE", "Musiqi", listOf(
            "https://radio.autoliferadio.com:8005/radio"
        ), "https://www.google.com/s2/favicons?domain=autoliferadio.com&sz=128"),
        RadioStation("Caspian Business Radio BOSS", "BOSS", "Musiqi", listOf(
            "https://myradio24.org/bosscomaz"
        ), "https://www.google.com/s2/favicons?domain=boss.com.az&sz=128"),
        RadioStation("Radio Enerji · 101.1 FM", "ENERJI", "Musiqi", listOf(
            "https://icecast.livetv.az/antennfm"
        ), "https://www.google.com/s2/favicons?domain=livetv.az&sz=128"),
        RadioStation("Planet FM · 88.7", "PLANET", "Musiqi", listOf(
            "https://live.dinle.az/planetfm"
        ), "https://www.google.com/s2/favicons?domain=dinle.az&sz=128"),
        RadioStation("Xəzər FM", "XEZERFM", "Musiqi", listOf(
            "https://s40.myradiostream.com/:22546/listen.mp3?"
        ), "https://www.google.com/s2/favicons?domain=myradiostream.com&sz=128"),
        RadioStation("106.3 FM", "FM106", "Milli", listOf(
            "https://audiostr.atv.az/106.3fm"
        ), "https://www.google.com/s2/favicons?domain=atv.az&sz=128")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildScreen()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) PulseAZTV/1.1")
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()

        playerView.player = player
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        statusText.text = "Yayım yüklənir…"
                        retryButton.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        statusText.text = if (selectedRadio != null) "RADİO · CANLI" else "CANLI YAYIM HAZIRDIR"
                        retryButton.visibility = View.GONE
                    }
                    Player.STATE_ENDED -> statusText.text = "Yayım başa çatdı"
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                tryNextSource()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (::playPauseButton.isInitialized) {
                    playPauseButton.text = if (isPlaying) "Ⅱ  Dayandır" else "▶  Davam et"
                }
            }
        })
        renderItems()
        pulse(liveBadge)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(
                intArrayOf(Color.rgb(7, 13, 29), Color.rgb(8, 17, 34), Color.rgb(10, 12, 28)),
                0
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(10))
        }

        val appLogo = ImageView(this).apply {
            setImageResource(R.drawable.pulseaz_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(appLogo, LinearLayout.LayoutParams(dp(46), dp(46)))

        val brandBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandBlock.addView(label("PulseAZ TV", 22f, textMain, true))
        brandBlock.addView(label("AZƏRBAYCAN TV · RADIO", 9f, textMuted, true))
        val brandParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        brandParams.marginStart = dp(11)
        header.addView(brandBlock, brandParams)

        liveBadge = label("●  CANLI", 10f, accent, true).apply {
            background = rounded(Color.rgb(15, 50, 55), 30)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        header.addView(liveBadge)
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(3), dp(16), dp(26))
        }

        val welcome = label("Bir toxunuşla canlı yayım.", 15f, textMuted, false)
        welcome.setPadding(dp(3), 0, dp(3), dp(12))
        column.addView(welcome)

        playerFrame = FrameLayout(this).apply {
            background = gradient(intArrayOf(Color.rgb(14, 26, 48), Color.rgb(20, 30, 57)), dp(22))
            clipToOutline = true
            elevation = dp(8).toFloat()
        }

        playerView = PlayerView(this).apply {
            useController = true
            controllerAutoShow = true
            controllerShowTimeoutMs = 3500
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
        }
        playerFrame.addView(playerView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(218)
        ))

        emptyPlayerHint = label("KANAL SEÇ", 13f, textMuted, true).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        playerFrame.addView(emptyPlayerHint, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(218)
        ))

        audioPanel = FrameLayout(this).apply {
            background = gradient(intArrayOf(Color.rgb(13, 35, 54), Color.rgb(26, 25, 63)), dp(22))
            visibility = View.GONE
        }
        val audioContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        audioContent.addView(label("PULSE RADIO", 10f, accent, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
        })
        audioTitle = label("İndi dinlənilir", 18f, textMain, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(12))
        }
        audioContent.addView(audioTitle)
        val equalizer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(5) { index ->
            val bar = View(this).apply {
                background = rounded(if (index % 2 == 0) accent else accentBlue, 12)
                scaleY = 0.35f
            }
            val lp = LinearLayout.LayoutParams(dp(7), dp(36 + (index % 3) * 12))
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            equalizer.addView(bar, lp)
            val animator = ObjectAnimator.ofFloat(bar, "scaleY", 0.2f, 1f, 0.35f).apply {
                duration = (520 + index * 95).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = DecelerateInterpolator()
            }
            animations.add(animator)
        }
        audioContent.addView(equalizer)
        audioPanel.addView(audioContent, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        playerFrame.addView(audioPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(218)
        ))

        val topTag = label("PULSE  •  LIVE", 9f, accent, true).apply {
            background = rounded(Color.argb(185, 6, 15, 28), 20)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val tagParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END
        )
        tagParams.setMargins(0, dp(10), dp(10), 0)
        playerFrame.addView(topTag, tagParams)
        column.addView(playerFrame)

        titleText = label("PulseAZ-a xoş gəlmisən", 18f, textMain, true).apply {
            setPadding(dp(3), dp(13), dp(3), dp(3))
        }
        column.addView(titleText)

        statusText = label("Televiziya və ya radio seçərək başla.", 11f, textMuted, false)
        column.addView(statusText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        qualityButton = action("⚙  Keyfiyyət", panelRaised, accent).apply {
            setOnClickListener { showQualityDialog() }
        }
        controls.addView(qualityButton, weightedButtonParams())
        fitButton = action("▣  Ölçü: FIT", panelRaised, textMain).apply {
            setOnClickListener { cycleResizeMode() }
        }
        val fitParams = weightedButtonParams()
        fitParams.marginStart = dp(7)
        controls.addView(fitButton, fitParams)
        column.addView(controls)

        val playbackControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playPauseButton = action("▶  Başlat", accent, Color.rgb(5, 24, 31)).apply {
            setOnClickListener { togglePlayback() }
        }
        playbackControls.addView(playPauseButton, weightedButtonParams())
        retryButton = action("↻  Yenidən yoxla", panelRaised, textMain).apply {
            visibility = View.GONE
            setOnClickListener { retryCurrentSource() }
        }
        val retryParams = weightedButtonParams()
        retryParams.marginStart = dp(7)
        playbackControls.addView(retryButton, retryParams)
        column.addView(playbackControls)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(panel, 16)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        tabTv = label("▣  TELEVİZİYA", 11f, textMain, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(13), dp(6), dp(13))
            setOnClickListener {
                selectedTab = "tv"
                selectedCategory = "Hamısı"
                favoritesOnly = false
                renderItems()
            }
        }
        tabRadio = label("♫  RADİO", 11f, textMuted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(13), dp(6), dp(13))
            setOnClickListener {
                selectedTab = "radio"
                selectedCategory = "Hamısı"
                favoritesOnly = false
                renderItems()
            }
        }
        tabs.addView(tabTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(tabRadio, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val tabsParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tabsParams.topMargin = dp(22)
        column.addView(tabs, tabsParams)

        searchBox = EditText(this).apply {
            hint = "Kanal və ya radio axtar…"
            setHintTextColor(Color.rgb(112, 130, 158))
            setTextColor(textMain)
            textSize = 14f
            singleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(15), dp(2), dp(15), dp(2))
            background = rounded(panel, 14)
        }
        val searchParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(49)
        )
        searchParams.topMargin = dp(14)
        column.addView(searchBox, searchParams)

        val filterScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filterScroller.addView(filterRow)
        val filtersParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        )
        filtersParams.topMargin = dp(8)
        column.addView(filterScroller, filtersParams)

        sectionLabel = label("CANLI KANALLAR", 10f, textMuted, true).apply {
            letterSpacing = 0.14f
            setPadding(dp(3), dp(14), dp(3), dp(10))
        }
        column.addView(sectionLabel)

        channelList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(channelList)

        val footer = label(
            "Yayım ünvanları açıq mənbələrdən toplanır. Yayımçı linki dəyişə, yayımı söndürə və ya regiona görə məhdudlaşdıra bilər. Tətbiq yalnız birbaşa media axınını oynadır; sayt və brauzer açmır.",
            10f, textMuted, false
        ).apply {
            setPadding(dp(3), dp(21), dp(3), dp(5))
        }
        column.addView(footer)

        scroll.addView(column)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderItems()
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun renderItems() {
        if (!::channelList.isInitialized || !::filterRow.isInitialized) return
        val isTv = selectedTab == "tv"
        tabTv.background = if (isTv) rounded(Color.rgb(28, 65, 79), 12) else null
        tabRadio.background = if (!isTv) rounded(Color.rgb(28, 65, 79), 12) else null
        tabTv.setTextColor(if (isTv) accent else textMuted)
        tabRadio.setTextColor(if (!isTv) accent else textMuted)
        sectionLabel.text = if (isTv) "CANLI KANALLAR" else "RADİO STANSİYALARI"

        val categories = if (isTv) {
            listOf("Hamısı", "Xəbər", "Regional", "Əyləncə", "Mədəniyyət", "Musiqi")
        } else {
            listOf("Hamısı", "Musiqi", "Xəbər", "Milli")
        }
        renderFilters(categories)
        channelList.removeAllViews()

        val query = searchBox.text.toString().trim().lowercase()
        val saved = favorites.getStringSet("favorites", emptySet()) ?: emptySet()
        val rows: List<Pair<String, View>> = if (isTv) {
            channels.filter { item ->
                (selectedCategory == "Hamısı" || item.category == selectedCategory) &&
                    (!favoritesOnly || saved.contains("TV_" + item.code)) &&
                    (item.name.lowercase().contains(query) || item.category.lowercase().contains(query))
            }.map { item -> item.code to tvCard(item) }
        } else {
            radios.filter { item ->
                (selectedCategory == "Hamısı" || item.genre == selectedCategory) &&
                    (!favoritesOnly || saved.contains("RADIO_" + item.code)) &&
                    (item.name.lowercase().contains(query) || item.genre.lowercase().contains(query))
            }.map { item -> item.code to radioCard(item) }
        }

        if (rows.isEmpty()) {
            channelList.addView(label("Bu axtarışda uyğun nəticə yoxdur.", 13f, textMuted, false).apply {
                setPadding(dp(5), dp(22), dp(5), dp(22))
            })
        } else {
            rows.forEachIndexed { index, row ->
                val card = row.second
                channelList.addView(card)
                card.alpha = 0f
                card.translationY = dp(10).toFloat()
                card.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay((index * 28L).coerceAtMost(220L))
                    .setDuration(260)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                val spacer = View(this)
                channelList.addView(spacer, LinearLayout.LayoutParams(1, dp(8)))
            }
        }
    }

    private fun renderFilters(categories: List<String>) {
        filterRow.removeAllViews()
        categories.forEach { category ->
            val selected = selectedCategory == category
            val chip = label(category, 11f, if (selected) Color.rgb(5, 25, 33) else textMuted, true).apply {
                background = rounded(if (selected) accent else panel, 30)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                setOnClickListener {
                    selectedCategory = category
                    renderItems()
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(7)
            filterRow.addView(chip, lp)
        }
        val selected = favoritesOnly
        val fav = label(if (selected) "★ Seçilənlər" else "☆ Seçilənlər", 11f,
            if (selected) Color.rgb(5, 25, 33) else textMuted, true).apply {
            background = rounded(if (selected) accent else panel, 30)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener {
                favoritesOnly = !favoritesOnly
                renderItems()
            }
        }
        filterRow.addView(fav)
    }

    private fun tvCard(channel: TvChannel): View = mediaCard(
        channel.name, channel.category + "  ·  " + channel.qualityHint,
        channel.code, channel.logoUrl, true,
        { playChannel(channel) },
        { toggleFavorite("TV_" + channel.code) }
    )

    private fun radioCard(station: RadioStation): View = mediaCard(
        station.name, station.genre + "  ·  Audio yayımı",
        station.code, station.logoUrl, false,
        { playRadio(station) },
        { toggleFavorite("RADIO_" + station.code) }
    )

    private fun mediaCard(
        name: String,
        subtitle: String,
        code: String,
        logoUrl: String?,
        isTv: Boolean,
        onPlay: () -> Unit,
        onFavorite: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 17)
            setPadding(dp(12), dp(11), dp(10), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener { onPlay() }
        }

        val iconFrame = FrameLayout(this).apply {
            background = gradient(
                intArrayOf(Color.rgb(20, 47, 65), Color.rgb(32, 45, 76)),
                dp(14)
            )
        }
        val fallback = label(if (isTv) code.take(4) else "♫", 12f, textMain, true).apply {
            gravity = Gravity.CENTER
        }
        iconFrame.addView(fallback, FrameLayout.LayoutParams(
            dp(58), dp(58), Gravity.CENTER
        ))
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            visibility = View.GONE
        }
        iconFrame.addView(image, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        card.addView(iconFrame, LinearLayout.LayoutParams(dp(58), dp(58)))
        if (!logoUrl.isNullOrBlank()) loadLogo(logoUrl, image, fallback)

        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(label(name, 14f, textMain, true))
        details.addView(label(subtitle, 10f, textMuted, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        val infoParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        infoParams.marginStart = dp(12)
        card.addView(details, infoParams)

        val saved = favorites.getStringSet("favorites", emptySet()) ?: emptySet()
        val favKey = (if (isTv) "TV_" else "RADIO_") + code
        val favorite = label(if (saved.contains(favKey)) "★" else "☆", 22f,
            if (saved.contains(favKey)) accent else textMuted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(7), dp(5))
            setOnClickListener {
                onFavorite()
            }
        }
        card.addView(favorite, LinearLayout.LayoutParams(dp(40), dp(48)))
        return card
    }

    private fun playChannel(channel: TvChannel) {
        selectedChannel = channel
        selectedRadio = null
        titleText.text = channel.name
        audioPanel.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        qualityButton.visibility = View.VISIBLE
        emptyPlayerHint.visibility = View.GONE
        liveBadge.text = "●  TV CANLI"
        statusText.text = "Yayım bağlantısı yoxlanılır…"
        startStream(channel.streams, false)
    }

    private fun playRadio(station: RadioStation) {
        selectedRadio = station
        selectedChannel = null
        titleText.text = station.name
        audioTitle.text = station.name
        audioPanel.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        emptyPlayerHint.visibility = View.GONE
        qualityButton.visibility = View.GONE
        liveBadge.text = "●  RADIO CANLI"
        statusText.text = "Radio bağlantısı yoxlanılır…"
        startStream(station.streams, true)
    }

    private fun startStream(streams: List<String>, isAudio: Boolean) {
        activeStreams = streams
        activeStreamIndex = 0
        playStreamAtIndex(isAudio)
    }

    private fun playStreamAtIndex(isAudio: Boolean) {
        val url = activeStreams.getOrNull(activeStreamIndex) ?: run {
            showStreamError()
            return
        }
        try {
            val builder = MediaItem.Builder().setUri(Uri.parse(url))
            if (url.contains(".m3u8", ignoreCase = true)) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (isAudio) {
                builder.setMimeType(MimeTypes.AUDIO_MPEG)
            }
            player?.apply {
                stop()
                clearMediaItems()
                setMediaItem(builder.build())
                prepare()
                playWhenReady = true
            }
        } catch (_: Exception) {
            tryNextSource()
        }
    }

    private fun tryNextSource() {
        if (activeStreamIndex + 1 < activeStreams.size) {
            activeStreamIndex++
            statusText.text = "Alternativ yayım ünvanı yoxlanılır…"
            playStreamAtIndex(selectedRadio != null)
        } else {
            showStreamError()
        }
    }

    private fun showStreamError() {
        statusText.text = "Bu yayım hazırda əlçatan deyil. Başqa kanal və ya radio seç."
        statusText.setTextColor(danger)
        emptyPlayerHint.text = "YAYIM ƏLÇATAN DEYİL"
        emptyPlayerHint.visibility = if (selectedRadio == null) View.VISIBLE else View.GONE
        retryButton.visibility = View.VISIBLE
    }

    private fun retryCurrentSource() {
        val streams = activeStreams
        if (streams.isNotEmpty()) {
            activeStreamIndex = 0
            statusText.setTextColor(textMuted)
            statusText.text = "Yayım yenidən yoxlanılır…"
            emptyPlayerHint.visibility = View.GONE
            playStreamAtIndex(selectedRadio != null)
        }
    }

    private fun togglePlayback() {
        val activePlayer = player ?: return
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        } else {
            activePlayer.play()
        }
    }

    @OptIn(UnstableApi::class)
    private fun showQualityDialog() {
        val activePlayer = player ?: return
        if (selectedChannel == null) {
            Toast.makeText(this, "Keyfiyyət seçimi yalnız video üçün mövcuddur.", Toast.LENGTH_SHORT).show()
            return
        }
        val hasVideo = activePlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
        if (!hasVideo) {
            Toast.makeText(this, "Bu yayım hələ video keyfiyyətlərini təqdim etməyib.", Toast.LENGTH_SHORT).show()
            return
        }
        TrackSelectionDialogBuilder(this, "Video keyfiyyəti", activePlayer, C.TRACK_TYPE_VIDEO)
            .setAllowAdaptiveSelections(true)
            .setShowDisableOption(false)
            .build()
            .show()
    }

    private fun cycleResizeMode() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.resizeMode = resizeMode
        fitButton.text = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "▣  Ölçü: ZOOM"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "▣  Ölçü: FILL"
            else -> "▣  Ölçü: FIT"
        }
    }

    private fun toggleFavorite(key: String) {
        val saved = HashSet(favorites.getStringSet("favorites", emptySet()) ?: emptySet())
        if (saved.contains(key)) saved.remove(key) else saved.add(key)
        favorites.edit().putStringSet("favorites", saved).apply()
        renderItems()
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
                    setRequestProperty("User-Agent", "PulseAZTV/1.1 Android")
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

    private fun pulse(view: View) {
        val animator = ObjectAnimator.ofFloat(view, "alpha", 0.72f, 1f).apply {
            duration = 1250
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        animations.add(animator)
        animator.start()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }
    }

    private fun action(value: String, fill: Int, color: Int): TextView {
        return label(value, 11f, color, true).apply {
            gravity = Gravity.CENTER
            background = rounded(fill, 13)
            setPadding(dp(9), dp(13), dp(9), dp(13))
            isClickable = true
            isFocusable = true
        }
    }

    private fun weightedButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(45), 1f)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun gradient(colors: IntArray, radius: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        animations.forEach { it.cancel() }
        animations.clear()
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
