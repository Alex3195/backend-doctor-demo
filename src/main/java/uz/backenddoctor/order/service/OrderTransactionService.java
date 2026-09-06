package uz.backenddoctor.order.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.backenddoctor.customer.entity.Customer;
import uz.backenddoctor.customer.repository.CustomerRepository;
import uz.backenddoctor.order.dto.CreateOrderRequest;
import uz.backenddoctor.order.entity.Order;
import uz.backenddoctor.order.entity.OrderItem;
import uz.backenddoctor.order.entity.OrderStatus;
import uz.backenddoctor.order.exception.InsufficientStockException;
import uz.backenddoctor.order.repository.OrderItemRepository;
import uz.backenddoctor.order.repository.OrderRepository;
import uz.backenddoctor.payment.entity.Payment;
import uz.backenddoctor.payment.repository.PaymentRepository;
import uz.backenddoctor.product.entity.Product;
import uz.backenddoctor.product.repository.ProductRepository;

import java.math.BigDecimal;

/**
 * Fix #004 -- the two DB-only steps of order creation, each its own short
 * transaction. Deliberately a SEPARATE bean from OrderCreationService: if
 * these methods lived on the same class as the orchestrating method and
 * were called via "this.", Spring's proxy-based @Transactional would be
 * silently bypassed (self-invocation doesn't go through the proxy).
 */
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public Order reserveOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown customer: " + request.customerId()));
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + request.productId()));

        // Fix #005 -- atomic, conditional decrement instead of
        // read-check-write. See ProductRepository.decrementStockIfAvailable().
        int updated = productRepository.decrementStockIfAvailable(product.getId(), request.quantity());
        if (updated == 0) {
            throw new InsufficientStockException("Not enough stock for product " + product.getId());
        }

        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(request.quantity()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(total);
        orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(request.quantity());
        item.setUnitPrice(product.getPrice());
        orderItemRepository.save(item);

        return order;
    }

    @Transactional
    public Order finalizeOrder(Long orderId, boolean charged) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order: " + orderId));

        order.setStatus(charged ? OrderStatus.PAID : OrderStatus.CANCELLED);
        orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(charged ? "COMPLETED" : "FAILED");
        paymentRepository.save(payment);

        // With open-in-view off, the Hibernate session closes the moment
        // this method returns -- so order.customer (LAZY) must be
        // initialized NOW, while the session is still open, or the
        // controller's later order.getCustomer().getFullName() throws
        // LazyInitializationException instead of just working "by luck"
        // the way it would with OSIV on.
        Hibernate.initialize(order.getCustomer());

        return order;
    }
}
