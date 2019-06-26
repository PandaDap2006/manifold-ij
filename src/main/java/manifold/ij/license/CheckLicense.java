package manifold.ij.license;

import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.ui.LicensingFacade;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;


/**
 * @author Eugene Zhuravlev
 * Date: 26-Jul-18
 */
public class CheckLicense
{
  /**
   * PRODUCT_CODE must be the same specified in plugin.xml inside the <productCode> tag
   */
  private static final String PRODUCT_CODE = "PMANIFOLD";
  private static final String KEY_PREFIX = "key:";
  private static final String STAMP_PREFIX = "stamp:";

  /**
   * Public root certificates needed to verify JetBrains-signed licenses
   */
  private static final String[] ROOT_CERTIFICATES = new String[]{
    "-----BEGIN CERTIFICATE-----\n" +
    "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\n" +
    "-----END CERTIFICATE-----\n",
    "-----BEGIN CERTIFICATE-----\n" +
    "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\n" +
    "7tlDFnvaRnQukqSG+VqNWg==\n" +
    "-----END CERTIFICATE-----"
  };

  private static final long SECOND = 1000;
  private static final long MINUTE = 60 * SECOND;
  private static final long HOUR = 60 * MINUTE;
  private static final long TIMESTAMP_VALIDITY_PERIOD_MS = 1 * HOUR;  // configure period that suits your needs better


  public static boolean isLicensed() {
    final LicensingFacade facade = LicensingFacade.getInstance();
    if (facade == null) {
      return false;
    }
    final String cstamp = facade.getConfirmationStamp(PRODUCT_CODE);
    if (cstamp == null) {
      return false;
    }
    if (cstamp.startsWith(KEY_PREFIX)) {
      // the license is obtained via JetBrainsAccount or entered as an activation code
      return isKeyValid(cstamp.substring(KEY_PREFIX.length()));
    }
    if (cstamp.startsWith(STAMP_PREFIX)) {
      // licensed via ticket obtained from JetBrains Floating License Server
      return isLicenseServerStampValid(cstamp.substring(STAMP_PREFIX.length()));
    }
    return false;
  }

  private static boolean isKeyValid(String key) {
    String[] licenseParts = key.split("-");
    if (licenseParts.length !=  4) {
      return false; // invalid format
    }

    final String licenseId = licenseParts[0];
    final String licensePartBase64 = licenseParts[1];
    final String signatureBase64 = licenseParts[2];
    final String certBase64 = licenseParts[3];

    try {
      final Signature sig = Signature.getInstance("SHA1withRSA");
      sig.initVerify(createCertificate(
        Base64.getMimeDecoder().decode(certBase64.getBytes(StandardCharsets.UTF_8)), Collections.emptySet(), false
      ));
      final byte[] licenseBytes = Base64.getMimeDecoder().decode(licensePartBase64.getBytes(StandardCharsets.UTF_8));
      sig.update(licenseBytes);
      if (!sig.verify(Base64.getMimeDecoder().decode(signatureBase64.getBytes(StandardCharsets.UTF_8)))) {
        return false;
      }
      // Optional additional check: the licenseId corresponds to the licenseId encoded in the signed license data
      // The following is a 'least-effort' code. It would be more accurate to parse json and then find there the value of the attribute "licenseId"
      final String licenseData = new String(licenseBytes, Charset.forName("UTF-8"));
      return licenseData.contains("\"licenseId\":\"" + licenseId + "\"");
    }
    catch (Throwable ignored) {
    }
    return false;
  }

  private static boolean isLicenseServerStampValid(String serverStamp) {
    try {
      final String[] parts = serverStamp.split(":");
      final Base64.Decoder base64 = Base64.getMimeDecoder();
      
      final long timeStamp = Long.parseLong(parts[0]);
      final String machineId = parts[1];
      final String signatureType = parts[2];
      final byte[] signatureBytes = base64.decode(parts[3].getBytes(StandardCharsets.UTF_8));
      final byte[] certBytes = base64.decode(parts[4].getBytes(StandardCharsets.UTF_8));
      final Collection<byte[]> intermediate = new ArrayList<byte[]>();
      for (int idx = 5; idx < parts.length; idx++) {
        intermediate.add(base64.decode(parts[idx].getBytes(StandardCharsets.UTF_8)));
      }

      final Signature sig = Signature.getInstance(signatureType);
      sig.initVerify(createCertificate(certBytes, intermediate, false));
      sig.update((parts[0] + ":" + parts[1]).getBytes(StandardCharsets.UTF_8));
      if (sig.verify(signatureBytes)) {
        final String thisMachineId = PermanentInstallationID.get();
        // machineId must match the machineId from the server reply and
        // server reply should be relatively 'fresh'
        return thisMachineId.equals(machineId) && Math.abs(System.currentTimeMillis() - timeStamp) < TIMESTAMP_VALIDITY_PERIOD_MS;
      }
    }
    catch (Throwable ignored) {
      // consider serverStamp invalid
    }
    return false;
  }

  @NotNull
  private static X509Certificate createCertificate(byte[] certBytes, Collection<byte[]> intermediateCertsBytes, boolean checkValidityAtCurrentDate) throws Exception {
    final CertificateFactory x509factory = CertificateFactory.getInstance("X.509");
    final X509Certificate cert = (X509Certificate) x509factory.generateCertificate(new ByteArrayInputStream(certBytes));


    final Collection<Certificate> allCerts = new HashSet<>();
    allCerts.add(cert);
    for (byte[] bytes : intermediateCertsBytes) {
      allCerts.add(x509factory.generateCertificate(new ByteArrayInputStream(bytes)));
    }

    // Create the selector that specifies the starting certificate
    final X509CertSelector selector = new X509CertSelector();
    selector.setCertificate(cert);
    // Configure the PKIX certificate builder algorithm parameters
    final Set<TrustAnchor> trustAchors = new HashSet<>();
    for (String rc : ROOT_CERTIFICATES) {
      trustAchors.add(new TrustAnchor(
        (X509Certificate) x509factory.generateCertificate(new ByteArrayInputStream(rc.getBytes(StandardCharsets.UTF_8))), null
      ));
    }

    final PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(trustAchors, selector);
    pkixParams.setRevocationEnabled(false);
    if (!checkValidityAtCurrentDate) {
      // deliberately check validity on the start date of cert validity period, so that we do not depend on
      // the actual moment when the check is performed
      pkixParams.setDate(cert.getNotBefore());
    }
    pkixParams.addCertStore(
      CertStore.getInstance("Collection", new CollectionCertStoreParameters(allCerts))
    );
    // Build and verify the certification chain
    if (CertPathBuilder.getInstance("PKIX").build(pkixParams).getCertPath() == null) {
      throw new Exception ("Certificate used to sign the license is not signed by JetBrains root certificate");
    }

    return cert;
  }


}
