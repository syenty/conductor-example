package com.example.conductor.payment.repository;

import com.example.conductor.payment.domain.PaymentAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);
    long countByPaymentId(Long paymentId);
}
