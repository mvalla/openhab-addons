/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.openwebnet.handler;

import static org.openhab.binding.openwebnet.OpenWebNetBindingConstants.CHANNEL_POWER;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.EnergyManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetEnergyHandler} is responsible for handling commands/messages for a Energy Management OpenWebNet
 * device.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetEnergyHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetEnergyHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.ENERGY_SUPPORTED_THING_TYPES;

    public OpenWebNetEnergyHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:EnergyHandler== constructor");
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:EnergyHandler== initialize() thing={}", thing.getUID());

    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:EnergyHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        bridgeHandler.gateway.send(EnergyManagement.requestStatus(toWhere(channel)));
    }

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {

        logger.warn("==OWN:EnergyHandler== Unsupported command {}", command);

        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        super.handleMessage(msg);
        if (msg.isCommand()) {
            logger.warn("==OWN:EnergyHandler== handleMessage() Ignoring unsupported command for thing {}. Frame={}",
                    getThing().getUID(), msg);
            return;
        } else {
            switch (msg.getDim()) {
                case EnergyManagement.DIM_ACTIVE_POWER:
                    updateActivePower((EnergyManagement) msg);
                    break;
                default:
                    logger.debug(
                            "==OWN:EnergyHandler== handleMessage() Ignoring unsupported DIM for thing {}. Frame={}",
                            getThing().getUID(), msg);
                    break;
            }
        }
    }

    /**
     * Updates energy power state based on a EnergyManagement message received from the OWN network
     */
    private void updateActivePower(EnergyManagement msg) {
        logger.debug("==OWN:EnergyHandler== updateActivePower() for thing: {}", thing.getUID());
        Integer activePower;
        try {
            activePower = Integer.parseInt(msg.getDimValues()[0]);
            updateState(CHANNEL_POWER, new DecimalType(activePower));
        } catch (NumberFormatException e) {
            logger.warn("==OWN:EnergyHandler== NumberFormatException on frame {}: {}", msg, e);
            updateState(CHANNEL_POWER, UnDefType.UNDEF);
        }
    }

} // class
