/*
 * File:   App.java
 * Author: A00663387 - Alejandro Vázquez Cantú.
 *
 * Created on 10 de marzo de 2010, 08:20 PM
 */

package mx.itesm.gda.tc4003_1.logicclock;

import static java.lang.Runtime.getRuntime;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import javax.xml.bind.JAXBException;
import mx.itesm.gda.tc4003_1.logicclock.binding.Packet;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Main class
 * @author alexv
 */
public class App {

    /**
     * Generic Logger
     */
    private static final Logger LOGGER = getLogger(App.class);

    /**
     * Mean wait between packets to send.
     */
    private static final double MEAN_WAIT = 5000d; // Milliseconds

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
        xmlQueue = new SynchronousQueue<>();

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
     * Decodes an incoming {@see Message} and call the proper packet
     * processor.
     * @param msg the incoming message.
     */
    public void receive(Message msg) {
        CharsetDecoder decoder = UTF_8.newDecoder();

        try {
            CharBuffer buff = decoder.decode(ByteBuffer.wrap(
                    msg.getRawBuffer(), msg.getOffset(), msg.getLength()));
            xmlQueue.put(buff);

        } catch(InterruptedException | CharacterCodingException e) {
            LOGGER.error("Cannot receive xml", e);

        } // try ... catch

    } // receive(Message msg)

    /**
     * Executes the main loop sending {@see Message}s.
     */
    public void execute() {
        try {
            channel = new JChannel();
            channel.setDiscardOwnMessages(true);
            channel.connect(groupName);

        } catch(RuntimeException re) {
            throw re;

        } catch(Exception che) {
            LOGGER.error("Cannot connect to group {}", groupName);
            throw new RuntimeException(che);

        } // try ... catch

        try {
            channel.setReceiver(this::receive);

            LOGGER.info("Connected to group {}", groupName);

            long wait = nextWait();
            long start_time = currentTimeMillis();

            while(keepRunning) {
                try {
                    CharSequence xml = xmlQueue.poll(wait, MILLISECONDS);

                    if(xml != null) {
                        processPacket(xml);

                    } // if(xml != null)

                } catch(InterruptedException ie) {
                    interrupted();
                    continue;

                } // try ... catch

                long end_time = currentTimeMillis();
                wait -= (end_time - start_time);
                start_time = end_time;

                if(wait < 1) {
                    try {
                        CharSequence xml = generatePacket();
                        CharsetEncoder encoder = UTF_8.newEncoder();
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
            out.println("Usage: java -jar LogicClock.jar");
            return;

        } // if(args.length != 0)

        final App app = new App("lamport-cluster");
        final Thread main_t = currentThread();

        getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Closing operations");
            app.keepRunning = false;
            main_t.interrupt();
        }));

        app.execute();

    } // main(String[] args)

} // class App

