package uz.backenddoctor.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stands in for a real third-party payment gateway (Stripe, a local psp,
 * whatever). Simulates realistic network latency with a sleep so we can
 * demonstrate what happens when this call is made while a DB transaction
 * (and its connection) is held open -- see Issue #004.
 */
@Component
@Slf4j
public class PaymentGatewayClient {

    private static final int SIMULATED_LATENCY_MS = 300;

    public boolean charge(BigDecimal amount) {
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while charging payment", e);
        }
        log.info("Charged {} via payment gateway", amount);
        return true;
    }
}
