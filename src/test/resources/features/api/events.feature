# Full positive/negative coverage of eventhub's /events CRUD endpoints, against the live API -
# ported 1:1 from the original com.tests.tests.api.EventApiTest. Every event created is tracked
# and deleted after the scenario (see com.tests.hooks.ApiHooks), so a failed assertion mid-
# scenario still leaves the seeded account clean.
@api @events
Feature: Events CRUD

  Background:
    Given I am logged in via the API as the seeded account

  @smoke @positive
  Scenario: Creating an event with all fields persists every field
    When I create an event titled "Full Fields Event" from the "fullFieldsEvent" event test data
    Then the create-event response should match the "fullFieldsEvent" event test data's expected status code
    And every submitted field should be persisted on the created event

  @positive
  Scenario: Creating an event with only required fields succeeds
    When I create an event titled "Required Fields Only Event" from the "defaultEvent" event test data
    Then the create-event response should match the "defaultEvent" event test data's expected status code
    And the created event should be assigned a positive numeric id

  @negative
  Scenario: Creating an event without auth returns 401
    Given I am logged out
    When I create an event titled "Should Never Be Created" from the "defaultEvent" event test data
    Then the create-event response should match the "unauthenticatedEventCreate" event test data's expected status and error

  @negative
  Scenario: Creating an event with no body fields flags every required field
    When I create an event with an empty request body
    Then the event response status code should be 400
    And every required event field should be flagged as missing

  @negative
  Scenario: Creating an event with negative price and seats fails validation
    When I create an event titled "Negative Values Event" from the "negativePriceAndSeatsEvent" event test data
    Then the create-event response should match the "negativePriceAndSeatsEvent" event test data's expected status code
    And the event's validation errors should flag both "price" and "totalSeats"

  @negative
  Scenario: Creating an event with a past date fails validation
    When I create an event titled "Past Date Event" from the "pastDateEvent" event test data
    Then the create-event response should match the "pastDateEvent" event test data's expected status code
    And the first validation error should match the "pastDateEvent" event test data's expected field and message

  @smoke @positive
  Scenario: Getting an existing event by id returns it
    Given I have created an event titled "Get By Id Event" from the "defaultEvent" event test data
    When I get the created event by id
    Then the get-event response should be 200 with the same id

  @negative
  Scenario: Getting a nonexistent event returns 404 with an explanatory message
    When I get event id 999999
    Then the event response should match the "nonexistentEventLookup" event test data's expected status and error

  @negative
  Scenario: Getting an event by a non-numeric id returns 500, not a validation error
    # Documented, live-verified quirk: a non-numeric path segment is not caught by input
    # validation before it reaches the database layer, and surfaces as a generic 500 rather than
    # a 400/404 - worth locking in as a regression test precisely because it is surprising.
    When I get the event with id "not-a-number"
    Then the event response status code should be 500

  @negative
  Scenario: Getting another user's event returns 404, not forbidden
    # Verified live: eventhub scopes every event to its creating account. A second account gets
    # an ordinary "not found" 404 for a real event ID it simply doesn't own - not 403.
    Given I have created an event titled "Isolation Target Event" from the "defaultEvent" event test data
    And I log out and register a brand-new random second account using the "secondAccountForEventIsolation" auth test data
    When I get the created event by id
    Then the event response status code should be 404

  @smoke @positive
  Scenario: Listing events returns a paginated envelope
    When I list events on page 1 with a limit of 5
    Then the list-events response should be a paginated envelope of at most 5 results

  @positive
  Scenario: Listing events filters by category
    Given I have created an event titled "Category Filter Event" from the "sportsCategoryEvent" event test data
    When I list events filtered by the "sportsCategoryEvent" event test data's category
    Then every listed event should be in the "sportsCategoryEvent" event test data's category

  @positive
  Scenario: Listing events free-text search matches the title
    Given I have created an event titled "Searchable Unique Title" from the "defaultEvent" event test data
    When I search events for "Searchable Unique Title"
    Then the search results should include the created event's title

  @negative
  Scenario: Listing events without auth returns 401
    Given I am logged out
    When I list all events
    Then the event response status code should be 401

  @smoke @positive
  Scenario: Updating an event changes its fields
    Given I have created an event titled "Before Update" from the "defaultEvent" event test data
    When I update the created event to "After Update" using the "eventUpdate" event test data
    Then the update-event response should match the "eventUpdate" event test data's expected status code
    And the update response and a fresh GET should both show the new fields

  @negative
  Scenario: Updating a nonexistent event returns 404
    When I update event id 999999 to "Ghost Update" using the "defaultEvent" event test data
    Then the event response status code should be 404

  @negative
  Scenario: Updating an event with invalid data fails validation
    Given I have created an event titled "Invalid Update Target" from the "defaultEvent" event test data
    When I update the created event to "Bad Update" using the "invalidEventUpdate" event test data
    Then the update-event response should match the "invalidEventUpdate" event test data's expected status code

  @smoke @positive
  Scenario: Deleting an event then getting it returns 404
    Given I have created an event titled "Delete Me" from the "defaultEvent" event test data, untracked
    When I delete the created event
    Then the event response status code should be 200
    And getting the deleted event should return 404

  @negative
  Scenario: Deleting an already-deleted event returns 404 on the second call
    Given I have created an event titled "Double Delete" from the "defaultEvent" event test data, untracked
    When I delete the created event
    And I delete the created event again
    Then the event response status code should be 404

  @negative
  Scenario: Deleting a nonexistent event returns 404
    When I delete event id 999999
    Then the event response status code should be 404
    And the event response should include an explanatory error message
