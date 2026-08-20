package com.framework.testdata;

/**
 * The shape every {@code *TestCase} record follows: a {@link TestCaseMetadata} (id/name, for
 * logging and reporting) paired with the actual {@code data} of type {@code D}. A Java record
 * declared as {@code record LoginTestCase(TestCaseMetadata metadata, LoginData data) implements
 * TestCaseRecord<LoginData>} satisfies this for free - its own generated {@code metadata()}/
 * {@code data()} accessors are exactly what the interface asks for.
 *
 * <p>Implementing it is what lets {@link TestDataManager#getCaseData} work generically across
 * every test-case shape (login, an event payload, a booking, ...) without the framework needing
 * to know what any of them look like - it only ever calls {@link #metadata()}/{@link #data()}.</p>
 *
 * @param <D> the case-specific data type this test case pairs with its metadata
 */
public interface TestCaseRecord<D> {

    TestCaseMetadata metadata();

    D data();
}
