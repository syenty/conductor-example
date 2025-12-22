package com.example.conductor.order.service;

import com.example.conductor.order.domain.Approval;
import com.example.conductor.order.domain.Order;
import com.example.conductor.order.domain.OrderEvent;
import com.example.conductor.order.domain.OrderItem;
import com.example.conductor.order.dto.ApprovalDecisionRequest;
import com.example.conductor.order.dto.ApprovalRequest;
import com.example.conductor.order.dto.ApprovalResponse;
import com.example.conductor.order.dto.CreateOrderRequest;
import com.example.conductor.order.dto.OrderItemRequest;
import com.example.conductor.order.dto.OrderItemResponse;
import com.example.conductor.order.dto.OrderResponse;
import com.example.conductor.order.repository.ApprovalRepository;
import com.example.conductor.order.repository.OrderEventRepository;
import com.example.conductor.order.repository.OrderItemRepository;
import com.example.conductor.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventRepository orderEventRepository;
    private final ApprovalRepository approvalRepository;

    public OrderService(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderEventRepository orderEventRepository,
        ApprovalRepository approvalRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderEventRepository = orderEventRepository;
        this.approvalRepository = approvalRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        orderRepository.findByOrderNo(request.orderNo()).ifPresent(order -> {
            throw new IllegalArgumentException("order already exists: " + request.orderNo());
        });

        Order order = new Order();
        order.setOrderNo(request.orderNo());
        order.setStatus("CREATED");
        order.setTotalAmount(request.totalAmount());
        order.setCurrency(request.currency());
        order.setCustomerId(request.customerId());
        order.setPaymentMethod(request.paymentMethod());
        Order saved = orderRepository.save(order);

        List<OrderItemRequest> itemRequests = request.items() == null ? List.of() : request.items();
        List<OrderItemResponse> items = itemRequests.stream()
            .map(item -> toOrderItem(saved, item))
            .map(orderItemRepository::save)
            .map(this::toItemResponse)
            .toList();

        recordEvent(saved, "CREATED", null);
        return toOrderResponse(saved, items);
    }

    @Transactional
    public OrderResponse updateStatus(String orderNo, String status) {
        Order order = findOrder(orderNo);
        order.setStatus(status);
        Order saved = orderRepository.save(order);
        recordEvent(saved, "STATUS_" + status, null);
        List<OrderItemResponse> items = orderItemRepository.findByOrder_Id(saved.getId())
            .stream()
            .map(this::toItemResponse)
            .toList();
        return toOrderResponse(saved, items);
    }

    @Transactional
    public OrderResponse confirmOrder(String orderNo) {
        return updateStatus(orderNo, "CONFIRMED");
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNo) {
        return updateStatus(orderNo, "CANCELED");
    }

    @Transactional
    public ApprovalResponse requestApproval(ApprovalRequest request) {
        Approval approval = approvalRepository.findByOrderNo(request.orderNo())
            .orElseGet(Approval::new);
        approval.setOrderNo(request.orderNo());
        approval.setStatus("PENDING");
        approval.setRequestedBy(request.requestedBy());
        approval.setComment(request.comment());
        Approval saved = approvalRepository.save(approval);
        return toApprovalResponse(saved);
    }

    @Transactional
    public ApprovalResponse approve(String orderNo, ApprovalDecisionRequest request) {
        return updateApproval(orderNo, "APPROVED", request.comment());
    }

    @Transactional
    public ApprovalResponse reject(String orderNo, ApprovalDecisionRequest request) {
        return updateApproval(orderNo, "REJECTED", request.comment());
    }

    public OrderResponse getOrder(String orderNo) {
        Order order = findOrder(orderNo);
        List<OrderItemResponse> items = orderItemRepository.findByOrder_Id(order.getId())
            .stream()
            .map(this::toItemResponse)
            .toList();
        return toOrderResponse(order, items);
    }

    public Optional<ApprovalResponse> getApproval(String orderNo) {
        return approvalRepository.findByOrderNo(orderNo).map(this::toApprovalResponse);
    }

    private Order findOrder(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderNo));
    }

    private OrderItem toOrderItem(Order order, OrderItemRequest item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProductId(item.productId());
        orderItem.setQuantity(item.quantity());
        orderItem.setUnitPrice(item.unitPrice());
        orderItem.setStatus("CREATED");
        return orderItem;
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
            item.getId(),
            item.getProductId(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getStatus()
        );
    }

    private OrderResponse toOrderResponse(Order order, List<OrderItemResponse> items) {
        return new OrderResponse(
            order.getOrderNo(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCurrency(),
            order.getCustomerId(),
            order.getPaymentMethod(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            items
        );
    }

    private void recordEvent(Order order, String type, String detail) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setType(type);
        event.setDetail(detail);
        orderEventRepository.save(event);
    }

    private ApprovalResponse updateApproval(String orderNo, String status, String comment) {
        Approval approval = approvalRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new IllegalArgumentException("approval not found: " + orderNo));
        approval.setStatus(status);
        if (comment != null && !comment.isBlank()) {
            approval.setComment(comment);
        }
        Approval saved = approvalRepository.save(approval);
        return toApprovalResponse(saved);
    }

    private ApprovalResponse toApprovalResponse(Approval approval) {
        return new ApprovalResponse(
            approval.getOrderNo(),
            approval.getStatus(),
            approval.getRequestedBy(),
            approval.getComment(),
            approval.getCreatedAt(),
            approval.getUpdatedAt()
        );
    }
}
