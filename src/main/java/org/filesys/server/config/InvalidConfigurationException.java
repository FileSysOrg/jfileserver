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

package org.filesys.server.config;

/**
 * <p>Indicates that one or more parameters in the server configuration are not valid.
 *
 * @author gkspencer
 */
public class InvalidConfigurationException extends Exception {

    private static final long serialVersionUID = 4660972667850041322L;

    //	Chained exception details
    private Exception m_exception;

    /**
     * InvalidConfigurationException constructor.
     */
    public InvalidConfigurationException() {
        super();
    }

    /**
     * InvalidConfigurationException constructor.
     *
     * @param s java.lang.String
     */
    public InvalidConfigurationException(String s) {
        super(s);
    }

    /**
     * InvalidConfigurationException constructor.
     *
     * @param s  java.lang.String
     * @param ex Exception
     */
    public InvalidConfigurationException(String s, Exception ex) {
        super(s, ex);
        m_exception = ex;
    }

    /**
     * Check if there is a chained exception
     *
     * @return boolean
     */
    public final boolean hasChainedException() {
        return m_exception != null ? true : false;
    }

    /**
     * Return the chained exception details
     *
     * @return Exception
     */
    public final Exception getChainedException() {
        return m_exception;
    }
}
