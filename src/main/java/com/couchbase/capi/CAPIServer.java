package com.couchbase.capi;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.couchbase.capi.servlet.BucketMapServlet;
import com.couchbase.capi.servlet.CAPIServlet;
import com.couchbase.capi.servlet.ClusterMapServlet;

public class CAPIServer extends Server {

    private String publishAddress;

    private CAPIBehavior capiBehavior;
    private CouchbaseBehavior couchbaseBehavior;

    public CAPIServer(CAPIBehavior capiBehavior, CouchbaseBehavior couchbaseBehavior) {
        this(capiBehavior, couchbaseBehavior, 0);
    }

    public CAPIServer(CAPIBehavior capiBehavior, CouchbaseBehavior couchbaseBehavior, int port) {
        this(capiBehavior, couchbaseBehavior, new InetSocketAddress("0.0.0.0", port));
    }

    public CAPIServer(CAPIBehavior capiBehavior, CouchbaseBehavior couchbaseBehavior, InetSocketAddress bindAddress) {
        super(bindAddress);

        this.capiBehavior = capiBehavior;
        this.couchbaseBehavior = couchbaseBehavior;

        ServletContextHandler context = new ServletContextHandler(
                ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        setHandler(context);

        context.addServlet(new ServletHolder(new ClusterMapServlet(couchbaseBehavior)),
                "/pools/*");
        context.addServlet(new ServletHolder(new BucketMapServlet(
                couchbaseBehavior)), "/pools/default/buckets/*");
        context.addServlet(
                new ServletHolder(new CAPIServlet(capiBehavior)), "/*");
    }

    public int getPort() {
        Connector[] connectors = getConnectors();
        if(connectors.length < 1) {
            throw new IllegalStateException("Cannot get port, there are no connectors");
        }
        Connector connector = connectors[0];
        return connector.getLocalPort();
    }

    /**
     * Returns the first IPv4 address we find
     *
     * @return
     */
    protected String guessPublishAddress() {
        NetworkInterface ni;
        try {
            ni = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());
        } catch (Exception e) {
            return null;
        }

        Enumeration ia = ni.getInetAddresses();
        while (ia.hasMoreElements()) {
            InetAddress elem = (InetAddress) ia.nextElement();
            if (elem instanceof Inet4Address) {
                return elem.getHostAddress();
            }
        }
        return null;
    }

    public URI getCAPIAddress() {
        if(publishAddress == null) {
            publishAddress = guessPublishAddress();
        }
        try {
            return new URI(String.format("http://%s:%d/", publishAddress,
                    getPort()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public String getPublishAddress() {
        return publishAddress;
    }

    public void setPublishAddress(String publishAddress) {
        this.publishAddress = publishAddress;
    }
}