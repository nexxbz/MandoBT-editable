# Control Remoto (Android) — por wifi

Controla otro teléfono Android desde el tuyo **por wifi**. La misma app va en los dos teléfonos:
- **Teléfono controlado:** pantalla "Este teléfono" → activar el servicio en Accesibilidad. Muestra un PIN.
- **Teléfono que controla:** la lista "Dispositivos" encuentra solo al otro teléfono en la wifi (o se agrega por IP),
  pide el PIN la primera vez y abre el control.

## Pantallas
- **Dispositivos:** favoritos y teléfonos que tienen la app en tu wifi (solo esos). Menú ⋮ de cada uno: conectar,
  favoritos, conexión automática (la etiqueta "Auto" solo sale en el que actives) y olvidar.
- **Control:** se adapta al tamaño de la ventana (también en modo ventana). El ícono del mouse cambia la cruceta por el
  panel táctil, en el mismo lugar. Teclado de texto con espacio, flechas, borrar e intro, teclado numérico y "Más botones".
- **Ajustes:** modo de navegación por defecto, botones de volumen del teléfono, velocidad del puntero y del desplazamiento,
  invertir desplazamiento, borrar el campo después de enviar, versión y buscar actualización.

## Qué controla
Inicio, atrás, recientes, notificaciones, volumen y mute, reproducir/pausar/adelantar/retroceder, captura de pantalla,
brillo (con permiso), escribir texto y números.
- **Encendido:** un solo botón; si la pantalla está encendida la apaga y si está apagada la enciende.
- **Cruceta y OK:** en Android 13 o superior usan las teclas de dirección reales del sistema. En Android 12 o menor se
  dibuja un recuadro sobre el elemento seleccionado, las flechas lo mueven al elemento más cercano en esa dirección
  (y desplazan la lista al llegar al borde) y OK lo pulsa; si no se puede, toca en su centro.
- **Panel táctil:** cursor en forma de flecha de ratón. Un toque = clic; deslizar = mover; mantener el dedo quieto y deslizar =
  apretar y deslizar (arrastrar). Botones de abajo: izquierdo (mantenerlo apretado y deslizar en el panel = arrastrar),
  central (clic) y derecho (mantener presionado = abrir las opciones, como al presionar la pantalla).

## Requisitos
- Android 9 o superior, ambos teléfonos en la misma wifi.
- En el teléfono controlado: activar el servicio en Accesibilidad. Si Android dice "Configuración restringida",
  abre Info de la app → menú ⋮ → "Permitir ajustes restringidos".
- Para brillo: permitir "Modificar ajustes del sistema" (botón en "Este teléfono").

## Seguridad
El tráfico va por tu wifi sin cifrar, protegido por un PIN de 6 dígitos. Úsalo en redes de confianza.

## Actualizaciones dentro de la app
Cada push a GitHub compila y publica un **Release** con el APK. Al abrir la app, revisa el último Release y, si es más
nuevo, pregunta **"¿Desea descargar esta actualización?"** con **Aceptar** o **Rechazar**.
El repositorio debe ser **público**. Ajustes (engrane) → "Buscar actualización" explica por qué no hay una si falla.
Todos los builds se firman con la misma llave (`app/debug.keystore`) para poder actualizar encima.

## Limitaciones
- Las TVs y otros dispositivos sin esta app no se controlan en esta versión (la TV por wifi sería el siguiente paso).
- Las flechas no mueven el foco como un control de TV: deslizan la pantalla.
- Tocar en pantalla y escribir dependen de lo que permita cada app (algunas bloquean la accesibilidad).

## Compilar
Sube el proyecto a GitHub: *Actions* compila y el APK queda en *Releases*. O ábrelo con Android Studio.
