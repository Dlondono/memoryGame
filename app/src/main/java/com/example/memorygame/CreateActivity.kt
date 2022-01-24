package com.example.memorygame

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memorygame.models.BoardSize
import com.example.memorygame.utils.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName:EditText
    private lateinit var btnSave: Button
    private lateinit var adapter: ImagePickerAdapter
    private lateinit var boardSize: BoardSize
    private var numImagesRequired=-1
    private val chosenImageUris= mutableListOf<Uri>()
    private lateinit var pbUploading :ProgressBar

    private val storage = Firebase.storage
    private val db=Firebase.firestore

    companion object{
        private const val PICK_PHOTOS_CODE= 16
        private const val READ_EXTERNAL_PHOTOS_CODE= 4
        private const val READ_PHOTOS_PERMISSION= android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val TAG="CreateActivity"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker=findViewById(R.id.rvImagePicker)
        etGameName=findViewById(R.id.etGameName)
        btnSave=findViewById(R.id.btnSave)
        pbUploading=findViewById(R.id.pbUploading)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize=intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired=boardSize.getNumPairs()

        btnSave.setOnClickListener{
            saveDataToFirebase()
        }
        supportActionBar?.title="Escoger imágenes (0/$numImagesRequired)"

        etGameName.filters = arrayOf(InputFilter.LengthFilter(14))
        etGameName.addTextChangedListener(object :TextWatcher{
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled=shouldEnableSaveButton()
            }

        })
        adapter=ImagePickerAdapter(this,chosenImageUris,boardSize,object:ImagePickerAdapter.ImageClickListener{
            override fun onPlaceHolderClick() {
                if(isPermissionGranted(this@CreateActivity,READ_PHOTOS_PERMISSION)) {
                    launchIntentForPhotos()
                }else{
                    requestPermission(this@CreateActivity, READ_PHOTOS_PERMISSION,
                        READ_EXTERNAL_PHOTOS_CODE)
                }
            }
        })
        rvImagePicker.adapter=adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager=GridLayoutManager(this,boardSize.getWidth())

    }

    private fun saveDataToFirebase() {
        //check unique gamename
        btnSave.isEnabled=false
        val customGamename=etGameName.text.toString()
        db.collection("games").document(customGamename).get().addOnSuccessListener {
            document -> if(document !=null && document.data !=null){
                AlertDialog.Builder(this)
                        .setTitle("Nombre no disponible")
                        .setMessage("Ya existe un juego con este nombre $customGamename")
                        .setPositiveButton("Ok",null)
                        .show()
            btnSave.isEnabled=false
                }else{
                    pbUploading.visibility= View.VISIBLE
            val customGameName= etGameName.text.toString()
            var didEncounterError=false
            val uploadImageUrls= mutableListOf<String>()
            Log.i(TAG,"save to firebase")
            for((index,photoUri)in chosenImageUris.withIndex()){
                val imageByteArray=getImageByteArray(photoUri)
                val filePath = "images/$customGameName/${System.currentTimeMillis()}-${index}.jpg"
                val photoReference = storage.reference.child(filePath)
                photoReference.putBytes(imageByteArray)
                        .continueWithTask {
                            photoUploadTask ->
                            Log.i(TAG,"Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                            photoReference.downloadUrl
                        }.addOnCompleteListener{downloadUrlTask ->
                            if(!downloadUrlTask.isSuccessful){
                                Log.e(TAG,"Error firebase storage")
                                Toast.makeText(this,"Error subiendo imagen",Toast.LENGTH_LONG).show()
                                didEncounterError=true
                                return@addOnCompleteListener
                            }
                            if(didEncounterError){
                                pbUploading.visibility=View.GONE
                                return@addOnCompleteListener
                            }
                            val downloadUrl=downloadUrlTask.result.toString()
                            uploadImageUrls.add(downloadUrl)
                            pbUploading.progress=uploadImageUrls.size * 100/chosenImageUris.size
                            Log.i(TAG,"Finished upload $photoUri, num ${uploadImageUrls.size}")
                            if(uploadImageUrls.size==chosenImageUris.size){
                                handleAllImagesUploaded(customGameName,uploadImageUrls)
                            }
                        }
                }
            }
        }.addOnFailureListener{
            Log.e(TAG,"Error guardando juego personalizado")
            btnSave.isEnabled=true
        }
    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games").document(gameName)
                .set(mapOf("images" to imageUrls))
                .addOnCompleteListener{gameCreationTask ->
                    pbUploading.visibility=View.GONE
                    if(!gameCreationTask.isSuccessful){
                        Log.e(TAG,"Error en firestore",gameCreationTask.exception)
                        Toast.makeText(this,"Error en la creacion",Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }
                    pbUploading.visibility=View.GONE
                    Log.i(TAG,"Juego creado correctamente $gameName")
                    AlertDialog.Builder(this)
                            .setTitle("Juego creado correctamente $gameName")
                            .setPositiveButton("Ok"){_,_ ->
                                val resultData =Intent()
                                resultData.putExtra(EXTRA_GAME_NAME,gameName)
                                setResult(Activity.RESULT_OK,resultData)
                                finish()
                            }.show()
                }
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){
            val source =ImageDecoder.createSource(contentResolver,photoUri)
            ImageDecoder.decodeBitmap(source)
        }else{
            MediaStore.Images.Media.getBitmap(contentResolver,photoUri)
        }
        Log.i(TAG,"Original ${originalBitmap.width} and height ${originalBitmap.height}")
        val scaledBitmap= BitmapScaler.scaleToFitHeight(originalBitmap,250)
        Log.i(TAG,"Scaled ${scaledBitmap.width} and height ${scaledBitmap.height}")
        val byteOutputStream=ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG,60,byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode== READ_EXTERNAL_PHOTOS_CODE && grantResults[0]==PackageManager.PERMISSION_GRANTED){
            launchIntentForPhotos()
        }else{
            Toast.makeText(this,"Necesita permisos",Toast.LENGTH_LONG).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(PICK_PHOTOS_CODE!=requestCode || resultCode!= Activity.RESULT_OK || data==null){
            Log.w(TAG,"User denied permissions")
            return
        }
        val selectedUri=data.data
        val clipData=data.clipData
        if(clipData!=null){
            Log.i(TAG,"clipData numImages ${clipData.itemCount}:$clipData")
            for(i in 0 until clipData.itemCount){
                val clipItem=clipData.getItemAt(i)
                if(chosenImageUris.size < numImagesRequired){
                    chosenImageUris.add(clipItem.uri)
                }
            }
        }else if(selectedUri!=null){
            Log.i(TAG,"data:$selectedUri")
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title="Escoger imágenes (${chosenImageUris.size}/$numImagesRequired)"
        btnSave.isEnabled=shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if(chosenImageUris.size==numImagesRequired && etGameName.text.isNotEmpty() ) {
            return true
        }
        return false
    }

    private fun launchIntentForPhotos() {
        val intent= Intent(Intent.ACTION_PICK)
        intent.type="image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        startActivityForResult(Intent.createChooser(intent,"Choose pics"),PICK_PHOTOS_CODE)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==android.R.id.home){
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}