# The mobile equivalent of api/booking_e2e_flow.feature: the same "login -> browse -> book ->
# confirm -> shows up in My Bookings" journey, driven through the real app UI (Appium/XCUITest)
# instead of direct HTTP calls - each step chains a real value read from the previous screen (the
# event's name, the booking reference generated on confirmation) into the next assertion.
# Individual screen positive/negative cases live in mobile/login.feature and
# mobile/events.feature. Ported 1:1 from the original com.tests.tests.mobile.EventBookingE2EFlowTest.
@mobile @e2e @events @bookings
Feature: Mobile event booking end-to-end flow

  @smoke
  Scenario: Booking flow from login through confirmation and My Bookings works end to end
    Given I am logged in on the mobile app
    And I browse events and note the first event's name
    When I tap Book Now on the noted event card
    Then the event detail page should be displayed
    When I fill in the "standardBooking" mobile booking test data and confirm the booking
    Then the booking confirmation screen should be displayed with a generated reference
    When I tap View My Bookings
    Then My Bookings should show a card for the booked event
