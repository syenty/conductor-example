package com.example.conductor.order.repository;

import com.example.conductor.order.domain.Approval;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    Optional<Approval> findByOrderNo(String orderNo);
}
