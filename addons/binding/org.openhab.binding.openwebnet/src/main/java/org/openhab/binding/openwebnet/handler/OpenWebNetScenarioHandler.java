/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.openwebnet.handler;

import static org.openhab.binding.openwebnet.OpenWebNetBindingConstants.CHANNEL_SCENARIO_BUTTON;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
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
import org.openwebnet.message.CENScenario;
import org.openwebnet.message.CENScenario.PRESSURE_TYPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetScenarioHandler} is responsible for handling commands/messages for CEN/CEN+ Scenarios.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetScenarioHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetScenarioHandler.class);

    private enum PressureState {
        // TODO make it a single map and integrate it with CENScenario.PRESSURE_TYPE to have automatic translation
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

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.SCENARIO_SUPPORTED_THING_TYPES;

    public OpenWebNetScenarioHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:ScenarioHandler== constructor");
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:ScenarioHandler== initialize() thing={}", thing.getUID());

    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        // logger.debug("==OWN:ScenarioHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
        // channel.getId());
        // bridgeHandler.gateway.send(CENScenario.requestStatus(toWhere(channel)));
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
            updateChannel((CENScenario) msg);
        } else {
            logger.debug("==OWN:ScenarioHandler== handleMessage() Ignoring unsupported DIM for thing {}. Frame={}",
                    getThing().getUID(), msg);
        }
    }

    private void updateChannel(CENScenario cenMsg) {
        logger.debug("==OWN:ScenarioHandler== updateChannel() for thing: {}", thing.getUID());
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
            ChannelTypeUID channelTypeUID = new ChannelTypeUID("openwebnet", "scenarioButton");
            ThingBuilder thingBuilder = editThing();
            Channel channel = ChannelBuilder
                    .create(new ChannelUID(getThing().getUID(), CHANNEL_SCENARIO_BUTTON + buttonNumber), "String")
                    .withType(channelTypeUID).withLabel("Button " + buttonNumber).build();
            thingBuilder.withChannel(channel);
            // thingBuilder.withLabel(thing.getLabel()); //TODO needed???
            updateThing(thingBuilder.build());
        }

        String channel = CHANNEL_SCENARIO_BUTTON + buttonNumber;
        PressureState state = null;
        PRESSURE_TYPE pt = cenMsg.getButtonPressure();
        if (pt == null) {
            logger.warn("==OWN:ScenarioHandler== invalid PRESSURE_TYPE. Frame: {}", cenMsg);
            return;
        }
        switch (pt) {
            case PRESSURE:
                // do nothing, let's wait RELEASE_SHORT_PRESSURE
                break;
            case RELEASE_SHORT_PRESSURE:
                state = PressureState.PRESSED;
                scheduler.schedule(() -> {
                    logger.debug("==OWN:ScenarioHandler== # " + toWhere(channel) + " updating state to 'RELEASED'...");
                    updateState(channel, new StringType(PressureState.RELEASED.toString()));
                }, 400, TimeUnit.MILLISECONDS);
                break;
            case EXT_PRESSURE:
                state = PressureState.PRESSED_EXT;
                break;
            case RELEASE_EXT_PRESSURE:
                state = PressureState.RELEASED_EXT;
                break;
        }
        if (state != null) {
            updateState(channel, new StringType(state.toString()));
        }
    }

} // class
