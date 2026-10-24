# Decisiones

Cada decisión sigue el mismo esquema: contexto, alternativas, elección, costo y cómo se comprobó. Los supuestos de negocio están en [casos.md](casos.md).

## La regla vive en Java y también en el procedimiento

La evaluación pide una operación ejecutable y un PL/SQL atómico. H2, que es la base de las pruebas, no compila PL/SQL.

Se consideró dejar la regla solo en Java y que el procedimiento recibiera la mutación ya decidida. También se consideró dejar la regla solo en Oracle y probar el Java con una base de verdad duplicada a mano.

La regla está en los dos lugares, con los mismos resultados de [casos.md](casos.md). Java es lo que `mvn test` ejecuta. `prc_reconcile_payment` hace lo mismo bajo `SELECT FOR UPDATE` y no confirma la transacción. El costo es mantener las dos copias: un cambio de regla tiene que tocar las dos. La aplicación no llama al procedimiento, porque el runtime de esta entrega es H2.

## H2 para ejecutar, Oracle para el artefacto

Oracle no está en el entorno de la prueba. La operación arranca con H2 en memoria y `data.sql`. Los scripts de `db/` son el contrato para el esquema real. Se comprobó la operación con `mvn test`. El SQL se recorrió con los cuatro pagos de la muestra; el resultado esperado está escrito en `db/01_deteccion.sql`.

## Bloqueo de la fila, no solo la versión

Dos conciliaciones pueden leer el mismo neto duplicado y proponer el mismo reverso. `row_version` detecta el choque al final. El `SELECT FOR UPDATE` evita llegar a escribir dos veces: la segunda espera, relee y ya ve el reverso.

En Java el bloqueo es `findByExternalRefForUpdate`. En Oracle es el `FOR UPDATE` del procedimiento. La prueba de dos llamadas simultáneas deja un solo reverso.

## Idempotencia por el estado, no por un id de request

La conciliación no trae un identificador de intento. El resultado correcto se calcula con la evidencia actual. Si esa evidencia ya está alineada, no hay movimiento nuevo. Si ya existe la auditoría equivalente, no se inserta otra.

Se consideró guardar una llave de idempotencia por llamada. Obliga a que el cliente la envíe, y soporte a veces reintenta desde otra pantalla. El costo del enfoque elegido: si alguien cambia los eventos entre las dos llamadas, la segunda sí puede escribir. Es el efecto buscado.

## No se desempata una evidencia contradictoria

`PAY-CQ22` tiene un `APPROVED` y un `DECLINED`. Contar votos y quedarse con el estado más repetido produciría un débito o un rechazo sin base suficiente. La alternativa elegida es no tocar estado ni ledger y dejar `MANUAL_REVIEW`. La prueba de `PAY-CQ22` cubre la primera llamada y la repetición.

## El ledger no se corrige borrando

El débito `5003` permanece. La corrección es un `REVERSAL` con `related_entry_id` hacia el débito más reciente que quepa entero en el exceso. Si ese débito no cabe, no se usa uno más antiguo y no se escribe nada. Borrar o actualizar el movimiento escondería el incidente. La prueba de `PAY-BX17` comprueba que el `5003` sigue siendo un `DEBIT` y que la segunda llamada no agrega otro reverso.

## La respuesta no explica el interior

El cuerpo HTTP lleva referencia, resultado, estado, neto y motivo. Un pago inexistente responde `404` con `NOT_FOUND`. Un fallo inesperado responde `500` con un texto fijo; el detalle queda en el log del servidor, con la referencia y sin datos de cliente. Esta muestra no tiene tarjetas ni secretos, y aun así la excepción no sale en el JSON.

## Índices, aparte y opcionales

`db/03_indices.sql` no hace falta para que la operación funcione. Los índices por `payment_id` sirven a la conciliación y a la consulta de 24 horas. El índice único sobre `related_entry_id` impide dos reversos del mismo débito. En Oracle varios `NULL` caben en ese índice, así que los débitos originales siguen siendo válidos.

## Fuera de esta entrega

No se llama al proveedor durante la conciliación: se usa la evidencia ya guardada. No se modifica el job que reintentó el `APPROVE` después del timeout. Conciliar `PAY-BX17` deja el neto bien y no impide que ese job vuelva a insertar un débito. El paso siguiente, antes de correr esto en masa, es frenar ese reintento y cruzar una referencia con el estado canónico del proveedor.
