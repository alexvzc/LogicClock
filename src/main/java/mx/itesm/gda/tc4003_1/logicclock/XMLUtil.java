/*
 * File:   XMLUtil.java
 * Author: A00663387 - Alejandro Vázquez Cantú.
 *
 * Created on 10 de marzo de 2010, 08:20 PM
 */

package mx.itesm.gda.tc4003_1.logicclock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import mx.itesm.gda.tc4003_1.logicclock.binding.ObjectFactory;
import mx.itesm.gda.tc4003_1.logicclock.binding.Packet;

/**
 * Provides quick-and-dirty utilities for XML and JAXB.
 * @author alexv
 */
public class XMLUtil {

    /**
     * {@see JAXBContext} for instance usage.
     */
    private JAXBContext jaxbCtx;

    /**
     * Retrieves all {@see ClassLoader}s bound for this class.
     * @return a set of {@see ClassLoader}.
     */
    private static Set<ClassLoader> getClassLoaders() {
        Set<ClassLoader> ret = new HashSet<ClassLoader>();
        ret.add(Thread.currentThread().getContextClassLoader());
        ret.add(XMLUtil.class.getClassLoader());
        ret.add(ClassLoader.getSystemClassLoader());
        return ret;

    } // getClassLoaders()

    /**
     * Finds almost everywhere a filename is.
     * @param filename The filename to look for,
     * @return an open stream for retrieving the file's data.
     * @throws FileNotFoundException in case there's no property file to look
     * for.
     */
    private static InputStream ultimateFind(String filename)
            throws FileNotFoundException {
        for(ClassLoader cl : getClassLoaders()) {
            InputStream in = cl.getResourceAsStream(filename);
            if(in != null) {
                return in;

            } // if(in != null)

        } // for(ClassLoader cl : getClassLoaders())

        File file_in = new File(System.getProperty("user.dir"), filename);
        return new FileInputStream(file_in);

    } // ultimateFind(String filename)

    /**
     * Creates an instance of {@see XMLUtil}.
     * @throws JAXBException in case there's a JAXB problem.
     */
    public XMLUtil() throws JAXBException {
        jaxbCtx = JAXBContext.newInstance(
                ObjectFactory.class.getPackage().getName());

    } // XMLUtil()

    /**
     * Unmarshall an XML charsequence list into a Java object.
     * @param source the source XML.
     * @return the unmarshalled instance.
     * @throws JAXBException in case there's a JAXB problem.
     */
    public Packet unmarshallPacket(CharSequence source)
            throws JAXBException {
        Unmarshaller unmarshaller = jaxbCtx.createUnmarshaller();
        JAXBElement<Packet> elem = unmarshaller.unmarshal(new StreamSource(
                new StringReader(source.toString())), Packet.class);
        return elem.getValue();

    } // unmarshallPacket(CharSequence source)

    /**
     * Marshalls a Java object into XML
     * @param packet the Java object to marshall.
     * @return the marshalled XML.
     * @throws JAXBException in case there's a JAXB problem.
     */
    public CharSequence marshallPacket(Packet packet) throws JAXBException {
        Marshaller marshaller = jaxbCtx.createMarshaller();
        marshaller.setProperty("com.sun.xml.bind.xmlDeclaration", false);
        StringWriter sw = new StringWriter(4096);
        marshaller.marshal(new ObjectFactory().createPacket(packet), sw);
        return sw.toString();

    } // marshallPacket(Packet packet)

} // class XMLUtil
