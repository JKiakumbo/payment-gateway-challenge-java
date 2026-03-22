package com.checkout.payment.gateway.controller;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  PaymentsRepository paymentsRepository;

  @BeforeEach
  void setUp() {
    // Clear the in‑memory repository before each test
    paymentsRepository.clear();
  }

  @Test
  void givenPaymentWithIdExist_whenGetPaymentById_ThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

    paymentsRepository.save(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payments/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void givenPaymentWithIdDoesNotExist_whenGetPaymentById_Then404IsReturned() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(MockMvcRequestBuilders.get("/payments/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Payment not found with id: " +id));
  }


  @Test
  void givenValidRequest_whenProcessPayment_thenReturnsAccepted() throws Exception {
    // Card number ending with 7 (odd) → bank simulator returns authorized
    String requestJson = """
                {
                    "card_number": "2222405343248877",
                    "expiry_month": 4,
                    "expiry_year": 2027,
                    "currency": "GBP",
                    "amount": 100,
                    "cvv": 123
                }
                """;

    mvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.expiryMonth").value(4))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));

  }

  @Test
  void givenBankUnavailable_whenProcessPayment_thenReturnsServiceUnavailable() throws Exception {
    // Card number too short → validation fails, bank is not called
    String requestJson = """
                {
                    "card_number": "2222405343248870",
                    "expiry_month": 4,
                    "expiry_year": 2027,
                    "currency": "GBP",
                    "amount": 100,
                    "cvv": 123
                }
                """;

    mvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value(
            "Acquiring bank is temporarily unavailable"));
  }

  @Test
  void givenInvalidCardNumber_whenProcessPayment_thenReturnsBadRequest() throws Exception {
    // Card number too short → validation fails, bank is not called
    String requestJson = """
                {
                    "card_number": "123",
                    "expiry_month": 4,
                    "expiry_year": 2025,
                    "currency": "GBP",
                    "amount": 100,
                    "cvv": 123
                }
                """;

    mvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            "Card number must be 14–19 digits and contain only numeric characters"));
  }

}
