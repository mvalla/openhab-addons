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
import org.eclipse.smarthome.config.discovery.UpnpDiscoveryParticipant;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BusGatewayDiscoveryParticipant} is responsible processing the results of searches for UPnP devices
 *
 * @author Massimo Valla - Initial contribution
 */

// @Component(service = DiscoveryService.class, immediate = true, configurationPid = "discovery.<bindingID>")

// @Component(immediate = true)
public class BusGatewayDiscoveryParticipant implements UpnpDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(BusGatewayDiscoveryParticipant.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        // logger.debug("==OWN:UPnP== getSupportedThingTypeUIDs()");
        return Collections.singleton(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY);
    }

    @Override
    public @Nullable DiscoveryResult createResult(RemoteDevice device) {
        ThingUID uid = getThingUID(device);
        if (uid != null) {
            Map<String, Object> properties = new HashMap<>(2);
            properties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_HOST, device.getDetails().getBaseURL().getHost());
            properties.put(OpenWebNetBindingConstants.CONFIG_PROPERTY_PORT, device.getDetails().getBaseURL().getPort());
            // properties.put(SERIAL_NUMBER, device.getDetails().getSerialNumber());
            DiscoveryResult result = DiscoveryResultBuilder.create(uid).withProperties(properties)
                    .withLabel(device.getDetails().getFriendlyName()).build();
            logger.info("==OWN:UPnP== Created a DiscoveryResult for device '{}' ",
                    device.getDetails().getFriendlyName());
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
        // return null;
    }

    private ThingUID discoverDetails(RemoteDevice device) {
        // FIXME debug mode for now to print as log info UPnP answers from devices
        // TODO check device Manufacturer and notify found device to OH2 inbox
        //
        // device.Manufacturer contains "BTicino"
        // device.RemoteEndPoint.Address
        // device.RemoteEndPoint.Port
        // device.ModelName
        //
        logger.info("================================================");
        logger.info("==OWN:UPnP== DISCOVERED DEVICE: {}", device);
        DeviceDetails details = device.getDetails();
        if (details != null) {
            logger.info("=FRIENDLY NAME: {}", details.getFriendlyName());
            logger.info("=BASE URL     : {}", details.getBaseURL());
            logger.info("=SERIAL #     : {}", details.getSerialNumber());
            logger.info("=UPC          : {}", details.getUpc());
            RemoteDeviceIdentity identity = device.getIdentity();
            if (identity != null) {
                logger.info("=ID.DESC URL  : {}", identity.getDescriptorURL());
                logger.info("=ID.MAX AGE   : {}", identity.getMaxAgeSeconds());
            }
            ManufacturerDetails manufacturerDetails = details.getManufacturerDetails();
            if (manufacturerDetails != null) {
                logger.info("=MANUFACTURER : {}", manufacturerDetails.getManufacturer());
            }
            ModelDetails modelDetails = details.getModelDetails();
            if (modelDetails != null) {
                // Model Name | Desc | Number
                logger.info("=MODEL        : {} | {} | {}", modelDetails.getModelName(),
                        modelDetails.getModelDescription(), modelDetails.getModelNumber());
                String modelName = modelDetails.getModelName();
                if (modelName != null) {
                    if (modelName.startsWith("BTicino")) {
                        return new ThingUID(OpenWebNetBindingConstants.THING_TYPE_BUS_GATEWAY,
                                details.getBaseURL().getHost());
                    }
                }
            }
        }
        return null;
    }
}
