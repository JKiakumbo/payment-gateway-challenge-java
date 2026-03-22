package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.dto.BankPaymentResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.GetPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);
  private final Map<String, PostPaymentResponse> idempotencyCache;
  private final PaymentsRepository paymentsRepository;
  private final BankSimulatorClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository, BankSimulatorClient bankClient) {
    this.idempotencyCache = new ConcurrentHashMap<>();
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
  }


  public GetPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    PostPaymentResponse paymentResponse = paymentsRepository.findById(id)
        .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
    return createGetPaymentResponseFrom(paymentResponse);
  }

  public PostPaymentResponse processPayment(PostPaymentRequest request, String idempotencyKey) {
    if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
      return idempotencyCache.get(idempotencyKey);
    }

    validateExpiryDate(request.getExpiryMonth(), request.getExpiryYear());
    BankPaymentResponse bankResponse = bankClient.authorise(request);
    PaymentStatus paymentStatus = bankResponse.isAuthorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
    PostPaymentResponse response = createPostPaymentResponse(UUID.randomUUID(), paymentStatus, request);
    paymentsRepository.save(response);

    if (idempotencyKey != null) {
      idempotencyCache.put(idempotencyKey, response);
    }

    return  response;
  }

  private void validateExpiryDate(int month, int year) {
    YearMonth expiry = YearMonth.of(year, month);
    if (expiry.isBefore(YearMonth.now())) {
      throw new EventProcessingException("Expiry date must be in the future");
    }
  }


  private PostPaymentResponse createPostPaymentResponse(UUID id, PaymentStatus paymentStatus, PostPaymentRequest request) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(id);
    response.setStatus(paymentStatus);
    response.setCardNumberLastFour(request.getCardNumber().substring(request.getCardNumber().length() - 4));
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());

    return response;
  }

  private GetPaymentResponse createGetPaymentResponseFrom(PostPaymentResponse response) {
    GetPaymentResponse getResponse = new GetPaymentResponse();
    getResponse.setId(response.getId());
    getResponse.setStatus(response.getStatus());
    getResponse.setCardNumberLastFour(response.getCardNumberLastFour());
    getResponse.setExpiryMonth(response.getExpiryMonth());
    getResponse.setExpiryYear(response.getExpiryYear());
    getResponse.setCurrency(response.getCurrency());
    getResponse.setAmount(response.getAmount());

    return getResponse;
  }

  private String getLastFourDigits(String cardNumber){
    if (cardNumber == null || cardNumber.length() > 4) {
      throw new IllegalArgumentException("Invalid card number");
    }
    return cardNumber.substring(cardNumber.length() - 4);
  }
}

