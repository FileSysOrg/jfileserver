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

package org.filesys.app;

import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.smb.util.DriveMappingList;

/**
 *  Drive Mappings Configuration Section Class
 *
 * @author gkspencer
 */
public class DriveMappingsConfigSection extends ConfigSection {

  // Drive mappings configuration section name
  public static final String SectionName = "DriveMappings";
  
  //  Win32 local drive mappings to be added when the SMB/CIFS server has started
  private DriveMappingList m_mappedDrives;
  
  //  Enable debug output
  private boolean m_debug;
  
  /**
   * Class constructor
   * 
   * @param config ServerConfiguration
   */
  public DriveMappingsConfigSection(ServerConfiguration config) {
    super( SectionName, config);
  }

  /**
   * Check if debug output is enabled
   * 
   * @return boolean
   */
  public final boolean hasDebug() {
    return m_debug;
  }
  
  /**
   * Determine if there are mapped drives specified to be added when the SMB/CIFS server has started
   * 
   * @return boolean
   */
  public final boolean hasMappedDrives() {
    return m_mappedDrives != null ? true : false;
  }

  /**
   * Return the mapped drives list
   * 
   * @return DriveMappingList
   */
  public final DriveMappingList getMappedDrives() {
    return m_mappedDrives;
  }
  
  /**
   * Add a list of mapped drives
   *
   * @param mappedDrives DriveMappingList
   * @return int
   * @exception InvalidConfigurationException Set configuration failed
   */
  public final int setMappedDrives(DriveMappingList mappedDrives)
    throws InvalidConfigurationException {
      
    //  Inform listeners, validate the configuration change
    int sts = fireConfigurationChange(ConfigId.SMBMappedDrives, mappedDrives);
    m_mappedDrives = mappedDrives;
    
    //  Return the change status
    return sts;
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
