package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ProgramaModel(app: Application) : AndroidViewModel(app) {
    private val servicios = Servicios(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // true si alguna vez se descargó una edición con éxito (aunque esté vacía): decide si una
    // falla muestra los datos guardados o pide reintentar.
    val hayEdicion = repo.hayEdicion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val dia = MutableStateFlow(0)
    private val _falloDescarga = MutableStateFlow(false)
    val falloDescarga = _falloDescarga.asStateFlow()
    val cargando = MutableStateFlow(false)

    fun recargar() {
        viewModelScope.launch {
            cargando.value = true
            _falloDescarga.value = false
            try {
                repo.sincronizar()
            } catch (e: IOException) {          // sin señal / servicio apagado
                _falloDescarga.value = true
            } catch (e: HttpException) {        // respuesta 4xx/5xx: también es una falla, no un crash
                _falloDescarga.value = true
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
