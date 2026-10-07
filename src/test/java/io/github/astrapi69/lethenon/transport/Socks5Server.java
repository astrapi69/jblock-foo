/*
 * The MIT License
 *
 * Copyright (C) 2015 Asterios Raptis
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.astrapi69.lethenon.transport;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A SOCKS5 server for tests (RFC 1928, CONNECT without authentication) that stands in for a Tor
 * daemon: it records every request with its address type, and forwards only the hosts it was given
 * a route for, so a test can tell what reached the proxy and in which form
 */
public final class Socks5Server implements Closeable
{

	/** RFC 1928 address types */
	public static final int IPV4 = 1;

	public static final int DOMAIN_NAME = 3;

	public static final int IPV6 = 4;

	/**
	 * One CONNECT as it reached the proxy
	 *
	 * @param addressType
	 *            {@link #IPV4}, {@link #DOMAIN_NAME} or {@link #IPV6}
	 * @param host
	 *            the host as sent: a name, or the address in text
	 * @param port
	 *            the port
	 */
	public record Request(int addressType, String host, int port)
	{
	}

	private final ServerSocket server;

	private final Map<String, Integer> routes = new ConcurrentHashMap<>();

	private final List<Request> requests = new CopyOnWriteArrayList<>();

	private final ExecutorService threads = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "socks5-test-server");
		thread.setDaemon(true);
		return thread;
	});

	private Socks5Server(final ServerSocket server)
	{
		this.server = server;
	}

	/**
	 * Starts a server on a free loopback port
	 */
	public static Socks5Server start() throws IOException
	{
		Socks5Server socks = new Socks5Server(
			new ServerSocket(0, 50, InetAddress.getLoopbackAddress()));
		socks.threads.submit(socks::accept);
		return socks;
	}

	/**
	 * Forwards CONNECTs to the given host and port to the given port on loopback
	 */
	public Socks5Server route(final String host, final int port, final int localPort)
	{
		routes.put(host.toLowerCase(java.util.Locale.ROOT) + ":" + port, localPort);
		return this;
	}

	public PeerAddress address()
	{
		return new PeerAddress("127.0.0.1", server.getLocalPort());
	}

	public List<Request> requests()
	{
		return List.copyOf(requests);
	}

	private void accept()
	{
		while (!server.isClosed())
		{
			try
			{
				Socket client = server.accept();
				threads.submit(() -> serve(client));
			}
			catch (IOException closed)
			{
				return;
			}
		}
	}

	private Void serve(final Socket client) throws IOException
	{
		try (client)
		{
			DataInputStream in = new DataInputStream(client.getInputStream());
			OutputStream out = client.getOutputStream();
			int version = in.readUnsignedByte();
			int methods = in.readUnsignedByte();
			in.readFully(new byte[methods]);
			if (version != 5)
			{
				return null;
			}
			out.write(new byte[] { 5, 0 });
			Request request = readRequest(in);
			requests.add(request);
			Integer localPort = routes
				.get(request.host().toLowerCase(java.util.Locale.ROOT) + ":" + request.port());
			if (localPort == null)
			{
				// 4: host unreachable
				out.write(new byte[] { 5, 4, 0, 1, 0, 0, 0, 0, 0, 0 });
				return null;
			}
			try (Socket target = new Socket(InetAddress.getLoopbackAddress(), localPort))
			{
				out.write(new byte[] { 5, 0, 0, 1, 0, 0, 0, 0, 0, 0 });
				out.flush();
				threads.submit(() -> {
					pipe(in, target.getOutputStream());
					// the client closed its direction: pass that on, as a TCP half-close
					target.shutdownOutput();
					return null;
				});
				pipe(target.getInputStream(), out);
				client.shutdownOutput();
			}
		}
		return null;
	}

	private static Request readRequest(final DataInputStream in) throws IOException
	{
		in.readUnsignedByte();
		int command = in.readUnsignedByte();
		in.readUnsignedByte();
		int addressType = in.readUnsignedByte();
		String host = switch (addressType)
		{
			case IPV4 -> {
				byte[] address = new byte[4];
				in.readFully(address);
				yield InetAddress.getByAddress(address).getHostAddress();
			}
			case IPV6 -> {
				byte[] address = new byte[16];
				in.readFully(address);
				yield InetAddress.getByAddress(address).getHostAddress();
			}
			case DOMAIN_NAME -> {
				byte[] name = new byte[in.readUnsignedByte()];
				in.readFully(name);
				yield new String(name, StandardCharsets.US_ASCII);
			}
			default -> throw new IOException("address type " + addressType);
		};
		int port = in.readUnsignedShort();
		if (command != 1)
		{
			throw new IOException("command " + command + " is not CONNECT");
		}
		return new Request(addressType, host, port);
	}

	private static Void pipe(final InputStream from, final OutputStream to)
	{
		byte[] buffer = new byte[8192];
		try
		{
			for (int read = from.read(buffer); read >= 0; read = from.read(buffer))
			{
				to.write(buffer, 0, read);
				to.flush();
			}
		}
		catch (IOException closed)
		{
			// either side went away; the other follows when its socket closes
		}
		return null;
	}

	@Override
	public void close() throws IOException
	{
		server.close();
		threads.shutdownNow();
	}
}
