package uz.backenddoctor.order.event;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Fix #007. KafkaTemplate.send() enqueues the message and returns
 * immediately -- it does NOT wait for a consumer to process it, so
 * publishing here adds negligible latency to the caller compared to
 * calling NotificationService.sendOrderConfirmation() synchronously.
 */
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    public static final String TOPIC = "order-created";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(TOPIC, event.orderId().toString(), event);
    }
}
