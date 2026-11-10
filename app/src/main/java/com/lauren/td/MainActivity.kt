package com.lauren.td

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme {

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(30.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.Center
                ) {

                    Text(
                        text = "TD",
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Text(
                        text =
                            "Real-time transaction classification"
                    )

                    Button(
                        onClick = {

                            val intent =
                                Intent(
                                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                                )

                            startActivity(intent)
                        }
                    ) {

                        Text(
                            "Enable Transaction Detection"
                        )
                    }
                    Button(
                        onClick = {

                            TransactionOverlayManager.show(
                                context = this@MainActivity,
                                transaction = TransactionData(
                                    reference = "TEST123",
                                    amount = 5.00,
                                    type = "TEST",
                                    otherParty = "Test Person",
                                    transactionCost = 0.00,
                                    time = System.currentTimeMillis(),
                                    source = "TEST"
                                ))

                        }
                    ) {
                        Text("Test TD Prompt")
                    }
                    Button(
                        onClick = {

                            val intent =
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse(
                                        "package:$packageName"
                                    )
                                )

                            startActivity(intent)
                        }
                    ) {

                        Text(
                            "Allow TD to Display Prompts"
                        )
                    }

                    if (Build.VERSION.SDK_INT >= 33) {

                        Button(
                            onClick = {

                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        ) {

                            Text(
                                "Allow Notifications"
                            )
                        }
                    }
                }
            }
        }
    }
}