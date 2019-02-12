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
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.Lighting;
import org.openwebnet.message.OpenMessage;
import org.openwebnet.message.OpenMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetCommandHandler} is responsible for handling commands/messages for a Command OpenWebNet
 * device.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */

public class OpenWebNetCommandHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetCommandHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.COMMAND_SUPPORTED_THING_TYPES;
    protected Lighting.Type lightingType = Lighting.Type.ZIGBEE;

    // protected Command.Type commandType = Command.Type.ZIGBEE;
    public OpenWebNetCommandHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:CommandHandler== constructor");
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:CommandHandler== initialize() thing={}", thing.getUID());
        if (bridgeHandler != null && bridgeHandler.isBusGateway()) {
            lightingType = Lighting.Type.POINT_TO_POINT;
        }
    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:CommandHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        // is not possible to request channel state for Command buttons
        updateStatus(ThingStatus.ONLINE);
        updateState(channel, UnDefType.UNDEF);
    }

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {
        String who = (String) getConfig().get(CONFIG_PROPERTY_WHO);
        String what = (String) getConfig().get(CONFIG_PROPERTY_WHAT);
        String whatOff = (String) getConfig().get(CONFIG_PROPERTY_WHATOFF);
        String where = (String) getConfig().get(CONFIG_PROPERTY_WHERE);
        switch (channel.getId()) {
            case CHANNEL_SWITCH:
                handleCommandSwitch(channel, command, who, what, whatOff, where);
                break;
            case CHANNEL_COMMAND_CONTACT:
                logger.debug("CHANNEL_COMMAND_CONTACT {}", command);
                break;
            case CHANNEL_COMMAND_WHAT:
                handleCommandWhat(channel, command, who, what, where);
                break;
            default: {
                logger.warn("==OWN:CommandHandler== Unsupported channel UID {}", channel);
            }
        }
        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    /**
     * Handles Command what command
     *
     * @param channel
     * @param command
     * @param who
     * @param what
     * @param where
     */
    private void handleCommandWhat(ChannelUID channel, Command command, String who, String what, String where) {
        logger.debug("==OWN:CommandHandler== handleCommandWhat() (who={} - what={} - channel={})", who, what, channel);
        if (what != null) {
            SendOWN(who, command.toString(), where);
        } else {
            logger.debug("==OWN:CommandHandler== handleCommandWhat() What is null");
        }
    }

    /**
     * Handles Command switch command ON/OFF
     *
     * @param channel
     * @param command
     * @param who
     * @param what
     * @param whatOff
     * @param where
     */
    private void handleCommandSwitch(ChannelUID channel, Command command, String who, String what, String whatOff,
            String where) {
        logger.debug("==OWN:CommandHandler== handleCommandSwitch() (who={} - what={} - channel={})", who, what,
                channel);
        if (command instanceof OnOffType) {
            if (OnOffType.ON.equals(command)) {
                if (what != null) {
                    SendOWN(who, what, where);
                    updateState(CHANNEL_COMMAND_CONTACT, OpenClosedType.OPEN);
                } else {
                    logger.debug("==OWN:CommandHandler== handleCommandWhat() What is null");
                }
            } else if (OnOffType.OFF.equals(command)) {
                if (whatOff != null) {
                    SendOWN(who, whatOff, where);
                    updateState(CHANNEL_COMMAND_CONTACT, OpenClosedType.CLOSED);
                } else {
                    logger.debug("==OWN:CommandHandler== handleCommandWhat() WhatOff is null");
                }
            }
        }
    }

    /**
     * Send to bus
     *
     * @param channel
     * @param command
     * @param who
     * @param what
     * @param where
     */
    private void SendOWN(String who, String what, String where) {
        String commandOWN = OpenMessage.FRAME_START + who + OpenMessage.FRAME_START + what + OpenMessage.FRAME_START
                + where + OpenMessage.FRAME_END;
        bridgeHandler.gateway.send(OpenMessageFactory.parse(commandOWN));
    }

    @Override
    protected String ownIdPrefix() {
        // return org.openwebnet.message.Who.AUX.value().toString();
        // MC Problema da risolvere per ID univoco
        return "C." + getConfig().get(CONFIG_PROPERTY_WHO).toString();
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        super.handleMessage(msg);
        String who = (String) getConfig().get(CONFIG_PROPERTY_WHO);
        String what = (String) getConfig().get(CONFIG_PROPERTY_WHAT);
        String where = (String) getConfig().get(CONFIG_PROPERTY_WHERE);
        String compare = "<" + getConfig().get(CONFIG_PROPERTY_COMPARE) + ">";
        if (compare.equals(msg.toString())) {
            SendOWN(who, what, where);
            updateState(CHANNEL_COMMAND_SWITCH, OnOffType.ON);
            updateState(CHANNEL_COMMAND_CONTACT, OpenClosedType.OPEN);
        } else {
            logger.debug("==OWN:CommandHandler== handleMessage() Compare is different");
        }
    }

    /**
     * Returns a WHERE address (string) based on bridge type and unit (optional)
     *
     * @param unit device unit
     **/
    protected String toWhere(String unit) {
        logger.debug("==OWN:CommandHandler== toWhere(unit) ownId={}", ownId);
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
        logger.debug("==OWN:CommandHandler== toWhere(ChannelUID) ownId={}", ownId);
        if (bridgeHandler.isBusGateway()) {
            return deviceWhere;
        } else if (channel.getId().equals(CHANNEL_SWITCH_02)) {
            return deviceWhere + BaseOpenMessage.UNIT_02;
        } else { // CHANNEL_SWITCH_01 or other channels
            return deviceWhere + BaseOpenMessage.UNIT_01;
        }
    }

} // class
