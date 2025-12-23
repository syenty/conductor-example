package com.example.conductor.payment.repository;

import com.example.conductor.payment.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderNo(String orderNo);
}
