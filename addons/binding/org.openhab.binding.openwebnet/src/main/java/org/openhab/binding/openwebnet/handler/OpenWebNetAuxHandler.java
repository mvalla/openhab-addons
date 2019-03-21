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

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
//import org.eclipse.smarthome.core.library.types.DecimalType;
//import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
//import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.Auxiliary;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.OpenMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetAuxHandler} is responsible for handling commands/messages for a Aux OpenWebNet device.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */

public class OpenWebNetAuxHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetAuxHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.AUX_SUPPORTED_THING_TYPES;

    protected Auxiliary.Type auxiliaryType = Auxiliary.Type.ZIGBEE;

    public OpenWebNetAuxHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:AuxHandler== constructor");
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:AuxHandler== initialize() thing={}", thing.getUID());
        if (bridgeHandler != null && bridgeHandler.isBusGateway()) {
            auxiliaryType = Auxiliary.Type.POINT_TO_POINT;
        }
    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:AuxHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        logger.debug("==OWN:AuxHandler== requestChannelState() requestStatus {}",
                Auxiliary.requestStatus(toWhere(channel), auxiliaryType));
        bridgeHandler.gateway.send(Auxiliary.requestStatus(toWhere(channel), auxiliaryType));
    }

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {
        switch (channel.getId()) {
            case CHANNEL_SWITCH:
            case CHANNEL_SWITCH_01:
            case CHANNEL_SWITCH_02:
                handleAuxCommand(channel, command);
                break;
            default: {
                logger.warn("==OWN:AuxHandler== Unsupported channel UID {}", channel);
            }
        }
        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    /**
     * Handles Aux switch command
     *
     * @param channel
     * @param command
     */
    private void handleAuxCommand(ChannelUID channel, Command command) {
        logger.debug("==OWN:AuxHandler== handleAuxCommand() (command={} - channel={})", command, channel);
        if (command instanceof OnOffType) {
            if (OnOffType.ON.equals(command)) {
                bridgeHandler.gateway.send(Auxiliary.requestTurnOn(toWhere(channel), auxiliaryType));
            } else if (OnOffType.OFF.equals(command)) {
                bridgeHandler.gateway.send(Auxiliary.requestTurnOff(toWhere(channel), auxiliaryType));
            }
        } else {
            logger.warn("==OWN:AuxHandler== Unsupported command: {}", command);
        }
    }

    @Override
    protected String ownIdPrefix() {
        return org.openwebnet.message.Who.AUX.value().toString();
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        super.handleMessage(msg);
        updateAuxState((Auxiliary) msg);
    }

    /**
     * Updates Aux state based on a Aux message received from the OWN network
     */
    private void updateAuxState(Auxiliary msg) {
        logger.debug("==OWN:AuxHandler== updateAuxState() for thing: {}", thing.getUID());
        updateAuxOnOffState(msg);
    }

    /**
     * Updates on/off state based on a Aux message received from the OWN network
     */
    private void updateAuxOnOffState(Auxiliary msg) {
        String channelID;
        if (bridgeHandler.isBusGateway()) {
            channelID = CHANNEL_SWITCH;
        } else {
            if (BaseOpenMessage.UNIT_02.equals(OpenMessageFactory.getUnit(msg.getWhere()))) {
                channelID = CHANNEL_SWITCH_02;
            } else {
                channelID = CHANNEL_SWITCH_01;
            }
        }
        if (msg.isOn()) {
            updateState(channelID, OnOffType.ON);
        } else if (msg.isOff()) {
            updateState(channelID, OnOffType.OFF);
        } else {
            logger.info("==OWN:AuxHandler== updateAuxOnOffState() Ignoring unsupported WHAT for thing {}. Frame={}",
                    getThing().getUID(), msg);
        }
    }

    /**
     * Returns a WHERE address (string) based on bridge type and unit (optional)
     *
     * @param unit device unit
     **/
    protected String toWhere(String unit) {
        logger.debug("==OWN:AuxHandler== toWhere(unit) ownId={}", ownId);
        if (bridgeHandler.isBusGateway()) {
            return deviceWhere;
        } else {
            return deviceWhere + unit;
        }
    }

    /**
     * Returns a WHERE address based on channel
     *
     * @param channel channel
     **/
    protected String toWhere(ChannelUID channel) {
        logger.debug("==OWN:AuxHandler== toWhere(ChannelUID) ownId={}", ownId);
        if (bridgeHandler.isBusGateway()) {
            return deviceWhere;
        } else if (channel.getId().equals(CHANNEL_SWITCH_02)) {
            return deviceWhere + BaseOpenMessage.UNIT_02;
        } else { // CHANNEL_SWITCH_01 or other channels
            return deviceWhere + BaseOpenMessage.UNIT_01;
        }
    }

} // class
