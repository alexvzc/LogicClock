/*
 * File:   XMLUtil.java
 * Author: A00663387 - Alejandro Vázquez Cantú.
 *
 * Created on 10 de marzo de 2010, 08:20 PM
 */

package mx.itesm.gda.tc4003_1.logicclock;

import java.io.StringReader;
import java.io.StringWriter;
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
