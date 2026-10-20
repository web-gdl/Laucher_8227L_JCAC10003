# Historial de cambios

Todas las versiones se publican en [Releases](https://github.com/web-gdl/Laucher_8227L_JCAC10003/releases) con los dos APK y sus sumas.

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
