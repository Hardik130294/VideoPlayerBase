package com.hardik.videoplayerbase

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hardik.videoplayerbase.databinding.ActivityPlayerBinding
import com.hardik.videoplayerbase.databinding.BoosterBinding
import com.hardik.videoplayerbase.databinding.MoreFeaturesBinding
import com.hardik.videoplayerbase.databinding.SpeedDialogBinding
import java.text.DecimalFormat
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlin.system.exitProcess

class PlayerActivity : AppCompatActivity(), AudioManager.OnAudioFocusChangeListener {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var runnable: Runnable
    private var isSubtitle: Boolean = true
    private var moreTime: Int = 0

    companion object {
        private var audioManager: AudioManager? = null
        private lateinit var player: ExoPlayer
        lateinit var playerList: ArrayList<Video>
        var position: Int = -1
        var repeat: Boolean = false
        private var isFullscreen: Boolean = false
        private var isLocked: Boolean = false

        @SuppressLint("StaticFieldLeak")
        private lateinit var trackSelector: DefaultTrackSelector
        private lateinit var loudnessEnhancer: LoudnessEnhancer
        private var speed: Float = 1.0f
        private var timer:Timer? = null
        var pipStatus:Int = 0
        var nowPlayingId: String = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //hide top title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        //for knock display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setTheme(R.style.playerActivityTheme)
        setContentView(binding.root)
        // for immersive mode (fullscreen mode) this for bottom button of android
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        initializeLayout()
        initializeBinding()
        binding.forwardFL.setOnClickListener(DoubleClickListener(callback = object: DoubleClickListener.Callback{
            override fun doubleClicked() {
                binding.playerView.showController()
                binding.forwardBtn.visibility = View.VISIBLE
                player.seekTo(player.currentPosition + 10000L)
                moreTime = 0
            }

        }))
        binding.rewindFL.setOnClickListener(DoubleClickListener(callback = object: DoubleClickListener.Callback{
            override fun doubleClicked() {
                binding.playerView.showController()
                binding.rewindBtn.visibility = View.VISIBLE
                player.seekTo(player.currentPosition - 10000L)
                moreTime = 0
            }

        }))
    }

    private fun initializeLayout() {
        when (intent.getStringExtra("class")) {
            "AllVideos" -> {
                playerList = ArrayList()
                playerList.addAll(MainActivity.videoList)
                createPlayer()
            }
            "FolderActivity" -> {
                playerList = ArrayList()
                playerList.addAll(FoldersActivity.currentFolderVideo)
                createPlayer()
            }
            "SearchedVideos" -> {
                playerList = ArrayList()
                playerList.addAll(MainActivity.searchList)
                createPlayer()
            }
            "NowPlaying" ->{
                //initialize speed again
                speed = 1.0f
                binding.videoTitle.text = playerList[position].title
                binding.videoTitle.isSelected = true
                binding.playerView.player = player
                playVideo()
                playInFullscreen(enable = isFullscreen)
                setVisibility()
            }
        }
        if (repeat) binding.repeatBtn.setImageResource(R.drawable.repeat_icon_one)
        else binding.repeatBtn.setImageResource(R.drawable.repeat_icon_all)
    }

    @SuppressLint("SetTextI18n", "ObsoleteSdkInt")
    private fun initializeBinding() {
        binding.backBtn.setOnClickListener {
            finish()//when click this button activity close
        }
        binding.playPauseBtn.setOnClickListener {
            //if player is playing so pause either play
            if (player.isPlaying) pauseVideo()
            else playVideo()
        }
        binding.nextBtn.setOnClickListener { nextPrevVideo() }
        binding.prevBtn.setOnClickListener { nextPrevVideo(isNext = false) }
        binding.repeatBtn.setOnClickListener {
            if (repeat) {
                repeat = false
                player.repeatMode = Player.REPEAT_MODE_OFF
                binding.repeatBtn.setImageResource(R.drawable.repeat_icon_off)
            } else {
                repeat = true
                player.repeatMode = Player.REPEAT_MODE_ONE
                binding.repeatBtn.setImageResource(R.drawable.repeat_icon_one)
            }
        }
        binding.fullScreenBtn.setOnClickListener {
            if (isFullscreen) {
                isFullscreen = false
                playInFullscreen(enable = false)
            } else {
                isFullscreen = true
                playInFullscreen(enable = true)
            }
        }

        binding.lockButton.setOnClickListener {
            if (!isLocked) {
                //for hide control
                isLocked = true
                binding.playerView.hideController()
                binding.playerView.useController = false
                binding.lockButton.setImageResource(R.drawable.lock_close_icon)
            } else {
                //for show control
                isLocked = false
                binding.playerView.useController = true
                binding.playerView.showController()
                binding.lockButton.setImageResource(R.drawable.lock_open_icon)

            }
        }

        binding.moreFeatures.setOnClickListener {
            pauseVideo()//first pause video
            val customDialog =
                LayoutInflater.from(this).inflate(R.layout.more_features, binding.root, false)
            val bindingMF = MoreFeaturesBinding.bind(customDialog)
            val dialog = MaterialAlertDialogBuilder(this)
                .setView(customDialog)
                .setOnCancelListener { playVideo() }
                .setBackground(ColorDrawable(0x803700B3.toInt()))
                .create()
            dialog.show()

            //for audio Track
            bindingMF.audioTrack.setOnClickListener {
                dialog.dismiss()
                playVideo()

                val audioTrack = ArrayList<String>()
                for (i in 0 until player.currentTrackGroups.length) {
                    if (player.currentTrackGroups.get(i)
                            .getFormat(0).selectionFlags == C.SELECTION_FLAG_DEFAULT
                    ) {//if that track is selectable so select it.
                        audioTrack.add(
                            Locale(
                                player.currentTrackGroups.get(i).getFormat(0).language.toString()
                            ).displayLanguage
                        )//Locale is short form to long form convert
                    }
                }
                val tempTracks =
                    audioTrack.toArray(arrayOfNulls<CharSequence>(audioTrack.size))//convert arrayList to CharSequence
                MaterialAlertDialogBuilder(this, R.style.alertDialog)
                    .setTitle("Select Language")
                    .setOnCancelListener { playVideo() }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .setItems(tempTracks) { _, position ->
                        Toast.makeText(this, audioTrack[position] + " selected", Toast.LENGTH_SHORT)
                            .show()
//                        trackSelector.setParameters(trackSelector.buildUponParameters().setPreferredAudioLanguage(audioTrack[position]))// change audio track on video
                        // To switch audio track
                        val parameters = trackSelector.buildUponParameters()
                            .setPreferredAudioLanguage(audioTrack[position])
                        trackSelector.setParameters(parameters)

                    }
                    .create()
                    .show()
            }

            bindingMF.subtitlesBtn.setOnClickListener {
                if (isSubtitle) {
                    //is on
                    trackSelector.parameters =
                        DefaultTrackSelector.ParametersBuilder(this).setRendererDisabled(
                            C.TRACK_TYPE_VIDEO, true
                        ).build()
                    Toast.makeText(this, "Subtitles Off", Toast.LENGTH_SHORT).show()
                    isSubtitle = false
                } else {
                    //is off
                    trackSelector.parameters =
                        DefaultTrackSelector.ParametersBuilder(this).setRendererDisabled(
                            C.TRACK_TYPE_VIDEO, false
                        ).build()
                    Toast.makeText(this, "Subtitles On", Toast.LENGTH_SHORT).show()
                    isSubtitle = true
                }
                dialog.dismiss()
                playVideo()
            }

            bindingMF.audioBooster.setOnClickListener {
                dialog.dismiss()
                val customDialogB =
                    LayoutInflater.from(this).inflate(R.layout.booster, binding.root, false)
                val bindingB = BoosterBinding.bind(customDialog)
                val dialogB = MaterialAlertDialogBuilder(this).setView(customDialogB)
                    .setOnCancelListener { playVideo() }
                    .setPositiveButton("OK") { self, _ ->
                        loudnessEnhancer.setTargetGain(bindingB.verticalBar.progress * 100)
                        playVideo()
                        self.dismiss()
                    }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .create()
                bindingB.verticalBar.progress = loudnessEnhancer.targetGain.toInt() / 100
                bindingB.progressText.text =
                    "Audio Booster\n\n ${loudnessEnhancer.targetGain.toInt() / 10}%"
                bindingB.verticalBar.setOnProgressChangeListener {
                    bindingB.progressText.text = "Audio Booster\n\n ${it * 10}%"
                }
                dialogB.show()
            }

            bindingMF.speedBtn.setOnClickListener {
                dialog.dismiss()
                playVideo()
                val customDialogS =
                    LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
                val bindingS = SpeedDialogBinding.bind(customDialogS)
                val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                    .setCancelable(false)
                    .setPositiveButton("OK") { self, _ ->
                        self.dismiss()
                    }
                    .setBackground(ColorDrawable(0x803700B3.toInt()))
                    .create()
                dialogS.show()
                bindingS.speedTxt.text = "${DecimalFormat("#.##").format(speed)} X"
                bindingS.minusBtn.setOnClickListener {
                    changeSped(isIncrement = false)
                    bindingS.speedTxt.text = "${DecimalFormat("#.##").format(speed)} X"
                }
                bindingS.plusBtn.setOnClickListener {
                    changeSped(isIncrement = true)
                    bindingS.speedTxt.text = "${DecimalFormat("#.##").format(speed)} X"
                }
            }

            bindingMF.sleepTimeBtn.setOnClickListener {
                dialog.dismiss()
                if(timer != null) Toast.makeText(this,"Timer is Already Running!!\nClose app to rest time!!",Toast.LENGTH_SHORT).show()
                else{
                    var sleepTime = 15
                    val customDialogS = LayoutInflater.from(this).inflate(R.layout.speed_dialog, binding.root, false)
                    val bindingS = SpeedDialogBinding.bind(customDialogS)
                    val dialogS = MaterialAlertDialogBuilder(this).setView(customDialogS)
                        .setCancelable(false)
                        .setPositiveButton("OK") { self, _ ->
                            // initialize timer
                            timer = Timer()
                            val task = object :TimerTask(){
                                override fun run() {
                                    moveTaskToBack(true)//this two line write app is completely close
                                    exitProcess(1)//only use that, app also close but again start
                                }
                            }
                            timer!!.schedule(task,sleepTime*60*1000.toLong())
                            self.dismiss()
                            playVideo()
                        }
                        .setBackground(ColorDrawable(0x803700B3.toInt()))
                        .create()
                    dialogS.show()
                    bindingS.speedTxt.text = "$sleepTime Min"
                    bindingS.minusBtn.setOnClickListener {
                        if (sleepTime > 15) sleepTime -= 15
                        bindingS.speedTxt.text = "$sleepTime Min"
                    }
                    bindingS.plusBtn.setOnClickListener {
                        if (sleepTime < 120)sleepTime += 15
                        bindingS.speedTxt.text = "$sleepTime Min"
                    }
                }
            }

            bindingMF.pipModeBtn.setOnClickListener{
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE,android.os.Process.myUid(),packageName) == AppOpsManager.MODE_ALLOWED
                }else{
                    false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    //when permission is granted
                    if (status) {
                        this.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                        dialog.dismiss()
                        binding.playerView.hideController()
                        playVideo()
                        pipStatus = 0
                    }
                    else {
    //                    when permission isn't granted, so requesting for permission
                            val intent = Intent(
                                "android.settings.PICTURE_IN_PICTURE_SETTINGS",
                                Uri.parse("package:${packageName}")
                            )
                            startActivity(intent)
                    }
                }else{
                    Toast.makeText(this,"Feature not supported!!",Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    playVideo()
                }
            }
        }
    }

    private fun createPlayer() {
        try {
            player.release()//for release all old resource in side stored
        } catch (e: Exception) {
            e.printStackTrace()
        }
        //initialize speed again
        speed = 1.0f
        //initialize trackSelector
        trackSelector = DefaultTrackSelector(this)

        binding.videoTitle.text = playerList[position].title
        binding.videoTitle.isSelected = true
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
        binding.playerView.player = player
        val mediaItem = MediaItem.fromUri(playerList[position].artUri)//directly play
        player.setMediaItem(mediaItem)
        player.prepare()
        playVideo()

        //add completion listener
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_ENDED) {//when video finish
                    nextPrevVideo()
                }
            }
        })

        playInFullscreen(enable = isFullscreen)//when value is contain isFullscreen val,that is set.

        setVisibility()

        //initialize loudnessEnhancer
        loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
        loudnessEnhancer.enabled = true

        //initialize and store id. for when again play same video
        nowPlayingId = playerList[position].id
    }

    private fun playVideo() {
        binding.playPauseBtn.setImageResource(R.drawable.pause_icon)
        player.play()
    }

    private fun pauseVideo() {
        binding.playPauseBtn.setImageResource(R.drawable.play_icon)
        player.pause()
    }

    private fun nextPrevVideo(isNext: Boolean = true) {
        if (isNext) setPosition()
        else setPosition(isIncrement = false)
        createPlayer()
    }

    private fun setPosition(isIncrement: Boolean = true) {
        if (!repeat) {
            if (isIncrement) {
                if (playerList.size - 1 == position) position =
                    0 //if list size is last item so set 0 index
                else ++position
            } else {
                if (position == 0)
                    position = playerList.size - 1 //set last position of list
                else --position
            }
        }
    }

    private fun playInFullscreen(enable: Boolean) {
        if (enable) {
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            binding.fullScreenBtn.setImageResource(R.drawable.fullscreen_exit_icon)
        } else {

            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            binding.fullScreenBtn.setImageResource(R.drawable.fullscreen_icon)
        }
    }

    private fun setVisibility() {
        runnable = Runnable {
            if (binding.playerView.isControllerVisible) changeVisibility(View.VISIBLE)
            else changeVisibility(View.INVISIBLE)
            Handler(Looper.getMainLooper()).postDelayed(runnable, 200)
        }
        Handler(Looper.getMainLooper()).postDelayed(runnable, 0)
    }

    private fun changeVisibility(visibility: Int) {
        binding.topController.visibility = visibility
        binding.bottomController.visibility = visibility
        binding.playPauseBtn.visibility = visibility

        //when lock is close that time it's not hide but always show
        if (isLocked) binding.lockButton.visibility = View.VISIBLE
        // when lock is open that time it's work
        else binding.lockButton.visibility = visibility

        if (moreTime == 2){
            binding.rewindBtn.visibility = View.GONE
            binding.forwardBtn.visibility = View.GONE
        }else ++moreTime

        //for lock screen function stop
        binding.forwardFL.visibility = visibility
        binding.rewindFL.visibility = visibility
    }

    private fun changeSped(isIncrement: Boolean) {
        if (isIncrement) {
            if (speed <= 2.9f) {
                speed += 0.10f
            }
        } else {
            if (speed > 0.20f) {
                speed -= 0.10f
            }
        }
        player.setPlaybackSpeed(speed)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (pipStatus != 0){
            finish()//close earlier activity and start new one activity for new pip mode
            val intent = Intent(this,PlayerActivity::class.java)
            when(pipStatus){
                1 -> intent.putExtra("class","FolderActivity")//intent get from the videoAdapter
                2 -> intent.putExtra("class","SearchedVideos")//intent get from the videoAdapter
                3 -> intent.putExtra("class","AllVideos")//intent get from the videoAdapter
            }
            startActivity(intent)
        }
        if(!isInPictureInPictureMode) pauseVideo() //when we not in pip mode our video is pause
    }

    override fun onDestroy() {
        super.onDestroy()
//        player.release()
        player.pause()
        audioManager?.abandonAudioFocus(this)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange <= 0) pauseVideo()// video pause when app is in background
    }

    override fun onResume() {
        super.onResume()
        if (audioManager == null)audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager!!.requestAudioFocus(this,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)
    }
}