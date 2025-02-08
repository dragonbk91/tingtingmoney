package money.tingting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import money.tingting.ui.theme.TingTingMoneyTheme
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

class ContactActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TingTingMoneyTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ContactScreen(
                        onBackPressed = { finish() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ContactScreen(
        onBackPressed: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current as ContactActivity

        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Liên hệ",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Box(modifier = Modifier.width(48.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tham gia cộng đồng TingTing Money:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Telegram Icon
                        IconButton(
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                context.logButtonClick("contact_telegram")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/vntingtingmoney"))
                                context.startActivity(intent)
                            }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_telegram),
                                contentDescription = "Telegram"
                            )
                        }

                        // Zalo Icon
                        IconButton(
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                context.logButtonClick("contact_zalo")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://zalo.me/g/ladjjm669"))
                                context.startActivity(intent)
                            }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_zalo),
                                contentDescription = "Zalo"
                            )
                        }

                        // Github Icon
                        IconButton(
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                context.logButtonClick("contact_github")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dragonbk91/tingtingmoney"))
                                context.startActivity(intent)
                            }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = "Github"
                            )
                        }
                    }

                    QRCodeSection()

                    Text(
                        text = buildAnnotatedString {
                            pushStringAnnotation(
                                tag = "policy",
                                annotation = "https://tingting.money/privacy-policy"
                            )
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append("Chính sách bảo mật")
                            }
                            pop()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 32.dp, vertical = 32.dp) // Increased vertical padding from 16.dp to 32.dp
                            .clickable(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tingting.money/privacy-policy"))
                                this@ContactActivity.startActivity(intent)
                            })
                    )
                }
            }
        }
    }

    @Composable
    fun QRCodeSection() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text(
                text = "Link cài đặt ứng dụng",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            val playStoreUrl = "https://play.google.com/store/apps/details?id=money.tingting"
            val qrBitmap = remember {
                generateQRCode(playStoreUrl)
            }
            
            qrBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "App QR Code",
                    modifier = Modifier.size(200.dp)
                )
            }
        }
    }

    private fun generateQRCode(content: String): Bitmap? {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(
                content,
                BarcodeFormat.QR_CODE,
                512,
                512
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}