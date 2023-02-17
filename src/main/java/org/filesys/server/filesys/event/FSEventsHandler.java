/*
 * Copyright (C) 2021 GK Spencer
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
package org.filesys.server.filesys.event;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.*;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Filesystem Events Handler Class
 *
 * <p>The filesystem events handler receives filesystem change events which it then passes to change
 * event handler implementations from a background thread</p>
 *
 * @author gkspencer
 */
public class FSEventsHandler {

    //	Change notification processing thread(s)
    private Map<Long, FSEventProcessor> m_eventThreads;

    // List of change event handlers
    private ChangeEventHandlerList m_handlerList;
    private boolean m_wantAllEvents;

    //	Change events queue(s)
    private Map<Long, ChangeEventList> m_eventLists;

    //	Debug output enable
    private boolean m_debug = true;

    //	Shutdown request flag
    private boolean m_shutdown;

    /**
     * Event Processing Thread Class
     *
     * <p>Wait for events on the specified queue and process the events
     */
    protected class FSEventProcessor extends Thread {

        // Event queue this event processor is monitoring
        private ChangeEventList m_eventQueue;

        // Associated filesyetem context
        private DiskDeviceContext m_diskCtx;

        // Shutdown request flag
        private boolean m_shutdown = false;

        /**
         * Class constructor
         *
         * @param eventQueue ChangeEventList
         * @param diskCtx DiskDeviceContext
         */
        public FSEventProcessor( ChangeEventList eventQueue, DiskDeviceContext diskCtx) {
            m_eventQueue = eventQueue;
            m_diskCtx = diskCtx;

            // Set the processor thread name and start the thread
            setName("FSEvents_" + m_diskCtx.getShareName());
            setDaemon( true);
            start();
        }

        @Override
        public void run() {

            // Get the thread name, that includes the filesystem name
            String threadName = "[" + Thread.currentThread().getName() + "] ";

            //	Loop until shutdown
            while (m_shutdown == false) {

                //	Wait for some events to process
                synchronized (m_eventQueue) {
                    try {
                        m_eventQueue.wait();
                    }
                    catch (InterruptedException ex) {
                    }
                }

                //	Check if the shutdown flag has been set
                if (m_shutdown == true)
                    break;

                //	Loop until all pending events have been processed
                while (m_eventQueue.numberOfEvents() > 0) {

                    //	Remove the event at the head of the queue
                    ChangeEvent evt = null;

                    synchronized (m_eventQueue) {
                        evt = m_eventQueue.removeEventAt(0);
                    }

                    //	Check if the event is valid
                    if (evt == null)
                        break;

                    try {

                        // DEBUG
                        if ( Debug.EnableInfo && hasDebug())
                            Debug.println(threadName + "Process event " + evt);

                        // Call the change event handler(s)
                        for ( ChangeEventHandler handler : m_handlerList) {

                            try {

                                // Call the current event handler
                                if ( handler.wantFSEvent( evt.isChange(), evt.isDirectory(), m_diskCtx))
                                    handler.handleFSEvent( evt, m_diskCtx);
                            }
                            catch ( Exception ex) {

                                // DEBUG
                                if ( Debug.EnableInfo && hasDebug()) {
                                    Debug.println(threadName + "Handler " + handler.getName() + " threw exception " + ex);
                                    ex.printStackTrace();
                                }
                            }
                        }
                    }
                    catch (Throwable ex) {
                        Debug.println(threadName + "Thread exception");
                        Debug.println(ex);
                    }
                }
            }

            //	DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println(threadName + "Thread exit");
        }

        /**
         * Shutdown the connection reaper
         */
        public final void shutdownRequest() {
            m_shutdown = true;
            m_eventQueue.notify();
        }
    }

    /**
     * Default constructor
     */
    public FSEventsHandler() {

        // Allocate the event list map
        m_eventLists = new HashMap<>();

        // Allocate the event processor map
        m_eventThreads = new HashMap<>();
    }

    /**
     * Add a change event handler
     *
     * @param handler ChangeEventHandler
     */
    public synchronized final void addChangeHandler( ChangeEventHandler handler) {

        if (m_handlerList == null)
            m_handlerList = new ChangeEventHandlerList();
        m_handlerList.addHandler( handler);

        // Check if the handler wants all filesystem events
        if ( handler.wantAllFSEvents())
            m_wantAllEvents = true;

        // DEBUG
        if ( Debug.EnableInfo && hasDebug())
            Debug.println("[FSEvents] Add change handler name=" + handler.getName() + ", priority=" + handler.getPriority() + ", wantAll=" + handler.wantAllFSEvents());
    }

    /**
     * Get the specified change handler by name
     *
     * @param name String
     * @return ChangeEventHandler
     */
    public final ChangeEventHandler findHandler( String name) {
        if ( m_handlerList == null || m_handlerList.numberOfHandlers() == 0)
            return null;
        return m_handlerList.findHandler( name);
    }

    /**
     * Register the filesystem events handler with a filesystem
     *
     * @param diskCtx DiskDeviceContext
     */
    public void registerFilesystem( DiskDeviceContext diskCtx) {

        // Create an event queue for the filesystem
        ChangeEventList eventQueue = m_eventLists.get( diskCtx.getUniqueId());

        if ( eventQueue == null) {

            // Create an event queue for the filesystem
            eventQueue = new ChangeEventList();
            m_eventLists.put( diskCtx.getUniqueId(), eventQueue);
        }

        // Create a thread to process events on the queue
        FSEventProcessor fsProc = m_eventThreads.get( diskCtx.getUniqueId());

        if ( fsProc == null) {

            // Creat the event processor thread
            fsProc = new FSEventProcessor(eventQueue, diskCtx);
            m_eventThreads.put(diskCtx.getUniqueId(), fsProc);
        }

        // Call each change handler to register the filesystem
        for ( ChangeEventHandler handler : m_handlerList) {
            try {
                handler.registerFilesystem( diskCtx);
            }
            catch ( Exception ex) {

                //	DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("Error registering filesystem with handler " + handler.getName() + ", ex=" + ex);
            }
        }

        // DEBUG
        if ( Debug.EnableInfo && hasDebug())
            Debug.println("[FSEvents] Registered filesystem " + diskCtx.getShareName() + ", uniqueId=0x" + Long.toHexString( diskCtx.getUniqueId()));
    }

    /**
     * Unregister a filesystem from the filesystem events handler
     *
     * @param diskCtx DiskDeviceContext
     */
    public void unregisterFilesystem( DiskDeviceContext diskCtx) {

        // Remove the event queue
        ChangeEventList eventQueue = m_eventLists.remove( diskCtx.getUniqueId());

        // Remove the event processor and shutdown the processing thread
        FSEventProcessor fsProc = m_eventThreads.remove( diskCtx.getUniqueId());

        if ( fsProc != null) {

            // Shutdown the event processor thread
            fsProc.shutdownRequest();

            // Call each change handler to unregister the filesystem
            for ( ChangeEventHandler handler : m_handlerList) {
                try {
                    handler.unregisterFilesystem( diskCtx);
                }
                catch ( Exception ex) {

                    //	DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("Error un-registering filesystem with handler " + handler.getName() + ", ex=" + ex);
                }
            }

            // DEBUG
            if ( Debug.EnableInfo && hasDebug())
                Debug.println("[FSEvents] Unregistered filesystem " + diskCtx.getShareName() + ", uniqueId=0x" + Long.toHexString( diskCtx.getUniqueId()));
        }
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
     * Enable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Shutdown the change notification processing threads
     */
    public final void shutdownRequest() {

        // DEBUG
        if ( Debug.EnableInfo && hasDebug())
            Debug.println("[FSEvents] Shutdown requested");

        //	Check if the processing thread is valid
        m_eventThreads.forEach(( key, fsProc) -> {
            fsProc.shutdownRequest();
        });

        // Close the change event handlers
        m_handlerList.forEach(( handler) -> {
            try {
                handler.closeHandler();
            }
            catch( Exception ex) {
            }
        } );
    }

    /**
     * Check if any event handlers are interested in an event that is about to be queued, if not then the event
     * will be ignored and not queued
     *
     * @param change FSChange
     * @param dir boolean
     * @param diskCtx DiskDeviceContext
     * @return boolean
     */
    protected final boolean wantEvent( FSChange change, boolean dir, DiskDeviceContext diskCtx) {
        if ( m_wantAllEvents)
            return true;

        for ( ChangeEventHandler handler : m_handlerList) {
            if ( handler.wantFSEvent( change, dir, diskCtx))
                return true;
        }

        return false;
    }

    /**
     * Queue a change notification event for processing
     *
     * @param evt NotifyChangeEvent
     * @param diskCtx DiskDeviceContext
     */
    protected final void queueNotification(ChangeEvent evt, DiskDeviceContext diskCtx) {

        // Set the filesystem id for the event
        evt.setFilesystemId( diskCtx.getUniqueId());

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[" + Thread.currentThread().getName() + "] Queue notification event=" + evt.toString());

        // Get the associated event queue for the filesystem
        ChangeEventList eventList = m_eventLists.get( diskCtx.getUniqueId());

        if ( eventList != null) {

            // Queue the notification event to the filesystem event handler thread
            synchronized (eventList) {

                // Add the event to the list
                eventList.addEvent(evt);

                // Notify the processing thread that there are events to process
                eventList.notifyAll();
            }
        }
    }

    /**
     * File changed notification
     *
     * @param change FSChange
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueFileChanged(FSChange change, NetworkFile netFile, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( change, false, diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( change, netFile.getFullName(), netFile.getFileId(), netFile.isDirectory()), diskCtx);
    }

    /**
     * File changed notification
     *
     * @param change FSChange
     * @param fInfo FileInfo
     * @param diskCtx DiskDeviceContext
     */
    public final void queueFileChanged(FSChange change, FileInfo fInfo, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( change, false, diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( change, fInfo.getPath(), fInfo.getFileId(), fInfo.isDirectory()), diskCtx);
    }

    /**
     * File/directory renamed notification
     *
     * @param oldName String
     * @param newName String
     * @param fInfo FileInfo
     * @param diskCtx DiskDeviceContext
     */
    public final void queueRename(String oldName, String newName, FileInfo fInfo, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.Renamed, fInfo.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.Renamed, newName, oldName, fInfo.getFileId(), fInfo.isDirectory()), diskCtx);
    }

    /**
     * File/directory renamed notification
     *
     * @param oldName String
     * @param newName String
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueRename(String oldName, String newName, NetworkFile netFile, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.Renamed, netFile.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.Renamed, newName, oldName, netFile.getFileId(), netFile.isDirectory()), diskCtx);
    }

    /**
     * Directory changed notification
     *
     * @param change FSChange
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueDirectoryChanged(FSChange change, NetworkFile netFile, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( change, true, diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( change, netFile.getFullName(), netFile.getFileId(), true), diskCtx);
    }

    /**
     * Directory changed notification
     *
     * @param change FSChange
     * @param fInfo FileInfo
     * @param diskCtx DiskDeviceContext
     */
    public final void queueDirectoryChanged(FSChange change, FileInfo fInfo, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( change, true, diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( change, fInfo.getPath(), fInfo, null), diskCtx);
    }

    /**
     * Attributes changed notification
     *
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueAttributesChanged(NetworkFile netFile, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.Attributes, netFile.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.Attributes, netFile.getFullName(), netFile.getFileId(), netFile.isDirectory()), diskCtx);
    }

    /**
     * Attributes changed notification
     *
     * @param path String
     * @param fInfo FileInfo
     * @param diskCtx DiskDeviceContext
     */
    public final void queueAttributesChanged(String path, FileInfo fInfo, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.Attributes, fInfo.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.Attributes, path, fInfo, null), diskCtx);
    }

    /**
     * File size changed notification
     *
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     * @param closed boolean
     */
    public final void queueFileSizeChanged(NetworkFile netFile, DiskDeviceContext diskCtx, boolean closed) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.Modified, false, diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.Modified, netFile, closed ? EnumSet.of(ChangeEvent.Flags.Closed) : null), diskCtx);
    }

    /**
     * Last write time changed notification
     *
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueLastWriteTimeChanged(NetworkFile netFile, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.LastWrite, netFile.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.LastWrite, netFile, null), diskCtx);
    }

    /**
     * Last write time changed notification
     *
     * @param path String
     * @param fInfo FileInfo
     * @param diskCtx DiskDeviceContext
     */
    public final void queueLastWriteTimeChanged(String path, FileInfo fInfo, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.LastWrite, fInfo.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.LastWrite, path, fInfo.getFileId(), fInfo.isDirectory()), diskCtx);
    }

    /**
     * Last access time changed notification
     *
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueLastAccessTimeChanged(NetworkFile netFile, DiskDeviceContext diskCtx) {

        // TODO: Not implemented
    }

    /**
     * Creation time changed notification
     *
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueCreationTimeChanged(NetworkFile netFile, DiskDeviceContext diskCtx) {

        // TODO: Not implemented
    }

    /**
     * Security descriptor changed notification
     *
     * @param netFile NetworkFile
     * @param diskCtx DiskDeviceContext
     */
    public final void queueSecurityDescriptorChanged(NetworkFile netFile, DiskDeviceContext diskCtx) {

        //	Check if file change events are required by any event handlers
        if ( wantEvent( FSChange.Security, netFile.isDirectory(), diskCtx) == false)
            return;

        //	Queue the change notification
        queueNotification(new ChangeEvent( FSChange.Security, netFile.getFullName(), netFile.getFileId(), netFile.isDirectory()), diskCtx);
    }
}
