package com.example.conductor.order.repository;

import com.example.conductor.order.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
}
