package libsodiumjni.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import libsodiumjni.KeyPair;
import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;
import libsodiumjni.internal.SodiumApi;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Checks that invalid sizes passed to the bindings are rejected, rather than making the native code
 * read or write out of bounds.
 */
public class SodiumInputValidationTests {

    private static final int KEY_BYTES = 32;
    private static final int SEAL_BYTES = 48;

    /** Error code returned by the native functions when passed inconsistent sizes */
    private static final int INVALID_ARGUMENTS = -10;

    private static final byte FILLER = 0x55;

    @BeforeClass
    public static void setUp() throws Exception {
        LoadLibrary.initializeFromResources();
        Sodium.init();
    }

    private static byte[] filled(int len) {
        byte[] arr = new byte[len];
        Arrays.fill(arr, FILLER);
        return arr;
    }

    private static byte[] message() {
        return "Hello, libsodium!".getBytes(StandardCharsets.UTF_8);
    }

    // Sodium (public API)

    @Test
    public void sealRejectsTooShortPublicKey() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Sodium.seal(message(), new byte[KEY_BYTES - 1]));
        assertEquals("Invalid public key length (31, expected 32)", ex.getMessage());
    }

    @Test
    public void sealRejectsTooLongPublicKey() {
        assertThrows(IllegalArgumentException.class, () -> Sodium.seal(message(), new byte[KEY_BYTES + 1]));
    }

    @Test
    public void sealRejectsEmptyPublicKey() {
        assertThrows(IllegalArgumentException.class, () -> Sodium.seal(message(), new byte[0]));
    }

    @Test
    public void sealOpenRejectsInvalidPublicKey() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message(), keyPair.getPubKey());
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Sodium.sealOpen(cipher, new byte[KEY_BYTES - 1], keyPair.getSecKey()));
        assertEquals("Invalid public key length (31, expected 32)", ex.getMessage());
    }

    @Test
    public void sealOpenRejectsInvalidSecretKey() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message(), keyPair.getPubKey());
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Sodium.sealOpen(cipher, keyPair.getPubKey(), new byte[KEY_BYTES - 1]));
        assertEquals("Invalid secret key length (31, expected 32)", ex.getMessage());
    }

    // SodiumApi (public native bindings, used by Sodium)

    @Test
    public void nativeSealRejectsMessageLengthPastMessageArray() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = message();
        long messageLen = message.length + 100L;
        byte[] cipher = new byte[(int) (SEAL_BYTES + messageLen)];
        assertEquals(INVALID_ARGUMENTS, SodiumApi.crypto_box_seal(cipher, message, messageLen, keyPair.getPubKey()));
    }

    @Test
    public void nativeSealRejectsNegativeMessageLength() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = new byte[SEAL_BYTES];
        assertEquals(INVALID_ARGUMENTS, SodiumApi.crypto_box_seal(cipher, message(), -1L, keyPair.getPubKey()));
    }

    @Test
    public void nativeSealRejectsTooShortPublicKey() {
        byte[] message = message();
        byte[] cipher = new byte[SEAL_BYTES + message.length];
        assertEquals(
                INVALID_ARGUMENTS,
                SodiumApi.crypto_box_seal(cipher, message, message.length, new byte[KEY_BYTES - 1]));
    }

    @Test
    public void nativeSealOnlyWritesTheSealedMessage() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = message();
        int sealedLen = SEAL_BYTES + message.length;
        byte[] cipher = filled(sealedLen + 16);
        assertEquals(0, SodiumApi.crypto_box_seal(cipher, message, message.length, keyPair.getPubKey()));
        // the bytes past the sealed message must be left untouched
        assertArrayEquals(filled(16), Arrays.copyOfRange(cipher, sealedLen, cipher.length));
        // and the sealed message must be valid
        byte[] decrypted = Sodium.sealOpen(Arrays.copyOf(cipher, sealedLen), keyPair.getPubKey(), keyPair.getSecKey());
        assertArrayEquals(message, decrypted);
    }

    @Test
    public void nativeSealOpenRejectsTooSmallOutputArray() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = new byte[1024];
        byte[] cipher = Sodium.seal(message, keyPair.getPubKey());
        byte[] decrypted = filled(16);
        assertEquals(
                INVALID_ARGUMENTS,
                SodiumApi.crypto_box_seal_open(decrypted, cipher, cipher.length, keyPair.getPubKey(), keyPair.getSecKey()));
        assertArrayEquals(filled(16), decrypted);
    }

    @Test
    public void nativeSealOpenRejectsTooLargeOutputArray() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message(), keyPair.getPubKey());
        byte[] decrypted = new byte[cipher.length];
        assertEquals(
                INVALID_ARGUMENTS,
                SodiumApi.crypto_box_seal_open(decrypted, cipher, cipher.length, keyPair.getPubKey(), keyPair.getSecKey()));
    }

    @Test
    public void nativeSealOpenRejectsTooShortKeys() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message(), keyPair.getPubKey());
        byte[] decrypted = new byte[cipher.length - SEAL_BYTES];
        assertEquals(
                INVALID_ARGUMENTS,
                SodiumApi.crypto_box_seal_open(decrypted, cipher, cipher.length, new byte[KEY_BYTES - 1], keyPair.getSecKey()));
        assertEquals(
                INVALID_ARGUMENTS,
                SodiumApi.crypto_box_seal_open(decrypted, cipher, cipher.length, keyPair.getPubKey(), new byte[KEY_BYTES - 1]));
    }

    @Test
    public void nativeKeyPairRejectsTooShortArrays() {
        assertEquals(INVALID_ARGUMENTS, SodiumApi.crypto_box_keypair(new byte[KEY_BYTES - 1], new byte[KEY_BYTES]));
        assertEquals(INVALID_ARGUMENTS, SodiumApi.crypto_box_keypair(new byte[KEY_BYTES], new byte[KEY_BYTES - 1]));
        assertTrue("valid arrays still work", SodiumApi.crypto_box_keypair(new byte[KEY_BYTES], new byte[KEY_BYTES]) == 0);
    }
}
