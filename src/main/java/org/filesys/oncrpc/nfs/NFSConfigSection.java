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

package org.filesys.oncrpc.nfs;

import org.filesys.oncrpc.RpcAuthenticator;
import org.filesys.oncrpc.portmap.PortMapper;
import org.filesys.server.config.InvalidConfigurationException;
import org.springframework.extensions.config.ConfigElement;
import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.ServerConfiguration;


/**
 * NFS Server Configuration Section Class
 *
 * @author gkspencer
 */
public class NFSConfigSection extends ConfigSection {

    // NFS server configuration section name
    public static final String SectionName = "NFS";

    //  Enable the port mapper server
    private boolean m_nfsPortMapper;

    //  Port mapper port
    private int m_portMapperPort = PortMapper.DefaultPort;

    //  Mount server port
    private int m_mountServerPort;

    //  NFS server port
    private int m_nfsServerPort;

    // RPC registration port, 0 = use next free non-privileged port
    private int m_rpcRegisterPort;

    //  NFS debug flags
    private int m_nfsDebug;

    //  Port mapper and mount server debug enable
    private boolean m_portMapDebug;
    private boolean m_mountServerDebug;

    //  Thread pool size and packet pool size
    private int m_nfsThreadPoolSize;
    private int m_nfsPacketPoolSize;

    //  RPC authenticator implementation
    private RpcAuthenticator m_rpcAuthenticator;
    private ConfigElement m_rpcAuthParams;

    //  Network file cache timers/debug
    private long m_nfsFileCacheIOTimer;
    private long m_nfsFileCacheCloseTimer;

    private boolean m_nfsFileCacheDebug;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public NFSConfigSection(ServerConfiguration config) {
        super(SectionName, config);
    }

    /**
     * Determine if port mapper debug is enabled
     *
     * @return boolean
     */
    public final boolean hasPortMapperDebug() {
        return m_portMapDebug;
    }

    /**
     * Determine if mount server debug is enabled
     *
     * @return boolean
     */
    public final boolean hasMountServerDebug() {
        return m_mountServerDebug;
    }

    /**
     * Check if the NFS port mapper is enabled
     *
     * @return boolean
     */
    public final boolean hasNFSPortMapper() {
        return m_nfsPortMapper;
    }

    /**
     * Return the port for port mapper to use, or zero for the default port
     *
     * @return int
     */
    public final int getPortMapperPort() {
        return m_portMapperPort;
    }

    /**
     * Return the port the mount server should use, or zero for the default port
     *
     * @return int
     */
    public final int getMountServerPort() {
        return m_mountServerPort;
    }

    /**
     * Return the port the NFS server should use, or zero for the default port
     *
     * @return int
     */
    public final int getNFSServerPort() {
        return m_nfsServerPort;
    }

    /**
     * Return the RPC registration port
     *
     * @return int
     */
    public final int getRPCRegistrationPort() {
        return m_rpcRegisterPort;
    }

    /**
     * Return the NFS debug flags
     *
     * @return int
     */
    public final int getNFSDebug() {
        return m_nfsDebug;
    }

    /**
     * Return the NFS thread pool size
     *
     * @return int
     */
    public final int getNFSThreadPoolSize() {
        return m_nfsThreadPoolSize;
    }

    /**
     * Return the NFS server packet pool size, or -1 for the default size
     *
     * @return int
     */
    public final int getNFSPacketPoolSize() {
        return m_nfsPacketPoolSize;
    }

    /**
     * Get the authenticator object that is used to provide RPC authentication (for the portmapper, mount server and
     * NFS server)
     *
     * @return RpcAuthenticator
     */
    public final RpcAuthenticator getRpcAuthenticator() {
        return m_rpcAuthenticator;
    }

    /**
     * Return the RPC authenticator initialization parameters
     *
     * @return ConfigElement
     */
    public final ConfigElement getRPCAuthenticatorParameters() {
        return m_rpcAuthParams;
    }

    /**
     * Return the NFS file cache I/O timer, in milliseconds
     *
     * @return long
     */
    public final long getNFSFileCacheIOTimer() {
        return m_nfsFileCacheIOTimer;
    }

    /**
     * Return the NFS file cache close timer, in milliseconds
     *
     * @return long
     */
    public final long getNFSFileCacheCloseTimer() {
        return m_nfsFileCacheCloseTimer;
    }

    /**
     * Check if NFS file cache debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasNFSFileCacheDebug() {
        return m_nfsFileCacheDebug;
    }

    /**
     * Set the NFS port mapper enable flag
     *
     * @param ena boolean
     * @return int
     * @exception InvalidConfigurationException Error setting the port mapper enable flag
     */
    public final int setNFSPortMapper(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_nfsPortMapper != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.NFSPortMapEnable, new Boolean(ena));
            m_nfsPortMapper = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS debug flags
     *
     * @param dbg int
     * @return int
     * @exception InvalidConfigurationException Error setting the debug flags
     */
    public final int setNFSDebug(int dbg)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSDebugFlags, new Integer(dbg));
        m_nfsDebug = dbg;

        //  Return the change status
        return sts;
    }

    /**
     * Set the port mapper port
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the port mapper port
     */
    public final int setPortMapperPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSPortMapPort, new Integer(port));
        m_portMapperPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the mount server port
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the mount server port
     */
    public final int setMountServerPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSMountPort, new Integer(port));
        m_mountServerPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS server port
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the server port
     */
    public final int setNFSServerPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSServerPort, new Integer(port));
        m_nfsServerPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the RPC registration port
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the RPC registration port
     */
    public final int setRPCRegistrationPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSRPCRegistrationPort, new Integer(port));
        m_rpcRegisterPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS thread pool size
     *
     * @param poolSize int
     * @return int
     * @exception InvalidConfigurationException Error setting the thread pool size
     */
    public final int setNFSThreadPoolSize(int poolSize)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSThreads, new Integer(poolSize));
        m_nfsThreadPoolSize = poolSize;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS packet pool size
     *
     * @param poolSize int
     * @return int
     * @exception InvalidConfigurationException Error setting the packet pool size
     */
    public final int setNFSPacketPoolSize(int poolSize)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSPacketPool, new Integer(poolSize));
        m_nfsPacketPoolSize = poolSize;

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable port mapper debug output
     *
     * @param dbg boolean
     * @return int
     * @exception InvalidConfigurationException Error setting the debug enable flag
     */
    public final int setPortMapperDebug(boolean dbg)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_portMapDebug != dbg) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.NFSPortMapDebug, new Boolean(dbg));
            m_portMapDebug = dbg;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable mount server debug output
     *
     * @param dbg boolean
     * @return int
     * @exception InvalidConfigurationException Error setting the mount server debug flag
     */
    public final int setMountServerDebug(boolean dbg)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_portMapDebug != dbg) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.NFSMountDebug, new Boolean(dbg));
            m_mountServerDebug = dbg;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the RPC authenticator to be used to authenticate access to the RPC based services (portmapper, mount
     * server and NFS server)
     *
     * @param authClass String
     * @param params    ConfigElement
     * @return int
     * @exception InvalidConfigurationException Error setting the RPC authenticator
     */
    public final int setRpcAuthenticator(String authClass, ConfigElement params)
            throws InvalidConfigurationException {

        //  Validate the RPC authenticator class
        int sts = ConfigurationListener.StsIgnored;
        RpcAuthenticator auth = null;

        try {

            //  Load the RPC authenticator class
            Object authObj = Class.forName(authClass).newInstance();
            if (authObj instanceof RpcAuthenticator) {

                //  Set the RPC authenticator
                auth = (RpcAuthenticator) authObj;
            } else
                throw new InvalidConfigurationException("RPC Authenticator is not derived from required base class");
        }
        catch (ClassNotFoundException ex) {
            throw new InvalidConfigurationException("RPC Authenticator class " + authClass + " not found");
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("RPC Authenticator class error");
        }

        //  Initialize the authenticator using the parameter values
        auth.initialize(getServerConfiguration(), params);

        //  Inform listeners, validate the configuration change
        sts = setRpcAuthenticator(auth);

        //  Set the initialization parameters
        m_rpcAuthParams = params;

        //  Return the change status
        return sts;
    }

    /**
     * Set the RPC authenticator to be used to authenticate access to the RPC based services (portmapper, mount
     * server and NFS server)
     *
     * @param auth RpcAuthenticator
     * @return int
     * @exception InvalidConfigurationException Error setting the RPC authenticator
     */
    public final int setRpcAuthenticator(RpcAuthenticator auth)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSRpcAuthenticator, auth);

        //  Set the RPC authenticator
        m_rpcAuthenticator = auth;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS file cache I/O timer, in milliseconds
     *
     * @param ioTimer long
     * @return int
     * @exception InvalidConfigurationException Error setting the file cache timer
     */
    public final int setNFSFileCacheIOTimer(long ioTimer)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSFileCacheIOTimer, new Long(ioTimer));
        m_nfsFileCacheIOTimer = ioTimer;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS file cache close timer, in milliseconds
     *
     * @param closeTimer long
     * @return int
     * @exception InvalidConfigurationException Error setting the file cache close timer
     */
    public final int setNFSFileCacheCloseTimer(long closeTimer)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NFSFileCacheCloseTimer, new Long(closeTimer));
        m_nfsFileCacheCloseTimer = closeTimer;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS file cache debug enable flag
     *
     * @param ena boolean
     * @return int
     * @exception InvalidConfigurationException Error setting the file cache debug flag
     */
    public final int setNFSFileCacheDebug(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_nfsFileCacheDebug != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.NFSFileCacheDebug, new Boolean(ena));
            m_nfsFileCacheDebug = ena;
        }

        //  Return the change status
        return sts;
    }
}
