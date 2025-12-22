package com.example.conductor.payment.service;

import com.example.conductor.payment.domain.Payment;
import com.example.conductor.payment.domain.PaymentAttempt;
import com.example.conductor.payment.domain.PaymentEvent;
import com.example.conductor.payment.dto.AuthorizePaymentRequest;
import com.example.conductor.payment.dto.BankTransferConfirmRequest;
import com.example.conductor.payment.dto.PaymentResponse;
import com.example.conductor.payment.dto.RefundRequest;
import com.example.conductor.payment.repository.PaymentAttemptRepository;
import com.example.conductor.payment.repository.PaymentEventRepository;
import com.example.conductor.payment.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentEventRepository paymentEventRepository;

    public PaymentService(
        PaymentRepository paymentRepository,
        PaymentAttemptRepository paymentAttemptRepository,
        PaymentEventRepository paymentEventRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.paymentEventRepository = paymentEventRepository;
    }

    @Transactional
    public PaymentResponse authorize(AuthorizePaymentRequest request) {
        Payment payment = paymentRepository.findByOrderNo(request.orderNo())
            .orElseGet(Payment::new);

        payment.setOrderNo(request.orderNo());
        payment.setAmount(request.amount());
        payment.setCurrency(request.currency());
        payment.setMethod(request.method());
        payment.setFailRate(request.failRate());
        payment.setDelayMs(request.delayMs());
        payment.setStatus("REQUESTED");
        Payment saved = paymentRepository.save(payment);

        int attemptNo = (int) paymentAttemptRepository.countByPaymentId(saved.getId()) + 1;
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setPayment(saved);
        attempt.setAttemptNo(attemptNo);

        applyDelay(saved.getDelayMs());

        if (shouldFail(saved.getFailRate())) {
            attempt.setStatus("FAILED");
            attempt.setErrorCode("PAYMENT_FAILED");
            attempt.setErrorMessage("Simulated failure");
            paymentAttemptRepository.save(attempt);
            saved.setStatus("FAILED");
            paymentRepository.save(saved);
            return toResponse(saved, attemptNo);
        }

        attempt.setStatus("SUCCESS");
        paymentAttemptRepository.save(attempt);
        saved.setStatus("AUTHORIZED");
        paymentRepository.save(saved);
        return toResponse(saved, attemptNo);
    }

    @Transactional
    public PaymentResponse capture(String orderNo) {
        Payment payment = findPayment(orderNo);
        payment.setStatus("CAPTURED");
        Payment saved = paymentRepository.save(payment);
        int attempts = (int) paymentAttemptRepository.countByPaymentId(saved.getId());
        return toResponse(saved, attempts);
    }

    @Transactional
    public PaymentResponse refund(String orderNo, RefundRequest request) {
        Payment payment = findPayment(orderNo);
        payment.setStatus("REFUNDED");
        Payment saved = paymentRepository.save(payment);
        int attempts = (int) paymentAttemptRepository.countByPaymentId(saved.getId());
        return toResponse(saved, attempts);
    }

    @Transactional
    public PaymentResponse confirmBankTransfer(String orderNo, BankTransferConfirmRequest request) {
        Payment payment = findPayment(orderNo);
        PaymentEvent event = new PaymentEvent();
        event.setPayment(payment);
        event.setEventType("BANK_DEPOSIT_CONFIRMED");
        event.setPayload(request.payload());
        paymentEventRepository.save(event);
        payment.setStatus("AUTHORIZED");
        Payment saved = paymentRepository.save(payment);
        int attempts = (int) paymentAttemptRepository.countByPaymentId(saved.getId());
        return toResponse(saved, attempts);
    }

    public Optional<PaymentResponse> getPayment(String orderNo) {
        return paymentRepository.findByOrderNo(orderNo)
            .map(payment -> toResponse(payment, (int) paymentAttemptRepository.countByPaymentId(payment.getId())));
    }

    private Payment findPayment(String orderNo) {
        return paymentRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new IllegalArgumentException("payment not found: " + orderNo));
    }

    private boolean shouldFail(BigDecimal failRate) {
        if (failRate == null) {
            return false;
        }
        double rate = failRate.doubleValue();
        if (rate <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    private void applyDelay(Integer delayMs) {
        if (delayMs == null || delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private PaymentResponse toResponse(Payment payment, int attempts) {
        return new PaymentResponse(
            payment.getOrderNo(),
            payment.getStatus(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getMethod(),
            payment.getFailRate(),
            payment.getDelayMs(),
            attempts,
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
