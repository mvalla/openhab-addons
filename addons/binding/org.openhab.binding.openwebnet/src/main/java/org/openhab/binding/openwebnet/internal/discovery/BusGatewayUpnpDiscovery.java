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
package org.openhab.binding.openwebnet.internal.discovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.upnp.UpnpDiscoveryParticipant;
//import org.eclipse.smarthome.config.discovery.UpnpDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.types.UDN;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BusGatewayUpnpDiscovery} is responsible for discovering new and removed supported BTicino BUS
 * gateways devices using UPnP. It uses the central {@link UpnpDiscoveryService} implementing
 * {@link UpnpDiscoveryParticipant}.
 *
 * @author Massimo Valla - Initial contribution
 */

@Component(service = UpnpDiscoveryParticipant.class, immediate = true)
public class BusGatewayUpnpDiscovery implements UpnpDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(BusGatewayUpnpDiscovery.class);

    public enum BusGatewayId {
        MH202("scheduler", "MH202"),
        F454("webserver", "F454"),
        MY_HOME_SERVER1("myhomeserver1", "MYHOMESERVER1"),
        TOUCH_SCREEN_10("ts10", "TOUCHSCREEN10"),
        MH200N("lightingcontrolunit", "MH200N");

        private final String value, thingId;

        private BusGatewayId(String value, String thingId) {
            this.value = value;
            this.thingId = thingId;
        }

        public static BusGatewayId fromValue(String s) {
            Optional<BusGatewayId> m = Arrays.stream(values()).filter(val -> s.equals(val.value)).findFirst();
            return m.orElse(null);
        }

        public String getThingId() {
            return thingId;
        }
    }

    /**
     * DeviceInfo bean to store device useful info and log them
     */
    public class DeviceInfo {
        private String friendlyName = "<unknown>";
        private String modelName = "<unknown>";
        private String modelDescription = "<unknown>";
        private String modelNumber = "<unknown>";
        private String serialNumber = "<unknown>";
        private String host;
        private String manufacturer = "<unknown>";
        private UDN udn;
        private boolean isBTicino = false;

        private DeviceInfo(RemoteDevice device) {
            logger.info("+=== UPnP =========================================");
            RemoteDeviceIdentity identity = device.getIdentity();
            if (identity != null) {
                this.udn = identity.getUdn();
                logger.info("| ID.UDN       : {}", udn);
                if (identity.getDescriptorURL() != null) {
                    logger.info("| ID.DESC URL  : {}", identity.getDescriptorURL());
                    this.host = identity.getDescriptorURL().getHost();
                }
                logger.info("| ID.MAX AGE : {}", identity.getMaxAgeSeconds());
                // logger.info("| ID.LOC_ADDR : {}", identity.getDiscoveredOnLocalAddress());
            }
            logger.info("| --------------");
            DeviceDetails details = device.getDetails();
            if (details != null) {
                ManufacturerDetails manufacturerDetails = details.getManufacturerDetails();
                if (manufacturerDetails != null) {
                    this.manufacturer = manufacturerDetails.getManufacturer();
                    logger.info("| MANUFACTURER : {} ({})", manufacturer, manufacturerDetails.getManufacturerURI());
                    if (manufacturer != null && manufacturer.toUpperCase().contains("BTICINO")) {
                        this.isBTicino = true;
                    }
                }
                ModelDetails modelDetails = details.getModelDetails();
                if (modelDetails != null) {
                    // Model Name | Desc | Number (Uri)
                    this.modelName = modelDetails.getModelName();
                    this.modelDescription = modelDetails.getModelDescription();
                    this.modelNumber = modelDetails.getModelNumber();
                    logger.info("| MODEL        : {} | {} | {} ({})", modelName, modelDescription, modelNumber,
                            modelDetails.getModelURI());
                }
                if (isBTicino) {
                    this.friendlyName = details.getFriendlyName();
                    logger.info("| FRIENDLY NAME: {}", friendlyName);
                    this.serialNumber = details.getSerialNumber();
                    logger.info("| SERIAL #     : {}", serialNumber);
                    logger.info("| BASE URL     : {}", details.getBaseURL());
                    logger.info("| UPC          : {}", details.getUpc());
                    logger.info("| PRES. URI    : {}", details.getPresentationURI());
                }
            }
            logger.info("+==================================================");
        }

    } /* DeviceInfo */

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY);
    }

    @Override
    public @Nullable DiscoveryResult createResult(RemoteDevice device) {
        logger.info("==OWN:UPnP== createResult() device={}", device);
        DeviceInfo devInfo = new DeviceInfo(device);
        ThingUID thingId = generateThingUID(devInfo);
        if (thingId != null) {
            if (devInfo.host != null) {
                Map<String, Object> properties = new HashMap<>(4);
                String label = "BUS Gateway (" + thingId.getId().split("-")[0] + ")";
                try {
                    label = "BUS Gateway " + devInfo.friendlyName + " (" + devInfo.host + ", v" + devInfo.modelNumber
                            + ")";
                } catch (Exception e) {
                    // ignore and use default label
                }
                // properties.put(UDN, device.getIdentity().getUdn().getIdentifierString());
                // DiscoveryResult result =
                // DiscoveryResultBuilder.create(uid).withProperties(properties).withLabel(label)
                // .withRepresentationProperty(UDN).build();
                properties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_HOST, devInfo.host);
                properties.put(OpenWebNetBindingConstants.PROPERTY_FIRMWARE, devInfo.modelNumber);
                properties.put(OpenWebNetBindingConstants.PROPERTY_MODEL, devInfo.modelName);
                properties.put(OpenWebNetBindingConstants.PROPERTY_SERIAL_NO, devInfo.serialNumber);
                DiscoveryResult result = DiscoveryResultBuilder.create(thingId).withProperties(properties)
                        .withLabel(label).build();
                logger.info("==OWN:UPnP== Created a DiscoveryResult for gatway '{}' (UDN={})", devInfo.friendlyName,
                        devInfo.udn.getIdentifierString());
                return result;
            } else {
                logger.warn("==OWN:UPnP== Could not get host for device (UDN={})", devInfo.udn);
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public @Nullable ThingUID getThingUID(RemoteDevice device) {
        logger.info("==OWN:UPnP== getThingUID()");
        return generateThingUID(new DeviceInfo(device));
    }

    /** generate a ThingUID from already extracted DeviceInfo */
    private @Nullable ThingUID generateThingUID(DeviceInfo devInfo) {
        if (devInfo != null && devInfo.isBTicino) {
            String idString = devInfo.udn.getIdentifierString();
            // uuid:pnp-scheduler-1_0-00:03:50:8F:59:48
            BusGatewayId gwId = BusGatewayId.fromValue(idString.split("-")[1]);
            if (gwId != null) {
                logger.debug("==OWN:UPnP== '{}' is a supported gateway", gwId);
                String mac = idString.split("-")[3];
                String normalizedMac = mac.toLowerCase().replaceAll("[^a-f0-9]", "");
                if (!normalizedMac.equals("")) {
                    return new ThingUID(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY,
                            gwId.getThingId() + "_" + normalizedMac);
                }
            } else {
                logger.warn("==OWN:UPnP== device is not supported by the binding (UDN={})", idString);
            }
        }
        return null;
    }

} /* class */
