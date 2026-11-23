# Flow Launcher — para Flow Box F1

Launcher (pantalla de inicio) para Android TV, manejado 100 % con el control remoto.

## Qué incluye (v1.0.0)

- **Inicio**: reloj grande, fecha en español, estado de la red e IP.
- **Favoritos** (fila superior) que se pueden ordenar, y **todas las apps** en cuadrícula.
- Banners de las apps de TV (YouTube, Netflix, etc.) o el ícono si la app no tiene banner.
- **6 temas de colores** (Azul Flow, Noche, Esmeralda, Púrpura, Rojo y Claro), cada uno con su imagen de fondo, más 8 imágenes incluidas para elegir.
- **Fondo personalizado**: imagen desde la galería, un pendrive USB o el explorador de archivos, con nivel de oscurecimiento.
- **PIN de seguridad**: protege Ajustes, Mantenimiento y las apps que elijas (ideal para niños).
- **Apps ocultas**, orden alfabético o por más usadas, 3 a 8 columnas, nombres siempre visibles o solo al seleccionar.
- **Copias de seguridad**: en Descargas, en un archivo (USB) o copia interna rápida. Incluye el fondo.
- **Centro de mantenimiento**: RAM, almacenamiento, datos del equipo, liberar RAM, limpiar caché, reiniciar el launcher y accesos a los ajustes de Android.
- **Administrador de aplicaciones**: abrir, ver información (borrar caché, forzar detención) y desinstalar.
- Opción para abrir Flow Launcher al encender el equipo.

## Control remoto

| Botón | Acción |
|---|---|
| OK | Abrir la app |
| Mantener OK, o botón Menú | Opciones: favoritos, mover, ocultar, bloquear con PIN, info, desinstalar |
| Atrás / Inicio | Volver arriba |

## Cómo obtener el APK

### Opción A — Sin instalar nada (GitHub Actions)

1. Creá un repositorio nuevo en GitHub (por ejemplo `flow-launcher`) y subí **todo el contenido de esta carpeta** (incluida la carpeta oculta `.github`).
2. Entrá a la pestaña **Actions**: la compilación arranca sola (tarda unos 4–6 minutos).
3. Cuando termine, el APK queda en **Releases** con este link directo:
   `https://github.com/TU_USUARIO/flow-launcher/releases/latest/download/FlowLauncher.apk`

Cada vez que subas un cambio se compila una versión nueva automáticamente.

### Opción B — Android Studio (Windows)

1. Abrí Android Studio → **Open** → elegí la carpeta `FlowLauncher`.
2. Esperá que termine **Gradle Sync** (si ofrece actualizar el plugin de Gradle, aceptá).
3. Menú **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

Todas las compilaciones usan la misma firma (`app/flowlauncher.keystore`), así que cada versión nueva se instala encima de la anterior sin perder la configuración.

## Instalar en el Flow Box F1

- **Con pendrive**: copiá el APK al pendrive, conectalo al Flow Box y abrilo con un explorador de archivos (por ejemplo *File Commander* o *X-plore*).
- **Con la app Downloader**: instalá *Downloader* (AFTVnews) desde la tienda, escribí el link de Releases y se descarga e instala directo.
- Si pide permiso para **instalar apps de origen desconocido**, activalo para esa app.

## Ponerlo como pantalla de inicio

1. Presioná el botón **Inicio** del control. Si aparece «¿Qué app usar?», elegí **Flow Launcher** → **Siempre**.
2. O bien: Flow Launcher → Ajustes → **Elegir launcher predeterminado**.
3. Algunos equipos de operadora bloquean el cambio de launcher. En ese caso, activá **Abrir Flow Launcher al encender** y abrilo desde la lista de apps.

**Recomendación:** no desinstales ni desactives el launcher original del Flow Box. Si algo falla, desinstalá Flow Launcher desde Ajustes de Android → Aplicaciones y todo vuelve a como estaba.

## Estructura del proyecto

```
app/src/main/java/py/crz/flowlauncher/
  HomeActivity.java         Pantalla de inicio
  SettingsActivity.java     Ajustes
  MaintenanceActivity.java  Centro de mantenimiento
  AppManagerActivity.java   Administrador de aplicaciones
  AppRepository.java        Lectura y apertura de apps
  Prefs.java                Configuración guardada
  PinDialogs.java           PIN
  Backup.java               Copias de seguridad
  Wallpaper.java            Fondo personalizado
  LauncherTheme.java        Temas de colores
  Ui.java                   Componentes visuales para control remoto
  BootReceiver.java         Inicio al encender
```

Requisitos: Android 5.0 o superior. Sin librerías externas.
