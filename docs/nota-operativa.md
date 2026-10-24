# Nota para soporte

Conciliar un pago es seguro cuando todos sus eventos dicen lo mismo y el ajuste es uno de estos: cambiar el estado, agregar el débito que falta por la diferencia exacta, o reversar débitos completos desde el más reciente hasta cubrir el exceso. El débito original no se borra.

Hay que detenerse, sin aplicar la corrección, cuando ocurre cualquiera de estos casos:

- Hay al menos un `APPROVED` y un `DECLINED`.
- No hay eventos del proveedor.
- El pago está `REJECTED` y la evidencia solo dice `APPROVED`.
- El exceso no coincide con débitos enteros, empezando por el más reciente.
- El neto es negativo.

La operación responde `MANUAL_REVIEW` y no cambia el estado ni los movimientos. La primera vez deja una auditoría con el motivo. Repetirla no agrega otra igual.

## Antes de ejecutar en un lote

1. Corra `db/01_deteccion.sql`. Para la muestra del 13 de octubre de 2026, use `as_of = TIMESTAMP '2026-10-13 12:00:00'`. Deben salir `PAY-AX91`, `PAY-BX17` y `PAY-CQ22`. `PAY-DK44` no sale.
2. Confirme con el proveedor el estado de una referencia antes de reversar dinero. Dos eventos `APPROVED` se tratan como reintentos del mismo pago.
3. Frene el job que reintenta el llamado al proveedor. Esta conciliación no lo apaga.

## Después de ejecutar

Revise, para esa referencia:

- El estado quedó en el que indica la respuesta.
- El neto es el monto del pago si la evidencia es `APPROVED`, o cero si es `DECLINED`.
- El reverso, si existe, apunta al débito corregido. El débito original sigue en la tabla.
- Hay una sola auditoría nueva. Una segunda ejecución no debe insertar otra.
- El proveedor sigue diciendo lo mismo que los eventos usados.

En esta entrega la llamada es:

```bash
curl -X POST http://localhost:8080/api/support/payments/PAY-BX17/reconciliation
```

La segunda vez la respuesta es `ALREADY_CONSISTENT` y el neto sigue en `89000`.

En Oracle el mismo efecto es `prc_reconcile_payment`. No confirma solo. Mire `p_outcome` y el neto, y haga `COMMIT` únicamente si el caso era seguro. Para ensayar, termine en `ROLLBACK`.
