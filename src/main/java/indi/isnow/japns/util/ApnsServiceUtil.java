package indi.isnow.japns.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class ApnsServiceUtil {
	private static final String KEYSTORE_TYPE = "PKCS12";
	private static final String KEY_ALGORITHM = ((java.security.Security
			.getProperty("ssl.KeyManagerFactory.algorithm") == null) ? "sunx509"
			: java.security.Security
					.getProperty("ssl.KeyManagerFactory.algorithm"));
	
	public static final String SANDBOX_GATEWAY_HOST = "gateway.sandbox.push.apple.com";
    public static final int SANDBOX_GATEWAY_PORT = 2195;

    public static final String SANDBOX_FEEDBACK_HOST = "feedback.sandbox.push.apple.com";
    public static final int SANDBOX_FEEDBACK_PORT = 2196;

    public static final String PRODUCTION_GATEWAY_HOST = "gateway.push.apple.com";
    public static final int PRODUCTION_GATEWAY_PORT = 2195;

    public static final String PRODUCTION_FEEDBACK_HOST = "feedback.push.apple.com";
    public static final int PRODUCTION_FEEDBACK_PORT = 2196;

    public static final int MAX_PAYLOAD_LENGTH = 2048;

	public static SSLContext getContext(final KeyStore keyStore,
			final String password, final TrustManagerFactory tmf,
			final KeyManagerFactory km) throws NoSuchAlgorithmException,
			UnrecoverableKeyException, KeyStoreException,
			KeyManagementException {

		SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(km.getKeyManagers(), tmf.getTrustManagers(), null);

		return sslContext;
	}

	public static KeyStore getKeyStore(final String fileName, final String password)
			throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
		InputStream in;
		KeyStore ks = null;
		try {
			in = new FileInputStream(fileName);
			ks = KeyStore.getInstance("PKCS12");
			ks.load(in, password.toCharArray());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ks;
	}

	public static TrustManagerFactory getTrustManagerFactory()
			throws NoSuchAlgorithmException, KeyStoreException {
		TrustManagerFactory tmf = TrustManagerFactory
				.getInstance(KEY_ALGORITHM);
		tmf.init((KeyStore) null);
		return tmf;
	}

	public static KeyManagerFactory getKeyManagerFactory(final KeyStore ks,
			final String password) throws NoSuchAlgorithmException,
			UnrecoverableKeyException, KeyStoreException {
		KeyManagerFactory km = KeyManagerFactory.getInstance(KEY_ALGORITHM);
		km.init(ks, password.toCharArray());
		return km;
	}
}
