package net.wasdev.wlp.maven.test.app;

import java.io.File;
import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * 
 * Web application test case
 * 
 */

public class DefaultAppDirectoryTest {

    public final String CONFIG_XML = "liberty-plugin-config.xml";

    @Test
    public void testConfigPropFileExist() throws Exception {
        File f = new File(CONFIG_XML);
        Assert.assertTrue(f.getCanonicalFile() + " doesn't exist", f.exists());
    }

    @Test
    public void testXmlElements() throws Exception {
        File in = new File(CONFIG_XML);
        FileInputStream input = new FileInputStream(in);
        
        // get input XML Document 
        DocumentBuilderFactory inputBuilderFactory = DocumentBuilderFactory.newInstance();
        inputBuilderFactory.setIgnoringComments(true);
        inputBuilderFactory.setCoalescing(true);
        inputBuilderFactory.setIgnoringElementContentWhitespace(true);
        inputBuilderFactory.setValidating(false);
        DocumentBuilder inputBuilder = inputBuilderFactory.newDocumentBuilder();
        Document inputDoc=inputBuilder.parse(input);
        
        // parse input XML Document
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expression = "/liberty-plugin-config/serverDirectory";
        NodeList nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
        Assert.assertEquals("Number of <serverDirectory/> element ==>", 1, nodes.getLength());
        
        xPath = XPathFactory.newInstance().newXPath();
        expression = "/liberty-plugin-config/configFile";
        nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
        Assert.assertEquals("Number of <configFile/> element ==>", 1, nodes.getLength());

        xPath = XPathFactory.newInstance().newXPath();
        expression = "/liberty-plugin-config/serverName/text()";
        String value = (String) xPath.compile(expression).evaluate(inputDoc, XPathConstants.STRING);
        Assert.assertEquals("Value of <serverName/> ==>", "test", value);

        expression = "/liberty-plugin-config/appsDirectory/text()";
        value = (String) xPath.compile(expression).evaluate(inputDoc, XPathConstants.STRING);
        Assert.assertEquals("Value of <appsDirectory/> ==>", "dropins", value);
        
        expression = "/liberty-plugin-config/installAppPackages/text()";
        value = (String) xPath.compile(expression).evaluate(inputDoc, XPathConstants.STRING);
        Assert.assertEquals("Value of <installAppPackages/> ==>", "project", value);
        
        expression = "/liberty-plugin-config/applicationFilename/text()";
        value = (String) xPath.compile(expression).evaluate(inputDoc, XPathConstants.STRING);
        Assert.assertEquals("Value of <applicationFilename/> ==>", "appsdirectory-notset-notconfigured-it.war", value);

    }
    
    @Test
    public void testApplicationFileExist() throws Exception {
        File f = new File("liberty/usr/servers/test/dropins/appsdirectory-notset-notconfigured-it.war");
        Assert.assertTrue(f.getCanonicalFile() + " doesn't exist", f.exists());
    }

    @Test
    public void testCopyDependenciesFilesExist() throws Exception {
        // stripVersion was set to true

        // This was SCOPE_PROVIDED and should be copied. The default location was used.
        File f = new File("liberty/usr/servers/test/lib/global/commons-logging-1.0.4.jar");
        Assert.assertTrue(f.getCanonicalFile() + " doesn't exist", f.exists());
 
        // This next three derby related dependencies were pulled in with
        // 'derby*' for artifactId and the location was overridden in a dependencyGroup.

        f = new File("liberty/usr/servers/test/lib/global/derby/derby.jar");
        Assert.assertTrue(f.getCanonicalFile() + " doesn't exist", f.exists());
 
        f = new File("liberty/usr/servers/test/lib/global/derby/derbyshared.jar");
        Assert.assertTrue(f.getCanonicalFile() + " doesn't exist", f.exists());
 
        f = new File("liberty/usr/servers/test/lib/global/derby/derbyclient.jar");
        Assert.assertTrue(f.getCanonicalFile() + " doesn't exist", f.exists());
 
   }

}
