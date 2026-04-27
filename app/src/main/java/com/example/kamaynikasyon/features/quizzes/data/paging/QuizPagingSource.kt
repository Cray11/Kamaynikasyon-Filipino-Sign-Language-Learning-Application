package com.example.kamaynikasyon.features.quizzes.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kamaynikasyon.features.quizzes.data.models.Quiz
import com.example.kamaynikasyon.features.quizzes.data.repositories.QuizRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PagingSource for loading quizzes in pages.
 * Since quizzes are loaded from JSON files, we load them all at once but paginate the display.
 */
class QuizPagingSource(
    private val quizRepository: QuizRepository
) : PagingSource<Int, Quiz>() {
    
    companion object {
        private const val PAGE_SIZE = 10
    }
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Quiz> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            
            // Load all quizzes (they're small JSON files, so loading all is acceptable)
            val allQuizzes = withContext(Dispatchers.IO) {
                quizRepository.loadQuizzes()
            }
            
            // Calculate pagination
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allQuizzes.size)
            
            if (startIndex >= allQuizzes.size) {
                // No more data
                LoadResult.Page(
                    data = emptyList(),
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = null
                )
            } else {
                val quizzesPage = allQuizzes.subList(startIndex, endIndex)
                
                LoadResult.Page(
                    data = quizzesPage,
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = if (endIndex < allQuizzes.size) page + 1 else null
                )
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, Quiz>): Int? {
        // Return the key for the page that should be loaded when refreshing
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}

