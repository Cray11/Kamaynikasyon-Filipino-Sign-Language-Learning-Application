package com.example.kamaynikasyon.features.quizzes.data.models

import android.os.Parcelable
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.core.media.MediaType
import com.example.kamaynikasyon.core.media.toMediaResource
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Quiz(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val questions: List<QuizQuestion>
) : Parcelable

@Parcelize
data class QuizQuestion(
    val id: String,
    val question: QuestionContent,
    val options: List<AnswerOption>,
    val correctAnswer: Int,
    val explanation: String? = null
) : Parcelable

@Parcelize
data class QuestionContent(
    val type: ContentType,
    val text: String? = null,
    val media: MediaResource? = null
) : Parcelable

@Parcelize
data class AnswerOption(
    val id: Int,
    val type: ContentType,
    val text: String? = null,
    val media: MediaResource? = null
) : Parcelable

enum class ContentType {
    @SerializedName("text")
    TEXT,
    @SerializedName("video")
    VIDEO,
    @SerializedName("image")
    IMAGE
}

fun QuestionContent.primaryMedia(): MediaResource? = media

fun AnswerOption.primaryMedia(): MediaResource? = media

@Parcelize
data class QuizResult(
    val quizId: String,
    val score: Int,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val completionTime: Long,
    val date: String
) : Parcelable

