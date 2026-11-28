package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.viewmodel.DriverTerminalUiState
import com.example.viewmodel.TerminalRideState
import kotlin.math.abs

/**
 * Lightweight, crash-safe native WindowManager Floating Ride Info Overlay Card.
 * Floats cleanly over Google Maps / Waze when the driver has opted into "Display over other apps":
 * - Compact Readable Card: Stage pill, Passenger, Fare, 2-line Destination/Stop Address & Postcode, Live ETA/Distance, Speed, Unread Chat dot.
 * - 1-Tap Expand: Reveals Rider Notes, 1-tap Call, 1-tap Chat, and 1-tap Return to Controls/Slider.
 * - 1-Tap Minimize Bubble: Shrinks to a tiny draggable corner badge whenever driver wants 100% map screen.
 * - Auto-Pop Urgent Offer Mode: Instantly transforms into an orange NEXT RIDE / NEW OFFER card with direct Accept & Decline buttons!
 */
class FloatingRideOverlayController(
    context: Context,
    private val onAcceptOffer: (isStacked: Boolean) -> Unit,
    private val onDeclineOffer: (isStacked: Boolean) -> Unit
) {

    companion object {
        const val EXTRA_OPEN_CHAT = "extra_open_chat_from_overlay"

        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun openOverlayPermissionSettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = appContext.resources.displayMetrics.density

    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var isMinimizedBubble = false
    private var isExpandedDetails = false
    private var lastOfferIdAutoExpanded: String? = null
    private var lastRenderedState: DriverTerminalUiState? = null

    // View references inside card mode
    private lateinit var cardContainer: LinearLayout
    private lateinit var bubbleContainer: LinearLayout
    private lateinit var bubbleEtaText: TextView
    private lateinit var bubbleDot: View

    private lateinit var stageBadge: TextView
    private lateinit var queuedBadge: TextView
    private lateinit var fareText: TextView
    private lateinit var speedText: TextView
    private lateinit var unreadDot: View
    private lateinit var minimizeBtn: TextView

    private lateinit var passengerRow: LinearLayout
    private lateinit var passengerNameText: TextView
    private lateinit var etaDistText: TextView
    private lateinit var addressText: TextView
    private lateinit var notesText: TextView

    private lateinit var normalActionsRow: LinearLayout
    private lateinit var toggleDetailsBtn: TextView
    private lateinit var openControlsBtn: TextView

    private lateinit var expandedActionsRow: LinearLayout
    private lateinit var callBtn: TextView
    private lateinit var chatBtn: TextView
    private lateinit var openSliderBtn: TextView

    private lateinit var offerActionsRow: LinearLayout
    private lateinit var acceptOfferBtn: TextView
    private lateinit var declineOfferBtn: TextView
    private lateinit var openOfferBtn: TextView

    fun syncState(
        state: DriverTerminalUiState,
        isFullScreenVisible: Boolean,
        isSplitScreen: Boolean
    ) {
        val hasOffer = state.stackedRideOffer != null || state.incomingOffer != null
        val shouldShow = state.cockpitSettings.floatingOverlayEnabled &&
            canDrawOverlays(appContext) &&
            !isFullScreenVisible &&
            !isSplitScreen &&
            (state.activeRide != null || hasOffer)

        mainHandler.post {
            if (!shouldShow) {
                hideInternal()
                return@post
            }
            lastRenderedState = state
            // Auto-expand out of minimized bubble if a new stacked or incoming offer arrives
            val activeOfferId = state.stackedRideOffer?.id ?: state.incomingOffer?.id
            if (activeOfferId != null && activeOfferId != lastOfferIdAutoExpanded) {
                lastOfferIdAutoExpanded = activeOfferId
                isMinimizedBubble = false
            } else if (activeOfferId == null) {
                lastOfferIdAutoExpanded = null
            }

            if (!isAttached) {
                attachInternal()
            }
            if (isAttached) {
                bindStateInternal(state)
            }
        }
    }

    fun hide() {
        mainHandler.post { hideInternal() }
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post { hideInternal() }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun roundedBg(
        fillColor: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * density
            setColor(fillColor)
            if (strokeColor != null) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    private fun attachInternal() {
        val wm = windowManager ?: return
        if (isAttached || !canDrawOverlays(appContext)) return

        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val cardWidth = minOf((screenWidth * 0.90f).toInt(), dp(364))

        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }

        // --- Mode 1: Minimized Corner Bubble ---
        bubbleContainer = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F21A1A1E"))
                setStroke(dp(2), Color.parseColor("#00D26A"))
            }
            elevation = dp(8).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58))
            visibility = View.GONE
        }
        bubbleDot = View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                bottomMargin = dp(2)
            }
            visibility = View.GONE
        }
        bubbleEtaText = TextView(appContext).apply {
            text = "AR"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        bubbleContainer.addView(bubbleDot)
        bubbleContainer.addView(bubbleEtaText)
        root.addView(bubbleContainer)

        // --- Mode 2: Readable Floating Ride Info Card ---
        cardContainer = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(
                fillColor = Color.parseColor("#F51A1A1E"),
                radiusDp = 18,
                strokeColor = Color.parseColor("#2C2C30"),
                strokeWidthDp = 1
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(10).toFloat()
            layoutParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Top bar: Stage pill + Queued chip + Fare + Speed + Unread dot + Minimize (-)
        val topBar = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        stageBadge = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(7), dp(3), dp(7), dp(3))
        }
        queuedBadge = TextView(appContext).apply {
            text = "+1 QUEUED"
            setTextColor(Color.parseColor("#AF52DE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTypeface(null, Typeface.BOLD)
            background = roundedBg(Color.parseColor("#33AF52DE"), 7)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
            visibility = View.GONE
        }
        fareText = TextView(appContext).apply {
            setTextColor(Color.parseColor("#00D26A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        val topSpacer = View(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        unreadDot = View(appContext).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                marginEnd = dp(6)
            }
            visibility = View.GONE
        }
        speedText = TextView(appContext).apply {
            setTextColor(Color.parseColor("#E4E4E7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        }
        minimizeBtn = TextView(appContext).apply {
            text = "—"
            setTextColor(Color.parseColor("#A1A1AA"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#252529"), 10)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(24))
            setOnClickListener {
                isMinimizedBubble = true
                lastRenderedState?.let { bindStateInternal(it) }
            }
        }
        topBar.addView(stageBadge)
        topBar.addView(queuedBadge)
        topBar.addView(fareText)
        topBar.addView(topSpacer)
        topBar.addView(unreadDot)
        topBar.addView(speedText)
        topBar.addView(minimizeBtn)
        cardContainer.addView(topBar)

        // Passenger + Live ETA/Distance row
        passengerRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }
        passengerNameText = TextView(appContext).apply {
            setTextColor(Color.parseColor("#E4E4E7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        etaDistText = TextView(appContext).apply {
            setTextColor(Color.parseColor("#00D26A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        passengerRow.addView(passengerNameText)
        passengerRow.addView(etaDistText)
        cardContainer.addView(passengerRow)

        // Prominent 2-line Destination / Pickup / Stop Address + Postcode
        addressText = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        cardContainer.addView(addressText)

        // Rider notes (visible when expanded)
        notesText = TextView(appContext).apply {
            setTextColor(Color.parseColor("#FF9500"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            visibility = View.GONE
        }
        cardContainer.addView(notesText)

        // Compact bottom row: Details toggle + Open Controls
        normalActionsRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        toggleDetailsBtn = makePillButton("Details ▾", Color.parseColor("#252529"), Color.parseColor("#E4E4E7")).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                isExpandedDetails = !isExpandedDetails
                lastRenderedState?.let { bindStateInternal(it) }
            }
        }
        openControlsBtn = makePillButton("Open Slider & Menu ↗", Color.parseColor("#007AFF"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1.45f)
            setOnClickListener { openDriverApp(openChat = false) }
        }
        normalActionsRow.addView(toggleDetailsBtn)
        normalActionsRow.addView(openControlsBtn)
        cardContainer.addView(normalActionsRow)

        // Expanded quick actions row: Call + Chat + Open Slider
        expandedActionsRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
            visibility = View.GONE
        }
        callBtn = makePillButton("Call", Color.parseColor("#252529"), Color.parseColor("#007AFF")).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                val phone = lastRenderedState?.activeRide?.passengerPhone
                if (!phone.isNullOrBlank()) {
                    ExternalIntentHelpers.dialPhoneNumber(appContext, phone)
                }
            }
        }
        chatBtn = makePillButton("Chat", Color.parseColor("#252529"), Color.parseColor("#00D26A")).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { openDriverApp(openChat = true) }
        }
        openSliderBtn = makePillButton("Less ▴", Color.parseColor("#252529"), Color.parseColor("#A1A1AA")).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 0.9f)
            setOnClickListener {
                isExpandedDetails = false
                lastRenderedState?.let { bindStateInternal(it) }
            }
        }
        expandedActionsRow.addView(callBtn)
        expandedActionsRow.addView(chatBtn)
        expandedActionsRow.addView(openSliderBtn)
        cardContainer.addView(expandedActionsRow)

        // Urgent Offer Actions Row: ACCEPT + DECLINE + Open
        offerActionsRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            visibility = View.GONE
        }
        acceptOfferBtn = makePillButton("ACCEPT", Color.parseColor("#00D26A"), Color.parseColor("#0D0D0F")).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.4f).apply { marginEnd = dp(6) }
            setOnClickListener {
                val st = lastRenderedState ?: return@setOnClickListener
                val isStacked = st.stackedRideOffer != null
                onAcceptOffer(isStacked)
            }
        }
        declineOfferBtn = makePillButton("DECLINE", Color.parseColor("#252529"), Color.parseColor("#FF3B30")).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                val st = lastRenderedState ?: return@setOnClickListener
                val isStacked = st.stackedRideOffer != null
                onDeclineOffer(isStacked)
            }
        }
        openOfferBtn = makePillButton("Map", Color.parseColor("#252529"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 0.7f)
            setOnClickListener { openDriverApp(openChat = false) }
        }
        offerActionsRow.addView(acceptOfferBtn)
        offerActionsRow.addView(declineOfferBtn)
        offerActionsRow.addView(openOfferBtn)
        cardContainer.addView(offerActionsRow)

        root.addView(cardContainer)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - cardWidth) / 2).coerceAtLeast(dp(12))
            // Positioned below Google Maps' top turn banner so it never covers turn arrows!
            y = dp(128)
        }

        // Smooth dragging for both the full card header/address and the minimized bubble
        val dragListener = createDragTouchListener(params, wm, root)
        cardContainer.setOnTouchListener(dragListener)
        bubbleContainer.setOnTouchListener { v, event ->
            handleBubbleTouch(v, event, params, wm, root)
        }

        runCatching {
            wm.addView(root, params)
            rootView = root
            layoutParams = params
            isAttached = true
        }
    }

    private fun makePillButton(label: String, bgColor: Int, textColor: Int): TextView {
        return TextView(appContext).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = roundedBg(bgColor, 10)
            isClickable = true
        }
    }

    private fun createDragTouchListener(
        params: WindowManager.LayoutParams,
        wm: WindowManager,
        root: View
    ): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    val metrics = appContext.resources.displayMetrics
                    params.x = (initialX + dx).coerceIn(0, (metrics.widthPixels - dp(60)).coerceAtLeast(0))
                    params.y = (initialY + dy).coerceIn(dp(24), (metrics.heightPixels - dp(80)).coerceAtLeast(dp(24)))
                    runCatching { wm.updateViewLayout(root, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleBubbleTouch(
        view: View,
        event: MotionEvent,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
        root: View
    ): Boolean {
        val tag = view.tag as? FloatArray ?: FloatArray(4).also { view.tag = it }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                tag[0] = params.x.toFloat()
                tag[1] = params.y.toFloat()
                tag[2] = event.rawX
                tag[3] = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - tag[2]).toInt()
                val dy = (event.rawY - tag[3]).toInt()
                val metrics = appContext.resources.displayMetrics
                params.x = (tag[0].toInt() + dx).coerceIn(0, (metrics.widthPixels - dp(60)).coerceAtLeast(0))
                params.y = (tag[1].toInt() + dy).coerceIn(dp(24), (metrics.heightPixels - dp(80)).coerceAtLeast(dp(24)))
                runCatching { wm.updateViewLayout(root, params) }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = abs(event.rawX - tag[2]) + abs(event.rawY - tag[3])
                if (moved < dp(10)) {
                    isMinimizedBubble = false
                    lastRenderedState?.let { bindStateInternal(it) }
                }
                return true
            }
        }
        return false
    }

    private fun bindStateInternal(state: DriverTerminalUiState) {
        val stackedOffer = state.stackedRideOffer
        val incomingOffer = state.incomingOffer
        val activeOffer = stackedOffer ?: incomingOffer
        val activeRide = state.activeRide

        if (isMinimizedBubble && activeOffer == null) {
            cardContainer.visibility = View.GONE
            bubbleContainer.visibility = View.VISIBLE
            val liveEta = state.liveRemainingEtaMinutes ?: state.activeRoute.etaMinutes
            bubbleEtaText.text = if (liveEta != null) "${liveEta}m" else "AR"
            bubbleDot.visibility = if (state.unreadChatCount > 0) View.VISIBLE else View.GONE
            val ringColor = when (state.rideState) {
                TerminalRideState.EN_ROUTE_PICKUP -> Color.parseColor("#007AFF")
                TerminalRideState.WAITING -> Color.parseColor("#FF9500")
                TerminalRideState.IN_PROGRESS -> Color.parseColor("#00D26A")
                else -> Color.parseColor("#FF9500")
            }
            bubbleContainer.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F21A1A1E"))
                setStroke(dp(2), ringColor)
            }
            return
        }

        bubbleContainer.visibility = View.GONE
        cardContainer.visibility = View.VISIBLE

        unreadDot.visibility = if (state.unreadChatCount > 0) View.VISIBLE else View.GONE
        speedText.text = "${state.currentSpeedMph} mph"
        speedText.setTextColor(
            if (state.isOverSpeedAlert) Color.parseColor("#FF3B30") else Color.parseColor("#E4E4E7")
        )

        if (activeOffer != null) {
            val isStacked = stackedOffer != null
            val secs = if (isStacked) state.stackedOfferSecondsLeft else state.offerSecondsLeft
            cardContainer.background = roundedBg(
                fillColor = Color.parseColor("#F720160A"),
                radiusDp = 18,
                strokeColor = Color.parseColor("#FF9500"),
                strokeWidthDp = 2
            )
            stageBadge.text = if (isStacked) "NEXT RIDE • ${secs}s" else "NEW OFFER • ${secs}s"
            stageBadge.setTextColor(Color.parseColor("#FF9500"))
            stageBadge.background = roundedBg(Color.parseColor("#33FF9500"), 7)
            queuedBadge.visibility = View.GONE
            fareText.text = "£%.2f".format(activeOffer.fareEstimate)
            minimizeBtn.visibility = View.GONE

            passengerNameText.text = if (isStacked) "Queued after current drop-off" else "Incoming Dispatch"
            etaDistText.text = if (activeOffer.distanceMiles > 0.0) {
                "%.1f mi".format(activeOffer.distanceMiles)
            } else ""
            addressText.text = "${activeOffer.pickupAddress} → ${activeOffer.dropoffAddress}"
            notesText.visibility = View.GONE

            normalActionsRow.visibility = View.GONE
            expandedActionsRow.visibility = View.GONE
            offerActionsRow.visibility = View.VISIBLE
            return
        }

        // Active Ride Mode
        offerActionsRow.visibility = View.GONE
        minimizeBtn.visibility = View.VISIBLE
        cardContainer.background = roundedBg(
            fillColor = Color.parseColor("#F51A1A1E"),
            radiusDp = 18,
            strokeColor = Color.parseColor("#2C2C30"),
            strokeWidthDp = 1
        )

        val ride = activeRide ?: return
        val hasRemainingStop = state.rideState == TerminalRideState.IN_PROGRESS &&
            ride.currentStopIndex < ride.stops.size
        val activeStop = if (hasRemainingStop) ride.stops.getOrNull(ride.currentStopIndex) else null

        val (stageLabel, stageColorHex, stageBgHex) = when (state.rideState) {
            TerminalRideState.EN_ROUTE_PICKUP -> Triple("TO PICKUP", "#007AFF", "#33007AFF")
            TerminalRideState.WAITING -> Triple("AT PICKUP", "#FF9500", "#33FF9500")
            TerminalRideState.IN_PROGRESS -> if (hasRemainingStop) {
                Triple("STOP ${ride.currentStopIndex + 1}/${ride.stops.size}", "#00D26A", "#3300D26A")
            } else {
                Triple("TO DROPOFF", "#00D26A", "#3300D26A")
            }
            TerminalRideState.COMPLETED_PAYMENT -> Triple("PAYMENT", "#FF9500", "#33FF9500")
            else -> Triple("ACTIVE", "#00D26A", "#3300D26A")
        }
        stageBadge.text = stageLabel
        stageBadge.setTextColor(Color.parseColor(stageColorHex))
        stageBadge.background = roundedBg(Color.parseColor(stageBgHex), 7)

        queuedBadge.visibility = if (state.queuedStackedRide != null) View.VISIBLE else View.GONE

        val liveFare = ride.finalFare?.takeIf { it > 0.0 } ?: ride.fareEstimate
        fareText.text = "£%.2f".format(liveFare)

        passengerNameText.text = ride.passengerName.ifBlank { "Passenger" }
        val liveEta = state.liveRemainingEtaMinutes ?: state.activeRoute.etaMinutes
        val liveDist = state.liveRemainingDistanceMeters?.takeIf { it > 0 }?.let { m ->
            if (m >= 805) "%.1f mi".format(m / 1609.344) else "${(m * 1.09361).toInt().coerceAtLeast(10)} yd"
        } ?: state.activeRoute.distanceText
        etaDistText.text = buildString {
            if (liveEta != null) append("${liveEta} min")
            if (!liveDist.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(liveDist)
            }
        }

        val targetAddr = when {
            state.rideState == TerminalRideState.EN_ROUTE_PICKUP || state.rideState == TerminalRideState.WAITING ->
                ride.pickupAddress
            activeStop != null && activeStop.address.isNotBlank() ->
                "Stop ${ride.currentStopIndex + 1}: ${activeStop.address}"
            else -> ride.dropoffAddress
        }
        addressText.text = targetAddr.ifBlank { "Active trip destination" }

        val openActionLabel = when (state.rideState) {
            TerminalRideState.EN_ROUTE_PICKUP -> "Arrived / Controls ↗"
            TerminalRideState.WAITING -> "Start Trip / Controls ↗"
            TerminalRideState.IN_PROGRESS -> if (hasRemainingStop) "Go Next / Controls ↗" else "Complete / Controls ↗"
            else -> "Open Controls ↗"
        }
        openControlsBtn.text = openActionLabel

        if (isExpandedDetails) {
            normalActionsRow.visibility = View.GONE
            expandedActionsRow.visibility = View.VISIBLE
            val note = ride.riderNotes?.trim().orEmpty()
            if (note.isNotBlank()) {
                notesText.text = "Note: $note"
                notesText.visibility = View.VISIBLE
            } else {
                notesText.visibility = View.GONE
            }
            callBtn.visibility = if (!ride.passengerPhone.isNullOrBlank()) View.VISIBLE else View.GONE
            chatBtn.text = if (state.unreadChatCount > 0) "Chat (${state.unreadChatCount})" else "Chat"
        } else {
            notesText.visibility = View.GONE
            expandedActionsRow.visibility = View.GONE
            normalActionsRow.visibility = View.VISIBLE
            toggleDetailsBtn.text = if (state.unreadChatCount > 0) "Details (${state.unreadChatCount}) ▾" else "Details ▾"
        }
    }

    private fun openDriverApp(openChat: Boolean) {
        runCatching {
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (openChat) putExtra(EXTRA_OPEN_CHAT, true)
            }
            appContext.startActivity(intent)
        }
    }

    private fun hideInternal() {
        val wm = windowManager
        val root = rootView
        if (isAttached && wm != null && root != null) {
            runCatching { wm.removeView(root) }
        }
        isAttached = false
        rootView = null
        layoutParams = null
        isExpandedDetails = false
    }
}
