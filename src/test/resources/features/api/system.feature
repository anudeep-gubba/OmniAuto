# Coverage of eventhub's two unauthenticated endpoints, GET /health and GET /config. Both are
# public by design (no bearer token, no per-account isolation), so there is no meaningful
# "negative" case beyond confirming they stay reachable and shaped as documented - unlike every
# other endpoint in this suite, there is nothing to log in as or clean up afterward. Ported 1:1
# from the original com.tests.tests.api.SystemApiTest.
@api @system
Feature: System health and config

  # Also "sanity": the narrowest possible "is the app fundamentally alive" checkpoint - one
  # representative live scenario per surface, distinct from and smaller than "smoke". The API
  # surface's sole sanity scenario (not login, despite that also being smoke+sanity-shaped) -
  # /health needs neither auth nor test data to succeed, which is exactly what "is anything even
  # up" should mean, one level narrower than a case that also exercises login itself.
  @smoke @sanity @positive
  Scenario: Health check reports ok with a connected database
    When I call GET /health
    Then the health response should match the "healthCheck" system test data's expected status, status, and db status
    And the health response should include a timestamp

  @positive
  Scenario: Config returns the public feature flags
    When I call GET /config
    Then the config response should match the "systemConfig" system test data's expected status code
    And the config response should deserialize into a non-null config
