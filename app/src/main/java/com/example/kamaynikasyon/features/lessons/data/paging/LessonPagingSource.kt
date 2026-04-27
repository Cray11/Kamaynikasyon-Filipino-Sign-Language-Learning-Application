package com.example.kamaynikasyon.features.lessons.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.kamaynikasyon.features.lessons.data.models.Lesson
import com.example.kamaynikasyon.features.lessons.data.repositories.LessonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PagingSource for loading lessons in pages.
 * Since lessons are loaded from JSON files, we load them all at once but paginate the display.
 */
class LessonPagingSource(
    private val lessonRepository: LessonRepository
) : PagingSource<Int, Lesson>() {
    
    companion object {
        private const val PAGE_SIZE = 10
    }
    
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Lesson> {
        return try {
            val page = params.key ?: 0
            val pageSize = params.loadSize
            
            // Load all lessons (they're small JSON files, so loading all is acceptable)
            val allLessons = withContext(Dispatchers.IO) {
                lessonRepository.loadLessons()
            }
            
            // Calculate pagination
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, allLessons.size)
            
            if (startIndex >= allLessons.size) {
                // No more data
                LoadResult.Page(
                    data = emptyList(),
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = null
                )
            } else {
                val lessonsPage = allLessons.subList(startIndex, endIndex)
                
                LoadResult.Page(
                    data = lessonsPage,
                    prevKey = if (page > 0) page - 1 else null,
                    nextKey = if (endIndex < allLessons.size) page + 1 else null
                )
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    
    override fun getRefreshKey(state: PagingState<Int, Lesson>): Int? {
        // Return the key for the page that should be loaded when refreshing
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}

