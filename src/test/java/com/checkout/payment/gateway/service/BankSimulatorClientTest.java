package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.dto.BankPaymentRequest;
import com.checkout.payment.gateway.dto.BankPaymentResponse;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankSimulatorClientTest {

  @Mock
  private RestTemplate restTemplate;
  @Mock
  RestTemplateBuilder builder;

  private BankSimulatorClient bankSimulatorClient;
  private PostPaymentRequest validRequest;
  private final String baseUrl = "http://localhost:8080/payments";

  @BeforeEach
  void setUp() {
    when(builder.build()).thenReturn(restTemplate);
    bankSimulatorClient = new BankSimulatorClient(builder);

    validRequest = new PostPaymentRequest();
    validRequest.setCardNumber("2222405343248877");
    validRequest.setExpiryMonth(4);
    validRequest.setExpiryYear(2025);
    validRequest.setCurrency("GBP");
    validRequest.setAmount(100);
    validRequest.setCvv("123");
  }

  @Test
  void givenValidResponse_whenAuthoriseIsCalled_thenReturnsBankPaymentResponse() {
    // Given
    BankPaymentResponse expectedResponse = new BankPaymentResponse();
    expectedResponse.setAuthorized(true);
    expectedResponse.setAuthorizationCode("abc123");

    when(restTemplate.postForObject(eq(baseUrl), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenReturn(expectedResponse);

    // When
    BankPaymentResponse actualResponse = bankSimulatorClient.authorise(validRequest);

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    ArgumentCaptor<BankPaymentRequest> requestCaptor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(restTemplate).postForObject(eq(baseUrl), requestCaptor.capture(), eq(BankPaymentResponse.class));

    BankPaymentRequest captured = requestCaptor.getValue();
    assertThat(captured.getCardNumber()).isEqualTo("2222405343248877");
    assertThat(captured.getExpiryDate()).isEqualTo("4/2025");
    assertThat(captured.getCurrency()).isEqualTo("GBP");
    assertThat(captured.getAmount()).isEqualTo(100);
    assertThat(captured.getCvv()).isEqualTo("123");
  }

  @Test
  void givenBankUnavailable_whenAuthoriseIsCalled_thenThrowBankUnavailableException() {
    // Given
    HttpStatusCodeException exception = mock(HttpStatusCodeException.class);
    when(exception.getStatusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE);
    when(restTemplate.postForObject(eq(baseUrl), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(exception);

    // When / Then
    assertThatThrownBy(() -> bankSimulatorClient.authorise(validRequest))
        .isInstanceOf(BankUnavailableException.class)
        .hasMessageContaining("Acquiring bank is currently unavailable")
        .hasCause(exception);
  }

  @Test
  void givenBadRequest_whenAuthoriseIsCalled_thenThrowHttpStatusCodeException() {
    // Given
    HttpStatusCodeException exception = mock(HttpStatusCodeException.class);
    when(exception.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
    when(restTemplate.postForObject(eq(baseUrl), any(BankPaymentRequest.class), eq(BankPaymentResponse.class)))
        .thenThrow(exception);

    // When / Then
    assertThatThrownBy(() -> bankSimulatorClient.authorise(validRequest))
        .isSameAs(exception);
  }
}
