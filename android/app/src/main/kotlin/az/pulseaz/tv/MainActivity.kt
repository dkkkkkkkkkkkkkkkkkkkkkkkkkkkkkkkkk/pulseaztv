package az.pulseaz.tv

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.util.Xml
import android.util.Rational
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.caverock.androidsvg.SVG
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private data class TvChannel(
    val name: String,
    val code: String,
    val category: String,
    val streams: List<String>,
    val logoUrl: String? = null,
    val qualityHint: String = "Canlı yayım",
    val pages: List<String> = emptyList(),
    val guideUrl: String? = null,
    val logoFile: String? = null
)

private data class RadioStation(
    val name: String,
    val code: String,
    val genre: String,
    val streams: List<String>,
    val logoUrl: String? = null,
    val pages: List<String> = emptyList(),
    val trackUrl: String? = null,
    val logoFile: String? = null
)

private data class TvProgram(
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val description: String
)

class MainActivity : Activity() {
    private val bg = Color.rgb(5, 9, 19)
    private val panel = Color.rgb(13, 21, 38)
    private val panelRaised = Color.rgb(21, 32, 54)
    private val accent = Color.rgb(0, 226, 190)
    private val accentBlue = Color.rgb(91, 145, 255)
    private val textMain = Color.rgb(246, 249, 255)
    private val textMuted = Color.rgb(151, 169, 197)
    private val danger = Color.rgb(255, 112, 132)

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
    private lateinit var muteButton: TextView
    private lateinit var volumeValueText: TextView
    private lateinit var guideButton: TextView
    private lateinit var volumeSlider: SeekBar
    private lateinit var searchBox: EditText
    private lateinit var channelList: LinearLayout
    private lateinit var filterRow: LinearLayout
    private lateinit var tabTv: TextView
    private lateinit var tabRadio: TextView
    private lateinit var sectionLabel: TextView
    private lateinit var liveBadge: TextView

    private var player: ExoPlayer? = null
    private var playbackService: PulsePlaybackService? = null
    private var serviceBound = false
    private var playerListener: Player.Listener? = null
    private var selectedChannel: TvChannel? = null
    private var selectedRadio: RadioStation? = null
    private var selectedCategory = "Hamısı"
    private var selectedTab = "tv"
    private var favoritesOnly = false
    private var recentOnly = false
    private var activeStreams: List<String> = emptyList()
    private var activeStreamIndex = 0
    private var pageResolutionAttempted = 0
    private var pageResolutionInProgress = false
    private var streamReferer: String? = null
    private var streamRequestHeaders: Map<String, String> = emptyMap()
    private var lastPlaybackError: String? = null
    private var lastNonMutedVolume = 1f
    private var yodaTokenCache: String? = null
    private var yodaTokenExpiresAt = 0L
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private val animations = mutableListOf<Animator>()
    private val audioAnimations = mutableListOf<Animator>()
    private val logoCache = HashMap<String, Bitmap>()
    private val svgIconCache = HashMap<String, Bitmap>()
    private val loadingLogos = HashSet<String>()

    // Only inspect publicly reachable pages for openly exposed HTTPS HLS media.
    // Provider pages are probed invisibly; only public media files are passed to native Media3.
    // The user can also choose a provider manually from the Sources panel.
    private val resolverPages = mapOf(
        "AZTV" to listOf(
            "https://yodaplayer.yodacdn.net/aztvpop/index.php",
            "https://aztv.az/az",
            "https://operativmm.az/en/canli-tv/aztv",
            "https://tvstream.az/"
        ),
        "MEDEN" to listOf(
            "https://yodaplayer.yodacdn.net/medeniyyetpop/index.php",
            "https://www.medeniyyettv.az/az",
            "https://operativmm.az/en/canli-tv/medeniyyet"
        ),
        "NTV" to listOf(
            "https://live.ntv.az/",
            "https://yodaplayer.yodacdn.net/ntvpop/index.php",
            "https://www.ntv.az/",
            "https://operativmm.az/en/canli-tv/naxcivan-tv"
        ),
        "ANEWZ" to listOf(
            "https://cdn.maksnet.tv/em/cubesanewz-secure/?abr-app=web&streamname=live",
            "https://anewz.tv/tv-live",
            "https://operativmm.az/az/canli-tv/anewz"
        ),
        "XEZER" to listOf(
            "https://xezerxeber.az/en/live",
            "https://www.xezerxeber.az/stream/index.m3u8",
            "https://operativmm.az/en/canli-tv/xezer-tv",
            "https://tvstream.az/"
        )
    )
    private var resolverWebView: WebView? = null
    private var resolverGeneration = 0
    private var resolverTimeout: Runnable? = null
    private var resolverProbeRunnable: Runnable? = null
    private val resolverHandler = Handler(Looper.getMainLooper())
    private val favorites by lazy { getSharedPreferences("pulseaz_tv", MODE_PRIVATE) }
    private lateinit var channels: List<TvChannel>
    private lateinit var radios: List<RadioStation>

    private fun loadCatalog() {
        val tv = mutableListOf<TvChannel>()
        val radio = mutableListOf<RadioStation>()
        assets.open("channel_catalog.psv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val fields = line.split('|', limit = 9)
                if (fields.size < 10) return@forEach
                val kind = fields[0].trim()
                val id = fields[1].trim()
                val rawName = fields[2].trim()
                val name = when (id) {
                    "azertv" -> "AzTV"
                    "ntv" -> "Naxçıvan TV"
                    "medeniyyettele" -> "Mədəniyyət TV"
                    "ictimaitele" -> "İctimai TV"
                    "xazartv" -> "Xəzər TV"
                    else -> rawName
                }
                val category = fields[3].trim().ifBlank { "Milli" }
                val streams = fields[4].split('~').map { it.trim() }
                    .filter { it.startsWith("https://", true) }
                    .distinct()
                val pages = fields[5].split('~').map { it.trim() }
                    .filter { it.startsWith("https://", true) }
                    .distinct()
                val guide = fields[6].trim().takeIf { it.startsWith("https://", true) }
                val trackUrl = fields[7].trim().takeIf { it.startsWith("https://", true) }
                val logoDomain = fields[8].trim().takeIf { it.isNotBlank() }
                val logoFile = fields[9].trim().takeIf { it.isNotBlank() }
                val code = when (id) {
                    "azertv" -> "AZTV"
                    "ntv" -> "NTV"
                    "medeniyyettele" -> "MEDEN"
                    "anewz" -> "ANEWZ"
                    "xazartv" -> "XEZER"
                    "ictimaitele" -> "ITV"
                    "bakutv" -> "BAKU"
                    "eltv" -> "ELTV"
                    "o-kanals" -> "KANALS"
                    "o-ayaz" -> "AYAZ"
                    "o-vilayet" -> "VILAYET"
                    "idmantele" -> "IDMAN"
                    else -> id.uppercase(Locale.ROOT).replace('-', '_')
                }
                val logoUrl = when (code) {
                    "AZTV" -> "https://upload.wikimedia.org/wikipedia/commons/4/4b/AzTV_%282019%29.png"
                    "MEDEN" -> "https://upload.wikimedia.org/wikipedia/commons/2/25/M%C9%99d%C9%99niyy%C9%99t_TV_%282019%29.png"
                    "NTV" -> "https://upload.wikimedia.org/wikipedia/commons/f/fa/Nax%C3%A7%C4%B1van_TV_%282025%29.png"
                    "ANEWZ" -> "https://anewz.tv/files-redizajn/img/logo-text.svg"
                    "XEZER" -> "https://tvstream.az/assets/xezertv.jpg"
                    else -> logoDomain?.let { "https://www.google.com/s2/favicons?domain=" + it + "&sz=256" }
                }
                if (kind == "tv") {
                    tv.add(TvChannel(
                        name = name,
                        code = code,
                        category = category,
                        streams = streams,
                        logoUrl = logoUrl,
                        qualityHint = if (streams.isEmpty()) "Canlı player" else "Canlı yayım",
                        pages = pages,
                        guideUrl = guide,
                        logoFile = logoFile
                    ))
                } else if (kind == "radio") {
                    radio.add(RadioStation(
                        name = name,
                        code = code,
                        genre = category,
                        streams = streams,
                        logoUrl = logoUrl,
                        pages = pages,
                        trackUrl = trackUrl,
                        logoFile = logoFile
                    ))
                }
            }
        }
        channels = tv.distinctBy { it.code }
        radios = radio.distinctBy { it.code }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        loadCatalog()
        buildScreen()

        bindPlaybackService()
        renderItems()
        pulse(liveBadge)
    }

    private val playbackConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? PulsePlaybackService.LocalBinder)?.getService() ?: return
            playbackService = service
            player = service.player
            configurePlayer(player ?: return)
            playerView.player = player
            attachPlayerListener(player ?: return)

            // A channel may have been tapped while the service was still starting.
            selectedChannel?.let { startStream(it.streams, false) }
                ?: selectedRadio?.let { startStream(it.streams, true) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerView.player = null
            player = null
            playbackService = null
        }
    }

    private fun bindPlaybackService() {
        val intent = Intent(this, PulsePlaybackService::class.java)
        serviceBound = bindService(intent, playbackConnection, Context.BIND_AUTO_CREATE)
    }

    private fun configurePlayer(activePlayer: ExoPlayer) {
        activePlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        activePlayer.trackSelectionParameters = activePlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(3840, 2160)
            .build()
    }

    private fun attachPlayerListener(activePlayer: ExoPlayer) {
        playerListener?.let { activePlayer.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        statusText.text = "Yayım yüklənir…"
                        retryButton.visibility = View.GONE
                    }
                    Player.STATE_READY -> {
                        statusText.text = if (selectedRadio != null) "RADİO · CANLI" else "CANLI YAYIM HAZIRDIR"
                        retryButton.visibility = View.GONE
                        lastPlaybackError = null
                        rememberSuccessfulStream()
                    }
                    Player.STATE_ENDED -> statusText.text = "Yayım başa çatdı"
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                lastPlaybackError = playbackFailureLabel(error)
                tryNextSource()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (::playPauseButton.isInitialized) {
                    playPauseButton.text = if (isPlaying) "Dayandır" else "Davam et"
                    applySvgIcon(playPauseButton, if (isPlaying) "pause" else "play",
                        if (isPlaying) Color.rgb(5, 24, 31) else accent)
                }
                selectedRadio?.let { station ->
                    playbackService?.updateRadioNotification(station.name, isPlaying)
                    if (!isPlaying) stopService(Intent(this@MainActivity, PulsePlaybackService::class.java))
                }
            }
        }
        playerListener = listener
        activePlayer.addListener(listener)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(
                intArrayOf(Color.rgb(5, 9, 19), Color.rgb(8, 18, 35), Color.rgb(13, 13, 34)),
                0
            )
        }
        animateAmbientBackground(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(10))
        }

        val appLogo = ImageView(this).apply {
            setImageResource(R.drawable.pulseaz_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(appLogo, LinearLayout.LayoutParams(dp(46), dp(46)))
        val logoFloat = ObjectAnimator.ofFloat(appLogo, "translationY", 0f, dp(-2).toFloat(), 0f).apply {
            duration = 3600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        animations.add(logoFloat)
        logoFloat.start()

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

        val welcome = label("Canlı TV və radio — bir toxunuşla.", 15f, textMuted, false)
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
            audioAnimations.add(animator)
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
        qualityButton = action("Keyfiyyət", panelRaised, accent).apply {
            applySvgIcon(this, "settings", accent)
            setOnClickListener { showQualityDialog() }
        }
        controls.addView(qualityButton, weightedButtonParams())
        fitButton = action("Ölçü: FIT", panelRaised, textMain).apply {
            applySvgIcon(this, "fit", textMain)
            setOnClickListener { cycleResizeMode() }
        }
        val fitParams = weightedButtonParams()
        fitParams.marginStart = dp(7)
        controls.addView(fitButton, fitParams)
        column.addView(controls)

        guideButton = action("Proqram bələdçisi", panelRaised, accentBlue).apply {
            visibility = View.GONE
            setOnClickListener { showProgramGuide() }
        }
        val guideParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ).apply { topMargin = dp(6) }
        column.addView(guideButton, guideParams)

        val volumeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(9))
        }
        volumeRow.addView(label("SƏS", 10f, textMuted, true), LinearLayout.LayoutParams(dp(34), dp(42)))
        val volumeDown = action("−", panelRaised, textMain).apply {
            setOnClickListener { setPlayerVolume((player?.volume ?: 1f) - 0.1f) }
        }
        volumeRow.addView(volumeDown, LinearLayout.LayoutParams(dp(34), dp(40)))
        volumeSlider = SeekBar(this).apply {
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) setPlayerVolume(progress / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val sliderParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
            marginStart = dp(5)
            marginEnd = dp(5)
        }
        volumeRow.addView(volumeSlider, sliderParams)
        val volumeUp = action("+", panelRaised, textMain).apply {
            setOnClickListener { setPlayerVolume((player?.volume ?: 1f) + 0.1f) }
        }
        volumeRow.addView(volumeUp, LinearLayout.LayoutParams(dp(34), dp(40)))
        muteButton = action("Mute", panelRaised, accent).apply {
            setOnClickListener { toggleMute() }
        }
        val muteParams = LinearLayout.LayoutParams(dp(58), dp(40)).apply { marginStart = dp(6) }
        volumeRow.addView(muteButton, muteParams)
        volumeValueText = label("100%", 10f, textMuted, true).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        val volumeValueParams = LinearLayout.LayoutParams(dp(39), dp(40)).apply {
            marginStart = dp(4)
        }
        volumeRow.addView(volumeValueText, volumeValueParams)
        column.addView(volumeRow)

        val playbackControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        playPauseButton = action("Başlat", accent, Color.rgb(5, 24, 31)).apply {
            applySvgIcon(this, "play", Color.rgb(5, 24, 31))
            setOnClickListener { togglePlayback() }
        }
        playbackControls.addView(playPauseButton, weightedButtonParams())
        retryButton = action("Yenidən yoxla", panelRaised, textMain).apply {
            applySvgIcon(this, "refresh", textMain)
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
        tabTv = label("TELEVİZİYA", 11f, textMain, true).apply {
            gravity = Gravity.CENTER
            applySvgIcon(this, "tv", accent)
            setPadding(dp(6), dp(13), dp(6), dp(13))
            setOnClickListener {
                selectedTab = "tv"
                selectedCategory = "Hamısı"
                favoritesOnly = false
                renderItems()
            }
        }
        tabRadio = label("RADİO", 11f, textMuted, true).apply {
            gravity = Gravity.CENTER
            applySvgIcon(this, "radio", accent)
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
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(15), dp(2), dp(15), dp(2))
            background = rounded(panel, 14)
        }
        applySvgIcon(searchBox, "search", textMuted, 18)
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
            listOf("Hamısı", "Xəbər", "Regional", "Əyləncə", "Mədəniyyət", "Musiqi", "İdman", "Biznes")
        } else {
            listOf("Hamısı", "Musiqi", "Xəbər", "Milli")
        }
        renderFilters(categories)
        channelList.removeAllViews()

        val query = searchBox.text.toString().trim().lowercase()
        val saved = favorites.getStringSet("favorites", emptySet()) ?: emptySet()
        val recent = favorites.getString("recent_order", "").orEmpty().split('|').filter { it.isNotBlank() }.toSet()
        val rows: List<Pair<String, View>> = if (isTv) {
            channels.filter { item ->
                (selectedCategory == "Hamısı" || item.category == selectedCategory) &&
                    (!favoritesOnly || saved.contains("TV_" + item.code)) &&
                    (!recentOnly || recent.contains("TV_" + item.code)) &&
                    (item.name.lowercase().contains(query) || item.category.lowercase().contains(query))
            }.map { item -> item.code to tvCard(item) }
        } else {
            radios.filter { item ->
                (selectedCategory == "Hamısı" || item.genre == selectedCategory) &&
                    (!favoritesOnly || saved.contains("RADIO_" + item.code)) &&
                    (!recentOnly || recent.contains("RADIO_" + item.code)) &&
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
                    favoritesOnly = false
                    recentOnly = false
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
        val fav = label("Seçilənlər", 11f,
            if (selected) Color.rgb(5, 25, 33) else textMuted, true).apply {
            background = rounded(if (selected) accent else panel, 30)
            applySvgIcon(this, "heart", if (selected) Color.rgb(5, 25, 33) else textMuted, 15)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener {
                favoritesOnly = !favoritesOnly
                renderItems()
            }
        }
        filterRow.addView(fav)

        val recentSelected = recentOnly
        val recentChip = label("Son baxılanlar", 11f,
            if (recentSelected) Color.rgb(5, 25, 33) else textMuted, true).apply {
            background = rounded(if (recentSelected) accent else panel, 30)
            applySvgIcon(this, "history", if (recentSelected) Color.rgb(5, 25, 33) else textMuted, 15)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener {
                recentOnly = !recentOnly
                favoritesOnly = false
                renderItems()
            }
        }
        filterRow.addView(recentChip)
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
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(14, 25, 44), Color.rgb(18, 29, 51))
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.argb(75, 91, 145, 185))
            }
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
        val fallback = label(if (isTv) code.take(4) else "RADIO", 10f, textMain, true).apply {
            gravity = Gravity.CENTER
        }
        if (!isTv) applySvgIcon(fallback, "radio", textMain, 16)
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
        val favorite = label("", 18f,
            if (saved.contains(favKey)) accent else textMuted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(7), dp(5))
            applySvgIcon(this, "heart", if (saved.contains(favKey)) accent else textMuted, 20)
            setOnClickListener { onFavorite() }
        }
        card.addView(favorite, LinearLayout.LayoutParams(dp(40), dp(48)))
        return card
    }

    private fun setPlayerVolume(requested: Float) {
        val volume = requested.coerceIn(0f, 1f)
        player?.volume = volume
        if (volume > 0f) lastNonMutedVolume = volume
        if (::volumeSlider.isInitialized && volumeSlider.progress != (volume * 100f).toInt()) {
            volumeSlider.progress = (volume * 100f).toInt()
        }
        if (::volumeValueText.isInitialized) volumeValueText.text = (volume * 100f).toInt().toString() + "%"
        if (::muteButton.isInitialized) {
            muteButton.text = if (volume <= 0f) "Səsi aç" else "Mute"
            muteButton.setTextColor(if (volume <= 0f) accentBlue else accent)
        }
    }

    private fun toggleMute() {
        val current = player?.volume ?: 1f
        if (current > 0f) {
            lastNonMutedVolume = current
            setPlayerVolume(0f)
        } else {
            setPlayerVolume(lastNonMutedVolume.coerceIn(0.1f, 1f))
        }
    }

    private fun parseXmlTvDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val patterns = listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = format.parse(value.trim())
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try the next XMLTV date pattern.
            }
        }
        return 0L
    }

    private fun parseProgramGuide(input: java.io.InputStream): List<TvProgram> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val result = mutableListOf<TvProgram>()
        var start = 0L
        var stop = 0L
        var title = ""
        var description = ""
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        start = parseXmlTvDate(parser.getAttributeValue(null, "start"))
                        stop = parseXmlTvDate(parser.getAttributeValue(null, "stop"))
                        title = ""
                        description = ""
                    }
                    "title" -> title = runCatching { parser.nextText().trim() }.getOrDefault(title)
                    "desc" -> description = runCatching { parser.nextText().trim() }.getOrDefault(description)
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    if (start > 0L && title.isNotBlank()) {
                        result.add(TvProgram(start, stop, title, description))
                    }
                }
            }
            if (result.size >= 2000) break
        }
        return result
    }

    private fun showProgramGuide() {
        val channel = selectedChannel ?: return
        val guideUrl = channel.guideUrl ?: return
        statusText.text = "Proqram cədvəli yüklənir…"
        Thread {
            val connection = runCatching {
                (URL(guideUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PulseAZTV/1.7 (Android)")
                    setRequestProperty("Accept", "application/xml,text/xml,*/*")
                }
            }.getOrNull()
            val programs = try {
                if (connection == null || connection.responseCode !in 200..299) {
                    emptyList()
                } else {
                    connection.inputStream.use { parseProgramGuide(it) }
                }
            } catch (_: Exception) {
                emptyList()
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || selectedChannel?.code != channel.code) return@runOnUiThread
                statusText.text = "CANLI YAYIM · " + channel.name
                if (programs.isEmpty()) {
                    Toast.makeText(this, "Bu kanal üçün proqram cədvəli hazırda əlçatan deyil.", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val cutoff = System.currentTimeMillis() - 15L * 24L * 60L * 60L * 1000L
                val visible = programs.filter { it.stopMillis == 0L || it.stopMillis >= cutoff }
                    .sortedBy { it.startMillis }
                    .take(80)
                val timeFormat = SimpleDateFormat("dd.MM  HH:mm", Locale.getDefault())
                val labels = visible.map {
                    timeFormat.format(Date(it.startMillis)) + "  ·  " + it.title
                }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(channel.name + " · Proqramlar")
                    .setItems(labels) { _, index ->
                        val selected = visible[index]
                        val detail = buildString {
                            append(selected.title)
                            append("\n\n")
                            append(timeFormat.format(Date(selected.startMillis)))
                            if (selected.stopMillis > selected.startMillis) {
                                append(" – ")
                                append(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(selected.stopMillis)))
                            }
                            if (selected.description.isNotBlank()) {
                                append("\n\n")
                                append(selected.description)
                            }
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Proqram məlumatı")
                            .setMessage(detail)
                            .setPositiveButton("Bağla", null)
                            .show()
                    }
                    .setNegativeButton("Bağla", null)
                    .show()
            }
        }.start()
    }

    private fun playChannel(channel: TvChannel) {
        audioAnimations.forEach { it.cancel() }
        statusText.setTextColor(textMuted)
        selectedChannel = channel
        selectedRadio = null
        recentOnly = false
        favoritesOnly = false
        titleText.text = channel.name
        audioPanel.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        qualityButton.visibility = View.VISIBLE
        emptyPlayerHint.visibility = View.GONE
        liveBadge.text = "●  TV CANLI"
        guideButton.visibility = if (channel.guideUrl.isNullOrBlank()) View.GONE else View.VISIBLE
        rememberRecent("TV_" + channel.code)
        playbackService?.updateRadioNotification(channel.name, false)
        stopService(Intent(this, PulsePlaybackService::class.java))
        statusText.text = "Yayım bağlantısı yoxlanılır…"
        startStream(channel.streams, false)
    }

    private fun startRadioForeground(station: String) {
        val intent = Intent(this, PulsePlaybackService::class.java)
            .putExtra(PulsePlaybackService.EXTRA_STATION, station)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun playRadio(station: RadioStation) {
        statusText.setTextColor(textMuted)
        audioAnimations.forEach { it.start() }
        selectedRadio = station
        selectedChannel = null
        recentOnly = false
        favoritesOnly = false
        titleText.text = station.name
        audioTitle.text = station.name
        loadRadioTrack(station)
        audioPanel.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        emptyPlayerHint.visibility = View.GONE
        qualityButton.visibility = View.GONE
        liveBadge.text = "●  RADIO CANLI"
        guideButton.visibility = View.GONE
        rememberRecent("RADIO_" + station.code)
        startRadioForeground(station.name)
        statusText.text = "Radio bağlantısı yoxlanılır…"
        startStream(station.streams, true)
    }

    private fun loadRadioTrack(station: RadioStation) {
        val metadataUrl = station.trackUrl ?: return
        Thread {
            var artist: String? = null
            var title: String? = null
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(metadataUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 6500
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PulseAZTV/1.7 (Android)")
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = org.json.JSONTokener(body).nextValue()
                    fun findValue(value: Any?, keys: Set<String>, depth: Int): String? {
                        if (depth > 8 || value == null) return null
                        when (value) {
                            is org.json.JSONObject -> {
                                for (key in keys) {
                                    val candidate = value.opt(key)
                                    if (candidate is String && candidate.isNotBlank()) return candidate
                                }
                                val iterator = value.keys()
                                while (iterator.hasNext()) {
                                    val found = findValue(value.opt(iterator.next()), keys, depth + 1)
                                    if (!found.isNullOrBlank()) return found
                                }
                            }
                            is org.json.JSONArray -> {
                                for (index in 0 until value.length()) {
                                    val found = findValue(value.opt(index), keys, depth + 1)
                                    if (!found.isNullOrBlank()) return found
                                }
                            }
                        }
                        return null
                    }
                    artist = findValue(root, setOf("artist", "performer", "author"), 0)
                    title = findValue(root, setOf("current_track", "currentTrack", "track_title", "title", "song"), 0)
                }
            } catch (_: Exception) {
                // Track metadata is optional; audio playback continues without it.
            } finally {
                connection?.disconnect()
            }
            val display = when {
                !artist.isNullOrBlank() && !title.isNullOrBlank() -> artist + " — " + title
                !title.isNullOrBlank() -> title
                else -> null
            }
            if (!display.isNullOrBlank()) {
                runOnUiThread {
                    if (selectedRadio?.code == station.code && !isFinishing && !isDestroyed) {
                        audioTitle.text = display
                    }
                }
            }
        }.start()
    }

    private fun startStream(streams: List<String>, isAudio: Boolean) {
        cancelStreamResolver()
        val candidates = streams.distinct()
        val preferenceKey = selectedChannel?.let { "TV_" + it.code }
            ?: selectedRadio?.let { "RADIO_" + it.code }
        val preferred = preferenceKey?.let { favorites.getString("last_stream_" + it, null) }
        activeStreams = if (!preferred.isNullOrBlank()) {
            listOf(preferred) + candidates.filterNot { it == preferred }
        } else {
            candidates
        }
        activeStreamIndex = 0
        pageResolutionAttempted = 0
        pageResolutionInProgress = false
        streamReferer = null
        streamRequestHeaders = emptyMap()
        lastPlaybackError = null
        playStreamAtIndex(isAudio)
    }

    private fun rememberSuccessfulStream() {
        val url = activeStreams.getOrNull(activeStreamIndex) ?: return
        val query = runCatching { Uri.parse(url).queryParameterNames }.getOrNull().orEmpty()
        val isTemporary = query.any {
            val key = it.lowercase()
            key.contains("token") || key.contains("expires") || key.contains("signature") ||
                key == "sig" || key == "auth" || key == "key"
        }
        if (isTemporary) return
        val key = selectedChannel?.let { "TV_" + it.code }
            ?: selectedRadio?.let { "RADIO_" + it.code }
            ?: return
        favorites.edit().putString("last_stream_" + key, url).apply()
    }

    private fun playbackFailureLabel(error: PlaybackException): String {
        var cause: Throwable? = error
        repeat(8) {
            val httpError = cause as? HttpDataSource.InvalidResponseCodeException
            if (httpError != null) {
                return when (httpError.responseCode) {
                    401, 403 -> "server girişə icazə vermədi (" + httpError.responseCode + ")"
                    404, 410 -> "yayım ünvanı tapılmadı (" + httpError.responseCode + ")"
                    in 500..599 -> "yayım serverində xəta (" + httpError.responseCode + ")"
                    else -> "HTTP " + httpError.responseCode
                }
            }
            cause = cause?.cause
        }
        return error.errorCodeName.replace('_', ' ').lowercase()
    }

    private fun fetchYodaToken(callback: (String?) -> Unit) {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val cached = yodaTokenCache
        if (!cached.isNullOrBlank() && yodaTokenExpiresAt > nowSeconds + 10L) {
            callback(cached)
            return
        }
        Thread {
            var fetched: String? = null
            var expiry = 0L
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://yoda.az/stream-token.php").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 9000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PulseAZTV/1.7 (Android)")
                    setRequestProperty("Accept", "application/json")
                }
                if (connection.responseCode in 200..299) {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = org.json.JSONObject(body)
                    fetched = json.optString("token").takeIf { it.isNotBlank() && it != "null" }
                    expiry = json.optLong("exp", 0L)
                    if (expiry > 1000000000000L) expiry /= 1000L
                    if (expiry <= 0L) expiry = System.currentTimeMillis() / 1000L + 120L
                }
            } catch (_: Exception) {
                fetched = null
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                if (!fetched.isNullOrBlank()) {
                    yodaTokenCache = fetched
                    yodaTokenExpiresAt = expiry
                }
                callback(fetched)
            }
        }.start()
    }

    private fun appendYodaToken(url: String, token: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            if (!uri.host.orEmpty().endsWith("yodacdn.net", ignoreCase = true) ||
                !uri.getQueryParameter("token").isNullOrBlank()) {
                url
            } else {
                uri.buildUpon().appendQueryParameter("token", token).build().toString()
            }
        }.getOrDefault(url)
    }

    private fun playStreamAtIndex(
        isAudio: Boolean,
        allowTokenFetch: Boolean = true,
        overrideUrl: String? = null
    ) {
        val url = overrideUrl ?: activeStreams.getOrNull(activeStreamIndex) ?: run {
            tryNextSource()
            return
        }
        val parsedUrl = runCatching { Uri.parse(url) }.getOrNull()
        val isYodaStream = parsedUrl?.host?.endsWith("yodacdn.net", ignoreCase = true) == true
        val hasYodaToken = parsedUrl?.getQueryParameter("token").isNullOrBlank().not()
        if (allowTokenFetch && isYodaStream && !hasYodaToken) {
            val requestCode = selectedChannel?.code ?: selectedRadio?.code
            val requestIndex = activeStreamIndex
            fetchYodaToken { token ->
                val stillSelected = selectedChannel?.code == requestCode || selectedRadio?.code == requestCode
                if (!stillSelected || activeStreamIndex != requestIndex) return@fetchYodaToken
                val taggedUrl = if (token.isNullOrBlank()) url else appendYodaToken(url, token)
                playStreamAtIndex(isAudio, false, taggedUrl)
            }
            return
        }

        if (player == null) {
            statusText.text = "Pleyer hazırlanır…"
            return
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            tryNextSource()
            return
        }

        try {
            val builder = MediaItem.Builder().setUri(Uri.parse(url))
            val mediaPath = Uri.parse(url).path.orEmpty().lowercase()
            when {
                mediaPath.endsWith(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                mediaPath.endsWith(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
                mediaPath.endsWith(".mp3") || isAudio -> builder.setMimeType(MimeTypes.AUDIO_MPEG)
            }

            val referer = streamRequestHeaders["Referer"] ?: streamReferer ?: when (selectedChannel?.code) {
                "AZTV" -> "https://yodaplayer.yodacdn.net/aztvpop/index.php"
                "MEDEN" -> "https://yodaplayer.yodacdn.net/medeniyyetpop/index.php"
                "NTV" -> "https://yodaplayer.yodacdn.net/ntvpop/index.php"
                "XEZER" -> "https://xezerxeber.az/en/live"
                "ANEWZ" -> "https://cdn.maksnet.tv/em/cubesanewz-secure/?abr-app=web&streamname=live"
                else -> null
            }
            val headers = mutableMapOf(
                "Accept" to (streamRequestHeaders["Accept"] ?: "*/*"),
                "Accept-Language" to (streamRequestHeaders["Accept-Language"] ?: "az,en-US;q=0.9,en;q=0.8")
            )
            streamRequestHeaders.forEach { (name, value) ->
                if (name.equals("Origin", true) || name.equals("Referer", true)) headers[name] = value
            }
            if (!referer.isNullOrBlank()) {
                headers["Referer"] = referer
                val origin = streamRequestHeaders["Origin"] ?: runCatching {
                    val parsed = URL(referer)
                    parsed.protocol + "://" + parsed.host
                }.getOrNull()
                if (!origin.isNullOrBlank()) headers["Origin"] = origin
            }

            val userAgent = streamRequestHeaders["User-Agent"]
                ?: "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36 PulseAZTV/1.4"
            val sourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(22000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)

            val source = DefaultMediaSourceFactory(sourceFactory).createMediaSource(builder.build())
            player?.apply {
                stop()
                clearMediaItems()
                setMediaSource(source)
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
            streamReferer = null
            streamRequestHeaders = emptyMap()
            statusText.text = "Alternativ yayım mənbəyi yoxlanılır…"
            playStreamAtIndex(selectedRadio != null)
            return
        }

        val code = selectedChannel?.code ?: selectedRadio?.code ?: return showStreamError()
        val pages = (selectedChannel?.pages ?: selectedRadio?.pages ?: emptyList()) +
            resolverPages[code].orEmpty()
        if (pageResolutionInProgress) return
        val page = pages.getOrNull(pageResolutionAttempted)
        if (page == null) {
            showStreamError()
            return
        }

        pageResolutionAttempted++
        pageResolutionInProgress = true
        statusText.setTextColor(textMuted)
        statusText.text = "Alternativ saytda açıq video yayımı axtarılır…"
        resolveHlsFromPage(page) { streamUrl, referer ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val stillSelected = selectedChannel?.code == code || selectedRadio?.code == code
                if (!stillSelected) return@runOnUiThread
                pageResolutionInProgress = false
                if (!streamUrl.isNullOrBlank()) {
                    streamReferer = referer ?: page
                    activeStreams = activeStreams + streamUrl
                    activeStreamIndex = activeStreams.lastIndex
                    statusText.text = "Tapılan yayım player-ə qoşulur…"
                    playStreamAtIndex(selectedRadio != null)
                } else {
                    tryNextSource()
                }
            }
        }
    }

    private fun resolveHlsFromPage(pageUrl: String, callback: (String?, String?) -> Unit) {
        cancelStreamResolver()
        val generation = ++resolverGeneration
        val completed = AtomicBoolean(false)
        streamRequestHeaders = emptyMap()
        val webView = WebView(this)
        resolverWebView = webView
        var finish: ((String?) -> Unit)? = null

        val aliases = when (selectedChannel?.code) {
            "AZTV" -> listOf("aztv", "aztv hd", "azerbaijan television")
            "MEDEN" -> listOf("mədəniyyət", "medeniyyet", "medeniyyet tv")
            "NTV" -> listOf("naxçıvan tv", "naxcivan tv", "ntv")
            "ANEWZ" -> listOf("anewz")
            "XEZER" -> listOf("xəzər tv", "xezer tv", "xezer")
            else -> emptyList()
        }
        val aliasesJson = org.json.JSONArray(aliases).toString()
        val probeScript = """
            (function() {
              var desiredNames = $aliasesJson;
              function norm(s) {
                return String(s || '').toLowerCase()
                  .replace(/ə/g,'e').replace(/ı/g,'i').replace(/ç/g,'c')
                  .replace(/ş/g,'s').replace(/ö/g,'o').replace(/ü/g,'u')
                  .replace(/ğ/g,'g').replace(/[^a-z0-9]+/g,' ').trim();
              }
              // Some channel detail pages link onward to the broadcaster's official live player.
              try {
                if (String(location.hostname || '').indexOf('operativmm.az') >= 0 &&
                    !window.__pulseazOfficialLinkClicked) {
                  var links = document.querySelectorAll('a,button,[role="button"]');
                  for (var k=0; k<links.length; k++) {
                    var linkText = norm((links[k].innerText || '') + ' ' +
                      (links[k].getAttribute('aria-label') || '') + ' ' +
                      (links[k].getAttribute('title') || ''));
                    if (/watch on the official site|official site|resmi saytda|kanalin saytinda/.test(linkText)) {
                      links[k].click();
                      window.__pulseazOfficialLinkClicked = true;
                      break;
                    }
                  }
                }
              } catch(e) {}
              // If a provider page lists several channels, choose the channel selected in PulseAZ.
              try {
                if (!window.__pulseazChannelSelected && desiredNames.length) {
                  var nodes = document.querySelectorAll('a,button,[role="button"],[onclick],.channel,.tv-channel');
                  for (var i=0; i<nodes.length; i++) {
                    var el = nodes[i];
                    var label = [el.innerText, el.textContent, el.getAttribute('aria-label'),
                      el.getAttribute('title'), el.getAttribute('alt')].join(' ');
                    if (el.querySelector) {
                      var img = el.querySelector('img');
                      if (img) label += ' ' + (img.alt || img.title || '');
                    }
                    var normalized = norm(label);
                    if (desiredNames.some(function(name) {
                      return normalized === norm(name) || normalized.indexOf(norm(name)) >= 0;
                    })) {
                      el.click();
                      window.__pulseazChannelSelected = true;
                      break;
                    }
                  }
                }
              } catch(e) {}
              var found = [];
              function add(v) {
                if (typeof v === 'string' && /^https:\\/\\//i.test(v) && /\\.(?:m3u8|mpd|mp4|m4v|mp3|m4a|aac)(?:$|[?#])/i.test(v)) {
                  if (found.indexOf(v) < 0) found.push(v);
                }
              }
              // Legacy public APIs may return a manifest under full_url or threadAddress.
              // Only use an exposed HTTPS media URL returned directly by the provider response.
              function walkProviderData(value, depth) {
                if (!value || depth > 9) return;
                if (typeof value === 'string') { add(value); return; }
                if (Array.isArray(value)) {
                  value.forEach(function(item) { walkProviderData(item, depth + 1); });
                  return;
                }
                if (typeof value === 'object') {
                  var candidate = value.full_url || value.threadAddress || value.thread_address ||
                    value.play_url || value.stream_url || value.file || value.url;
                  var token = value.token || value.streamToken || value.stream_token;
                  if (typeof candidate === 'string' && typeof token === 'string' &&
                      /^https:\\/\\//i.test(candidate) &&
                      /\\.(?:m3u8|mpd|mp4|m4v|mp3|m4a|aac)(?:$|[?#])/i.test(candidate) &&
                      !/[?&]token=/i.test(candidate)) {
                    add(candidate + (candidate.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token));
                  }
                  Object.keys(value).forEach(function(key) {
                    walkProviderData(value[key], depth + 1);
                  });
                }
              }
              try {
                var responseText = document.body ? (document.body.innerText || document.body.textContent || '') : '';
                if (responseText && responseText.length < 2000000) {
                  walkProviderData(JSON.parse(responseText), 0);
                }
              } catch(e) {}
              // Some official players create their HLS request only after a play action.
              // Channel selection in PulseAZ is the user's play intent, so start this hidden probe muted.
              function startVideo(v) {
                try {
                  if (!v || typeof v.play !== 'function') return;
                  v.muted = true;
                  v.playsInline = true;
                  var p = v.play();
                  if (p && typeof p.catch === 'function') p.catch(function(){});
                } catch(e) {}
              }
              document.querySelectorAll('video,source').forEach(function(e) {
                add(e.currentSrc); add(e.src); add(e.getAttribute('src'));
                if (e.tagName && e.tagName.toLowerCase() === 'video') startVideo(e);
              });
              // If the site exposes a visible player control rather than HTML5 autoplay, trigger play.
              document.querySelectorAll('button,[role="button"]').forEach(function(b) {
                try {
                  var t = ((b.getAttribute('aria-label') || '') + ' ' +
                    (b.getAttribute('title') || '') + ' ' + (b.innerText || '')).toLowerCase();
                  if (/play|start|watch live|live|canlı|izlə/.test(t)) b.click();
                } catch(e) {}
              });
              try {
                if (window.jwplayer) {
                  document.querySelectorAll('[id]').forEach(function(el) {
                    try {
                      var p = window.jwplayer(el.id);
                      try { if (p && p.play) p.play(); } catch(e) {}
                      var item = p && p.getPlaylistItem && p.getPlaylistItem();
                      if (item) { add(item.file); (item.sources || []).forEach(function(x){add(x.file);}); }
                    } catch(e) {}
                  });
                }
              } catch(e) {}
              try {
                if (window.videojs) {
                  document.querySelectorAll('video').forEach(function(v) {
                    try {
                      var player = window.videojs(v);
                      add(player.currentSrc());
                      try { var p = player.play(); if (p && p.catch) p.catch(function(){}); } catch(e) {}
                    } catch(e) {}
                  });
                }
              } catch(e) {}
              try {
                if (window.performance && performance.getEntriesByType) {
                  performance.getEntriesByType('resource').forEach(function(e) { add(e.name); });
                }
              } catch(e) {}
              var html = document.documentElement ? document.documentElement.innerHTML : '';
              var m = html.match(/https?:\\\\/\\\\/[^"'\\\\s<>]+?\\\\.(?:m3u8|mpd|mp4|m4v|mp3|m4a|aac)(?:\\\\?[^"'\\\\s<>]*)?/i);
              if (m) add(m[0].replace(/\\\\\\\\\\//g, '/').replace(/&amp;/g, '&'));
              return found.length ? found[0] : null;
            })();
        """.trimIndent()

        val probeRunnable = object : Runnable {
            override fun run() {
                if (completed.get() || generation != resolverGeneration || isFinishing || isDestroyed) return
                webView.evaluateJavascript(probeScript) { raw ->
                    val found = runCatching {
                        org.json.JSONTokener(raw ?: "null").nextValue() as? String
                    }.getOrNull()
                    if (isDirectMediaUrl(found.orEmpty())) finish?.invoke(found)
                }
                if (!completed.get() && generation == resolverGeneration) {
                    resolverHandler.postDelayed(this, 750L)
                }
            }
        }
        resolverProbeRunnable = probeRunnable

        finish = { url ->
            if (completed.compareAndSet(false, true)) {
                resolverHandler.post {
                    if (generation != resolverGeneration || isFinishing || isDestroyed) return@post
                    resolverHandler.removeCallbacks(probeRunnable)
                    if (resolverProbeRunnable === probeRunnable) resolverProbeRunnable = null
                    resolverTimeout?.let { resolverHandler.removeCallbacks(it) }
                    resolverTimeout = null
                    destroyResolverView()
                    callback(url, if (url.isNullOrBlank()) null else pageUrl)
                }
            }
        }

        webView.apply {
            alpha = 0f
            visibility = View.VISIBLE
            isClickable = false
            isLongClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress >= 60 && !completed.get()) {
                        resolverHandler.removeCallbacks(probeRunnable)
                        resolverHandler.post(probeRunnable)
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val requestUrl = request?.url?.toString().orEmpty()
                    if (isDirectMediaUrl(requestUrl)) {
                        val allowedHeaders = setOf("referer", "origin", "user-agent", "accept", "accept-language")
                        val captured = request?.requestHeaders.orEmpty()
                            .filterKeys { it.lowercase() in allowedHeaders }
                        if (captured.isNotEmpty()) streamRequestHeaders = captured
                        finish?.invoke(requestUrl)
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            200,
                            "Captured by PulseAZ",
                            mapOf("Cache-Control" to "no-store"),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (view != null && !completed.get()) {
                        resolverHandler.removeCallbacks(probeRunnable)
                        resolverHandler.postDelayed(probeRunnable, 350L)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Player iframes may initialize after the parent document has loaded.
                }
            }
        }

        // Hidden and transparent beneath the native player; no webpage appears on screen.
        playerFrame.addView(
            webView,
            0,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        val timeout = Runnable { finish?.invoke(null) }
        resolverTimeout = timeout
        resolverHandler.postDelayed(timeout, 18000L)
        runCatching { webView.loadUrl(pageUrl) }.onFailure { finish?.invoke(null) }
    }

    private fun isDirectMediaUrl(value: String): Boolean {
        if (!value.startsWith("https://", ignoreCase = true)) return false
        val parsed = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        val target = (parsed.path.orEmpty() + "?" + parsed.query.orEmpty())
        return Regex("""(?i)\.(?:m3u8|mpd|mp4|m4v|mp3|m4a|aac)(?:$|[?&#])""").containsMatchIn(target)
    }

    private fun destroyResolverView() {
        val view = resolverWebView ?: return
        resolverWebView = null
        runCatching {
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.loadUrl("about:blank")
            view.clearHistory()
            view.removeAllViews()
            view.destroy()
        }
    }

    private fun cancelStreamResolver() {
        resolverGeneration++
        resolverTimeout?.let { resolverHandler.removeCallbacks(it) }
        resolverTimeout = null
        resolverProbeRunnable?.let { resolverHandler.removeCallbacks(it) }
        resolverProbeRunnable = null
        destroyResolverView()
    }

    private fun showStreamError() {
        val reason = lastPlaybackError?.let { " · " + it }.orEmpty()
        statusText.text = "Bu kanal üçün açıq yayım tapılmadı" + reason + ". Sonra yenidən yoxla."
        statusText.setTextColor(danger)
        emptyPlayerHint.text = "YAYIM ƏLÇATAN DEYİL"
        emptyPlayerHint.visibility = if (selectedRadio == null) View.VISIBLE else View.GONE
        retryButton.visibility = View.VISIBLE
    }

    private fun retryCurrentSource() {
        val streams = activeStreams
        if (streams.isNotEmpty()) {
            cancelStreamResolver()
            pageResolutionAttempted = 0
            pageResolutionInProgress = false
            streamReferer = null
            streamRequestHeaders = emptyMap()
            lastPlaybackError = null
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
            selectedRadio?.let { startRadioForeground(it.name) }
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
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Ölçü: ZOOM"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Ölçü: FILL"
            else -> "Ölçü: FIT"
        }
        applySvgIcon(fitButton, "fit", textMain)
    }

    private fun rememberRecent(key: String) {
        val current = favorites.getString("recent_order", "").orEmpty()
            .split('|').filter { it.isNotBlank() }
        val next = (listOf(key) + current.filterNot { it == key }).take(30)
        favorites.edit().putString("recent_order", next.joinToString("|")).apply()
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

        val domain = Uri.parse(url).getQueryParameter("domain")
            ?: runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        val candidates = listOf(
            url,
            "https://www.google.com/s2/favicons?domain=$domain&sz=256"
        ).distinct()

        Thread {
            var bitmap: Bitmap? = null
            for (candidate in candidates) {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(candidate).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Mozilla/5.0 PulseAZTV/1.4")
                    }
                    if (connection.responseCode in 200..299) {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        val contentType = connection.contentType.orEmpty().lowercase()
                        bitmap = if (contentType.contains("svg") || candidate.substringBefore("?").endsWith(".svg", true)) {
                            runCatching {
                                val svg = SVG.getFromString(bytes.toString(Charsets.UTF_8))
                                val outputSize = 320
                                val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(output)
                                val docWidth = svg.documentWidth.takeIf { it > 0f } ?: outputSize.toFloat()
                                val docHeight = svg.documentHeight.takeIf { it > 0f } ?: outputSize.toFloat()
                                val scale = minOf(outputSize / docWidth, outputSize / docHeight)
                                canvas.save()
                                canvas.translate(
                                    (outputSize - docWidth * scale) / 2f,
                                    (outputSize - docHeight * scale) / 2f
                                )
                                canvas.scale(scale, scale)
                                svg.renderToCanvas(canvas)
                                canvas.restore()
                                output
                            }.getOrNull()
                        } else {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                        // Very small favicons are blurred in a 58dp tile; keep the sharp initials instead.
                        if (bitmap != null && (maxOf(bitmap!!.width, bitmap!!.height) < 96 || bitmap!!.width < 40 || bitmap!!.height < 32)) {
                            bitmap = null
                        }
                    }
                } catch (_: Exception) {
                    bitmap = null
                } finally {
                    connection?.disconnect()
                }
                if (bitmap != null) break
            }

            synchronized(logoCache) {
                loadingLogos.remove(url)
                if (bitmap != null) logoCache[url] = bitmap!!
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

    private fun applySvgIcon(view: TextView, name: String, tint: Int, sizeDp: Int = 18) {
        val cacheKey = "$name:$tint"
        val bitmap = synchronized(svgIconCache) {
            svgIconCache[cacheKey] ?: try {
                val svgText = assets.open("icons/$name.svg").bufferedReader().use { it.readText() }
                val color = String.format("#%06X", 0xFFFFFF and tint)
                val svg = SVG.getFromString(svgText.replace("#FFFFFF", color))
                val pixels = dp(64)
                svg.documentWidth = pixels.toFloat()
                svg.documentHeight = pixels.toFloat()
                Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also { output ->
                    svg.renderToCanvas(Canvas(output))
                    svgIconCache[cacheKey] = output
                }
            } catch (_: Exception) {
                null
            }
        } ?: return

        val drawable = BitmapDrawable(resources, bitmap).apply {
            setBounds(0, 0, dp(sizeDp), dp(sizeDp))
        }
        view.setCompoundDrawablesRelative(drawable, null, null, null)
        view.compoundDrawablePadding = dp(7)
    }

    private fun animateAmbientBackground(view: View) {
        val from = intArrayOf(
            Color.rgb(5, 9, 19),
            Color.rgb(8, 18, 35),
            Color.rgb(13, 13, 34)
        )
        val to = intArrayOf(
            Color.rgb(7, 13, 27),
            Color.rgb(10, 22, 42),
            Color.rgb(17, 15, 39)
        )
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 14000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                val amount = animation.animatedValue as Float
                val colors = IntArray(from.size) { index ->
                    blendColor(from[index], to[index], amount)
                }
                view.background = gradient(colors, 0)
            }
        }
        animations.add(animator)
        animator.start()
    }

    private fun blendColor(start: Int, end: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(start) + (Color.red(end) - Color.red(start)) * t).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * t).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t).toInt()
        )
    }

    private fun pulse(view: View) {
        val alphaPulse = ObjectAnimator.ofFloat(view, "alpha", 0.78f, 1f).apply {
            duration = 1250
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.98f, 1.025f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.98f, 1.025f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        animations.addAll(listOf(alphaPulse, scaleX, scaleY))
        alphaPulse.start()
        scaleX.start()
        scaleY.start()
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            val family = when {
                size >= 18f && bold -> "sans-serif-black"
                bold -> "sans-serif-medium"
                else -> "sans-serif"
            }
            setTypeface(Typeface.create(family, Typeface.NORMAL))
            if (bold && size <= 12f) letterSpacing = 0.035f
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            selectedChannel != null && player?.isPlaying == true) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val radioKeepsPlaying = selectedRadio != null && player?.isPlaying == true
        val pipKeepsPlaying = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        if (!radioKeepsPlaying && !pipKeepsPlaying) player?.pause()
    }

    override fun onDestroy() {
        cancelStreamResolver()
        animations.forEach { it.cancel() }
        audioAnimations.forEach { it.cancel() }
        animations.clear()
        audioAnimations.clear()
        player?.let { active -> playerListener?.let { active.removeListener(it) } }
        playerView.player = null
        player = null
        if (serviceBound) {
            runCatching { unbindService(playbackConnection) }
            serviceBound = false
        }
        super.onDestroy()
    }
}
