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

import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.CEN;
import org.openwebnet.message.CENPlusScenario;
import org.openwebnet.message.CENScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetScenarioHandler} is responsible for handling commands/messages for CEN/CEN+ Scenarios and Dry
 * Contact / IR sensors.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetScenarioHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetScenarioHandler.class);

    private enum PressureState {
        // TODO make it a single map and integrate it with CENScenario/CENPlusScenario.PRESSURE_TYPE to have automatic
        // translation
        PRESSED("PRESSED"),
        RELEASED("RELEASED"),
        PRESSED_EXT("PRESSED_EXT"),
        RELEASED_EXT("RELEASED_EXT");

        private final String pressure;

        PressureState(final String pr) {
            this.pressure = pr;
        }

        @Override
        public String toString() {
            return pressure;
        }

    }

    private boolean isDryContactIR = false;
    private boolean isCENPlus = false;

    private final static int SHORT_PRESSURE_DELAY = 300; // ms
    private final static int EXT_PRESS_INTERVAL = 500; // ms

    // ConcurrentHashMap of schedules associated to channels (buttons)
    private Map<Channel, ScheduledFuture<?>> channelsSchedules = new ConcurrentHashMap<>();

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.SCENARIO_SUPPORTED_THING_TYPES;

    public OpenWebNetScenarioHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:ScenarioHandler== constructor");
        if (OpenWebNetBindingConstants.THING_TYPE_BUS_DRY_CONTACT_IR.equals(thing.getThingTypeUID())) {
            isDryContactIR = true;
        } else if (OpenWebNetBindingConstants.THING_TYPE_BUS_CENPLUS_SCENARIO_CONTROL.equals(thing.getThingTypeUID())) {
            isCENPlus = true;
            logger.debug("==OWN:ScenarioHandler== CEN+ device for thing: {}", getThing().getUID());
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:ScenarioHandler== initialize() thing={}", thing.getUID());
        Object buttonsConfig = getConfig().get(CONFIG_PROPERTY_SCENARIO_BUTTONS);
        if (buttonsConfig != null) {
            Set<Integer> buttons = csvStringToSetInt((String) buttonsConfig);
            if (!buttons.isEmpty()) {
                ThingBuilder thingBuilder = editThing();
                Channel ch;
                for (Integer i : buttons) {
                    ch = thing.getChannel(CHANNEL_SCENARIO_BUTTON + i);
                    if (ch == null) {
                        thingBuilder.withChannel(buttonToChannel(i));
                        logger.debug("==OWN:ScenarioHandler== added channel {} to thing: {}", i, getThing().getUID());
                    }
                }
                updateThing(thingBuilder.build());
            } else {
                logger.warn("==OWN:ScenarioHandler== invalid config parameter buttons='{}' for thing {}", buttonsConfig,
                        thing.getUID());
            }
        }
    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:ScenarioHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        if (isDryContactIR) {
            bridgeHandler.gateway.send(CENPlusScenario.requestStatus(deviceWhere));
        } else { // is not possible to request channel state for CEN/CEN+ buttons
            updateStatus(ThingStatus.ONLINE);
            updateState(channel, UnDefType.UNDEF);
        }
    }

    // @formatter:off
    /*
     * MAPPING FROM channel command to CENScenario/CENPlusScenario OWN messages (N=button number 0-31)
     *   ch command     | PRESSURE_TYPE                               | OWN message (CEN/CEN+)
     *  ------------------------------------------------------------------------------------------------------------
     *     PRESSED      | CEN:  PRESSURE then RELEASE_SHORT_PRESSURE  | CEN:  *15*N*WHERE## then *15*N#1*WHERE## /
     *                  | CEN+: SHORT_PRESSURE                        |     CEN+: *25*21#N*WHERE##
     *     RELEASED     | nothing                                     | --- / ---
     *     PRESSED_EXT  | EXT_PRESSURE / START_EXT_PRESSURE           | *15*N#3*WHERE## / *25*22#N*WHERE##
     *     RELEASED_EXT | RELEASE_EXT_PRESSURE / RELEASE_EXT_PRESSURE | *15*N#2*WHERE## / *25*24#N*WHERE##
     */
    // @formatter:on

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {
        logger.debug("==OWN:ScenarioHandler== handleChannelCommand() (command={})", command);
        Integer buttonNumber = channelToButton(channel);
        if (buttonNumber == null) {
            logger.warn("==OWN:ScenarioHandler== cannot get button number from channel: {}. Ignoring command {}",
                    channel, command);
            return;
        }
        if (command instanceof StringType) {
            PressureState prState;
            try {
                prState = PressureState.valueOf(((StringType) command).toString());
            } catch (IllegalArgumentException e) {
                logger.warn("==OWN:ScenarioHandler== Cannot handle command {} for thing {} ({})", command,
                        getThing().getUID(), e.getMessage());
                return;
            }
            switch (prState) {
                case PRESSED:
                    if (isCENPlus) {
                        bridgeHandler.gateway.send(CENPlusScenario.virtualShortPressure(deviceWhere, buttonNumber));
                    } else {
                        bridgeHandler.gateway.send(CENScenario.virtualStartPressure(deviceWhere, buttonNumber));
                        scheduler.schedule(() -> { // let's schedule a CEN virtual release OWN message
                            logger.debug(
                                    "==OWN:ScenarioHandler== # " + deviceWhere + " sending CEN virtual release...");
                            bridgeHandler.gateway
                                    .send(CENScenario.virtualReleaseShortPressure(deviceWhere, buttonNumber));
                        }, SHORT_PRESSURE_DELAY, TimeUnit.MILLISECONDS);
                    }
                    break;
                case RELEASED:
                    // do nothing
                    break;
                case PRESSED_EXT:
                    // TODO send more EXT PRESSURE messages every 500ms until RELEASE_EXT command
                    if (isCENPlus) {
                        bridgeHandler.gateway
                                .send(CENPlusScenario.virtualStartExtendedPressure(deviceWhere, buttonNumber));
                    } else {
                        bridgeHandler.gateway.send(CENScenario.virtualStartPressure(deviceWhere, buttonNumber));
                        scheduler.schedule(() -> { // let's schedule a CEN virtual ext pressure OWN message
                            logger.debug("==OWN:ScenarioHandler== # " + deviceWhere
                                    + " sending CEN virtual ext pressure...");
                            bridgeHandler.gateway.send(CENScenario.virtualExtendedPressure(deviceWhere, buttonNumber));
                        }, EXT_PRESS_INTERVAL, TimeUnit.MILLISECONDS);
                    }
                    break;
                case RELEASED_EXT:
                    if (isCENPlus) {
                        bridgeHandler.gateway
                                .send(CENPlusScenario.virtualReleaseExtendedPressure(deviceWhere, buttonNumber));
                    } else {
                        bridgeHandler.gateway
                                .send(CENScenario.virtualReleaseExtendedPressure(deviceWhere, buttonNumber));
                    }
                    break;
            }

        } else {
            logger.warn("==OWN:ScenarioHandler== Unsupported command {} for thing {}", command, getThing().getUID());
            return;
        }
        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    @Override
    protected String ownIdPrefix() {
        if (isDryContactIR || isCENPlus) {
            return org.openwebnet.message.Who.CEN_PLUS_SCENARIO_SCHEDULER.value().toString();
        } else {
            return org.openwebnet.message.Who.CEN_SCENARIO_SCHEDULER.value().toString();
        }
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        super.handleMessage(msg);
        logger.debug("==OWN:ScenarioHandler== handleMessage() for thing: {}", thing.getUID());
        if (msg.isCommand()) {
            if (isDryContactIR) {
                updateDryContactIRState((CENPlusScenario) msg);
            } else {
                updateButtonState((CEN) msg);
            }
        } else {
            logger.debug("==OWN:ScenarioHandler== handleMessage() Ignoring unsupported DIM for thing {}. Frame={}",
                    getThing().getUID(), msg);
        }
    }

    private void updateDryContactIRState(CENPlusScenario msg) {
        logger.debug("==OWN:ScenarioHandler== updateDryContactIRState() for thing: {}", thing.getUID());
        if (msg.isOn()) {
            updateState(CHANNEL_DRY_CONTACT_IR, OnOffType.ON);
        } else if (msg.isOff()) {
            updateState(CHANNEL_DRY_CONTACT_IR, OnOffType.OFF);
        } else {
            logger.info(
                    "==OWN:ScenarioHandler== updateDryContactIRState() Ignoring unsupported WHAT for thing {}. Frame={}",
                    getThing().getUID(), msg);
        }
    }

    private void updateButtonState(CEN cenMsg) {
        logger.debug("==OWN:ScenarioHandler== updateButtonState() for thing: {}", thing.getUID());
        Integer buttonNumber = cenMsg.getButtonNumber();
        if (buttonNumber == null || buttonNumber < 0 || buttonNumber > 31) {
            logger.warn("==OWN:ScenarioHandler== invalid CEN/CEN+ button number: {}. Ignoring message {}", buttonNumber,
                    cenMsg);
            return;
        }
        Channel ch = thing.getChannel(CHANNEL_SCENARIO_BUTTON + buttonNumber);
        if (ch == null) { // we have found a new button for this device, let's add a new channel for the button
            ThingBuilder thingBuilder = editThing();
            ch = buttonToChannel(buttonNumber);
            thingBuilder.withChannel(ch);
            updateThing(thingBuilder.build());
            logger.info("==OWN:ScenarioHandler== added new channel {} to thing {}", ch.getUID(), getThing().getUID());
        }
        final Channel channel = ch;
        PressureState prState;
        if (cenMsg instanceof CENScenario) {
            prState = cenPressureToPressureState((CENScenario) cenMsg, channel);
        } else {
            prState = cenPlusPressureToPressureState((CENPlusScenario) cenMsg);
        }
        if (prState == PressureState.PRESSED) {
            scheduler.schedule(() -> { // let's schedule state -> RELEASED
                logger.debug("==OWN:ScenarioHandler== # " + deviceWhere + " updating state to 'RELEASED'...");
                updateState(channel.getUID(), new StringType(PressureState.RELEASED.toString()));
            }, SHORT_PRESSURE_DELAY, TimeUnit.MILLISECONDS);
        }
        if (prState != null) {
            updateState(ch.getUID(), new StringType(prState.toString()));
        }
    }

    // @formatter:off
    /*
     * MAPPING FROM CENScenario and CENPlusScenario message to channel (button) state:
     *
     *   N=button number 0-31
     *
     *      received
     *    OWN Message     | PRESSURE_TYPE          | channel state
     *  ---------------------------------------------------------------------------------------
     *   *15*N*WHERE##    | PRESSURE               | schedule after (EXT_PRESS_INTERVAL + 10) a PRESSED-->RELEASED in case scenario is activated from Touchscreens (no new message will be received)
     *   *15*N#1*WHERE##  | RELEASE_SHORT_PRESSURE | PRESSED, after shortPressureDelay: RELEASED (cancel schedule)
     *   *15*N#2*WHERE##  | RELEASE_EXT_PRESSURE   | RELEASED_EXT
     *   *15*N#3*WHERE##  | EXT_PRESSURE           | PRESSED_EXT (cancel schedule)
     *   --------------------------------------------------------------------------------------
     *   *25*21#N*WHERE## | SHORT_PRESSURE         | PRESSED, after shortPressureDelay: RELEASED
     *   *25*22#N*WHERE## | START_EXT_PRESSURE     | PRESSED_EXT
     *   *25*23#N*WHERE## | EXT_PRESSURE           | PRESSED_EXT
     *   *25*24#N*WHERE## | RELEASE_EXT_PRESSURE   | RELEASED_EXT
     *
     *  For example, channel sequences will be (for both CEN and CEN+ channels):
     *      short pressure: previous state (UNDEF/RELEASED/RELEASED_EXT) --> PRESSED --> RELEASED
     *      long pressure:  previous state (UNDEF/RELEASED/RELEASED_EXT) --> PRESSED_EXT (*repeated if keep pressed) ... --> RELEASED_EXT
     */
    // @formatter:on

    private PressureState cenPressureToPressureState(CENScenario cMsg, Channel channel) {
        ScheduledFuture<?> sch;
        CENScenario.CEN_PRESSURE_TYPE pt = cMsg.getButtonPressure();
        if (pt == null) {
            logger.warn("==OWN:ScenarioHandler== invalid CENScenario.PRESSURE_TYPE. Frame: {}", cMsg);
            return null;
        }
        switch (pt) {
            case PRESSURE: // schedule a PRESSED-->RELEASED in case scenario is activated from Touchscreens (no new
                           // message will be received)
                sch = scheduler.schedule(() -> {
                    logger.debug("==OWN:ScenarioHandler== # " + deviceWhere
                            + " no message after CEN.PRESSURE, updating state to 'PRESSED'...");
                    updateState(channel.getUID(), new StringType(PressureState.PRESSED.toString()));
                    scheduler.schedule(() -> {
                        logger.debug("==OWN:ScenarioHandler== # " + deviceWhere
                                + " no message after CEN.PRESSURE, updating state to 'RELEASED'...");
                        updateState(channel.getUID(), new StringType(PressureState.RELEASED.toString()));
                    }, SHORT_PRESSURE_DELAY, TimeUnit.MILLISECONDS);
                }, EXT_PRESS_INTERVAL + 10, TimeUnit.MILLISECONDS);
                channelsSchedules.put(channel, sch);
                return null;
            case RELEASE_SHORT_PRESSURE:
                sch = channelsSchedules.get(channel);
                if (sch != null) {
                    sch.cancel(false);
                    logger.debug("==OWN:ScenarioHandler== # " + deviceWhere + " schedule cancelled");
                }
                return PressureState.PRESSED;
            case EXT_PRESSURE:
                sch = channelsSchedules.get(channel);
                if (sch != null) {
                    sch.cancel(false);
                    logger.debug("==OWN:ScenarioHandler== # " + deviceWhere + " schedule cancelled");
                }
                return PressureState.PRESSED_EXT;
            case RELEASE_EXT_PRESSURE:
                return PressureState.RELEASED_EXT;
            default:
                logger.warn("==OWN:ScenarioHandler== unsupported CENScenario.PRESSURE_TYPE. Frame: {}", cMsg);
                return null;
        }
    }

    private PressureState cenPlusPressureToPressureState(CENPlusScenario cMsg) {
        CENPlusScenario.CEN_PLUS_PRESSURE_TYPE pt = cMsg.getButtonPressure();
        if (pt == null) {
            logger.warn("==OWN:ScenarioHandler== invalid CENPlusScenario.PRESSURE_TYPE. Frame: {}", cMsg);
            return null;
        }
        switch (pt) {
            case SHORT_PRESSURE:
                return PressureState.PRESSED;
            case START_EXT_PRESSURE:
                return PressureState.PRESSED_EXT;
            case EXT_PRESSURE:
                return PressureState.PRESSED_EXT;
            case RELEASE_EXT_PRESSURE:
                return PressureState.RELEASED_EXT;
            default:
                logger.warn("==OWN:ScenarioHandler== unsupported CENPlusScenario.PRESSURE_TYPE. Frame: {}", cMsg);
                return null;
        }
    }

    private Channel buttonToChannel(int buttonNumber) {
        ChannelTypeUID channelTypeUID = new ChannelTypeUID(BINDING_ID, CHANNEL_TYPE_SCENARIO_BUTTON);
        return ChannelBuilder
                .create(new ChannelUID(getThing().getUID(), CHANNEL_SCENARIO_BUTTON + buttonNumber), "String")
                .withType(channelTypeUID).withLabel("Button " + buttonNumber).build();
    }

    private Integer channelToButton(ChannelUID channel) {
        try {
            return Integer.parseInt(channel.getId().substring(channel.getId().lastIndexOf("_") + 1));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static Set<Integer> csvStringToSetInt(String s) {
        TreeSet<Integer> intSet = new TreeSet<Integer>();
        if (s != null) {
            String sNorm = s.replaceAll("\\s", "");
            Scanner sc = new Scanner(sNorm);
            sc.useDelimiter(",");
            while (sc.hasNextInt()) {
                intSet.add(sc.nextInt());
            }
            sc.close();
        }
        return intSet;
    }

    private static String setIntToCsvString(Set<Integer> set) {
        if (set.isEmpty()) {
            return "";
        } else {
            final String SEPARATOR = ",";
            StringBuilder csvBuilder = new StringBuilder();
            for (Integer i : set) {
                csvBuilder.append(i);
                csvBuilder.append(SEPARATOR);
            }
            String csv = csvBuilder.toString();
            csv = csv.substring(0, csv.length() - SEPARATOR.length());
            return csv;
        }
    }

} // class
