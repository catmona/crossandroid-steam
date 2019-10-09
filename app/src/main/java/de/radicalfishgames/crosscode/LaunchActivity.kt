package de.radicalfishgames.crosscode

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.storage.OnObbStateChangeListener
import android.os.storage.StorageManager
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
import de.namnodorel.creditz.views.*
import de.radicalfishgames.crosscode.preferences.PreferencesActivity
import java.util.*


class LaunchActivity : AppCompatActivity() {

    private var userConfirmedJava = false
    private var userConfirmedGotGameFiles = false
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
            tryToMountObb()
        }
    }

    private fun tryToMountObb() {

        Log.i("CrossCode", "Attempting to mount OBB...")

        val version = 1
        val obbName = "main.$version.$packageName.obb"

        val obbPath = "$obbDir/$obbName"

        if(!isMountingPossible(obbPath)){
            mountFailed()
            return
        }

        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        if(storageManager.isObbMounted(obbPath)){
            obbFilesFound(storageManager.getMountedObbPath(obbPath))

        }else{
            mountObb(obbPath, storageManager)
        }
    }

    private fun isMountingPossible(obbPath: String): Boolean{
        if(!hasStoragePermission()){
            Log.i("CrossCode", "No storage permission!")

            return false
        }else if(!File(obbPath).exists()){
            Log.i("CrossCode", "OBB file not found!")

            return false
        }

        return true
    }

    private fun mountObb(path: String, storageManager: StorageManager){
        storageManager.mountObb(path, null, object : OnObbStateChangeListener() {
            override fun onObbStateChange(path: String, state: Int) {

                if(state == MOUNTED || state == ERROR_ALREADY_MOUNTED){
                    val mountedPath = storageManager.getMountedObbPath(path)

                    obbFilesFound(mountedPath)

                } else {
                    Toast.makeText(this@LaunchActivity, "Mount failed: $state", Toast.LENGTH_LONG).show()
                    mounting_progress_bar.visibility = GONE

                    mountFailed()
                }
            }
        })
    }

    private fun obbFilesFound(path: String){
        val launchFile = File("$path/node-webkit.html")
        if(!launchFile.exists()){
            Toast.makeText(this@LaunchActivity, "Assets in incorrect format", Toast.LENGTH_LONG).show()

            mountFailed()
        }else{
            gameFilesIntact()
            return
        }
    }

    private fun gameFilesIntact(){

        Log.i("CrossCode", "OBB file found!")

        mounting_progress_bar.visibility = GONE
        launch_button.isEnabled = true
    }

    private fun mountFailed(){
        userConfirmedJava = false
        userConfirmedGotGameFiles = false
        userConfirmedDownloadedTools = false
        userConfirmedPackagedGameFiles = false

        showCurrentGuide(false)
    }

    private fun showCurrentGuide(showTransition: Boolean){

        Log.i("CrossCode", "Showing user guide on installing the OBB.")

        setup_container.visibility = VISIBLE
        launch_container.visibility = GONE

        var pastStepsSuccessfull = true

        val hasStoragePermission = hasStoragePermission()

        if(hasStoragePermission){
            permission_text_tv.visibility = GONE
            ask_permission_btn.visibility = GONE

            val folder = obbDir
            if(!folder.exists()){
                folder.mkdirs()
            }

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


        if(pastStepsSuccessfull && userConfirmedJava){
            check_java_text_tv.visibility = GONE
            have_java_btn.visibility = GONE

        }else if(pastStepsSuccessfull){

            check_java_text_tv.visibility = VISIBLE
            have_java_btn.visibility = VISIBLE

            have_java_btn.setOnClickListener {
                userConfirmedJava = true
                showCurrentGuide(true)
            }
        }
        pastStepsSuccessfull = pastStepsSuccessfull && userConfirmedJava


        if(pastStepsSuccessfull && userConfirmedGotGameFiles){
            get_assets_text_tv.visibility = GONE
            got_assets_btn.visibility = GONE

        }else if(pastStepsSuccessfull){

            get_assets_text_tv.visibility = VISIBLE
            got_assets_btn.visibility = VISIBLE

            got_assets_btn.setOnClickListener {
                userConfirmedGotGameFiles = true
                showCurrentGuide(true)
            }
        }
        pastStepsSuccessfull = pastStepsSuccessfull && userConfirmedGotGameFiles


        if(pastStepsSuccessfull && userConfirmedDownloadedTools){
            download_tools_text_tv.visibility = GONE
            download_tools_btn.visibility = GONE

        }else if(pastStepsSuccessfull){

            download_tools_text_tv.visibility = VISIBLE
            download_tools_btn.visibility = VISIBLE

            download_tools_btn.setOnClickListener {
                userConfirmedDownloadedTools = true
                showCurrentGuide(true)
            }
        }
        pastStepsSuccessfull = pastStepsSuccessfull && userConfirmedDownloadedTools


        if(pastStepsSuccessfull && userConfirmedPackagedGameFiles){
            package_assets_text_tv.visibility = GONE
            package_assets_btn.visibility = GONE

        }else if(pastStepsSuccessfull){

            package_assets_text_tv.visibility = VISIBLE
            package_assets_btn.visibility = VISIBLE

            package_assets_btn.setOnClickListener {
                userConfirmedPackagedGameFiles = true
                showCurrentGuide(true)
            }
        }
        pastStepsSuccessfull = pastStepsSuccessfull && userConfirmedPackagedGameFiles


        if(!pastStepsSuccessfull){
            move_files_text_tv.visibility = GONE
            move_files_btn.visibility = GONE

        }else{

            move_files_text_tv.visibility = VISIBLE
            move_files_btn.visibility = VISIBLE

            move_files_btn.setOnClickListener {
                setup_container.visibility = GONE
                launch_container.visibility = VISIBLE

                handler.post {
                    tryToMountObb()
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
        config.contributors = contributors

        intent.putExtra(AboutActivity.CREDITZ_CONFIG, config)
        startActivity(intent)
    }
}