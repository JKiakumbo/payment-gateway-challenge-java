package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.dto.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceIdempotencyTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private BankSimulatorClient bankClient;

  @InjectMocks
  private PaymentGatewayService service;

  private PostPaymentRequest validRequest;
  private final String idempotencyKey = "test-key-123";

  @BeforeEach
  void setUp() {
    validRequest = new PostPaymentRequest();
    validRequest.setCardNumber("2222405343248877");
    validRequest.setExpiryMonth(4);
    validRequest.setExpiryYear(2026);
    validRequest.setCurrency("GBP");
    validRequest.setAmount(100);
    validRequest.setCvv("123");
  }

  @Test
  void givenRequestWithNewIdempotencyKey_whenProcessesPayment_thenCacheResponse() {
    // Given
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("abc123");
    when(bankClient.authorise(any(PostPaymentRequest.class))).thenReturn(bankResponse);

    ArgumentCaptor<PostPaymentResponse> responseCaptor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    doNothing().when(paymentsRepository).save(responseCaptor.capture());

    // When
    PostPaymentResponse firstResult = service.processPayment(validRequest, idempotencyKey);

    // Then
    verify(bankClient, times(1)).authorise(any(PostPaymentRequest.class));
    verify(paymentsRepository, times(1)).save(any());

    PostPaymentResponse savedResponse = responseCaptor.getValue();
    assertThat(savedResponse).isEqualTo(firstResult);
    assertThat(savedResponse.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

    // When – second call with same key
    PostPaymentResponse secondResult = service.processPayment(validRequest, idempotencyKey);

    // Then – no second bank call, no second repository save, same response returned
    verify(bankClient, times(1)).authorise(any(PostPaymentRequest.class));
    verify(paymentsRepository, times(1)).save(any());
    assertThat(secondResult).isSameAs(firstResult); // same object from cache
  }

  @Test
  void givenRequestWithNoIdempotencyKey_whenProcessesPayment_thenDoesNotCacheResponse() {
    // Given
    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    when(bankClient.authorise(any(PostPaymentRequest.class))).thenReturn(bankResponse);

    ArgumentCaptor<PostPaymentResponse> responseCaptor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    doNothing().when(paymentsRepository).save(responseCaptor.capture());

    // When
    PostPaymentResponse result = service.processPayment(validRequest, null);

    // Then
    verify(bankClient, times(1)).authorise(any(PostPaymentRequest.class));
    verify(paymentsRepository, times(1)).save(any());

    // When – another call without key
    PostPaymentResponse secondResult = service.processPayment(validRequest, null);

    // Then – a second bank call occurs (no caching)
    verify(bankClient, times(2)).authorise(any(PostPaymentRequest.class));
    verify(paymentsRepository, times(2)).save(any());
    assertThat(secondResult).isNotSameAs(result);
  }

  @Test
  void givenRequestWithNewIdempotencyKey_whenPaymentFailsDueToValidation_thenDoesNotCacheResponse() {
    // Given – request with expiry in the past
    PostPaymentRequest invalidRequest = new PostPaymentRequest();
    invalidRequest.setCardNumber("2222405343248877");
    invalidRequest.setExpiryMonth(1);
    invalidRequest.setExpiryYear(2020); // expired
    invalidRequest.setCurrency("GBP");
    invalidRequest.setAmount(100);
    invalidRequest.setCvv("123");

    // When & Then – first call throws validation exception
    assertThatThrownBy(() -> service.processPayment(invalidRequest, idempotencyKey))
        .isInstanceOf(EventProcessingException.class)
        .hasMessageContaining("Expiry date");

    // Bank never called
    verify(bankClient, never()).authorise(any());
    verify(paymentsRepository, never()).save(any());

    // When – second call with same key, still fails because no cache
    assertThatThrownBy(() -> service.processPayment(invalidRequest, idempotencyKey))
        .isInstanceOf(EventProcessingException.class);
    verify(bankClient, never()).authorise(any());
  }

  @Test
  void givenRequestWithNewIdempotencyKey_whenBankUnavailable_thenNoCacheAndRetryWorks() {
    // Given – bank throws on first call
    when(bankClient.authorise(any(PostPaymentRequest.class)))
        .thenThrow(new BankUnavailableException("Bank down", null))
        .thenReturn(createBankResponse());

    ArgumentCaptor<PostPaymentResponse> responseCaptor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    doNothing().when(paymentsRepository).save(responseCaptor.capture());

    // When – first call fails
    assertThatThrownBy(() -> service.processPayment(validRequest, idempotencyKey))
        .isInstanceOf(BankUnavailableException.class);

    // Then – no save, no cache entry
    verify(paymentsRepository, never()).save(any());

    // When – second call with same key, bank is now available
    PostPaymentResponse result = service.processPayment(validRequest, idempotencyKey);

    // Then – second bank call made, payment saved and cached
    verify(bankClient, times(2)).authorise(any(PostPaymentRequest.class));
    verify(paymentsRepository, times(1)).save(any());

    PostPaymentResponse savedResponse = responseCaptor.getValue();
    assertThat(savedResponse).isEqualTo(result);
    assertThat(savedResponse.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

    // When – third call with same key, should return cached
    PostPaymentResponse cachedResult = service.processPayment(validRequest, idempotencyKey);
    verify(bankClient, times(2)).authorise(any(PostPaymentRequest.class)); // still 2 calls
    verify(paymentsRepository, times(1)).save(any());
    assertThat(cachedResult).isSameAs(result);
  }

  @Test
  void givenTwoRequestWithDifferentIdempotencyKeys_whenProcessPayment_thenCacheBothResponses() {
    // Given
    BankPaymentResponse bankResponse = createBankResponse();
    when(bankClient.authorise(any(PostPaymentRequest.class))).thenReturn(bankResponse);
    doNothing().when(paymentsRepository).save(any());

    // When
    PostPaymentResponse result1 = service.processPayment(validRequest, "key-1");
    PostPaymentResponse result2 = service.processPayment(validRequest, "key-2");

    // Then – two bank calls, two saves
    verify(bankClient, times(2)).authorise(any(PostPaymentRequest.class));
    verify(paymentsRepository, times(2)).save(any());
    assertThat(result1).isNotSameAs(result2);
  }

  private BankPaymentResponse createBankResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(true);
    response.setAuthorizationCode("auth123");
    return response;
  }
}