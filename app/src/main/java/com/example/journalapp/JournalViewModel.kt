package com.example.journalapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.journalapp.data.EntryKey
import com.example.journalapp.data.JournalDao
import com.example.journalapp.data.JournalDatabase
import com.example.journalapp.data.JournalEntry
import kotlinx.coroutines.launch

class JournalViewModel(
    application: Application,
    private val dao: JournalDao
) : AndroidViewModel(application) {

    constructor(application: Application) :
            this(application, JournalDatabase.getDatabase(application).journalDao())

    data class Filter(val query: String = "", val sessionOnly: Boolean = false)

    private val _filter = MutableLiveData(Filter())
    val filter: LiveData<Filter> = _filter

    fun setQuery(raw: String) {
        val query = escapeLike(raw.trim())
        if (_filter.value?.query != query) {
            _filter.value = _filter.value?.copy(query = query)
        }
    }

    /** SQLite LIKE treats % and _ as wildcards — escape them so "100%" finds
     *  the literal text. Pairs with ESCAPE '\' in the DAO. */
    private fun escapeLike(input: String): String = buildString {
        for (ch in input) {
            if (ch == '\\' || ch == '%' || ch == '_') append('\\')
            append(ch)
        }
    }

    fun toggleSessionFilter() {
        _filter.value = _filter.value?.let { it.copy(sessionOnly = !it.sessionOnly) }
    }

    val entries: LiveData<List<JournalEntry>> = _filter.switchMap { f ->
        when {
            f.query.isNotBlank() && f.sessionOnly ->
                dao.searchSessionEntries(f.query, SessionManager.sessionId).asLiveData()
            f.query.isNotBlank() ->
                dao.searchEntries(f.query).asLiveData()
            f.sessionOnly ->
                dao.getSessionEntries(SessionManager.sessionId).asLiveData()
            else ->
                dao.getAllEntries().asLiveData()
        }
    }

    val allEntries: LiveData<List<JournalEntry>> = dao.getAllEntries().asLiveData()

    fun getSessionEntries(sessionId: String): LiveData<List<JournalEntry>> =
        dao.getSessionEntries(sessionId).asLiveData()

    suspend fun existingKeys(): Set<EntryKey> = dao.getEntryKeys().toSet()

    fun importAll(entries: List<JournalEntry>, onDone: (Int) -> Unit) =
        viewModelScope.launch {
            dao.insertAll(entries)
            onDone(entries.size)
        }

    fun insert(entry: JournalEntry) = viewModelScope.launch { dao.insert(entry) }
    fun update(entry: JournalEntry) = viewModelScope.launch { dao.update(entry) }
    fun delete(entry: JournalEntry) = viewModelScope.launch { dao.delete(entry) }
    suspend fun getEntry(id: Long): JournalEntry? = dao.getEntryById(id)
}
