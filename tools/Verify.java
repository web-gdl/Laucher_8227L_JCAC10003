import com.android.apksig.ApkVerifier; import java.io.File; import java.security.*; import java.security.cert.X509Certificate; import java.util.HexFormat;
public class Verify { public static void main(String[] a) throws Exception {
  ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[0])).setMinCheckedPlatformVersion(26).build().verify();
  System.out.println("verified=" + r.isVerified());
  for (X509Certificate c : r.getSignerCertificates()) System.out.println("signer sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())).toUpperCase());
}}
