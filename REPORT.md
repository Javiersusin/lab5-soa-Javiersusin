# Lab 5 Integration and SOA - Project Report

## 1. EIP Diagram (Before)

![Before Diagram](diagrams/before.png) 

Descripción de lo que hace el starter (con bugs):
- Un `AtomicInteger` (bean `integerSource`) genera números secuenciales cada 100 ms (poller).
- Un router decide el canal según `p % 2 == 0`:
  - Pares → `evenChannel`.
  - Impares → `oddChannel`.
- En `evenChannel` (pub/sub) el flujo transforma `Int -> "Number n"` y lo maneja (log).
- En `oddChannel` ocurre lo problemático:
  - El filtro del `oddFlow` estaba invertido: usaba `p % 2 == 0`, aceptando pares y rechazando impares (los verdaderos destinatarios de ese flow).
  - Además `oddChannel` era un `DirectChannel` con dos suscriptores:  `oddFlow` y `SomeService`, compitiendo en load-balancing. Resultado: algunos impares iban al `oddFlow` (donde podían ser rechazados) y otros iban directo a `SomeService` (sin transformar), produciendo comportamiento inconsistente.
  - El `discardChannel` existía pero no estaba conectado, así que los mensajes rechazados se perdían sin trazabilidad.
- El `MessagingGateway SendNumber` inyectaba números negativos en `evenChannel`, saltándose el router. Así, impares negativos terminaban procesándose como pares.


---

## 2. What Was Wrong

- Bug 1: Filtro de impares invertido en `oddFlow`.
  - Por qué: condición `p % 2 == 0` (acepta pares) en lugar de aceptar impares.
  - Efecto: todos los impares que entraban al `oddFlow` eran rechazados.
  - Fix aplicado: `p % 2 != 0`. Aunque después he terminado borrándolo, ya que no hacía falta el filtro.

- Bug 2: Competencia entre `oddFlow` y `SomeService` en `oddChannel` (DirectChannel).
  - Por qué: dos suscriptores al mismo canal directo → load-balancing.
  - Efecto: parte de los impares no pasaban por el filtro/transformer y llegaban al servicio sin transformar.
  - Fix aplicado: cambiar `oddChannel` a `PublishSubscribeChannel` para broadcasting.

- Bug 3: Gateway apuntando a `evenChannel`.
  - Por qué: `@Gateway(requestChannel = "evenChannel")` enviaba negativos directamente a pares.
  - Efecto: Los negativos impares se procesaban como pares.
  - Fix aplicado: enviamos todo a NumberChannel, que luego el router distribuye correctamente.

---

## 3. What You Learned

- He entendido que puedes crear las funciones en "bloques" conectados con channels intermedios, lo que facilita la lectura y el mantenimiento del código. De esta forma sabría como implementarlo ahora en el código del proyecto final de asignatura.
- He aprendido a utilizar los diferentes tipos de canales (DirectChannel, PublishSubscribeChannel) y cuándo es apropiado usar cada uno según el caso de uso (competencia vs broadcast).

## 4. AI Disclosure
**Did you use AI tools?** (ChatGPT, Copilot, Claude, etc.)

YES, ChatGPT para la redacción y estructuración del reporte (como suelo hacer, redacto todo y luego utilizo la IA para que lo edite de forma profesional). Y utilicé Copilot para que me ayudase a generar el diagrama, ya que no 
comprendía cómo hacer bien el markdown para mermaid y estaba teniendo problemas para que me generase el diagrama correctamente.

## Additional Notes
- Quiero destacar que todas las preguntas que se van haciendo en el guion, las he ido respondiendo en forma de comentarios en el propio código, esto me ayudó a entender mejor el flujo general e identificar los bugs más fácilmente.
