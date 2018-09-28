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

package org.filesys.oncrpc;

import java.io.IOException;
import java.net.InetAddress;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.nfs.NFSConfigSection;
import org.filesys.oncrpc.portmap.PortMapper;
import org.filesys.server.NetworkServer;
import org.filesys.server.config.ServerConfiguration;


/**
 * RPC Network Server Abstract Class
 *
 * <p>Provides the base class for RPC servers (such as mount and NFS).
 *
 * @author gkspencer
 */
public abstract class RpcNetworkServer extends NetworkServer implements RpcProcessor {

    // RPC service register/unregsiter lock
    private static final Object _rpcRegisterLock = new Object();

    // Port mapper port
    private int m_portMapperPort = PortMapper.DefaultPort;

    // RPC registration port
    private int m_rpcRegisterPort;

    /**
     * Class constructor
     *
     * @param name   String
     * @param config ServerConfiguration
     */
    public RpcNetworkServer(String name, ServerConfiguration config) {
        super(name, config);

        // Set the RPC registration port
        NFSConfigSection nfsConfig = (NFSConfigSection) config.getConfigSection(NFSConfigSection.SectionName);

        if (nfsConfig != null)
            m_rpcRegisterPort = nfsConfig.getRPCRegistrationPort();
    }

    /**
     * Register a port/protocol for the RPC server
     *
     * @param mapping PortMapping
     * @exception IOException Socket error
     */
    protected final void registerRPCServer(PortMapping mapping)
            throws IOException {

        //	Call the main registration method
        PortMapping[] mappings = new PortMapping[1];
        mappings[0] = mapping;

        registerRPCServer(mappings);
    }

    /**
     * Register a set of ports/protocols for the RPC server
     *
     * @param mappings PortMapping[]
     * @exception IOException Socket error
     */
    protected final void registerRPCServer(PortMapping[] mappings)
            throws IOException {

        // Check if portmapper registration has been disabled
        if (m_portMapperPort == -1)
            return;

        //	Connect to the local portmapper service to register the RPC service
        InetAddress localHost = InetAddress.getByName("127.0.0.1");

        TcpRpcClient rpcClient = null;

        try {

            // Synchronize access to the register port
            synchronized (_rpcRegisterLock) {

                // Create the RPC client to talk to the portmapper/rpcbind service
                rpcClient = new TcpRpcClient(localHost, m_portMapperPort, localHost, m_rpcRegisterPort, 512);

                // Allocate RPC request and response packets
                RpcPacket setPortRpc = new RpcPacket(512);
                RpcPacket rxRpc = new RpcPacket(512);

                //	Loop through the port mappings and register each port with the portmapper service
                for (int i = 0; i < mappings.length; i++) {

                    //	Build the RPC request header
                    setPortRpc.buildRequestHeader(PortMapper.ProgramId, PortMapper.VersionId, PortMapper.ProcSet, 0, null, 0, null);

                    //	Pack the request parameters and set the request length
                    setPortRpc.packPortMapping(mappings[i]);
                    setPortRpc.setLength();

                    //	DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[" + getProtocolName() + "] Register server RPC " + mappings[i] + " ...");

                    //	Send the RPC request and receive a response
                    rxRpc = rpcClient.sendRPC(setPortRpc, rxRpc);

                    // Check if the server has been registered successfully with the portmapper/rpcbind service
                    if (rxRpc != null && rxRpc.getAcceptStatus() == Rpc.StsSuccess) {

                        // Server registered successfully
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[" + getProtocolName() + "] Registered successfully, " + mappings[i]);
                    } else {

                        // Indicate that the server registration failed
                        Debug.println("[" + getProtocolName() + "] RPC Server registration failed for " + mappings[i]);
                        Debug.println("  Response:" + rxRpc);
                    }
                }

                // Close the connection to the portmapper
                rpcClient.closeConnection();
                rpcClient = null;
            }
        }
        catch (Exception ex) {

            // Debug
            if (Debug.EnableInfo && hasDebug()) {
                Debug.println("[" + getProtocolName() + "] Failed to register RPC service");
                Debug.println(ex);
            }
        }
        finally {

            // Make sure the RPC client is closed down
            if (rpcClient != null)
                rpcClient.closeConnection();
        }
    }

    /**
     * Unregister a port/protocol for the RPC server
     *
     * @param mapping PortMapping
     * @exception IOException Socket error
     */
    protected final void unregisterRPCServer(PortMapping mapping)
            throws IOException {

        //	Call the main unregister ports method
        PortMapping[] mappings = new PortMapping[1];
        mappings[0] = mapping;

        unregisterRPCServer(mappings);
    }

    /**
     * Unregister a set of ports/protocols for the RPC server
     *
     * @param mappings PortMapping[]
     * @exception IOException Socket error
     */
    protected final void unregisterRPCServer(PortMapping[] mappings)
            throws IOException {

        // Check if portmapper registration has been disabled
        if (m_portMapperPort == -1)
            return;

        //  Connect to the local portmapper service to unregister the RPC service
        InetAddress localHost = InetAddress.getByName("127.0.0.1");

        TcpRpcClient rpcClient = null;

        try {

            // Synchronize access to the register port
            synchronized (_rpcRegisterLock) {

                // Create the RPC client to talk to the portmapper/rpcbind service
                rpcClient = new TcpRpcClient(localHost, m_portMapperPort, localHost, m_rpcRegisterPort, 512);

                //  Allocate RPC request and response packets
                RpcPacket setPortRpc = new RpcPacket(512);
                RpcPacket rxRpc = new RpcPacket(512);

                //  Loop through the port mappings and unregister each port with the portmapper service
                for (int i = 0; i < mappings.length; i++) {

                    //  Build the RPC request header
                    setPortRpc.buildRequestHeader(PortMapper.ProgramId, PortMapper.VersionId, PortMapper.ProcUnSet, 0, null, 0, null);

                    //  Pack the request parameters and set the request length
                    setPortRpc.packPortMapping(mappings[i]);
                    setPortRpc.setLength();

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[" + getProtocolName() + "] UnRegister server RPC " + mappings[i] + " ...");

                    //  Send the RPC request and receive a response
                    rxRpc = rpcClient.sendRPC(setPortRpc, rxRpc);

                    // Check if the server has been unregistered successfully with the portmapper/rpcbind service
                    if (rxRpc != null && rxRpc.getAcceptStatus() == Rpc.StsSuccess) {

                        // Server registered successfully
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[" + getProtocolName() + "] UnRegistered successfully, " + mappings[i]);
                    } else {

                        // Indicate that the server registration failed
                        Debug.println("[" + getProtocolName() + "] RPC Server unregistration failed for " + mappings[i]);
                        Debug.println("  Response:" + rxRpc);
                    }
                }

                // Close the connection to the portmapper
                rpcClient.closeConnection();
                rpcClient = null;
            }
        }
        catch (Exception ex) {

            // Debug
            if (Debug.EnableInfo && hasDebug()) {
                Debug.println("[" + getProtocolName() + "] Failed to unregister RPC service");
                Debug.println(ex);
            }
        }
        finally {

            // Make sure the RPC client is closed down
            if (rpcClient != null)
                rpcClient.closeConnection();
        }
    }

    /**
     * Set the port mapper port, or -1 to disable portmapper registration
     *
     * @param port int
     */
    public final void setPortMapper(int port) {
        m_portMapperPort = port;
    }

    /**
     * Start the RPC server
     */
    public abstract void startServer();

    /**
     * Shutdown the RPC server
     *
     * @param immediate boolean
     */
    public abstract void shutdownServer(boolean immediate);

    /**
     * Process an RPC request
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     * @exception IOException Socket error
     */
    public abstract RpcPacket processRpc(RpcPacket rpc)
            throws IOException;
}
