package com.example.memorygame

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memorygame.models.BoardSize
import com.example.memorygame.models.MemoryGame
import com.example.memorygame.models.UserImageList
import com.example.memorygame.utils.EXTRA_BOARD_SIZE
import com.example.memorygame.utils.EXTRA_GAME_NAME
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {
    companion object{
        private const val TAG="MainActivity"
        private const val CREATE_REQUEST_CODE= 28
    }

    private val db = Firebase.firestore
    private var gameName: String? =null
    private lateinit var adapter: MemoryBoardAdapter
    private lateinit var clRoot: ConstraintLayout
    private lateinit var memoryGame: MemoryGame
    private lateinit var  rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private var boardSize:BoardSize=BoardSize.EASY
    private var customGameImages: List<String>? =null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        clRoot=findViewById(R.id.clRoot)
        rvBoard=findViewById(R.id.rvBoard)
        tvNumMoves=findViewById(R.id.tvNumMoves)
        tvNumPairs=findViewById(R.id.tvNumPairs)

        //automatic travel to next screen
        //val intent = Intent(this,CreateActivity::class.java)
        //intent.putExtra(EXTRA_BOARD_SIZE,BoardSize.EASY)
        //startActivity(intent)

        setupBoard()
    }

    private fun setupBoard() {
        supportActionBar?.title=gameName ?: getString(R.string.app_name)
        when(boardSize){
            BoardSize.EASY -> {
                tvNumMoves.text="Fácil 4 x 2"
                tvNumPairs.text="Pares 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text="Medio 6 x 3"
                tvNumPairs.text="Pares 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text="Difícil 6 x 4"
                tvNumPairs.text="Pares 0 / 12"
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this,R.color.color_progress_none))
        memoryGame=MemoryGame(boardSize,customGameImages)

        adapter= MemoryBoardAdapter(this,boardSize,memoryGame.cards,object: MemoryBoardAdapter.CardClickListener{
            override fun onCardClicked(position: Int) {
                updateGameflipCard(position)
                Log.i(TAG,"MainActivityClick")
            }

        })
        rvBoard.adapter=adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager= GridLayoutManager(this,boardSize.getWidth())
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.mi_refresh ->{
                //restart game
                if(memoryGame.getNumMoves()>0&&!memoryGame.haveWonGame()){
                    showAlertDialog("Reiniciar juego?",null,View.OnClickListener {
                        setupBoard()
                    })
                }else {
                    setupBoard()
                }
            }
            R.id.mi_new_size->{
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom->{
                showCreationDialog()
                return true
            }
            R.id.mi_download->{
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDownloadDialog() {
        val boardDownloadView= LayoutInflater.from(this).inflate(R.layout.dialog_download,null)
        showAlertDialog("Descargar juego personalizado",boardDownloadView,View.OnClickListener {
            val etDownload = boardDownloadView.findViewById<EditText>(R.id.etDownload)
            val gameToDownLoad=etDownload.text.toString().trim()
            downloadGame(gameToDownLoad)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(requestCode== CREATE_REQUEST_CODE && resultCode== Activity.RESULT_OK){
            val customGameName=data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName==null){
                Log.e(TAG,"Null name from createActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList= document.toObject(UserImageList::class.java)
            if(userImageList?.images==null){
                Log.e(TAG,"Error de firestore")
                Snackbar.make(clRoot,"No se encontró un juego con este nombre, $customGameName",Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size*2
            boardSize=BoardSize.getByValue(numCards)
            customGameImages=userImageList.images
            for(imageUrl in userImageList.images){
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot,"Jugando $customGameName",Snackbar.LENGTH_LONG).show()
            gameName=customGameName
            setupBoard()

        }.addOnFailureListener{
            Log.e(TAG,"Error descargando juego")
        }
    }

    private fun showCreationDialog() {
        val boardSizeView= LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog("Crear tu propio juego",boardSizeView,View.OnClickListener {
            //new value board size
            val desiredBoardSize=when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            //navigate
            val intent = Intent(this,CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE,desiredBoardSize)
            startActivityForResult(intent,CREATE_REQUEST_CODE)
        })
    }

    private fun showNewSizeDialog() {
        val boardSizeView= LayoutInflater.from(this).inflate(R.layout.dialog_board_size,null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when(boardSize){
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }
        showAlertDialog("Escoger tamaño",boardSizeView,View.OnClickListener {
            //new value board size
            boardSize=when(radioGroupSize.checkedRadioButtonId){
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName=null
            customGameImages=null
            setupBoard()
        })
    }

    private fun showAlertDialog(title:String, view: View?,positiveButtonClickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Ok"){_,_ ->
                    positiveButtonClickListener.onClick(null)
                }.show()
    }

    private fun updateGameflipCard(position: Int) {
        if(memoryGame.haveWonGame()){
            Snackbar.make(clRoot, "Has ganado",Snackbar.LENGTH_LONG).show()
            return
        }
        if(memoryGame.isCardFaceUp(position)){
            Snackbar.make(clRoot, "Movimiento inválido",Snackbar.LENGTH_LONG).show()
            return
        }
        if(memoryGame.flipCard(position)){
            Log.i(TAG,"match found: ${memoryGame.numPairsFound}")
            val color = ArgbEvaluator().evaluate(
                    memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                        ContextCompat.getColor(this,R.color.color_progress_none),
                        ContextCompat.getColor(this,R.color.color_progress_full)
            )as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text="Pairs: ${memoryGame.numPairsFound}/${boardSize.getNumPairs()}"
            if(memoryGame.haveWonGame()){
                Snackbar.make(clRoot,"Has ganado!",Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.BLUE,Color.GREEN)).oneShot()
            }
        }
        tvNumMoves.text="Movimientos: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }
}