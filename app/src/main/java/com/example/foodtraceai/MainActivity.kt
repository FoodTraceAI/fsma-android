package com.example.foodtraceai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.google.zxing.BarcodeFormat
import com.example.foodtraceai.ui.theme.FoodTraceAITheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class MainActivity : ComponentActivity() {

    // State variables for user login
    private val emailState = mutableStateOf("")
    private val passwordState = mutableStateOf("")
    private val loginTokenState = mutableStateOf<String?>(null) // Token to be used for authenticated requests

    // State variable to hold the scanned data and display it in the UI.
    private val scannedDataState = mutableStateOf<String?>(null)

    // Camera permission code constant.
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    // SharedPreferences key for storing the token
    private val sharedPreferences by lazy {
        getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    }

    // Registering a launcher for the QR, PTI, Data Matrix, or Code 128 scanner.
    private val qrAndPtiScannerLauncher: ActivityResultLauncher<ScanOptions> =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            if (result.contents != null) {
                // QR, PTI, Data Matrix, or Code 128 scan successful; display and send to the server.
                val scannedData = result.contents
                scannedDataState.value = scannedData // Display the scanned data in the preview.
                val parsedData = parseScannedData(scannedData) // Parse the scanned data
                sendScannedDataToServer(parsedData, loginTokenState.value) // Send the parsed data to the server.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve the token from SharedPreferences (if it exists)
        loginTokenState.value = sharedPreferences.getString("token", null)

        // Step 1: Check camera permission for the scanner
        checkCameraPermission()

        // Step 2: Set content based on login status
        setContent {
            FoodTraceAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Check if a token is stored (indicating user is logged in)
                    if (loginTokenState.value != null) {
                        // User is logged in, show the QR scanner screen
                        QRScannerScreen(
                            onScanClick = { startQrAndPtiLabelScan() },
                            scannedData = scannedDataState.value // Pass scanned data to the UI.
                        )
                    } else {
                        // User is not logged in, show the login screen
                        LoginScreen(
                            onLoginClick = { username, password -> handleLogin(username, password) }
                        )
                    }
                }
            }
        }
    }

    private fun handleLogin(email: String, password: String) {
        // Create a logging interceptor
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Logs request and response bodies
        }

        // Create an OkHttpClient with the logging interceptor
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        // Build Retrofit with the OkHttpClient
        val retrofit = Retrofit.Builder()
            .baseUrl("http://fsma-loadbalancer-1104915305.us-east-2.elb.amazonaws.com/api/v1/") // Server URL
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient) // Add the OkHttpClient with logging
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val loginRequest = LoginRequest(email, password)

        apiService.loginUser(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null && loginResponse.token.isNotEmpty()) {
                        // Store the login token and proceed to the scanner
                        loginTokenState.value = loginResponse.token
                        sharedPreferences.edit().putString("token", loginTokenState.value).apply() // Persist token
                        Toast.makeText(this@MainActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        // Handle invalid response
                        Toast.makeText(this@MainActivity, "Login failed: Invalid response from server", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Handle login failure
                    Toast.makeText(this@MainActivity, "Login failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // Show error message if login request fails
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Function to check camera permission and request if not granted.
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Request permission if not granted.
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with scanning
            } else {
                // Permission denied, show a message or disable scanning
                Toast.makeText(this, "Camera permission is required for scanning", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to start the QR code, Code 128, Data Matrix and PTI label scan.
    private fun startQrAndPtiLabelScan() {
        val options = ScanOptions().apply {
            setPrompt("QR, PTI, Data Matrix, or Code 128") // Prompt message during scan.
            setBeepEnabled(true) // Beep sound on scan success.
            setOrientationLocked(false) // Allow device orientation changes during scan.
            setDesiredBarcodeFormats( // Specify supported formats for both QR and PTI labels.
                listOf(
                    BarcodeFormat.QR_CODE.name, // QR Code
                    BarcodeFormat.DATA_MATRIX.name, // Common for PTI labels
                    BarcodeFormat.CODE_128.name // Another common format for PTI labels
                )
            )
        }
        qrAndPtiScannerLauncher.launch(options)
    }

    // Function to parse scanned data (Custom QR or PTI).
    private fun parseScannedData(scannedData: String): ParsedLabel {
        return try {
            val regex = Regex("A(.*?)B(.*?)C(.*)")
            val matchResult = regex.matchEntire(scannedData)
            if (matchResult != null && matchResult.groupValues.size == 4) {
                // Extract parts based on regex groups
                ParsedLabel(
                    type = "Custom",
                    tlcid = matchResult.groupValues[1],
                    sscc = matchResult.groupValues[2],
                    shipToLocationId = matchResult.groupValues[3]
                )
            } else {
                ParsedLabel(type = "Invalid", error = "Scanned data format is not recognized.")
            }
        } catch (e: Exception) {
            ParsedLabel(type = "Invalid", error = "Error parsing scanned data: ${e.message}")
        }
    }

    // Sending parsed data to the server using Retrofit.
    private fun sendScannedDataToServer(parsedData: ParsedLabel, token: String?) {
        if (token == null) {
            Toast.makeText(this, "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("http://fsma-loadbalancer-1104915305.us-east-2.elb.amazonaws.com/api/v1/") // Server URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)

        val supShipArgs = SupShipArgs(
            sscc = parsedData.sscc ?: "",
            tlcId = parsedData.tlcid?.toLongOrNull() ?: 0,
            shipToLocationId = parsedData.shipToLocationId?.toLongOrNull() ?: 0,
            receiveDate = LocalDate.now(),
            receiveTime = OffsetDateTime.now()
        )

        apiService.sendQRCodeData("Bearer $token", supShipArgs).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    scannedDataState.value = null // Clear the scanned data
                    Toast.makeText(this@MainActivity, "Data sent successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to send data: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Data class to hold parsed label information.
    data class ParsedLabel(
        val type: String,
        val tlcid: String? = null,
        val sscc: String? = null,
        val shipToLocationId: String? = null,
        val error: String? = null
    )

    @Composable
    fun LoginScreen(onLoginClick: (String, String) -> Unit) {
        val emailState = remember { mutableStateOf("") }
        val passwordState = remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FoodTraceAI",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = emailState.value,
                        onValueChange = { emailState.value = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordState.value,
                        onValueChange = { passwordState.value = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onLoginClick(emailState.value, passwordState.value) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF880E4F))
            ) {
                Text("Login", color = Color.White, fontSize = 20.sp)
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun LoginScreenPreview() {
        FoodTraceAITheme {
            LoginScreen(onLoginClick = { _, _ -> })
        }
    }

    @Composable
    fun QRScannerScreen(onScanClick: () -> Unit, scannedData: String?) {
        var showDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FoodTraceAI",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF6A1B9A))
            ) {
                Text(
                    text = "Scan: QR, PTI, Data Matrix, or Code 128",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            scannedData?.let {
                Text(
                    text = "Scanned Data: $it",
                    fontSize = 16.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            Button(
                onClick = { showDialog = true },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF880E4F))
            ) {
                Text(
                    text = "Updates",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = {
                        Text(
                            text = "FSMA 204 Updates",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color(0xFF1E88E5)
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "FSMA 204 ensures faster identification and removal of potentially contaminated foods.",
                                fontSize = 16.sp,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This update helps ensure compliance with the Food Safety Modernization Act, enhancing food safety in the supply chain.",
                                fontSize = 16.sp,
                                color = Color(0xFF333333)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text(
                                "OK",
                                color = Color(0xFF1E88E5),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun QRScannerPreview() {
        FoodTraceAITheme {
            QRScannerScreen(onScanClick = {}, scannedData = null)
        }
    }
}