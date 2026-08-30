# Mobile Component Object Model coverage for the eventhub app's Events listing:
# HeaderComponent as a page-wide singleton component, EventCardComponent as an N-repeated
# component - mirroring web/events.feature on the real screen, verified live against the
# iPhone 17 Pro Simulator. Ported 1:1 from the original com.tests.tests.mobile.EventsTest.
@mobile @events
Feature: Mobile events listing

  Background:
    Given I am logged in and browsing events

  @positive
  Scenario: Events listing shows at least one event
    When I read the mobile event cards
    Then the events listing should show at least one event
    And the first mobile event card should display a non-blank name and a dollar price

  @positive
  Scenario: Each event card is independently scoped
    # Proves BaseMobileComponent's root-scoping: reading N cards' names never returns the same
    # element twice or bleeds one card's data into another's.
    When I read the mobile event cards
    Then every mobile event card should report its own distinct name

  @positive
  Scenario: Book Now navigates to the event detail page
    When I tap Book Now on the first event card
    Then the event detail page should be displayed
