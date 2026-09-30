# Decisiones de diseño

## Estructura

Separé la implementación en cuatro clases dentro de `com.store.inventory.impl`:

- **`InventoryServiceImpl`** — orquesta las operaciones del servicio.
- **`ProductStock`** — estado mutable de un producto: stock total, reservas activas y flag de alerta.
- **`ReservationEntry`** — estado mutable de una reserva individual.
- **`CategoryPolicy`** — centraliza las reglas de negocio por categoría (timeout de pago, límite por pedido). Cuando Marketing cree una nueva categoría, basta con agregar una entrada al `EnumMap`.

## Supuestos

- **Idempotencia en `reserve`:** como la app reenvía pedidos automáticamente ante conexiones lentas, si llega un `orderId` que ya tiene una reserva activa, se retorna la misma reserva sin consumir stock adicional.
- **Límite por pedido, no por cliente:** `FLASH_SALE` limita a 2 unidades por orden. Un cliente puede hacer múltiples órdenes de 2 unidades cada una.
- **Alerta de stock bajo:** se dispara durante `reserve` cuando las unidades disponibles caen a 5 o menos. No se repite hasta que se llame a `addStock`. Después de reabastecer, la siguiente reserva que deje el stock en 5 o menos vuelve a disparar la alerta.
- **Expiración pasiva:** no hay un hilo en segundo plano que expire reservas. La expiración se evalúa al consultar disponibilidad o al intentar reservar/confirmar. Esto simplifica el modelo sin sacrificar correctitud.

## Concurrencia

- Se usa `ConcurrentHashMap` para los mapas principales y `synchronized` sobre cada `ProductStock` individual para las operaciones compuestas (check-then-act).
- `confirm` también sincroniza sobre el `ProductStock` para garantizar consistencia entre la verificación de expiración y la confirmación.
- Este esquema es suficiente para una sola instancia en memoria. No resiste múltiples instancias ni replicas.

## Lo que dejé fuera

- **Limpieza de reservas expiradas:** las entradas expiradas permanecen en las listas internas (se ignoran en los cálculos). En memoria esto es aceptable; con base de datos se resolverá con TTL o un job periódico.
- **Persistencia:** todo vive en memoria según lo indicado. La migración a base de datos requerirá transacciones para reemplazar los `synchronized`.
- **Validación de `registerProduct` duplicado:** si se registra el mismo SKU dos veces, se ignora la segunda llamada (`putIfAbsent`). No se lanza error.
- **Lombok:** Para integrar anotaciones y reducir la cantidad de lineas  y mejor legibilidad en el código.

## Qué cambiaría antes de producción

- Reemplazar los `synchronized` por transacciones de base de datos con bloqueo optimista o pesimista.
- Agregar un scheduler para expirar reservas activamente y liberar recursos.
- Mover la emisión de alertas a un sistema de eventos (ej. Spring Events, un message broker) para desacoplar del flujo de reserva.
- Agregar logging (Sl4j) estructurado y métricas (reservas creadas, expiradas, confirmadas, alertas emitidas).
- Implementar un mecanismo de rate-limiting por cliente para categorías como `FLASH_SALE`.
