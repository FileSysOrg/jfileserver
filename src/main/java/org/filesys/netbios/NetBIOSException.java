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

package org.filesys.netbios;

/**
 * NetBIOS exception class.
 *
 * @author gkspencer
 */
public class NetBIOSException extends Exception {

    private static final long serialVersionUID = -7071184447933421306L;

    /**
     * NetBIOSException constructor comment.
     */
    public NetBIOSException() {
        super();
    }

    /**
     * NetBIOSException constructor comment.
     *
     * @param s java.lang.String
     */
    public NetBIOSException(String s) {
        super(s);
    }
}
