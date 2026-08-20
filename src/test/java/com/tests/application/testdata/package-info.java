/**
 * All test-case data for every surface, gathered under one tree rather than scattered across
 * {@code com.tests.api}/{@code com.tests.web}/{@code com.tests.mobile}. Every {@code
 * *TestCase} record below implements {@link com.framework.testdata.TestCaseRecord}, pairing a
 * {@link com.framework.testdata.TestCaseMetadata} (the id/name pair, framework core, generic)
 * with its own {@code data} type - see {@link com.tests.application.testdata.LoginTestCase}
 * (login's {@code email}/{@code password} shape, shared by Web and Mobile since it's identical
 * across both rather than duplicated as near-copies). One subpackage per surface where the
 * shape genuinely differs - {@code com.tests.application.testdata.api}, {@code
 * com.tests.application.testdata.mobile} - each holding that surface's own {@code *TestCase}
 * records for its {@code testdata/json/*.json} files.
 *
 * <p>A new test case is still just a new JSON entry, never a new Java file; a new field on an
 * existing shape is one file to edit (the record and its nested {@code *Data} type live
 * together), not two; and a shape two surfaces happen to share is one file, not two, until the
 * day it genuinely diverges.</p>
 */
package com.tests.application.testdata;
