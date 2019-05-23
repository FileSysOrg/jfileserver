@echo off
echo Alfresco JLAN Server Tests
java -cp .\jars\alfresco-jlan-full.jar;.\libs\cryptix-jce-provider.jar org.filesys.jlan.test.cluster.ClusterTest clusterTests.xml
