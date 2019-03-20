/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.openwebnet.handler;

import static org.openhab.binding.openwebnet.OpenWebNetBindingConstants.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openhab.binding.openwebnet.internal.discovery.OpenWebNetDeviceDiscoveryService;
import org.openwebnet.OpenDeviceType;
import org.openwebnet.OpenError;
import org.openwebnet.OpenGateway;
import org.openwebnet.OpenGatewayBus;
import org.openwebnet.OpenGatewayZigBee;
import org.openwebnet.OpenListener;
import org.openwebnet.OpenNewDeviceListener;
import org.openwebnet.OpenWebNet;
import org.openwebnet.message.Automation;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.CEN;
import org.openwebnet.message.CENPlusScenario;
import org.openwebnet.message.CENScenario;
import org.openwebnet.message.EnergyManagement;
import org.openwebnet.message.GatewayManagement;
import org.openwebnet.message.Lighting;
import org.openwebnet.message.OpenMessage;
import org.openwebnet.message.OpenMessageFactory;
import org.openwebnet.message.Thermoregulation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetBridgeHandler} is responsible for handling communication with gateways and handling events.
 *
 * @author Massimo Valla - Initial contribution
 */
@NonNullByDefault
public class OpenWebNetBridgeHandler extends ConfigStatusBridgeHandler implements OpenListener {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetBridgeHandler.class);

    private static final int GATEWAY_ONLINE_TIMEOUT = 20; // (sec) Time to wait for the gateway to become connected
    private static final int CONFIG_GATEWAY_DEFAULT_PORT = 20000;
    private static final String CONFIG_GATEWAY_DEFAULT_PASSWD = "12345";

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.BRIDGE_SUPPORTED_THING_TYPES;

    // ConcurrentHashMap of devices registered to this BridgeHandler
    // Association is: String ownId -> OpenWebNetThingHandler, with ownId = WHO.WHERE
    private Map<String, OpenWebNetThingHandler> registeredDevices = new ConcurrentHashMap<>();

    @Nullable
    protected OpenGateway gateway;
    private boolean isBusGateway = false;

    private boolean isGatewayConnected = false;

    @Nullable
    public OpenWebNetDeviceDiscoveryService deviceDiscoveryService;
    private boolean searchingGatewayDevices = false; // devices search is in progress on gateway
    private boolean scanIsActive = false; // a device scan has been activated by OpenWebNetDeviceDiscoveryService;
    private boolean discoveryByActivation = false; // discover BUS devices when they are activated also when a device
                                                   // scan is not active
    @Nullable
    private OpenNewDeviceListener deviceDiscoveryListener = null;

    public OpenWebNetBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Nullable
    public OpenGateway getGateway() {
        return gateway;
    }

    public boolean isBusGateway() {
        return isBusGateway;
    }

    @Override
    public void initialize() {
        logger.debug("==OWN== BridgeHandler.initialize() ");

        ThingTypeUID thingType = getThing().getThingTypeUID();
        logger.debug("==OWN== Bridge type: {}", thingType);

        if (thingType.equals(THING_TYPE_DONGLE)) {
            initZigBeeGateway();
        } else {
            initBusGateway();
            isBusGateway = true;
        }

        // FIXME debug
        if (!this.testTransformations()) {
            logger.error("==OWN== @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ TRANSFORMATION ERORR");
            return;
        }
        // FIXME end-debug

        gateway.subscribe(this);
        if (gateway.isConnected()) { // gateway is already connected, device can go ONLINE
            isGatewayConnected = true;
            logger.info("==OWN== ------------------- ALREADY CONNECTED -> setting status to ONLINE");
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.UNKNOWN);
            logger.debug("==OWN== Trying to connect gateway...");
            gateway.connect();
            scheduler.schedule(() -> {
                // if status is still UNKNOWN after timer ends, set the device as OFFLINE
                if (thing.getStatus().equals(ThingStatus.UNKNOWN)) {
                    logger.info("==OWN== BridgeHandler status still UNKNOWN. Setting device={} to OFFLINE",
                            thing.getUID());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR);
                }
            }, GATEWAY_ONLINE_TIMEOUT, TimeUnit.SECONDS);
        }

        // TODO
        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    /**
     * Init a ZigBee gateway based on config properties
     *
     */
    private void initZigBeeGateway() {
        String serialPort = (String) (getConfig().get(CONFIG_PROPERTY_SERIAL_PORT));
        if (serialPort == null) {
            logger.warn(
                    "==OWN== BridgeHandler ZigBee gateway port config is <null>, will try to find a gateway on serial ports");
            gateway = OpenWebNet.gatewayZigBeeAsSingleton(); // TODO do not use singleton
        } else {
            // TODO connect to serial port specified using config params
            gateway = OpenWebNet.gatewayZigBeeAsSingleton();
        }
    }

    /**
     * Init a BUS/SCS gateway based on config properties
     *
     */
    private void initBusGateway() {
        if (getConfig().get(CONFIG_PROPERTY_HOST) != null) {
            String host = (String) (getConfig().get(CONFIG_PROPERTY_HOST));
            int port = CONFIG_GATEWAY_DEFAULT_PORT;
            Object portConfig = getConfig().get(CONFIG_PROPERTY_PORT);
            if (portConfig != null) {
                port = ((BigDecimal) portConfig).intValue();
            }
            String passwd = (String) (getConfig().get(CONFIG_PROPERTY_PASSWD));
            if (passwd == null) {
                passwd = CONFIG_GATEWAY_DEFAULT_PASSWD;
            }
            String passwdMasked;
            if (passwd.length() >= 4) {
                passwdMasked = "******" + passwd.substring(passwd.length() - 3, passwd.length());
            } else {
                passwdMasked = "******";
            }
            String discoveryConfig = (String) getConfig().get(CONFIG_PROPERTY_DISCOVERY_ACTIVATION);
            if (discoveryConfig != null && discoveryConfig.equalsIgnoreCase("true")) {
                discoveryByActivation = true;
            }
            logger.debug("==OWN== Creating new BUS gateway with config properties: {}:{}, pwd={}", host, port,
                    passwdMasked);
            gateway = OpenWebNet.gatewayBus(host, port, passwd);
        } else {
            logger.warn(
                    "==OWN== BridgeHandler Cannot connect to gateway. No host/IP has been provided in Bridge configuration.");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-no-ip-address");
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("==OWN== BridgeHandler handleCommand (command={} - channel={})", command, channelUID);
        if (!gateway.isConnected()) {
            logger.warn("==OWN== BridgeHandler gateway is NOT connected, skipping command");
            return;
        } else {
            logger.warn("==OWN== BridgeHandler Channel not supported: channel={}", channelUID);
        }
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        logger.debug("==OWN== BridgeHandler.getConfigStatus() ");
        Collection<ConfigStatusMessage> configStatusMessages;

        configStatusMessages = Collections.emptyList();

        return configStatusMessages;
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);

        logger.info("==OWN== Bridge configuration updated.");
        // for (Thing t : getThing().getThings()) {
        // final ThingHandler thingHandler = t.getHandler();
        // if (thingHandler != null) {
        // thingHandler.thingUpdated(t);
        // }
        // }
    }

    @Override
    public void handleRemoval() {
        logger.debug("==OWN== BridgeHandler.handleRemoval() ");
        if (gateway != null) {
            gateway.closeConnection();
            gateway.unsubscribe(this);
            logger.debug("==OWN== Connection closed and unsubscribed.");
        }
        logger.debug("==OWN== now calling super.handleRemoval()");
        super.handleRemoval();
    }

    @Override
    public void dispose() {
        logger.debug("==OWN== BridgeHandler.dispose() ");
        if (gateway != null) {
            gateway.closeConnection();
            gateway.unsubscribe(this);
            logger.debug("==OWN== Connection closed and unsubscribed.");
        }
        logger.debug("==OWN== now calling super.dispose()");
        super.dispose();
    }

    /**
     * Search for devices connected to this bridge handler's gateway
     *
     * @param listener to receive device found notifications
     */
    public synchronized void searchDevices(OpenNewDeviceListener listener) {
        logger.debug("==OWN==  -------- BridgeHandler.searchDevices()");
        scanIsActive = true;
        logger.debug("==OWN== -------- scanIsActive={}", scanIsActive);
        deviceDiscoveryListener = listener;
        if (gateway != null) {
            if (!searchingGatewayDevices) {
                if (!gateway.isConnected()) {
                    logger.warn("==OWN== -------- Gateway is NOT connected, cannot search for devices");
                    return;
                }
                searchingGatewayDevices = true;
                logger.info("==OWN== -------- STARTED active search for devices on gateway '{}'",
                        this.getThing().getLabel());
                try {
                    gateway.discoverDevices(listener);
                } catch (Exception e) {
                    logger.error("==OWN== -------- EXCEPTION while searching devices on gateway '{}': {}",
                            this.getThing().getLabel(), e.getMessage());
                }
                searchingGatewayDevices = false;
                logger.info("==OWN== -------- FINISHED active search for devices on gateway '{}'",
                        this.getThing().getLabel());

            } else {
                logger.warn("==OWN== -------- Searching devices on gateway {} already activated",
                        this.getThing().getLabel());
                return;
            }
        } else {
            logger.warn("==OWN== -------- Cannot search devices: no gateway associated to this handler");
        }
    }

    /**
     * NOtifies that the scan has been stopped/aborted by OpenWebNetDeviceDiscoveryService
     *
     */
    public void scanStopped() {
        scanIsActive = false;
        deviceDiscoveryListener = null;
        logger.debug("==OWN== -------- scanIsActive={}", scanIsActive);
    }

    private void discoverByActivation(BaseOpenMessage baseMsg) {
        logger.debug("==OWN==  BridgeHandler.discoverByActivation() ");
        if (baseMsg instanceof Lighting || baseMsg instanceof Automation || baseMsg instanceof CEN
                || baseMsg instanceof Thermoregulation || baseMsg instanceof EnergyManagement) {
            OpenDeviceType type = baseMsg.detectDeviceType();
            if (type != null) {
                deviceDiscoveryService.newDiscoveryResult(baseMsg.getWhere(), type, baseMsg);
            }
        }
    }

    /**
     * Register a device ThingHandler to this BridgHandler
     *
     * @param String                 ownId device OpenWebNet id
     * @param OpenWebNetThingHandler thingHandler to register
     */
    protected void registerDevice(String ownId, OpenWebNetThingHandler thingHandler) {
        if (registeredDevices.containsKey(ownId)) {
            logger.warn("==OWN:BridgeHandler== registering device with an existing ownId={}", ownId);
        }
        registeredDevices.put(ownId, thingHandler);
        logger.info("==OWN:BridgeHandler== registered device ownId={}, thing={}", ownId,
                thingHandler.getThing().getUID());
    }

    /**
     * Un-register a device from this bridge handler
     *
     * @param ownId device OpenWebNet id
     */
    protected void unregisterDevice(String ownId) {
        if (registeredDevices.remove(ownId) != null) {
            logger.info("==OWN:BridgeHandler== un-registered device ownId={}", ownId); // TODO move to debug
        } else {
            logger.warn("==OWN:BridgeHandler== could not un-register ownId={} (not found)", ownId);
        }
    }

    /**
     * Get a ThingHandler for a device associated to this BridgeHandler, based on ownID
     *
     * @param String ownId OpenWebNet id for device
     * @returns OpenWebNetThingHandler handler for the device, or null if the device is not associated with this
     *          BridgeHanldler
     */
    private @Nullable OpenWebNetThingHandler getDevice(String ownId) {
        return registeredDevices.get(ownId);
    }

    @Override
    public void onMessage(OpenMessage msg) {
        logger.trace("==OWN==  RECEIVED <<<<< {}", msg);
        // TODO provide direct methods msg.isACK() and msg.isNACK()
        if (OpenMessage.ACK.equals(msg.getValue()) || OpenMessage.NACK.equals(msg.getValue())) {
            return; // we ignore ACKS/NACKS
        }
        // GATEWAY MANAGEMENT
        if (msg instanceof GatewayManagement) {
            GatewayManagement gwMgmtMsg = (GatewayManagement) msg;
            logger.debug("==OWN==  GatewayManagement WHAT = {}", gwMgmtMsg.getWhat());
            return;
        }

        BaseOpenMessage baseMsg = (BaseOpenMessage) msg;
        // let's try to get the Thing associated with this message...
        if (baseMsg instanceof Lighting || baseMsg instanceof Automation || baseMsg instanceof Thermoregulation
                || baseMsg instanceof EnergyManagement || baseMsg instanceof CENScenario
                || baseMsg instanceof CENPlusScenario) {
            String ownId = ownIdFromMessage(baseMsg);
            logger.debug("==OWN==  ownId={}", ownId);
            OpenWebNetThingHandler deviceHandler = getDevice(ownId);
            if (deviceHandler == null) {
                if (isBusGateway && ((deviceDiscoveryListener != null && !searchingGatewayDevices && scanIsActive)
                        || (discoveryByActivation && !scanIsActive))) {
                    // try device discovery by activation
                    discoverByActivation(baseMsg);
                } else {
                    logger.debug("==OWN==  ownId={} has NO DEVICE associated, ignoring it", ownId);
                }
            } else {
                // OpenWebNetThingHandler deviceHandler = (OpenWebNetThingHandler) device.getHandler();
                // if (deviceHandler != null) {
                deviceHandler.handleMessage(baseMsg);
                // } else {
                // logger.debug("==OWN== ownId={} has NO HANDLER associated, ignoring it", ownId);
                // }
            }
        } else {
            logger.debug("==OWN==  BridgeHandler ignoring frame {}. WHO={} is not supported by the binding", baseMsg,
                    baseMsg.getWho());
        }

    }

    @Override
    public void onConnected() {
        isGatewayConnected = true;
        if (gateway instanceof OpenGatewayZigBee) {
            logger.info("==OWN== ------------------- CONNECTED to ZigBee gateway - USB port: {}",
                    ((OpenGatewayZigBee) gateway).getConnectedPort());
        } else {
            logger.info("==OWN== ------------------- CONNECTED to BUS gateway - {}:{}",
                    ((OpenGatewayBus) gateway).getHost(), ((OpenGatewayBus) gateway).getPort());
            // update gw model
            String currentGwModel = (editProperties().get(PROPERTY_MODEL));
            // String currentGwModel = (String) (getConfig().get(PROPERTY_MODEL));
            if (currentGwModel == null || currentGwModel.equals("Unknown")) {
                updateProperty(PROPERTY_MODEL, ((OpenGatewayBus) gateway).getModelName());
                logger.debug("==OWN== updated gw model: {}", ((OpenGatewayBus) gateway).getModelName());
            }
        }
        updateStatus(ThingStatus.ONLINE);

    }

    @Override
    public void onConnectionError(OpenError error, String errMsg) {
        String cause;
        switch (error) {
            case DISCONNECTED:
            case LIB_LINKAGE_ERROR:
                cause = "Please check that the ZigBee dongle is correctly plugged-in, and the driver installed and loaded";
                break;
            case NO_SERIAL_PORTS_ERROR:
                cause = "No serial ports found";
                break;
            case JVM_ERROR:
                cause = "Make sure you have a working Java Runtime Environment installed in 32 bit version";
                break;
            case IO_EXCEPTION_ERROR:
                cause = "Connection error (IOException). Check network and gateway thing Configuration Parameters ("
                        + errMsg + ")";
                break;
            case AUTH_ERROR:
                cause = "Authentication error. Check gateway password in Thing Configuration Parameters (" + errMsg
                        + ")";
                break;
            case OTHER_ERROR:
            default:
                cause = "==ERROR NOT RECOGNIZED==";
                break;
        }
        logger.warn("==OWN==  CONNECTION ERROR: {} - {}", cause, errMsg);
        isGatewayConnected = false;
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, cause);
    }

    @Override
    public void onConnectionClosed() {
        isGatewayConnected = false;
        logger.debug("==OWN==  onConnectionClosed() - isGatewayConnected={}", isGatewayConnected);
        // cannot change to OFFLINE here because we are already in REMOVING state
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "The CONNECTION to the gateway HAS BEEN CLOSED");
    }

    @Override
    public void onDisconnected() {
        isGatewayConnected = false;
        logger.warn("==OWN== ---------- DISCONNECTED from the gateway");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                "The gateway HAS BEEN DISCONNECTED");
        logger.debug("==OWN==  Bridge status set to OFFLINE");
        // TODO start here a re-connect cycle??
    }

    @Override
    public void onReconnected() {
        logger.info("==OWN== ------------------- RE-CONNECTED to gateway!");
        updateStatus(ThingStatus.ONLINE);
        logger.debug("==OWN== Bridge status set to ONLINE");
        // TODO refresh devices' status?

    }

    /**
     * Return a ownId string (=WHO.WHERE) from a WHERE String and ThingHandler
     *
     * @param String                 where WHERE address
     * @param OpenWebNetThingHandler thing handler
     * @return ownId string
     */
    /*
     * protected String ownIdFromWhere(String where, OpenWebNetThingHandler handler) {
     * return handler.ownIdPrefix() + "." + normalizeWhere(where);
     * }
     */

    /**
     * Return a ownId string (=WHO.WHERE) from a deviceWhere thing config parameter and ThingHandler.
     * In case of ZigBee gatewya, ownId=Z.ADDR
     *
     * @param String                 deviceWhere
     * @param OpenWebNetThingHandler thing handler
     * @return ownId string
     */
    protected String ownIdFromDeviceWhere(String deviceWhere, OpenWebNetThingHandler handler) {
        if (isBusGateway) {
            return handler.ownIdPrefix() + "." + deviceWhere;
        } else {
            return "Z" + "." + deviceWhere;
        }
    }

    public String ownIdFromWhoWhere(String where, String who) {
        return who + "." + normalizeWhere(where);
    }

    /**
     * Return a ownId string (=WHO.WHERE) from a BaseOpenMessage
     *
     * @param BaseOpenMessage baseMsg message
     * @return ownId String
     */
    private String ownIdFromMessage(BaseOpenMessage baseMsg) {
        return baseMsg.getWho().value() + "." + normalizeWhere(baseMsg.getWhere());
    }

    /**
     * Transform a WHERE string address into a Thing id string based on bridge type (BUS/ZigBee).
     * '#' in WHERE are changed to 'h'
     *
     * @param String where WHERE address
     * @return String thing Id
     */
    public String thingIdFromWhere(String where) {
        return normalizeWhere(where).replace('#', 'h'); // '#' cannot be used in ThingUID;
    }

    // @formatter:off
    /**
     *
     *                              deviceWhere (DevAddrParam)
     * TYPE         WHERE           normalizeWhere   ownId       ThingID
     * ---------------------------------------------------------------
     * Zigbee       789309801#9     7893098          Z.7893098   7893098   (*)
     * Switch       51              51               1.51        51
     * Dimmer       25#4#01         25#4#01          1.25#4#01   25h4h01   (*)
     * Autom        93              93               2.93        93
     * Thermo       #1 or 1         1                4.1         1         (*)
     * TempSen      500             500              4.500       500
     * Energy       51              51               18.51       51
     * CEN          51              51               15.51       51
     * CEN+         212             212              25.212      212
     * DryContact   399             399              25.399      399
     *
     *        METHOD                            CALLED FROM                                     CALLING
     *        ------                            -----------                                     -------
     *      - OpenWebNetDeviceDiscoveryService new discovery result                         --> deviceWhere = normalizeWhere()
     *      - ThingHandler.initialize                                                       --> ownId = ownIdFromDeviceWhere()
     *      - public    normalizeWhere()        locally and OpenWebNetDeviceDiscoveryService    --> getAddrFromWhere
     *      - public    getAddrFromWhere                                                    --> remove last 4
     *      - protected ownIdFromDeviceWhere()  ThingHandler.initialize                         --> ownIdPrefix() + "." + deviceWhere

     *      - private   ownIdFromMessage()  onMessage()
     *      - public    thingIdFromWhere()  OpenWebNetDeviceDiscoveryService                --> normalizeWhere().replace(#)
     */

    public enum TEST {
        zigbee("789309801#9","1","7893098","1.7893098","7893098"),
        sw("51","1","51","1.51","51"),
        dimmer("25#4#01","1","25#4#01","1.25#4#01","25h4h01"),
        thermo("#1","4","1","4.1","1"),
        tempSen("500","4","500","4.500","500"),
        energy("51","18","51","18.51","51");

        public final String where, who,norm,ownId,thing ;

        private TEST(String where,String who, String norm,String ownId,String thing) {
            this.where = where;
            this.who = who;
            this.norm=norm;
            this.ownId= ownId;
            this.thing = thing;
        }
/*
        @Nullable
        public static TEST fromValue(String where) {
            Optional<TEST> t = Arrays.stream(values()).filter(test -> where.equals(test.where)).findFirst();
            TEST ret = t.orElse(null);
        }
*/
    }

    private boolean testTransformations() {
        boolean[] testResults = new boolean[6];
     for  (int i = 0; i < TEST.values().length; i++) {
         TEST test = TEST.values()[i];
         if ( (isBusGateway && test!=TEST.zigbee) || (!isBusGateway && test == TEST.zigbee) ) {
             testResults[i] = test.norm.equals(normalizeWhere(test.where));
             testResults[i] = testResults[i] && test.ownId.equals(ownIdFromWhoWhere(test.where, test.who));
             testResults[i] = testResults[i] && test.ownId.equals(ownIdFromMessage((BaseOpenMessage)OpenMessageFactory.parse("*"+test.who+"*1*"+test.where+"##")));
             testResults[i] = testResults[i] && test.thing.equals(thingIdFromWhere(test.where));
         }  else {
             testResults[i] = true;
         }
     }
    return testResults[0]&&testResults[1]&&testResults[2]&&testResults[3]&&testResults[4]&&testResults[5];

    }

 // @formatter:on

    /**
     * Normalize a WHERE string for Thermo and Zigbee devices
     */
    public String normalizeWhere(String where) {
        String str = "";
        if (isBusGateway) {
            if (where.indexOf('#') < 0) { // no hash present
                str = where;
            } else if (where.indexOf("#4#") > 0) { // local bus: APL#4#bus
                str = where;
            } else if (where.indexOf('#') == 0) { // thermo zone via central unit: #0 or #Z (Z=[1-99]) --> Z
                str = where.substring(1);
            } else if (where.indexOf('#') > 0) { // thermo zone and actuator N: Z#N (Z=[1-99], N=[1-9]) -- > Z
                str = where.substring(0, where.indexOf('#'));
            } else {
                logger.warn("==OWN== normalizeWhere() unexpected WHERE: {}", where);
                str = where;
            }
            return str;
        } else {
            return OpenMessageFactory.getAddrFromWhere(where);
        }
    }

}
