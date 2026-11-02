package com.dzire.dzirecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.billing.PlayBillingManager
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme

class SubscriptionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                SubscriptionScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val billing = remember { PlayBillingManager(context) }
    val state by billing.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dzire Premium", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Understand yourself more deeply", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Premium unlocks remote personalized advice and future advanced companion features.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            item { PremiumFeature("Personalized advice", "Advice can be updated remotely from Firebase without releasing a new APK.") }
            item { PremiumFeature("Deeper companion guidance", "Use the evidence collected by Dzire's analytical layers for richer guidance.") }
            item { PremiumFeature("Future premium experiences", "The subscription architecture leaves room for additional premium features and content.") }
            item {
                Button(
                    onClick = { billing.launchPremium(context as android.app.Activity) },
                    enabled = !state.loading && !state.isPremium && state.price != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(if (state.isPremium) "Premium Active" else "Subscribe${state.price?.let { " • $it" } ?: ""}")
                }
                if (state.loading) {
                    Spacer(Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (!state.isPremium) {
                    Spacer(Modifier.height(8.dp))
                    Text("Subscription is managed by Google Play.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PremiumFeature(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
