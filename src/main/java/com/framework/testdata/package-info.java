/**
 * Source-agnostic test data loading: JSON, YAML, Excel, CSV readers (package-private,
 * {@link com.framework.testdata.TestDataReader} implementations) behind the public
 * {@link com.framework.testdata.TestDataManager} facade and its
 * {@link com.framework.testdata.TestData} result type (Phase 9). {@code ${{...}}}
 * placeholder resolution ({@link com.framework.testdata.PlaceholderResolver}, Phase 3/8)
 * is applied automatically on every access.
 */
package com.framework.testdata;
