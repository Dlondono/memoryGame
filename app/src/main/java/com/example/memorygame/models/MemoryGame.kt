package com.example.memorygame.models

import android.util.Log
import com.example.memorygame.utils.DEFAULT_ICONS

class MemoryGame(private val boardSize: BoardSize, customImages: List<String>?){

    val cards:List<MemoryCard>
    var numPairsFound= 0
    private var numFlips=0;
    private var indexOfSelectedCard: Int?=null

    init {
        if(customImages==null){
            val chosenImages= DEFAULT_ICONS.shuffled().take(boardSize.getNumPairs())
            val randomizedImages=(chosenImages+chosenImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it)}
        }else{
            val randomizedImages= (customImages+customImages).shuffled()
            cards =randomizedImages.map { MemoryCard(it.hashCode(),it)}
        }
    }
    fun flipCard(position: Int):Boolean {
        var foundMatch=false
        numFlips++
        //0 or 2 restore and flip new card
        if (indexOfSelectedCard==null) {
            restore()
            indexOfSelectedCard=position
        }else{
            //1 flip and check match
            foundMatch= checkForMatch(indexOfSelectedCard!!,position)
            indexOfSelectedCard=null
        }

        val card=cards[position]
        card.isFaceUp=!card.isFaceUp
        Log.i("MemoryGameLogic",card.id.toString())
        return  foundMatch
    }

    private fun checkForMatch(position1: Int, position2: Int):Boolean {
    if(cards[position1].id !=cards[position2].id){
        return false
    }
        cards[position1].isMatched=true
        cards[position2].isMatched=true
        numPairsFound++
        return  true
    }

    private fun restore() {
        for(card in cards){
            if(!card.isMatched) {
                card.isFaceUp = false
            }
        }
    }

    fun haveWonGame(): Boolean {
        return numPairsFound==boardSize.getNumPairs()
    }

    fun isCardFaceUp(position: Int): Boolean {
        return cards[position].isFaceUp
    }

    fun getNumMoves(): Int {
        return numFlips/2
    }
}