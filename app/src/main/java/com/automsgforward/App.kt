package com.automsgforward

import android.app.Application
import com.automsgforward.sms.InboxCatchUp
import kotlin.concurrent.thread

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Every process start (app opened, boot, SMS broadcast, WorkManager job) is a chance
        // to forward texts that arrived while the process was dead.
        thread(name = "inbox-catch-up") { InboxCatchUp.run(this) }
    }
}
