package libsodiumjni.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.Assume;
import org.junit.Test;

/**
 * The JAR bundles copies of the LICENSE and THIRD-PARTY-NOTICES.txt files of the repository, under
 * META-INF/libsodiumjni (rather than a bare META-INF/LICENSE, which collides with other JARs' in
 * assemblies). The content of THIRD-PARTY-NOTICES.txt is checked by libsodiumjni.checkThirdPartyNotices.
 */
public class LicenseResourcesTests {

    private static final String LICENSE = "META-INF/libsodiumjni/LICENSE";
    private static final String NOTICES = "META-INF/libsodiumjni/THIRD-PARTY-NOTICES.txt";

    /** SHA-256 of https://www.apache.org/licenses/LICENSE-2.0.txt */
    private static final String APACHE_2_0_SHA256 = "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30";

    private static byte[] resource(String path) throws IOException {
        try (InputStream is = LicenseResourcesTests.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull("Resource " + path + " not found", is);
            return is.readAllBytes();
        }
    }

    @Test
    public void licenseIsTheApache2License() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(resource(LICENSE));
        assertEquals("SHA-256 of " + LICENSE, APACHE_2_0_SHA256, HexFormat.of().formatHex(digest));
    }

    @Test
    public void bundledFilesAreTheRepositoryOnes() throws Exception {
        String workspace = System.getProperty("libsodiumjni.test.workspace", "");
        Assume.assumeFalse("no workspace passed by the build (not run by Mill)", workspace.isEmpty());
        assertArrayEquals(LICENSE, Files.readAllBytes(Paths.get(workspace, "LICENSE")), resource(LICENSE));
        assertArrayEquals(
                NOTICES, Files.readAllBytes(Paths.get(workspace, "THIRD-PARTY-NOTICES.txt")), resource(NOTICES));
    }
}
