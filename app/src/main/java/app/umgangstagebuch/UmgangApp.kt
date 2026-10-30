package app.umgangstagebuch

import android.app.Application
import app.umgangstagebuch.data.Backup
import app.umgangstagebuch.data.PdfReport
import app.umgangstagebuch.data.Prefs
import app.umgangstagebuch.data.Repository
import app.umgangstagebuch.reminders.Reminders

class UmgangApp : Application() {
    lateinit var repository: Repository
        private set
    val backup by lazy { Backup(this, repository) }
    val report by lazy { PdfReport(this, repository) }

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this, Prefs(this))
        Reminders.createChannels(this)
    }
}
