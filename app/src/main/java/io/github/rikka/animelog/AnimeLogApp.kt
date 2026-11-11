package io.github.rikka.animelog

import android.app.Application
import io.github.rikka.animelog.data.Repository

class AnimeLogApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
    }
}
