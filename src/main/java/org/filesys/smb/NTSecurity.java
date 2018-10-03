/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb;

/**
 * NT Security Descriptor Constants Class
 *
 * @author gkspencer
 */
public class NTSecurity {

    //	Get/set security descriptor flags
    public final static int Owner				= 0x0001;
    public final static int Group				= 0x0002;
    public final static int DACL				= 0x0004;
    public final static int SACL				= 0x0008;

    //	Security flags
    public static final int ContextTracking		= 0x00040000;
    public static final int EffectiveOnly		= 0x00080000;


}
