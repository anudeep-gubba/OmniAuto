# Phase 5 validation for the Component Object Model (requirement.md §7): HeaderComponent as a
# page-wide singleton component, EventCardComponent as an N-repeated component, on
# eventhub.rahulshettyacademy.com's real event listing. Ported 1:1 from the original
# com.tests.tests.web.EventsTest.
@web @events
Feature: Events listing

  Background:
    Given I am logged in as the seeded account
    And I navigate to the events page

  @positive
  Scenario: Events listing shows at least one event
    When I read the event cards
    Then the events page should list at least one event
    And the first event card should display a non-blank name and a dollar price

  @positive
  Scenario: Each event card is independently scoped
    # Proves BaseComponent's root-scoping: reading N cards' names never returns the same
    # element twice or bleeds one card's data into another's.
    When I read the event cards
    Then every event card should report its own distinct name

  @positive
  Scenario: Book Now navigates to the event detail page
    When I click Book Now on the first event card
    Then I should be navigated to an event detail page

  @positive
  Scenario: Header shows the logged-in user across pages
    Then the header should show the logged-in user's email
