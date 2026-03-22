package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.dto.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private BankSimulatorClient bankClient;

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  @Test
  void givenPaymentExist_whenGetPaymentById_thenReturnsPayment() {
    // Given
    UUID paymentId = UUID.randomUUID();
    PostPaymentResponse postResponse = validResponse(paymentId);

    when(paymentsRepository.findById(paymentId)).thenReturn(Optional.of(postResponse));

    // When
    GetPaymentResponse response = paymentGatewayService.getPaymentById(paymentId);

    // Then
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo("4241");


  }

  @Test
  void givenPaymentNotExist_whenGetPaymentById_thenReturnsNotFound() {
    // Given
    UUID paymentId = UUID.randomUUID();

    // When / Then
    assertThatThrownBy(() -> paymentGatewayService.getPaymentById(paymentId))
        .isInstanceOf(PaymentNotFoundException.class)
        .hasMessageContaining("Payment not found with id: " + paymentId);


  }

  @Test
  void givenValidRequest_whenProcessPayment_thenAuthorizePaymentAndSave() {
    // Given
    String idempotencyKey = "request_id";
    PostPaymentRequest request = validRequest();
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("abc123");
    when(bankClient.authorise(any())).thenReturn(bankResponse);

    // When
    PostPaymentResponse response = paymentGatewayService.processPayment(request, idempotencyKey);

    // Then
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getCardNumberLastFour()).isEqualTo("4241");
    verify(paymentsRepository).save(any());

  }

  @Test
  void givenExpiredCard_whenProcessPayment_thenThrowsEventProcessingException() {
    // Given
    String idempotencyKey = "request_id";
    PostPaymentRequest request = validRequest();
    request.setExpiryYear(2020); // past year

    // When / Then
    assertThatThrownBy(() -> paymentGatewayService.processPayment(request, idempotencyKey))
        .isInstanceOf(EventProcessingException.class)
        .hasMessageContaining("Expiry date must be in the future");
  }

  @Test
  void givenBankUnavailable_whenProcessPayment_thenThrowsBankUnavailableException() {
    // Given
    String idempotencyKey = "request_id";
    PostPaymentRequest request = validRequest();
    when(bankClient.authorise(any())).thenThrow(new BankUnavailableException("Bank down", null));

    // When / Then
    assertThatThrownBy(() -> paymentGatewayService.processPayment(request, idempotencyKey))
        .isInstanceOf(BankUnavailableException.class);
  }


  private PostPaymentRequest validRequest(){
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4244242 4242 4241");
    request.setExpiryMonth(4);
    request.setExpiryYear(2026);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("122");
    return request;
  }

  private PostPaymentResponse validResponse(UUID paymentId){
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(paymentId);
    response.setStatus(PaymentStatus.AUTHORIZED);
    response.setCardNumberLastFour("4241");
    response.setExpiryMonth(4);
    response.setExpiryYear(2026);
    response.setCurrency("GBP");
    response.setAmount(100);
    return response;
  }

}

