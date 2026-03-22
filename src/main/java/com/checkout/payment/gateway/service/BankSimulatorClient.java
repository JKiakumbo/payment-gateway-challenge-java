package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.dto.BankPaymentRequest;
import com.checkout.payment.gateway.dto.BankPaymentResponse;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.util.Mapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorClient {
  private final RestTemplate restTemplate;
  private final String BASE_URL = "http://localhost:8080/payments";

  public BankSimulatorClient(RestTemplateBuilder builder) {
    this.restTemplate = builder.build();
  }

  public BankPaymentResponse authorise(PostPaymentRequest request) {
    BankPaymentRequest bankPaymentRequest = Mapper.bankPaymentRequestFrom(request);
    try {
      return restTemplate.postForObject(BASE_URL, bankPaymentRequest, BankPaymentResponse.class);
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
        throw new BankUnavailableException("Acquiring bank is currently unavailable", ex);
      }
      throw ex;
    }
  }
}

