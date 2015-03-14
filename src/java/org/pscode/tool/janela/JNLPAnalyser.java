/*******************************************************************************
 * Copyright 2009, 2010 Andrew Thompson.
 * 
 * This file is part of JaNeLa.
 * 
 * JaNeLa is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * JaNeLa is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with JaNeLa.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.pscode.tool.janela;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.ImageIcon;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.pscode.tool.janela.LaunchError.ErrorLevel;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class JNLPAnalyser {
    private static final String contentType = "application/x-java-jnlp-file";

    private final URL page;
    private final List<LaunchError> errors = new ArrayList<LaunchError>();
    private final List<URL> extensions = new ArrayList<URL>();
    private boolean xmlValid;

    private ListErrorHandler errorHandler;
    private Document document;

    public JNLPAnalyser(URL url) {
        this.page = url;
    }
    
    public boolean isXMLValid() {
        return xmlValid;
    }
    
    public List<LaunchError> getErrors() {
        return errors;
    }
    
    public List<URL> getExtensions() {
        return extensions;
    }
    
    public String getReport() {
        StringBuffer sb = new StringBuffer("Report for ");
        sb.append( getURL() );
        sb.append( "\n\n" );

        for (LaunchError error : errors) {
            sb.append( error );
            sb.append( "\n" );
        }

        return sb.toString();
    }

    public URL getURL() {
        return page;
    }


    public void checkResource() {
        NodeList nodeList;
        nodeList = document.getElementsByTagName("jar");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkJarResource(nodeList.item(ii));
        }
        nodeList = document.getElementsByTagName("nativelib");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkNativeLibResource(nodeList.item(ii));
        }
        nodeList = document.getElementsByTagName("extension");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkExtensionResource(nodeList.item(ii));
        }
        nodeList = document.getElementsByTagName("icon");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkIconResource(nodeList.item(ii));
        }
        nodeList = document.getElementsByTagName("help");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkLinkResource(nodeList.item(ii));
        }
        nodeList = document.getElementsByTagName("homepage");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkLinkResource(nodeList.item(ii));
        }
    }

    public void checkJarResource(Node resource) {
        try {
            // should be 1st check..
            checkResourceAvailability(resource);

            // check other aspects of this resource
            String[] types = {
                "jar"
            };
            checkResourceType(resource, types);
            checkResourceHrefOptimisation(resource);

            checkResourceSize(resource);

            checkDefaultValueSpecified(resource);

            checkLazyForNonMain(resource);

            checkLazyHasPart(resource);
        } catch(IOException ioe) {
            addResourceFetchError(resource, ioe, true);
        } catch(Exception e) {
            addException(e);
        }
    }

    public void checkNativeLibResource(Node resource) {
        try {
            // should be 1st check..
            checkResourceAvailability(resource);

            // check other aspects of this resource
            String[] types = {
                "jar"
            };
            checkResourceType(resource, types);
            checkResourceHrefOptimisation(resource);

            checkResourceSize(resource);

            checkLibIsInTrusted(resource);

            checkLibIsInRoot(resource);

            checkLibIsOptimized(resource);

            checkDefaultValueSpecified(resource);

            checkLazyForNonMain(resource);

            checkLazyHasPart(resource);

        } catch(IOException ioe) {
            addResourceFetchError(resource, ioe, true);
        } catch(Exception e) {
            addException(e);
        }
    }

    public void checkDefaultValueSpecified(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        Node downloadNode = attributes.getNamedItem("download");
        if (downloadNode!=null && downloadNode.getTextContent().equals("eager") ) {
            LaunchError launchError = new LaunchError(
                "The resource download at " +
                hrefNode.getTextContent() +
                " can be optimized by removing the (default) value of download='eager'.",
                (Exception)null,
                ErrorLevel.OPTIMIZE
                );
            errors.add( launchError );
        }
        Node mainNode = attributes.getNamedItem("main");
        if (mainNode!=null && mainNode.getTextContent().equals("false") ) {
            LaunchError launchError = new LaunchError(
                "The resource download at " +
                hrefNode.getTextContent() +
                " can be optimized by removing the (default) value of main='false'.",
                (Exception)null,
                ErrorLevel.OPTIMIZE
                );
            errors.add( launchError );
        }
    }

    public void checkLibIsOptimized(Node node) {
        // this will be a j2se/java element
        Node parent = node.getParentNode();
        NamedNodeMap attributes = parent.getAttributes();
        Node osNode = attributes.getNamedItem("os");
        Node platformNode = attributes.getNamedItem("platform");
        if (osNode==null && platformNode==null) {
            Node hrefNode = node.getAttributes().getNamedItem("href");
            LaunchError launchError = new LaunchError(
                "The download can be optimized by including the nativelib " +
                hrefNode.getTextContent() +
                " in an os/platform specific resources element",
                (Exception)null,
                ErrorLevel.OPTIMIZE
                );
            
            errors.add( launchError );
        }
    }

    public void checkLibIsInTrusted(Node node) {
        if (document.getElementsByTagName("all-permissions").getLength()!=1) {
            Node hrefNode = node.getAttributes().getNamedItem("href");
            LaunchError launchError = new LaunchError(
                "Native lib " +
                hrefNode.getTextContent() +
                " requires 'all-permissions'.",
                (Exception)null,
                ErrorLevel.ERROR
                );
            errors.add( launchError );
        }
    }

    public void checkLibIsInRoot(Node node) throws IOException {
        Node hrefNode = node.getAttributes().getNamedItem("href");
        URL url = getCodeBase();
        URL nativelib = new URL(url, hrefNode.getTextContent() );
        ZipInputStream zis = new ZipInputStream(nativelib.openStream());
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) !=null) {
                String name = ze.getName();
                if ( name.endsWith(".dll") || name.endsWith(".so") ) {
                    if ( name.indexOf("/")!=-1 ) {
                        LaunchError launchError = new LaunchError(
                            "Native lib " +
                            name +
                            " must be in the root of " +
                            hrefNode.getTextContent() +
                            ".",
                            (Exception)null,
                            ErrorLevel.ERROR);
                        errors.add( launchError );
                    }
                }
            }
        }
        finally {
                zis.close();
        }
    }

    public void checkExtensionResource(Node resource) {
        try {
            // should be 1st check..
            checkResourceAvailability(resource);

            // check other aspects of this resource
            String[] types = {
                "jnlp"
            };
            checkResourceType(resource, types);
            checkResourceHrefOptimisation(resource);

            NamedNodeMap attributes = resource.getAttributes();
            Node hrefNode = attributes.getNamedItem("href");
            try {
                URL url = new URL(getCodeBase(), hrefNode.getTextContent() );
                extensions.add(url);
            } catch(MalformedURLException murle) {
                addException(murle);
            }

        } catch(IOException ioe) {
            addResourceFetchError(resource, ioe, true);
        } catch(Exception e) {
            addException(e);
        }
    }

    public void checkIconResource(Node resource) {
        try {
            // should be 1st check..
            checkResourceAvailability(resource);

            // check other aspects of this resource
            String[] types = {
                "gif",
                "jpg",
                "jpeg"
            };
            checkResourceType(resource, types);
            checkResourceHrefOptimisation(resource);

            checkResourceSize(resource);

            checkIconSize(resource);

            checkSplashIconHasHref(resource);

        } catch(IOException ioe) {
            addResourceFetchError(resource, ioe, true);
        } catch(Exception e) {
            addException(e);
        }
    }

    public void checkIconSize(Node resource) {
        NamedNodeMap attributes = resource.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        Node widthNode = attributes.getNamedItem("width");
        Node heightNode = attributes.getNamedItem("height");
        if (widthNode!=null && heightNode!=null) {
            try {
                URL url = new URL(getCodeBase(), hrefNode.getTextContent());
                ImageIcon icon = new ImageIcon(url);
                // make sure the image is reloaded
                icon.getImage().flush();
                int actualWidth = icon.getIconWidth();
                int actualHeight = icon.getIconHeight();
                try {
                    int declaredWidth = Integer.parseInt( widthNode.getTextContent() );
                    if (declaredWidth!=actualWidth) {
                        LaunchError launchError = new LaunchError(
                            "Icon at " +
                            hrefNode.getTextContent() +
                            " is declared with width " +
                            declaredWidth +
                            " but is actually " +
                            actualWidth +
                            ".",
                            (Exception)null,
                            ErrorLevel.OPTIMIZE
                            );
                        errors.add( launchError );
                    }
                    int declaredHeight = Integer.parseInt( heightNode.getTextContent() );
                    if (declaredHeight!=actualHeight) {
                        LaunchError launchError = new LaunchError(
                            "Icon at " +
                            hrefNode.getTextContent() +
                            " is declared with height " +
                            declaredHeight +
                            " but is actually " +
                            actualHeight +
                            ".",
                            (Exception)null,
                            ErrorLevel.OPTIMIZE
                            );
                        errors.add( launchError );
                    }
                } catch (Exception e) {
                    addException(e);
                }
            } catch(MalformedURLException murle) {
                // noted elsewhere, ignore
            }
        } else {
            LaunchError launchError = new LaunchError(
                "Icon loading & use can be optimized by specifying the width and height for " +
                hrefNode.getTextContent(),
                (Exception)null,
                ErrorLevel.OPTIMIZE
                );
            errors.add( launchError );
        }
    }

    public void checkLazyHasPart(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        Node partNode = attributes.getNamedItem("part");
        Node downloadNode = attributes.getNamedItem("download");
        if (downloadNode!=null && partNode==null && "lazy".equals(downloadNode.getTextContent())) {
            LaunchError launchError = new LaunchError(
                "Lazy downloads might not work as expected for " +
                hrefNode.getTextContent() +
                " unless the download 'part' is specified. ",
                (Exception)null,
                ErrorLevel.WARNING
                );
            errors.add( launchError );
        }
    }

    public void checkLazyForNonMain(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        Node mainNode = attributes.getNamedItem("main");
        Node downloadNode = attributes.getNamedItem("download");
        if (mainNode == null || mainNode.getTextContent().equals("false") ) {
            if (downloadNode == null || downloadNode.getTextContent().equals("eager")) {
                LaunchError launchError = new LaunchError(
                    "It might be possible to optimize the start-up of the app. by " +
                    " specifying download='lazy' for the " +
                    hrefNode.getTextContent() +
                    " resource.",
                    (Exception)null,
                    ErrorLevel.OPTIMIZE
                    );
                errors.add( launchError );
            }
        }
    }

    public void checkSplashIconHasHref(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        String href = getHref();
        if (href==null) {
            LaunchError launchError = new LaunchError(
                    "Icon  " +
                    hrefNode.getTextContent() +
                    " will not appear as a splash unless the JNLP href attribute is specified.",
                    (Exception)null,
                    ErrorLevel.WARNING
            );
            errors.add( launchError );
        }
    }

    public void checkLinkResource(Node resource) {
        try {
            // should be 1st check..
            checkResourceAvailability(resource);

            checkResourceHrefOptimisation(resource);

        } catch(IOException ioe) {
            addResourceFetchError(resource, ioe, true);
        } catch(Exception e) {
            addException(e);
        }
    }

    public void addResourceFetchError(Node resource, Exception e, boolean error) {
        NamedNodeMap attributes = resource.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        addResourceFetchError(hrefNode.getTextContent(), e, error);
    }
    
    public void addResourceFetchError(String href, Exception e, boolean error) {
        ErrorLevel level = (error ? ErrorLevel.ERROR : ErrorLevel.WARNING );
        errors.add( new LaunchError(
            "Problem fetching resource " + href
            + ".  " + e.getMessage(),
            e, level) );
    }

    public void checkResourceHrefOptimisation(Node resource) {
        NamedNodeMap attributes = resource.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        String href = hrefNode.getTextContent();
        try {
            URL fullPath = new URL(getCodeBase(), href);
            URI codebaseUri = getCodeBase().toURI();
            URI pathUri = fullPath.toURI();
            String relative = codebaseUri.relativize(pathUri).toString();
            if (relative.length()<href.length()) {
                LaunchError launchError = new LaunchError(
                    "The HREF of '" +
                    href +
                    "' could be optimized to '" +
                    relative +
                    "'.",
                    (Exception)null,
                    ErrorLevel.OPTIMIZE);
                errors.add( launchError );
            }
        } catch(Exception e) {
            addException(e);
        }
    }

    public void checkResourceType(Node resource, String[] allowable) {
        NamedNodeMap attributes = resource.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        String[] parts = hrefNode.getTextContent().split("\\.");
        int last = parts.length-1;
        for ( int ii=0; ii<allowable.length; ii++ ) {
            System.out.println(parts[last] + "==" + allowable[ii]);
            if (parts[last].equalsIgnoreCase(allowable[ii])) {
                // type found
                return;
            }
        }
        StringBuffer types = new StringBuffer();
        for ( int ii=0; ii<allowable.length; ii++ ) {
            types.append( allowable[ii] );
            types.append( ", " );
        }
        String allowableTypes = types.toString();
        allowableTypes = allowableTypes.substring(0,allowableTypes.length()-2);
        LaunchError launchError = new LaunchError(
            "Resource type " +
            parts[last] +
            " of resource " +
            hrefNode.getTextContent() +
            " is not one of the allowable types of " +
            allowableTypes +
            ".",
            (Exception)null,
            ErrorLevel.ERROR);
        errors.add(launchError);
    }

    public void checkResourceAvailability(Node resource) throws IOException {
        NamedNodeMap attributes = resource.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        URL url = new URL(getCodeBase(), hrefNode.getTextContent() );
        URLConnection urlc = url.openConnection();
        urlc.connect();
    }

    public void checkResourceSize(Node resourceNode) {
        NamedNodeMap attributes = resourceNode.getAttributes();
        Node hrefNode = attributes.getNamedItem("href");
        try {
            Node sizeNode = attributes.getNamedItem("size");
            if (sizeNode==null) {
                LaunchError launchError = new LaunchError(
                    "Downloads can be optimized by specifying a resource size for '" +
                    hrefNode.getTextContent() +
                    "'.",
                    (Exception)null,
                    ErrorLevel.OPTIMIZE);
                errors.add( launchError );
            } else {
                URL url = new URL(getCodeBase(), hrefNode.getTextContent() );
                URLConnection urlc = url.openConnection();
                urlc.connect();
                // check size against declared size
                int actualSize = urlc.getContentLength();
                int declaredSize = Integer.parseInt( sizeNode.getTextContent() );
                if (actualSize!=declaredSize) {
                    LaunchError launchError = new LaunchError(
                        "Resource '" +
                        hrefNode.getTextContent() +
                        "' declared as size '" +
                        declaredSize +
                        "' but is actually '" +
                        actualSize +
                        "'.",
                        (Exception)null,
                        ErrorLevel.WARNING);
                    errors.add( launchError );
                }
            }
        } catch(Exception e) {
            addException( e );
        }
    }

    public void checkContentType() throws IOException {
        //URL url = content.getPage();
        URLConnection urlc = page.openConnection();
        String type = urlc.getContentType();
        if ( !type.equals(contentType) && !type.startsWith(contentType + ";")) {
            errors.add(
                new LaunchError(
                "Content type " +
                type +
                " does not equal expected type of " +
                contentType,
                (Exception)null,
                ErrorLevel.WARNING) );
        }
    }

    private void addException(Exception e) {
            errors.add(
                new LaunchError(
                e.getMessage(),
                e,
                ErrorLevel.ERROR) );
    }

    /** Check the well-formedness of the data. */
    private void checkWellFormedness()
        throws
        ParserConfigurationException,
        SAXException,
        IOException
        {

        DocumentBuilderFactory factory =
            DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = factory.newDocumentBuilder();

        InputStream is = page.openStream();
        try {
            documentBuilder.parse( is );
        }
        finally {
            is.close();
        }

        System.out.println("XML is well-formed.");
    }

    private void offlineAllowed() {
        NodeList nodeList = document.getElementsByTagName("offline-allowed");
        if (nodeList.getLength()==0) {
            errors.add(
                new LaunchError(
                    "Optimize this application for off-line use by adding the <offline-allowed /> flag.",
                    (Exception)null,
                    ErrorLevel.OPTIMIZE
                    ));
        }
    }

    private void checkDesktopIcon() {
        NodeList nodeList = document.getElementsByTagName("desktop");
        if (nodeList.getLength()>0) {
            errors.add(
                new LaunchError(
                    "Desktop icons were subject to bug nnnn in earlier J2SE versions",
                    (Exception)null,
                    ErrorLevel.WARNING
                    ));
        }
    }

    private String getDeclaredEncoding() {
        System.out.println( "******  documentType: " + document.getDoctype() );
        System.out.println( "******  documentElement: " + document.getDocumentElement() );
        return document.getXmlEncoding();
        
//        String jnlpContent = content.getText().toLowerCase();
//        int encodingStart= jnlpContent.indexOf("encoding");
//        int nextQuoteDelimiter = jnlpContent.indexOf("'", encodingStart);
//        int nextDoubleQuoteDelimiter = jnlpContent.indexOf("\"", encodingStart);
//        int delim;
//        if (nextQuoteDelimiter<0) {
//            delim = nextDoubleQuoteDelimiter;
//        } else if(nextDoubleQuoteDelimiter<0) {
//            delim = nextQuoteDelimiter;
//        } else {
//            delim = (
//                nextQuoteDelimiter<nextDoubleQuoteDelimiter ?
//                nextQuoteDelimiter :
//                nextDoubleQuoteDelimiter);
//        }
//        int nextQuoteDelimiterSecond = jnlpContent.indexOf("'", delim+1);
//        int nextDoubleQuoteDelimiterSecond = jnlpContent.indexOf("\"", delim+1);
//        int end;
//        if (nextQuoteDelimiterSecond<0) {
//            end = nextDoubleQuoteDelimiterSecond;
//        } else if (nextDoubleQuoteDelimiterSecond<0) {
//            end = nextQuoteDelimiterSecond;
//        } else {
//            end = (
//                nextQuoteDelimiterSecond<nextDoubleQuoteDelimiterSecond ?
//                nextQuoteDelimiterSecond :
//                nextDoubleQuoteDelimiterSecond);
//        }
//        System.out.println( delim + " " + end );
//        String encoding = jnlpContent.substring(delim+1, end).trim();
//
//        return encoding;
    }

    private void checkCodebaseAndHrefEqualsLocation() {
        try {
            URL url = new URL(getCodeBase(), getHref());
            if (!url.equals(page)) {
                errors.add(
                    new LaunchError(
                        "Codebase + href '" +
                        url +
                        "' is not equal to actual location of '" +
                        page +
                        "'.",
                        (Exception)null,
                        ErrorLevel.WARNING
                        ));
            }
        } catch(MalformedURLException murle) {
            errors.add(
                new LaunchError(
                    "Codebase + href '" +
                    getCodeBase() +
                    getHref() +
                    "' is a malformed URL!",
                    (Exception)null,
                    ErrorLevel.ERROR
                    ));
        }
    }

    private void checkCodebasePresent() {
        String codebase = getCodeBaseString();
        if (codebase==null) {
            errors.add(
                new LaunchError(
                    "Codebase not specified.  Defaulting to " +
                    getCodeBase(),
                    (Exception)null,
                    ErrorLevel.WARNING
                    ));
        } else {
            System.out.println("Codebase: " + codebase );
        }
    }

    private URL getCodeBase() {
        String codebase = getCodeBaseString();
        URL url;
        try {
            if (codebase==null) {
                url = new URL(page, ".");
            } else {
                url = new URL(codebase);
            }
        } catch(MalformedURLException murle) {
            murle.printStackTrace();
            errors.add(
                new LaunchError(
                    "Codebase '" +
                    codebase +
                    "' is a malformed URL!  Defaulting to " +
                    page,
                    (Exception)null,
                    ErrorLevel.ERROR
                    ));
            url = page;
        }
        return url;
    }

    private String getCodeBaseString() {
        Node jnlpNode = document.getDocumentElement();
        NamedNodeMap namedNodeMap = jnlpNode.getAttributes();
        Node node = namedNodeMap.getNamedItem("codebase");
        if (node==null) {
            return null;
        } else {
            System.out.println("Codebase: " +
                node.getNodeValue() );
            return node.getNodeValue();
        }
    }

    private void checkHrefPresent() {
        String href = getHrefString();
        if (href==null) {
            errors.add(
                new LaunchError(
                    "href not specified.  Defaulting to document name '" +
                    page.getFile(),
                    (Exception)null,
                    ErrorLevel.WARNING
                    ));
        } else {
            System.out.println("HREF: " + href );
        }
    }

    private String getHref() {
        if ( getHrefString()==null ) {
            return page.getFile();
        } else {
            return getHrefString();
        }
    }

    private String getHrefString() {
        Node jnlpNode = document.getDocumentElement();
        NamedNodeMap namedNodeMap = jnlpNode.getAttributes();
        Node node = namedNodeMap.getNamedItem("href");
        if (node==null) {
            return null;
        } else {
            System.out.println("HREF: " +
                node.getNodeValue() );
            return node.getNodeValue();
        }
    }

    private void checkContentEncoding() {
        URLConnection urlc;
        try {
            String declaredEncoding = getDeclaredEncoding();
            System.out.println("** encoding: " + declaredEncoding);

            urlc = page.openConnection();
            String encoding = urlc.getContentEncoding();
            System.out.println("Reported encoding " + encoding);
            if (encoding==null) {
                errors.add(
                    new LaunchError(
                        "XML encoding not known, but declared as " +
                        declaredEncoding,
                        (Exception)null,
                        ErrorLevel.WARNING
                        ));
            } else {
                if (!encoding.toLowerCase().equals(declaredEncoding)) {
                    errors.add(
                        new LaunchError(
                            "Declared encoding of " +
                            declaredEncoding +
                            " does not match actual encoding of " +
                            encoding,
                            (Exception)null,
                            ErrorLevel.WARNING
                            ));
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            addException(e);
        }
    }

    private NodeList getJ2seNodes() {
        NodeList nodeList = document.getElementsByTagName("resources");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            Node node = nodeList.item(ii);
            System.out.println(node);

            NamedNodeMap nodeMap = node.getAttributes();
            for (int jj=0; jj<nodeMap.getLength(); jj++) {
                System.out.println(nodeMap.item(jj));
            }

            NodeList children = node.getChildNodes();
            for (int kk=0; kk<children.getLength(); kk++) {
                System.out.println(children.item(kk));
            }
        }
        return nodeList;
    }

    /** Check the XML validity of the data via XSD. */
    private void validateContent() {
        errorHandler = new ListErrorHandler();
        System.out.println("Validating JNLP.");

        try {
            URL schemaSource = Thread.currentThread().getContextClassLoader().getResource("JNLP-6.0.xsd");
            System.out.println( "schemaSource: " + schemaSource );

            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/validation", true);
            factory.setFeature("http://apache.org/xml/features/validation/schema", true) ;
            factory.setFeature("http://xml.org/sax/features/namespaces", true) ;
            factory.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
            factory.setAttribute(
                "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation",
                schemaSource.toString());
            factory.setNamespaceAware(true);
            factory.setValidating(true);
            
            InputStream schemaStream = schemaSource.openStream();
            try {
                StreamSource ss = new StreamSource( schemaStream );
                String language = XMLConstants.W3C_XML_SCHEMA_NS_URI;
                SchemaFactory schemaFactory = SchemaFactory.newInstance(language);

                Schema schema = schemaFactory.newSchema(ss);
                factory.setSchema( schema );
            }
            finally {
                schemaStream.close();
            }
            
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setErrorHandler( errorHandler );

            InputStream is = page.openStream();
            try {
                document = documentBuilder.parse( is );
            }
            finally {
                is.close();
            }

            List<LaunchError> parseErrors = errorHandler.getParseErrors();
            xmlValid = parseErrors.isEmpty();
            errors.addAll(parseErrors);
        } catch(Exception e) {
            System.err.println( "Error: " + e.getMessage() );
            // TODO Show to user
        }
        System.out.println("END: Validating JNLP.");
    }

    public void analyze() {
        errors.clear();
        extensions.clear();
        xmlValid = false;
        
        try {
            checkContentType();
            checkWellFormedness();
            
            
            validateContent();

            // post validation checks
            checkContentEncoding();
            checkCodebasePresent();
            checkCodebaseAndHrefEqualsLocation();

            checkDesktopIcon();
            offlineAllowed();

            checkDescriptionLengths();

            checkResource();

            checkJ2seNodes();

            //getJ2seNodes();
        }
        catch (IOException e) {
            addResourceFetchError(page.toExternalForm(), e, true);
        }
        catch (ParserConfigurationException e) {
            errors.add( new LaunchError(
                    e.getMessage(), e,
                    ErrorLevel.FATAL) );
        }
        catch (SAXException e) {
            errors.add( new LaunchError(
                    e.getMessage(), e,
                    ErrorLevel.ERROR) );
        }
    }

    private void checkJ2seNodes() {
        NodeList nodeList = document.getElementsByTagName("java");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkJ2seForMaxHeapSize(nodeList.item(ii));
            checkJ2seForSpecificVersion(nodeList.item(ii));
            checkJ2seForAllowedJavaVmArguments(nodeList.item(ii));
        }
        nodeList = document.getElementsByTagName("j2se");
        for (int ii=0; ii<nodeList.getLength(); ii++) {
            checkJ2seForMaxHeapSize(nodeList.item(ii));
            checkJ2seForSpecificVersion(nodeList.item(ii));
            checkJ2seForAllowedJavaVmArguments(nodeList.item(ii));
        }
    }
    
    private void checkJ2seForAllowedJavaVmArguments(Node node){
      NamedNodeMap attributes = node.getAttributes();
      Node javaVmArgsNode = attributes.getNamedItem("java-vm-args");
      if (javaVmArgsNode != null)
      {
        String javaVmArgsString = javaVmArgsNode.getTextContent().trim();
        for (String argument : javaVmArgsString.split("\\s"))
        {
          boolean isAllowedArgument = false;
          for (String allowedArgument : new String[]{
              "-d32",                                                          /* use a 32-bit data model if available (unix platforms only) */
              "-client",                                                       /* to select the client VM */
              "-server",                                                       /* to select the server VM */
              "-verbose",                                                      /* enable verbose output */
              "-version",                                                      /* print product version and exit */
              "-showversion",                                                  /* print product version and continue */                                              
              "-help",                                                         /* print this help message */
              "-X",                                                            /* print help on non-standard options */
              "-ea",                                                           /* enable assertions */
              "-enableassertions",                                             /* enable assertions */
              "-da",                                                           /* disable assertions */
              "-disableassertions",                                            /* disable assertions */
              "-esa",                                                          /* enable system assertions */
              "-enablesystemassertions",                                       /* enable system assertions */
              "-dsa",                                                          /* disable system assertione */
              "-disablesystemassertions",                                      /* disable system assertione */
              "-Xmixed",                                                       /* mixed mode execution (default) */
              "-Xint",                                                         /* interpreted mode execution only */
              "-Xnoclassgc",                                                   /* disable class garbage collection */
              "-Xincgc",                                                       /* enable incremental garbage collection */
              "-Xbatch",                                                       /* disable background compilation */
              "-Xprof",                                                        /* output cpu profiling data */
              "-Xdebug",                                                       /* enable remote debugging */
              "-Xfuture",                                                      /* enable strictest checks, anticipating future default */
              "-Xrs",                                                          /* reduce use of OS signals by Java/VM (see documentation) */
              "-XX:+ForceTimeHighResolution",                                  /* use high resolution timer */
              "-XX:-ForceTimeHighResolution"                                  /* use low resolution (default) */
          })
          {
            if (argument.equals(allowedArgument))
              isAllowedArgument = true;
          }
          
          for (String allowedArgument : new String[]{
              "-ea",                          /* enable assertions for classes */
              "-enableassertions",            /* enable assertions for classes */
              "-da",                          /* disable assertions for classes */
              "-disableassertions",           /* disable assertions for classes */
              "-verbose",                     /* enable verbose output */
              "-Xms",                         /* set initial Java heap size */
              "-Xmx",                         /* set maximum Java heap size */
              "-Xss",                         /* set java thread stack size */
              "-XX:NewRatio",                 /* set Ratio of new/old gen sizes */
              "-XX:NewSize",                  /* set initial size of new generation */
              "-XX:MaxNewSize",               /* set max size of new generation */
              "-XX:PermSize",                 /* set initial size of permanent gen */
              "-XX:MaxPermSize",              /* set max size of permanent gen */
              "-XX:MaxHeapFreeRatio",         /* heap free percentage (default 70) */
              "-XX:MinHeapFreeRatio",         /* heap free percentage (default 40) */
              "-XX:UseSerialGC",              /* use serial garbage collection */
              "-XX:ThreadStackSize",          /* thread stack size (in KB) */
              "-XX:MaxInlineSize",            /* set max num of bytecodes to inline */
              "-XX:ReservedCodeCacheSize",    /* Reserved code cache size (bytes) */
              "-XX:MaxDirectMemorySize"
          })
          {
            if (argument.startsWith(allowedArgument))
              isAllowedArgument = true;
          }
          if (!isAllowedArgument)
            errors.add(new LaunchError(argument + " is not allowed as java-vm-rg", null, ErrorLevel.WARNING));
        }
      }
    }

    private void checkJ2seForSpecificVersion(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node versionNode = attributes.getNamedItem("version");
        if (versionNode!=null) {
            String versionString = versionNode.getTextContent();
            if (versionString.endsWith("*")) {
                errors.add(
                    new LaunchError(
                        "Note that a java/j2se version of 'n.n*' will limit the app. to that " +
                        " specific Java runtime, and no later.  It is often better to specify " +
                        " version as 'n.n+'.",
                        (Exception)null,
                        ErrorLevel.OPTIMIZE
                        ));
            }
        }
    }

    private void checkJ2seForMaxHeapSize(Node node) {
        NamedNodeMap attributes = node.getAttributes();
        Node maxHeapSizeNode = attributes.getNamedItem("max-heap-size");
        if (maxHeapSizeNode!=null) {
            String maxHeap = maxHeapSizeNode.getTextContent().toLowerCase();
            int multiplier = 1;
            if (maxHeap.endsWith("k")) {
                maxHeap = maxHeap.substring(0,maxHeap.length()-1);
                multiplier = 1000;
            } else if (maxHeap.endsWith("m")) {
                maxHeap = maxHeap.substring(0,maxHeap.length()-1);
                multiplier = 1000000;
            }
            int maxHeapSize = Integer.parseInt(maxHeap)*multiplier;
            if (maxHeapSize>1000000000) {
                errors.add(
                    new LaunchError(
                        "Some JWS launches have trouble with a max-heap-size>1000Meg." +
                        "  A java/j2se element was defined, calling for " +
                        maxHeapSizeNode.getTextContent() +
                        " bytes of memory.",
                        (Exception)null,
                        ErrorLevel.WARNING
                        ));
            }
        }
    }

    private void checkDescriptionLengths() {
        System.out.println( getDescription("tooltip") );
        System.out.println( getDescription("short") );
        System.out.println( getDescription("one-line") );
        System.out.println( getDefaultDescription() );

        ArrayList<String> desc = new ArrayList<String>();
        boolean defaultDesc = getDefaultDescription() != null;
        String type;
        type = "tooltip";
        if ( getDescription(type)!=null ) {
            desc.add( type );
        }
        type = "short";
        if ( getDescription(type)!=null ) {
            desc.add( type );
        }
        type = "one-line";
        if ( getDescription(type)!=null ) {
            desc.add( type );
        }

        // check the short description, against the one-line or default.
        if ( getDescription("short")!=null ) {
            if ( getDescription("one-line")!=null ) {
                checkOneDescriptionAgainstAnother("short", "one-line");
            } else if ( defaultDesc ) {
                checkOneDescriptionAgainstAnother("short", null);
            }
        }

        if ( getDescription("tooltip")!=null ) {
            if ( getDescription("short")!=null ) {
                checkOneDescriptionAgainstAnother("tooltip", "short");
            } else if ( getDescription("one-line")!=null ) {
                checkOneDescriptionAgainstAnother("tooltip", "one-line");
            } else if ( defaultDesc ) {
                checkOneDescriptionAgainstAnother("tooltip", null);
            }
        }
    }

    private void checkOneDescriptionAgainstAnother(
        String smaller,
        String larger) {

        String shortDesc = getDescription(smaller);
        String longDesc = null;
        if ( larger==null ) {
            longDesc = getDefaultDescription();
            larger = "default";
        } else {
            longDesc = getDescription(larger);
        }
        if (!(longDesc.length()>=shortDesc.length())) {
            LaunchError launchError = new LaunchError(
                "'" +
                smaller +
                "' description is longer than '" +
                larger +
                "' description.",
                (Exception)null,
                ErrorLevel.WARNING);
            errors.add(launchError);
        }
    }

    private NodeList getDescriptionNodes() {
        return document.getElementsByTagName("description");
    }

    private String getDescription(String kind) {
        NodeList descriptions = getDescriptionNodes();
        for (int ii=0; ii<descriptions.getLength(); ii++) {
            Node description = descriptions.item(ii);
            NamedNodeMap attributes = description.getAttributes();
            Node type = attributes.getNamedItem("kind");
            if (type!=null) {
                String value = type.getTextContent();
                if (value.equalsIgnoreCase(kind)) {
                    return description.getTextContent().trim();
                }
            }
        }
        return null;
    }

    private String getDefaultDescription() {
        NodeList descriptions = getDescriptionNodes();
        for (int ii=0; ii<descriptions.getLength(); ii++) {
            Node description = descriptions.item(ii);
            NamedNodeMap attributes = description.getAttributes();
            Node type = attributes.getNamedItem("kind");
            if (type==null) {
                return description.getTextContent().trim();
            }
        }
        return null;
    }

    private static class ListErrorHandler extends DefaultHandler {

        private final List<LaunchError> errorList;

        public ListErrorHandler() {
            errorList = new ArrayList<LaunchError>();
        }

        @Override
        public void warning(SAXParseException e) {
          errorList.add(new LaunchError(e.getMessage() + " (line: " + e.getLineNumber() + ", column: "
              + e.getColumnNumber() + ")", e, ErrorLevel.WARNING));
        }

        @Override
        public void error(SAXParseException e) {
          errorList.add(new LaunchError(e.getMessage() + " (line: " + e.getLineNumber() + ", column: "
              + e.getColumnNumber() + ")", e, ErrorLevel.ERROR));
        }

        @Override
        public void fatalError(SAXParseException e) {
          errorList.add(new LaunchError(e.getMessage() + " (line: " + e.getLineNumber() + ", column: "
              + e.getColumnNumber() + ")", e, ErrorLevel.FATAL));
        }

        public List<LaunchError> getParseErrors() {
            return errorList;
        }
    }

}
