JFileServer - The Java File Server
==================================

Introduction
------------
JFileServer is a Java based file server that currently supports the SMB/CIFS, FTP/FTPS
and NFS protocols. A virtual filesystem interface allows custom filesystems to be implemented,
with a database filesystem framework that makes it easy to use a database to store the filesystem
metadata, and optionally the file data.

The file server is highly scaleable using asynchronous I/O, thread pools and memory pools
to optimise the throughput. The server may be clustered for additional scaleability based
on the Hazelcast cluster technology.

An Enterprise add-on module adds support for SMBv2 (higher performance) and SMBv3 (full encryption).

History
-------
The JFileServer code originally started out as the JLAN Server, the first version being
released by Starlasoft back in the late 1990's with basic SMB1 support. As development of
the JLAN Server continued, SMB1 support was enhanced, and FTP and NFS support were added,
along with the database filesystem framework and smart caching.

In 2007 Alfresco Software bought the rights to the JLAN Server code rebranding it as Alfresco-JLAN,
and releasing the code as open source. Development continued with the addition of support for 
NTLMv2 and Kerberos enterprise authentication, scaleability enhancements using NIO, thread
pools and memory pools, plus cluster support using Hazelcast, and FTPS support.

In recent years no new development has been done on the Alfresco-JLAN code.

At the beginning of 2018 we forked the Alfresco-JLAN code to create the JFileServer project
to bring the source code up to date, and continue development of the file server.

License
-------
The core JFileServer library and components such as the database filesystem implementations
are licensed under the same GNU LGPL v3.0 license as the original Alfresco-JLAN code.

JDK Version
-----------
The JFileServer code uses features of JDK version 7 onwards.

Docker
------
Pre-configured setups of the core JFileServer and various database fileserver implementations
are available as Docker images which can either be used as they are or used as the starting
point for your own configurations.

For a list of the available Docker images see <https://hub.docker.com/r/filesysorg/>.

Website
-------
The main website can be found at <http://www.filesys.org>.

Additional information and documentation will be added there in the near future.

