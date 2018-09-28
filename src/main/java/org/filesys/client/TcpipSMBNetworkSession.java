/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.client;

import java.io.*;
import java.net.*;

import org.filesys.debug.Debug;
import org.filesys.netbios.NetworkSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.TcpipSMB;
import org.filesys.util.DataPacker;

/**
 * Native TCP/IP SMB Network Session Class
 * 
 * <p>Contains the details of a connection to a remote file server that has used the native SMB protocol
 * of port 445 to connect to the remote server.
 * 
 *  <p>Contains methods for sending/receiving SMB requests/responses using native SMB.
 * 
 * @author gkspencer
 */
public class TcpipSMBNetworkSession extends NetworkSession {

	//  Define the protocol name
	  
	private static final String ProtocolName = "Native SMB (port 445)";
	  
	//  Socket used to connect and read/write to remote host
	
	private Socket m_socket;
	
	//  Input and output data streams, from the socket network connection
	
	private DataInputStream m_in;
	private DataOutputStream m_out;
	
	//  Session port
	  
	private int m_sessPort = TcpipSMB.PORT;
	  
	//  Debug enable flag and debug output stream
	
	private static boolean m_debug = false;

	/**
	 * Default constructor
	 */
	public TcpipSMBNetworkSession() {
		super(ProtocolName);
	}

	/**
	 * Class constructor
	 * 
	 * @param tmo Socket timeout, in milliseconds
	 */
	public TcpipSMBNetworkSession(int tmo) {
		super(ProtocolName);
		setTimeout(tmo);
	}

	/**
	 * Class constructor
	 * 
	 * @param tmo Socket timeout, in milliseconds
	 * @param port Session port to connect to on the server
	 */
	public TcpipSMBNetworkSession(int tmo, int port) {
		super(ProtocolName);
		setTimeout(tmo);
		m_sessPort = port;
	}

	/**
	 * Open a connection to a remote host
	 * 
	 * @param toName Host name/address being called
	 * @param fromName Local host name/address
	 * @param toAddr Optional address
	 * @exception IOException Socket error
	 */
	public void Open(String toName, String fromName, String toAddr)
		throws IOException, UnknownHostException {

		// Create the socket

		m_socket = new Socket();
		m_socket.connect(new InetSocketAddress(toName, m_sessPort), getTimeout());

		// Enable the timeout on the socket, disable the Nagle algorithm

		m_socket.setSoTimeout(getTimeout());
		m_socket.setTcpNoDelay(true);

		// Attach input/output streams to the socket

		m_in = new DataInputStream(m_socket.getInputStream());
		m_out = new DataOutputStream(m_socket.getOutputStream());
	}

	/**
	 * Determine if the session is connected to a remote host
	 * 
	 * @return boolean
	 */
	public boolean isConnected() {
		return m_socket != null ? true : false;
	}

	/**
	 * Check if there is data available on this network session
	 * 
	 * @return boolean
	 * @exception IOException Socket error
	 */
	public final boolean hasData()
		throws IOException {

		// Check if the connection is active

		if ( m_socket == null || m_in == null)
			return false;

		// Check if there is data available

		return m_in.available() > 0 ? true : false;
	}

	/**
	 * Receive a data packet from the remote host.
	 * 
	 * @param buf Byte buffer to receive the data into.
	 * @return Length of the received data.
	 * @exception java.io.IOException I/O error occurred.
	 */
	public int Receive(byte[] buf)
		throws IOException {

		// Read a data packet of data

		int rdlen = m_in.read(buf, 0, RFCNetBIOSProtocol.HEADER_LEN);

		// Check if a header was received

		if ( rdlen < RFCNetBIOSProtocol.HEADER_LEN)
			throw new java.io.IOException("TCP/IP SMB Short Read");

		// Get the packet data length

		int pktlen = DataPacker.getInt(buf, 0);

		// Debug mode

		if ( m_debug)
			Debug.println("TcpSMB: Rx " + pktlen + " bytes");

		// Check that the packet size is within the valid range for a SMB request

		if ( pktlen > (buf.length - RFCNetBIOSProtocol.HEADER_LEN) || pktlen > RFCNetBIOSProtocol.MaxPacketSize)
			throw new IOException("TCP/IP SMB Long Read");

		// Read the data part of the packet into the users buffer, this may take
		// several reads

		int totlen = 0;
		int offset = RFCNetBIOSProtocol.HEADER_LEN;

		while (pktlen > 0) {

			// Read the data

			rdlen = m_in.read(buf, offset, pktlen);

			// Update the received length and remaining data length

			totlen += rdlen;
			pktlen -= rdlen;

			// Update the user buffer offset as more reads will be required
			// to complete the data read

			offset += rdlen;

		} // end while reading data

		// Return the received data length, not including the header

		return totlen;
	}

	/**
	 * Send a data packet to the remote host.
	 * 
	 * @param data Byte array containing the data to be sent.
	 * @param siz Length of the data to send.
	 * @return true if the data was sent successfully, else false.
	 * @exception java.io.IOException I/O error occurred.
	 */
	public boolean Send(byte[] data, int siz)
		throws IOException {

		// Pack the data length as the first four bytes of the packet

		DataPacker.putInt(siz, data, 0);

		// Send the packet to the remote host

		int len = siz + RFCNetBIOSProtocol.HEADER_LEN;
		m_out.write(data, 0, len);
		return true;
	}

	/**
	 * Close the network session
	 * 
	 * @exception java.io.IOException I/O error occurred
	 */
	public void Close()
		throws IOException {

		// Close the input/output streams

		if ( m_in != null) {
			m_in.close();
			m_in = null;
		}

		if ( m_out != null) {
			m_out.close();
			m_out = null;
		}

		// Close the socket

		if ( m_socket != null) {
			m_socket.close();
			m_socket = null;
		}
	}

	/**
	 * Set the socket timeout
	 * 
	 * @param tmo int
	 */
	public void setTimeout(int tmo) {

		// Call the base class to store the timeout

		super.setTimeout(tmo);

		// Set the socket timeout, if the socket is valid

		if ( m_socket != null) {
			try {
				m_socket.setSoTimeout(getTimeout());
			}
			catch (SocketException ex) {
			}
		}
	}

	/**
	 * Enable/disable session debugging output
	 * 
	 * @param dbg true to enable debugging, else false
	 */
	public static void setDebug(boolean dbg) {
		m_debug = dbg;
	}
}
