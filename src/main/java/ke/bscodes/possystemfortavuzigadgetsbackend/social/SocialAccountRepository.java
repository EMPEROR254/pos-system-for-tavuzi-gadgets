package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import org.springframework.data.jpa.repository.JpaRepository;

/** Linked accounts, by network. */
public interface SocialAccountRepository extends JpaRepository<SocialAccount, SocialNetwork> {}
