# Phase 5 validation (requirement.md §38 WEB checklist: valid login, invalid login) against
# eventhub.rahulshettyacademy.com. Ported 1:1 from the original com.tests.tests.web.LoginTest.
# Real account credentials, resolved via ${{EVENTHUB_EMAIL}}/${{EVENTHUB_PASSWORD}} placeholders
# in testdata/json/web/web.json - never hardcoded, never committed (see .secret.env.example).
@web @auth
Feature: Login

  @smoke @sanity @positive
  Scenario: Valid login navigates to the home page
    Given I am on the login page
    When I log in with the "validLogin" web test data
    Then the home page should be displayed

  @smoke @negative
  Scenario: Invalid login shows an error and stays on the login page
    Given I am on the login page
    When I log in with the "invalidLogin" web test data
    Then an error message should be displayed
    And the error message should mention "invalid" credentials
