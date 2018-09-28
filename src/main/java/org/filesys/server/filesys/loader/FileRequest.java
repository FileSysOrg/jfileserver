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

package org.filesys.server.filesys.loader;

import java.util.StringTokenizer;

import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * File Request Class
 *
 * <p>File loader file load/save requests class.
 *
 * @author gkspencer
 */
public class FileRequest {

    //	Request types
    public final static int LOAD        = 0;
    public final static int SAVE        = 1;
    public final static int TRANSSAVE   = 2;
    public final static int DELETE  = 3;

    //  Standard attribute names
    public final static String AttrUserName = "UserName";
    public final static String AttrProtocol = "Protocol";

    //	File request type
    private int m_reqType;

    //	Thread id of the worker thread that is servicing the request
    private int m_threadId;

    //	Transaction id, if this request is part of a transaction else -1
    private int m_tranId = -1;
    private boolean m_lastFile;

    // Additional attributes saved as part of the file request
    private NameValueList m_attributes;

    /**
     * Class constructor
     *
     * @param typ int
     */
    protected FileRequest(int typ) {
        m_reqType = typ;
    }

    /**
     * Return the file request type
     *
     * @return int
     */
    public final int isType() {
        return m_reqType;
    }

    /**
     * Return the thread id of the worker thread servicing the request
     *
     * @return int
     */
    public final int getThreadId() {
        return m_threadId;
    }

    /**
     * Check if the request is the last file in the transaction
     *
     * @return boolean
     */
    public final boolean isLastTransactionFile() {
        return m_lastFile;
    }

    /**
     * Check if the request is part of a transaction
     *
     * @return boolean
     */
    public final boolean isTransaction() {
        return m_tranId != -1 ? true : false;
    }

    /**
     * Return the transaction id
     *
     * @return int
     */
    public final int getTransactionId() {
        return m_tranId;
    }

    /**
     * Set the thread id of the worker thread servicing the request
     *
     * @param id int
     */
    public final void setThreadId(int id) {
        m_threadId = id;
    }

    /**
     * Set the transaction id
     *
     * @param id int
     */
    public final void setTransactionId(int id) {
        m_tranId = id;
    }

    /**
     * Set the transaction id and last file of transaction flag
     *
     * @param id   int
     * @param last boolean
     */
    public final void setTransactionId(int id, boolean last) {
        m_tranId = id;
        m_lastFile = last;
    }

    /**
     * Check if the file request has any attributes
     *
     * @return boolean
     */
    public final boolean hasAttributes() {
        return m_attributes != null && m_attributes.numberOfItems() > 0 ? true : false;
    }

    /**
     * Return the associated attributes list
     *
     * @return NameValueList
     */
    public final NameValueList getAttributes() {
        return m_attributes;
    }

    /**
     * Add an attribute
     *
     * @param attr NameValue
     */
    public final void addAttribute(NameValue attr) {
        if (m_attributes == null)
            m_attributes = new NameValueList();
        m_attributes.addItem(attr);
    }

    /**
     * Check for the specified attribute
     *
     * @param attrName String
     * @return NameValue
     */
    public final NameValue hasAttribute(String attrName) {
        NameValue attr = null;

        if (m_attributes != null)
            attr = m_attributes.findItem(attrName);
        return attr;
    }

    /**
     * Get the attributes as a comma delimted list
     *
     * @return String
     */
    public final String getAttributesString() {

        // Check if there are any attributes
        if (hasAttributes() == false)
            return "";

        // Build the attributes string
        StringBuffer str = new StringBuffer(256);
        NameValueList attrList = getAttributes();

        for (int i = 0; i < attrList.numberOfItems(); i++) {

            // Add the current attribute string
            NameValue attr = attrList.getItemAt(i);

            if (str.length() > 0)
                str.append(",");

            str.append(attr.getName());
            str.append("=");
            str.append(attr.getValue());
        }

        return str.toString();
    }

    /**
     * Set the attributes from a comma delimted list
     *
     * @param attrs String
     */
    public final void setAttributes(String attrs) {

        // Check if the attributes string is valid
        if (attrs == null || attrs.length() == 0) {
            m_attributes = null;
            return;
        }

        // Allocate the list for the attributes
        m_attributes = new NameValueList();

        // Parse the attributes string
        StringTokenizer token = new StringTokenizer(attrs, ",");

        while (token.hasMoreTokens()) {

            // Get the current name/value string
            String nameVal = token.nextToken();
            int pos = nameVal.indexOf('=');

            if (pos != -1) {

                // Split the name and value
                String name = nameVal.substring(0, pos);
                String value = nameVal.substring(pos + 1);

                // Add the attribute
                m_attributes.addItem(new NameValue(name, value));
            }
        }
    }
}
