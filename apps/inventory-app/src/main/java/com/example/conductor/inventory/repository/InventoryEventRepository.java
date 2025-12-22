package com.example.conductor.inventory.repository;

import com.example.conductor.inventory.domain.InventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryEventRepository extends JpaRepository<InventoryEvent, Long> {
}
