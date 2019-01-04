/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.openwebnet.internal.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BusGatewayDiscoveryParticipant} is responsible for discovering new and removed supported BTicino BUS
 * gateways devices. It uses the central {@link UpnpDiscoveryService}.
 *
 * @author Massimo Valla - Initial contribution
 */

@Component(service = UpnpDiscoveryParticipant.class, immediate = true)
public class BusGatewayDiscoveryParticipant implements UpnpDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(BusGatewayDiscoveryParticipant.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY);
    }

    @Override
    public @Nullable DiscoveryResult createResult(RemoteDevice device) {
        ThingUID uid = getThingUID(device);
        if (uid != null) {
            Map<String, Object> properties = new HashMap<>(2);
            String label = "BTicino Gateway";
            try {
                label = device.getDetails().getFriendlyName();
            } catch (Exception e) {
                // ignore and use default label
            }
            // properties.put(UDN, device.getIdentity().getUdn().getIdentifierString());
            // DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties).withLabel(label)
            // .withRepresentationProperty(UDN).build();
            properties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_HOST, device.getDetails().getBaseURL().getHost());
            // properties.put(SERIAL_NUMBER, device.getDetails().getSerialNumber());
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties).withLabel(label)
                    .build();
            logger.info("==OWN:UPnP== Created a DiscoveryResult for device '{}' with UDN '{}'",
                    device.getDetails().getFriendlyName(), device.getIdentity().getUdn().getIdentifierString());
            return result;
        } else {
            return null;
        }
    }

    /**
     * Discover using UPnP devices and log them as INFO to track response
     *
     */
    @Override
    public @Nullable ThingUID getThingUID(RemoteDevice device) {
        return discoverDetails(device);
    }

    // TODO move to getThingUID
    private ThingUID discoverDetails(RemoteDevice device) {
        if (device != null) {
            // FIXME debug mode for now prints as log info about UPnP found device

            // print details from UPnP discovery and assign 'manufacturer'
            String manufacturer = null;
            logger.info("================================================");
            logger.info("==OWN:UPnP== DISCOVERED DEVICE: {}", device);
            RemoteDeviceIdentity identity = device.getIdentity();
            if (identity != null) {
                logger.info("=ID.UDN       : {}", identity.getUdn());
                logger.info("=ID.DESC URL  : {}", identity.getDescriptorURL());
                logger.info("=ID.MAX AGE   : {}", identity.getMaxAgeSeconds());
                logger.info("=ID.LOC_ADDR  : {}", identity.getDiscoveredOnLocalAddress());
            }
            DeviceDetails details = device.getDetails();
            if (details != null) {
                logger.info("=BASE URL     : {}", details.getBaseURL());
                logger.info("=FRIENDLY NAME: {}", details.getFriendlyName());
                logger.info("=SERIAL #     : {}", details.getSerialNumber());
                logger.info("=UPC          : {}", details.getUpc());
                logger.info("=PRES URI     : {}", details.getPresentationURI());
                ManufacturerDetails manufacturerDetails = details.getManufacturerDetails();
                if (manufacturerDetails != null) {
                    manufacturer = manufacturerDetails.getManufacturer();
                    logger.info("=MANUFACTURER : {}", manufacturer);
                    logger.info("=MANUFACT.URI : {}", manufacturerDetails.getManufacturerURI());
                }
                ModelDetails modelDetails = details.getModelDetails();
                if (modelDetails != null) {
                    // Model Name | Desc | Number
                    logger.info("=MODEL        : {} | {} | {}", modelDetails.getModelName(),
                            modelDetails.getModelDescription(), modelDetails.getModelNumber());
                    logger.info("=MODEL URI    : {}", modelDetails.getModelURI());
                }
            }

            // build up ThingUID
            if (manufacturer != null) {
                if (manufacturer.toUpperCase().contains("BTICINO")) {
                    logger.debug("==OWN:UPnP== Discovered a BTicino gateway thing with UDN '{}'",
                            device.getIdentity().getUdn().getIdentifierString());
                    return new ThingUID(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY,
                            device.getIdentity().getUdn().getIdentifierString());
                }
            }
        }
        return null;
    }

} /* class */
