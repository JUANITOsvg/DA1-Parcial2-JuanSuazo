package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicBoolean

// Contenedor único por proceso: una sola base Room, un solo Retrofit y un solo ServicioLocal
// (su contador de peticiones vuelve a cero solo cuando muere el proceso).
class Servicios private constructor(context: Context) {
    val mesa = ServicioLocal(context)

    // Una sola carga automática por sesión (proceso), no por Activity ni por ViewModel.
    val cargaAutomaticaPendiente = AtomicBoolean(true)

    companion object {
        @Volatile private var instancia: Servicios? = null
        fun de(context: Context): Servicios = instancia ?: synchronized(this) {
            instancia ?: Servicios(context.applicationContext).also { instancia = it }
        }
    }

    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db")
        .addMigrations(MIGRACION_1_2).build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    val agenda = AgendaRepository(api, archivo.cartelera())
}
