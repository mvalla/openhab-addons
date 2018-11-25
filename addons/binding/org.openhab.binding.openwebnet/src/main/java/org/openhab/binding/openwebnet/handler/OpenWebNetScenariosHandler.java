/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.openwebnet.handler;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.BaseOpenMessage;
import org.openwebnet.message.CENScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetScenariosHandler} is responsible for handling commands/messages for CEN/CEN+ Scenarios.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetScenariosHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetScenariosHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.SCENARIOS_SUPPORTED_THING_TYPES;

    public OpenWebNetScenariosHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:ScenariosHandler== constructor");
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:ScenariosHandler== initialize() thing={}", thing.getUID());

    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:ScenariosHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        bridgeHandler.gateway.send(CENScenario.requestStatus(toWhere(channel)));
    }

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {

        logger.warn("==OWN:ScenariosHandler== Read only channel, unsupported command {}", command);

        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        super.handleMessage(msg);
        // TODO
    }

} // class
