import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

public class Sign {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]), out = new File(a[1]);
        byte[] pk8 = Files.readAllBytes(Paths.get(a[2]));
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pk8));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(Files.newInputStream(Paths.get(a[3])));
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("platform", key, List.of(cert)).build();
        new ApkSigner.Builder(List.of(sc)).setInputApk(in).setOutputApk(out)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false)
            .setMinSdkVersion(26).build().sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(out).setMinCheckedPlatformVersion(26).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme());
        for (X509Certificate c : r.getSignerCertificates())
            System.out.println("signer sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())).toUpperCase());
    }
}
