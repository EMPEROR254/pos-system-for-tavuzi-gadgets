package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import org.springframework.data.jpa.repository.JpaRepository;

/** Linking requests in progress. */
public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {}
