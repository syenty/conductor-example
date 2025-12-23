package com.example.conductor.payment.controller;

import com.example.conductor.payment.dto.AuthorizePaymentRequest;
import com.example.conductor.payment.dto.BankTransferConfirmRequest;
import com.example.conductor.payment.dto.PaymentResponse;
import com.example.conductor.payment.dto.RefundRequest;
import com.example.conductor.payment.service.PaymentService;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/authorize")
    public PaymentResponse authorize(@RequestBody AuthorizePaymentRequest request) {
        return paymentService.authorize(request);
    }

    @PostMapping("/{orderNo}/capture")
    public PaymentResponse capture(@PathVariable String orderNo) {
        return paymentService.capture(orderNo);
    }

    @PostMapping("/{orderNo}/refund")
    public PaymentResponse refund(@PathVariable String orderNo, @RequestBody(required = false) RefundRequest request) {
        return paymentService.refund(orderNo, request == null ? new RefundRequest(null) : request);
    }

    @PostMapping("/{orderNo}/bank-transfer/confirm")
    public PaymentResponse confirmBankTransfer(
        @PathVariable String orderNo,
        @RequestBody(required = false) BankTransferConfirmRequest request
    ) {
        return paymentService.confirmBankTransfer(
            orderNo,
            request == null ? new BankTransferConfirmRequest(null) : request
        );
    }

    @GetMapping("/{orderNo}")
    public Optional<PaymentResponse> get(@PathVariable String orderNo) {
        return paymentService.getPayment(orderNo);
    }
}
