package uz.backenddoctor.order.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import uz.backenddoctor.order.service.NotificationService;

/**
 * Fix #007. Runs on a Kafka consumer thread, not the HTTP request
 * thread that published the event -- so the ~250ms notification send
 * no longer blocks the caller of POST /api/orders/fast-checkout.
 */
@Component
@RequiredArgsConstructor
public class OrderCreatedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = OrderEventProducer.TOPIC, groupId = "backend-doctor-demo")
    public void onOrderCreated(OrderCreatedEvent event) {
        notificationService.sendOrderConfirmation(event.orderId());
    }
}
