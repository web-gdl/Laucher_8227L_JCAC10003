# Historial de cambios

Todas las versiones se publican en [Releases](https://github.com/web-gdl/Laucher_8227L_JCAC10003/releases) con los dos APK y sus sumas.

## 3.5.3: el inicio abre siempre; instrumentos por etapas

**Probada en el JCAC10003 (12-nov-2026): abre bien y el velocímetro y la brújula funcionan.** Versión estable.

La 3.5.2 no resolvió el bloqueo en el alps 8227L (el inicio no abría y el sistema se congelaba). En un emulador de Android 8.1
las versiones 3.5.x arrancan bien, así que el fallo depende del equipo; el GPS de este estéreo ya se colgaba de vez en cuando
con el software de fábrica (había que pulsar RESET). Esta versión quita del arranque todo lo que toca el GPS:

- **Nada de GPS al abrir.** El velocímetro y el odómetro empiezan apagados: la tarjeta dice «Toca para activar velocímetro y
  odómetro». Al tocarla se pide el permiso (en ventana aparte) y se enciende; desde entonces se enciende sola con el auto.
- **Las llamadas al GPS salen del hilo principal.** Van en un hilo propio con vigilancia: si el GPS del sistema no contesta en 6 s,
  la tarjeta avisa «El GPS del estéreo no responde · reinicia el estéreo (RESET)» y el launcher sigue funcionando.
- **Seguro de arranque por etapas** (`car/Guard`): dibujar los instrumentos → pedir posiciones (solo con el inicio visible) →
  servicio en segundo plano. Cada etapa se anota en disco antes de empezar; si el launcher se congela o se cierra en una, al
  siguiente arranque esa etapa queda desactivada sola y Diagnóstico dice cuál fue («Seguro de instrumentos»).
- El servicio en primer plano se pide una sola vez aunque el estéreo repita el aviso de encendido, y nunca se detiene a medio arrancar.
- Menú del odómetro: «Medir con otras apps delante: sí/no» y «Apagar/Activar velocímetro y odómetro».
- Nueva prueba automática en un emulador (GitHub Actions, `test/emulator.sh`): instala, elige el inicio, activa los
  instrumentos, simula los avisos de ACC y posiciones GPS, y falla si el launcher se cierra o deja de responder.
- El nombre de la app también es «Laucher 8227L» con el sistema en inglés.

## 3.5.2: el inicio no abría al instalarlo

- **Corregido:** en el alps 8227L, tras instalar la 3.5.0/3.5.1 y elegir «Laucher 8227L» en *Abrir con… una vez / siempre*,
  el inicio no se abría. La 3.5 pedía el permiso de ubicación desde la propia pantalla de inicio al abrirse; ahora **nunca se
  pide al abrir**: la tarjeta muestra «Toca para permitir la ubicación» y el permiso se pide en una ventana aparte
  (`PermissionActivity`, en su propia tarea, fuera de la HOME). Al volver se actualiza solo y arranca el odómetro.
- Los instrumentos no pueden impedir que el inicio abra: cualquier fallo del GPS queda en Diagnóstico y el inicio sigue.
- Si el launcher se cierra, `crash.txt` se guarda también en `Android/data/com.webgdl.launcher8227/files/` (accesible con un
  explorador de archivos o por USB) para poder enviarlo.

## 3.5.1: versión definitiva «Laucher 8227L»

Publicada como versión estable (deja de ser «de prueba»).

- Los APK se llaman **`Laucher-8227L.apk`** (la versión para instalar) y **`Laucher-8227L-reemplazo.apk`**.
  Enlaces fijos: `releases/latest/download/Laucher-8227L.apk` y `.../Laucher-8227L-reemplazo.apk`.
- La app aparece como **«Laucher 8227L»** (antes «Inicio (prueba)»). Mismo paquete `com.webgdl.launcher8227`: se instala encima
  de la anterior y conserva favoritos, odómetro y límite.
- La variante de compilación se llama `normal` (`./build.sh --variant normal`; `prueba` sigue aceptándose).
- Release marcada como estable y última; las pruebas automáticas también corren antes de publicar.
- README, Diagnóstico e imágenes actualizados.

## 3.5.0: velocidad, brújula, odómetro y límite de velocidad

Pendiente de probar en el equipo.

- Inicio con el **diseño A** (elegido entre 5 muestras): música a la izquierda y, a la derecha, una tarjeta translúcida con
  los instrumentos. Portada 150 dp, título 32 sp y controles dentro de la columna de la música.
- **Velocímetro** de arco 0–180 km/h con **degradado verde → amarillo → rojo**, a partir del GPS del estéreo (velocidad Doppler; si no
  la trae, distancia/tiempo). «--» sin señal.
- **Límite de velocidad**: tocar el velocímetro → escribir los km/h (10–250) o «Desactivar». Marca roja en el arco, número en rojo
  al pasarlo y **aviso de voz** «Está excediendo el límite de velocidad» tras 2 s por encima; se repite cada 30 s y se rearma al bajar
  3 km/h del límite. Voz del sistema (TextToSpeech) por el canal de navegación; si no hay voz en el idioma, pitido.
- **Brújula** con rumbo del GPS (solo en movimiento; parado conserva el último) y texto «NE 45°».
- **Odómetro** de viaje y total, guardado cada minuto y al apagar el auto. Tocarlo abre: *Reiniciar viaje a 0*, *Ajustar viaje*,
  *Ajustar total*. Pulsación larga: reiniciar viaje / ajustar total directamente.
- Filtros de la distancia: precisión ≤ 30 m, el auto parado no suma (el punto «baila»), saltos > 260 km/h descartados, tramo nuevo
  tras perder la señal. Pruebas con recorridos simulados en `test/` (error < 2 % a 50–60 km/h y 0 m parado).
- Servicio en primer plano `TripService` (notificación silenciosa «Odómetro») solo con el ACC encendido: Android 8 limita el GPS
  de las apps en segundo plano. Nuevo permiso `ACCESS_FINE_LOCATION` (la variante de prueba lo pide una vez; tocar la tarjeta lleva
  a concederlo o a encender el GPS).
- Diagnóstico: líneas «GPS» (estado, posiciones, precisión, satélites) y «Aviso de velocidad».

## 3.4.0: panel más limpio y código revisado

Probada en el JCAC10003 (25-oct-2026): todo correcto.

- Panel de aplicaciones **sin recuadros** detrás de los iconos: solo icono y nombre, con el efecto de toque.
- Código revisado y refactorizado:
  - Clases anónimas de un método pasadas a lambdas (menos código y menos clases en el APK).
  - La fusión «servicio Jancar + sesión multimedia» vive en un solo sitio (`SessionProbe.enrich`), y la usan el widget y la memoria de música.
  - El widget no reescribe textos iguales: el servicio repite la misma información cada pocos segundos y eso reiniciaba el
    desplazamiento del título y obligaba a medir de nuevo.
  - Iconos: un solo rasterizado aunque el panel pida la misma celda varias veces, y sin redibujar si la celda ya muestra ese icono.
  - Fuera recursos sin uso (dibujos, colores, medidas, textos y estilos de la interfaz anterior).
  - El contador de apps ocultas se traduce (es/en).

## 3.3.2: memoria

- Diagnóstico muestra el **desglose de la memoria** del proceso (Java, nativa, gráficos, código, pila, otros, sistema)
  para saber qué hace subir el consumo en el equipo.
- Menos trabajo de la GPU: la pantalla ya no se pinta de negro dos veces (lo hace solo la ventana).
- Al cerrar el panel de aplicaciones se sueltan sus celdas; al salir del inicio se recorta la caché de iconos a la mitad.

## 3.3.1: corrección del encendido

- El informe de Diagnóstico mostró que el estéreo no se reinicia al encender el auto: solo despierta, y el servicio
  cierra la variante de prueba al apagar. Ahora el launcher toma como «apagado» el estado con el que nace, guarda si
  sonaba música al apagar y, al encender, **avisa a la app de memoria con el aviso de la llave dirigido a ella**
  (`com.jancar.services.action.acc`, llega aunque la app esté cerrada). RadilloPlayerWave decide si se reabre;
  las apps que no escuchan ese aviso se abren con `boot_memory=true`, solo si sonaban y desde la pantalla de inicio.

## 3.3.0: la música vuelve al encender el auto

- Al **arrancar el estéreo**, 6 s después (como el launcher de fábrica), se vuelve a abrir la **última app de música**
  con `boot_memory=true`; RadilloPlayerWave y la música de fábrica restauran su cola y siguen sonando si sonaban.
  La variante de reemplazo se lo pide al servicio (`openMemoryApp`); la de prueba abre la app ella misma, porque el
  servicio solo atiende esa petición si viene del launcher de fábrica.
- Al **despertar** (llave en ACC tras haberla quitado), si antes sonaba algo y a los 6 s no ha vuelto, se reabre la app.
  Solo si estás en la pantalla de inicio, para no tapar la navegación.
- El widget **recuerda la última canción**: si no hay fuente activa la muestra con «Última canción · toca ▶ para seguir».
- Diagnóstico: nueva línea «Memoria de música» con la última canción, la app y la última acción tomada.

## 3.2.0: fondo con los colores de la portada

- El fondo ocupa **toda la pantalla** y se pinta con los **colores de la portada muy difuminados**, no con la imagen.
  Se calcula una vez por canción (una rejilla de 4×4 colores, interpolada y difuminada en un bitmap de 64×34) y cambia con un fundido.
- Si la canción no trae portada se **conserva el último fondo**; también se recuerda al reiniciar el estéreo.
- Sustituye al halo cuadrado de la 3.1.0, que dejaba ver los bordes y la propia imagen.

## 3.1.0: inicio «nocturno»

- **Fondo negro puro** que se une con la barra del sistema y no deslumbra de noche.
- **La música al centro**: portada redonda de 170 dp, título grande, tiempo transcurrido y total, y botones de 72 y 92 dp.
  Detrás, el fondo con los colores de la portada (ver 3.2.0).
- **Sin reloj ni iconos de estado duplicados**: el estéreo ya muestra hora, Wi‑Fi, GPS, Bluetooth y USB en su barra.
  Así se corrige también el reloj cortado («8:10 p…») de la 3.0.0 con formato de 12 horas.
- **Dock de 6 favoritos** en anillos con el nombre a dos líneas, más «Todas» para el panel de aplicaciones.
- Apagar pantalla, Ajustes y Diagnóstico pasan a la cabecera del panel de aplicaciones.
- Nombres de apps a dos líneas también en el panel («Gestor de archivos» ya no se corta).


## 3.0.0: primera versión pública (17-oct-2026)

- Launcher 100 % nativo desde cero: una sola Activity, Java sin librerías, toolchain sin Gradle, APK de ~80 KB.
- Reloj, tarjeta de música, 8 favoritos, barra inferior y panel de todas las aplicaciones.
- Cliente binder propio de `com.jancar.services` (música, apagado de pantalla, ACC, cámara de reversa) y sesiones multimedia para apps de terceros.
- Pantalla de Diagnóstico con memoria, servicios y registro.
- Dos variantes: prueba (convive con el de fábrica) y reemplazo (misma identidad que el de fábrica).
- Dominio público (Unlicense). Probada en el equipo: PSS ≈ 26 MB.
