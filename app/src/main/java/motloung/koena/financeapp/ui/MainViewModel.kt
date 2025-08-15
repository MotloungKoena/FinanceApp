package motloung.koena.financeapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import motloung.koena.financeapp.data.AppDb

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).eventDao()
    val events = dao.all().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    fun clear() = viewModelScope.launch { dao.clear() }
}
