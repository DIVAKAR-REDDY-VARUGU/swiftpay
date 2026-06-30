package com.swiftpay.gateway.payment.controller;

import com.swiftpay.gateway.entity.TransactionStatus;
import com.swiftpay.gateway.payment.dto.PaymentResponse;
import com.swiftpay.gateway.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the HTTP layer + error handling (validation and malformed bodies map to 400, not 500).
@WebMvcTest(PaymentController.class)
class PaymentControllerWebTest {

    @Autowired MockMvc mvc;
    @MockBean PaymentService service;

    @Test
    void validPaymentReturns202() throws Exception {
        when(service.initiate(any())).thenReturn(new PaymentResponse("P1", TransactionStatus.PENDING, "ok"));

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":1,\"receiverId\":2,\"amount\":100,\"currency\":\"INR\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingRequiredFieldsReturn400() throws Exception {
        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":1}"))   // missing receiver / amount / currency -> validation fails
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedBodyReturns400NotS500() throws Exception {
        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":\"not-a-number\"}"))   // unparseable -> HttpMessageNotReadableException
                .andExpect(status().isBadRequest());
    }
}
