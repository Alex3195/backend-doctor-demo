package uz.backenddoctor.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.backenddoctor.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
