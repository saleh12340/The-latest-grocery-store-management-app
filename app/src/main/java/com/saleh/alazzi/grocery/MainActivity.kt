package com.saleh.alazzi.grocery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF155E9B),
                secondary = androidx.compose.ui.graphics.Color(0xFF16804A),
                error = androidx.compose.ui.graphics.Color(0xFFB42318),
                background = androidx.compose.ui.graphics.Color(0xFFF4F6F8),
                surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
            )
            MaterialTheme(colorScheme = colors) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        WelcomeScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "بقالة العزي للمواد الغذائية",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "نظام إدارة المبيعات والمشتريات والمخزون والحسابات",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text("بدء إعداد النظام")
        }
        Text(
            text = "نسخة تأسيسية — لا تُستخدم تجارياً قبل استكمال الوحدات والاختبارات",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
