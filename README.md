# Conciliación segura de pagos

PagoClaro procesa pagos para comercios. Después de una degradación del proveedor, algunos pagos quedaron con el estado desalineado o con débitos duplicados. Reintentar la operación a ciegas puede agravar el incidente.

Este repositorio resuelve la conciliación de soporte N3 para un pago identificado por su referencia externa. Corrige solo cuando la evidencia alcanza. Si la evidencia es contradictoria o insuficiente, conserva los datos y deja el caso para revisión manual. Repetir la misma conciliación no genera otro movimiento ni otra auditoría.

## Estado de esta rama

La operación se ejecuta con Java y H2. Los scripts de `db/` son el contrato Oracle: consulta de las últimas 24 horas, procedimiento atómico, índices y la muestra. H2 no compila el PL/SQL. Las mismas reglas las cubre `mvn test`.

| Documento | Contenido |
| --- | --- |
| [docs/diagnostico.md](docs/diagnostico.md) | Hechos del incidente, hipótesis, validaciones y corrección propuesta |
| [docs/casos.md](docs/casos.md) | Reglas, supuestos y resultado esperado de los cuatro pagos de muestra |
| [docs/decisiones.md](docs/decisiones.md) | Decisiones, alternativas, costos y cómo se comprobaron |
| [docs/nota-operativa.md](docs/nota-operativa.md) | Cuándo conciliar, cuándo detenerse y qué revisar después |
| [docs/uso-de-ia.md](docs/uso-de-ia.md) | Dónde se usó IA, qué se conservó, qué se corrigió y cómo se validó |

## Ejecutar

Hace falta Java 17 y Maven.

```bash
mvn spring-boot:run
```

La operación recibe la referencia externa del pago:

```bash
curl -X POST http://localhost:8080/api/support/payments/PAY-BX17/reconciliation
```

La misma llamada se puede repetir. La segunda vez el neto sigue en 89000 y no aparece otro reverso. Al cerrar la aplicación, H2 se borra y los cuatro pagos vuelven a su estado inicial.

| Referencia | Primera llamada |
| --- | --- |
| `PAY-AX91` | `RECONCILED`. El pago pasa a `CONFIRMED` y conserva su débito |
| `PAY-BX17` | `RECONCILED`. Un reverso del débito `5003`. Neto `89000` |
| `PAY-CQ22` | `MANUAL_REVIEW`. No cambia estado ni movimientos |
| `PAY-DK44` | `ALREADY_CONSISTENT`. Ya estaba sano |

Una referencia que no existe responde `404` con `outcome` `NOT_FOUND`.

## Pruebas

```bash
mvn test
```

Eso corre la política sin base de datos y la operación contra H2, incluida la repetición de `PAY-BX17`.

## Scripts Oracle

Orden para un esquema vacío. El procedimiento no hace `COMMIT`. El ensayo de `db/02_prc_reconcile_payment.sql` debe cerrarse con `ROLLBACK` si no se quiere conservar la corrección.

| Orden | Script | Qué hace |
| --- | --- | --- |
| 1 | `db/00_tablas.sql` | Tablas del enunciado |
| 2 | `db/04_muestra.sql` | Los cuatro pagos |
| 3 | `db/03_indices.sql` | Índices opcionales |
| 4 | `db/01_deteccion.sql` | Consulta de las últimas 24 horas |
| 5 | `db/02_prc_reconcile_payment.sql` | Procedimiento `prc_reconcile_payment` |

La muestra es del 2026-10-13. Para verla en la consulta, cambie `as_of` por `TIMESTAMP '2026-10-13 12:00:00'`. Deben aparecer `PAY-AX91`, `PAY-BX17` y `PAY-CQ22`. `PAY-DK44` no aparece.

Esta entrega no corrige el job que reintenta el llamado al proveedor. Conciliar un pago deja el neto consistente y no impide un débito nuevo si ese job sigue activo.
