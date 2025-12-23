package com.example.conductor.payment.repository;

import com.example.conductor.payment.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
}
