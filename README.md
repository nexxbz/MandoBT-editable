# Mando BT (versión con distribución editable)

Convierte un teléfono Android 9 o superior en control, ratón y teclado Bluetooth
para una PC u otro teléfono. No hay que instalar nada en el equipo que recibe.

## Cómo generar la APK
1. Instala Android Studio (Koala o superior) y ábrelo con "Open" sobre esta carpeta.
2. Espera a que termine la sincronización de Gradle (descarga lo necesario).
3. Menú Build > Build Bundle(s) / APK(s) > Build APK(s). El archivo queda en
   app/build/outputs/apk/debug/app-debug.apk
4. Para instalar por cable: activa Depuración USB en el teléfono (Ajustes > Opciones de
   desarrollador) y pulsa Run en Android Studio, o usa `adb install app-debug.apk`.

## Uso
1. Abre la app y concede el permiso de Bluetooth.
2. Pulsa Visible, y en la PC u otro teléfono busca "Mando BT" y empareja. También
   puedes emparejar desde los ajustes de ese equipo y luego pulsar Conectar en la app.
3. Cambia entre Mando, Ratón y Teclado. Elige la consola en la lista (Xbox, PlayStation,
   Switch, Genérico).
4. Editar: arrastra cada botón, palanca o dirección de la cruceta a donde quieras,
   toca uno para cambiar su tamaño y pulsa Guardar. Se guarda una distribución por consola.
5. Ajustes: sensibilidad y zona muerta de las palancas, velocidad del ratón y del desplazamiento.

## Notas
- El equipo que recibe lo ve como un gamepad HID genérico. Muchos juegos de PC que solo
  aceptan XInput necesitan un traductor como Steam Input.
- PlayStation verifica la autenticidad del control, así que no aceptará esta emulación.
- Si el teléfono no admite el modo HID (algunos fabricantes lo bloquean), la app lo avisa.
- El modo USB (controlar por cable) no está incluido en esta versión.

## Compilar la APK en GitHub (sin Android Studio)
Sube el contenido de esta carpeta a un repositorio nuevo de GitHub. La compilación
empieza sola (pestaña Actions) y la APK se descarga desde el resultado, en
"Artifacts > MandoBT-apk". Los pasos detallados están en la respuesta de la conversación.

## Menú
La app abre a pantalla completa. El botón de tres rayas (arriba al centro) abre el menú:
modo (Mando, Ratón, Teclado), consola, sensibilidad y ajustes, editar botones y conexión.
Si ya habías guardado una distribución antes de esta versión, usa Editar > Restablecer
para ver la nueva disposición.
