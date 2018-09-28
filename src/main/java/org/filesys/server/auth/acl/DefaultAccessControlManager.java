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

package org.filesys.server.auth.acl;

import java.util.Enumeration;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.springframework.extensions.config.ConfigElement;

/**
 * Default Access Control Manager Class
 *
 * <p>Default access control manager implementation.
 *
 * @author gkspencer
 */
public class DefaultAccessControlManager implements AccessControlManager {

    //	Access control factory
    private AccessControlFactory m_factory;

    //	Debug enable flag
    private boolean m_debug;

    /**
     * Class constructor
     */
    public DefaultAccessControlManager() {

        //	Create the access control factory
        m_factory = new AccessControlFactory();
    }

    /**
     * Check if the session has access to the shared device.
     *
     * @param sess  SrvSession
     * @param share SharedDevice
     * @return int
     */
    public int checkAccessControl(SrvSession sess, SharedDevice share) {

        //	Check if the shared device has any access control configured
        if (share.hasAccessControls() == false) {

            //	DEBUG
            if (Debug.EnableInfo && hasDebug())
                sess.debugPrintln("Check access control for " + share.getName() + ", no ACLs");

            //	Allow full access to the share
            return AccessControl.ReadWrite;
        }

        //	Process the access control list
        AccessControlList acls = share.getAccessControls();
        int access = AccessControl.Default;

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            sess.debugPrintln("Check access control for " + share.getName() + ", ACLs=" + acls.numberOfControls());

        for (int i = 0; i < acls.numberOfControls(); i++) {

            //	Get the current access control and run
            AccessControl acl = acls.getControlAt(i);
            int curAccess = acl.allowsAccess(sess, share, this);

            //	Debug
            if (Debug.EnableInfo && hasDebug())
                sess.debugPrintln("  Check access ACL=" + acl + ", access=" + AccessControl.asAccessString(curAccess));

            //	Update the allowed access
            if (curAccess != AccessControl.Default)
                access = curAccess;
        }

        //	Check if the default access level is still selected, if so then get the default level from the
        //	access control list
        if (access == AccessControl.Default) {

            //	Use the default access level
            access = acls.getDefaultAccessLevel();

            //	Debug
            if (Debug.EnableInfo && hasDebug())
                sess.debugPrintln("Access defaulted=" + AccessControl.asAccessString(access) + ", share=" + share);
        } else if (Debug.EnableInfo && hasDebug())
            sess.debugPrintln("Access allowed=" + AccessControl.asAccessString(access) + ", share=" + share);

        //	Return the access type
        return access;
    }

    /**
     * Filter the list of shared devices to return a list that contains only the shares that
     * are visible or accessible by the session.
     *
     * @param sess   SrvSession
     * @param shares SharedDeviceList
     * @return SharedDeviceList
     */
    public SharedDeviceList filterShareList(SrvSession sess, SharedDeviceList shares) {

        //	Check if the share list is valid or empty
        if (shares == null || shares.numberOfShares() == 0)
            return shares;

        //	Debug
        if (Debug.EnableInfo && hasDebug())
            sess.debugPrintln("Filter share list for " + sess + ", shares=" + shares);

        //	For each share in the list check the access, remove any shares that the session does not
        //	have access to.
        SharedDeviceList filterList = new SharedDeviceList();
        Enumeration enm = shares.enumerateShares();

        while (enm.hasMoreElements()) {

            //	Get the current share
            SharedDevice share = (SharedDevice) enm.nextElement();

            //	Check if the share has any access controls
            if (share.hasAccessControls()) {

                //	Check if the session has access to this share
                int access = checkAccessControl(sess, share);
                if (access != AccessControl.NoAccess)
                    filterList.addShare(share);
            } else {

                //	Add the share to the filtered list
                filterList.addShare(share);
            }
        }

        //	Debug
        if (Debug.EnableInfo && hasDebug())
            sess.debugPrintln("Filtered share list " + filterList);

        //	Return the filtered share list
        return filterList;
    }

    /**
     * Initialize the access control manager
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the access control manager
     */
    public void initialize(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException {

        //	Check if debug output is enabled
        if (params.getChild("debug") != null)
            setDebug(true);

        //	Add the default access control types
        addAccessControlType(new UserAccessControlParser());
        addAccessControlType(new ProtocolAccessControlParser());
        addAccessControlType(new DomainAccessControlParser());
        addAccessControlType(new IpAddressAccessControlParser());
        addAccessControlType(new GidAccessControlParser());
        addAccessControlType(new UidAccessControlParser());

        //	Check if there are any custom access control rules
        ConfigElement ruleList = params.getChild("rule");

        if (ruleList != null && ruleList.hasChildren()) {

            //	Add the custom rule types
            List<ConfigElement> rules = ruleList.getChildren();

            for (ConfigElement ruleVal : rules) {

                if (ruleVal.getValue() == null || ruleVal.getValue().length() == 0)
                    throw new InvalidConfigurationException("Empty rule definition");

                //	Create an instance of the rule parser and check that it is based on the access control
                //	parser class.
                try {

                    //	Create an instance of the rule parser class
                    Object ruleObj = Class.forName(ruleVal.getValue()).newInstance();

                    //	Check if the class is an access control parser
                    if (ruleObj instanceof AccessControlParser) {

                        //	Add the new rule type
                        addAccessControlType((AccessControlParser) ruleObj);
                    }
                }
                catch (ClassNotFoundException ex) {
                    throw new InvalidConfigurationException("Rule class not found, " + ruleVal.getValue());
                }
                catch (InstantiationException ex) {
                    throw new InvalidConfigurationException("Error creating rule object, " + ruleVal.getValue() + ", " + ex.toString());
                }
                catch (IllegalAccessException ex) {
                    throw new InvalidConfigurationException("Error creating rule object, " + ruleVal.getValue() + ", " + ex.toString());
                }
            }
        }
    }

    /**
     * Create an access control.
     *
     * @param type   String
     * @param params ConfigElement
     * @return AccessControl
     * @exception ACLParseException Error parsing the ACL
     * @exception InvalidACLTypeException Invalid ACL type
     */
    public AccessControl createAccessControl(String type, ConfigElement params)
            throws ACLParseException, InvalidACLTypeException {

        //	Use the access control factory to create the access control instance
        return m_factory.createAccessControl(type, params);
    }

    /**
     * Add an access control parser to the list of available access control types.
     *
     * @param parser AccessControlParser
     */
    public void addAccessControlType(AccessControlParser parser) {

        //	Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("AccessControlManager Add rule type " + parser.getType());

        //	Add the new access control type to the factory
        m_factory.addParser(parser);
    }

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public final void setDebug(boolean dbg) {
        m_debug = dbg;
    }
}
