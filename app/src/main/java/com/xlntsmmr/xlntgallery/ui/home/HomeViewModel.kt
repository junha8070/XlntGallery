package com.xlntsmmr.xlntgallery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xlntsmmr.xlntgallery.data.Photo
import com.xlntsmmr.xlntgallery.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ui/home/HomeViewModel.kt
class HomeViewModel(
    private val repository: PhotoRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    // 이미 불러왔으면 다시 안 불러옴 (화면 회전 시 LaunchedEffect가 또 호출하기 때문)
    private var isLoaded = false

    fun loadPhotos() {
        if (isLoaded) return
        isLoaded = true
        viewModelScope.launch {
            _photos.value = repository.loadPhotos()
        }
    }

    // "HomeViewModel은 이렇게 만들어라"는 설명서
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // APPLICATION_KEY로 Application(=Context)을 꺼낼 수 있음
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                HomeViewModel(PhotoRepository(app))
            }
        }
    }
}