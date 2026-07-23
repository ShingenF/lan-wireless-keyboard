package com.local.virtualkeyboard

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.local.virtualkeyboard.input.GameControlSource
import com.local.virtualkeyboard.input.GameMovementController
import com.local.virtualkeyboard.input.RemoteInputEditText
import com.local.virtualkeyboard.input.ShortcutInputRouter
import com.local.virtualkeyboard.input.ShortcutSelectionSnapshot
import com.local.virtualkeyboard.network.NetworkClient
import com.local.virtualkeyboard.protocol.AuthenticationProof
import com.local.virtualkeyboard.protocol.ButtonAction
import com.local.virtualkeyboard.protocol.MouseButton
import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.RemoteKey
import com.local.virtualkeyboard.settings.ConnectionSettings
import com.local.virtualkeyboard.settings.InputMethodShortcut
import com.local.virtualkeyboard.settings.LanguageToggleShortcut
import com.local.virtualkeyboard.settings.PointerAcceleration
import com.local.virtualkeyboard.settings.ScrollHapticProfile
import com.local.virtualkeyboard.settings.ScrollWheelTuning
import com.local.virtualkeyboard.settings.SettingsStore
import com.local.virtualkeyboard.settings.ThemeColors
import com.local.virtualkeyboard.settings.ThemeFramework
import com.local.virtualkeyboard.settings.ThemeMode
import com.local.virtualkeyboard.settings.ThemeSettings
import com.local.virtualkeyboard.settings.requiresConnectionRestart
import com.local.virtualkeyboard.ui.JoystickDirection
import com.local.virtualkeyboard.ui.JoystickView
import com.local.virtualkeyboard.ui.ImePanelMotionState
import com.local.virtualkeyboard.ui.ImePanelMotionUpdate
import com.local.virtualkeyboard.ui.ImePanelVisibilityUpdate
import com.local.virtualkeyboard.ui.ScrollStripView
import com.local.virtualkeyboard.ui.ShortcutPanelView
import com.local.virtualkeyboard.ui.TouchpadView

class MainActivity : Activity(), NetworkClient.Listener {
    private lateinit var settingsStore: SettingsStore
    private lateinit var statusView: TextView
    private lateinit var inputView: RemoteInputEditText
    private lateinit var synchronousModeButton: TextView
    private lateinit var deferredModeButton: TextView
    private lateinit var sendButton: ImageButton
    private lateinit var syncShortcutContainer: LinearLayout
    private lateinit var inputMethodButton: ImageButton
    private lateinit var languageToggleButton: TextView
    private lateinit var touchpadView: TouchpadView
    private lateinit var scrollStripView: ScrollStripView
    private lateinit var shortcutPanel: ShortcutPanelView
    private val repeatCancellations = mutableListOf<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gameMovementController = GameMovementController { sendCommand(it) }
    private var pendingImeHide: Runnable? = null
    private var pendingLegacyImeShowRollback: Runnable? = null
    private var legacyImeVisible = false

    private var networkClient: NetworkClient? = null
    private var activityVisible = false
    private var settingsPromptShown = false
    private var deferredModeEnabled = false
    private var currentThemeColors = ThemeColors()
    private var languageToggleShortcut = LanguageToggleShortcut.SHIFT
    private var inputMethodShortcut = InputMethodShortcut.WINDOWS_SPACE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settingsStore = SettingsStore(this)
        statusView = findViewById(R.id.connectionStatus)
        inputView = findViewById(R.id.remoteInput)
        synchronousModeButton = findViewById(R.id.synchronousModeButton)
        deferredModeButton = findViewById(R.id.deferredModeButton)
        sendButton = findViewById(R.id.sendButton)
        syncShortcutContainer = findViewById(R.id.syncShortcutContainer)
        inputMethodButton = findViewById(R.id.inputMethodButton)
        languageToggleButton = findViewById(R.id.languageToggleButton)
        touchpadView = findViewById(R.id.touchpad)
        scrollStripView = findViewById(R.id.scrollStrip)
        shortcutPanel = findViewById(R.id.shortcutPanel)
        installImeInsetHandling(findViewById(R.id.mainRoot))

        inputView.configure(commandSink = ::sendCommand)
        installLegacyImeLaunchPreparation()
        shortcutPanel.setOnSelectionChangedListener {
            inputView.refreshShortcutInputConnection()
        }
        inputView.configureShortcutInput(
            router = ShortcutInputRouter(
                selection = shortcutPanel.selection,
                onSelectionChanged = shortcutPanel::notifySelectionChanged,
                emit = { sendCommand(it) },
            ),
            onInvalidInput = {
                Toast.makeText(this, R.string.shortcut_input_invalid, Toast.LENGTH_SHORT).show()
            },
        )
        (lastNonConfigurationInstance as? ShortcutSelectionSnapshot)?.let(shortcutPanel::restoreSelection)
        inputView.setOnEditorActionListener { _, _, _ -> inputView.handleEditorAction() }

        synchronousModeButton.apply {
            installPressHaptic(this)
            setOnClickListener { selectInputMode(deferred = false) }
        }
        deferredModeButton.apply {
            installPressHaptic(this)
            setOnClickListener { selectInputMode(deferred = true) }
        }
        sendButton.apply {
            installPressHaptic(this)
            setOnClickListener {
                inputView.submitDeferredDraft()
                focusInputWithoutShowingIme()
            }
        }
        inputMethodButton.apply {
            installPressHaptic(this)
            setOnClickListener {
                sendCommand(OutgoingCommand.SystemShortcutPress(inputMethodShortcut.command))
                focusInputWithoutShowingIme()
            }
        }
        languageToggleButton.apply {
            installPressHaptic(this)
            setOnClickListener {
                sendCommand(OutgoingCommand.SystemShortcutPress(languageToggleShortcut.command))
                focusInputWithoutShowingIme()
            }
        }
        inputView.restoreDeferredDraft(savedInstanceState?.getString(STATE_DEFERRED_DRAFT).orEmpty())
        selectInputMode(savedInstanceState?.getBoolean(STATE_DEFERRED_MODE) == true)

        findViewById<ImageButton>(R.id.settingsButton).apply {
            installPressHaptic(this)
            setOnClickListener { showSettingsDialog() }
        }
        bindRepeatingKey(findViewById(R.id.upButton), RemoteKey.UP)
        bindRepeatingKey(findViewById(R.id.downButton), RemoteKey.DOWN)
        bindRepeatingKey(findViewById(R.id.leftButton), RemoteKey.LEFT)
        bindRepeatingKey(findViewById(R.id.rightButton), RemoteKey.RIGHT)
        bindJoystick(findViewById(R.id.joystick))
        bindGameMovementButton(
            findViewById(R.id.gameUpButton),
            GameControlSource.UP_BUTTON,
            JoystickDirection.UP,
        )
        bindGameMovementButton(
            findViewById(R.id.gameDownButton),
            GameControlSource.DOWN_BUTTON,
            JoystickDirection.DOWN,
        )
        bindGameMovementButton(
            findViewById(R.id.gameLeftButton),
            GameControlSource.LEFT_BUTTON,
            JoystickDirection.LEFT,
        )
        bindGameMovementButton(
            findViewById(R.id.gameRightButton),
            GameControlSource.RIGHT_BUTTON,
            JoystickDirection.RIGHT,
        )
        bindGameJoystick(findViewById(R.id.gameJoystick))
        findViewById<TextView>(R.id.escapeButton).apply {
            installPressHaptic(this)
            setOnClickListener {
                sendCommand(OutgoingCommand.KeyPress(RemoteKey.ESCAPE))
                focusInputWithoutShowingIme()
            }
        }
        findViewById<TextView>(R.id.rightClickButton).apply {
            installPressHaptic(this)
            setOnClickListener { emitPointerClick(MouseButton.RIGHT) }
        }
        scrollStripView.listener = ScrollStripView.Listener { delta ->
            sendCommand(OutgoingCommand.Wheel(delta))
        }

        touchpadView.listener = object : TouchpadView.Listener {
            override fun onMove(dx: Int, dy: Int) {
                sendCommand(OutgoingCommand.PointerMove(dx, dy))
            }

            override fun onLeftClick() = emitPointerClick(MouseButton.LEFT)

            override fun onRightClick() = emitPointerClick(MouseButton.RIGHT)

            override fun onLeftButtonDown() {
                emitPointerButton(MouseButton.LEFT, ButtonAction.DOWN)
            }

            override fun onLeftButtonUp() {
                emitPointerButton(MouseButton.LEFT, ButtonAction.UP)
                focusInputWithoutShowingIme()
            }

            override fun onWheel(delta: Int) {
                sendCommand(OutgoingCommand.Wheel(delta))
            }
        }
        applySettingsToUi(settingsStore.load())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_DEFERRED_MODE, deferredModeEnabled)
        outState.putString(STATE_DEFERRED_DRAFT, inputView.deferredDraftSnapshot())
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onRetainNonConfigurationInstance(): Any = shortcutPanel.selection.snapshot()

    override fun onStart() {
        super.onStart()
        activityVisible = true
        val settings = settingsStore.load()
        applySettingsToUi(settings)
        if (isComplete(settings)) {
            startConnection(settings)
        } else {
            showStatus("需要设置电脑", R.color.disconnected)
            if (!settingsPromptShown) {
                settingsPromptShown = true
                inputView.post { showSettingsDialog() }
            }
        }
        focusInputWithoutShowingIme()
    }

    override fun onStop() {
        activityVisible = false
        repeatCancellations.forEach { it() }
        pendingImeHide?.let(mainHandler::removeCallbacks)
        pendingImeHide = null
        pendingLegacyImeShowRollback?.let(mainHandler::removeCallbacks)
        pendingLegacyImeShowRollback = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            legacyImeVisible = false
            shortcutPanel.setImeVisible(false, animate = false)
        }
        gameMovementController.releaseAll()
        scrollStripView.cancelMotion()
        if (!isChangingConfigurations) shortcutPanel.resetSelection()
        networkClient?.close()
        networkClient = null
        super.onStop()
    }

    override fun onStateChanged(state: NetworkClient.State) {
        when (state) {
            NetworkClient.State.DISCONNECTED -> {
                gameMovementController.releaseAll()
                shortcutPanel.resetSelection()
                showStatus("未连接，正在重试", R.color.disconnected)
            }
            NetworkClient.State.CONNECTING -> showStatus("正在连接电脑", R.color.connecting)
            NetworkClient.State.AUTHENTICATING -> showStatus("正在验证配对", R.color.connecting)
            NetworkClient.State.CONNECTED -> showStatus("已连接", R.color.connected)
        }
    }

    override fun onPinLearned(fingerprint: String) {
        settingsStore.savePinnedFingerprint(fingerprint)
    }

    override fun onConnectionError(message: String) {
        showStatus(message, R.color.disconnected)
    }

    private fun sendCommand(
        command: OutgoingCommand,
        completion: ((Boolean) -> Unit)? = null,
    ): Boolean = networkClient?.send(command, completion) ?: false

    private fun startConnection(settings: ConnectionSettings) {
        networkClient?.close()
        networkClient = NetworkClient(settings, this).also { it.start() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindRepeatingKey(button: View, key: RemoteKey) {
        val repeater = RemoteKeyRepeatController()
        val cancel = {
            repeater.update(null)
            button.isPressed = false
        }
        repeatCancellations += cancel
        button.setOnTouchListener { view, event ->
            focusInputWithoutShowingIme()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    repeater.update(key)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancel()
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun bindJoystick(joystick: JoystickView) {
        val repeater = RemoteKeyRepeatController()
        repeatCancellations += {
            repeater.update(null)
            joystick.cancelGesture()
        }
        joystick.listener = JoystickView.Listener { directions ->
            repeater.update(directions.singleOrNull()?.toRemoteKey())
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindGameMovementButton(
        button: View,
        source: GameControlSource,
        direction: JoystickDirection,
    ) {
        val cancel = {
            button.isPressed = false
            gameMovementController.update(source, emptySet())
        }
        repeatCancellations += cancel
        button.setOnTouchListener { view, event ->
            focusInputWithoutShowingIme()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    gameMovementController.update(source, setOf(direction))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancel()
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun bindGameJoystick(joystick: JoystickView) {
        joystick.allowDiagonals = true
        repeatCancellations += {
            joystick.cancelGesture()
            gameMovementController.update(GameControlSource.JOYSTICK, emptySet())
        }
        joystick.listener = JoystickView.Listener { directions ->
            gameMovementController.update(GameControlSource.JOYSTICK, directions)
        }
    }

    private inner class RemoteKeyRepeatController {
        private var currentKey: RemoteKey? = null
        private val repeat = object : Runnable {
            override fun run() {
                val key = currentKey ?: return
                sendCommand(OutgoingCommand.KeyPress(key))
                mainHandler.postDelayed(this, KEY_REPEAT_INTERVAL_MILLIS)
            }
        }

        fun update(key: RemoteKey?) {
            if (key == currentKey) return
            currentKey = null
            mainHandler.removeCallbacks(repeat)
            currentKey = key
            if (key != null) {
                sendCommand(OutgoingCommand.KeyPress(key))
                mainHandler.postDelayed(repeat, KEY_REPEAT_DELAY_MILLIS)
            }
        }
    }

    private fun JoystickDirection.toRemoteKey(): RemoteKey = when (this) {
        JoystickDirection.UP -> RemoteKey.UP
        JoystickDirection.DOWN -> RemoteKey.DOWN
        JoystickDirection.LEFT -> RemoteKey.LEFT
        JoystickDirection.RIGHT -> RemoteKey.RIGHT
    }

    private fun emitPointerClick(button: MouseButton) {
        emitPointerButton(button, ButtonAction.DOWN)
        emitPointerButton(button, ButtonAction.UP)
        focusInputWithoutShowingIme()
    }

    private fun emitPointerButton(button: MouseButton, action: ButtonAction) {
        sendCommand(OutgoingCommand.PointerButton(button, action))
    }

    private fun selectInputMode(deferred: Boolean) {
        if (!inputView.setDeferredMode(deferred)) {
            focusInputWithoutShowingIme()
            return
        }
        deferredModeEnabled = deferred
        sendButton.visibility = if (deferred) View.VISIBLE else View.GONE
        syncShortcutContainer.visibility = if (deferred) View.GONE else View.VISIBLE
        styleInputModeSelector()
        focusInputWithoutShowingIme()
    }

    private fun styleInputModeSelector() {
        styleInputModeOption(synchronousModeButton, selected = !deferredModeEnabled)
        styleInputModeOption(deferredModeButton, selected = deferredModeEnabled)
    }

    private fun styleInputModeOption(option: TextView, selected: Boolean) {
        option.setTextColor(
            if (selected) currentThemeColors.primaryTextArgb else currentThemeColors.secondaryTextArgb,
        )
        option.setTypeface(option.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        option.isSelected = selected
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installPressHaptic(view: View) {
        view.setOnTouchListener { touchedView, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                touchedView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            false
        }
    }

    private fun focusInputWithoutShowingIme() {
        if (inputView.hasFocus()) return
        val previousShowSoftInputOnFocus = inputView.showSoftInputOnFocus
        inputView.showSoftInputOnFocus = false
        inputView.requestFocus()
        inputView.post {
            inputView.showSoftInputOnFocus = previousShowSoftInputOnFocus
        }
    }

    private fun installImeInsetHandling(root: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            installAnimatedImeInsetHandling(root)
            return
        }

        val visibleFrame = Rect()
        var baselineObscuredHeight: Int? = null
        root.viewTreeObserver.addOnGlobalLayoutListener {
            root.getWindowVisibleDisplayFrame(visibleFrame)
            val obscuredHeight = root.rootView.height - visibleFrame.bottom
            val baseline = baselineObscuredHeight
                ?.let { minOf(it, obscuredHeight) }
                ?: obscuredHeight
            baselineObscuredHeight = baseline
            updateLegacyShortcutPanelImeVisibility(
                obscuredHeight > baseline + dp(LEGACY_IME_VISIBILITY_THRESHOLD_DP),
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installLegacyImeLaunchPreparation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        inputView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !legacyImeVisible) {
                pendingImeHide?.let(mainHandler::removeCallbacks)
                pendingImeHide = null
                pendingLegacyImeShowRollback?.let(mainHandler::removeCallbacks)
                shortcutPanel.setImeVisible(true, animate = false)
                val rollback = Runnable {
                    pendingLegacyImeShowRollback = null
                    if (!legacyImeVisible) shortcutPanel.setImeVisible(false, animate = false)
                }
                pendingLegacyImeShowRollback = rollback
                mainHandler.postDelayed(rollback, LEGACY_IME_SHOW_TIMEOUT_MILLIS)
            }
            false
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun installAnimatedImeInsetHandling(root: View) {
        val originalLeft = root.paddingLeft
        val originalTop = root.paddingTop
        val originalRight = root.paddingRight
        val originalBottom = root.paddingBottom
        val panelMotionState = ImePanelMotionState()
        var activeImeAnimation: WindowInsetsAnimation? = null

        fun applyPanelMotion(update: ImePanelMotionUpdate) {
            shortcutPanel.translationY = update.translationY.toFloat()
            when (update.visibility) {
                ImePanelVisibilityUpdate.KEEP -> Unit
                ImePanelVisibilityUpdate.SHOW -> shortcutPanel.setImeVisible(true, animate = false)
                ImePanelVisibilityUpdate.HIDE -> shortcutPanel.setImeVisible(false, animate = false)
            }
        }

        root.setWindowInsetsAnimationCallback(
            object : WindowInsetsAnimation.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimation) {
                    if (animation.typeMask and WindowInsets.Type.ime() == 0) return
                    activeImeAnimation = animation
                    applyPanelMotion(panelMotionState.onAnimationPrepare())
                }

                override fun onProgress(
                    insets: WindowInsets,
                    runningAnimations: MutableList<WindowInsetsAnimation>,
                ): WindowInsets {
                    if (activeImeAnimation != null) {
                        val systemBottom = insets.getInsets(WindowInsets.Type.systemBars()).bottom
                        val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
                        applyPanelMotion(
                            panelMotionState.onAnimationProgress(
                                maxOf(systemBottom, imeBottom),
                            ),
                        )
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimation) {
                    if (animation !== activeImeAnimation) return
                    applyPanelMotion(panelMotionState.onAnimationEnd())
                    activeImeAnimation = null
                }
            },
        )
        root.setOnApplyWindowInsetsListener { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsets.Type.systemBars())
            val imeBottom = windowInsets.getInsets(WindowInsets.Type.ime()).bottom
            applyPanelMotion(
                panelMotionState.onInsetsApplied(
                    visible = windowInsets.isVisible(WindowInsets.Type.ime()),
                    layoutBottom = maxOf(systemBars.bottom, imeBottom),
                ),
            )
            view.setPadding(
                originalLeft + systemBars.left,
                originalTop + systemBars.top,
                originalRight + systemBars.right,
                originalBottom + maxOf(systemBars.bottom, imeBottom),
            )
            windowInsets
        }
        root.requestApplyInsets()
    }

    private fun updateLegacyShortcutPanelImeVisibility(visible: Boolean) {
        if (visible) {
            pendingImeHide?.let(mainHandler::removeCallbacks)
            pendingImeHide = null
            legacyImeVisible = true
            pendingLegacyImeShowRollback?.let(mainHandler::removeCallbacks)
            pendingLegacyImeShowRollback = null
            shortcutPanel.setImeVisible(true, animate = false)
            return
        }
        if (!legacyImeVisible) return
        legacyImeVisible = false
        val hide = Runnable {
            pendingImeHide = null
            shortcutPanel.setImeVisible(false, animate = false)
        }
        pendingImeHide = hide
        mainHandler.postDelayed(hide, IME_HIDE_DEBOUNCE_MILLIS)
    }

    private fun showSettingsDialog() {
        val current = settingsStore.load()
        val themeColors = current.themeSettings.resolve(isSystemDarkTheme())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), 0)
        }
        val hostField = settingsField("电脑 IP 地址", current.host, InputType.TYPE_CLASS_TEXT, themeColors)
        val portField = settingsField("端口", current.port.toString(), InputType.TYPE_CLASS_NUMBER, themeColors)
        val codeField = settingsField(
            "16 位配对码",
            current.pairingCode.chunked(4).joinToString("-"),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
            themeColors,
        )
        val sensitivityLabel = TextView(this).apply {
            text = getString(R.string.pointer_speed, current.pointerSensitivity)
            setTextColor(themeColors.primaryTextArgb)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, 0)
        }
        val sensitivityBar = SeekBar(this).apply {
            id = View.generateViewId()
            max = 150
            progress = ((current.pointerSensitivity - 0.5f) * 100).toInt().coerceIn(0, 150)
            progressDrawable = getDrawable(R.drawable.seekbar_track)
            thumb = getDrawable(R.drawable.seekbar_thumb)
            splitTrack = false
            updateSeekBarAccessibility(
                this,
                getString(R.string.pointer_speed_name),
                getString(R.string.pointer_speed, current.pointerSensitivity),
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val label = getString(R.string.pointer_speed, 0.5f + progress / 100f)
                    sensitivityLabel.text = label
                    seekBar?.let {
                        updateSeekBarAccessibility(it, getString(R.string.pointer_speed_name), label)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        sensitivityLabel.labelFor = sensitivityBar.id
        val accelerationLabel = TextView(this).apply {
            text = getString(R.string.pointer_acceleration, current.pointerAcceleration)
            setTextColor(themeColors.primaryTextArgb)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        }
        val accelerationBar = SeekBar(this).apply {
            id = View.generateViewId()
            max = 200
            progress = ((current.pointerAcceleration - PointerAcceleration.MIN_GAIN) * 100)
                .toInt()
                .coerceIn(0, max)
            progressDrawable = getDrawable(R.drawable.seekbar_track)
            thumb = getDrawable(R.drawable.seekbar_thumb)
            splitTrack = false
            updateSeekBarAccessibility(
                this,
                getString(R.string.pointer_acceleration_name),
                getString(R.string.pointer_acceleration, current.pointerAcceleration),
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val gain = PointerAcceleration.MIN_GAIN + progress / 100f
                    val label = getString(R.string.pointer_acceleration, gain)
                    accelerationLabel.text = label
                    seekBar?.let {
                        updateSeekBarAccessibility(
                            it,
                            getString(R.string.pointer_acceleration_name),
                            label,
                        )
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        accelerationLabel.labelFor = accelerationBar.id
        val scrollStripToggle = CheckBox(this).apply {
            text = getString(R.string.scroll_strip_setting)
            isChecked = current.scrollStripEnabled
            setTextColor(themeColors.primaryTextArgb)
            buttonTintList = toggleTintList(themeColors)
            setPadding(0, dp(10), 0, 0)
        }
        val scrollDetentLabel = TextView(this).apply {
            text = getString(R.string.scroll_detent_spacing, current.scrollDetentSpacingDp)
            setTextColor(themeColors.primaryTextArgb)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        }
        val scrollDetentBar = SeekBar(this).apply {
            id = View.generateViewId()
            max = (ScrollWheelTuning.MAX_DETENT_SPACING_DP - ScrollWheelTuning.MIN_DETENT_SPACING_DP)
                .toInt()
            progress = (current.scrollDetentSpacingDp - ScrollWheelTuning.MIN_DETENT_SPACING_DP)
                .toInt()
                .coerceIn(0, max)
            progressDrawable = getDrawable(R.drawable.seekbar_track)
            thumb = getDrawable(R.drawable.seekbar_thumb)
            splitTrack = false
            updateSeekBarAccessibility(
                this,
                getString(R.string.scroll_detent_spacing_name),
                getString(R.string.scroll_detent_spacing, current.scrollDetentSpacingDp),
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val spacing = ScrollWheelTuning.MIN_DETENT_SPACING_DP + progress
                    val label = getString(R.string.scroll_detent_spacing, spacing)
                    scrollDetentLabel.text = label
                    seekBar?.let {
                        updateSeekBarAccessibility(
                            it,
                            getString(R.string.scroll_detent_spacing_name),
                            label,
                        )
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        scrollDetentLabel.labelFor = scrollDetentBar.id
        val scrollInertiaLabel = TextView(this).apply {
            text = getString(R.string.scroll_inertia, (current.scrollInertiaScale * 100).toInt())
            setTextColor(themeColors.primaryTextArgb)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        }
        val scrollInertiaBar = SeekBar(this).apply {
            id = View.generateViewId()
            max = (ScrollWheelTuning.MAX_INERTIA_SCALE * 10).toInt()
            progress = (current.scrollInertiaScale * 10).toInt().coerceIn(0, max)
            progressDrawable = getDrawable(R.drawable.seekbar_track)
            thumb = getDrawable(R.drawable.seekbar_thumb)
            splitTrack = false
            updateSeekBarAccessibility(
                this,
                getString(R.string.scroll_inertia_name),
                getString(R.string.scroll_inertia, progress * 10),
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val label = getString(R.string.scroll_inertia, progress * 10)
                    scrollInertiaLabel.text = label
                    seekBar?.let {
                        updateSeekBarAccessibility(it, getString(R.string.scroll_inertia_name), label)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        scrollInertiaLabel.labelFor = scrollInertiaBar.id
        val scrollHapticLabel = TextView(this).apply {
            text = getString(R.string.scroll_haptic, scrollHapticProfileLabel(current.scrollHapticProfile))
            setTextColor(themeColors.primaryTextArgb)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(10), 0, 0)
        }
        val scrollHapticBar = SeekBar(this).apply {
            id = View.generateViewId()
            max = ScrollHapticProfile.entries.lastIndex
            progress = current.scrollHapticProfile.ordinal
            progressDrawable = getDrawable(R.drawable.seekbar_track)
            thumb = getDrawable(R.drawable.seekbar_thumb)
            splitTrack = false
            updateSeekBarAccessibility(
                this,
                getString(R.string.scroll_haptic_name),
                getString(R.string.scroll_haptic, scrollHapticProfileLabel(current.scrollHapticProfile)),
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val profile = ScrollHapticProfile.entries[progress]
                    val label = getString(R.string.scroll_haptic, scrollHapticProfileLabel(profile))
                    scrollHapticLabel.text = label
                    seekBar?.let {
                        updateSeekBarAccessibility(it, getString(R.string.scroll_haptic_name), label)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        scrollHapticLabel.labelFor = scrollHapticBar.id
        val languageShortcutSelector = createShortcutSelector(
            title = "中 / EN 切换快捷键",
            choices = LANGUAGE_SHORTCUT_CHOICES,
            selected = current.languageToggleShortcut,
            themeColors = themeColors,
        )
        val inputMethodShortcutSelector = createShortcutSelector(
            title = "输入法切换快捷键",
            choices = INPUT_METHOD_SHORTCUT_CHOICES,
            selected = current.inputMethodShortcut,
            themeColors = themeColors,
        )
        val followSystemToggle = CheckBox(this).apply {
            setText(R.string.theme_follow_system)
            isChecked = current.themeSettings.mode == ThemeMode.FOLLOW_SYSTEM
            setTextColor(themeColors.primaryTextArgb)
            buttonTintList = toggleTintList(themeColors)
            setPadding(0, dp(10), 0, 0)
        }
        val forceDarkToggle = CheckBox(this).apply {
            setText(R.string.theme_force_dark)
            isChecked = current.themeSettings.mode == ThemeMode.DARK
            setTextColor(themeColors.primaryTextArgb)
            buttonTintList = toggleTintList(themeColors)
            setPadding(0, 0, 0, dp(4))
        }
        fun updateForcedThemeToggle() {
            forceDarkToggle.isEnabled = !followSystemToggle.isChecked
            forceDarkToggle.alpha = if (forceDarkToggle.isEnabled) 1f else 0.45f
        }
        followSystemToggle.setOnCheckedChangeListener { _, _ -> updateForcedThemeToggle() }
        updateForcedThemeToggle()

        val themeAiPrompt = getString(R.string.theme_ai_prompt)
        val themePromptView = TextView(this).apply {
            text = themeAiPrompt
            setTextColor(themeColors.secondaryTextArgb)
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        val themeFrameworkEditor = themeFrameworkEditor(
            ThemeFramework.format(current.themeSettings.palettes),
            themeColors,
        )
        val copyThemePromptButton = shortcutSelectorField(
            getString(R.string.theme_copy_prompt_and_framework),
            themeColors,
        ).apply {
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.theme_clipboard_label),
                        "$themeAiPrompt\n\n${themeFrameworkEditor.text}",
                    ),
                )
                Toast.makeText(
                    this@MainActivity,
                    R.string.theme_copy_confirmation,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        listOf(sensitivityBar, accelerationBar, scrollDetentBar, scrollInertiaBar, scrollHapticBar)
            .forEach { styleSettingsSeekBar(it, themeColors) }
        content.addView(hostField)
        content.addView(portField)
        content.addView(codeField)
        content.addView(sensitivityLabel)
        content.addView(sensitivityBar)
        content.addView(accelerationLabel)
        content.addView(accelerationBar)
        content.addView(scrollStripToggle)
        content.addView(scrollDetentLabel)
        content.addView(scrollDetentBar)
        content.addView(scrollInertiaLabel)
        content.addView(scrollInertiaBar)
        content.addView(scrollHapticLabel)
        content.addView(scrollHapticBar)
        content.addView(settingsLabel("中 / EN 切换", themeColors))
        content.addView(languageShortcutSelector.view)
        content.addView(settingsLabel("输入法切换（地球键）", themeColors))
        content.addView(inputMethodShortcutSelector.view)
        content.addView(settingsLabel(getString(R.string.theme_mode_section), themeColors))
        content.addView(followSystemToggle)
        content.addView(forceDarkToggle)
        content.addView(settingsLabel(getString(R.string.theme_prompt_section), themeColors))
        content.addView(themePromptView)
        content.addView(copyThemePromptButton)
        content.addView(settingsLabel(getString(R.string.theme_framework_section), themeColors))
        content.addView(themeFrameworkEditor)
        val scrollContent = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("连接设置")
            .setView(scrollContent)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存并连接", null)
            .create()
        dialog.setOnShowListener {
            styleSettingsDialog(dialog, themeColors)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val host = hostField.text.toString().trim()
                val port = portField.text.toString().toIntOrNull()
                val pairingCode = AuthenticationProof.normalizePairingCode(codeField.text.toString())
                val themePalettes = try {
                    ThemeFramework.parse(themeFrameworkEditor.text.toString())
                } catch (error: IllegalArgumentException) {
                    themeFrameworkEditor.error = error.message ?: "颜色配置格式不正确"
                    null
                }
                var valid = true
                if (host.isEmpty()) {
                    hostField.error = "请输入电脑 IP 地址"
                    valid = false
                }
                if (port == null || port !in 1..65535) {
                    portField.error = "端口必须在 1–65535 之间"
                    valid = false
                }
                if (pairingCode.length != 16 || pairingCode.any { it !in BASE32_ALPHABET }) {
                    codeField.error = "请输入电脑端显示的 16 位配对码"
                    valid = false
                }
                if (themePalettes == null) valid = false
                if (!valid) return@setOnClickListener

                val sensitivity = 0.5f + sensitivityBar.progress / 100f
                val acceleration = PointerAcceleration.MIN_GAIN + accelerationBar.progress / 100f
                val scrollDetentSpacing =
                    ScrollWheelTuning.MIN_DETENT_SPACING_DP + scrollDetentBar.progress
                val scrollInertiaScale = scrollInertiaBar.progress / 10f
                val scrollHapticProfile = ScrollHapticProfile.entries[scrollHapticBar.progress]
                settingsStore.saveConnection(
                    host = host,
                    port = port!!,
                    pairingCode = pairingCode,
                    pointerSensitivity = sensitivity,
                    pointerAcceleration = acceleration,
                    scrollStripEnabled = scrollStripToggle.isChecked,
                    scrollDetentSpacingDp = scrollDetentSpacing,
                    scrollInertiaScale = scrollInertiaScale,
                    scrollHapticProfile = scrollHapticProfile,
                    languageToggleShortcut = languageShortcutSelector.selected,
                    inputMethodShortcut = inputMethodShortcutSelector.selected,
                    themeSettings = ThemeSettings(
                        mode = when {
                            followSystemToggle.isChecked -> ThemeMode.FOLLOW_SYSTEM
                            forceDarkToggle.isChecked -> ThemeMode.DARK
                            else -> ThemeMode.LIGHT
                        },
                        palettes = themePalettes!!,
                    ),
                )
                val updated = settingsStore.load()
                val connectionChanged = current.requiresConnectionRestart(updated)
                applySettingsToUi(updated)
                if (activityVisible && (networkClient == null || connectionChanged)) {
                    startConnection(updated)
                }
                dialog.dismiss()
                focusInputWithoutShowingIme()
            }
        }
        dialog.setOnDismissListener { focusInputWithoutShowingIme() }
        dialog.show()
    }

    private fun settingsField(
        label: String,
        value: String,
        inputType: Int,
        themeColors: ThemeColors,
    ): EditText =
        EditText(this).apply {
            hint = label
            setText(value)
            this.inputType = inputType
            background = getDrawable(R.drawable.input_background)
            backgroundTintList = ColorStateList.valueOf(themeColors.inputBackgroundArgb)
            setTextColor(themeColors.primaryTextArgb)
            setHintTextColor(themeColors.secondaryTextArgb)
            isSingleLine = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            }
        }

    private fun themeFrameworkEditor(value: String, themeColors: ThemeColors): EditText =
        EditText(this).apply {
            hint = getString(R.string.theme_framework_hint)
            setText(value)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            background = getDrawable(R.drawable.input_background)
            backgroundTintList = ColorStateList.valueOf(themeColors.inputBackgroundArgb)
            setTextColor(themeColors.primaryTextArgb)
            setHintTextColor(themeColors.secondaryTextArgb)
            setHorizontallyScrolling(false)
            minLines = 14
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(18)
            }
        }

    private fun settingsLabel(label: String, themeColors: ThemeColors): TextView =
        TextView(this).apply {
            text = label
            setTextColor(themeColors.secondaryTextArgb)
            textSize = 14f
            setPadding(dp(4), dp(12), 0, 0)
        }

    private fun shortcutSelectorField(value: String, themeColors: ThemeColors): TextView =
        TextView(this).apply {
            text = value
            setTextColor(themeColors.primaryTextArgb)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.input_background)
            backgroundTintList = ColorStateList.valueOf(themeColors.inputBackgroundArgb)
            isClickable = true
            isFocusable = false
            minHeight = dp(44)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
            }
            installPressHaptic(this)
        }

    private fun <T> createShortcutSelector(
        title: String,
        choices: List<ShortcutChoice<T>>,
        selected: T,
        themeColors: ThemeColors,
    ): SettingsShortcutSelection<T> {
        val selection = SettingsShortcutSelection(
            view = shortcutSelectorField(shortcutLabel(selected, choices), themeColors),
            selected = selected,
        )
        selection.view.setOnClickListener {
            showShortcutChoiceDialog(
                title = title,
                choices = choices,
                selected = selection.selected,
                themeColors = themeColors,
            ) { shortcut ->
                selection.selected = shortcut
                selection.view.text = shortcutLabel(shortcut, choices)
            }
        }
        return selection
    }

    private fun <T> showShortcutChoiceDialog(
        title: String,
        choices: List<ShortcutChoice<T>>,
        selected: T,
        themeColors: ThemeColors,
        onSelected: (T) -> Unit,
    ) {
        val optionList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(14))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(optionList)
            .create()
        choices.forEach { choice ->
            optionList.addView(
                TextView(this).apply {
                    text = choice.label
                    setTextColor(
                        if (choice.value == selected) {
                            themeColors.primaryTextArgb
                        } else {
                            themeColors.secondaryTextArgb
                        },
                    )
                    setTypeface(typeface, if (choice.value == selected) Typeface.BOLD else Typeface.NORMAL)
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    background = getDrawable(R.drawable.direction_button_background)
                    isClickable = true
                    minHeight = dp(48)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    installPressHaptic(this)
                    setOnClickListener {
                        onSelected(choice.value)
                        dialog.dismiss()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(getDrawable(R.drawable.dialog_background)?.mutate()?.apply {
                    setTint(themeColors.backgroundArgb)
                })
                decorView.elevation = 0f
            }
            val alertTitleId = resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<TextView>(alertTitleId)?.setTextColor(themeColors.primaryTextArgb)
        }
        dialog.show()
    }

    private fun <T> shortcutLabel(
        shortcut: T,
        choices: List<ShortcutChoice<T>>,
    ): String = choices.first { it.value == shortcut }.label

    private fun updateSeekBarAccessibility(seekBar: SeekBar, name: String, formattedLabel: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            seekBar.contentDescription = name
            seekBar.stateDescription = formattedLabel.substringAfter('：', formattedLabel)
        } else {
            seekBar.contentDescription = formattedLabel
        }
    }

    private fun scrollHapticProfileLabel(profile: ScrollHapticProfile): String = getString(
        when (profile) {
            ScrollHapticProfile.OFF -> R.string.scroll_haptic_off
            ScrollHapticProfile.LIGHT -> R.string.scroll_haptic_light
            ScrollHapticProfile.STANDARD -> R.string.scroll_haptic_standard
            ScrollHapticProfile.STRONG -> R.string.scroll_haptic_strong
        },
    )

    private fun styleSettingsSeekBar(seekBar: SeekBar, themeColors: ThemeColors) {
        seekBar.progressTintList = ColorStateList.valueOf(themeColors.primaryTextArgb)
        seekBar.progressBackgroundTintList = ColorStateList.valueOf(themeColors.inputBackgroundArgb)
        seekBar.thumbTintList = ColorStateList.valueOf(themeColors.primaryTextArgb)
    }

    private fun toggleTintList(themeColors: ThemeColors): ColorStateList = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(themeColors.primaryTextArgb, themeColors.iconArgb),
    )

    private fun styleSettingsDialog(dialog: AlertDialog, themeColors: ThemeColors) {
        dialog.window?.apply {
            setBackgroundDrawable(getDrawable(R.drawable.dialog_background)?.mutate()?.apply {
                setTint(themeColors.backgroundArgb)
            })
            decorView.elevation = 0f
        }
        val alertTitleId = resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(alertTitleId)?.setTextColor(themeColors.primaryTextArgb)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val buttonBar = positiveButton.parent as? LinearLayout
        if (buttonBar != null && negativeButton.parent === buttonBar) {
            buttonBar.removeAllViews()
            buttonBar.apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(12), dp(22), dp(18))
                showDividers = LinearLayout.SHOW_DIVIDER_NONE
            }
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                weightSum = 2f
            }
            negativeButton.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginEnd = dp(6)
            }
            positiveButton.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(6)
            }
            buttonRow.addView(negativeButton)
            buttonRow.addView(positiveButton)
            buttonBar.addView(
                buttonRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        styleDialogAction(negativeButton, themeColors, bold = false)
        styleDialogAction(positiveButton, themeColors, bold = true)
    }

    private fun styleDialogAction(button: TextView, themeColors: ThemeColors, bold: Boolean) {
        button.apply {
            setTextColor(
                if (bold) themeColors.primaryTextArgb else themeColors.secondaryTextArgb,
            )
            textSize = 15f
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.control_button_background)
            backgroundTintList = surfaceTintList(themeColors.inputBackgroundArgb, themeColors.primaryTextArgb)
            elevation = 0f
            stateListAnimator = null
            minimumWidth = 0
            minimumHeight = dp(48)
            includeFontPadding = false
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
            installPressHaptic(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun applySettingsToUi(settings: ConnectionSettings) {
        touchpadView.sensitivity = settings.pointerSensitivity
        touchpadView.maximumAccelerationGain = settings.pointerAcceleration
        scrollStripView.configure(
            detentSpacingDp = settings.scrollDetentSpacingDp,
            inertiaScale = settings.scrollInertiaScale,
            hapticProfile = settings.scrollHapticProfile,
        )
        scrollStripView.visibility = if (settings.scrollStripEnabled) View.VISIBLE else View.GONE
        val themeColors = settings.themeSettings.resolve(isSystemDarkTheme())
        currentThemeColors = themeColors
        languageToggleShortcut = settings.languageToggleShortcut
        inputMethodShortcut = settings.inputMethodShortcut
        findViewById<View>(R.id.mainRoot).setBackgroundColor(themeColors.backgroundArgb)
        findViewById<View>(R.id.inputContainer).backgroundTintList =
            ColorStateList.valueOf(themeColors.inputBackgroundArgb)
        touchpadView.backgroundTintList = ColorStateList.valueOf(themeColors.touchpadBackgroundArgb)
        shortcutPanel.applyTheme(themeColors)
        statusView.setTextColor(themeColors.primaryTextArgb)
        inputView.setTextColor(themeColors.primaryTextArgb)
        inputView.setHintTextColor(themeColors.secondaryTextArgb)
        styleInputModeSelector()
        findViewById<TextView>(R.id.touchpadHint).setTextColor(themeColors.secondaryTextArgb)
        findViewById<TextView>(R.id.escapeButton).setTextColor(themeColors.iconArgb)
        findViewById<TextView>(R.id.rightClickButton).setTextColor(themeColors.iconArgb)
        val controlSurfaceTint = surfaceTintList(
            themeColors.inputBackgroundArgb,
            themeColors.primaryTextArgb,
        )
        findViewById<TextView>(R.id.escapeButton).backgroundTintList = controlSurfaceTint
        findViewById<TextView>(R.id.rightClickButton).backgroundTintList = controlSurfaceTint
        scrollStripView.setIndicatorColor(themeColors.iconArgb)

        val iconTint = ColorStateList.valueOf(themeColors.iconArgb)
        listOf(
            R.id.settingsButton,
            R.id.sendButton,
            R.id.inputMethodButton,
            R.id.upButton,
            R.id.downButton,
            R.id.leftButton,
            R.id.rightButton,
        )
            .forEach { id -> findViewById<ImageButton>(id).imageTintList = iconTint }
        listOf(
            R.id.languageToggleButton,
            R.id.gameUpButton,
            R.id.gameDownButton,
            R.id.gameLeftButton,
            R.id.gameRightButton,
        ).forEach { id -> findViewById<TextView>(id).setTextColor(themeColors.iconArgb) }
        findViewById<JoystickView>(R.id.joystick).setKnobColor(themeColors.iconArgb)
        findViewById<JoystickView>(R.id.gameJoystick).setKnobColor(themeColors.iconArgb)

        window.statusBarColor = themeColors.backgroundArgb
        window.navigationBarColor = themeColors.backgroundArgb
        applySystemBarIconContrast(themeColors.backgroundArgb)
    }

    private fun isSystemDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun surfaceTintList(normalColor: Int, pressedOverlayColor: Int): ColorStateList =
        ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(),
            ),
            intArrayOf(blendColors(normalColor, pressedOverlayColor, 0.14f), normalColor),
        )

    private fun blendColors(background: Int, foreground: Int, foregroundRatio: Float): Int {
        val backgroundRatio = 1f - foregroundRatio
        return Color.rgb(
            (Color.red(background) * backgroundRatio + Color.red(foreground) * foregroundRatio).toInt(),
            (Color.green(background) * backgroundRatio + Color.green(foreground) * foregroundRatio).toInt(),
            (Color.blue(background) * backgroundRatio + Color.blue(foreground) * foregroundRatio).toInt(),
        )
    }

    @Suppress("DEPRECATION")
    private fun applySystemBarIconContrast(backgroundColor: Int) {
        val useDarkIcons = Color.luminance(backgroundColor) >= 0.5f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask =
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (useDarkIcons) mask else 0, mask)
        } else {
            val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = if (useDarkIcons) {
                window.decorView.systemUiVisibility or mask
            } else {
                window.decorView.systemUiVisibility and mask.inv()
            }
        }
    }

    private fun isComplete(settings: ConnectionSettings): Boolean =
        settings.host.isNotBlank() &&
            settings.port in 1..65535 &&
            settings.pairingCode.length == 16 &&
            settings.pairingCode.all { it in BASE32_ALPHABET }

    private fun showStatus(text: String, colorResource: Int) {
        statusView.text = SpannableString("● $text").apply {
            setSpan(
                ForegroundColorSpan(getColor(colorResource)),
                0,
                1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        data class ShortcutChoice<T>(val value: T, val label: String)
        data class SettingsShortcutSelection<T>(val view: TextView, var selected: T)

        val LANGUAGE_SHORTCUT_CHOICES = listOf(
            ShortcutChoice(LanguageToggleShortcut.SHIFT, "Shift（默认）"),
            ShortcutChoice(
                LanguageToggleShortcut.CONTROL_SPACE,
                "Windows / macOS · Ctrl / Control + Space",
            ),
            ShortcutChoice(LanguageToggleShortcut.CAPS_LOCK, "macOS · Caps Lock"),
        )
        val INPUT_METHOD_SHORTCUT_CHOICES = listOf(
            ShortcutChoice(InputMethodShortcut.WINDOWS_SPACE, "Windows · Win + Space（默认）"),
            ShortcutChoice(InputMethodShortcut.ALT_SHIFT, "Windows · Alt + Shift"),
            ShortcutChoice(InputMethodShortcut.CONTROL_SHIFT, "Windows · Ctrl + Shift"),
            ShortcutChoice(InputMethodShortcut.CONTROL_SPACE, "macOS · Control + Space"),
        )
        const val KEY_REPEAT_DELAY_MILLIS = 350L
        const val KEY_REPEAT_INTERVAL_MILLIS = 70L
        const val IME_HIDE_DEBOUNCE_MILLIS = 300L
        const val LEGACY_IME_SHOW_TIMEOUT_MILLIS = 500L
        const val LEGACY_IME_VISIBILITY_THRESHOLD_DP = 24
        const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        const val STATE_DEFERRED_MODE = "deferred_mode"
        const val STATE_DEFERRED_DRAFT = "deferred_draft"
    }
}
