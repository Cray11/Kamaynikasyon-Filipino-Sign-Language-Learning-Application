package com.example.kamaynikasyon.features.lessons.data.models

import android.os.Parcelable
import com.example.kamaynikasyon.core.media.MediaResource
import com.example.kamaynikasyon.core.media.MediaType
import com.example.kamaynikasyon.core.media.toMediaResource
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Lesson(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val modelConfig: String? = null,
    val pages: List<LessonPage>
) : Parcelable

@Parcelize
data class LessonPage(
    val id: String,
    val type: PageType,
    val title: String,
    val content: String,
    val media: MediaResource? = null,
    val question: Question? = null,
    val expectedSign: String? = null,
    val modelConfig: String? = null
) : Parcelable

enum class PageType {
    @SerializedName("text")
    TEXT,
    @SerializedName("camera")
    CAMERA,
    @SerializedName("question")
    QUESTION
}

@Parcelize
data class Question(
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

fun LessonPage.primaryMedia(): MediaResource? = media

fun QuestionContent.primaryMedia(): MediaResource? = media

fun AnswerOption.primaryMedia(): MediaResource? = media

