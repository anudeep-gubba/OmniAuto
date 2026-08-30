# Mobile login coverage for the eventhub app (apps/eventhub-app-simulator.app on iOS,
# apps/eventhub-app-release.apk on Android). Every locator/behavior here was verified live
# against a real iPhone 17 Pro Simulator session (Appium + XCUITest). Ported 1:1 from the
# original com.tests.tests.mobile.LoginTest.
@mobile @auth
Feature: Mobile login

  Background:
    # Guarantees a logged-out start regardless of what a previous scenario in this run left
    # behind - see HomePage.logoutIfLoggedIn()'s javadoc.
    Given the app is launched logged out

  @smoke @sanity @positive
  Scenario: Valid credentials log in and show the home screen
    When I log in with the "validCredentials" mobile test data
    Then the home screen should be displayed

  @smoke @negative
  Scenario: Blank credentials show required field validation
    When I tap sign in with no credentials entered
    Then the "email is required" and "password is required" errors should be displayed
    And the login screen should still be displayed

  @negative
  Scenario: Malformed email shows invalid email validation
    When I log in with the "malformedEmail" mobile test data
    Then the "invalid email" error should be displayed
    And the login screen should still be displayed

  @positive
  Scenario: Login succeeds even with an incorrect password
    # Deliberately its own scenario rather than an unexplained surprise: this build's mock auth
    # accepts any well-formed credentials, so a genuinely wrong password is not, in fact, a
    # negative case here.
    When I log in with the "incorrectPassword" mobile test data
    Then the home screen should be displayed
