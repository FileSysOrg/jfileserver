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

package org.filesys.server;

/**
 * Request Post Processor Class
 *
 * @author gkspencer
 */
public abstract class RequestPostProcessor {

    // Linked list of request post processors
    private static ThreadLocal<RequestPostProcessor> m_postProcessorList = new ThreadLocal<RequestPostProcessor>();

    // Link to the next post processor
    private RequestPostProcessor m_nextLink;

    /**
     * Default constructor
     */
    public RequestPostProcessor() {
    }

    /**
     * Link a post processor to this processor
     *
     * @param postProc RequestPostProcessor
     */
    private void linkPostProcessor(RequestPostProcessor postProc) {
        m_nextLink = postProc;
    }

    /**
     * Check if the post processor has a linked post processor
     *
     * @return boolean
     */
    private boolean hasLinkedPostProcessor() {
        return m_nextLink != null ? true : false;
    }

    /**
     * Returned the linked post processor
     *
     * @return RequestPostProcessor
     */
    private RequestPostProcessor getLinkedPostProcessor() {
        return m_nextLink;
    }

    /**
     * Run the post processor
     */
    public abstract void runProcessor();

    /**
     * Queue a post processor
     *
     * @param postProc RequestPostProcessor
     */
    public static void queuePostProcessor(RequestPostProcessor postProc) {

        // Check if there is an existing post processor
        RequestPostProcessor curProc = m_postProcessorList.get();
        if (curProc == null) {

            // No queue, start a queue with this post processor
            m_postProcessorList.set(postProc);
        } else {

            // Find the last post processor in the list
            while (curProc.hasLinkedPostProcessor())
                curProc = curProc.getLinkedPostProcessor();

            // Add the new post processor to the end of the list
            curProc.linkPostProcessor(postProc);
        }
    }

    /**
     * Check if there are any queued post processors
     *
     * @return boolean
     */
    public static boolean hasPostProcessor() {
        return m_postProcessorList.get() != null ? true : false;
    }

    /**
     * Dequeue a post processor from the queue
     *
     * @return RequestPostProcessor
     */
    public static RequestPostProcessor dequeuePostProcessor() {

        // Get the head of the post processor queue
        RequestPostProcessor headProc = m_postProcessorList.get();
        if (headProc != null) {

            // Unlink the head of the queue from the list
            m_postProcessorList.set(headProc.getLinkedPostProcessor());
        }

        // Return the head of the queue
        return headProc;
    }

    /**
     * Search for a post processor of the specified type within the list
     *
     * @param postProcClass Class
     * @return RequestPostProcessor
     */
    public static RequestPostProcessor findPostProcessor(Class postProcClass) {

        // Check if there are any post processors in the queue
        if (hasPostProcessor() == false)
            return null;

        // Walk the list of post processors until we find a match or reach the end of the queue
        RequestPostProcessor postProc = m_postProcessorList.get();

        while (postProc != null && postProc.getClass() != postProcClass)
            postProc = postProc.getLinkedPostProcessor();

        // Return the matching post processor, or null if not found
        return postProc;
    }

    /**
     * Remove the post processor from the queue
     *
     * @param postProcessor RequestPostProcessor
     */
    public static void removePostProcessorFromQueue(RequestPostProcessor postProcessor) {

        // Find the post processor in the queue
        RequestPostProcessor curPostProc = m_postProcessorList.get();
        RequestPostProcessor prevPostProc = null;

        while (curPostProc != null && curPostProc != postProcessor) {
            prevPostProc = curPostProc;
            curPostProc = curPostProc.getLinkedPostProcessor();
        }

        // If we found the post processor then unlink it
        if (curPostProc != null) {

            // Check if we are replacing the head of the queue
            if (prevPostProc == null) {

                // New head of the queue
                m_postProcessorList.set(postProcessor.getLinkedPostProcessor());
            } else {

                // Unlink from the queue
                prevPostProc.linkPostProcessor(postProcessor.getLinkedPostProcessor());
            }
        }

    }
}
