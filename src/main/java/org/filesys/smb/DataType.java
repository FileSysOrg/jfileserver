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

package org.filesys.smb;

/**
 * SMB data type class.
 *
 * <p>This class contains the data types that are used within an SMB protocol
 * packet.
 *
 * @author gkspencer
 */
public class DataType {

    // SMB data types
    public static final char DataBlock      = (char) 0x01;
    public static final char Dialect        = (char) 0x02;
    public static final char Pathname       = (char) 0x03;
    public static final char ASCII          = (char) 0x04;
    public static final char VariableBlock  = (char) 0x05;
}
