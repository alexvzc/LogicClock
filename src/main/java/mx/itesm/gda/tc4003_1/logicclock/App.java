/*
 * File:   App.java
 * Author: A00663387 - Alejandro Vázquez Cantú.
 *
 * Created on 10 de marzo de 2010, 08:20 PM
 */

package mx.itesm.gda.tc4003_1.logicclock;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBException;
import mx.itesm.gda.tc4003_1.logicclock.binding.Packet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.Channel;
import org.jgroups.ChannelException;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

/**
 * Main class
 * @author alexv
 */
public class App {

    /**
     * Generic Logger
     */
    private static final Log LOGGER = LogFactory.getLog(App.class);

    /**
     * Mean wait between packets to send.
     */
    private static final double MEAN_WAIT = 5000d; // Milliseconds

    /**
     * Default charset used
     */
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * Group name to be used for {@see JChannel#connect(String)}
     */
    private String groupName;

    /**
     * Channel used for sending and receiving messages.
     */
    private JChannel channel;

    /**
     * Random generator for payload and delay.
     */
    private Random randomGenerator;

    /**
     * Internal clock.
     */
    private long internalClock;

    /**
     * XML incoming queue.
     */
    private BlockingQueue<CharSequence> xmlQueue;

    /**
     * Termination flag.
     */
    private boolean keepRunning;

    /**
     * Creates an instance of App.
     * @param group_name The group name to connect to.
     */
    public App(String group_name) {
        groupName = group_name;
        randomGenerator = new SecureRandom();
        internalClock = 0;
        keepRunning = true;
        xmlQueue = new SynchronousQueue<CharSequence>();

    } // App(String group_name)

    /**
     * Generate next delay time, using exponential distribution.
     * @return The generated delay to be observed.
     */
    private long nextWait() {
        double u = randomGenerator.nextDouble();
        double t = -Math.log(1d - u) * MEAN_WAIT;
        return Math.round(t);

    } // nextWait(long rate)

    /**
     * Generates an XML with a random payload. The packet's time
     * is the incremented {@see #internalClock}
     * @return the outgoing XML data.
     * @throws JAXBException In case it's unable to render XML.
     */
    private CharSequence generatePacket() throws JAXBException {
        XMLUtil utils = new XMLUtil();
        Packet packet = new Packet();
        packet.setPayload(new BigInteger(64, randomGenerator).toString(16));
        packet.setTimestamp(++internalClock);

        CharSequence xml = utils.marshallPacket(packet);
        LOGGER.info("Generating xml: " + xml);
        return xml;

    } // generatePacket()

    /**
     * Process an incoming packet. Makes the adjustment in {@see #internalClock}
     * if it's needed.
     * @param xml the incoming XML data.
     */
    private void processPacket(CharSequence xml) {
        LOGGER.info("Received xml: " + xml);

        try {
            XMLUtil utils = new XMLUtil();
            Packet packet = utils.unmarshallPacket(xml);
            ++internalClock;

            if(internalClock < packet.getTimestamp()) {
                LOGGER.info("Adjusting internal clock from: "
                        + internalClock + " to: " + packet.getTimestamp());

                internalClock = packet.getTimestamp();

            } // if(internalClock < packet.getTimestamp())

        } catch(JAXBException jaxbe) {
            LOGGER.error("Unable to parse xml", jaxbe);

        } // try ... catch

    } // processPacket(CharSequence xml)

    /**
     * Inner class to implement {@see Message} reception.
     */
    private class Receiver extends ReceiverAdapter {

        /**
         * Decodes an incoming {@see Message} and call the proper packet
         * processor.
         * @param msg the incoming message.
         */
        @Override
        public void receive(Message msg) {
            CharsetDecoder decoder = UTF8.newDecoder();

            try {
                CharBuffer buff = decoder.decode(ByteBuffer.wrap(
                        msg.getRawBuffer(), msg.getOffset(), msg.getLength()));
                xmlQueue.put(buff);

            } catch(Exception e) {
                LOGGER.error("Cannot receive xml", e);

            } // try ... catch

        } // receive(Message msg)

    } // class Receiver

    /**
     * Executes the main loop sending {@see Message}s.
     */
    public void execute() {
        try {
            channel = new JChannel();
            channel.setOpt(Channel.LOCAL, Boolean.FALSE);
            channel.connect(groupName);

        } catch(ChannelException che) {
            LOGGER.error("Cannot connect to group");
            throw new RuntimeException(che);

        } // try ... catch

        try {
            channel.setReceiver(new Receiver());

            LOGGER.info("Connected to group" + groupName);

            long wait = nextWait();
            long start_time = System.currentTimeMillis();

            while(keepRunning) {
                try {
                    CharSequence xml = xmlQueue.poll(wait,
                            TimeUnit.MILLISECONDS);

                    if(xml != null) {
                        processPacket(xml);

                    } // if(xml != null)

                } catch(InterruptedException ie) {
                    Thread.interrupted();
                    continue;

                } // try ... catch

                long end_time = System.currentTimeMillis();
                wait -= (end_time - start_time);
                start_time = end_time;

                if(wait < 1) {
                    try {
                        CharSequence xml = generatePacket();
                        CharsetEncoder encoder = UTF8.newEncoder();
                        ByteBuffer buff = encoder.encode(CharBuffer.wrap(xml));
                        Message msg = new Message();
                        msg.setBuffer(buff.array(), buff.position(),
                                buff.remaining());
                        channel.send(msg);

                    } catch(Exception e) {
                        LOGGER.error("Cannot send message", e);

                    } // try ... catch

                    wait = nextWait();

                } // if(wait < 1)

            } // while(keepRunning)

        } finally {
            channel.close();

        } // try .. finally

    } // execute()

    /**
     * Main program entry point
     * @param args the console arguments
     */
    public static void main(String[] args) {
        LOGGER.info("Starting up");
        if(args.length != 0) {
            System.out.println("Usage: java -jar LogicClock.jar");
            return;

        } // if(args.length != 0)


        final App app = new App("lamport-cluster");
        final Thread main_t = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                LOGGER.info("Closing operations");
                app.keepRunning = false;
                main_t.interrupt();
            }

        }));
        app.execute();

    } // main(String[] args)

} // class App

