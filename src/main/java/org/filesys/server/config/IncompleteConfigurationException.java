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
 * <p>Indicates that the server configuration is incomplete, and the server cannot be started.
 *
 * <p>The server name, domain name and network broadcast mask are the minimum parameters that must be specified
 * for a server configuration.
 *
 * @author gkspencer
 */
public class IncompleteConfigurationException extends Exception {

    private static final long serialVersionUID = 6805142016306543355L;

    /**
     * IncompleteConfigurationException constructor.
     */
    public IncompleteConfigurationException() {
        super();
    }

    /**
     * IncompleteConfigurationException constructor.
     *
     * @param s java.lang.String
     */
    public IncompleteConfigurationException(String s) {
        super(s);
    }
}
