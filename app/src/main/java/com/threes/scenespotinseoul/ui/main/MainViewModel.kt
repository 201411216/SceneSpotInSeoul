package com.threes.scenespotinseoul.ui.main

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.support.annotation.StringDef
import com.threes.scenespotinseoul.data.AppDatabase
import com.threes.scenespotinseoul.data.model.Location
import com.threes.scenespotinseoul.data.model.Media
import com.threes.scenespotinseoul.data.model.Scene
import com.threes.scenespotinseoul.ui.main.adapter.MediaCategory
import com.threes.scenespotinseoul.utilities.AppExecutors
import com.threes.scenespotinseoul.utilities.runOnDiskIO
import com.threes.scenespotinseoul.utilities.runOnMain

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var db: AppDatabase = AppDatabase.getInstance(application)
    private var executors: AppExecutors = AppExecutors()

    private val _tagAutoCompleteData = MutableLiveData<List<String>>()
    private val _mediaCategoryData = MutableLiveData<List<MediaCategory>>()

    private val _showSearchResult = MutableLiveData<Boolean>()

    val tagAutoCompleteData: LiveData<List<String>>
        get() = _tagAutoCompleteData

    val mediaCategory: LiveData<List<MediaCategory>>
        get() = _mediaCategoryData

    var searchResultMediaData: List<Media> = listOf()
    var searchResultLocationData: List<Location> = listOf()
    var searchResultSceneData: List<Scene> = listOf()

    val showSearchResult: LiveData<Boolean>
        get() = _showSearchResult

    init {
        loadMediaCategory()
    }

    fun loadMediaCategory() {
        runOnDiskIO {
            val media = db.mediaDao().loadAll()
            runOnMain {
                _mediaCategoryData.value = listOf(
                    MediaCategory("Category 1", media),
                    MediaCategory("Category 2", media),
                    MediaCategory("Category 3", media),
                    MediaCategory("Category 4", media),
                    MediaCategory("Category 5", media),
                    MediaCategory("Category 6", media),
                    MediaCategory("Category 7", media)
                )
            }
        }
    }

    fun loadTagAutoComplete(keyword: String) {
        if (keyword.isEmpty()) {
            _tagAutoCompleteData.value = listOf()
        } else {
            executors.diskIO().execute {
                val tags = db.tagDao().loadBySimilarName("%$keyword%", 5)
                executors.mainThread().execute {
                    _tagAutoCompleteData.value = tags.map { it.name }
                }
            }
        }
    }

    fun requestSearch(@RequestType requestType: String, keyword: String) {
        when (requestType) {
            TYPE_EXACTLY -> {
                runOnDiskIO {
                    val tag = db.tagDao().loadByName(keyword.trim())
                    val tagId = tag?.id!!

                    val mediaTags = db.mediaTagDao().loadByTagId(tagId)
                    val media = mediaTags.map {
                        db.mediaDao().loadById(it.mediaId)
                    }

                    val locationTags = db.locationTagDao().loadByTagId(tagId)
                    val locations = locationTags.map {
                        db.locationDao().loadById(it.locationId)
                    }

                    val sceneTags = db.sceneTagDao().loadByTagId(tagId)
                    val scenes = sceneTags.map {
                        db.sceneDao().loadById(it.sceneId)
                    }

                    runOnMain {
                        searchResultMediaData = media
                        searchResultLocationData = locations
                        searchResultSceneData = scenes
                        _showSearchResult.value = true
                    }
                }
            }
            else -> {
                runOnDiskIO {
                    val tags = db.tagDao().loadBySimilarName("%${keyword.trim()}%")

                    val media = tags
                        .flatMap { db.mediaTagDao().loadByTagId(it.id) }
                        .map { db.mediaDao().loadById(it.mediaId) }
                        .distinctBy { it.id }

                    val locations = tags
                        .flatMap { db.locationTagDao().loadByTagId(it.id) }
                        .map { db.locationDao().loadById(it.locationId) }
                        .distinctBy { it.id }

                    val scenes = tags
                        .flatMap { db.sceneTagDao().loadByTagId(it.id) }
                        .map { db.sceneDao().loadById(it.sceneId) }
                        .distinctBy { it.id }

                    runOnMain {
                        searchResultMediaData = media
                        searchResultLocationData = locations
                        searchResultSceneData = scenes
                        _showSearchResult.value = true
                    }
                }
            }
        }
    }

    fun hideSearchResult() {
        _showSearchResult.value = false
    }

    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        TYPE_SIMILAR,
        TYPE_EXACTLY
    )
    annotation class RequestType

    companion object {
        const val TYPE_SIMILAR = "type_similar"
        const val TYPE_EXACTLY = "type_exactly"
    }
}