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

import java.util.EnumSet;


/**
 * NFS Server Configuration Section Class
 *
 * @author gkspencer
 */
public class NFSConfigSection extends ConfigSection {

    // Available NFS versions
    public enum NFSVersion {
        NFS3,
        NFS4
    }

    // NFS server configuration section name
    public static final String SectionName = "NFS";

    //  Enable the port mapper server
    private boolean m_nfsPortMapper;

    //  Port mapper port
    private int m_portMapperPort = PortMapper.DefaultPort;

    //  Mount server port
    private int m_mountServerPort;

    //  NFS server port
    private int m_nfsServerPort = NFS.DefaultPort;

    // RPC registration port, 0 = use next free non-privileged port
    private int m_rpcRegisterPort;

    // Enabled NFS versions
    private EnumSet m_nfsVersions = EnumSet.of( NFSVersion.NFS3);

    //  NFS debug flags
    private EnumSet<NFSSrvSession.Dbg> m_nfsDebug;

    //  Port mapper and mount server debug enable
    private boolean m_portMapDebug;
    private boolean m_mountServerDebug;

    //  RPC authenticator implementation
    private RpcAuthenticator m_rpcAuthenticator;
    private ConfigElement m_rpcAuthParams;

    //  Network file cache timers/debug
    private long m_nfsFileCacheIOTimer;
    private long m_nfsFileCacheCloseTimer;

    private boolean m_nfsFileCacheDebug;

    // Disable NIO based code
    private boolean m_disableNIO;

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
     * Return the enabled NFS version(s)
     *
     * @return EnumSet&lt;NFSVersion&gt;
     */
    public final EnumSet<NFSVersion> getEnabledNFSVersions() { return m_nfsVersions; }

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
     * @return EnumSet&lt;NFSSrvSession.Dbg&gt;
     */
    public final EnumSet<NFSSrvSession.Dbg> getNFSDebug() {
        return m_nfsDebug;
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
     * Determine if NIO based code should be disabled
     *
     * @return boolean
     */
    public final boolean hasDisableNIOCode() {
        return m_disableNIO;
    }

    /**
     * Set the NFS port mapper enable flag
     *
     * @param ena boolean
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the port mapper enable flag
     */
    public final ConfigurationListener.Sts setNFSPortMapper(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        ConfigurationListener.Sts sts = ConfigurationListener.Sts.Ignored;

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
     * @param dbg EnumSet&lt;NFSSrvSession.Dbg&gt;
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the debug flags
     */
    public final ConfigurationListener.Sts setNFSDebug(EnumSet<NFSSrvSession.Dbg> dbg)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSDebugFlags, dbg);
        m_nfsDebug = dbg;

        //  Return the change status
        return sts;
    }

    /**
     * Set the port mapper port
     *
     * @param port int
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the port mapper port
     */
    public final ConfigurationListener.Sts setPortMapperPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSPortMapPort, new Integer(port));
        m_portMapperPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the mount server port
     *
     * @param port int
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the mount server port
     */
    public final ConfigurationListener.Sts setMountServerPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSMountPort, new Integer(port));
        m_mountServerPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS server port
     *
     * @param port int
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the server port
     */
    public final ConfigurationListener.Sts setNFSServerPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSServerPort, new Integer(port));
        m_nfsServerPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the enabled NFS verison(s)
     *
     * @param nfsVersions EnumSet&lt;NFSVersion&gt;
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the NFS versions
     */
    public final ConfigurationListener.Sts setEnabledNFSVersions(EnumSet<NFSVersion> nfsVersions)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSVersions, nfsVersions);
        m_nfsVersions = nfsVersions;

        //  Return the change status
        return sts;
    }

    /**
     * Set the RPC registration port
     *
     * @param port int
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the RPC registration port
     */
    public final ConfigurationListener.Sts setRPCRegistrationPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSRPCRegistrationPort, new Integer(port));
        m_rpcRegisterPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable port mapper debug output
     *
     * @param dbg boolean
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the debug enable flag
     */
    public final ConfigurationListener.Sts setPortMapperDebug(boolean dbg)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        ConfigurationListener.Sts sts = ConfigurationListener.Sts.Ignored;

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
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the mount server debug flag
     */
    public final ConfigurationListener.Sts setMountServerDebug(boolean dbg)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        ConfigurationListener.Sts sts = ConfigurationListener.Sts.Ignored;

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
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the RPC authenticator
     */
    public final ConfigurationListener.Sts setRpcAuthenticator(String authClass, ConfigElement params)
            throws InvalidConfigurationException {

        //  Validate the RPC authenticator class
        ConfigurationListener.Sts sts = ConfigurationListener.Sts.Ignored;
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
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the RPC authenticator
     */
    public final ConfigurationListener.Sts setRpcAuthenticator(RpcAuthenticator auth)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSRpcAuthenticator, auth);

        //  Set the RPC authenticator
        m_rpcAuthenticator = auth;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS file cache I/O timer, in milliseconds
     *
     * @param ioTimer long
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the file cache timer
     */
    public final ConfigurationListener.Sts setNFSFileCacheIOTimer(long ioTimer)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSFileCacheIOTimer, new Long(ioTimer));
        m_nfsFileCacheIOTimer = ioTimer;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS file cache close timer, in milliseconds
     *
     * @param closeTimer long
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the file cache close timer
     */
    public final ConfigurationListener.Sts setNFSFileCacheCloseTimer(long closeTimer)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSFileCacheCloseTimer, new Long(closeTimer));
        m_nfsFileCacheCloseTimer = closeTimer;

        //  Return the change status
        return sts;
    }

    /**
     * Set the NFS file cache debug enable flag
     *
     * @param ena boolean
     * @return ConfigurationListener.Sts
     * @exception InvalidConfigurationException Error setting the file cache debug flag
     */
    public final ConfigurationListener.Sts setNFSFileCacheDebug(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        ConfigurationListener.Sts sts = ConfigurationListener.Sts.Ignored;

        if (m_nfsFileCacheDebug != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.NFSFileCacheDebug, new Boolean(ena));
            m_nfsFileCacheDebug = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the disable NIO code flag
     *
     * @param disableNIO boolean
     * @return ConfigurationListener.Sts
     * @throws InvalidConfigurationException Failed to set the disable NIO flag
     */
    public final ConfigurationListener.Sts setDisableNIOCode(boolean disableNIO)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        ConfigurationListener.Sts sts = fireConfigurationChange(ConfigId.NFSDisableNIO, new Boolean(disableNIO));
        m_disableNIO = disableNIO;

        //  Return the change status
        return sts;
    }
}
