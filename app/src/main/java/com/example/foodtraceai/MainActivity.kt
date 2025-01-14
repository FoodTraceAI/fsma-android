package com.example.foodtraceai

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
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
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.Date
import java.time.LocalDate
import java.time.OffsetDateTime


class MainActivity : ComponentActivity() {



    // State variables for user login
    private val emailState = mutableStateOf("")
    private val passwordState = mutableStateOf("")
    private val loginTokenState = mutableStateOf<String?>(null) // Token to be used for authenticated requests


    // Registering a launcher for the  QR, PTI, Data Matrix, or Code 128 scanner.
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

    // State variable to hold the scanned data and display it in the UI.
    private val scannedDataState = mutableStateOf<String?>(null)

    // Camera permission code constant.
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:8080/") // Replace with actual server URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val loginRequest = LoginRequest(email, password)

        apiService.loginUser(loginRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    // Store the login token and proceed to the scanner
                    loginTokenState.value = response.body()?.token
                    Toast.makeText(this@MainActivity, "Login successful!", Toast.LENGTH_SHORT).show()
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
        } else {
            // If permission granted, load the UI.
            setContent {
                FoodTraceAITheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        QRScannerScreen(
                            onScanClick = { startQrAndPtiLabelScan() },
                            scannedData = scannedDataState.value // Pass scanned data to the UI.
                        )
                    }
                }
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

    // Function to parse scanned data (QR or PTI).
    private fun parseScannedData(scannedData: String): ParsedLabel {
        return if (scannedData.startsWith("01")) {
            // Extract GTIN (14 digits after "01")
            val gtin = scannedData.substring(2, 16)

            // Find Batch Code ("10" indicates the start, but it can have a variable length)
            val batchStartIndex = scannedData.indexOf("10") + 2 // "10" takes 2 characters
            val batchEndIndex = scannedData.indexOf("13", batchStartIndex) // Look for "13" for the date of packing
            val batchCode = if (batchEndIndex > batchStartIndex) {
                scannedData.substring(batchStartIndex, batchEndIndex).trim()
            } else {
                scannedData.substring(batchStartIndex).trim() // In case "13" is not found
            }

            // Extract Date of Packing (YYMMDD format after "13")
            val dateOfPacking = if (batchEndIndex != -1) {
                scannedData.substring(batchEndIndex + 2, batchEndIndex + 8) // Extract 6 characters for YYMMDD
            } else {
                ""
            }

            // Return parsed PTI label data
            ParsedLabel(
                type = "PTI",
                gtin = gtin,
                batchCode = batchCode,
                dateOfPacking = dateOfPacking
            )
        } else {
            // This is a QR code, return as it is
            ParsedLabel(type = "QR", qrData = scannedData)
        }
    }

    // Sending parsed data to the server using Retrofit.
    private fun sendScannedDataToServer(parsedData: ParsedLabel, token: String?) {
        // Step 1: Initialize Retrofit instance with base URL and converter
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:8080/") // Replace with actual server URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Step 2: Create an instance of the API service
        val apiService = retrofit.create(ApiService::class.java)

        // Step 3: Map parsed data to fields in `QRCodeRequest`
        val qrCodeRequest = QRCodeRequest(
            sscc = parsedData.gtin ?: "",       // Map `gtin` to `sscc`, or provide an empty string if null
            tlcId = 123456,                    // Replace with actual `tlcId`
            shipToLocationId = 78910,         // Replace with actual `shipToLocationId`
            receiveDate = LocalDate.now(),      // Use the current date for `receiveDate`
            receiveTime = OffsetDateTime.now()  // Use the current date-time for `receiveTime`
        )

        // Step 4: Make API call with populated `QRCodeRequest` and authorization token
        apiService.sendQRCodeData("Bearer $token", qrCodeRequest).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    // Handle successful response
                    Toast.makeText(this@MainActivity, "Data sent successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    // Handle unsuccessful response (e.g., error from server)
                    Toast.makeText(this@MainActivity, "Failed to send data: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                // Handle failure (e.g., network issue)
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

// Data class to hold parsed label information.
data class ParsedLabel(
    val type: String,
    val gtin: String? = null,
    val batchCode: String? = null,
    val dateOfPacking: String? = null,
    val qrData: String? = null
)

@Composable
fun LoginScreen(onLoginClick: (String, String) -> Unit) {
    // Define the username and password states
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

        // Card wrapping the input fields
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White) // White background for input fields
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Username OutlinedTextField
                OutlinedTextField(
                    value = emailState.value,
                    onValueChange = { emailState.value = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Password OutlinedTextField with visual transformation
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
    var showDialog by remember { mutableStateOf(false) } // State to control the Updates dialog.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        //  App name at the top, with black color and modern font.
        Text(
            text = "FoodTraceAI",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black, // Changed to black for a sleek appearance.
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp) // Space between title and buttons.
        )

        // "Scan: QR, PTI, Data Matrix, or Code 128" button with dark purple.
        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth(0.8f) // Button takes 80% of the screen width.
                .height(60.dp) // Button height.
                .padding(bottom = 16.dp), // Space below the button.
            shape = RoundedCornerShape(12.dp), // Rounded corners for a modern look.
            colors = ButtonDefaults.buttonColors(Color(0xFF6A1B9A)) // Dark purple color.
        ) {
            Text(
                text = "Scan: QR, PTI, Data Matrix, or Code 128",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        // Display scanned data if available, with slight style tweaks.
        scannedData?.let {
            Text(
                text = "Scanned Data: $it", // Show the scanned QR or PTI label data.
                fontSize = 16.sp,
                color = Color(0xFF333333),
                modifier = Modifier.padding(vertical = 16.dp) // Add padding around the text.
            )
        }

        // "Updates" button with dark maroon color.
        Button(
            onClick = { showDialog = true }, // Show the dialog on click.
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF880E4F)) // Dark maroon color.
        ) {
            Text(
                text = "Updates",
                fontSize = 16.sp,
                color = Color.White
            )
        }

        // Enhanced FSMA 204 Updates dialog box with sleek design and cleaner text layout.
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Text(
                        text = "FSMA 204 Updates",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, // Slightly larger title for emphasis.
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
                        Spacer(modifier = Modifier.height(8.dp)) // Space between paragraphs.
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
                            color = Color(0xFF1E88E5), // Same color for consistency.
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    }
}


// Preview for UI testing and design review.
@Preview(showBackground = true)
@Composable
fun QRScannerPreview() {
    FoodTraceAITheme {
        QRScannerScreen(onScanClick = {}, scannedData = null)
    }
}}
