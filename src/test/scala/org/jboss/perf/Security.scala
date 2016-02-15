package org.jboss.perf

import java.math.BigInteger
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security._
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

import org.keycloak.common.util.Base64
import sun.security.x509._

import scala.util.Random

/**
  * @author Radim Vansa &lt;rvansa@redhat.com&gt;
  */
object Security {
  // not really secure; gives deterministic numbers
  private val secureRandom = new SecureRandom(new SecureRandomSpi {
    val random = new Random(1234L)
    override def engineGenerateSeed(numBytes: Int): Array[Byte] = {
      val bytes = new Array[Byte](numBytes)
      random.nextBytes(bytes)
      bytes
    }

    override def engineSetSeed(seed: Array[Byte]) {}

    override def engineNextBytes(bytes: Array[Byte]) {
      random.nextBytes(bytes)
    }

  }, new SecureRandom().getProvider) {}
  private val algorithm = "RSA"
  private val signingAlgorithm = "SHA256WithRSA"
  private val keyPair: KeyPair = {
    val generator = KeyPairGenerator.getInstance(algorithm)
    generator.initialize(2048, secureRandom)
    generator.genKeyPair()
  }

  val PublicKey =  Base64.encodeBytes(KeyFactory.getInstance(algorithm).getKeySpec(keyPair.getPublic, classOf[X509EncodedKeySpec]).getEncoded)
  val PrivateKey = Base64.encodeBytes(KeyFactory.getInstance(algorithm).getKeySpec(keyPair.getPrivate, classOf[PKCS8EncodedKeySpec]).getEncoded)
  val Certificate = Base64.encodeBytes(generateCertificate("CN=Benchmark", keyPair).getEncoded)

  private def generateCertificate(dn: String, pair: KeyPair): X509Certificate = {
    val info = new X509CertInfo();
    val from = new java.util.Date();
    val to = new java.util.Date(from.getTime() + TimeUnit.DAYS.toMillis(365));
    val interval = new CertificateValidity(from, to);
    val sn = new BigInteger(64, secureRandom);
    val owner = new X500Name(dn);

    info.set(X509CertInfo.VALIDITY, interval);
    info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(sn));
    info.set(X509CertInfo.SUBJECT, owner);
    info.set(X509CertInfo.ISSUER, owner);
//    Use following for Java < 1.8:
//    info.set(X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
//    info.set(X509CertInfo.ISSUER, new CertificateIssuerName(owner));
    info.set(X509CertInfo.KEY, new CertificateX509Key(pair.getPublic()));
    info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
    var algo = new AlgorithmId(AlgorithmId.md5WithRSAEncryption_oid);
    info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

    // Sign the cert to identify the algorithm that's used.
    var cert = new X509CertImpl(info);
    cert.sign(pair.getPrivate, signingAlgorithm);

    // Update the algorith, and resign.
    algo = cert.get(X509CertImpl.SIG_ALG).asInstanceOf[AlgorithmId];
    info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
    cert = new X509CertImpl(info);
    cert.sign(pair.getPrivate, signingAlgorithm);
    return cert;
  }
}
