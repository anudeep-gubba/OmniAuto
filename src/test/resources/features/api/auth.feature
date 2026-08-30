# Full positive/negative coverage of eventhub's /auth/* endpoints (register, login, me), against
# the live API - not a mock. Ported 1:1 from the original com.tests.tests.api.AuthApiTest; every
# assertion was confirmed directly against the running API before being encoded here.
@api @auth
Feature: Authentication

  @smoke @positive
  Scenario: Registering a new user returns a usable token
    When I register a brand-new random user with the "registerNewUser" auth test data
    Then the registration should report success with a usable token for a newly registered user
    And GET /auth/me should return the same account that was just registered

  @negative
  Scenario: Registering with an already-registered email fails
    # The seeded account (used by "Logging in with an existing account works" etc.) is
    # guaranteed to exist.
    When I register with the "registerDuplicateEmail" auth test data as-is
    Then the response should match the "registerDuplicateEmail" auth test data's expected status and error
    And the response should report success as false

  @negative
  Scenario: Registering with an invalid email format fails validation
    When I register with the "registerInvalidEmailFormat" auth test data as-is
    Then the response should match the "registerInvalidEmailFormat" auth test data's expected status code
    And the first validation error should match the "registerInvalidEmailFormat" auth test data's expected field and message

  @negative
  Scenario: Registering with a password shorter than six characters fails validation
    When I attempt to register a brand-new random user with the "registerShortPassword" auth test data
    Then the response should match the "registerShortPassword" auth test data's expected status code
    And the first validation error should match the "registerShortPassword" auth test data's expected field and message

  @negative
  Scenario: Registering with no body fields at all flags every required field
    When I register with an empty request body
    Then the response should match the "registerEmptyBody" auth test data's expected status and error
    And the registration's validation errors should flag both "email" and "password"

  @smoke @positive
  Scenario: Logging in with an existing account works
    When I log in with the "loginExistingAccount" auth test data
    Then the login should report success with a usable token matching the account logged in with

  @negative
  Scenario: Logging in with the wrong password fails
    When I attempt to log in with the "loginWrongPassword" auth test data
    Then the login attempt should fail with a message containing "Login failed"

  @negative
  Scenario: Logging in with a nonexistent email returns a generic 400
    # The OpenAPI spec documents this case as a 404 "User not found" - but the live API does not
    # actually distinguish "no such account" from "wrong password" (both return this same 400
    # "Invalid email or password", confirmed live), which is the more defensible behavior anyway:
    # it avoids leaking which emails are registered to an unauthenticated caller. This locks in
    # the real, observed contract rather than the spec's stale documented one.
    When I log in with a brand-new random nonexistent email using the "loginNonexistentEmail" auth test data's password
    Then the response should match the "loginNonexistentEmail" auth test data's expected status and error

  @negative
  Scenario: Logging in with a missing password field fails validation
    When I log in with only the email from the "loginMissingPasswordField" auth test data
    Then the response should match the "loginMissingPasswordField" auth test data's expected status code

  @negative
  Scenario: Calling a protected endpoint without logging in fails fast
    # hasAuthToken() is false, so the service short-circuits before ever making the call, rather
    # than letting the API 401 it.
    Then calling GET /auth/me without logging in first should fail fast without a network call

  @negative
  Scenario: Calling /auth/me with a garbage bearer token returns 401
    Given I set the auth token from the "meGarbageToken" auth test data
    Then calling GET /auth/me should fail with a message containing "Token validation failed"

  @negative
  Scenario: Calling /auth/me with no Authorization header at all returns 401
    # Bypasses AuthenticationService's own "no token set" short-circuit, to prove the API itself
    # (not just the framework's client-side guard) rejects the call.
    When I call GET /auth/me directly with no Authorization header
    Then the response should match the "meWithoutAuth" auth test data's expected status and error
