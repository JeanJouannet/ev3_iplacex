package com.example.ev3_iplacex

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File


enum class Screen {
    Form,
    Camera,
    Map,
    BigPicture
}
class AppVM: ViewModel() {
    val currentScreen = mutableStateOf(Screen.Form)
    val onCameraPermissionOk:() -> Unit = {}
    var locationPermissionOk:() -> Unit = {}
}

class FormVM: ViewModel() {
    val placeVisited = mutableStateOf("")
    val photo = mutableStateOf<Uri?>(null)
    val lat = mutableStateOf(0.0);
    val lon = mutableStateOf(0.0);
}

class MainActivity : ComponentActivity() {

    val cameraVm:AppVM by viewModels()
    val formVm:FormVM by viewModels()
    val appVM:AppVM by viewModels()


    lateinit var cameraController:LifecycleCameraController

    val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        appVM.locationPermissionOk()
        if(it[android.Manifest.permission.CAMERA] == true) {
            cameraVm.onCameraPermissionOk()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        setContent {
            AppUI(permissionLauncher, cameraController)
        }
    }
}

@Composable
fun AppUI(
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    cameraController: LifecycleCameraController,
) {

    val appVM:AppVM = viewModel()
    val formVM:FormVM = viewModel()

    when(appVM.currentScreen.value) {
        Screen.Form -> {
            FormUI(appVM = appVM, formVM = formVM)
        }
        Screen.Camera -> {
            CameraUI(permissionLauncher = permissionLauncher, cameraController = cameraController, appVM = appVM, formVM = formVM)
        }
        Screen.Map -> {
            MapUI(appVM = appVM, formVM = formVM, permissionLauncher = permissionLauncher)
        }
        Screen.BigPicture -> {
            BigPhotoUI(formVM = formVM, appVM = appVM)
        }
    }


}

@Composable
fun BigPhotoUI(formVM: FormVM, appVM: AppVM) {
    formVM.photo.value?.let {
        Image(
            painter = BitmapPainter(uri2imageBitmap(it, LocalContext.current)),
            contentDescription = "Foto",
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    appVM.currentScreen.value = Screen.Form
                }
        )
    }
}

@Composable
fun MapUI(appVM: AppVM, formVM: FormVM, permissionLauncher: ActivityResultLauncher<Array<String>>) {
    val context = LocalContext.current

    permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))

    Column {


        Button(onClick = { appVM.currentScreen.value = Screen.Form}) {
        Text("Volver")
        }

        Spacer(modifier =Modifier.height(30.dp))

        Button(onClick = {
            Log.d("Location", "Lat: ${formVM.lat.value} Long: ${formVM.lon.value}")
            getLocation(context) {
                if (it != null) {
                    formVM.lat.value = it.latitude
                }
                if (it != null) {
                    formVM.lon.value = it.longitude
                }
                Log.d("Location", "Lat: ${formVM.lat.value} Long: ${formVM.lon.value}")

        }}) {
        Text("Obtener Ubicación")
        }

        Text(
        text = "Lat: ${formVM.lat.value} Long: ${formVM.lon.value}",
        modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier =Modifier.height(100.dp))

        AndroidView(factory = { MapView(it).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
            controller.setZoom(15.0)
        }
        }, update = {
            it.overlays.removeIf{true}
            it.invalidate()

            val geoPoint = GeoPoint(formVM.lat.value, formVM.lon.value)
            it.controller.animateTo(geoPoint)

            val marker = Marker(it)
            marker.position = geoPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marker)
        })


    }

}

fun uri2imageBitmap(uri: Uri, context: Context) = BitmapFactory.decodeStream(
    context.contentResolver.openInputStream(uri)
).asImageBitmap()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormUI(appVM: AppVM, formVM: FormVM) {
    var placeVisited by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {

        Text(text = "Luego de agregar un lugar visitado, puedes tomar una foto",
            modifier = Modifier.padding(vertical = 16.dp))

        TextField(
            value = placeVisited,
            onValueChange = { placeVisited = it},
            label = { Text("Lugar Visitado") },
            keyboardActions = KeyboardActions(
                onDone = {
                    formVM.placeVisited.value = placeVisited
                    appVM.currentScreen.value = Screen.Camera
                }
            ),
            keyboardOptions = KeyboardOptions.Default.copy(
                imeAction = ImeAction.Done
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            formVM.photo.value?.let {
                Image(
                    painter = BitmapPainter(uri2imageBitmap(it, LocalContext.current)),
                    contentDescription = "Foto",
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { appVM.currentScreen.value = Screen.BigPicture }
                )
            }


            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = formVM.placeVisited.value,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Text(
                    text = "Lat: ${formVM.lat.value} Long: ${formVM.lon.value}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Botón para ver el mapa
        Button(
            onClick = {
                appVM.currentScreen.value = Screen.Map
            }
        ) {
            Text("Ver en Mapa")
        }

//            if(showMap){MapUI(appVM = appVM, formVM = formVM)}

    }
}

@Composable
fun CameraUI(cameraController:LifecycleCameraController, permissionLauncher: ActivityResultLauncher<Array<String>>,
             appVM: AppVM, formVM: FormVM){
    permissionLauncher.launch(arrayOf(android.Manifest.permission.CAMERA))

    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
        PreviewView(it).apply { controller = cameraController }
    })


    Button(
        onClick = {
            takePhoto(
                cameraController = cameraController,
                file = makePrivatePhotoFile(context),
                context = context)
                {
                    formVM.photo.value = it
                    appVM.currentScreen.value = Screen.Form
                }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(text = "Tomar foto")
    }

}

fun takePhoto(
    cameraController: LifecycleCameraController,
    file: File,
    context: Context,
    onCaptureImage: (Uri) -> Unit
) {
    val options = ImageCapture.OutputFileOptions.Builder(file).build()

    cameraController.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object: OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {
                    onCaptureImage(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Camera", "Error taking photo", exception)
            }
        }
    )
}

fun makePrivatePhotoFile(context: Context): File = File(
    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${System.currentTimeMillis()}.jpg"
)

fun getLocation(context: Context, onSuccess: (location: Location?) -> Unit) {
    try {
        val service = LocationServices.getFusedLocationProviderClient(context)
        val task = service.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )
        task.addOnSuccessListener { location ->
            Log.d("Location", "Location retrieved: $location")
            onSuccess(location)
        }
        task.addOnFailureListener { exception ->
            Log.e("Location", "Failed to retrieve location: $exception")
            onSuccess(null)
        }
    } catch (e: SecurityException) {
        Log.e("Location", "Failed to retrieve location: $e")
        onSuccess(null)
    }
}

