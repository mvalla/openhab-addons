/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openwebnet.internal.discovery;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
//import org.openhab.binding.openwebnet.handler.OpenWebNetBridgeHandler;
import org.openwebnet.OpenError;
import org.openwebnet.OpenGatewayZigBee;
import org.openwebnet.OpenListener;
import org.openwebnet.OpenWebNet;
import org.openwebnet.bus.MyHomeSocketFactory;
import org.openwebnet.message.GatewayManagement;
import org.openwebnet.message.OpenMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetBridgeDiscoveryService} is responsible for discovering OpenWebNet bridges.
 *
 * @author Massimo Valla - Initial contribution
 */

public class OpenWebNetBridgeDiscoveryService extends AbstractDiscoveryService implements OpenListener {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetBridgeDiscoveryService.class);

    private final static int DISCOVERY_TIMEOUT = 30; // seconds

    // private OpenWebNetBridgeHandler bthandler; //not needed

    // TODO support multiple dongles at the same time
    private OpenGatewayZigBee zbgateway;
    private int dongleAddr = 0;
    private ThingUID dongleUID = null;

    public OpenWebNetBridgeDiscoveryService() {
        super(OpenWebNetBindingConstants.BRIDGE_SUPPORTED_THING_TYPES, DISCOVERY_TIMEOUT, false);

        logger.debug("#############################################################################################");
        logger.debug("==OWN:BridgeDiscovery== constructor()");
        logger.debug("#############################################################################################");
    }

    public OpenWebNetBridgeDiscoveryService(int timeout) throws IllegalArgumentException {
        super(timeout);
        logger.debug("==OWN:BridgeDiscovery== constructor(timeout)");
    }

    @Override
    protected void startScan() {
        logger.info("==OWN:BridgeDiscovery== ------ startScan() - SEARCHING for bridges...");
        startZigBeeScan();
        // FIXME
        // startBUSScan();
    }

    /**
     * OWN ZigBee gw discovery
     */
    private void startZigBeeScan() {
        if (zbgateway == null) {
            logger.debug("==OWN:BridgeDiscovery:Dongle== Gateway NULL, creating a new one ...");
            zbgateway = OpenWebNet.gatewayZigBeeAsSingleton();
            zbgateway.subscribe(this);
        }
        if (!zbgateway.isConnected()) {
            logger.debug("==OWN:BridgeDiscovery:Dongle== ... trying to connect dongle ...");
            zbgateway.connect();
        } else { // dongle is already connected
            logger.debug("==OWN:BridgeDiscovery:Dongle== ... dongle is already connected ...");
            if (dongleAddr != 0) {
                // a dongle was already discovered, notify new dongle thing to inbox
                logger.debug("==OWN:BridgeDiscovery:Dongle== ... dongle ADDR is: {}", dongleAddr);
                notifyNewDongleThing(dongleAddr);
            } else {
                logger.debug("==OWN:BridgeDiscovery:Dongle== ... requesting again MACAddress ...");
                zbgateway.send(GatewayManagement.requestMACAddress());
            }
        }
    }

    /**
     * BUS gw discovery for VDK on localhost:20000 (debug)
     *
     */
    private void startBUSScan() {
        String host = "localhost";
        int port = 20000;
        try {
            Socket monitorSk = MyHomeSocketFactory.openMonitorSession(host, port, "12345");
            monitorSk.close();
        } catch (IOException e) {
            logger.debug("==OWN:BridgeDiscovery== localhost GW not found");
            return;
        }
        ThingUID busgw = new ThingUID(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY, host);
        Map<String, Object> busgwProperties = new HashMap<>(3);
        busgwProperties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_HOST, host);
        busgwProperties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_PORT, Integer.toString(port));

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(busgw).withProperties(busgwProperties)
                .withLabel(OpenWebNetBindingConstants.THING_LABEL_BUS_GATEWAY + " (" + host + ":" + port + ")").build();
        logger.info("==OWN:BridgeDiscovery== --- BUS thing discovered: {}", discoveryResult.getLabel());
        thingDiscovered(discoveryResult);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        logger.debug("==OWN:BridgeDiscovery== getSupportedThingTypes()");
        return OpenWebNetBindingConstants.BRIDGE_SUPPORTED_THING_TYPES;
    }

    /**
     * Notifies to inbox a new USB dongle thing has been discovered
     */
    private void notifyNewDongleThing(int dongleAddr) {
        dongleUID = new ThingUID(OpenWebNetBindingConstants.THING_TYPE_DONGLE, Integer.toString(dongleAddr));
        Map<String, Object> dongleProperties = new HashMap<>(2);
        dongleProperties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_SERIAL_PORT, zbgateway.getConnectedPort());
        dongleProperties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_FIRMWARE, zbgateway.getDongleFirmwareVersion());

        DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(dongleUID).withProperties(dongleProperties)
                .withLabel(OpenWebNetBindingConstants.THING_LABEL_DONGLE + " (ID=" + dongleAddr + ", "
                        + zbgateway.getConnectedPort() + ", v=" + zbgateway.getDongleFirmwareVersion() + ")")
                .build();
        logger.info("==OWN:BridgeDiscovery== --- DONGLE thing discovered: {}", discoveryResult.getLabel());
        thingDiscovered(discoveryResult);
    }

    @Override
    public void onConnected() {
        logger.info("==OWN:BridgeDiscovery:Dongle== onConnected() FOUND DONGLE: CONNECTED port={}",
                zbgateway.getConnectedPort());
        dongleAddr = 0; // reset dongleAddr
        zbgateway.send(GatewayManagement.requestFirmwareVersion());
        zbgateway.send(GatewayManagement.requestMACAddress());
    }

    @Override
    public void onConnectionError(OpenError error) {
        if (error == OpenError.NO_SERIAL_PORTS_ERROR) {
            logger.info("==OWN:BridgeDiscovery== no serial ports found");
        } else {
            logger.warn("==OWN:BridgeDiscovery== onConnectionError() - CONNECTION ERROR: errorCode = {}", error);
        }
        stopScan();
        // TODO handle other dongle connection problems
    }

    @Override
    public void onConnectionClosed() {
        logger.debug("==OWN:BridgeDiscovery== onConnectionClosed()");
        stopScan();
    }

    @Override
    public void onDisconnected() {
        logger.error("==OWN:BridgeDiscovery== onDisconnected()");
        stopScan();
    }

    @Override
    public void onReconnected() {
        logger.info("==OWN:BridgeDiscovery== onReconnected()");
    }

    @Override
    public void onMessage(OpenMessage msg) {
        // TODO change this to listen to response to MACddress request session with timeout
        // and not to all messages that arrive here
        if (dongleAddr == 0) { // we do not know the discovered dongle addr yet, check if it was discovered with this
                               // message
            int addr = zbgateway.getDongleAddrAsDecimal();
            if (addr != 0) {
                // a dongle was discovered, notify new dongle thing to inbox
                dongleAddr = addr;
                logger.debug("==OWN:BridgeDiscovery== DONGLE ADDR is set: {}", dongleAddr);
                notifyNewDongleThing(dongleAddr);
            }
        } else {
            logger.trace("==OWN:BridgeDiscovery== onReceiveFrame() dongleAddr != 0 : ignoring (msg={})", msg);
        }

    }

}