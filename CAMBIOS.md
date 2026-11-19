# Link al repositorio

https://github.com/JUANITOsvg/DA1-Parcial2-JuanSuazo

# Cambios realizados

Las referencias `archivo:línea` de cada **Problema** apuntan al código original (commit `57a18e8`); las de
cada **Corrección**, al código actual.

## 41 · Programa sin señal

**Problema.** `Catalogo.kt:27-32` reemplazaba la lista por el aviso ante cualquier falla, con un mensaje
que no decía que había datos guardados. Tampoco se registraba si alguna vez hubo una descarga exitosa, así
que no se podía distinguir "nunca descargué" de "descargué una v4 vacía". No existía el estado
«sin charlas programadas». Además, solo se capturaba `IOException` (`ProgramaModel.kt:25`): un
`HttpException` cerraba la app.

**Corrección.**
- Nueva tabla `edicion` con una sola fila (`Programa.kt:27`), escrita dentro de la misma transacción
  que aplica el programa (`Programa.kt:55`). Una respuesta exitosa con cero charlas sigue siendo una edición
  válida.
- La base pasa a la versión 2 con `MIGRACION_1_2` (`Programa.kt:75`), registrada en `Servicios.kt:28`. La
  migración crea la tabla sin tocar `charlas` ni las marcas, y si el usuario ya tenía charlas guardadas
  lo deja con edición previa. No se usa migración destructiva.
- `ProgramaModel` expone `hayEdicion` y `falloDescarga` (este último es privado mutable y se expone como
  solo lectura), y captura `IOException` y `HttpException`.
- En `Catalogo.kt:28-34`, la lista se muestra siempre. Si falla y hay edición previa, se avisa que se usan
  los datos guardados. Si falla sin edición previa, se muestra «No se pudo descargar el programa» y el botón
  Reintentar. Si la edición está vacía, «Sin charlas programadas».

## 42 · Agenda personal

**Problema.** `Programa.kt:24` guardaba con `INSERT ... REPLACE`. `REPLACE` borra la fila y la reinserta
con `favorita = false`, así que al pasar de v1 a v2 la charla 11 perdía su marca, y cualquier marca puesta
durante una petición se pisaba. Además, el programa es una instantánea completa pero no se borraban las
charlas retiradas: la 11 seguía en v3.

**Corrección.** `CarteleraDao.guardar` (`Programa.kt:55`) pasa a ser una `@Transaction` con tres pasos:
1. `retirarAusentes`: `DELETE ... WHERE id NOT IN (:vigentes)`. La charla retirada se va con su marca,
   así que si después regresa, entra sin marcar.
2. `insertarNuevas`: `IGNORE`, así que solo entran las charlas que no existían.
3. `actualizarContenido`: un `@Update` parcial (`ContenidoCharla`, sin la columna `favorita`).

La marca nunca se lee ni se escribe desde Kotlin durante la sincronización. Por eso se respeta el valor
que tenga en la base al momento de escribir, aunque se haya cambiado durante la petición.

## 43 · Volver al punto de lectura

**Problema.** El tema vivía en un `remember` dentro de `Catalogo.kt:18`: se perdía al abrir una ficha
(el catálogo sale de la composición) y al rotar. El día vivía en un `MutableStateFlow` común del
ViewModel (`ProgramaModel.kt:15`): se perdía al morir el proceso. Además, la UI lo escribía directamente.

**Corrección.** `ProgramaModel` recibe un `SavedStateHandle` (`ProgramaModel.kt:12`). `dia` y `tema` salen
de `getStateFlow` y se modifican solo con `elegirDia` y `elegirTema` (`ProgramaModel.kt:22-23,52-53`).
Ese estado sobrevive a la rotación y a la muerte del proceso, y no pasa a una tarea nueva. La ficha y la
pestaña ya usaban `rememberSaveable` en `Portada.kt`.

## 44 · Control de descargas

**Problema.**
- Se descargaba en `MainActivity.onStart()` (`MainActivity.kt:20-23`) y en un `LaunchedEffect(Unit)`
  de `Catalogo.kt:16`: rotar, volver del fondo, cambiar de pestaña o salir de una ficha disparaban otra
  petición.
- `recargar()` no impedía peticiones simultáneas.
- `Servicios` se creaba en cada ViewModel (`ProgramaModel.kt:11`): varias instancias de Room y un
  contador que se reiniciaba con cada Activity nueva en vez de con el proceso.

**Corrección.**
- `Servicios` es único por proceso (`Servicios.de`, `Servicios.kt:22`, con `applicationContext`) y lleva
  la bandera `cargaAutomaticaPendiente` (`Servicios.kt:18`).
- `MainActivity.onCreate` llama a `cargaAutomatica()` (`ProgramaModel.kt:31`) después de aplicar el
  extra `servicio_disponible`. Solo la primera llamada del proceso descarga.
- `recargar()` ignora los toques mientras el `Job` anterior está activo (`ProgramaModel.kt:36`).
- Se eliminaron `onStart()` y el `LaunchedEffect`.
- Los filtros no dependen de la carga, así que siguen siendo editables durante la espera.

## Evidencia de validación

En esta máquina no hay emulador, así que todo se validó en un **dispositivo físico (moto g75 5G, Android,
USB)**. Los toques se automatizaron con `adb shell input tap` sobre coordenadas de `uiautomator dump`, y
la base se inspeccionó copiándola con `adb exec-out run-as ar.jornadas cat databases/cartelera.db*`.
Toolchain: JDK 17 (Temurin 17.0.21), Gradle 8.7 (wrapper), SDK 34.

| Archivo en `resultados/` | Qué demuestra |
|---|---|
| `build-assembleDebug.txt` | `./gradlew clean assembleDebug` → `BUILD SUCCESSFUL`, sin warnings |
| `41-migracion-en-dispositivo.txt` | Actualización sobre la base v1 sin desinstalar: `user_version = 2`, marcas conservadas y sin crash |
| `41-programa-sin-senal-en-dispositivo.txt` + 2 capturas | v4 → «Sin charlas programadas»; al reabrir sin señal sigue siendo una edición válida; arranque vacío sin señal → Reintentar, que funciona |
| `42-agenda-personal-en-dispositivo.txt` + 2 capturas | v1→v2: la 11 conserva la marca con el título y la sala nuevos. v3: sale la 11 y entra la 90. Al volver, la 11 regresa sin marcar. Las marcas agregadas o quitadas durante una petición se respetan |
| `43-punto-de-lectura-en-dispositivo.txt` | Día 2 + Datos → ficha → volver → rotar: 2 resultados. `am kill` con la ficha abierta: vuelven la ficha y los filtros. Una tarea nueva arranca sin filtros |
| `44-control-descargas-en-dispositivo.txt` + captura | Contador: 1 al arrancar; +0 por pestaña, ficha y rotación; +1 por doble toque; se puede volver a pedir; filtros editables con la petición en curso |

Logcat sin `FATAL EXCEPTION` en todas las pruebas.