package money.tingting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import money.tingting.data.AppDatabase
import money.tingting.data.User
import money.tingting.ui.theme.TingTingMoneyTheme
import androidx.compose.ui.text.withStyle
import money.tingting.data.PhraseEntity
import money.tingting.data.PhraseSettings
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.platform.LocalContext

class IntroActivity : BaseActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var db: AppDatabase
    private val RC_SIGN_IN = 9001

    private val sliderItems = listOf(
        SlideItem(R.drawable.intro_1, "Biến điện thoại thành loa đọc tiền chuyển khoản MIỄN PHÍ. Tương tích MỌI NGÂN HÀNG"),
        SlideItem(R.drawable.intro_2, "Không lưu dữ liệu người dùng, dữ liệu chỉ ở trên máy điện thoại của bạn. Công nghệ AI cá nhân hoá cho từng cửa hàng"),
        SlideItem(R.drawable.intro_3, "Miễn phí hoàn toàn tính năng cơ bản, mã nguồn mở minh bạch")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getDatabase(this)

        // Initialize Google Sign-in client first
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestId()
            .requestProfile()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            TingTingMoneyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var shouldShowIntro by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        val existingUser = db.userDao().getUser().first()
                        if (existingUser != null) {
                            Log.d("TingTingMoney", "Found existing user: ${existingUser.email}")
                            startMainActivity()
                            return@LaunchedEffect
                        }

                        val account = GoogleSignIn.getLastSignedInAccount(this@IntroActivity)
                        if (account != null) {
                            saveUserAndProceed(account)
                            return@LaunchedEffect
                        }

                        if (isIntroShown()) {
                            startMainActivity()
                            return@LaunchedEffect
                        }

                        shouldShowIntro = true
                    }

                    if (shouldShowIntro) {
                        IntroScreen(
                            items = sliderItems,
                            onSignInClick = { startSignIn() }
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun IntroScreen(
        items: List<SlideItem>,
        onSignInClick: () -> Unit
    ) {
        val pagerState = rememberPagerState(pageCount = { items.size })
        val scope = rememberCoroutineScope()
        val context = LocalContext.current as IntroActivity

        LaunchedEffect(key1 = pagerState.currentPage) {
            delay(5000)
            scope.launch {
                pagerState.animateScrollToPage(
                    page = (pagerState.currentPage + 1) % items.size
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(items[page].imageRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .padding(bottom = 32.dp)
                    )
                    Text(
                        text = items[page].text,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = {
                    context.logButtonClick("google_signin")
                    onSignInClick()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp) // Increased from 64.dp to 96.dp for more space
            ) {
                Text("Tiếp tục với Google")
            }

            Text(
                text = buildAnnotatedString {
                    append("Bằng việc đăng nhập, bạn đã đồng ý với Điều khoản dịch vụ và ")
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
                    append(" của chúng tôi")
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 32.dp) // Increased vertical padding from 16.dp to 32.dp
                    .clickable(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tingting.money/privacy-policy"))
                        this@IntroActivity.startActivity(intent)
                    })
            )
        }
    }

    private fun startSignIn() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            Log.d("TingTingMoney", "Sign in result: resultCode=$resultCode")
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            } catch (e: Exception) {
                Log.e("TingTingMoney", "Error getting sign in result", e)
            }
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d("TingTingMoney", "Sign in successful: ${account?.email}")
            
            account?.let { saveUserAndProceed(it) }
        } catch (e: ApiException) {
            Log.e("TingTingMoney", "Sign in failed code=${e.statusCode}: ${e.message}", e)
            when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
                    showError("Sign in cancelled")
                GoogleSignInStatusCodes.SIGN_IN_FAILED ->
                    showError("Sign in failed")
                else -> showError("Sign in error: ${e.message}")
            }
        }
    }

    private fun saveUserAndProceed(account: GoogleSignInAccount) {
        lifecycleScope.launch {
            try {
                val user = User(
                    name = account.displayName ?: "",
                    email = account.email ?: "",
                    avatar = account.photoUrl?.toString()
                )
                db.userDao().insert(user)
                Log.d("TingTingMoney", "User saved to database: ${user.email}")
                
                // Add default phrases in coroutine scope
                val phraseDao = db.phraseDao()
                val defaultPhrases = PhraseSettings()
                
                // Add opening phrases
                for ((index, phrase) in defaultPhrases.openingPhrases.phrases.withIndex()) {
                    phraseDao.insert(
                        PhraseEntity(
                            type = "opening",
                            content = phrase,
                            isEnabled = true,
                            order = index
                        )
                    )
                }

                // Add busy phrases
                for ((index, phrase) in defaultPhrases.busyPhrases.phrases.withIndex()) {
                    phraseDao.insert(
                        PhraseEntity(
                            type = "busy",
                            content = phrase,
                            isEnabled = true,
                            order = index
                        )
                    )
                }

                // Add random phrases
                for ((index, phrase) in defaultPhrases.randomPhrases.phrases.withIndex()) {
                    phraseDao.insert(
                        PhraseEntity(
                            type = "random",
                            content = phrase,
                            isEnabled = true,
                            order = index
                        )
                    )
                }
                
                Log.d("TingTingMoney", "Default phrases added to database")
                
                setIntroShown()
                startMainActivity()
            } catch (e: Exception) {
                Log.e("TingTingMoney", "Error saving user to database", e)
                showError("Error saving user data")
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun startMainActivity() {
        Log.d("TingTingMoney", "Starting LoadingActivity")
        startActivity(Intent(this, LoadingActivity::class.java))
        finish()
    }

    private fun isIntroShown(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("intro_shown", false)
    }

    private fun setIntroShown() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("intro_shown", true)
            .apply()
    }
}

data class SlideItem(val imageRes: Int, val text: String)
