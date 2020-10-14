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
package org.openhab.binding.lcn.internal;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.lcn.internal.common.LcnChannelGroup;
import org.openhab.binding.lcn.internal.common.LcnDefs;
import org.openhab.binding.lcn.internal.common.LcnDefs.KeyTable;
import org.openhab.binding.lcn.internal.common.LcnDefs.SendKeyCommand;
import org.openhab.binding.lcn.internal.common.LcnException;
import org.openhab.binding.lcn.internal.common.PckGenerator;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles actions requested to be sent to an LCN module.
 *
 * @author Fabian Wolter - Initial contribution
 */
@ThingActionsScope(name = "lcn")
@NonNullByDefault
public class LcnModuleActions implements ThingActions {
    private final Logger logger = LoggerFactory.getLogger(LcnModuleActions.class);
    private static final int DYN_TEXT_CHUNK_COUNT = 5;
    private static final int DYN_TEXT_HEADER_LENGTH = 6;
    private static final int DYN_TEXT_CHUNK_LENGTH = 12;
    private @Nullable LcnModuleHandler moduleHandler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.moduleHandler = (LcnModuleHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return moduleHandler;
    }

    @RuleAction(label = "send a hit key command", description = "Sends a \"hit key\" command to an LCN module.")
    public void hitKey(
            @ActionInput(name = "table", required = true, type = "java.lang.String", label = "Table", description = "The key table (A-D)") @Nullable String table,
            @ActionInput(name = "key", required = true, type = "java.lang.Integer", label = "Key", description = "The key number (1-8)") int key,
            @ActionInput(name = "action", required = true, type = "java.lang.String", label = "Action", description = "The action (HIT, MAKE, BREAK)") @Nullable String action) {
        try {
            if (table == null) {
                throw new LcnException("Table is not set");
            }

            if (action == null) {
                throw new LcnException("Action is not set");
            }

            KeyTable keyTable;
            try {
                keyTable = LcnDefs.KeyTable.valueOf(table.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new LcnException("Unknown key table: " + table);
            }

            SendKeyCommand sendKeyCommand;
            try {
                sendKeyCommand = SendKeyCommand.valueOf(action.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new LcnException("Unknown action: " + action);
            }

            if (!LcnChannelGroup.KEYLOCKTABLEA.isValidId(key - 1)) {
                throw new LcnException("Key number is out of range: " + key);
            }

            SendKeyCommand[] cmds = new SendKeyCommand[LcnDefs.KEY_TABLE_COUNT];
            Arrays.fill(cmds, SendKeyCommand.DONTSEND);
            boolean[] keys = new boolean[LcnChannelGroup.KEYLOCKTABLEA.getCount()];

            int keyTableNumber = keyTable.name().charAt(0) - LcnDefs.KeyTable.A.name().charAt(0);
            cmds[keyTableNumber] = sendKeyCommand;
            keys[key - 1] = true;

            getHandler().sendPck(PckGenerator.sendKeys(cmds, keys));
        } catch (LcnException e) {
            logger.warn("Could not execute hit key command: {}", e.getMessage());
        }
    }

    @RuleAction(label = "flicker a dimmer output", description = "Let a dimmer output flicker for a given count of flashes.")
    public void flickerOutput(
            @ActionInput(name = "output", type = "java.lang.Integer", required = true, label = "Output", description = "The output number (1-4)") int output,
            @ActionInput(name = "depth", type = "java.lang.Integer", label = "Depth", description = "0=25% 1=50% 2=100%") int depth,
            @ActionInput(name = "ramp", type = "java.lang.Integer", label = "Ramp", description = "0=2sec 1=1sec 2=0.5sec") int ramp,
            @ActionInput(name = "count", type = "java.lang.Integer", label = "Count", description = "Number of flashes (1-15)") int count) {
        try {
            getHandler().sendPck(PckGenerator.flickerOutput(output - 1, depth, ramp, count));
        } catch (LcnException e) {
            logger.warn("Could not send output flicker command: {}", e.getMessage());
        }
    }

    @RuleAction(label = "send a custom text", description = "Send custom text to an LCN-GTxD display.")
    public void sendDynamicText(
            @ActionInput(name = "row", type = "java.lang.Integer", required = true, label = "Row", description = "Display the text on the LCN-GTxD in the given row number (1-4)") int row,
            @ActionInput(name = "text", type = "java.lang.String", label = "Text", description = "The text to display (max. 60 chars/bytes)") @Nullable String textInput) {
        try {
            String text = textInput;

            if (text == null) {
                text = new String();
            }

            // convert String to bytes to split the data every 12 bytes, because a unicode character can take more than
            // one byte
            ByteBuffer bb = ByteBuffer.wrap(text.getBytes(LcnDefs.LCN_ENCODING));

            if (bb.capacity() > DYN_TEXT_CHUNK_LENGTH * DYN_TEXT_CHUNK_COUNT) {
                logger.warn("Dynamic text truncated. Has {} bytes: '{}'", bb.capacity(), text);
            }

            bb.limit(Math.min(DYN_TEXT_CHUNK_LENGTH * DYN_TEXT_CHUNK_COUNT, bb.capacity()));

            int part = 0;
            while (bb.hasRemaining()) {
                byte[] chunk = new byte[DYN_TEXT_CHUNK_LENGTH];
                bb.get(chunk, 0, Math.min(bb.remaining(), DYN_TEXT_CHUNK_LENGTH));

                ByteBuffer command = ByteBuffer.allocate(DYN_TEXT_HEADER_LENGTH + DYN_TEXT_CHUNK_LENGTH);
                command.put(PckGenerator.dynTextHeader(row - 1, part++).getBytes(LcnDefs.LCN_ENCODING));
                command.put(chunk);

                getHandler().sendPck(command.array());
            }
        } catch (IllegalArgumentException | LcnException e) {
            logger.warn("Could not send dynamic text: {}", e.getMessage());
        }
    }

    /**
     * Start an lcn relay timer with the given duration [ms]
     *
     * @param relaynumber 1-based number of the relay to use
     * @param duration duration of the relay timer in milliseconds
     */
    @RuleAction(label = "start a relay timer", description = "Start an LCN relay timer.")
    public void startRelayTimer(
            @ActionInput(name = "relaynumber", required = true, type = "java.lang.Integer", label = "Relay Number", description = "The relay number (1-8)") int relayNumber,
            @ActionInput(name = "duration", required = true, type = "java.lang.Double", label = "Duration [ms]", description = "The timer duration in milliseconds") double duration) {
        try {
            getHandler().sendPck(PckGenerator.startRelayTimer(relayNumber, duration));
        } catch (LcnException e) {
            logger.warn("Could not send start relay timer command: {}", e.getMessage());
        }
    }

    /** Static alias to support the old DSL rules engine and make the action available there. */
    public static void hitKey(@Nullable ThingActions actions, @Nullable String table, int key,
            @Nullable String action) {
        if (actions instanceof LcnModuleHandler) {
            ((LcnModuleActions) actions).hitKey(table, key, action);
        } else {
            throw new IllegalArgumentException("Actions is not an instance of LcnModuleActions");
        }
    }

    /** Static alias to support the old DSL rules engine and make the action available there. */
    public static void flickerOutput(@Nullable ThingActions actions, int output, int depth, int ramp, int count) {
        if (actions instanceof LcnModuleHandler) {
            ((LcnModuleActions) actions).flickerOutput(output, depth, ramp, count);
        } else {
            throw new IllegalArgumentException("Actions is not an instance of LcnModuleActions");
        }
    }

    /** Static alias to support the old DSL rules engine and make the action available there. */
    public static void sendDynamicText(@Nullable ThingActions actions, int row, @Nullable String text) {
        if (actions instanceof LcnModuleHandler) {
            ((LcnModuleActions) actions).sendDynamicText(row, text);
        } else {
            throw new IllegalArgumentException("Actions is not an instance of LcnModuleActions");
        }
    }

    /** Static alias to support the old DSL rules engine and make the action available there. */
    public static void startRelayTimer(@Nullable ThingActions actions, int relaynumber, double duration) {
        if (actions instanceof LcnModuleHandler) {
            ((LcnModuleActions) actions).startRelayTimer(relaynumber, duration);
        } else {
            throw new IllegalArgumentException("Actions is not an instance of LcnModuleActions");
        }
    }

    private LcnModuleHandler getHandler() throws LcnException {
        LcnModuleHandler localModuleHandler = moduleHandler;
        if (localModuleHandler != null) {
            return localModuleHandler;
        } else {
            throw new LcnException("Handler not set");
        }
    }
}