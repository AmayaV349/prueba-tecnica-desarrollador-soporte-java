# Reglas y resultado esperado

La operación de soporte recibe la referencia externa de un pago y decide con `PAYMENT`, `PROVIDER_EVENT` y `LEDGER_ENTRY`. Esta página fija el resultado de los cuatro pagos de muestra. Las pruebas de las ramas siguientes deben cumplir esta tabla.

## Definiciones

- El débito neto es la suma de los movimientos `DEBIT` menos la suma de los movimientos `REVERSAL`.
- La evidencia es unánime cuando todos los eventos tienen el mismo estado: todos `APPROVED` o todos `DECLINED`.
- La evidencia es contradictoria cuando hay al menos un `APPROVED` y un `DECLINED`. No se desempata por cantidad de eventos.
- La evidencia es insuficiente cuando el pago no tiene eventos.
- Un movimiento contable solo se agrega. No se actualiza ni se elimina.
- Una corrección de un débito es un `REVERSAL` nuevo, con `related_entry_id` apuntando al débito que corrige.

## Decisión

1. Evidencia contradictoria o insuficiente: no se cambia el estado ni el ledger. El resultado es `MANUAL_REVIEW`.
2. Solo eventos `APPROVED`: el estado objetivo es `CONFIRMED` y el neto objetivo es el monto del pago.
3. Solo eventos `DECLINED`: el estado objetivo es `REJECTED` y el neto objetivo es `0`. Si hay débitos, se reversan con la misma regla de exceso exacto.
4. Si el pago está `REJECTED` y la evidencia es solo `APPROVED`, el resultado es `MANUAL_REVIEW`. No se reabre solo.
5. Si falta dinero, la evidencia es solo `APPROVED` y la diferencia es exacta, se agrega un `DEBIT` por esa diferencia.
6. Si sobra dinero, se reversan débitos completos del más reciente al más antiguo. Cada uno se reversa solo si su monto cabe en el exceso que falta. Si el más reciente no cabe, no se usa un débito anterior: el resultado es `MANUAL_REVIEW` y no se escribe ningún movimiento. No se parte un monto.
7. La primera ejecución que no cambia datos deja una auditoría. La repetición de una conciliación ya resuelta, o ya marcada para revisión con el mismo motivo, no escribe de nuevo.

Resultados de la operación:

| Resultado | Cuándo |
| --- | --- |
| `RECONCILED` | Esta ejecución cambió el estado o agregó un movimiento |
| `ALREADY_CONSISTENT` | El pago ya cumple el estado y el neto que pide la evidencia |
| `MANUAL_REVIEW` | No se puede decidir con seguridad y no se tocan los datos |

Auditoría, una sola vez por motivo:

| Acción | Cuándo se inserta |
| --- | --- |
| `RECONCILIATION_APPLIED` | La ejecución aplicó un cambio |
| `RECONCILIATION_NO_CHANGE` | La primera vez que un pago ya consistente se concilia |
| `MANUAL_REVIEW` | La primera vez que el caso queda para revisión, con el motivo de esta tabla |

El texto del motivo cabe en `SUPPORT_AUDIT.reason` (400 caracteres).

## Los cuatro pagos

Montos y estados tal como vienen en la muestra. El neto inicial se calcula con los movimientos descritos ahí.

| Referencia | Estado inicial | Monto | Eventos | Ledger inicial | Neto inicial |
| --- | --- | --- | --- | --- | --- |
| `PAY-AX91` | `PENDING` | 125000 | 1 `APPROVED` | 1 `DEBIT` de 125000 | 125000 |
| `PAY-BX17` | `CONFIRMED` | 89000 | 2 `APPROVED` | 2 `DEBIT` de 89000 | 178000 |
| `PAY-CQ22` | `PENDING` | 54000 | 1 `APPROVED` y 1 `DECLINED` | sin movimientos | 0 |
| `PAY-DK44` | `CONFIRMED` | 31000 | 1 `APPROVED` | 1 `DEBIT` de 31000 | 31000 |

### Primera ejecución

| Referencia | Resultado | Estado final | Ledger | Auditoría | Motivo |
| --- | --- | --- | --- | --- | --- |
| `PAY-AX91` | `RECONCILED` | `CONFIRMED` | Sin movimientos nuevos. El débito existente se conserva | `RECONCILIATION_APPLIED` | El pago queda `CONFIRMED`. La evidencia es `APPROVED` y el débito neto ya es igual al monto. |
| `PAY-BX17` | `RECONCILED` | `CONFIRMED` | Un `REVERSAL` de 89000 ligado al débito más reciente. Los dos débitos se conservan. Neto final 89000 | `RECONCILIATION_APPLIED` | Se registró un reverso de 89000 sobre el débito duplicado más reciente. El débito neto queda igual al monto. |
| `PAY-CQ22` | `MANUAL_REVIEW` | `PENDING` | Sin cambios | `MANUAL_REVIEW` | Hay eventos `APPROVED` y `DECLINED` para el mismo pago. No se aplica corrección. |
| `PAY-DK44` | `ALREADY_CONSISTENT` | `CONFIRMED` | Sin cambios. Neto 31000 | `RECONCILIATION_NO_CHANGE` | El pago ya está consistente con la evidencia. No se requieren cambios. |

### Segunda ejecución

La evidencia y los datos no cambian entre las dos llamadas. La segunda no inserta movimientos ni filas de auditoría.

| Referencia | Resultado | Escrituras |
| --- | --- | --- |
| `PAY-AX91` | `ALREADY_CONSISTENT` | Ninguna |
| `PAY-BX17` | `ALREADY_CONSISTENT` | Ninguna |
| `PAY-CQ22` | `MANUAL_REVIEW` | Ninguna |
| `PAY-DK44` | `ALREADY_CONSISTENT` | Ninguna |

`PAY-CQ22` sigue en revisión porque la evidencia sigue siendo contradictoria. El resultado se repite; el registro de auditoría, no.

## Supuestos y riesgos

Estos puntos no están cerrados por el enunciado. La implementación los va a seguir. Cambiar uno cambia el resultado de un caso.

| Supuesto | Riesgo | Qué lo comprobaría |
| --- | --- | --- |
| Varios eventos con el mismo estado son reintentos de un mismo pago, no cargos distintos. Por eso dos `APPROVED` no son contradicción. | Reversar un débito que sí correspondía a un segundo cargo. | El estado canónico de esa referencia en el proveedor, y que la referencia externa identifique un único pago. |
| El débito que se conserva es el más antiguo. Se reversa el más reciente, entero. Si hacen falta varios, se sigue con el siguiente más reciente mientras cada uno quepa en el exceso que falta. Si el más reciente no cabe, no se usa un débito anterior y no se escribe nada. Si la hora coincide, el más reciente es el de identificador mayor. En `PAY-BX17` el candidato es el movimiento `5003`, si su hora es la posterior. | Apuntar el reverso al débito que sí debía quedarse, si el orden real no es el del log. | Hora e identificador de los dos débitos antes de ejecutar. |
| Un pago `REJECTED` con evidencia solo `APPROVED` no se pasa a `CONFIRMED` de forma automática. | Dejar sin corregir un rechazo que fue un fallo del propio sistema. | El motivo del rechazo y si el proveedor mantiene `APPROVED`. |
| Sin eventos no se modifica nada, aunque el estado y el ledger parezcan sanos. | Señalar para revisión un pago coherente cuya evidencia aún no se guardó. | Si los eventos existen en el proveedor y faltan en la base. |
| La repetición es idempotente porque el estado deseado se calcula con la evidencia actual. No hace falta un identificador de request. | Una segunda llamada sí escribe si alguien cambió eventos o movimientos entre las dos. Ese efecto es deliberado: la evidencia nueva merece otra decisión. | Comparar eventos y ledger de las dos ejecuciones cuando aparezca una auditoría nueva. |

## Casos que la muestra no trae

Quedan decididos para que la política no improvise cuando aparezcan.

| Situación | Resultado | Efecto |
| --- | --- | --- |
| Solo `DECLINED`, con neto en cero y estado `PENDING` | `RECONCILED` | El estado pasa a `REJECTED`. No se crean movimientos. |
| Solo `DECLINED` y todavía hay débito neto | `RECONCILED` | Reversos enteros hasta dejar el neto en cero, y estado `REJECTED`, si cada exceso cuadra. Si no cuadra, `MANUAL_REVIEW` y sin cambios. |
| `REJECTED` con solo eventos `APPROVED` | `MANUAL_REVIEW` | Sin cambios. |
| Exceso de neto que no coincide con un débito completo | `MANUAL_REVIEW` | Sin cambios. |
| Sin eventos | `MANUAL_REVIEW` | Sin cambios. |
| Solo `APPROVED`, estado `PENDING` y ningún débito | `RECONCILED` | Un `DEBIT` por el monto del pago y estado `CONFIRMED`. |
