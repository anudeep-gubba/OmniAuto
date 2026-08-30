# Multi-step, cross-resource flows against eventhub's live, persisted API - each one chains a
# real value extracted from one response (an event ID, a booking reference) into the next
# request, rather than exercising any single endpoint in isolation. Individual endpoint
# positive/negative cases live in auth.feature/events.feature/bookings.feature; this file is
# specifically about the journeys a real client makes across several of them. Ported 1:1 from the
# original com.tests.tests.api.EventBookingE2EFlowTest.
@api @e2e @events @bookings
Feature: Event booking end-to-end flow

  @smoke
  Scenario: Full event lifecycle from registration through booking to deletion works end to end
    # Registration through cancellation/deletion, on a brand-new account so the flow is fully
    # isolated from the shared seeded account other tests use.
    Given I register a brand-new fully isolated account using the "e2eFullLifecycleRegistration" auth test data
    When I create an e2e event titled "E2E Flow Event" from the "e2eFullLifecycleEvent" event test data
    Then the e2e create-event response should match the "e2eFullLifecycleEvent" event test data's expected status code
    And the created event should surface via a direct GET and via a search for "E2E Flow Event"
    When I update the e2e event to "E2E Flow Event Updated" using the "e2eFullLifecycleEventUpdate" event test data
    Then the e2e update should be durable per the "e2eFullLifecycleEventUpdate" event test data
    When I book the e2e event using the "e2eFullLifecycleBooking" booking test data
    Then the e2e booking should reference the event and be confirmed per the "e2eFullLifecycleBooking" booking test data
    And the e2e event's available seats should reflect the "e2eFullLifecycleEvent" event test data's seats minus the "e2eFullLifecycleBooking" booking test data's quantity
    And the e2e booking should be findable by id, by reference, and in its event's booking list
    When I cancel the e2e booking
    Then cancelling should restore the "e2eFullLifecycleEvent" event test data's total seats and the booking should now return 404
    When I delete the e2e event
    Then the e2e event should now return 404

  @smoke
  Scenario: The booked event's id chains through ApiContext into the booking call
    # Proves ApiClient's bearer token (stored in ApiContext under ACCESS_TOKEN_KEY) and an
    # arbitrary chained value (a booked event's ID) coexist correctly in the same runtime-
    # variable store across a multi-call flow.
    Given I am logged in via the API as the seeded account
    Then ApiContext should already hold the access token
    When I create an e2e event titled "ApiContext Chaining Event" from the "e2eContextChainingEvent" event test data
    Then the e2e create-event response should match the "e2eContextChainingEvent" event test data's expected status code
    And the created event id should be chained through ApiContext
    When I book the event id chained through ApiContext using the "e2eContextChainingBooking" booking test data
    Then the e2e booking should reference the event id chained through ApiContext

  Scenario: Seat count stays correct across multiple sequential bookings and a cancellation
    Given I am logged in via the API as the seeded account
    When I create an e2e event titled "Multi Booking Event" from the "e2eSequentialBookingsEvent" event test data
    And I make the first sequential booking using the "e2eSequentialBookingOne" booking test data
    Then the available seats after the first booking should be correct
    When I make the second sequential booking using the "e2eSequentialBookingTwo" booking test data
    Then the available seats after the second booking should be correct
    When I cancel the first sequential booking
    Then cancelling the first booking should restore exactly its own seats

  Scenario: Deleting an event cascades to its bookings
    # Deleting an event cascades to its bookings (per the API's own documented behavior) - a
    # booking made through it becomes unreachable afterward rather than orphaned.
    Given I am logged in via the API as the seeded account
    When I create an e2e event titled "Cascade Delete Event" from the "e2eCascadeDeleteEvent" event test data
    And I book the e2e event using the "e2eCascadeDeleteBooking" booking test data
    And I delete the e2e event
    Then the e2e event should now return 404
    And the cascade-deleted booking should now return 404
