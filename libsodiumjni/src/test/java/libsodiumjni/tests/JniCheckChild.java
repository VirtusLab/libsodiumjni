package libsodiumjni.tests;

import java.nio.charset.StandardCharsets;

import libsodiumjni.KeyPair;
import libsodiumjni.Sodium;
import libsodiumjni.SodiumException;
import libsodiumjni.internal.LoadLibrary;
import libsodiumjni.internal.SodiumApi;

/**
 * Run in a child JVM with -Xcheck:jni by JniCheckTests: calls every native method, on success and
 * error paths (null arrays, invalid lengths, libsodium failures), so that the JVM reports any JNI
 * misuse in them. Prints "DONE" and exits with 0 once done.
 */
public class JniCheckChild {

    private static final int KEY_BYTES = 32;
    private static final int SEAL_BYTES = 48;

    private static void expect(Class<? extends Throwable> expected, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) return;
            throw new AssertionError("Expected " + expected.getName() + ", got " + t, t);
        }
        throw new AssertionError("Expected " + expected.getName());
    }

    private static void expectResult(int expected, int actual) {
        if (actual != expected)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) throws Exception {
        LoadLibrary.initializeFromResources();
        Sodium.init();

        // success paths
        byte[] message = "Hello, libsodium!".getBytes(StandardCharsets.UTF_8);
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message, keyPair.getPubKey());
        Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey());
        Sodium.sealOpen(Sodium.seal(new byte[0], keyPair.getPubKey()), keyPair.getPubKey(), keyPair.getSecKey());
        expectResult(KEY_BYTES, SodiumApi.crypto_box_public_key_bytes());
        expectResult(KEY_BYTES, SodiumApi.crypto_box_secret_key_bytes());
        expectResult(SEAL_BYTES, SodiumApi.crypto_box_seal_bytes());

        // libsodium failures
        byte[] tampered = cipher.clone();
        tampered[tampered.length - 1] ^= 1;
        expect(SodiumException.class, () -> Sodium.sealOpen(tampered, keyPair.getPubKey(), keyPair.getSecKey()));
        expect(SodiumException.class, () -> Sodium.seal(message, new byte[KEY_BYTES]));

        // null arrays (NullPointerException thrown from the native code)
        byte[] pk = keyPair.getPubKey();
        byte[] sk = keyPair.getSecKey();
        byte[] out = new byte[cipher.length - SEAL_BYTES];
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_keypair(null, new byte[KEY_BYTES]));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_keypair(new byte[KEY_BYTES], null));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal(null, message, pk));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal(new byte[cipher.length], null, pk));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal(new byte[cipher.length], message, null));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal_open(null, cipher, pk, sk));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal_open(out, null, pk, sk));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal_open(out, cipher, null, sk));
        expect(NullPointerException.class, () -> SodiumApi.crypto_box_seal_open(out, cipher, pk, null));

        // invalid lengths
        int invalid = -10;
        expectResult(invalid, SodiumApi.crypto_box_keypair(new byte[KEY_BYTES - 1], new byte[KEY_BYTES]));
        expectResult(invalid, SodiumApi.crypto_box_seal(new byte[cipher.length - 1], message, pk));
        expectResult(invalid, SodiumApi.crypto_box_seal(new byte[cipher.length], message, new byte[KEY_BYTES - 1]));
        expectResult(invalid, SodiumApi.crypto_box_seal_open(new byte[out.length + 1], cipher, pk, sk));
        expectResult(invalid, SodiumApi.crypto_box_seal_open(out, cipher, pk, new byte[KEY_BYTES - 1]));

        System.out.println("DONE");
    }
}
