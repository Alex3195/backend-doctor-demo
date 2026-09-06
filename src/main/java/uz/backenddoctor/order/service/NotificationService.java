package uz.backenddoctor.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uz.backenddoctor.order.entity.Order;

@Service
@Slf4j
public class NotificationService {

    private static final int SIMULATED_LATENCY_MS = 250;

    /**
     * Stands in for a real notification send (email/SMS/push via a
     * third-party provider). Simulates realistic latency with a sleep.
     */
    public void sendOrderConfirmation(Order order) {
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending notification", e);
        }
        log.info("Sent order confirmation for order {}", order.getId());
    }
}
