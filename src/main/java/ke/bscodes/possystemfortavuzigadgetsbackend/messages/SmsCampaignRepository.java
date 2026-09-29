package ke.bscodes.possystemfortavuzigadgetsbackend.messages;

import org.springframework.data.jpa.repository.JpaRepository;

/** Promotional SMS campaigns. */
public interface SmsCampaignRepository extends JpaRepository<SmsCampaign, Long> {}
