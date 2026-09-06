package uz.backenddoctor.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.backenddoctor.customer.entity.Customer;
import uz.backenddoctor.customer.repository.CustomerRepository;
import uz.backenddoctor.order.dto.CreateOrderRequest;
import uz.backenddoctor.order.entity.Order;
import uz.backenddoctor.order.entity.OrderItem;
import uz.backenddoctor.order.entity.OrderStatus;
import uz.backenddoctor.order.repository.OrderItemRepository;
import uz.backenddoctor.order.repository.OrderRepository;
import uz.backenddoctor.payment.client.PaymentGatewayClient;
import uz.backenddoctor.payment.entity.Payment;
import uz.backenddoctor.payment.repository.PaymentRepository;
import uz.backenddoctor.product.entity.Product;
import uz.backenddoctor.product.repository.ProductRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderCreationService {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient paymentGatewayClient;

    /**
     * ISSUE #004 -- LONG-HELD TRANSACTION AROUND AN EXTERNAL CALL
     * (intentional, this is the "before" state).
     *
     * @Transactional wraps this entire method, so the DB connection
     * checked out of the (intentionally small, 20-connection) Hikari
     * pool is held for the whole thing -- including the ~300ms call to
     * paymentGatewayClient.charge(), which is a network call to a third
     * party that has nothing to do with our database.
     *
     * Under concurrent load this ties up connections for ~300ms per
     * request instead of the few ms the DB writes actually need, so the
     * pool exhausts far sooner than it should.
     */
    @Transactional
    public Order createOrderLongTransaction(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown customer: " + request.customerId()));
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + request.productId()));

        if (product.getStock() < request.quantity()) {
            throw new IllegalStateException("Not enough stock for product " + product.getId());
        }
        product.setStock(product.getStock() - request.quantity());
        productRepository.save(product);

        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(request.quantity()));

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(total);
        orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(request.quantity());
        item.setUnitPrice(product.getPrice());
        orderItemRepository.save(item);

        // <-- the DB connection for this transaction sits idle-but-checked-out
        //     for ~300ms here, blocking anyone else who needs a connection.
        boolean charged = paymentGatewayClient.charge(total);

        order.setStatus(charged ? OrderStatus.PAID : OrderStatus.CANCELLED);
        orderRepository.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(total);
        payment.setStatus(charged ? "COMPLETED" : "FAILED");
        paymentRepository.save(payment);

        return order;
    }
}
