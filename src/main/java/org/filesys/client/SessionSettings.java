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

import java.net.*;

import org.filesys.netbios.NetBIOSSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.Protocol;
import org.filesys.smb.TcpipSMB;

/**
 *  The session settings class holds the connection options for a new SMB session.
 *  
 *  <p>A SessionSettings object can be used with the various open session methods of the SessionFactory class to specify per
 *  session overrides to the default settings, such as protocol order, remote ports, timeout values, debugging options.
 * 
 * @author gkspencer
 */
public class SessionSettings {

    //  Primary and secondary protocols to connect with
    
    private int m_primaryProto   = Protocol.TCPNetBIOS;
    private int m_secondaryProto = Protocol.NativeSMB;

    // SMB dialects to negotiate
    
    private DialectSelector m_dialects;
    
    // Session timeout, in milliseconds. Used for initial connection and send/receive.
    
    private int m_timeout = RFCNetBIOSProtocol.TMO;
    
    // NetBIOS session and native SMB session ports
    
    private int m_netbiosPort = RFCNetBIOSProtocol.SESSION;
    private int m_nativeSMBPort = TcpipSMB.PORT;

    // NetBIOS name scope
    
    private String m_netbiosScopeId;
    
    // NetBIOS name port
    
    private int m_netbiosNamePort = RFCNetBIOSProtocol.NAMING;
    
    // NetBIOS broadcast name lookup subnet mask
    
    private String m_subnetMask;
    
    // WINS server address
    
    private InetAddress m_WINSServer = NetBIOSSession.getDefaultWINSServer();
    
    // NetBIOS name lookup type and timeout, in milliseconds
    
    private NetBIOSSession.LookupType m_lookupType = NetBIOSSession.getDefaultLookupType();
    private int m_lookupTmo  = NetBIOSSession.getDefaultLookupTimeout();
    
    // Use wildcard file server name when connecting to remote server
    
    private boolean m_useWildcardName = NetBIOSSession.getDefaultWildcardFileServerName();
    
    // Flag to indicate if the primary protocol should be updated from the secondary protocol
    // setting when the connection is successfully made using the secondary protocol.
    
    private boolean m_updateProtocol;
    
    // Virtual circuit, a value of zero may reset other connections from this name or address
    
    private int m_vcircuit;
    
	/**
	 * Default constructor
	 */
	public SessionSettings() {
	}

	/**
	 * Class constructor
	 * 
	 * @param primaryProto int
	 * @param secondaryProto int
	 */
	public SessionSettings(int primaryProto, int secondaryProto) {
		m_primaryProto = primaryProto;
		m_secondaryProto = secondaryProto;
	}

	/**
	 * /** Class constructor
	 * 
	 * @param primaryProto int
	 * @param secondaryProto int
	 * @param tmo int
	 */
	public SessionSettings(int primaryProto, int secondaryProto, int tmo) {
		m_primaryProto = primaryProto;
		m_secondaryProto = secondaryProto;

		m_timeout = tmo;
	}

	/**
	 * Return the primary protocol
	 * 
	 * @return int
	 */
	public final int getPrimaryProtocol() {
		return m_primaryProto;
	}

	/**
	 * Return the secondary protocol
	 * 
	 * @return int
	 */
	public final int getSecondaryProtocol() {
		return m_secondaryProto;
	}

	/**
	 * Return the session timeout
	 * 
	 * @return int
	 */
	public final int getSessionTimeout() {
		return m_timeout;
	}

	/**
	 * Return the SMB dialect list to negotiate
	 * 
	 * @return DialectSelector
	 */
	public final DialectSelector getDialects() {
		return m_dialects;
	}

	/**
	 * Return the NetBIOS session port
	 * 
	 * @return int
	 */
	public final int getNetBIOSSessionPort() {
		return m_netbiosPort;
	}

	/**
	 * Return the NetBIOS name port
	 * 
	 * @return int
	 */
	public final int getNetBIOSNamePort() {
		return m_netbiosNamePort;
	}

	/**
	 * Return the native SMB port
	 * 
	 * @return int
	 */
	public final int getNativeSMBPort() {
		return m_nativeSMBPort;
	}

	/**
	 * Determine if the NetBIOS name scope is set
	 * 
	 * @return boolean
	 */
	public final boolean hasNetBIOSNameScope() {
		return m_netbiosScopeId != null ? true : false;
	}

	/**
	 * Return the NetBIOS name scope
	 * 
	 * @return String
	 */
	public final String getNetBIOSNameScope() {
		return m_netbiosScopeId;
	}

	/**
	 * Get the subnet mask to be used for NetBIOS name lookup broadcasts
	 * 
	 * @return String
	 */
	public final String getSubnetMask() {
		return m_subnetMask;
	}

	/**
	 * Get the WINS server to be used for NetBIOS name lookups
	 * 
	 * @return InetAddress
	 */
	public final InetAddress getWINSServer() {
		return m_WINSServer;
	}

	/**
	 * Get the NetBIOS name lookup type(s) to use
	 * 
	 * @return NetBIOSSession.LookupType
	 */
	public final NetBIOSSession.LookupType getLookupType() {
		return m_lookupType;
	}

	/**
	 * Get the NetBIOS name lookup timeout, in milliseconds
	 * 
	 * @return int
	 */
	public final int getLookupTimeout() {
		return m_lookupTmo;
	}

	/**
	 * Get the use wildcard file server name (*SMBSERVER) flag
	 * 
	 * @return boolean
	 */
	public final boolean useWildcardServerName() {
		return m_useWildcardName;
	}

	/**
	 * Check if the update primary protocol flag is set
	 * 
	 * @return boolean
	 */
	public final boolean hasUpdateProtocol() {
		return m_updateProtocol;
	}

	/**
	 * Return the virtual circuit for this session
	 * 
	 * @return int
	 */
	public final int getVirtualCircuit() {
		return m_vcircuit;
	}

	/**
	 * Set the primary connection protocol
	 * 
	 * @param proto int
	 */
	public final void setPrimaryProtocol(int proto) {
		m_primaryProto = proto;
	}

	/**
	 * Set the secondary connection protocol
	 * 
	 * @param proto int
	 */
	public final void setSecondaryProtocol(int proto) {
		m_secondaryProto = proto;
	}

	/**
	 * Set the session connection timeout, in milliseconds
	 * 
	 * @param tmo int
	 */
	public final void setSessionTimeout(int tmo) {
		m_timeout = tmo;
	}

	/**
	 * Set the negotiated dialect list
	 * 
	 * @param dialects DialectSelector
	 */
	public final void setDialects(DialectSelector dialects) {
		m_dialects = dialects;
	}

	/**
	 * Set the NetBIOS session port
	 * 
	 * @param port int
	 */
	public final void setNetBIOSSessionPort(int port) {
		m_netbiosPort = port;
	}

	/**
	 * Set the NetBIOS name port
	 * 
	 * @param port int
	 */
	public final void setNetBIOSNamePort(int port) {
		m_netbiosNamePort = port;
	}

	/**
	 * Set the native SMB port
	 * 
	 * @param port int
	 */
	public final void setNativeSMBPort(int port) {
		m_nativeSMBPort = port;
	}

	/**
	 * Set the NetBIOS name scope
	 * 
	 * @param scope String
	 */
	public final void setNetBIOSNameScope(String scope) {
		m_netbiosScopeId = scope;
	}

	/**
	 * Set the subnet mask to be used for NetBIOS name lookup broadcasts
	 * 
	 * @param mask String
	 */
	public final void setSubnetMask(String mask) {
		m_subnetMask = mask;
	}

	/**
	 * Set the WINS server to be used for NetBIOS name lookups
	 * 
	 * @param addr InetAddress
	 */
	public final void setWINSServer(InetAddress addr) {
		m_WINSServer = addr;
	}

	/**
	 * Set the NetBIOS name lookup type(s) to use
	 * 
	 * @param typ NetBIOSSession.LookupType
	 */
	public final void setLookupType(NetBIOSSession.LookupType typ) {
		m_lookupType = typ;
	}

	/**
	 * Set the NetBIOS name lookup timeout, in milliseconds
	 * 
	 * @param tmo int
	 */
	public final void setLookupTimeout(int tmo) {
		m_lookupTmo = tmo;
	}

	/**
	 * Set/clear the use wildcard file server name (*SMBSERVER) flag
	 * 
	 * @param ena boolean
	 */
	public final void setUseWildcardServerName(boolean ena) {
		m_useWildcardName = ena;
	}

	/**
	 * Set/clear the update protocol flag
	 * 
	 * @param updateProto boolean
	 */
	public final void setUpdateProtocol(boolean updateProto) {
		m_updateProtocol = updateProto;
	}

	/**
	 * Set the virtual circuit
	 * 
	 * @param vc int
	 */
	public final void setVirtualCircuit(int vc) {
		m_vcircuit = vc;
	}

	/**
	 * Return the session settings as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(Protocol.asString(getPrimaryProtocol()));
		str.append(",");
		str.append(Protocol.asString(getSecondaryProtocol()));
		str.append(",Tmo=");
		str.append(getSessionTimeout());
		str.append("ms,Dialects=");
		str.append(getDialects());

		if ( getNetBIOSSessionPort() != RFCNetBIOSProtocol.SESSION) {
			str.append(",NB Port=");
			str.append(getNetBIOSSessionPort());
		}

		if ( getNativeSMBPort() != TcpipSMB.PORT) {
			str.append(",CIFS Port=");
			str.append(getNativeSMBPort());
		}

		if ( hasNetBIOSNameScope()) {
			str.append(",ScopeId=");
			str.append(getNetBIOSNameScope());
		}

		str.append(",VC=");
		str.append(getVirtualCircuit());

		str.append("]");

		return str.toString();
	}
}
