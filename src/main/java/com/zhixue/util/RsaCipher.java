package com.zhixue.util;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 密码传输层 RSA 解密（与前端 src/utils/rsa.ts 配对）。
 *
 * 前端用固定 RSA-2048 公钥做 RSA-OAEP(SHA-256) 加密后传输，
 * 后端用私钥解密还原明文，再交给 PasswordUtil 做 BCrypt 存储。
 * 解密失败时原样返回（兼容明文直传：curl / E2E 脚本 / 降级场景）。
 */
public final class RsaCipher {

    /** PKCS8 私钥（固定密钥对；公钥硬编码在前端 rsa.ts） */
    private static final String PRIVATE_KEY_B64 =
        "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCjfzUkuXXMCVI6148vjY410215oTNJaspE7DKjoZ2PTpuRk1haW1KM0pQVa5P6LdSCn823BqcGmd4ov+Jap4iEylQl3/QGuD9D90St/nI6Ze7+3ilKAYLu6On+GXorVZvgChJ5UgxsDU6m/jVA4emkB2T/ULFDRTZrJ1QYFsZr1mpoBmsubnljGPqE0kBxZOwGgo6mFIVemwa2v3wW7TfAhm2eouVqHYmCUfFg9+Vh01IecMM1aSJYt6TU/Uz/pt8wIvmz+Bkq7kuzTAc9RqUv6RND/sN+2Y2iTa6vlCqSGRC5Eauc+yMJtkI4U6QLNKBCLUk0cYOdg9ZFlwEkwrTdAgMBAAECggEACbThf/1+Elp9UMlVErgp42UwKy1DAQ6qKomxVJ32341bAWYI3qMXAA2icAZz1xcFy6mBdOZxX8bJIIyKe40AYbiCDWuXLmzy3e3zXGHDq1bmPP24itBofcGYiWw+y2LTlGtUqceRaDrUBWhd21fDihL2+ozHsVw4Z7KCihmUDHkqdCl1n1BGaCAXvUMow1qcNfXDJSXDMhmfOxXfXzdyDVKxBjJVqjPHqZOqbmefgD0zvXVJAnUobDGjaZtrsh/CProD8AW5GfmQ0CfGcuVEkoTcWWVp/gCmcBwQgsm2iQ5gdy+i2xPHZM5sn1EhsMLLfTCyNOqGaAn76BG0zS/LsQKBgQDfIItekyrMJN05HkRt3xSzOtv+b4V/NSTPl3o+blAq1LMs0c+TwdACy7QTWqEU4WgAEN1n0Clq7RgKSAy1dhPuf/8BogGesVWvI4V7tBjQRWaqAWVC/fY1J5XVKEIMXXfUsb86R/cr5oq8zmfICMPN2g9iF1EIkT84mha+kdJl1QKBgQC7labRS7D1tm5iGBltpqyr5oINACQKJtHsdpuAdRMXmGb2p95ZTyoPb2pFoFotHj0chIhT0CJH4nJJEYHTsZHc+OS05rl3eq6GRnZgjJBwiW6rkLbz776R26gusyiq2RpUeoBrj5kBZVm5sclQ8ky/2nJQgAZDBMkWjhU63zju6QKBgBU1DaoWiMnQcypboxtiSaBjZsd/KAwYLACDID5Q09POVZ1b78PWdwPdAqeYRJksd2sSL0kPsvATyuYnRPX2XByeE8m+8TJ5HnDACo7gZj9daU2dRlegtccFlSsmYWi1dY9Eua07a9XDWsppP9n2Y03sfOvQ1YXf8Edb9e91Aq9lAoGAReZFDDadUWJSjaEXUKHMNB4oFfoYc+P1U5z64wo9PAyhSYZSN4tlS+0jd6CCGc/ry8WjG0AylgDOv2WTsDO5+9KPPs2/DMOdZxG4qM+YPycnIy1Az3Nl2uo0KHrAS7GsuxAO7IXM6RVAMw8Jym0PpmobMN4T90NAP3RgetiVWYkCgYASgKk/1D5EYdSWKJzpB+pToCU3L7Q3xxoTDMWJYjRWB6gkQo2sqooMoo1xdYZcpJtg94gmcf+f6AkxXCOVhVQQwGaxYPik610oBRGkNd8m5n24mABG/KcvVDzzzX7T6ga6xO86qrY7IuGMuqIoJShZA8qhH2jIge6U6Nf7bw2K9Q==";

    private static PrivateKey privateKey;

    static {
        try {
            byte[] der = Base64.getDecoder().decode(PRIVATE_KEY_B64);
            privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            privateKey = null;
            System.err.println("[RsaCipher] 私钥初始化失败，传输加密不可用: " + e.getMessage());
        }
    }

    private RsaCipher() {}

    /**
     * 若为前端加密密文（RSA-OAEP/SHA-256，Base64）则解密还原明文；
     * 否则原样返回（明文直传兼容）。
     */
    public static String decryptIfEncrypted(String value) {
        if (value == null || value.length() < 100 || privateKey == null) return value;
        try {
            byte[] ct = Base64.getDecoder().decode(value);
            if (ct.length != 256) return value; // RSA-2048 密文恒为 256 字节
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey,
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value; // 非密文/解密失败 → 按明文处理
        }
    }
}
