# Conciliación segura de pagos

PagoClaro procesa pagos para comercios. Después de una degradación del proveedor, algunos pagos quedaron con el estado desalineado o con débitos duplicados. Reintentar la operación a ciegas puede agravar el incidente.

Este repositorio resuelve la conciliación de soporte N3 para un pago identificado por su referencia externa. Corrige solo cuando la evidencia alcanza. Si la evidencia es contradictoria o insuficiente, conserva los datos y deja el caso para revisión manual. Repetir la misma conciliación no genera otro movimiento ni otra auditoría.

## Estado de esta rama

La operación de soporte ya se puede ejecutar. La base de esta rama es H2 en memoria, con los cuatro pagos de muestra. Los scripts Oracle quedan para la rama siguiente.

| Documento | Contenido |
| --- | --- |
| [docs/diagnostico.md](docs/diagnostico.md) | Hechos del incidente, hipótesis, validaciones y corrección propuesta |
| [docs/casos.md](docs/casos.md) | Reglas, supuestos y resultado esperado de los cuatro pagos de muestra |

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

## Qué viene después

Consulta de detección, procedimiento PL/SQL, nota operativa y decisiones técnicas.
