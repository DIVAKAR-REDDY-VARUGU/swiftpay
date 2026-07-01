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

// tests just the http/web layer (the service is mocked). the main point here is the error handling -
// bad input should come back as a clean 400, not a 500. this is what i fixed after the swagger test blew up.
@WebMvcTest(PaymentController.class)
class PaymentControllerWebTest {

    @Autowired MockMvc mvc;
    @MockBean PaymentService service;

    @Test
    void validPaymentReturns202() throws Exception {
        // a proper request should give 202 accepted (we reply straight away, the ledger settles in the background)
        when(service.initiate(any())).thenReturn(new PaymentResponse("P1", TransactionStatus.PENDING, "ok"));

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":1,\"receiverId\":2,\"amount\":100,\"currency\":\"INR\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingRequiredFieldsReturn400() throws Exception {
        // only senderId sent, the rest are missing. the @Valid checks should fail and give a 400.
        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedBodyReturns400Not500() throws Exception {
        // senderId sent as text instead of a number, so jackson cant even parse the json.
        // before the fix this fell through to the catch-all and returned 500. now it should be a 400.
        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest());
    }
}
