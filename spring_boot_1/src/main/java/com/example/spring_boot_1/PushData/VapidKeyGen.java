package com.example.spring_boot_1.PushData;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * VAPID 키 발급 유틸 — 한 번만 실행해서 결과를 환경변수에 넣고 끝.
 *
 * 출력 형식 (둘 다 base64url no-padding):
 *   - APP_VAPID_PUBLIC : 65-byte 비압축 EC 포인트 (0x04 || X || Y), secp256r1
 *   - APP_VAPID_PRIVATE: 32-byte raw scalar
 */
public class VapidKeyGen {
    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDH", "BC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();

        byte[] pubBytes = encodeUncompressedEcPoint(pub.getW());
        byte[] privBytes = toFixedBytes(priv.getS(), 32);

        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        System.out.println("APP_VAPID_PUBLIC=" + enc.encodeToString(pubBytes));
        System.out.println("APP_VAPID_PRIVATE=" + enc.encodeToString(privBytes));
    }

    private static byte[] encodeUncompressedEcPoint(ECPoint point) {
        byte[] x = toFixedBytes(point.getAffineX(), 32);
        byte[] y = toFixedBytes(point.getAffineY(), 32);
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(x, 0, out, 1, 32);
        System.arraycopy(y, 0, out, 33, 32);
        return out;
    }

    private static byte[] toFixedBytes(BigInteger n, int size) {
        byte[] raw = n.toByteArray();
        if (raw.length == size) return raw;
        byte[] out = new byte[size];
        if (raw.length > size) {
            // BigInteger 는 부호용 leading 0x00 이 붙을 수 있음
            System.arraycopy(raw, raw.length - size, out, 0, size);
        } else {
            System.arraycopy(raw, 0, out, size - raw.length, raw.length);
        }
        return out;
    }
}
