/**! -*- Mode: Java; c-basic-offset: 4 -*-
 *
 * Copyright (c) 1999 by Rasmus Sten <rasmus@sno.pp.se>
 *
 */
// -*- Mode: Java; c-basic-offset: 4 -*-
package nu.dll.lyskom;

import java.net.*;
import java.io.*;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.LinkedList;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

class Connection {

    boolean debug = Boolean.getBoolean("lattekom.debug-writer");

    private LinkedList<byte[]> writeQueue = new LinkedList<byte[]>();
    private Socket sock;
    private InputStream input;
    private OutputStream output;
    private Session session;
    String server;
    int port;
    boolean ssl_connection = true;
    int cert_level = 0;
    
    Thread queuedWriter = null;
    boolean keepRunning = true;

    public Connection(Session session, InputStream root_stream) throws IOException, UnknownHostException {
        this.session = session;
        this.server = session.getServer();
        this.port = session.getPort();
        this.ssl_connection = session.getUseSSL();
        this.cert_level = session.getCertLevel();
        
        if (ssl_connection) {
            Debug.println("Trying to make SSL Connection");
            javax.net.ssl.SSLSocketFactory factory = null;

            switch (cert_level) {
            case 0:
                // TO ONLY TRUST CERTS TRACABLE TO DEFAULT ROOT CERTS
                Debug.println("Allow only signed cert");
                factory = HttpsURLConnection.getDefaultSSLSocketFactory();
                break;
            case 1:
                // TO TRUST EMBEDDED ROOT CERTS
                Debug.println("Allow embedded cert");
                factory = newSslSocketFactory(root_stream);
                break;
            case 2:
                // FOR TESTING: TO TRUST ANY CERTIFICATE
                Debug.println("Use any cert");
                X509TrustManager[] trustAllCerts = (X509TrustManager[]) new X509TrustManager[] { new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs,
                            String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs,
                            String authType) {
                    }
                } };

                // Install the all-trusting trust manager
                try {
                    SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init(null, trustAllCerts,
                            new java.security.SecureRandom());
                    factory = sc.getSocketFactory();
                } catch (Exception e) {
                    Debug.println("Error creating factory:" + e);
                }
                // factory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                // END BLOCK FOR TESTING: TO TRUST ANY CERTIFICATE
            }
            SSLSocket ssl_sock = null;
            Debug.println("Creating a SSL Socket For " + server
                    + " on port " + port);
            ssl_sock = (SSLSocket) factory.createSocket(server, port);
            
            /**
             * Starts an SSL handshake on this connection. Common reasons
             * include a need to use new encryption keys, to change cipher
             * suites, or to initiate a new session. To force complete
             * reauthentication, the current session could be invalidated before
             * starting this handshake. If data has already been sent on the
             * connection, it continues to flow during this handshake. When the
             * handshake completes, this will be signaled with an event. This
             * method is synchronous for the initial handshake on a connection
             * and returns when the negotiated handshake is complete. Some
             * protocols may not support multiple handshakes on an existing
             * socket and may throw an IOException.
             */

            ssl_sock.startHandshake();
            System.out.println("Handshaking Complete");

            /**
             * Retrieve the server's certificate chain
             * 
             * Returns the identity of the peer which was established as part of
             * defining the session. Note: This method can be used only when
             * using certificate-based cipher suites; using it with
             * non-certificate-based cipher suites, such as Kerberos, will throw
             * an SSLPeerUnverifiedException.
             * 
             * 
             * Returns: an ordered array of peer certificates, with the peer's
             * own certificate first followed by any certificate authorities.
             */
            Certificate[] serverCerts = ssl_sock.getSession().getPeerCertificates();
            Debug.println("Retreived Server's Certificate Chain");

            Debug.println(serverCerts.length + " certificates found\n\n\n");
            for (int i = 0; i < serverCerts.length; i++) {
                Certificate myCert = serverCerts[i];
                Debug.println("====Certificate:" + (i + 1) + "====");
                Debug.println("-Public Key-\n" + myCert.getPublicKey());
                //Debug.println("-Public Key-\n" + myCert.toString());
                Debug.println("-Certificate Type-\n " + myCert.getType());

                Debug.println("");
            }
            sock = ssl_sock;
        } else {
            Debug.println("Connect without encryption");
            sock = new Socket(server, port);
        }
        if(sock == null) {
            Debug.println("Sock is null!");
            return;
        }
        input = sock.getInputStream();
        output = sock.getOutputStream();

        queuedWriter = new Thread(new Runnable() {
            public void run() {
                Debug.println("Queued writer start.");
                while (keepRunning) {
                    try {
                        synchronized (writeQueue) {
                            if (writeQueue.isEmpty()) {
                                if (debug)
                                    Debug.println("Write queue empty.");
                                writeQueue.wait();
                            }
                            synchronized (output) {
                                while (!writeQueue.isEmpty()) {
                                    byte[] bytes = (byte[]) writeQueue
                                            .removeFirst();
                                    output.write(bytes);
                                    if (Debug.ENABLED) {
                                        String s;
                                        if (bytes[bytes.length - 1] == '\n') {
                                            s = new String(bytes, 0,
                                                    bytes.length - 1);
                                        } else {
                                            s = new String(bytes);
                                        }
                                        if (debug)
                                            Debug.println("wrote: " + s);
                                    }
                                }
                            }
                        }

                    } catch (IOException ex1) {
                        Debug.println("I/O error during write: "
                                + ex1.getMessage());
                        keepRunning = false;
                    } catch (InterruptedException ex2) {
                        Debug.println("Interrupted during wait(): "
                                + ex2.getMessage());
                    }
                }
                Debug.println("Queued writer exit.");
            }
        });
        queuedWriter.setName("QueuedWriter-" + writerThreadCount++);
        queuedWriter.setDaemon(true);
        queuedWriter.start();

    }

    static int writerThreadCount = 0;

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public void close() throws IOException {
        sock.close();
        keepRunning = false;
        queuedWriter.interrupt();
    }

    public InputStream getInputStream() {
        return input;
    }

    public OutputStream getOutputStream() {
        return output;
    }

    public void queuedWrite(String s) throws IOException {
        synchronized (writeQueue) {
            if (!keepRunning) {
                throw new IllegalStateException(
                        "Connection has been terminated.");
            }
            if (session.listener.getException() != null) {
                Exception ex1 = session.listener.getException();
                throw new IOException("Exception in listener: "
                        + ex1.toString());
            }
            try {
                writeQueue.addLast(s.getBytes(session.serverEncoding));
            } catch (UnsupportedEncodingException ex1) {
                throw new RuntimeException("Unsupported server encoding: "
                        + ex1.getMessage());
            }
            writeQueue.notifyAll();
        }
    }

    public void queuedWrite(byte[] b) {
        synchronized (writeQueue) {
            writeQueue.addLast(b);
            writeQueue.notifyAll();
        }
    }

    /**
     * 
     * @deprecated use writeLine() or queuedWrite() instead
     */
    public void write(char c) throws IOException {
        synchronized (output) {
            output.write(c);
        }
    }

    public void writeLine(byte[] b) throws IOException {
        synchronized (output) {
            output.write(b);
            output.write('\n');
        }
    }

    public void writeLine(String s) throws IOException {
        synchronized (output) {
            try {
                byte[] bytes = s.getBytes(session.serverEncoding);
                byte[] line;
                // append \n if necessary
                if (bytes[bytes.length - 1] != '\n') {
                    line = new byte[bytes.length + 1];
                    System.arraycopy(bytes, 0, line, 0, bytes.length);
                    line[bytes.length] = (byte) '\n';
                } else {
                    line = bytes;
                }
                output.write(line);
            } catch (UnsupportedEncodingException ex1) {
                throw new RuntimeException("Unsupported server encoding: "
                        + ex1.getMessage());
            }
            output.write('\n');
        }
    }

    /*
     * Reads until "\n" is encountered.
     */

    public String readLine(String s) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(80);
        byte b = (byte) input.read();
        while (b != -1 && b != '\n') {
            os.write(b);
            b = (byte) input.read();
        }

        switch (b) {
        case -1:
            Debug.println("Connection.readLine(): EOF from stream");
            break;
        case 0:
            Debug.println("Connection.readLine(): \\0 from stream");
            break;
        }

        return new String(os.toByteArray(), session.serverEncoding);
    }
    
    private SSLSocketFactory newSslSocketFactory(InputStream in) {
        KeyStore ks = null;
        SSLSocketFactory factory = null;
        try {
            ks = KeyStore.getInstance("BKS");

            // get user password and file input stream
            char[] password = ("2fa190f4McD").toCharArray();
            // Get the raw resource, which contains the keystore with your
            // trusted certificates (root and any intermediate certs)
            ks.load(in, password);
            in.close();

            SSLContext sc = null;
            sc = SSLContext.getInstance("TLS");
            KeyManagerFactory kmf = null;
            kmf = KeyManagerFactory.getInstance("X509");
            TrustManagerFactory tmf = null;
            tmf = TrustManagerFactory.getInstance("X509");

            kmf.init(ks, password);
            tmf.init(ks);

            sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            factory = sc.getSocketFactory();
        } catch (KeyManagementException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CertificateException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (KeyStoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return factory;
    }

}
