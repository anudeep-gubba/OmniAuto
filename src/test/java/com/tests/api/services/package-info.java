/**
 * Application-specific API Service Object Model classes (e.g. AuthenticationService, EventService)
 * - eventhub's own endpoints, business logic, and vocabulary. Deliberately in {@code src/test},
 * not {@code com.framework}: the reusable REST client engine these wrap
 * ({@link com.framework.api.ApiClient}) belongs in the core framework, but knowledge of "an
 * event has a totalSeats field" does not - the same reasoning that keeps Web/Mobile Page Objects
 * (see {@code com.tests.pages}) out of {@code com.framework} too.
 */
package com.tests.api.services;
