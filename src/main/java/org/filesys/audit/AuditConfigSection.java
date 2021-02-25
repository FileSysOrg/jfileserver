/*
 * Copyright (C) 2020 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.audit;

import org.filesys.debug.DebugInterface;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * Audit Configuration Section Class
 *
 * @author gkspencer
 */
public class AuditConfigSection extends ConfigSection {

    // Audit configuration section name
    public static final String SectionName = "Audit";

    //  Debugging interface to use for audit log output
    private DebugInterface m_auditDev;
    private ConfigElement m_auditParams;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public AuditConfigSection(ServerConfiguration config) {
        super(SectionName, config);
    }

    /**
     * Return the debug interface used for audit logging.
     *
     * @return DebugInterface
     */
    public final DebugInterface getAudit() {
        return m_auditDev;
    }

    /**
     * Return the audit device initialization parameters
     *
     * @return ConfigElement
     */
    public final ConfigElement getAuditParameters() {
        return m_auditParams;
    }

    /**
     * Determine if the configuration has a valid debug interface.
     *
     * @return boolean
     */
    public final boolean hasAudit() {
        return m_auditDev != null ? true : false;
    }

    /**
     * Set the debug interface to be used to output audit log messages.
     *
     * @param dbgClass String
     * @param params   ConfigElement
     * @return int
     * @exception InvalidConfigurationException Error setting the debug interface
     */
    public final int setAudit(String dbgClass, ConfigElement params)
            throws InvalidConfigurationException {

        int sts = ConfigurationListener.StsIgnored;

        try {

            //  Check if the debug device is being set
            if (dbgClass != null) {

                //  Validate the debug output class
                Object obj = Class.forName(dbgClass).newInstance();

                //  Check if the debug class implements the Debug interface
                if (obj instanceof DebugInterface) {

                    //  Initialize the debug output class
                    DebugInterface dbg = (DebugInterface) obj;
                    dbg.initialize(params, getServerConfiguration());

                    //  Set the debug class and initialization parameters
                    m_auditDev = dbg;
                    m_auditParams = params;

                    //  Update the global audit interface
                    Audit.setAuditInterface(dbg);
                } else
                    throw new InvalidConfigurationException("Audit Debugclass does not implement the Debug interface");
            } else {

                //  Clear the audit debug device and parameters
                m_auditDev = null;
                m_auditParams = null;
            }
        }
        catch (ClassNotFoundException ex) {
            throw new InvalidConfigurationException("Audit Debugclass not found, " + dbgClass);
        }
        catch (IllegalAccessException ex) {
            throw new InvalidConfigurationException("Cannot load audit debugclass " + dbgClass + ", access error");
        }
        catch (InstantiationException ex) {
            throw new InvalidConfigurationException("Cannot instantiate audit debugclass " + dbgClass);
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("Failed to initialize audit debug class, " + ex.toString());
        }

        //  Return the change status
        return sts;
    }

    /**
     * Close the configuration section, perform any cleanup
     */
    public void closeConfig() {

        // Close the audit log debug interface
        if (m_auditDev != null) {
            m_auditDev.close();
            m_auditDev = null;
        }
    }
}
