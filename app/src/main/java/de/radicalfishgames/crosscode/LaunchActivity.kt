package de.radicalfishgames.crosscode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_launch.*
import java.io.File
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Handler
import android.view.View
import android.view.ViewStub
import android.widget.ScrollView
import de.namnodorel.creditz.views.*
import de.radicalfishgames.crosscode.preferences.PreferencesActivity
import kotlinx.android.synthetic.main.guide_layout.*
import java.util.*

class LaunchActivity : AppCompatActivity() {

    private var userConfirmedCCLoader = false
    private var userConfirmedCopiedInst = false
    private var userConfirmedDownloadedTools = false
    private var userConfirmedPackagedGameFiles = false

    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_launch)

        launch_button.setOnClickListener {
            launchGameActivity()
        }

        preferences_button.setOnClickListener {
            launchPreferencesActivity()
        }

        about_button.setOnClickListener {
            launchAboutActivity()
        }
    }

    override fun onResume() {
        super.onResume()

        handler.post {
            tryToAccessInstance()
        }
    }

    private fun tryToAccessInstance() {
        Log.i("CrossCode", "Attempting to access the CrossCode instance...")

        val sdcardPath = this.getExternalFilesDir(null)
        if (sdcardPath == null) {
            Toast.makeText(this@LaunchActivity, "Storage permission denied", Toast.LENGTH_LONG).show()
            return
        }
        val instanceDir = "${sdcardPath.absolutePath}/CrossCode"

        if (!File("${instanceDir}/assets").exists()) {
            instanceNotFound()
            return
        }
        instanceFound("${instanceDir}/assets")
    }

    private fun instanceFound(path: String){
        val normalLaunchFile = File("$path/node-webkit.html")
        val modLoaderLaunchFile = (File("$path/ccloader/index.html") or File("$path/ccloader/main.html")) or File("$path/ccloader3/main.html")

        if(!(normalLaunchFile.exists() || modLoaderLaunchFile.exists())){
            Toast.makeText(this@LaunchActivity, "Can't find node-webkit.html or ccloader/index.html in OBB", Toast.LENGTH_LONG).show()

            instanceNotFound()
        }else{
            gameFilesIntact()
            return
        }
    }

    private fun gameFilesIntact(){

        Log.i("CrossCode", "Instance directory found!")

        mounting_progress_bar.visibility = GONE
        launch_button.isEnabled = true
    }

    private fun instanceNotFound(){
        userConfirmedCCLoader = false
        userConfirmedCopiedInst = false
        userConfirmedDownloadedTools = false
        userConfirmedPackagedGameFiles = false

        showCurrentGuide(false)
    }

    private fun showCurrentGuide(showTransition: Boolean){

        Log.i("CrossCode", "Showing user guide on installing the instance directory.")

        val setupContainer: View
        if(findViewById<ScrollView>(R.id.setup_container) == null){
            setupContainer = findViewById<ViewStub>(R.id.setup_container_stub).inflate()
        }else{
            setupContainer = findViewById<ScrollView>(R.id.setup_container)
        }

        launch_container.visibility = GONE

        var pastStepsSuccessfull = true

        val hasStoragePermission = hasStoragePermission()


        if(hasStoragePermission){
            permission_text_tv.visibility = GONE
            ask_permission_btn.visibility = GONE
        }else{
            permission_text_tv.visibility = VISIBLE
            ask_permission_btn.visibility = VISIBLE

            ask_permission_btn.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 0)

                }else{
                    Toast.makeText(this, "Can't request permission. Please contact the dev.", Toast.LENGTH_LONG).show()
                }
            }
        }
        pastStepsSuccessfull = pastStepsSuccessfull && hasStoragePermission


        if(pastStepsSuccessfull && userConfirmedCCLoader){
            install_ccloader_text_tv.visibility = GONE
            install_ccloader_btn.visibility = GONE

        }else if(pastStepsSuccessfull){

            install_ccloader_text_tv.visibility = VISIBLE
            install_ccloader_btn.visibility = VISIBLE

            install_ccloader_btn.setOnClickListener {
                userConfirmedCCLoader = true
                showCurrentGuide(true)
            }
        }
        pastStepsSuccessfull = pastStepsSuccessfull && userConfirmedCCLoader

        if(pastStepsSuccessfull && userConfirmedCopiedInst){
            copy_instance_text_tv.visibility = GONE
            copy_instance_btn.visibility = GONE

        }else if(pastStepsSuccessfull){

            copy_instance_text_tv.visibility = VISIBLE
            copy_instance_btn.visibility = VISIBLE

            copy_instance_btn.setOnClickListener {
                userConfirmedCopiedInst = true

                setupContainer.visibility = GONE
                launch_container.visibility = VISIBLE
                handler.post {
                    tryToAccessInstance()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED)){
            Toast.makeText(this, "CrossAndroid unfortunately can not function without this permission.", Toast.LENGTH_LONG).show()
        }

        showCurrentGuide(false)
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchGameActivity(){
        val intent = Intent(this, GameActivity::class.java)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun launchPreferencesActivity(){
        val intent = Intent(this, PreferencesActivity::class.java)
        startActivity(intent)
    }

    private fun launchAboutActivity(){
        val intent = Intent(this, AboutActivity::class.java)

        val config = CreditzConfig()

        config.aboutTitle = "CrossAndroid"
        config.aboutDescription = "A port/wrapper app to run CrossCode on Android devices developed by Namnodorel."
        config.theme = R.style.AppTheme

        val infoButtons = LinkedList<InfoButton>()
        infoButtons.add(InfoButton("Project Page", "https://gitlab.com/Namnodorel/crossandroid/"))
        config.infoButtons = infoButtons

        val contributors = LinkedList<ContributorInfo>()
        contributors.add(ContributorInfo("Ichiki Hayaite aka TheSparkstarScope", "Contributed the awesome pixelart"))
        contributors.add(ContributorInfo("Dmytro Meleshko", "Enhanced modloader support"))
        contributors.add(ContributorInfo("Alyxia & Xinto", "CCLoader3 support"))
        contributors.add(ContributorInfo("krypek", "Changed CrossCode instance format loading from .obb files to a regular directory"))
        config.contributors = contributors

        intent.putExtra(AboutActivity.CREDITZ_CONFIG, config)
        startActivity(intent)
    }
}