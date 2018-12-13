/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.openwebnet.handler;

import static org.openhab.binding.openwebnet.OpenWebNetBindingConstants.*;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.CEN;
import org.openwebnet.message.CENPlusScenario;
import org.openwebnet.message.CENScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetScenarioHandler} is responsible for handling commands/messages for CEN/CEN+ Scenarios and Dry
 * Contact / IR.
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
    private final static int shortPressureDelay = 250; // ms

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.SCENARIO_SUPPORTED_THING_TYPES;

    public OpenWebNetScenarioHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:ScenarioHandler== constructor");
        if (OpenWebNetBindingConstants.THING_TYPE_BUS_DRY_CONTACT_IR.equals(thing.getThingTypeUID())) {
            isDryContactIR = true;
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:ScenarioHandler== initialize() thing={}", thing.getUID());
    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:ScenarioHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        if (isDryContactIR) { // channel state request makes sense only for DryContactIR things
            bridgeHandler.gateway.send(CENPlusScenario.requestStatus(toWhere(channel)));
        }
    }

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {
        logger.warn("==OWN:ScenarioHandler== Read-only channel, unsupported command {}", command);

        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
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
        int buttonNumber = cenMsg.getButtonNumber();
        if (buttonNumber < 0 || buttonNumber > 31) {
            logger.warn("==OWN:ScenarioHandler== invalid CEN button number: {}. Ignoring message {}", buttonNumber,
                    cenMsg);
            return;
        }
        Channel ch = thing.getChannel(CHANNEL_SCENARIO_BUTTON + buttonNumber);
        if (ch == null) {
            logger.info("==OWN:ScenarioHandler== ADDING TO THING {} NEW CHANNEL: {}", getThing().getUID(),
                    CHANNEL_SCENARIO_BUTTON + buttonNumber);
            ChannelTypeUID channelTypeUID = new ChannelTypeUID("openwebnet", "scenarioButton"); // TODO use constants
            ThingBuilder thingBuilder = editThing();
            Channel newChannel = ChannelBuilder
                    .create(new ChannelUID(getThing().getUID(), CHANNEL_SCENARIO_BUTTON + buttonNumber), "String") // TODO
                                                                                                                   // use
                                                                                                                   // constants
                    .withType(channelTypeUID).withLabel("Button " + buttonNumber).build();
            thingBuilder.withChannel(newChannel);
            // thingBuilder.withLabel(thing.getLabel()); //TODO needed???
            updateThing(thingBuilder.build());
            ch = newChannel;
        }
        final Channel channel = ch;
        PressureState prState;
        if (cenMsg instanceof CENScenario) {
            prState = getPressureStateFromCENPressure((CENScenario) cenMsg);
        } else {
            prState = getPressureStateFromCENPlusPressure((CENPlusScenario) cenMsg);
        }
        if (prState == PressureState.PRESSED) {
            scheduler.schedule(() -> { // let's schedule state -> RELEASED
                logger.debug(
                        "==OWN:ScenarioHandler== # " + toWhere(channel.getUID()) + " updating state to 'RELEASED'...");
                updateState(channel.getUID(), new StringType(PressureState.RELEASED.toString()));
            }, shortPressureDelay, TimeUnit.MILLISECONDS);
        }

        if (prState != null) {
            updateState(ch.getUID(), new StringType(prState.toString()));
        }
    }

    // @formatter:off
    /*
     * MAPPING FROM CENScenario and CENPlusScenario to channel (button) state:
     *
     *   N=button number 0-31
     *
     *      received
     *    OWN Message     | PRESSURE_TYPE          | channel state
     *  --------------------------------------------------------
     *   *15*N*WHERE##    | PRESSURE               | do nothing
     *   *15*N#1*WHERE##  | RELEASE_SHORT_PRESSURE | PRESSED, after shortPressureDelay: RELEASED
     *   *15*N#2*WHERE##  | RELEASE_EXT_PRESSURE   | RELEASED_EXT
     *   *15*N#3*WHERE##  | EXT_PRESSURE           | PRESSED_EXT
     *   ---------------------------------------------------------
     *   *25*21#N*WHERE## | SHORT_PRESSURE         | PRESSED, after shortPressureDelay: RELEASED
     *   *25*22#N*WHERE## | START_EXT_PRESSURE     | PRESSED_EXT
     *   *25*23#N*WHERE## | EXT_PRESSURE           | PRESSED_EXT
     *   *25*24#N*WHERE## | RELEASE_EXT_PRESSURE   | RELEASED_EXT
     *
     *  For example, channel sequences will be:
     *      short pressure: previous state (UNDEF/RELEASED/RELEASED_EXT) -> PRESSED -> RELEASED
     *      long pressure:  previous state (UNDEF/RELEASED/RELEASED_EXT) -> PRESSED_EXT (*repeated if keep pressed) ... -> RELEASED_EXT
     */
    // @formatter:on

    private PressureState getPressureStateFromCENPressure(CENScenario cMsg) {
        CENScenario.CEN_PRESSURE_TYPE pt = cMsg.getButtonPressure();
        if (pt == null) {
            logger.warn("==OWN:ScenarioHandler== invalid CENScenario.PRESSURE_TYPE. Frame: {}", cMsg);
            return null;
        }
        switch (pt) {
            case PRESSURE: // do nothing, let's wait RELEASE_SHORT_PRESSURE
                return null;
            case RELEASE_SHORT_PRESSURE:
                return PressureState.PRESSED;
            case EXT_PRESSURE:
                return PressureState.PRESSED_EXT;
            case RELEASE_EXT_PRESSURE:
                return PressureState.RELEASED_EXT;
            default:
                logger.warn("==OWN:ScenarioHandler== unsupported CENScenario.PRESSURE_TYPE. Frame: {}", cMsg);
                return null;
        }
    }

    private PressureState getPressureStateFromCENPlusPressure(CENPlusScenario cMsg) {
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

} // class
