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

package org.filesys.oncrpc.portmap;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.PortMapping;
import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacket;
import org.filesys.oncrpc.RpcProcessor;
import org.filesys.oncrpc.TcpRpcSessionHandler;
import org.filesys.oncrpc.UdpRpcDatagramHandler;
import org.filesys.oncrpc.nfs.NFSConfigSection;
import org.filesys.server.NetworkServer;
import org.filesys.server.ServerListener;
import org.filesys.server.Version;
import org.filesys.server.config.ServerConfiguration;

/**
 * Port Mapper Server Class
 *
 * @author gkspencer
 */
public class PortMapperServer extends NetworkServer implements RpcProcessor {

    //	Constants
    //
    //	Server version
    private static final String ServerVersion = Version.PortMapServerVersion;

    //	Default port mapper port
    public final static int DefaultPort = 111;

    //	Maximum request size to accept
    public final static int MaxRequestSize = 1024;

    //  Configuration sections
    private NFSConfigSection m_nfsConfig;

    //	Incoming datagram handler for UDP requests
    private UdpRpcDatagramHandler m_udpHandler;

    //	Incoming session handler for TCP requests
    private TcpRpcSessionHandler m_tcpHandler;

    //	Portmapper port
    private int m_port;

    //	Table of active port mappings
    private Hashtable<Integer, PortMapping> m_mappings;
    private Hashtable<Integer, PortMapping> m_noVerMappings;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public PortMapperServer(ServerConfiguration config) {
        super("Portmap", config);

        //	Set the server version
        setVersion(ServerVersion);

        //  Get the NFS configuration
        m_nfsConfig = (NFSConfigSection) config.getConfigSection(NFSConfigSection.SectionName);

        if (m_nfsConfig != null) {

            //	Enable/disable debug output
            setDebug(getNFSConfiguration().hasPortMapperDebug());

            //	Set the port to use
            if (getNFSConfiguration().getPortMapperPort() != 0)
                setPort(getNFSConfiguration().getPortMapperPort());
            else
                setPort(DefaultPort);

            //	Create the mappings tables
            m_mappings = new Hashtable<Integer, PortMapping>();
            m_noVerMappings = new Hashtable<Integer, PortMapping>();
        } else
            setEnabled(false);
    }

    /**
     * Return the server port
     *
     * @return int
     */
    public final int getPort() {
        return m_port;
    }

    /**
     * Return the NFS configuration section
     *
     * @return NFSConfigSection
     */
    private final NFSConfigSection getNFSConfiguration() {
        return m_nfsConfig;
    }

    /**
     * Start the portmapper server
     */
    public void startServer() {

        try {

            //	Create the UDP RPC handler to accept incoming requests
            m_udpHandler = new UdpRpcDatagramHandler("PortMap", "Port", this, this, null, getPort(), MaxRequestSize);
            m_udpHandler.initializeSessionHandler(this);

            //	Start the UDP request listener is a seperate thread
            Thread udpThread = new Thread(m_udpHandler);
            udpThread.setName("PortMap_UDP");
            udpThread.start();

            //	Create the TCP RPC handler to accept incoming requests
            m_tcpHandler = new TcpRpcSessionHandler("PortMap", "Port", this, this, null, getPort(), MaxRequestSize);
            m_tcpHandler.initializeSessionHandler(this);

            //	Start the UDP request listener is a seperate thread
            Thread tcpThread = new Thread(m_tcpHandler);
            tcpThread.setName("PortMap_TCP");
            tcpThread.start();

            //	Add port mapper entries for the portmapper service
            PortMapping portMap = new PortMapping(PortMapper.ProgramId, PortMapper.VersionId, Rpc.UDP, getPort());
            addPortMapping(portMap);

            portMap = new PortMapping(PortMapper.ProgramId, PortMapper.VersionId, Rpc.TCP, getPort());
            addPortMapping(portMap);
        }
        catch (Exception ex) {
            Debug.println(ex);
        }
    }

    /**
     * Shutdown the server
     *
     * @param immediate boolean
     */
    public void shutdownServer(boolean immediate) {

        //	Stop the RPC handlers
        if (m_udpHandler != null) {
            m_udpHandler.closeSessionHandler(this);
            m_udpHandler = null;
        }

        if (m_tcpHandler != null) {
            m_tcpHandler.closeSessionHandler(this);
            m_tcpHandler = null;
        }

        //	Fire a shutdown notification event
        fireServerEvent(ServerListener.ServerShutdown);
    }

    /**
     * Set the server port
     *
     * @param port int
     */
    public final void setPort(int port) {
        m_port = port;
    }

    /**
     * Process an RPC request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     * @exception IOException Socket error
     */
    public RpcPacket processRpc(RpcPacket rpc)
            throws IOException {

        //	Validate the request
        if (rpc.getProgramId() != PortMapper.ProgramId) {

            //	Request is not for us
            rpc.buildAcceptErrorResponse(Rpc.StsProgUnavail);
            return rpc;
        } else if (rpc.getProgramVersion() != PortMapper.VersionId) {

            //	Request is not for this version of portmapper
            rpc.buildProgramMismatchResponse(PortMapper.VersionId, PortMapper.VersionId);
            return rpc;
        }

        //	Position the RPC buffer pointer at the start of the call parameters
        rpc.positionAtParameters();

        //	Process the RPC request
        RpcPacket response = null;

        switch (rpc.getProcedureId()) {

            //	Null request
            case PortMapper.ProcNull:
                response = procNull(rpc);
                break;

            //	Set a port
            case PortMapper.ProcSet:
                response = procSet(rpc);
                break;

            //	Release a port
            case PortMapper.ProcUnSet:
                response = procUnSet(rpc);
                break;

            //	Get the port for a service
            case PortMapper.ProcGetPort:
                response = procGetPort(rpc);
                break;

            //	Dump ports request
            case PortMapper.ProcDump:
                response = procDump(rpc);
                break;
        }

        //	Return the RPC response
        return response;
    }

    /**
     * Process the null request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procNull(RpcPacket rpc) {

        //	Build the response
        rpc.buildResponseHeader();
        return rpc;
    }

    /**
     * Process the set request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procSet(RpcPacket rpc) {

        //	Get the call parameters
        int progId = rpc.unpackInt();
        int verId = rpc.unpackInt();
        int proto = rpc.unpackInt();
        int port = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[PortMap] Set port program=" + Rpc.getServiceName(progId) + ", version=" + verId +
                    ", protocol=" + (proto == Rpc.TCP ? "TCP" : "UDP") + ", port=" + port);

        //	Check if the port is already mapped
        PortMapping portMap = findPortMapping(progId, verId, proto);
        int portAdded = Rpc.False;

        if (portMap == null) {

            //	Add a mapping for the new service
            portMap = new PortMapping(progId, verId, proto, port);
            if (addPortMapping(portMap) == true)
                portAdded = Rpc.True;
        }

        //	Check if the service is on the same port as the current port mapping, and it is not
        //	an attempt to set the port mapper service port.
        else if (progId != PortMapper.ProgramId && portMap.getPort() == port) {

            //	Settings are the same as the existing service settings so accept it
            portAdded = Rpc.True;
        }

        //	Build the response header
        rpc.buildResponseHeader();

        //	Pack a boolean indicating if the port was added, or not
        rpc.packInt(portAdded);
        rpc.setLength();

        //	Return the response
        return rpc;
    }

    /**
     * Process the unset request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procUnSet(RpcPacket rpc) {

        //	Get the call parameters
        int progId = rpc.unpackInt();
        int verId = rpc.unpackInt();
        int proto = rpc.unpackInt();
        int port = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[PortMap] UnSet port program=" + Rpc.getServiceName(progId) + ", version=" + verId +
                    ", protocol=" + (proto == Rpc.TCP ? "TCP" : "UDP") + ", port=" + port);

        //	Check if the port is mapped, and it is not an attempt to remove a portmapper portt
        PortMapping portMap = findPortMapping(progId, verId, proto);
        int portRemoved = Rpc.False;

        if (portMap != null && progId != PortMapper.ProgramId) {

            //	Remove the port mapping
            if (removePortMapping(portMap) == true)
                portRemoved = Rpc.True;
        }

        //	Build the response header
        rpc.buildResponseHeader();

        //	Pack a boolean indicating if the port was removed, or not
        rpc.packInt(portRemoved);
        rpc.setLength();

        //	Return the response
        return rpc;
    }

    /**
     * Process the get port request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procGetPort(RpcPacket rpc) {

        //	Get the call parameters
        int progId = rpc.unpackInt();
        int verId = rpc.unpackInt();
        int proto = rpc.unpackInt();

        //	Find the required port mapping
        PortMapping portMap = findPortMapping(progId, verId, proto);

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[PortMap] Get port program=" + Rpc.getServiceName(progId) + ", version=" + verId +
                    ", protocol=" + (proto == Rpc.TCP ? "TCP" : "UDP") +
                    ", port=" + (portMap != null ? portMap.getPort() : 0));

        //	Build the response header
        rpc.buildResponseHeader();

        //	Pack the port number of the requested RPC service, or zero if not found
        rpc.packInt(portMap != null ? portMap.getPort() : 0);
        rpc.setLength();

        //	Return the response
        return rpc;
    }

    /**
     * Process the dump request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procDump(RpcPacket rpc) {

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[PortMap] Dump ports request from " + rpc.getClientDetails());

        //	Build the response
        rpc.buildResponseHeader();

        //	Pack the active port mappings structures
        Enumeration enm = m_mappings.elements();

        while (enm.hasMoreElements()) {

            //	Get the current port mapping
            PortMapping portMap = (PortMapping) enm.nextElement();

            //	Pack the port mapping structure
            rpc.packInt(Rpc.True);
            rpc.packPortMapping(portMap);
        }

        //	Pack the end of list structure, set the response length
        rpc.packInt(Rpc.False);
        rpc.setLength();

        //	Return the response
        return rpc;
    }

    /**
     * Add a port mapping to the active list
     *
     * @param portMap PortMapping
     * @return boolean
     */
    private final boolean addPortMapping(PortMapping portMap) {

        //	Check if there is an existing port mapping that matches the new port
        Integer key = new Integer(portMap.hashCode());
        if (m_mappings.get(key) != null)
            return false;

        //	Add the port mapping
        m_mappings.put(key, portMap);

        //	Add a port mapping with a version id of zero
        key = new Integer(PortMapping.generateHashCode(portMap.getProgramId(), 0, portMap.getProtocol()));
        m_noVerMappings.put(key, portMap);

        //	Indicate that the mapping was added
        return true;
    }

    /**
     * Remove a port mapping from the active list
     *
     * @param portMap PortMapping
     * @return boolean
     */
    private final boolean removePortMapping(PortMapping portMap) {

        //	Remove the port mapping from the active lists
        Integer key = new Integer(portMap.hashCode());
        Object removedObj = m_mappings.remove(key);

        key = new Integer(PortMapping.generateHashCode(portMap.getProgramId(), 0, portMap.getProtocol()));
        m_noVerMappings.remove(key);

        //	Return a status indicating if the mapping was removed
        return removedObj != null ? true : false;
    }

    /**
     * Search for a port mapping
     *
     * @param progId int
     * @param verId  int
     * @param proto  int
     * @return PortMapping
     */
    private final PortMapping findPortMapping(int progId, int verId, int proto) {

        //	Create a key for the RPC service
        Integer key = new Integer(PortMapping.generateHashCode(progId, verId, proto));

        //	Search for the required port mapping, including the version id
        PortMapping portMap = m_mappings.get(key);
        if (portMap == null && verId == 0) {

            //	Search for the port mapping without the version id
            portMap = m_noVerMappings.get(key);
        }

        //	Return the port mapping, or null if not found
        return portMap;
    }
}
