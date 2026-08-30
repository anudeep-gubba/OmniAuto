# Full positive/negative coverage of eventhub's /bookings endpoints, against the live API -
# ported 1:1 from the original com.tests.tests.api.BookingApiTest. Every scenario provisions its
# own throwaway event (small totalSeats, so overbooking/insufficient-seats scenarios are cheap to
# trigger deterministically) and tears down both the booking and the event afterward (see
# com.tests.hooks.ApiHooks).
@api @bookings
Feature: Bookings

  Background:
    Given I am logged in via the API as the seeded account

  @smoke @positive
  Scenario: Booking tickets decrements available seats and returns a booking reference
    When I create an event and book it using the "standardBookingScenario" booking test data
    Then the booking response should match the "standardBookingScenario" booking test data's expected status code
    And the created booking should reference the event, record the quantity, and be confirmed per the "standardBookingScenario" booking test data
    And the event's available seats should reflect the "standardBookingScenario" booking test data's seat decrement

  @smoke @positive
  Scenario: Booking exactly all remaining seats succeeds
    When I create an event and book it using the "exactRemainingSeatsScenario" booking test data
    Then the booking response should match the "exactRemainingSeatsScenario" booking test data's expected status code
    And I track the booking for cleanup
    And the event should have zero available seats remaining

  @negative
  Scenario: Booking more tickets than available seats fails
    When I create an event and book it using the "overbookingScenario" booking test data
    Then the booking response should match the "overbookingScenario" booking test data's expected status code
    And the overbooking error message should state the actual seat shortfall per the "overbookingScenario" booking test data

  @negative
  Scenario: Booking for a nonexistent event returns 404
    When I book event id 999999 with quantity 1 using the default customer details
    Then the booking response should match the "nonexistentEventBooking" booking test data's expected status and error

  @negative
  Scenario: Booking with quantity zero fails validation
    When I create an event and book it using the "zeroQuantityScenario" booking test data
    Then the booking response should match the "zeroQuantityScenario" booking test data's expected status code
    And the booking validation error should flag the "zeroQuantityScenario" booking test data's expected field

  @negative
  Scenario: Booking with quantity above ten fails validation
    When I create an event and book it using the "aboveMaxQuantityScenario" booking test data
    Then the booking response should match the "aboveMaxQuantityScenario" booking test data's expected status code
    And the booking validation error should match the "aboveMaxQuantityScenario" booking test data's expected message

  @negative
  Scenario: Booking with an invalid customer email fails validation
    Given I create a throwaway event with 5 seats
    When I book that event using the "invalidCustomerEmail" booking test data's own customer details
    Then the booking response should match the "invalidCustomerEmail" booking test data's expected status code
    And the booking validation error should flag the "invalidCustomerEmail" booking test data's expected field

  @negative
  Scenario: Booking with a too-short customer phone fails validation
    Given I create a throwaway event with 5 seats
    When I book that event using the "tooShortCustomerPhone" booking test data's own customer details
    Then the booking response should match the "tooShortCustomerPhone" booking test data's expected status code
    And the booking validation error should flag the "tooShortCustomerPhone" booking test data's expected field

  @negative
  Scenario: Booking without auth returns 401
    Given I am logged out
    When I book event id 1 with quantity 1 using the default customer details
    Then the booking response status code should be 401

  @smoke @positive
  Scenario: Getting a booking by id and by reference return the same booking
    Given I create a throwaway event with 5 seats
    When I book that event with quantity 1 using the default customer details
    And I track the booking for cleanup
    Then getting the booking by id and by reference should return the same booking

  @negative
  Scenario: Getting a nonexistent booking by id returns 404
    When I get booking id 999999
    Then the booking response should match the "nonexistentBookingLookup" booking test data's expected status and error

  @negative
  Scenario: Getting a nonexistent booking by reference returns 404
    When I get the booking by the "nonexistentBookingReference" booking test data's reference
    Then the booking response should match the "nonexistentBookingReference" booking test data's expected status code
    And the not-found error should echo back the "nonexistentBookingReference" booking test data's reference

  @smoke @positive
  Scenario: Listing bookings filtered by event id returns only that event's bookings
    Given I create a throwaway event with 5 seats
    When I book that event with quantity 1 using the default customer details
    And I track the booking for cleanup
    And I list bookings filtered by that event's id
    Then the booking response status code should be 200
    And every listed booking should reference that event

  @positive
  Scenario: Listing bookings respects pagination
    When I list bookings using the "pageableListingScenario" booking test data's page and limit
    Then the booking response should match the "pageableListingScenario" booking test data's expected status code
    And the booking list pagination should echo back the "pageableListingScenario" booking test data's page and limit

  @negative
  Scenario: Listing bookings without auth returns 401
    Given I am logged out
    When I list all bookings
    Then the booking response status code should be 401

  @smoke @positive
  Scenario: Cancelling a booking restores the seat and makes it unretrievable
    Given I create a throwaway event with 3 seats
    When I book that event with quantity 2 using the default customer details
    Then the event should have 1 available seat remaining
    When I cancel that booking
    Then the cancel-booking response should match the "bookingCancellation" booking test data's expected status and message
    And the event should have 3 available seats remaining
    And that booking should now return 404

  @negative
  Scenario: Cancelling an already-cancelled booking returns 404 on the second call
    Given I create a throwaway event with 3 seats
    When I book that event with quantity 1 using the default customer details
    And I cancel that booking
    Then that booking should now return 404
    When I cancel that booking again
    Then the booking response status code should be 404

  @negative
  Scenario: Cancelling a nonexistent booking returns 404
    When I cancel booking id 999999
    Then the booking response status code should be 404
