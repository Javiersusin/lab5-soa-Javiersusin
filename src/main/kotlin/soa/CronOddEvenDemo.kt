@file:Suppress("WildcardImport", "NoWildcardImports", "MagicNumber")

package soa

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.integration.annotation.Gateway
import org.springframework.integration.annotation.MessagingGateway
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.Pollers
import org.springframework.integration.dsl.PublishSubscribeChannelSpec
import org.springframework.integration.dsl.integrationFlow
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("soa.CronOddEvenDemo")

/**
 * Spring Integration configuration for demonstrating Enterprise Integration Patterns.
 * This application implements a message flow that processes numbers and routes them
 * based on whether they are even or odd.
 *
 * **Your Task**: Analyze this configuration, create an EIP diagram, and compare it
 * with the target diagram to identify and fix any issues.
 */
@SpringBootApplication
@EnableIntegration
@EnableScheduling
class IntegrationApplication(
    private val sendNumber: SendNumber,
) {
    /**
     * Creates an atomic integer source that generates sequential numbers.
     */
    @Bean
    fun integerSource(): AtomicInteger = AtomicInteger()

    /**
     * Defines a publish-subscribe channel for odd numbers.
     * Multiple subscribers can receive messages from this channel.
     */
    @Bean
    fun oddChannel(): PublishSubscribeChannelSpec<*> = MessageChannels.publishSubscribe()

    /**
     * Main integration flow that polls the integer source and routes messages.
     * Polls every 100ms and routes based on even/odd logic.
     */
    @Bean
    fun myFlow(integerSource: AtomicInteger): IntegrationFlow =
        integrationFlow("NumberChannel") {
            // canal conjunto para pares e impares
            transform { num: Int ->
                logger.info("📥 Source generated number: {}", num)
                num
            }
            route { p: Int ->
                val channel = if (p % 2 == 0) "evenChannel" else "oddChannel" // aqui se decide
                // si el número es par o impar, y le mandamos a un canal u otro
                logger.info("🔀 Router: {} → {}", p, channel)
                channel
            }
        }

    @Bean
    fun myFlowNum(integerSource: AtomicInteger): IntegrationFlow =
        integrationFlow(
            source = { integerSource.getAndIncrement() }, // saca de aquí los números, le
            // llama cada 100ms el poller
            options = { poller(Pollers.fixedRate(100)) },
        ) {
            transform { num: Int ->
                logger.info("📥 Source generated number: {}", num)
                num
            }
            route { p: Int ->
                val channel = "NumberChannel"
                logger.info("🔀 Router: {} → {}", p, channel)
                channel
            }
        }

    /**
     * Integration flow for processing even numbers.
     * Transforms integers to strings and logs the result.
     */
    @Bean
    fun evenFlow(): IntegrationFlow =
        integrationFlow("evenChannel") {
            transform { obj: Int ->
                logger.info("  ⚙️  Even Transformer: {} → 'Number {}'", obj, obj)
                "Number $obj"
            }
            handle { p ->
                logger.info("  ✅ Even Handler: Processed [{}]", p.payload) // vemos como el flow para pares,
                // lo que hace es transformar el número en un string y luego lo loggea
            }
        }

    /**
     * Integration flow for processing odd numbers.
     * Applies a filter before transformation and logging.
     * Note: Examine the filter condition carefully.
     */
    @Bean
    fun oddFlow(): IntegrationFlow =
        integrationFlow("oddChannel") {
            // filtro eliminado, ya que no es posible que lleguen numeros pares a este flow
            transform { obj: Int ->
                logger.info("  ⚙️  Odd Transformer: {} → 'Number {}'", obj, obj)
                "Number $obj"
            }
            handle { p ->
                // Primer manejador del flow de impares
                logger.info("  ✅ Odd Handler: Processed [{}]", p.payload) // vemos como el flow para impares,
                // primero filtra (y descarta todos los números, porque el filtro que dejo comentado está mal hecho),
                // luego transforma el número en un string y luego lo loggea
            }
        }

    /**
     * Scheduled task that periodically sends negative random numbers via the gateway.
     */
    @Scheduled(fixedRate = 1000)
    fun sendNumber() {
        val number = -Random.nextInt(100)
        logger.info("🚀 Gateway injecting: {}", number)
        sendNumber.sendNumber(number) // inyecta en el canal evenChanel
    }
}

/**
 * Service component that processes messages from the odd channel.
 * Uses @ServiceActivator annotation to connect to the integration flow.
 */
@Component
class SomeService {
    @ServiceActivator(inputChannel = "oddChannel") // conectamos al canal de salida del flow de impares
    fun handle(p: Any) { // segundo manejador del flow de impares (el service odd)
        logger.info("  🔧 Service Activator: Received [{}] (type: {})", p, p.javaClass.simpleName)
    }
}

/**
 * Messaging Gateway for sending numbers into the integration flow.
 * This provides a simple interface to inject messages into the system.
 * Note: Check which channel this gateway sends messages to.
 */
@MessagingGateway
interface SendNumber {
    @Gateway(requestChannel = "NumberChannel")
    fun sendNumber(number: Int)
}

fun main() {
    runApplication<IntegrationApplication>()
}
