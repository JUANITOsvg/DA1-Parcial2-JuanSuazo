package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.de(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // true si alguna vez se descargó una edición con éxito (aunque esté vacía): decide si una
    // falla muestra los datos guardados o pide reintentar.
    val hayEdicion = repo.hayEdicion.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    // Filtros en SavedStateHandle: sobreviven a rotación, a salir de la pantalla y a la muerte del
    // proceso (Android los restaura con la tarea); una tarea nueva arranca sin filtros.
    val dia = estado.getStateFlow("dia", 0)
    val tema = estado.getStateFlow("tema", "Todos")
    private val _falloDescarga = MutableStateFlow(false)
    val falloDescarga = _falloDescarga.asStateFlow()
    val cargando = MutableStateFlow(false)

    private var descarga: Job? = null

    // La llama la Activity al crearse; solo la primera vez en el proceso dispara la descarga.
    fun cargaAutomatica() {
        if (servicios.cargaAutomaticaPendiente.getAndSet(false)) recargar()
    }

    fun recargar() {
        if (descarga?.isActive == true) return   // un toque durante una petición no agrega intentos
        descarga = viewModelScope.launch {
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

    fun elegirDia(valor: Int) { estado["dia"] = valor }
    fun elegirTema(valor: String) { estado["tema"] = valor }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
