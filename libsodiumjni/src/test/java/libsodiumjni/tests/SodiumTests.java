package libsodiumjni.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import libsodiumjni.KeyPair;
import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;
import libsodiumjni.internal.SodiumApi;
import org.junit.BeforeClass;
import org.junit.Test;

public class SodiumTests {

    // Sizes defined by libsodium for crypto_box (X25519 + XSalsa20-Poly1305)
    private static final int PUBLIC_KEY_BYTES = 32;
    private static final int SECRET_KEY_BYTES = 32;
    private static final int SEAL_BYTES = 48;

    @BeforeClass
    public static void setUp() throws Exception {
        LoadLibrary.initializeFromResources();
        Sodium.init();
    }

    @Test
    public void initIsIdempotent() {
        Sodium.init();
        Sodium.init();
    }

    @Test
    public void constantsMatchLibsodium() {
        assertEquals(PUBLIC_KEY_BYTES, SodiumApi.crypto_box_public_key_bytes());
        assertEquals(SECRET_KEY_BYTES, SodiumApi.crypto_box_secret_key_bytes());
        assertEquals(SEAL_BYTES, SodiumApi.crypto_box_seal_bytes());
    }

    @Test
    public void keyPairHasExpectedSizes() {
        KeyPair keyPair = Sodium.keyPair();
        assertNotNull(keyPair.getPubKey());
        assertNotNull(keyPair.getSecKey());
        assertEquals(PUBLIC_KEY_BYTES, keyPair.getPubKey().length);
        assertEquals(SECRET_KEY_BYTES, keyPair.getSecKey().length);
    }

    @Test
    public void keyPairsAreRandom() {
        KeyPair a = Sodium.keyPair();
        KeyPair b = Sodium.keyPair();
        assertFalse(Arrays.equals(a.getPubKey(), b.getPubKey()));
        assertFalse(Arrays.equals(a.getSecKey(), b.getSecKey()));
        assertFalse(Arrays.equals(a.getPubKey(), new byte[PUBLIC_KEY_BYTES]));
    }

    @Test
    public void sealAndOpenRoundTrip() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = "Hello, libsodium!".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = Sodium.seal(message, keyPair.getPubKey());
        assertEquals(message.length + SEAL_BYTES, cipher.length);
        assertFalse(Arrays.equals(message, Arrays.copyOf(cipher, message.length)));

        byte[] decrypted = Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey());
        assertArrayEquals(message, decrypted);
    }

    @Test
    public void sealAndOpenEmptyMessage() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(new byte[0], keyPair.getPubKey());
        assertEquals(SEAL_BYTES, cipher.length);
        byte[] decrypted = Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey());
        assertEquals(0, decrypted.length);
    }

    @Test
    public void sealAndOpenLargeMessage() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = new byte[1024 * 1024];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) i;
        }
        byte[] cipher = Sodium.seal(message, keyPair.getPubKey());
        byte[] decrypted = Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey());
        assertArrayEquals(message, decrypted);
    }

    @Test
    public void sealingIsNonDeterministic() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = "same message".getBytes(StandardCharsets.UTF_8);
        byte[] cipher1 = Sodium.seal(message, keyPair.getPubKey());
        byte[] cipher2 = Sodium.seal(message, keyPair.getPubKey());
        assertFalse(Arrays.equals(cipher1, cipher2));
    }

    @Test
    public void openWithWrongKeyPairFails() {
        KeyPair recipient = Sodium.keyPair();
        KeyPair other = Sodium.keyPair();
        byte[] cipher = Sodium.seal("secret".getBytes(StandardCharsets.UTF_8), recipient.getPubKey());
        assertThrows(
                RuntimeException.class,
                () -> Sodium.sealOpen(cipher, other.getPubKey(), other.getSecKey()));
    }

    @Test
    public void openTamperedCipherFails() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal("secret".getBytes(StandardCharsets.UTF_8), keyPair.getPubKey());
        cipher[cipher.length - 1] ^= 0x01;
        assertThrows(
                RuntimeException.class,
                () -> Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey()));
    }

    @Test
    public void openTooShortCipherFails() {
        KeyPair keyPair = Sodium.keyPair();
        assertThrows(
                RuntimeException.class,
                () -> Sodium.sealOpen(new byte[SEAL_BYTES - 1], keyPair.getPubKey(), keyPair.getSecKey()));
    }
}
