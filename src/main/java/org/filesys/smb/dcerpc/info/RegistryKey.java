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

package org.filesys.smb.dcerpc.info;

import java.util.*;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEReadable;
import org.filesys.smb.dcerpc.PolicyHandle;

/**
 * Registry Key Class
 *
 * @author gkspencer
 */
public class RegistryKey extends PolicyHandle implements DCEReadable {

    // Link to parent registry key
    private RegistryKey m_parent;

    // Link to next sibling registry key
    private RegistryKey m_sibling;

    // Link to child registry key
    private RegistryKey m_child;

    /**
     * Default constructor
     */
    public RegistryKey() {
    }

    /**
     * Class constructor
     *
     * @param name String
     */
    public RegistryKey(String name) {
        setName(name);
    }

    /**
     * Class constructor
     *
     * @param parent RegistryKey
     */
    public RegistryKey(RegistryKey parent) {
        setParent(parent);
    }

    /**
     * Class constructor
     *
     * @param name   String
     * @param parent RegistryKey
     */
    public RegistryKey(String name, RegistryKey parent) {
        setName(name);
        setParent(parent);
    }

    /**
     * Check if the registry key is open, ie. a handle has been allocated
     *
     * @return boolean
     */
    public final boolean isOpen() {
        return isValid();
    }

    /**
     * Mark the registry key as closed, clear the handle
     */
    public final void markClosed() {
        clearHandle();
    }

    /**
     * Check if the registry key has a parent key
     *
     * @return boolean
     */
    public final boolean hasParent() {
        return m_parent != null ? true : false;
    }

    /**
     * Return the parent key
     *
     * @return RegistryKey
     */
    public final RegistryKey getParent() {
        return m_parent;
    }

    /**
     * Check if the registry key has a sibling
     *
     * @return boolean
     */
    public final boolean hasSibling() {
        return m_sibling != null ? true : false;
    }

    /**
     * Return the sibling key
     *
     * @return RegistryKey
     */
    public final RegistryKey getSibling() {
        return m_sibling;
    }

    /**
     * Check if the key has child keys
     *
     * @return boolean
     */
    public final boolean hasChild() {
        return m_child != null ? true : false;
    }

    /**
     * Return the child registry key
     *
     * @return RegistryKey
     */
    public final RegistryKey getChild() {
        return m_child;
    }

    /**
     * Return the full registry key name by walking back to the root registry key and prepending key
     * names.
     *
     * @return String
     */
    public final String getFullName() {

        // Check if the key has a parent, if not then just return the key name
        if (hasParent() == false)
            return getName();

        // Build the list of registry keys
        Vector keys = new Vector();
        RegistryKey curKey = this;

        while (curKey != null) {

            // Add the key to the list and get the parent key
            keys.addElement(curKey);
            curKey = curKey.getParent();
        }

        // Build the full key name
        StringBuffer keyStr = new StringBuffer(256);

        for (int i = keys.size() - 1; i >= 0; i--) {

            // Get the current registry key
            curKey = (RegistryKey) keys.elementAt(i);
            if (keyStr.length() > 0)
                keyStr.append("\\");
            keyStr.append(curKey.getName());
        }

        // Return the full key name
        return keyStr.toString();
    }

    /**
     * Read the key details from a DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Read the Unicode header and name string
        int len = buf.getUnicodeHeaderLength();
        if (len > 0)
            setName(buf.getString());

        if (buf.getPointer() != 0)
            buf.skipBytes(4);
        if (buf.getPointer() != 0)
            buf.skipBytes(4);
        if (buf.getPointer() != 0)
            buf.skipBytes(4);
    }

    /**
     * Read the strings for this object from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException {

        // Not required
    }

    /**
     * Set the parent registry key for this key
     *
     * @param parent RegistryKey
     */
    protected final void setParent(RegistryKey parent) {
        m_parent = parent;
    }

    /**
     * Set the sibling registry key
     *
     * @param key RegistryKey
     */
    public final void setSibling(RegistryKey key) {
        m_sibling = key;
    }

    /**
     * Set the child registry key
     *
     * @param key RegistryKey
     */
    public final void setChild(RegistryKey key) {
        m_child = key;
    }

    /**
     * Return the registry key as a string
     *
     * @return String
     */
    public String toString() {
        return getFullName();
    }
}
