package com.checkout.payment.gateway.util;

import com.checkout.payment.gateway.dto.BankPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentRequest;

public class Mapper {

  public static BankPaymentRequest bankPaymentRequestFrom(PostPaymentRequest request) {
    BankPaymentRequest bankPaymentRequest = new BankPaymentRequest();
    bankPaymentRequest.setAmount(request.getAmount());
    bankPaymentRequest.setCurrency(request.getCurrency());
    bankPaymentRequest.setCardNumber(request.getCardNumber());
    bankPaymentRequest.setCvv(request.getCvv());
    bankPaymentRequest.setExpiryDate(request.getExpiryDate());
    return bankPaymentRequest;
  }

}
