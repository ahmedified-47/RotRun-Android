package com.ahmed.rotrun.data

object RotRunGraph {
    lateinit var repo: RotRunRepository
    fun ready(): Boolean = ::repo.isInitialized
}
