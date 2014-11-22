package indi.isnow.japns.japns;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	SocketFactory factory = null;
    	try {
			Socket socket = factory.createSocket("", 8080);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        System.out.println( "Hello World!" );
    }
    
    public static SSLContext newSSLContext(final InputStream cert, final String password,
            final String ksType, final String ksAlgorithm) throws InvalidSSLConfig {
           try {
               final KeyStore ks = KeyStore.getInstance(ksType);
               ks.load(cert, password.toCharArray());
               return newSSLContext(ks, password, ksAlgorithm);
           } catch (final Exception e) {
               throw new InvalidSSLConfig(e);
           }
       }
    
    public static SSLContext newSSLContext(final KeyStore ks, final String password,
            final String ksAlgorithm) throws InvalidSSLConfig {
           try {
               // Get a KeyManager and initialize it
               final KeyManagerFactory kmf = KeyManagerFactory.getInstance(ksAlgorithm);
               kmf.init(ks, password.toCharArray());

               // Get a TrustManagerFactory with the DEFAULT KEYSTORE, so we have all
               // the certificates in cacerts trusted
               final TrustManagerFactory tmf = TrustManagerFactory.getInstance(ksAlgorithm);
               tmf.init((KeyStore)null);

               // Get the SSLContext to help create SSLSocketFactory
               final SSLContext sslContext = SSLContext.getInstance("TLS");
               sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
               return sslContext;
           } catch (final GeneralSecurityException e) {
               throw new InvalidSSLConfig(e);
           }
       }

}
