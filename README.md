# Conciliación segura de pagos

PagoClaro procesa pagos para comercios. Después de una degradación del proveedor, algunos pagos quedaron con el estado desalineado o con débitos duplicados. Reintentar la operación a ciegas puede agravar el incidente.

Este repositorio resuelve la conciliación de soporte N3 para un pago identificado por su referencia externa. Corrige solo cuando la evidencia alcanza. Si la evidencia es contradictoria o insuficiente, conserva los datos y deja el caso para revisión manual. Repetir la misma conciliación no genera otro movimiento ni otra auditoría.

## Estado de esta rama

La política de conciliación y sus pruebas. No hay API ni base de datos.

`ReconciliationPolicy` recibe el pago, los eventos, los movimientos y las auditorías ya registradas. Devuelve el resultado, el estado final y los movimientos o la auditoría que todavía no se escriben. No abre conexiones y no modifica esa evidencia.

| Documento | Contenido |
| --- | --- |
| [docs/diagnostico.md](docs/diagnostico.md) | Hechos del incidente, hipótesis, validaciones y corrección propuesta |
| [docs/casos.md](docs/casos.md) | Reglas, supuestos y resultado esperado de los cuatro pagos de muestra |

## Pruebas

Hace falta Java 17 y Maven.

```bash
mvn test
```

## Qué viene después

1. Operación de soporte en Spring Boot, con bloqueo del pago y prueba de repetición.
2. Consulta de detección, procedimiento PL/SQL, nota operativa y decisiones técnicas.

La forma de ejecutar la operación se documenta cuando esa rama arranque.
