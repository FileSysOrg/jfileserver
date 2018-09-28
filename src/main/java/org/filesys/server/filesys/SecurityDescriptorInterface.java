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

package org.filesys.server.filesys;

import org.filesys.server.SrvSession;
import org.filesys.smb.nt.SecurityDescriptor;
import org.filesys.smb.server.SMBSrvException;

/**
 * Security Descriptor Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement loading and saving of per file security descriptors.
 *
 * @author gkspencer
 */
public interface SecurityDescriptorInterface {

    /**
     * Return the security descriptor length for the specified file
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param netFile Network file
     * @return int
     * @throws SMBSrvException SMB error
     */
    public int getSecurityDescriptorLength(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws SMBSrvException;

    /**
     * Load a security descriptor for the specified file
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param netFile Network file
     * @return SecurityDescriptor
     * @throws SMBSrvException SMB error
     */
    public SecurityDescriptor loadSecurityDescriptor(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws SMBSrvException;

    /**
     * Save the security descriptor for the specified file
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param netFile Network file
     * @param secDesc Security descriptor
     * @throws SMBSrvException SMB error
     */
    public void saveSecurityDescriptor(SrvSession sess, TreeConnection tree, NetworkFile netFile, SecurityDescriptor secDesc)
            throws SMBSrvException;
}
