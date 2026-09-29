package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos.MessageView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** The SMS outbox. */
public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

  /** Queued messages that may go now (not scheduled for later, not held by quiet hours). */
  @Query(
      "select m.id from SmsMessage m where m.status = 'QUEUED'"
          + " and (m.sendAfter is null or m.sendAfter <= :now) order by m.id")
  List<Long> queuedIds(Instant now, Pageable pageable);

  Optional<SmsMessage> findFirstByProviderMessageId(String providerMessageId);

  /** Per promotion: how many went out, reached the phone, failed or are still waiting. */
  @Query(
      """
      select new ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos$CampaignView(
          c.id, c.message, c.audience, c.createdAt, c.sendAt, c.recipients,
          sum(case when m.status = 'SENT' then 1 else 0 end),
          sum(case when m.deliveryStatus = 'Success' then 1 else 0 end),
          sum(case when m.status = 'FAILED' or m.deliveryStatus in ('Failed', 'Rejected', 'Expired',
              'AbsentSubscriber') then 1 else 0 end),
          sum(case when m.status = 'QUEUED' then 1 else 0 end))
      from SmsCampaign c, SmsMessage m
      where m.campaignId = c.id
      group by c.id, c.message, c.audience, c.createdAt, c.sendAt, c.recipients
      order by c.createdAt desc
      """)
  List<MessageDtos.CampaignView> campaigns(Pageable pageable);

  @Query(
      value =
          """
          select new ke.bscodes.possystemfortavuzigadgetsbackend.messages.MessageDtos$MessageView(
              m.id, m.phone, m.body, cast(m.kind as string), cast(m.status as string), m.attempts,
              m.error, m.createdAt, m.sentAt, m.sendAfter, m.deliveryStatus, m.failureReason)
          from SmsMessage m
          where (:kind is null or m.kind = :kind) and (:status is null or m.status = :status)
            and (:phone is null or m.phone = :phone)
            and (:campaignId is null or m.campaignId = :campaignId)
          """,
      countQuery =
          "select count(m) from SmsMessage m"
              + " where (:kind is null or m.kind = :kind) and (:status is null or m.status = :status)"
              + " and (:phone is null or m.phone = :phone)"
              + " and (:campaignId is null or m.campaignId = :campaignId)")
  Page<MessageView> search(
      SmsMessage.Kind kind,
      SmsMessage.Status status,
      String phone,
      Long campaignId,
      Pageable pageable);
}
