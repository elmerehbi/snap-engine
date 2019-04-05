package org.esa.snap.vfs.remote.s3;

import org.esa.snap.core.util.StringUtils;
import org.esa.snap.vfs.remote.VFSFileAttributes;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Response Handler for S3 VFS.
 *
 * @author Norman Fomferra
 * @author Adrian Drăghici
 */
public class S3ResponseHandler extends DefaultHandler {

    /**
     * The name of XML element for Key, used on parsing VFS service response XML.
     */
    private static final String KEY_ELEMENT = "Key";

    /**
     * The name of XML element for Size, used on parsing VFS service response XML.
     */
    private static final String SIZE_ELEMENT = "Size";

    /**
     * The name of XML element for Contents, used on parsing VFS service response XML.
     */
    private static final String CONTENTS_ELEMENT = "Contents";

    /**
     * The name of XML element for LastModified, used on parsing VFS service response XML.
     */
    private static final String LAST_MODIFIED_ELEMENT = "LastModified";

    /**
     * The name of XML element for NextContinuationToken, used on parsing VFS service response XML.
     */
    private static final String NEXT_CONTINUATION_TOKEN_ELEMENT = "NextContinuationToken";

    /**
     * The name of XML element for IsTruncated, used on parsing VFS service response XML.
     */
    private static final String IS_TRUNCATED_ELEMENT = "IsTruncated";

    /**
     * The name of XML element for CommonPrefixes, used on parsing VFS service response XML.
     */
    private static final String COMMON_PREFIXES_ELEMENT = "CommonPrefixes";

    /**
     * The name of XML element for Prefix, used on parsing VFS service response XML.
     */
    private static final String PREFIX_ELEMENT = "Prefix";

    private static Logger logger = Logger.getLogger(S3ResponseHandler.class.getName());

    private LinkedList<String> elementStack = new LinkedList<>();
    private List<BasicFileAttributes> items;

    private String key;
    private long size;
    private String lastModified;
    private String nextContinuationToken;
    private boolean isTruncated;
    private String prefix;
    private String delimiter;

    /**
     * Creates the new response handler for S3 VFS.
     *
     * @param prefix    The VFS path to traverse
     * @param items     The list with VFS paths for files and directories
     * @param delimiter The VFS path delimiter
     */
    S3ResponseHandler(String prefix, List<BasicFileAttributes> items, String delimiter) {
        this.prefix = prefix;
        this.items = items;
        this.delimiter = delimiter;
    }

    /**
     * Creates the authorization token used for S3 authentication.
     *
     * @param accessKeyId     The access key id S3 credential (username)
     * @param secretAccessKey The secret access key S3 credential (password)
     * @return The authorization token
     */
    private static String getAuthorizationToken(String accessKeyId, String secretAccessKey) {//not real S3 authentication - only for function definition
        return (!StringUtils.isNotNullAndNotEmpty(accessKeyId) && !StringUtils.isNotNullAndNotEmpty(secretAccessKey)) ? Base64.getEncoder().encodeToString((accessKeyId + ":" + secretAccessKey).getBytes()) : "";
    }

    /**
     * Creates the connection channel.
     *
     * @param url               The URL address to connect
     * @param method            The HTTP method (GET POST DELETE etc)
     * @param requestProperties The properties used on the connection
     * @param accessKeyId       The access key id S3 credential (username)
     * @param secretAccessKey   The secret access key S3 credential (password)
     * @return The connection channel
     * @throws IOException If an I/O error occurs
     */
    static URLConnection getConnectionChannel(URL url, String method, Map<String, String> requestProperties, String accessKeyId, String secretAccessKey) throws IOException {
        String authorizationToken = getAuthorizationToken(accessKeyId, secretAccessKey);
        HttpURLConnection connection;
        if (url.getProtocol().equals("https")) {
            connection = (HttpsURLConnection) url.openConnection();
        } else {
            connection = (HttpURLConnection) url.openConnection();
        }
        connection.setRequestMethod(method);
        connection.setDoInput(true);
        method = method.toUpperCase();
        if (method.equals("POST") || method.equals("PUT") || method.equals("DELETE")) {
            connection.setDoOutput(true);
        }
        connection.setRequestProperty("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        if (authorizationToken != null && !authorizationToken.isEmpty())
            connection.setRequestProperty("authorization", "Basic " + authorizationToken);
        if (requestProperties != null && requestProperties.size() > 0) {
            Set<Map.Entry<String, String>> requestPropertiesSet = requestProperties.entrySet();
            for (Map.Entry<String, String> requestProperty : requestPropertiesSet) {
                connection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
            }
        }
        connection.setRequestProperty("user-agent", "SNAP Virtual File System");
        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            /* error from server */
            throw new IOException(url + ": response code " + responseCode + ": " + connection.getResponseMessage());
        } else {
            return connection;
        }
    }

    /**
     * Gets the text value of XML element data.
     *
     * @param ch     The XML line char array
     * @param start  The index of first char in XML element value
     * @param length The index of last char in XML element value
     * @return The text value of XML element data
     */
    private static String getTextValue(char[] ch, int start, int length) {
        return new String(ch, start, length).trim();
    }

    /**
     * Gets the continuation token (indicates S3 that the list of objects from the current bucket continues).
     *
     * @return The continuation token
     */
    String getNextContinuationToken() {
        return nextContinuationToken;
    }

    /**
     * Tells whether or not current request response contains more than 1000 objects.
     *
     * @return {@code true} if request response is truncated
     */
    boolean getIsTruncated() {
        return isTruncated;
    }

    /**
     * Receive notification of the start of an element.
     * Mark starting of the new XML element by adding it to the stack of XML elements.
     *
     * @param uri        The Namespace URI, or the empty string if the element has no Namespace URI or if Namespace processing is not being performed.
     * @param localName  The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qName      The qualified name (with prefix), or the empty string if qualified names are not available.
     * @param attributes The attributes attached to the element.  If there are no attributes, it shall be an empty Attributes object.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see org.xml.sax.ContentHandler#startElement
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        try {
            String currentElement = localName.intern();
            elementStack.addLast(currentElement);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to mark starting of the new XML element by adding it to the stack of XML elements, for S3 VFS. Details: " + ex.getMessage());
            throw new SAXException(ex);
        }
    }

    /**
     * Receive notification of the end of an element.
     * Remove ending XML element from the stack of XML elements.
     * Adds the new path of S3 object to the list of VFS paths for files and directories.
     *
     * @param uri       The Namespace URI, or the empty string if the element has no Namespace URI or if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace processing is not being performed.
     * @param qName     The qualified name (with prefix), or the empty string if qualified names are not available.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see org.xml.sax.ContentHandler#endElement
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            String currentElement = elementStack.removeLast();
            if (currentElement != null && currentElement.equals(localName)) {
                if (currentElement.equals(PREFIX_ELEMENT) && elementStack.size() == 2 && elementStack.get(1).equals(COMMON_PREFIXES_ELEMENT)) {
                    items.add(VFSFileAttributes.newDir(prefix + key));
                } else if (currentElement.equals(CONTENTS_ELEMENT) && elementStack.size() == 1) {
                    items.add(VFSFileAttributes.newFile(prefix + key, size, lastModified));
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to add the new path of S3 object to the list of S3 VFS paths for files and directories. Details: " + ex.getMessage());
            throw new SAXException(ex);
        }
    }

    /**
     * Receive notification of character data inside an element.
     * Creates the VFS path and file attributes.
     *
     * @param ch     The characters.
     * @param start  The start position in the character array.
     * @param length The number of characters to use from the character array.
     * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping another exception.
     * @see org.xml.sax.ContentHandler#characters
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        try {
            String currentElement = elementStack.getLast();
            switch (currentElement) {
                case KEY_ELEMENT:
                    key = getTextValue(ch, start, length);
                    String[] keyParts = key.split(delimiter);
                    key = key.endsWith(delimiter) ? keyParts[keyParts.length - 1] + delimiter : keyParts[keyParts.length - 1];
                    break;
                case SIZE_ELEMENT:
                    size = getLongValue(ch, start, length);
                    break;
                case LAST_MODIFIED_ELEMENT:
                    lastModified = getTextValue(ch, start, length);
                    break;
                case IS_TRUNCATED_ELEMENT:
                    isTruncated = getBooleanValue(ch, start, length);
                    break;
                case NEXT_CONTINUATION_TOKEN_ELEMENT:
                    nextContinuationToken = getTextValue(ch, start, length);
                    break;
                case PREFIX_ELEMENT:
                    key = getTextValue(ch, start, length);
                    keyParts = key.split(delimiter);
                    key = key.endsWith(delimiter) ? keyParts[keyParts.length - 1] + delimiter : keyParts[keyParts.length - 1];
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unable to create the S3 VFS path and file attributes. Details: " + ex.getMessage());
            throw new SAXException(ex);
        }
    }

    /**
     * Gets the boolean value of XML element data.
     *
     * @param ch     The XML line char array
     * @param start  The index of first char in XML element value
     * @param length The index of last char in XML element value
     * @return The boolean value of XML element data
     */
    private boolean getBooleanValue(char[] ch, int start, int length) {
        return Boolean.parseBoolean(getTextValue(ch, start, length));
    }

    /**
     * Gets the long value of XML element data.
     *
     * @param ch     The XML line char array
     * @param start  The index of first char in XML element value
     * @param length The index of last char in XML element value
     * @return The long value of XML element data
     */
    private long getLongValue(char[] ch, int start, int length) {
        return Long.parseLong(getTextValue(ch, start, length));
    }

}
