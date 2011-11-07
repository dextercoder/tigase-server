/*
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2010 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.io;

//~--- non-JDK imports --------------------------------------------------------

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import tigase.cert.CertificateEntry;
import tigase.cert.CertificateUtil;

//~--- classes ----------------------------------------------------------------

/**
 * Created: Oct 15, 2010 2:40:49 PM
 * 
 * @author <a href="mailto:artur.hefczyc@tigase.org">Artur Hefczyc</a>
 * @version $Rev$
 */
public class SSLContextContainer implements SSLContextContainerIfc {

	private static class FakeTrustManager implements X509TrustManager {
		private X509Certificate[] issuers = null;

		// ~--- constructors
		// -------------------------------------------------------

		/**
		 * Constructs ...
		 * 
		 */
		FakeTrustManager() {
		}

		/**
		 * Constructs ...
		 * 
		 * 
		 * @param ai
		 */
		FakeTrustManager(X509Certificate[] ai) {
			issuers = ai;
		}

		// ~--- methods
		// ------------------------------------------------------------

		// Implementation of javax.net.ssl.X509TrustManager

		/**
		 * Method description
		 * 
		 * 
		 * @param x509CertificateArray
		 * @param string
		 * 
		 * @throws CertificateException
		 */
		@Override
		public void checkClientTrusted(final X509Certificate[] x509CertificateArray, final String string)
				throws CertificateException {
		}

		/**
		 * Method description
		 * 
		 * 
		 * @param x509CertificateArray
		 * @param string
		 * 
		 * @throws CertificateException
		 */
		@Override
		public void checkServerTrusted(final X509Certificate[] x509CertificateArray, final String string)
				throws CertificateException {
		}

		// ~--- get methods
		// --------------------------------------------------------

		/**
		 * Method description
		 * 
		 * 
		 * @return
		 */
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return issuers;
		}
	}

	// ~--- fields
	// ---------------------------------------------------------------

	private class PEMFileFilter implements FileFilter {

		/**
		 * Method description
		 * 
		 * 
		 * @param pathname
		 * 
		 * @return
		 */
		@Override
		public boolean accept(File pathname) {
			if (pathname.isFile() && (pathname.getName().endsWith(".pem") || pathname.getName().endsWith(".PEM"))) {
				return true;
			}

			return false;
		}
	}

	private static final Logger log = Logger.getLogger(SSLContextContainer.class.getName());
	public final static String PER_DOMAIN_CERTIFICATE_KEY = "virt-hosts-cert-";
	private ArrayList<X509Certificate> acceptedIssuers = new ArrayList<X509Certificate>(200);
	private File[] certsDirs = null;
	private String def_cert_alias = null;
	private String email = "admin@tigase.org";
	private char[] emptyPass = new char[0];
	private Map<String, KeyManagerFactory> kmfs = new ConcurrentSkipListMap<String, KeyManagerFactory>();
	private String o = "Tigase.org";
	private String ou = "XMPP Service";
	private SecureRandom secureRandom = new SecureRandom();

	// ~--- methods
	// --------------------------------------------------------------

	private Map<String, SSLContext> sslContexts = new ConcurrentSkipListMap<String, SSLContext>();

	// ~--- get methods
	// ----------------------------------------------------------

	private X509TrustManager[] tms = new X509TrustManager[] { new FakeTrustManager() };

	private KeyStore trustKeyStore = null;

	// ~--- methods
	// --------------------------------------------------------------

	private KeyManagerFactory addCertificateEntry(CertificateEntry entry, String alias, boolean store)
			throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
		KeyStore keys = KeyStore.getInstance("JKS");

		keys.load(null, emptyPass);
		keys.setKeyEntry(alias, entry.getPrivateKey(), emptyPass, entry.getCertChain());

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

		kmf.init(keys, emptyPass);
		kmfs.put(alias, kmf);

		if (store) {
			CertificateUtil.storeCertificate(new File(certsDirs[0], alias + ".pem").toString(), entry);
		}

		return kmf;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param params
	 * 
	 * @throws CertificateParsingException
	 */
	@Override
	public void addCertificates(Map<String, String> params) throws CertificateParsingException {
		String pemCert = params.get(PEM_CERTIFICATE_KEY);
		String saveToDiskVal = params.get(CERT_SAVE_TO_DISK_KEY);
		boolean saveToDisk = (saveToDiskVal != null) && saveToDiskVal.equalsIgnoreCase("true");
		final String alias = params.get(CERT_ALIAS_KEY);

		if (alias == null) {
			throw new RuntimeException("Certificate alias must be specified");
		}

		if (pemCert != null) {
			try {
				CertificateEntry entry = CertificateUtil.parseCertificate(new CharArrayReader(pemCert.toCharArray()));

				addCertificateEntry(entry, alias, saveToDisk);
				sslContexts.remove(alias);
			} catch (Exception ex) {
				throw new CertificateParsingException("Problem adding a new certificate.", ex);
			}
		}
	}

	// ~--- get methods
	// ----------------------------------------------------------

	private Map<String, File> findPredefinedCertificates(Map<String, Object> params) {
		final Map<String, File> result = new HashMap<String, File>();
		if (params == null)
			return result;

		Iterator<String> it = params.keySet().iterator();
		while (it.hasNext()) {
			String t = it.next();
			if (t.startsWith(PER_DOMAIN_CERTIFICATE_KEY)) {
				String domainName = t.substring(PER_DOMAIN_CERTIFICATE_KEY.length());
				File f = new File(params.get(t).toString());

				result.put(domainName, f);
			}
		}

		return result;

	}

	// ~--- inner classes
	// --------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param protocol
	 * @param hostname
	 * 
	 * @return
	 */
	@Override
	public SSLContext getSSLContext(String protocol, String hostname) {
		SSLContext sslContext = null;

		String alias = hostname;

		try {

			if (alias == null) {
				alias = def_cert_alias;
			} // end of if (hostname == null)

			sslContext = sslContexts.get(alias);

			if (sslContext == null) {
				KeyManagerFactory kmf = kmfs.get(alias);

				if (kmf == null) {
					KeyPair keyPair = CertificateUtil.createKeyPair(1024, "secret");
					X509Certificate cert = CertificateUtil.createSelfSignedCertificate(email, alias, ou, o, null, null, null,
							keyPair);
					CertificateEntry entry = new CertificateEntry();

					entry.setPrivateKey(keyPair.getPrivate());
					entry.setCertChain(new Certificate[] { cert });
					kmf = addCertificateEntry(entry, alias, true);
					log.log(Level.WARNING, "Auto-generated certificate for domain: {0}", alias);
				}

				sslContext = SSLContext.getInstance(protocol);
				sslContext.init(kmf.getKeyManagers(), tms, secureRandom);
				sslContexts.put(alias, sslContext);
			}
		} catch (Exception e) {
			log.log(Level.SEVERE, "Can not initialize SSLContext for domain: " + alias + ", protocol: " + protocol, e);
			sslContext = null;
		}

		return sslContext;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public KeyStore getTrustStore() {
		return trustKeyStore;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param params
	 */
	@Override
	public void init(Map<String, Object> params) {
		try {
			def_cert_alias = (String) params.get(DEFAULT_DOMAIN_CERT_KEY);

			if (def_cert_alias == null) {
				def_cert_alias = DEFAULT_DOMAIN_CERT_VAL;
			}

			String pemD = (String) params.get(SERVER_CERTS_LOCATION_KEY);

			if (pemD == null) {
				pemD = SERVER_CERTS_LOCATION_VAL;
			}

			String[] pemDirs = pemD.split(",");

			certsDirs = new File[pemDirs.length];

			int certsDirsIdx = -1;

			Map<String, File> predefined = findPredefinedCertificates(params);
			log.log(Level.CONFIG, "Loading predefined server certificates");
			for (final Entry<String, File> entry : predefined.entrySet()) {
				try {
					CertificateEntry certEntry = CertificateUtil.loadCertificate(entry.getValue());
					String alias = entry.getKey();
					addCertificateEntry(certEntry, alias, false);
					log.log(Level.CONFIG, "Loaded server certificate for domain: {0} from file: {1}", new Object[] { alias,
							entry.getValue() });
				} catch (Exception ex) {
					log.log(Level.WARNING, "Cannot load certficate from file: " + entry.getValue(), ex);
				}
			}

			for (String pemDir : pemDirs) {
				log.log(Level.CONFIG, "Loading server certificates from PEM directory: {0}", pemDir);
				certsDirs[++certsDirsIdx] = new File(pemDir);

				for (File file : certsDirs[certsDirsIdx].listFiles(new PEMFileFilter())) {
					try {
						CertificateEntry certEntry = CertificateUtil.loadCertificate(file);
						String alias = file.getName();
						if (alias.endsWith(".pem"))
							alias = alias.substring(0, alias.length() - 4);

						addCertificateEntry(certEntry, alias, false);
						log.log(Level.CONFIG, "Loaded server certificate for domain: {0} from file: {1}", new Object[] { alias,
								file });
					} catch (Exception ex) {
						log.log(Level.WARNING, "Cannot load certficate from file: " + file, ex);
					}
				}
			}
		} catch (Exception ex) {
			log.log(Level.WARNING, "There was a problem initializing SSL certificates.", ex);
		}

		String trustLoc = (String) params.get(TRUSTED_CERTS_DIR_KEY);

		if (trustLoc == null) {
			trustLoc = TRUSTED_CERTS_DIR_VAL;
		}

		final String[] trustLocations = trustLoc.split(",");

		// It may take a while, let's do it in background
		new Thread() {
			@Override
			public void run() {
				loadTrustedCerts(trustLocations);
			}
		}.start();
	}

	private void loadTrustedCerts(String[] trustLocations) {
		int counter = 0;
		long start = System.currentTimeMillis();

		try {
			log.log(Level.CONFIG, "Loading trustKeyStore from locations: {0}", Arrays.toString(trustLocations));
			trustKeyStore = KeyStore.getInstance("JKS");
			trustKeyStore.load(null, emptyPass);

			for (String location : trustLocations) {
				File root = new File(location);
				File[] files = root.listFiles(new PEMFileFilter());

				if (files != null) {
					for (File file : files) {
						try {
							CertificateEntry certEntry = CertificateUtil.loadCertificate(file);
							Certificate[] chain = certEntry.getCertChain();

							if (chain != null) {
								for (Certificate cert : chain) {
									if (cert instanceof X509Certificate) {
										X509Certificate crt = (X509Certificate) cert;
										String alias = crt.getSubjectX500Principal().getName();

										trustKeyStore.setCertificateEntry(alias, crt);
										acceptedIssuers.add(crt);
										log.log(Level.FINEST, "Imported certificate: {0}", alias);
										++counter;
									}
								}
							}
						} catch (Exception e) {
							log.log(Level.WARNING, "Problem loading certificate from file: {0}", file);
						}
					}
				}
			}
		} catch (Exception ex) {
			log.log(Level.WARNING, "An error loading trusted certificates from locations: " + Arrays.toString(trustLocations),
					ex);
		}

		tms = new X509TrustManager[] { new FakeTrustManager(
				acceptedIssuers.toArray(new X509Certificate[acceptedIssuers.size()])) };

		long seconds = (System.currentTimeMillis() - start) / 1000;

		log.log(Level.CONFIG, "Loaded {0} trust certificates, it took {1} seconds.", new Object[] { counter, seconds });
	}
}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
