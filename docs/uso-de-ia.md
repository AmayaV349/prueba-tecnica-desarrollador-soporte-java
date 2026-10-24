# Uso de IA

Usé el asistente de programación del IDE para avanzar más rápido en el esqueleto y en los borradores. Las reglas de negocio, el diagnóstico y la lectura de las pruebas las cerré sobre la muestra, no sobre la primera salida. No pegué un resultado sin recorrer `PAY-AX91`, `PAY-BX17`, `PAY-CQ22` y `PAY-DK44`.

No incluí credenciales, datos de clientes ni información interna de la empresa en los pedidos. Los únicos pagos usados son los cuatro de la prueba.

## Qué le pedí y qué hice con la salida

| Parte | Objetivo | Qué conservé | Qué corregí o rechacé | Cómo lo validé |
| --- | --- | --- | --- | --- |
| Diagnóstico | Separar el log de la hipótesis | El orden del segundo tramo de `PAY-BX17`: evento `EVT-B2`, débito `5003`, aviso de estado ya confirmado, neto `178000` | No dejé como hecho el primer débito ni el momento en que el pago pasó a `CONFIRMED`. El extracto no los muestra | Releí el log línea por línea y lo contrasté con la muestra |
| Regla de conciliación | Una función sin base de datos y las pruebas de los cuatro pagos | Confirmar `PAY-AX91`, reversar el duplicado de `PAY-BX17`, no tocar `PAY-CQ22`, dejar quieto `PAY-DK44` | Rechacé desempatar `APPROVED` contra `DECLINED` por cantidad de eventos. Rechacé borrar el débito `5003`. Precisé el reverso: del más reciente al más antiguo, y si el más reciente no cabe no se usa uno anterior | `mvn test` y la tabla de [casos.md](casos.md) |
| Operación Spring Boot | Exponer la regla, bloquear el pago y persistir solo lo nuevo | La política sigue pura. El servicio bloquea la fila y después decide | La respuesta no devuelve la excepción, el SQL ni identificadores de más. El `404` de una referencia desconocida quedó explícito | `mvn test`, incluida la segunda llamada a `PAY-BX17` y dos llamadas simultáneas con un solo reverso |
| SQL y PL/SQL | Consulta de 24 horas y procedimiento atómico | La ventana por `updated_at`, `received_at` o `created_at`, y el procedimiento sin `COMMIT` | No junté eventos y movimientos en un solo `JOIN`: eso multiplica el neto. Los agregué por separado. El procedimiento no escribe ningún reverso si el plan completo no cuadra | Recorrido manual de los cuatro pagos sobre `db/01_deteccion.sql` y `db/02_prc_reconcile_payment.sql`. H2 no ejecuta el PL/SQL; la misma regla está cubierta por las pruebas Java |

## Qué sigue siendo decisión mía

Dos eventos `APPROVED` se tratan como reintentos de un mismo pago. Si en producción fueran dos cargos, el reverso sobraría. Antes de aplicar la corrección en un lote, cruzaría una referencia con el estado canónico del proveedor y miraría si el patrón del timeout se repite en otros pagos. Ese límite está en [decisiones.md](decisiones.md) y en [nota-operativa.md](nota-operativa.md).
