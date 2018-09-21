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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.openwebnet.OpenWebNetBindingConstants;
import org.openwebnet.message.Automation;
import org.openwebnet.message.BaseOpenMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link OpenWebNetAutomationHandler} is responsible for handling commands/messages for a Automation OpenWebNet
 * device.
 * It extends the abstract {@link OpenWebNetThingHandler}.
 *
 * @author Massimo Valla - Initial contribution
 */
public class OpenWebNetAutomationHandler extends OpenWebNetThingHandler {

    private final Logger logger = LoggerFactory.getLogger(OpenWebNetAutomationHandler.class);
    private static final SimpleDateFormat formatter = new SimpleDateFormat("ss.SSS");

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = OpenWebNetBindingConstants.AUTOMATION_SUPPORTED_THING_TYPES;

    protected Automation.Type automationType = Automation.Type.ZIGBEE;

    private ChannelUID channel;

    // internal states
    public static final int STATE_STOPPED = 0;
    public static final int STATE_MOVING_UP = 1;
    public static final int STATE_MOVING_DOWN = 2;
    public static final int STATE_UNKNOWN = -1;

    // calibration states
    public static final int CALIBRATION_INACTIVE = -1;
    public static final int CALIBRATION_ACTIVATED = 0;
    public static final int CALIBRATION_GOING_UP = 1;
    public static final int CALIBRATION_GOING_DOWN = 2;

    // positions
    public static final int POSITION_MAX_STEPS = 100;
    public static final int POSITION_DOWN = 100;
    public static final int POSITION_UP = 0;
    public static final int POSITION_UNKNOWN = -1;
    public static final int SHUTTER_RUN_UNDEFINED = -1;

    private int shutterRun = SHUTTER_RUN_UNDEFINED;

    private long startedMovingAt = SHUTTER_RUN_UNDEFINED;
    private int internalState = STATE_UNKNOWN;
    private int positionEst = POSITION_UNKNOWN;
    private ScheduledFuture<?> moveSchedule;
    private int positionRequested = POSITION_UNKNOWN;
    private int calibrating = CALIBRATION_INACTIVE;
    private static final int STEP_TIME_MIN = 50; // ms

    public OpenWebNetAutomationHandler(@NonNull Thing thing) {
        super(thing);
        logger.debug("==OWN:AutomationHandler== constructor");
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("==OWN:AutomationHandler== initialize() thing={}", thing.getUID());
        // TODO change to device where
        Channel ch = thing.getChannel(CHANNEL_SHUTTER);
        if (ch != null) {
            channel = ch.getUID();
        } else {
            logger.warn("==OWN:AutomationHandler== Cannot get channel in initialize()");
            return;
        }
        if (bridgeHandler != null && bridgeHandler.isBusGateway()) {
            automationType = Automation.Type.POINT_TO_POINT;
        }
        Object shutterRunConfig = getConfig().get(CONFIG_PROPERTY_SHUTTER_RUN);
        try {
            if (shutterRunConfig == null) {
                shutterRunConfig = new String("AUTO");
                logger.debug("==OWN:AutomationHandler== shutterRun null, default to AUTO");
            } else if (shutterRunConfig instanceof java.lang.String) {
                if ("AUTO".equals(((String) shutterRunConfig).toUpperCase())) {
                    logger.debug("==OWN:AutomationHandler== shutterRun set to AUTO in configuration");
                    shutterRun = SHUTTER_RUN_UNDEFINED;
                } else { // try to parse int>=1000
                    int shutterRunInt = Integer.parseInt((String) shutterRunConfig);
                    if (shutterRunInt < 1000) {
                        throw new NumberFormatException();
                    }
                    shutterRun = shutterRunInt;
                    logger.debug("==OWN:AutomationHandler== shutterRun set to {}", shutterRun);
                }

            } else {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            logger.warn("==OWN:AutomationHandler== Wrong configuration: {} must be AUTO or an integer >= 1000",
                    CONFIG_PROPERTY_SHUTTER_RUN);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "@text/offline.wrong-configuration");
            shutterRun = SHUTTER_RUN_UNDEFINED;
        }
        updateState(CHANNEL_SHUTTER, UnDefType.UNDEF);
        positionEst = POSITION_UNKNOWN;
    }

    @Override
    protected void requestChannelState(ChannelUID channel) {
        logger.debug("==OWN:AutomationHandler== requestChannelState() thingUID={} channel={}", thing.getUID(),
                channel.getId());
        bridgeHandler.gateway.send(Automation.requestStatus(toWhere(channel), automationType));
        // TODO request shutter position, if natively supported by device
    }

    @Override
    protected void handleChannelCommand(ChannelUID channel, Command command) {
        switch (channel.getId()) {
            case CHANNEL_SHUTTER:
                handleShutterCommand(channel, command);
                break;
            default: {
                logger.warn("==OWN:AutomationHandler== Unsupported channel UID {}", channel);
            }
        }
        // TODO
        // Note: if communication with thing fails for some reason,
        // indicate that by setting the status with detail information
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
        // "Could not control device at IP address x.x.x.x");
    }

    /**
     * Handles Automation Rollershutter command (UP/DOWN, STOP/MOVE, PERCENT xx%)
     *
     * @param command
     */
    private void handleShutterCommand(ChannelUID channel, Command command) {
        // logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # handleShutterCommand() (command={})",
        // command);
        calibrating = CALIBRATION_INACTIVE;
        if (command instanceof UpDownType) {
            if (UpDownType.UP.equals(command)) { // UP
                bridgeHandler.gateway.send(Automation.requestMoveUp(toWhere(channel), automationType));
            } else { // DOWN
                bridgeHandler.gateway.send(Automation.requestMoveDown(toWhere(channel), automationType));
            }
        } else if (StopMoveType.STOP.equals(command)) { // STOP
            bridgeHandler.gateway.send(Automation.requestStop(toWhere(channel), automationType));
        } else if (command instanceof PercentType) { // PERCENT
            if (internalState != STATE_STOPPED) {
                logger.debug(
                        "==OWN:AutomationHandler==  # " + toWhere(channel) + " # already moving, ignoring go-to-XX%");
                return;
            }
            int percent = ((PercentType) command).intValue();
            if (percent != positionEst) {
                if (percent == POSITION_DOWN) { // GO TO 100%
                    bridgeHandler.gateway.send(Automation.requestMoveDown(toWhere(channel), automationType));
                } else if (percent == POSITION_UP) { // GO TO 0%
                    bridgeHandler.gateway.send(Automation.requestMoveUp(toWhere(channel), automationType));
                } else {// GO TO XX%
                    logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # {}% requested", percent);
                    if (shutterRun == SHUTTER_RUN_UNDEFINED) {
                        logger.debug("==OWN:AutomationHandler== & " + toWhere(channel) + " & " + toWhere(channel)
                                + " & shutterRun not configured, starting CALIBRATION...");
                        calibrating = CALIBRATION_ACTIVATED;
                        bridgeHandler.gateway.send(Automation.requestMoveUp(toWhere(channel), automationType));
                        positionRequested = percent;
                    } else if (shutterRun > 0 && positionEst != POSITION_UNKNOWN) { // these two must be known to
                                                                                    // calculate
                                                                                    // moveTime
                        // calculate how much time we have to move and set a deadline to stop after that time
                        int moveTime = Math
                                .round(((float) Math.abs(percent - positionEst) / POSITION_MAX_STEPS * shutterRun));
                        logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # target moveTime={}",
                                moveTime);
                        if (moveTime > STEP_TIME_MIN) { // FIXME calibrate this
                            if (moveSchedule != null && !moveSchedule.isDone()) {
                                // a moveSchedule was already scheduled and is not done... let's cancel the schedule
                                moveSchedule.cancel(false);
                                logger.warn( // should not get here....
                                        "==OWN:AutomationHandler== # " + toWhere(channel)
                                                + " # new XX% requested, old moveSchedule cancelled");
                            }
                            // IMPORTANT IMPORTANT
                            // start the schedule BEFORE sending the command, because the synch command waits for ACK
                            // and can take some 300ms --- IS THIS STILL NEEDED ??
                            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # Starting schedule...");
                            moveSchedule = scheduler.schedule(() -> {
                                logger.debug("==OWN:AutomationHandler== # " + toWhere(channel)
                                        + " # moveSchedule expired, sending STOP...");
                                bridgeHandler.gateway
                                        .sendHighPriority(Automation.requestStop(toWhere(channel), automationType));
                            }, moveTime, TimeUnit.MILLISECONDS);
                            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel)
                                    + " # ...schedule started, now sending highPriority command...");
                            if (percent < positionEst) {
                                bridgeHandler.gateway
                                        .sendHighPriority(Automation.requestMoveUp(toWhere(channel), automationType));
                            } else {
                                bridgeHandler.gateway
                                        .sendHighPriority(Automation.requestMoveDown(toWhere(channel), automationType));
                            }
                            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel)
                                    + " # ...gateway.sendHighPriority() returned");
                        } else {
                            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel)
                                    + " # moveTime < STEP_TIME_MIN, do nothing");
                        }
                    } else {
                        logger.warn(
                                "==OWN:AutomationHandler== Command {} cannot be executed: unknown position or shutterRun configuration param not set (thing={})",
                                command, thing.getUID());
                    }
                }
            } else {
                logger.debug("==OWN:AutomationHandler== # " + toWhere(channel)
                        + " # handleShutterCommand() Command {}% == positionEst, nothing to do", percent);
            }
        } else {
            logger.warn("==OWN:AutomationHandler== Command {} is not supported for thing {}", command, thing.getUID());
        }
    }

    @Override
    protected void handleMessage(BaseOpenMessage msg) {
        updateAutomationState((Automation) msg);
        // IMPORTANT update state, then update thing status in the super method, to avoid delays
        super.handleMessage(msg);
    }

    /**
     * Updates automation device state based on an Automation message received from OWN network
     */
    private void updateAutomationState(Automation msg) {
        if (msg.isUp()) {
            updateStateInt(STATE_MOVING_UP);
            if (calibrating == CALIBRATION_ACTIVATED) {
                calibrating = CALIBRATION_GOING_UP;
                logger.debug("==OWN:AutomationHandler== & " + toWhere(channel)
                        + " & ...CALIBRATING: started going ALL UP...");
            }
        } else if (msg.isDown()) {
            updateStateInt(STATE_MOVING_DOWN);
            if (calibrating == CALIBRATION_ACTIVATED) {
                calibrating = CALIBRATION_GOING_DOWN;
                logger.debug("==OWN:AutomationHandler== & " + toWhere(channel)
                        + " & ...CALIBRATING: started going ALL DOWN...");
            }
        } else if (msg.isStop()) {
            long stoppedAt = System.currentTimeMillis();
            if (calibrating == CALIBRATION_GOING_DOWN && shutterRun == SHUTTER_RUN_UNDEFINED) {
                shutterRun = (int) (stoppedAt - startedMovingAt);
                logger.debug("==OWN:AutomationHandler== & " + toWhere(channel)
                        + " & ...CALIBRATING: reached DOWN ===> shutterRun={}", shutterRun);
                updateStateInt(STATE_STOPPED);
                logger.debug("==OWN:AutomationHandler== & " + toWhere(channel)
                        + " & ---CALIBRATION COMPLETED, now going to {}%", positionRequested);
                handleShutterCommand(channel, new PercentType(positionRequested));
                Configuration configuration = editConfiguration();
                configuration.put(CONFIG_PROPERTY_SHUTTER_RUN, Integer.toString(shutterRun));
                updateConfiguration(configuration);
                logger.debug("==OWN:AutomationHandler== & " + toWhere(channel)
                        + " & configuration updated: shutterRun = {}ms", shutterRun);
            } else if (calibrating == CALIBRATION_GOING_UP) {
                updateStateInt(STATE_STOPPED);
                logger.debug("==OWN:AutomationHandler==  & " + toWhere(channel)
                        + " & ..CALIBRATING: reached UP, now sending DOWN command...", shutterRun);
                calibrating = CALIBRATION_ACTIVATED;
                bridgeHandler.gateway.send(Automation.requestMoveDown(toWhere(channel), automationType));
            } else {
                updateStateInt(STATE_STOPPED);
            }
        } else {
            logger.warn(
                    "==OWN:AutomationHandler== updateAutomationState() FRAME {} NOT SUPPORTED for thing {}, ignoring it.",
                    msg, thing.getUID());
        }
    }

    /** Updates internal state: state and positionEst */
    private void updateStateInt(int newState) {
        if (internalState == STATE_STOPPED) {
            if (newState != STATE_STOPPED) { // moving after stop
                startedMovingAt = System.currentTimeMillis();
                logger.debug(
                        "==OWN:AutomationHandler== # " + toWhere(channel) + " # MOVING {} - startedMovingAt={} - {}",
                        newState, startedMovingAt, formatter.format(new Date(startedMovingAt)));
            }
        } else { // we were moving
            updatePosition();
            if (newState != STATE_STOPPED) { // moving after moving, take new timestamp
                startedMovingAt = System.currentTimeMillis();
                logger.debug(
                        "==OWN:AutomationHandler== # " + toWhere(channel) + " # MOVING {} - startedMovingAt={} - {}",
                        newState, startedMovingAt, formatter.format(new Date(startedMovingAt)));
            }
            // cancel the schedule
            if (moveSchedule != null && !moveSchedule.isDone()) {
                moveSchedule.cancel(false);
            }
        }
        internalState = newState;
        logger.debug(
                "==OWN:AutomationHandler== # " + toWhere(channel)
                        + " # [[[ internalState={} positionEst={} - calibrating={} shutterRun={} ]]]",
                internalState, positionEst, calibrating, shutterRun);
    }

    /**
     * update positionEst based on movement time and current internalState
     *
     */
    private void updatePosition() {
        int newPos = POSITION_UNKNOWN;
        if (shutterRun > 0) {// we have shutterRun defined, let's calculate new positionEst
            long movedTime = System.currentTimeMillis() - startedMovingAt;
            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # current positionEst={}", positionEst);
            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # movedTime={}", movedTime);
            int movedSteps = Math.round((float) movedTime / shutterRun * POSITION_MAX_STEPS);
            logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # movedSteps: {} {}", movedSteps,
                    (internalState == STATE_MOVING_DOWN) ? "DOWN(+)" : "UP(-)");
            if (positionEst == POSITION_UNKNOWN && movedSteps >= POSITION_MAX_STEPS) { // we did a full run
                newPos = (internalState == STATE_MOVING_DOWN) ? POSITION_DOWN : POSITION_UP;
            } else if (positionEst != POSITION_UNKNOWN) {
                newPos = positionEst + ((internalState == STATE_MOVING_DOWN) ? movedSteps : -movedSteps);
                logger.debug("==OWN:AutomationHandler== # " + toWhere(channel) + " # {} {} {} = {}", positionEst,
                        (internalState == STATE_MOVING_DOWN) ? "+" : "-", movedSteps, newPos);
                if (newPos > POSITION_DOWN) {
                    newPos = POSITION_DOWN;
                }
                if (newPos < POSITION_UP) {
                    newPos = POSITION_UP;
                }
            }
        }
        if (newPos != POSITION_UNKNOWN) {
            if (newPos != positionEst) {
                updateState(CHANNEL_SHUTTER, new PercentType(newPos));
            }
        } else {
            updateState(CHANNEL_SHUTTER, UnDefType.UNDEF);
        }
        positionEst = newPos;
    }

    @Override
    public void thingUpdated(Thing thing) {
        super.thingUpdated(thing);
        logger.debug("==OWN:AutomationHandler== thingUpdated()");
    }
} /* class */
