package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

// Vista parcial de Charla sin la marca del usuario, para actualizar solo los datos del programa.
data class ContenidoCharla(val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String)

// Fila única que indica que hay una edición descargada con éxito (aunque tenga cero charlas).
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val descargadaEn: Long)

@Dao
interface CarteleraDao {
    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    fun hayEdicion(): Flow<Boolean>

    @Upsert
    suspend fun registrarEdicion(edicion: Edicion)

    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    // La respuesta es una instantánea completa: lo que no vino se retiró (y pierde su marca).
    @Query("DELETE FROM charlas WHERE id NOT IN (:vigentes)")
    suspend fun retirarAusentes(vigentes: List<String>)

    // IGNORE: una charla que ya existe no se toca; una nueva (o que regresa) entra sin marcar.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarNuevas(fichas: List<Charla>)

    // Actualiza solo el contenido: la columna favorita queda con el valor que tenga al escribir,
    // así se respeta una marca agregada o quitada mientras la petición estaba en curso.
    @Update(entity = Charla::class)
    suspend fun actualizarContenido(fichas: List<ContenidoCharla>)

    // Todo o nada: nunca queda un programa a medio aplicar.
    @Transaction
    suspend fun guardar(fichas: List<Charla>) {
        retirarAusentes(fichas.map { it.id })
        insertarNuevas(fichas)
        actualizarContenido(fichas.map {
            ContenidoCharla(it.id, it.titulo, it.tema, it.dia, it.hora, it.sala, it.ponente, it.resumen)
        })
        registrarEdicion(Edicion(descargadaEn = System.currentTimeMillis()))
    }

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

// v1 -> v2: agrega la tabla edicion sin tocar charlas ni marcas. Quien ya tenía charlas
// guardadas (usuarios de la versión instalada) queda con una edición previa válida.
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}
