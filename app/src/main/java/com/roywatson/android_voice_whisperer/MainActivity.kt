package com.roywatson.android_voice_whisperer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.roywatson.android_voice_whisperer.ui.VoiceWhispererApp
import com.roywatson.android_voice_whisperer.ui.theme.Android_voice_whispererTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Android_voice_whispererTheme {
                VoiceWhispererApp()
            }
        }
    }
}
