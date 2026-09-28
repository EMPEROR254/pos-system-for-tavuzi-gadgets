package ke.bscodes.possystemfortavuzigadgetsbackend.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import ke.bscodes.possystemfortavuzigadgetsbackend.auth.RefreshTokenRepository;
import ke.bscodes.possystemfortavuzigadgetsbackend.common.ApiException;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.AuthUser;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.PasswordHasher;
import ke.bscodes.possystemfortavuzigadgetsbackend.security.Role;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.CreateUserRequest;
import ke.bscodes.possystemfortavuzigadgetsbackend.users.UserDtos.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceTest {

  private final Instant now = Instant.parse("2026-09-28T07:00:00Z");
  private final UserRepository users = mock(UserRepository.class);
  private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
  private final UserService service =
      new UserService(users, tokens, Clock.fixed(now, ZoneOffset.UTC));

  private final AuthUser owner = new AuthUser(1, "owner", Role.OWNER);

  @Test
  void usernamesAreCaseInsensitiveAndUnique() {
    when(users.existsByUsername("jane")).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.create(new CreateUserRequest(" Jane ", "Jane", Role.CASHIER, "password1")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("already taken");
  }

  @Test
  void newUsersGetAHashedPassword() {
    when(users.save(any())).thenAnswer(call -> withId(call.getArgument(0), 5L));
    var created =
        service.create(new CreateUserRequest("Peter", "Peter K", Role.MANAGER, "password1"));
    assertThat(created.username()).isEqualTo("peter");
    verify(users).save(argThat(u -> PasswordHasher.verify("password1", u.getPasswordHash())));
  }

  @Test
  void youCannotDisableOrDemoteYourself() {
    when(users.findById(1L)).thenReturn(Optional.of(user(1L, Role.OWNER)));
    assertThatThrownBy(
            () -> service.update(owner, 1, new UpdateUserRequest("Me", Role.OWNER, false)))
        .hasMessageContaining("disable your own");
    assertThatThrownBy(
            () -> service.update(owner, 1, new UpdateUserRequest("Me", Role.CASHIER, true)))
        .hasMessageContaining("own role");
  }

  @Test
  void theLastActiveOwnerStays() {
    when(users.findById(2L)).thenReturn(Optional.of(user(2L, Role.OWNER)));
    when(users.countByRoleAndActiveTrue(Role.OWNER)).thenReturn(1L);
    AuthUser otherOwner = new AuthUser(9, "boss", Role.OWNER);
    assertThatThrownBy(
            () -> service.update(otherOwner, 2, new UpdateUserRequest("X", Role.MANAGER, true)))
        .hasMessageContaining("at least one active owner");
  }

  @Test
  void disablingSignsTheUserOutEverywhere() {
    when(users.findById(3L)).thenReturn(Optional.of(user(3L, Role.CASHIER)));
    var updated = service.update(owner, 3, new UpdateUserRequest("Cash", Role.CASHIER, false));
    assertThat(updated.active()).isFalse();
    verify(tokens).revokeAllForUser(3L, now);
  }

  @Test
  void renamingDoesNotSignOut() {
    when(users.findById(3L)).thenReturn(Optional.of(user(3L, Role.CASHIER)));
    service.update(owner, 3, new UpdateUserRequest("New Name", Role.CASHIER, true));
    verify(tokens, never()).revokeAllForUser(anyLong(), any());
  }

  @Test
  void resetPasswordUnlocksAndSignsOut() {
    User locked = user(4L, Role.CASHIER);
    locked.recordFailedLogin(1, now.plusSeconds(600));
    when(users.findById(4L)).thenReturn(Optional.of(locked));

    service.resetPassword(4, "brand-new-pass");

    assertThat(locked.isLockedAt(now)).isFalse();
    assertThat(PasswordHasher.verify("brand-new-pass", locked.getPasswordHash())).isTrue();
    verify(tokens).revokeAllForUser(4L, now);
  }

  private User user(long id, Role role) {
    return withId(new User("user" + id, "User " + id, "pbkdf2$1$AA==$AA==", role, now), id);
  }

  private static User withId(User user, long id) {
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
