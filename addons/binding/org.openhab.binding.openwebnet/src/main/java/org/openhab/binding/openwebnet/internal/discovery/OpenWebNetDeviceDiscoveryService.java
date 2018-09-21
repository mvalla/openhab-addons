/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openwebnet.internal.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryServiceCallback;
import org.eclipse.smarthome.config.discovery.ExtendedDiscoveryService;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openhab.binding.openwebnet.handler.OpenWebNetBridgeHandler;
import org.openwebnet.OpenDeviceType;
import org.openwebnet.OpenNewDeviceListener;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.OpenMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetDeviceDiscoveryService} is responsible for discovering OpenWebNet devices connected to a
 * bridge/gateway
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetDeviceDiscoveryService extends AbstractDiscoveryService
        implements ExtendedDiscoveryService, OpenNewDeviceListener {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.DEVICE_SUPPORTED_THING_TYPES;

    private final static int SEARCH_TIME = 60;

    private DiscoveryServiceCallback discoveryServiceCallback;

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetDeviceDiscoveryService.class);
    private final OpenWebNetBridgeHandler bridgeHandler;
    private final ThingUID bridgeUID;

    public OpenWebNetDeviceDiscoveryService(OpenWebNetBridgeHandler handler) {
        super(SEARCH_TIME);
        bridgeHandler = handler;
        bridgeUID = handler.getThing().getUID();
        logger.debug("==OWN:DeviceDiscovery== constructor for bridge: {}", bridgeUID);
    }

    @Override
    public void setDiscoveryServiceCallback(DiscoveryServiceCallback discoveryServiceCallback) {
        this.discoveryServiceCallback = discoveryServiceCallback;
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        logger.debug("==OWN:DeviceDiscovery== getSupportedThingTypes()");
        return OpenWebNetDeviceDiscoveryService.SUPPORTED_THING_TYPES;
    }

    @Override
    protected void startScan() {
        logger.info("==OWN:DeviceDiscovery== ------ startScan() - SEARCHING for devices...");
        bridgeHandler.searchDevices(this);
    }

    @Override
    protected void stopScan() {
        logger.debug("==OWN:DeviceDiscovery== stopScan()");
        // TOOD
    }

    @Override
    public void onNewDevice(String where, OpenDeviceType deviceType) {
        logger.info("==OWN:DeviceDiscovery== onNewDevice WHERE={}, deviceType={}", where, deviceType);
        ThingTypeUID thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_DEVICE; // generic device
        String thingLabel = OpenWebNetBindingConstants.THING_LABEL_DEVICE;
        if (deviceType != null) {
            switch (deviceType) {
                case ZIGBEE_ON_OFF_SWITCH: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_ON_OFF_SWITCH;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_ON_OFF_SWITCH;
                    break;
                }
                case ZIGBEE_DIMMER_SWITCH: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_DIMMER;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_DIMMER;
                    break;
                }
                case SCS_ON_OFF_SWITCH: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_BUS_ON_OFF_SWITCH;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_BUS_ON_OFF_SWITCH;
                    break;
                }
                case SCS_DIMMER_SWITCH: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_BUS_DIMMER;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_BUS_DIMMER;
                    break;
                }
                case SCS_SHUTTER_SWITCH:
                case SCS_SHUTTER_CONTROL: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_BUS_AUTOMATION;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_BUS_AUTOMATION;
                    break;
                }
                case ZIGBEE_SHUTTER_SWITCH:
                case ZIGBEE_SHUTTER_CONTROL: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_AUTOMATION;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_AUTOMATION;
                    break;
                }
                case SCS_TEMP_SENSOR: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_BUS_TEMP_SENSOR;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_BUS_TEMP_SENSOR;
                    break;
                }
                case SCS_THERMOSTAT: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_BUS_THERMOSTAT;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_BUS_THERMOSTAT;
                    break;
                }

                case SCS_THERMO_CENTRAL_UNIT: {
                    thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_BUS_THERMO_CENTRAL_UNIT;
                    thingLabel = OpenWebNetBindingConstants.THING_LABEL_BUS_THERMO_CENTRAL_UNIT;
                    break;
                }

                default:
                    logger.warn(
                            "==OWN:DeviceDiscovery== ***** device type {} is not supported, default to generic device (WHERE={})",
                            deviceType, where);
            }
        }
        String ownId = bridgeHandler.ownIdFromWhere(where);
        ThingUID thingUID = new ThingUID(thingTypeUID, bridgeUID, ownId.replace('#', 'h')); // '#' cannot be used in
                                                                                            // ThingUID

        DiscoveryResult discoveryResult = null;
        // check if a device with same thingUID has been found already in discovery results
        if (discoveryServiceCallback != null) {
            discoveryResult = discoveryServiceCallback.getExistingDiscoveryResult(thingUID);
        }
        String whereLabel = where;
        if (BaseOpenMessage.UNIT_02.equals(OpenMessageFactory.getUnit(where))) {
            logger.debug("==OWN:DeviceDiscovery== UNIT=02 found (WHERE={})", where);
            if (discoveryResult != null) {
                logger.debug("==OWN:DeviceDiscovery== will remove previous result if exists");
                thingRemoved(thingUID); // remove previously discovered thing
                // re-create thingUID with new type
                thingTypeUID = OpenWebNetBindingConstants.THING_TYPE_ON_OFF_SWITCH_2UNITS;
                thingLabel = OpenWebNetBindingConstants.THING_LABEL_ON_OFF_SWITCH_2UNITS;
                thingUID = new ThingUID(thingTypeUID, bridgeUID, ownId.replace('#', 'h'));
                whereLabel = whereLabel.replace("02#", "00#");
                logger.debug("==OWN:DeviceDiscovery== UNIT=02, switching type from {} to {}",
                        OpenWebNetBindingConstants.THING_TYPE_ON_OFF_SWITCH,
                        OpenWebNetBindingConstants.THING_TYPE_ON_OFF_SWITCH_2UNITS);
            } else {
                logger.warn("==OWN:DeviceDiscovery== discoveryResult empty after UNIT=02 discovery (WHERE={})", where);
            }
        }
        Map<String, Object> properties = new HashMap<>(1);
        properties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_WHERE, ownId);
        discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID).withProperties(properties)
                .withBridge(bridgeUID).withLabel(thingLabel + " (WHERE=" + whereLabel + ")").build();
        thingDiscovered(discoveryResult);
    }

    public void activate() {
        logger.debug("==OWN:DeviceDiscovery== activate()");
        // TODO useful ?????
        // hueBridgeHandler.registerLightStatusListener(this);
    }

    @Override
    public void deactivate() {
        logger.debug("==OWN:DeviceDiscovery== deactivate()");
        // TODO useful?????
        // removeOlderResults(new Date().getTime());
        // hueBridgeHandler.unregisterLightStatusListener(this);
    }

}
