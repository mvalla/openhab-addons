/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.network.internal.action;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.network.internal.handler.NetworkHandler;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class is responsible to call corresponding actions on {@link NetworkHandler}.
 *
 * @author Wouter Born - Initial contribution
 */
@ThingActionsScope(name = "network")
@NonNullByDefault
public class NetworkActions implements ThingActions {

    private final Logger logger = LoggerFactory.getLogger(NetworkActions.class);

    private @Nullable NetworkHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof NetworkHandler) {
            this.handler = (NetworkHandler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "send a WoL packet", description = "Send a Wake-on-LAN packet to wake the device.")
    public void sendWakeOnLanPacket() {
        NetworkHandler localHandler = handler;
        if (localHandler != null) {
            localHandler.sendWakeOnLanPacket();
        } else {
            logger.warn("Failed to send Wake-on-LAN packet (handler null)");
        }
    }

    public static void sendWakeOnLanPacket(@Nullable ThingActions actions) {
        if (actions instanceof NetworkActions) {
            ((NetworkActions) actions).sendWakeOnLanPacket();
        } else {
            throw new IllegalArgumentException("Actions is not an instance of NetworkActions");
        }
    }
}