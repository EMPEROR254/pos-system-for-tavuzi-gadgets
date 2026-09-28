package ke.bscodes.possystemfortavuzigadgetsbackend.mpesa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Safaricom's answer when an STK Push request is accepted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StkPushResult(
    @JsonProperty("MerchantRequestID") String merchantRequestId,
    @JsonProperty("CheckoutRequestID") String checkoutRequestId,
    @JsonProperty("ResponseCode") String responseCode,
    @JsonProperty("ResponseDescription") String responseDescription,
    @JsonProperty("CustomerMessage") String customerMessage) {}
