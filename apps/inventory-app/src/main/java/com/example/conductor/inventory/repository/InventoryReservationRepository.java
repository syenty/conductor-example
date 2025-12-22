package com.example.conductor.inventory.repository;

import com.example.conductor.inventory.domain.InventoryReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderNo(String orderNo);
}
