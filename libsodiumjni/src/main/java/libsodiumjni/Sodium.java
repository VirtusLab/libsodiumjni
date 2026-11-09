package libsodiumjni;

import libsodiumjni.internal.SodiumApi;

public final class Sodium {

    private static boolean initialized = false;

    public static void init() {
        if (!initialized) {
            synchronized (Sodium.class) {
                int res = SodiumApi.sodium_init();
                if (res != 0) {
                    throw new RuntimeException("Cannot initialize libsodium");
                }
                initialized = true;
            }
        }
    }


    public static KeyPair keyPair() {
        byte[] pubKey = new byte[SodiumApi.crypto_box_public_key_bytes()];
        byte[] secKey = new byte[SodiumApi.crypto_box_secret_key_bytes()];
        int res = SodiumApi.crypto_box_keypair(pubKey, secKey);
        if (res != 0) {
            throw new RuntimeException("Failed to generate key pair");
        }
        return new KeyPair(pubKey, secKey);
    }

    private static void checkKeyLength(String name, byte[] key, int expectedLength) {
        if (key.length != expectedLength) {
            throw new IllegalArgumentException(
                    "Invalid " + name + " length (" + key.length + ", expected " + expectedLength + ")");
        }
    }

    public static byte[] seal(byte[] message, byte[] pubKey) {
        checkKeyLength("public key", pubKey, SodiumApi.crypto_box_public_key_bytes());
        byte[] cipher = new byte[SodiumApi.crypto_box_seal_bytes() + message.length];
        int res = SodiumApi.crypto_box_seal(cipher, message, message.length, pubKey);
        if (res != 0) {
            throw new RuntimeException("Failed to seal message");
        }
        return cipher;
    }

    public static byte[] sealOpen(byte[] cipher, byte[] pubKey, byte[] secretKey) {
        checkKeyLength("public key", pubKey, SodiumApi.crypto_box_public_key_bytes());
        checkKeyLength("secret key", secretKey, SodiumApi.crypto_box_secret_key_bytes());
        if (cipher.length < SodiumApi.crypto_box_seal_bytes()) {
            throw new RuntimeException(
                    "Invalid encrypted message length (" + cipher.length + ", should be > " + SodiumApi.crypto_box_seal_bytes() + ")");
        }
        byte[] message = new byte[cipher.length - SodiumApi.crypto_box_seal_bytes()];
        int res = SodiumApi.crypto_box_seal_open(message, cipher, cipher.length, pubKey, secretKey);
        if (res != 0) {
            throw new RuntimeException("Failed to open seal");
        }
        return message;
    }
}
