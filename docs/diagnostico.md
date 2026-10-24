# Diagnóstico del incidente

Incidente de soporte N3. La referencia visible en el log es `PAY-BX17`. Los otros tres pagos de la muestra sirven para fijar el comportamiento de la conciliación; no forman parte de este extracto.

Fecha del extracto: 2026-10-13.

## Hechos observados

Estos puntos salen del log y de la muestra. Lo que el extracto no muestra no se trata como hecho.

1. A las 09:41:22.104 empieza la llamada `APPROVE` del pago `PAY-BX17`, intento 1.
2. A las 09:41:23.983 esa llamada termina por timeout a los 1800 ms.
3. A las 09:41:24.106 se agenda un reintento con motivo `provider-timeout`.
4. A las 09:41:25.317 se recibe el evento de proveedor `EVT-B2` con estado `APPROVED`.
5. A las 09:41:25.390 se crea el movimiento contable `5003`, de tipo `DEBIT`.
6. A las 09:41:25.611 aparece el aviso de que el pago ya estaba `CONFIRMED` durante el reintento. El aviso queda después de crear el débito `5003`.
7. A las 09:43:08.512 el job de conciliación encuentra débito neto `178000` y monto esperado `89000` para `PAY-BX17`.
8. La muestra describe `PAY-BX17` así: estado `CONFIRMED`, monto `89000`, dos eventos `APPROVED` y dos movimientos `DEBIT`.

El extracto no muestra el primer débito, el primer evento de proveedor, el momento en que el estado pasó a `CONFIRMED` ni el identificador del segundo evento distinto de `EVT-B2`.

`178000` es el doble de `89000`. Con dos débitos y ningún reverso en la muestra, el neto duplicado coincide con dos débitos completos del mismo monto.

## Hipótesis de causa

El primer intento alcanzó a dejar el pago en `CONFIRMED` y a registrar un débito. Tras el timeout, el reintento volvió a aceptar un `APPROVED` y registró el débito `5003`. El control de "el pago ya está confirmado" se evaluó después de insertar ese movimiento, así que no impidió el segundo débito.

Es una hipótesis. El log prueba el orden del segundo tramo (evento, débito `5003`, aviso de estado ya confirmado) y el neto duplicado. No prueba por sí solo qué escribió el primer intento.

La causa de fondo que interesa a soporte es esta: un reintento dejó un efecto contable nuevo sobre un pago que ya tenía débito. Una nueva ejecución, sin mirar el neto, puede agregar otro débito o, si se compensa mal, un reverso de más.

## Qué validar en producción antes de corregir

Antes de aplicar la corrección fuera de este ejercicio, soporte comprobaría:

1. Identificador, monto y hora de cada `DEBIT` de `PAY-BX17`, y que no exista ya un `REVERSAL`.
2. Identificador y estado de los dos eventos de proveedor. Hace falta confirmar que los dos son `APPROVED` y que no hay un `DECLINED` que el extracto no muestre.
3. Que el débito `5003` es el más reciente. El log lo sitúa en el reintento; la hora del otro débito no viene en el extracto.
4. El estado canónico de esa referencia en el proveedor. La conciliación usa la evidencia ya guardada; no vuelve a llamar al proveedor.
5. Si otros pagos de la misma ventana repiten timeout, reintento y neto duplicado. Si el patrón es masivo, conciliar un solo pago no detiene el job que reintenta.

## Corrección propuesta para PAY-BX17

Dejar el pago en `CONFIRMED`. Registrar un único `REVERSAL` de `89000`, con `related_entry_id` apuntando al débito duplicado más reciente. Si las horas confirman el orden del log, ese débito es el `5003`.

El débito original se conserva. Ningún movimiento se actualiza ni se elimina. El neto queda en `89000`, igual al monto del pago.

Ejecutar otra vez la misma conciliación, ya con ese reverso, no crea otro movimiento ni otra auditoría.

## Qué no hace esta corrección

- No borra el débito `5003` ni lo modifica.
- No elige un ganador cuando un pago tiene eventos `APPROVED` y `DECLINED`.
- No reabre un pago que ya está `REJECTED` aunque los eventos digan `APPROVED`.
- No inventa un reverso parcial si el exceso no coincide con un débito completo.
- No marca como incidente un pago `PENDING` reciente que todavía no tiene eventos ni movimientos. Ese caso sigue en vuelo. La consulta de las últimas 24 horas se define en la rama de base de datos.
