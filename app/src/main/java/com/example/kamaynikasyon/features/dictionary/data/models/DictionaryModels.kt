package com.example.kamaynikasyon.features.dictionary.data.models

import com.example.kamaynikasyon.core.media.MediaResource

data class Word(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val media: MediaResource? = null
) {
    fun primaryMedia(): MediaResource? = media
}

data class CategoryData(
    val category: String,
    val words: List<Word>
)

data class CategoryIndex(
    val id: String? = null, // Optional, not used anymore - file name is used instead
    val name: String,
    val description: String,
    val file: String,
    val icon: String
)

data class DictionaryIndex(
    val categories: List<CategoryIndex>
)
