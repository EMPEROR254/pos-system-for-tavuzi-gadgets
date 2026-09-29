package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Africa's Talking calls these (form posts) with delivery reports, customers' replies and network
 * opt-outs. Africa's Talking does not sign its callbacks, so the URL carries a long secret
 * (SMS_CALLBACK_TOKEN); a wrong or missing token looks like a page that doesn't exist. We always
 * answer 200 quickly for things we can't use, or the provider keeps retrying.
 */
@RestController
@RequestMapping("/api/sms/callbacks/{token}")
public class SmsCallbackController {

  private static final Logger log = LoggerFactory.getLogger(SmsCallbackController.class);

  private final MessageService service;
  private final SmsProperties props;

  public SmsCallbackController(MessageService service, SmsProperties props) {
    this.service = service;
    this.props = props;
  }

  /** Fields: id, status (Success, Failed, Rejected...), phoneNumber, failureReason. */
  @PostMapping("/delivery")
  public ResponseEntity<Void> delivery(
      @PathVariable String token,
      @RequestParam String id,
      @RequestParam String status,
      @RequestParam(required = false) String failureReason) {
    check(token);
    if (!service.recordDelivery(id, status, failureReason)) {
      log.info("Delivery report for an unknown SMS id {}", id);
    }
    return ResponseEntity.ok().build();
  }

  /** Fields: from, to, text, date, id (plus linkId and networkCode, which we don't use). */
  @PostMapping("/inbound")
  public ResponseEntity<Void> inbound(
      @PathVariable String token,
      @RequestParam String from,
      @RequestParam(required = false) String text,
      @RequestParam(required = false) String id) {
    check(token);
    try {
      service.receiveReply(id, from, text);
    } catch (ApiException e) {
      log.info("Ignored an SMS reply from a number that is not a Kenyan mobile");
    }
    return ResponseEntity.ok().build();
  }

  /** Fields: senderId, phoneNumber - the customer opted out through the network. */
  @PostMapping("/opt-out")
  public ResponseEntity<Void> optOut(@PathVariable String token, @RequestParam String phoneNumber) {
    check(token);
    try {
      service.networkOptOut(phoneNumber);
    } catch (ApiException e) {
      log.info("Ignored an opt-out from a number that is not a Kenyan mobile");
    }
    return ResponseEntity.ok().build();
  }

  private void check(String token) {
    // Constant-time compare, so the token can't be guessed one character at a time.
    if (!props.callbacksEnabled()
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8),
            props.callbackToken().getBytes(StandardCharsets.UTF_8))) {
      throw ApiException.notFound("Not found.");
    }
  }
}
