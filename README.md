# Conciliación segura de pagos

PagoClaro procesa pagos para comercios. Después de una degradación del proveedor, algunos pagos quedaron con el estado desalineado o con débitos duplicados. Reintentar la operación a ciegas puede agravar el incidente.

Este repositorio resuelve la conciliación de soporte N3 para un pago identificado por su referencia externa. Corrige solo cuando la evidencia alcanza. Si la evidencia es contradictoria o insuficiente, conserva los datos y deja el caso para revisión manual. Repetir la misma conciliación no genera otro movimiento ni otra auditoría.

## Estado de esta rama

Esta rama contiene el diagnóstico y la especificación. Todavía no hay código, SQL ni pruebas.

| Documento | Contenido |
| --- | --- |
| [docs/diagnostico.md](docs/diagnostico.md) | Hechos del incidente, hipótesis, validaciones y corrección propuesta |
| [docs/casos.md](docs/casos.md) | Reglas, supuestos y resultado esperado de los cuatro pagos de muestra |

## Qué viene después

1. Política de conciliación en Java, cubierta por pruebas, sin base de datos.
2. Operación de soporte en Spring Boot, con bloqueo del pago y prueba de repetición.
3. Consulta de detección, procedimiento PL/SQL, nota operativa y decisiones técnicas.

La forma de ejecutar el proyecto se documenta cuando la operación ya arranque.
