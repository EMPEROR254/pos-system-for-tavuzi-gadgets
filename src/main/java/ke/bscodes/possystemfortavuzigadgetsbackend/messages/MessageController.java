package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import jakarta.validation.Valid;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.PageResponse;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignPreview;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.CampaignResult;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.MessageView;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/messages: the SMS log and promotions (owner and manager - SEND_PROMOTIONS). */
@RestController
@RequestMapping("/api/messages")
@PreAuthorize("hasAuthority('SEND_PROMOTIONS')")
public class MessageController {

  private final MessageService service;

  public MessageController(MessageService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<MessageView> list(
      @RequestParam(required = false) SmsMessage.Kind kind,
      @RequestParam(required = false) SmsMessage.Status status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    return PageResponse.of(
        service.list(
            kind,
            status,
            PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
                    .and(Sort.by(Sort.Direction.DESC, "id")))));
  }

  @PostMapping("/campaigns/preview")
  public CampaignPreview preview(@Valid @RequestBody CampaignRequest request) {
    return service.preview(request);
  }

  @PostMapping("/campaigns")
  @ResponseStatus(HttpStatus.CREATED)
  public CampaignResult send(
      @AuthenticationPrincipal AuthUser me, @Valid @RequestBody CampaignRequest request) {
    return service.sendCampaign(request, me.id());
  }
}
