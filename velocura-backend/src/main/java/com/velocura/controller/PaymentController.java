package com.velocura.controller;

import com.velocura.dto.PaymentRequest;
import com.velocura.dto.PaymentResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Value("${stripe.secret.key:${STRIPE_SECRET_KEY:}}")
    private String stripeSecretKey;

    @Value("${velocura.payment.mock-enabled:false}")
    private boolean mockPaymentEnabled;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
        }
    }

    @PostMapping("/checkout")
    public ResponseEntity<PaymentResponse> createCheckoutSession(@RequestBody PaymentRequest request) {
        if (request == null || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero.");
        }

        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            if (mockPaymentEnabled) {
                log.warn("[DEV MODE ONLY] Stripe key unconfigured; returning mock checkout session because velocura.payment.mock-enabled=true");
                PaymentResponse mockResponse = PaymentResponse.builder()
                        .sessionId("mock_session_" + System.currentTimeMillis())
                        .sessionUrl(request.getSuccessUrl())
                        .build();
                return ResponseEntity.ok(mockResponse);
            }
            log.error("Stripe payment rejected: Stripe API secret key is not configured.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway is currently unconfigured. Please contact clinic administration.");
        }

        try {
            long unitAmount = request.getAmount().multiply(new BigDecimal(100)).longValue();

            SessionCreateParams params = SessionCreateParams.builder()
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(request.getSuccessUrl())
                    .setCancelUrl(request.getCancelUrl())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("usd")
                                                    .setUnitAmount(unitAmount)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(request.getDescription() != null ? request.getDescription() : "VeloCura Medical Consultation")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session session = Session.create(params);

            PaymentResponse response = PaymentResponse.builder()
                    .sessionId(session.getId())
                    .sessionUrl(session.getUrl())
                    .build();

            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            log.error("Stripe session creation failed: {}", e.getMessage());
            if (mockPaymentEnabled) {
                log.warn("[DEV MODE ONLY] Failing over to mock payment redirect due to explicit mock-enabled flag.");
                PaymentResponse mockResponse = PaymentResponse.builder()
                        .sessionId("mock_session_" + System.currentTimeMillis())
                        .sessionUrl(request.getSuccessUrl())
                        .build();
                return ResponseEntity.ok(mockResponse);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment processing error: " + e.getMessage());
        }
    }
}
