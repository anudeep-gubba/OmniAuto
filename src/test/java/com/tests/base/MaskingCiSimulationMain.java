package com.tests.base;

import com.framework.secrets.SensitiveDataMasker;

/**
 * Not a TestNG test (no {@code @Test} method - never picked up by Surefire/TestNG's own
 * discovery). A tiny standalone {@code main()} spawned as a real child JVM by
 * {@code MaskingCiHardBlockTest}, so that test can give it a real {@code CI} environment
 * variable - something no code running inside the parent test JVM can fake, since
 * {@code System.getenv} has no supported mutation API.
 *
 * <p>Registers a known secret, masks a message containing it, and prints only the masked
 * result to stdout - the parent test's real assertion subject.</p>
 */
public final class MaskingCiSimulationMain {

    static final String SECRET_VALUE = "ci-simulation-secret-value-777666";

    private MaskingCiSimulationMain() {
    }

    public static void main(String[] args) {
        SensitiveDataMasker.registerSecretValue(SECRET_VALUE);
        System.out.println(SensitiveDataMasker.mask("value: " + SECRET_VALUE));
    }
}
